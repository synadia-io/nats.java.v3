# Builder / Creator numeric-coercion audit

Audit of every numeric setter across the builders and `*Creator` classes: does the **setter** coerce/normalize the value, does coercion happen in **build()/constructor**, does it **validate (throw)**, or is it stored **raw**. Goal: a consistent, memorable policy for `long`-millis (and other numeric) settings, so "do I coerce here?" isn't a per-field guess.

## The coercion idioms in use today

| idiom | meaning | example |
|---|---|---|
| **clamp-to-0** | `Math.max(0, v)` / `v < 1 ? 0 : v` — negatives become 0 ("unset/disabled") | `expiresIn`, `socketReadTimeout` |
| **clamp-to-min** | `v <= MIN ? MIN : v` — never below a floor, never disabled | `socketWriteTimeout` |
| **default-on-nonpositive** | `v <= 0 ? DEFAULT : v` — unset → the configured default | `connectionTimeout`, `drainTimeout` |
| **sentinel -1** | `v < 1 ? -1 : v` — `-1` is the "unset/OS-default" marker | `socketSoLinger`, buffers, `minPending` |
| **normalize helper** | `normalizeLong(v, min)` / `normalizeDuration(...)` — creator standard, coerces to `UNSET` below min | all `*Creator` time/count setters |
| **validate (throws)** | `validateGtZero` / `validateGtEqZero` / `validate…` — rejects bad input | `PublishOptions.streamTimeout`, KV TTLs, `batchSize` in build |
| **raw** | `this.f = v;` — stored verbatim, no coercion | see "Raw" list below |

## By class

### OptionsBuilder — **inconsistent** (the main finding)
| setter | coercion | kind |
|---|---|---|
| `connectionTimeout(long)` | `millis <= 0 ? DEFAULT_CONNECTION_TIMEOUT : millis` | default-on-nonpositive |
| `socketReadTimeout(long)` | `millis < 1 ? 0 : millis` | clamp-to-0 |
| `socketWriteTimeout(long)` | `millis <= MINIMUM_SOCKET_WRITE_TIMEOUT ? MINIMUM_SOCKET_WRITE_TIMEOUT : millis` | clamp-to-min |
| `socketSoLinger(int)` | `seconds < 1 ? -1 : seconds` | sentinel -1 |
| `socketReceiveBufferSize(int)` / `socketSendBufferSize(int)` | `bytes < 1 ? -1 : bytes` | sentinel -1 |
| `maxControlLine(int)` | `bytes < 0 ? DEFAULT_MAX_CONTROL_LINE : bytes` | default-on-nonpositive |
| `maxMessagesInOutgoingQueue(int)` | `< 0 ? DEFAULT… : v` | default-on-nonpositive |
| `reconnectWait(long)` | **raw** | — |
| `reconnectJitter(long)` / `reconnectJitterTls(long)` | **raw** | — |
| `pingInterval(long)` | **raw** | — |
| `requestCleanupInterval(long)` | **raw** | — |
| `writeQueuePushTimeout(long)` | **raw** (runtime floors via `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT` in `WriterMessageQueue`) | — |
| `bufferSize(int)` / `reconnectBufferSize(long)` | **raw** | — |

So roughly half of OptionsBuilder's numeric setters coerce and half are raw, with no obvious rule separating them.

### PullRequestOptions.Builder — coerces in setters (your recent change)
| setter | coercion |
|---|---|
| `expiresIn(long)` | `Math.max(0, millis)` (clamp-to-0) |
| `idleHeartbeat(long)` | `Math.max(0, millis)` (clamp-to-0) |
| `minPending(long)` / `minAckPending(long)` | `< 1 ? -1 : v` (sentinel -1) |
| `batchSize(int)` | raw setter; **validated in `build()`** (`validateGtZero`) |
| `maxBytes(long)` | raw |
| `priority(int)` | raw setter; **validated in `build()`** (0–9) |
| (build also has the cross-field idle-heartbeat-vs-expiration check — needs both, so it stays in `build()`) |

This is the model you described: per-field coercion in the setters, cross-field rules in `build()`.

