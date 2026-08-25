# Plan: Split `requestCleanupInterval`'s double duty

Extracted from `OPTIONS_TIMEOUT_IMPROVEMENTS.md` candidate #1 (a design decision with real semantic weight, not a mechanical cleanup, so it gets its own doc). Now also folds in the detailed mechanics trace — including a correction: since the `Duration`→millis refactor the blocking and async paths no longer share a default source, so the "two jobs" framing is really three.

Defaults referenced throughout: `DEFAULT_CONNECTION_TIMEOUT = 2000` ms, `DEFAULT_REQUEST_CLEANUP_INTERVAL = 5000` ms (`OptionsConstants.java:51,75`).

## How a request actually times out (mechanism)

A request never self-arms a timer. A request future is completed only by one of:
1. a reply arriving → `deliverReply` removes it from `responsesAwaiting` and completes it (`NatsConnection.java:1518`);
2. the background **cleanup task** scanning `responsesAwaiting`, finding `future.hasExceededTimeout()`, and calling `future.cancelTimedOut()` (`cleanResponses`).

The future's deadline is a single absolute nanotime stamp set once at construction and never moved: `timeOutAfterNanoTime = now + 10ms hydration + timeoutMillis*1e6` (`NatsRequestCompletableFuture.java:34`); `hasExceededTimeout()` is just `now > timeOutAfterNanoTime` (line 60). So **whether a future is cancelled "on time" depends entirely on when the cleanup task next runs** — the cadence is the enforcement resolution.

## The problem: one concept, three sources

| # | Job | Source today | Code |
|---|---|---|---|
| 1 | **Cleanup-scan cadence** — how often `cleanResponses(false)` runs to expire stale futures | `requestCleanupInterval` (5s) | `NatsConnection.java:583-585` (`cleanMillis`, gated on `> 0`) |
| 2 | **Async future's internal deadline** when caller passes no timeout | `requestCleanupInterval` (5s) | `NatsConnection.java:1506` → `NatsRequestCompletableFuture` ctor |
| 3 | **Blocking `request().get()` wait** when caller passes no timeout | `connectionTimeout` (2s) | `NatsConnection.java:1360` |

Jobs 1 and 2 are the original "double duty": one value drives both the cleanup cadence and the default async deadline. Job 3 is the wrinkle the high-level framing missed — the blocking path's default comes from a *different option* than the async path's.

### Finding A — blocking vs async disagree on the default deadline

For the *same* "I didn't specify a timeout" intent:
- `requestAsync(subject, data)` (`NatsConnection.java:1377`) passes `-1`; the future's deadline is `requestCleanupInterval` = **5s** (job 2), enforced by the cleanup scan.
- blocking `request(subject, data, <=0)` waits `connectionTimeout` = **2s** on `future.get(...)` then returns `null` (`NatsConnection.java:1360`; its javadoc even says *"a value less than 1 uses the default connection timeout"*).

So the blocking caller gives up at 2s over a future that, by its own reckoning, is still alive until 5s. Consequences:
- A reply arriving in the 2–5s window completes a future nobody is waiting on (the blocking caller already got `null`); the response is silently dropped.
- The abandoned future lingers in `responsesAwaiting` until the cleanup scan cancels it (~5s deadline + up to one cadence of slop), so the map holds entries ~2.5–5× longer than the caller's effective timeout.
- A reader who trusts the docs sees "default = connection timeout" for blocking and reasonably assumes async matches — it doesn't.

**This 2s-vs-5s split is a defect regardless of which option below wins; the blocking and async no-timeout defaults must be unified.**

### Finding B — the cadence bounds cancellation promptness for *every* async future

Because the cadence (job 1) *is* the cancellation mechanism, it's the resolution at which any async future's timeout is enforced — not just the no-timeout case. `requestAsync(subject, data, 500)` sets a 500ms deadline, but `cleanResponses` runs every 5s, so the future isn't `cancelTimedOut()`-ed until the next scan — anywhere from **0.5s to ~5.5s** after creation. (The 10ms `HYDRATION_TIME_NANOS` buffer is noise next to the up-to-5s scan slop.) Net: **you cannot tighten cancellation promptness without shortening the default async deadline, and cannot lengthen the default async deadline without making cancellation coarser** — it's the same number.

### Consequences at stock defaults (conn 2s, cleanup 5s)

| Call | Caller-visible timeout | Future actually cancelled | Note |
|---|---|---|---|
| `request(subj, data, 0)` (blocking, no timeout) | 2s → `null` | ~5–10s (cleanup) | get() default ≠ future deadline (Finding A) |
| `requestAsync(subj, data)` then `get()` | ~5–10s | ~5–10s | default deadline = cleanup interval + cadence slop |
| `requestAsync(subj, data, 500)` then `get()` | up to ~5.5s | up to ~5.5s | short deadline, coarse enforcement (Finding B) |
| set `requestCleanupInterval(30000)` for a longer default request timeout | async default → 30s **and** stale futures linger up to 30s; cancel slop up to 30s | — | the side effect, quantified |
| set `requestCleanupInterval(250)` for prompt cleanup | async default deadline drops to 250ms | — | tightening cadence silently shortens the async default |

## Options / decisions

Two **independent** decisions fall out of this:

### 1. Default-request-timeout source (and unify blocking + async)
- **(a) Add a distinct `defaultRequestTimeout(long)` option** (millis, like the other timeouts) and use it for **both** the blocking `get()` (job 3) and the async future deadline (job 2); leave `requestCleanupInterval` as cadence-only (job 1). Most explicit; unifies blocking/async; one new option.
- **(b) Use `connectionTimeout` as the default for both paths** (blocking already does — make async match) and keep `requestCleanupInterval` as cadence-only. Fewer options.
  - Note the Go client does **not** do this for JetStream — it uses a standalone 5 s default (`defaultAPITimeout`), independent of the 2 s connection timeout. So (b) diverges from Go's choice; weigh that against the simplicity.
- Either way, **unify the 2s/5s split from Finding A** — that's not optional.

### 2. Cancellation promptness vs cadence (Finding B)
Independent of #1: even after unifying the default, periodic-sweep cancellation stays coarse for async futures with short explicit timeouts. If prompt async cancellation matters, either decouple the cadence and make it tighter, or give each future a real per-future timer instead of a periodic sweep. If coarse cancellation is acceptable — the blocking path self-times via `get()`, so only no-arg `future.get()` async callers are exposed — leave it and document it.

## Dependency

Land decision #1 alongside `RequestBehaviorImprovement.md`'s request-result redesign: it touches both the blocking-`get()` fallback (`NatsConnection.java:1360`) and the async future-deadline (`1506`), so deciding the default-source there keeps the two edits from fighting over the same lines.

## Naming

If option (a): plain `defaultRequestTimeout(long)` millis with `getDefaultRequestTimeout()`, unit in the javadoc, per `DURATION_CATALOG_AND_IMPROVEMENTS.md` §3. `requestCleanupInterval(long)` stays as the cadence-only knob.
