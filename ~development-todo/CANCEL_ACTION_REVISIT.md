# Revisit: `NatsRequestCompletableFuture.CancelAction` — is `CANCEL` still earning its place?

**Status: open — revisit later.** No code change made yet; this is a parked finding.

## The question
Are all three `CancelAction` values (`CANCEL`, `REPORT`, `COMPLETE`) actually used anymore? (Defined at `core/.../utils/NatsRequestCompletableFuture.java:21`.)

## What they do
On a 503 "No Responders" status reply, `NatsConnection.deliverReply` switches on the future's action (`NatsConnection.java:1528`):
- `COMPLETE` → `f.complete(msg)` — hand the 503 status message back as the response.
- `REPORT` → `completeExceptionally(new StatusException(...))`.
- `CANCEL` (and the `default` arm) → `f.cancel(true)` — caller gets a `CancellationException`. This is the **legacy v2-jnats behavior**.

## Findings
| value | library uses it internally? | user-reachable? | tested? |
|---|---|---|---|
| `REPORT` | **Yes** — every public `request`/`requestAsync` overload + `JetStreamImpl` | yes | yes |
| `COMPLETE` | **Yes** — JetStream (`JetStreamImpl.java:200`, `JetStream.java:313`) | yes | yes |
| `CANCEL` | **No** — zero internal/production references | yes, but only via one overload | yes — only `RequestTests` |

`CANCEL` is the odd one out: the library never *selects* it. It's only reachable by a user through the single public overload `request(subject, headers, data, timeoutMillis, CancelAction)` (`NatsConnection.java:1346`). There is **no** public `requestAsync(..., CancelAction)` — those are the internal 6-arg workhorse. Otherwise `CANCEL` appears only in `RequestTests` (lines ~535–576).

## The decision to make
- **Keep all three** — `CANCEL` is a deliberately offered public option (cancel-on-No-Responders, the v2 semantic some users may rely on), it's tested, and it's the documented behavior of the `cancelAction`-taking `request` overload. The library using only `REPORT`/`COMPLETE` for its own calls is expected — that's its chosen defaults, not the whole menu.
- **OR remove `CANCEL`** — only if we decide to stop offering cancel-on-503 as a user choice. If so, also drop the public `request(..., CancelAction)` overload (a two-value enum exposed for no reason otherwise), and the `CANCEL` cases in `RequestTests`.

**Lean:** keep it, unless we're intentionally narrowing the request API. Worth a quick check of whether any v2 migration depends on the cancel-on-No-Responders behavior before removing.