### `*Creator` (ConsumerCreator, Pull/PullOrdered/Limits, StreamCreator) — **consistent via normalize helpers**
All time/count setters route through `normalizeLong(v, min)` / `normalizeDuration(...)` (e.g. `ackWait`, `idleHeartbeat`, `inactiveThreshold`, `maxExpires`, `priorityTimeout`, `maxAckPending`, `maxBatch`, `maxBytes`, `maxAge`, `duplicateWindow`, `subjectDeleteMarkerTtl`). `numReplicas` uses `< 1 ? UNSET : validateNumberOfReplicas(v)`. `idleHeartbeat` additionally has explicit `DURATION_UNSET` handling. Net: the creators **always coerce**, just through a shared helper rather than inline — and they're internally consistent.

### Other builders
| class.setter | coercion |
|---|---|
| `ForceReconnectOptions.flush(long)` | `millis > 0 ? millis : 0` (clamp-to-0) |
| `ServiceBuilder.drainTimeout(long)` | `millis <= 0 ? DEFAULT_DRAIN_TIMEOUT_MILLIS : millis` (default-on-nonpositive) |
| `PublishOptions.streamTimeout(long)` | `validateGtEqZero(millis, …)` (**validate/throws**) |
| `KeyValuePurgeOptions.deleteMarkersThreshold(long)` | conditional → `DEFAULT_THRESHOLD_MILLIS` / `-1` / value (coerces) |
| `KeyValueConfigurationCreator.limitMarker(long)` | `validateDurationGtOrEqSeconds(1, …)` (**validate/throws**) |
| `JetStreamOptions.requestTimeout(long)` | **raw** (`this.requestTimeout = millis;`) |
| `FetchConsumeOptions` / `BaseConsumeOptions.expiresIn(long)` | (coerces below `MIN_EXPIRES_MILLS` in build path — see `BaseConsumeOptions`) |

## Inconsistencies worth deciding

1. **OptionsBuilder timing setters are split** — `connectionTimeout`/`socketRead`/`socketWrite` coerce, but `reconnectWait`/`reconnectJitter`/`reconnectJitterTls`/`pingInterval`/`requestCleanupInterval`/`writeQueuePushTimeout` are raw. These raw ones mostly *work* only because something downstream tolerates the value (`pingInterval <= 0` = disabled at runtime; `writeQueuePushTimeout` floored in `WriterMessageQueue`; `requestCleanupInterval`/`reconnectWait` negatives are effectively never-fire/immediate). But the **getter returns the raw value**, so two builders set "the same thing" and read back different numbers.
2. **`JetStreamOptions.requestTimeout` raw vs `PublishOptions.streamTimeout` validates** — sibling timeouts, opposite handling.
3. **validate-vs-coerce for the same shape** — `PublishOptions.streamTimeout` and KV TTLs *throw* on bad input; everything else silently coerces. Throwing is the odd one out across the surface.

## Recommendation

**Default rule: coerce in the setter so the stored field is always canonical.** Then a getter never returns a "raw, not-yet-normalized" value, and you don't have to remember per-field whether validation happened. The coercion target follows the field's unset/disabled semantics (pick one and document it on the constant):

- *"unset → use default"* fields → `v <= 0 ? DEFAULT : v` (`connectionTimeout`, `drainTimeout`, `maxControlLine`, `maxMessagesInOutgoingQueue`, and **make `pingInterval`/`requestCleanupInterval` match if they have a default**).
- *"unset → disabled/0"* fields → `Math.max(0, v)` (`expiresIn`, `idleHeartbeat`, `socketReadTimeout`, `ForceReconnectOptions.flush`).
- *"unset → -1 / OS-default"* fields → `v < 1 ? -1 : v` (`socketSoLinger`, buffers, `minPending`).
- *"always-on floor"* fields → `Math.max(MIN, v)` (`socketWriteTimeout`).
- **Creators**: already consistent — keep routing through `normalizeLong`/`normalizeDuration`.

**Reserve `validate`/throw for hard requirements** the user must fix (a name, `batchSize > 0`, a semver) — not for "I'll just clamp it" cases. Today `PublishOptions.streamTimeout` and the KV TTLs throw where their peers clamp; I'd switch those to clamp unless you specifically want them to reject (then make `JetStreamOptions.requestTimeout` reject too, for parity).

**Cross-field validation stays in `build()`** (PullRequestOptions' idle-heartbeat-≤-half-expiration), but keep the per-field clamp in the setter as you're doing — they're not mutually exclusive.

**Concrete cleanup list (the raw setters to coerce for consistency):** `OptionsBuilder.reconnectWait`, `reconnectJitter`, `reconnectJitterTls`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout`, `bufferSize`, `reconnectBufferSize`; and `JetStreamOptions.requestTimeout`. For each, the only decision is *which* target sentinel — and most already imply it via their `DEFAULT_*`/`MINIMUM_*` constant or a documented `<= 0` runtime meaning.

*(Note: `normalizeLong`/`normalizeDuration` exact behavior was read as "coerce to the field's unset/min" from call sites, not re-derived from their source — verify before relying on the precise sentinel if you act on the creator rows.)*

---

## Addendum — the `null` = "reset to default" capability lost in `Duration` → primitive conversions

**Builders only (creators excluded), focused on files changed since the last commit — every row below is a currently-changed file.** A nullable-object setter (`Duration`) let a caller pass **`null` to reset the field back to its default/unset** — a genuine capability a primitive `long` can't express. When converting `Duration`→`long`, that reset path has to be reproduced one of two ways:
- **(a) keep a boxed `@Nullable Long` param**, `null → DEFAULT` — most faithful to the old semantics; or
- **(b) primitive `long` with a documented sentinel**, `<= 0 → DEFAULT/disabled` — no boxing, reset via a magic value.

What the conversions actually did (HEAD `Duration` body → current):

| builder.setter | HEAD `null` behavior | current | reset preserved? |
|---|---|---|---|
| `KeyValuePurgeOptions.deleteMarkersThreshold` | `null → DEFAULT_THRESHOLD_MILLIS` | **`@Nullable Long millis`**, `null → DEFAULT` | ✅ **model (a)** — kept nullable |
| `ServiceBuilder.drainTimeout` | `null → DEFAULT_DRAIN_TIMEOUT` | `long`; `millis <= 0 ? DEFAULT_DRAIN_TIMEOUT_MILLIS : millis` | ✅ model (b) |
| `OptionsBuilder.connectionTimeout` | (`Duration`, defaulted) | `long`; `millis <= 0 ? DEFAULT_CONNECTION_TIMEOUT : millis` | ✅ model (b) |
| `ForceReconnectOptions.flush` | `null → null` (no flush) | `long`; `millis > 0 ? millis : 0` (`0` = no flush) | ✅ model (b), `0` = unset |
| `PublishOptions.streamTimeout` | **`null → DEFAULT_TIMEOUT`** (`validateDurationNotRequiredGtOrEqZero(timeout, DEFAULT_TIMEOUT)`) | `long`; `validateGtEqZero(millis, …)` — **stores `0` as `0`, not `DEFAULT`** | ❌ **LOST** (the example you flagged) |
| `JetStreamOptions.requestTimeout` | `null → null` field (unset → connection-timeout fallback) | **raw `long`** | ⚠ only via runtime `<= 0` fallback; no explicit reset, and the getter hands back the raw value |
| `FeatureOptions.jsRequestTimeout` | `null` (delegates to `requestTimeout`) | `long` delegate | ⚠ same as `requestTimeout` |
| `OptionsBuilder.pingInterval` / `requestCleanupInterval` / `writeQueuePushTimeout` / `reconnectWait` / `reconnectJitter` / `reconnectJitterTls` | (`Duration`) | **raw `long`** | ⚠ no explicit reset; relies on downstream tolerating the value |

### Findings
- **`PublishOptions.streamTimeout` is the clear regression.** Its peers that had a `null`→default reset (`drainTimeout`, `connectionTimeout`, `flush`) reproduced it by **coercing** `<= 0 → DEFAULT/sentinel`. `streamTimeout` instead **validates** (`validateGtEqZero`) and stores the value verbatim, so `streamTimeout(0)` yields `0`, not `DEFAULT_TIMEOUT` — once set, it can no longer be reset to the default. The field still *starts* at `DEFAULT_TIMEOUT`, so the loss only bites a caller who sets it and then wants to revert.
- **`KeyValuePurgeOptions.deleteMarkersThreshold` is the model to copy if you want the exact old behavior** — it kept a `@Nullable Long` param so `null` still means "reset to default."
- The **raw** setters (`JetStreamOptions.requestTimeout` + the six `OptionsBuilder` ones) never had an explicit reset and still don't; they lean entirely on runtime tolerating `<= 0`/negatives.

### Recommendation
This is really the coercion audit's rule applied to one special value: **"reset to default" is just coercion where the magic input (`null` or `<= 0`) maps to `DEFAULT`.** So:
1. Every timing setter should be **either** `@Nullable Long` with `null → DEFAULT` (model a) **or** primitive `long` that **coerces `<= 0 → DEFAULT/disabled`** (model b) — and never `validateGtEqZero`-then-store-raw, which both rejects nothing useful *and* drops the reset path.
2. **Fix `PublishOptions.streamTimeout`** to one of those (e.g. `millis <= 0 ? DEFAULT_TIMEOUT : millis`, or take `@Nullable Long`) so its reset matches `drainTimeout`/`connectionTimeout`.
3. Decide model (a) vs (b) once and apply it across the raw `OptionsBuilder` timing setters + `JetStreamOptions.requestTimeout`, so "how do I reset this to default?" has one answer everywhere.

*(No source files were read-modified or reverted for this audit — only `git status`/`git show`/`git diff`/`git grep` were used; the only file written is this audit.)*

---

## Status re-check (2026-06-30) — what's actually left

Re-verified every recommendation against the current tree. **Short answer: the one concrete regression is fixed, and most of the remaining "cleanup list" should NOT be done — it would remove real capabilities.**

### Done since the audit
- **`PublishOptions.streamTimeout` regression — FIXED.** Now `streamTimeout(@Nullable Long millis)` with `millis == null ? DEFAULT_TIMEOUT : validateGtEqZero(...)` and javadoc "Pass null to reset to the default." That's model (a) — the reset path is restored. ✅
- **`socketWriteTimeout` changed** from clamp-to-min to `millis < MINIMUM_SOCKET_WRITE_TIMEOUT ? 0` (i.e. below the min, incl. ≤0, = *no* write timeout). Intentional, matches the `SocketDataPortWithWriteTimeout`→`SocketDataPort` merge, where the watchdog is gated on `socketWriteTimeout > 0`. The audit's "clamp-to-min" row is now stale; the new behavior is self-consistent. ✅

### The big correction: the "coerce all 8 raw setters" recommendation overreaches
Six of the eight raw `OptionsBuilder` setters use **≤0 / negative as a load-bearing sentinel**, handled at runtime. Coercing them to a default in the setter would *delete a capability* and change behavior:
- `pingInterval` — `> 0` gate at NatsConnection.java:570; **≤0 disables pings**.
- `requestCleanupInterval` — `> 0` gate at NatsConnection.java:584; **≤0 disables the cleanup task**.
- `reconnectBufferSize` — `< 0` at NatsConnectionWriter.java:245; **<0 = unbounded**.
- `reconnectWait` — `wait < 0 ? 0` in `DefaultReconnectDelayHandler.computeWaitMillis`; **≤0 = no wait (immediate)**, already clamped at use.
- `reconnectJitter` / `reconnectJitterTls` — `jitter > 0` gate there; **≤0 = no jitter**.

So these are **correctly raw** and should stay raw. The remaining two:
- `writeQueuePushTimeout` — floored downstream by `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT` in `WriterMessageQueue`; coercing in the setter is purely cosmetic.
- `bufferSize` — the only setter with no obvious ≤0 meaning; a mild candidate for a floor/default, low value.
- `JetStreamOptions.requestTimeout` — still raw; `≤0` falls back to the connection timeout at runtime (`JetStreamImpl`). Consistent with an "≤0 = use default" reading; coercing is optional/cosmetic.

### Verdict
Nothing behavior-changing is warranted. The audit's original "coerce the raw setters for consistency" push was the wrong call for the six sentinel-bearing ones — their negative/zero values are documented runtime meanings (disable / immediate / no-jitter / unbounded), exactly the cases the audit's own caveat hinted at. The only *useful* residual work is **documentation, not coercion**: state the ≤0 sentinel meaning on those getters/setters' javadoc so callers don't have to read the runtime to learn that `pingInterval(0)` disables pings, etc. `PullRequestOptions`, the `*Creator`s, and `KeyValuePurgeOptions.deleteMarkersThreshold` are unchanged and remain consistent.
