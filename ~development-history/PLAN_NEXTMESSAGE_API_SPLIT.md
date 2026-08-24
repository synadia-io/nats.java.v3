# Plan: split `nextMessage` into three explicit methods

## Motivation

Today `Subscription.nextMessage(Long timeoutMillis)` overloads three behaviors onto the sign/nullness of one boxed argument (semantics confirmed in `ConsumerMessageQueue` / `MessageQueueBase._poll`):

- `null` → poll once, **no wait**
- `<= 0` (e.g. `0`) → **wait forever**
- `> 0` → wait up to that many millis

Two problems. First, it's a live bug class, not just ugly: a computed `nextMessage(deadline - now)` silently flips from "timed wait" to "block forever" the moment it goes `<= 0` — exactly when the caller expected "return now." `nextMessage(0L)` = "forever" is also unreadable at the call site. Second, because the param is boxed `Long`, `nextMessage(1000)` won't compile (Java won't widen-then-box `int`→`long`→`Long` in one step), forcing the `1000L` literal everywhere.

Replace the magic values with three named methods. Making the timed method take a primitive `long` also kills the `L` annoyance — `nextMessage(1000)` then just widens `int`→`long`. (This is the real answer to the earlier `long`/`Long` overload question: no dual overload needed; a single primitive-`long` method does it.)

## New API (on `Subscription` / `NatsSubscription`)

```
@Nullable Message nextMessage(long timeoutMillis)               // timeoutMillis >= 1 (1 ms floor), else IllegalArgumentException
@Nullable Message nextMessage(long timeout, TimeUnit unit)      // timeout >= 1 in `unit` (1 ns floor w/ NANOSECONDS), else IllegalArgumentException
@Nullable Message nextMessageNoWait()                           // == old null: poll once, return null if nothing ready
@Nullable Message nextMessageWaitForever()                      // == old <= 0: block until a message (or unsub/poison)
```

Design decisions:
1. **Both timed overloads reject a raw `timeout < 1`** with `IllegalArgumentException` — i.e. the minimum is `1` *in the given unit*. For `nextMessage(long timeoutMillis)` that's a **1 ms** floor (`timeoutMillis < 1` → throw). For `nextMessage(long timeout, TimeUnit unit)` that's a **1 nanosecond** floor (`timeout < 1` → throw) — NOT a 1 ms floor. The nano floor is required: `JetStreamPullSubscription.fetch` calls `nextMessage(timeLeftNanos, TimeUnit.NANOSECONDS)` and `timeLeftNanos` can be as small as 1 near the deadline (the `while (... timeLeftNanos > 0)` guard keeps it `>= 1`). So validate on the **raw arg** (`timeout >= 1`), equivalently `unit.toNanos(timeout) >= 1` — do NOT validate on `toMillis`, which would wrongly reject a legitimate 1-nano call. We're removing the "0 means forever" / "null means nowait" meanings from the timed path, so don't let a `<= 0` value silently do something — reject at the boundary (also catches the negative-deadline bug, `nextMessage(deadline - now)` going `<= 0`).
2. **Keep the `TimeUnit` overload.** `nextMessage(10, TimeUnit.SECONDS)` reads better than `nextMessage(10000)`, and it's load-bearing internally: `fetch` passes nanosecond timeouts through it. The plumbing already supports it — the internal primitive `pop`/`_poll` takes `(Long, TimeUnit)`, so the overload validates on the raw arg then hands `(timeout, unit)` straight through. It does NOT reintroduce the `L` problem: both timed overloads take primitive `long`, so `nextMessage(1000)` / `nextMessage(10, SECONDS)` compile without a suffix, and the two differ by arity so there's no ambiguity. (Consistent with the house rule "only the low-level wait primitive handles the unit" — `pop`/`_poll` IS that primitive.)
3. **No bare `nextMessage()`.** A no-arg method reads as "next message with some default wait" — the exact ambiguity we're killing, and "block forever" is the most dangerous behavior to hide behind the shortest name. (Extra reason: `FetchMessageConsumer.nextMessage()` already exists with *different* semantics — "until the fetch batch completes/expires." Two same-named no-arg methods meaning different things across the hierarchy would be a trap.)
4. **`NoWait`, not `NowWait`** — matches the NATS `no_wait` pull vocabulary already in the codebase.

All four keep returning `@Nullable Message` (`@Nullable` style, not `Optional`, per house style). This is a **breaking change** — fine, we're pre-1.0 (`3.0.0-SNAPSHOT`) and already breaking APIs.

## Scope: parallel the split up the JetStream reader/consumer layer (DECIDED — in)

The same `nextMessage(@Nullable Long)` trichotomy is mirrored on parallel JetStream types; they get the **same four-method surface** as `Subscription` (`nextMessage(long)`, `nextMessage(long, TimeUnit)`, `nextMessageNoWait()`, `nextMessageWaitForever()`):

- **`JetStreamSubscription`** (abstract, `extends NatsSubscription`) — inherits the four methods; parallel to `Subscription` by construction.
- **`JetStreamReader`** (interface) — replace `nextMessage(@Nullable Long)` with the four.
- **`IterableMessageConsumer`** (interface) — replace `nextMessage(@Nullable Long)` with the four (endless-consume path; genuinely uses the nowait mode).

Implementations to update — **each wraps `sub.nextMessageXxx(...)` and must preserve its existing wrapper logic:**
- **`JetStreamPullSubscription.JetStreamReaderImpl.nextMessage`** (`:305`, implements `JetStreamReader`) — split into the four, and **keep the `track(...)` call** around each result. (My earlier note miscalled this `JetStreamPullSubscription.nextMessage`; it is the inner `JetStreamReaderImpl`.)
- **`NatsIterableMessageConsumer.nextMessage`** (`:19`) — split into the four, keeping its `JetStreamStatusCheckedException` try/catch wrapper.

`FetchMessageConsumer.nextMessage()` (already no-arg, meaning "until the batch completes/expires") stays as-is — it is a different concept, not part of this parallel.

## Migration audit — internal callers (`sub.nextMessage(...)` in main)

| Site | Passes | Maps to |
|------|--------|---------|
| `AbstractBucketFeature.java:108` | `long timeoutMillis = js.getTimeout()` | `nextMessage(long)` — ✓ confirmed `>= 1`: `JetStreamImpl:50` sets it to `getConnectionTimeout()` (positive default) unless `requestTimeout > 0` |
| `NatsNextConsumer.java:45` | `maxWaitMillis` (long) | `nextMessage(long)` — ✓ confirmed `>= 1`: `NatsConsumerContext.next()` throws if `maxWait < MIN_EXPIRES_MILLS` |
| `ObjectStore.java:256,277` | `jsm.getTimeout()` | `nextMessage(long)` — ✓ confirmed `>= 1`: `JetStreamManagement extends JetStreamImpl`, same `getTimeout()` as above |
| `JetStreamPullSubscription.java:140` (`fetch`) | `nextMessage(timeLeftNanos, TimeUnit.NANOSECONDS)`; loop guard keeps `timeLeftNanos >= 1` | `nextMessage(long, TimeUnit)` — **the reason the nano floor exists** |
| `JetStreamPullSubscription.java:178` (`drainAlreadyBuffered`) | `nextMessage(null, MILLISECONDS)` (comment: "try once, no wait") | `nextMessageNoWait()` |
| `JetStreamPullSubscription.java:305` (`JetStreamReaderImpl`) | `@Nullable Long` passthrough, wrapped in `track(...)` | split into four, keep `track(...)` |
| `NatsIterableMessageConsumer.java:19,21` | `@Nullable Long` passthrough, wrapped in status try/catch | split into four, keep wrapper |

All three plain-millis origins are now confirmed `>= 1` (✓ in the table) — no `<= 0` slips past the new floor. The `fetch` nano site is loop-guarded `> 0`. The two passthrough impls (`JetStreamReaderImpl`, `NatsIterableMessageConsumer`) are the Phase-3 work.

## Migration — tests (bulk, mostly mechanical)

144 `.nextMessage(...)` call sites across 16 test files. Breakdown:
- **Positive literals** (`nextMessage(1000L)`, `500L`, `100L`, …) — the large majority: drop the `L` → `nextMessage(1000)`. Pure mechanical.
- **`nextMessage(null)`** — 4 sites (`SubscriberTests` 162/174/290, `JetStreamPushTests` 106) → `nextMessageNoWait()`.
- **`nextMessage(0L)`** — 2 sites (`SubscriberTests:105`, `JetStreamPushTests:103`) → `nextMessageWaitForever()`.
- **No-arg `nextMessage()`** — 25 sites; these are `FetchMessageConsumer.nextMessage()`, which is **not** changing (stays "until batch completes/expires"). Leave them alone. (Watch for false positives: confirm each `.nextMessage()` receiver is a `FetchMessageConsumer`, not a reader whose signature we changed.)

## Doc references to update

`@link Subscription#nextMessage(Long)` javadoc refs in `Consumer.java:12`, `Subscription.java:11`, `NatsConnection.java:778/1103/1124`, and the `nc.subscribe("hello")...nextMessage(0L)` example in `Subscription.java:119`.

## Phasing

1. **Core API** — on `Subscription` + `NatsSubscription`: retype `nextMessage(Long)`→`nextMessage(long)` (1 ms floor) and `nextMessage(Long, TimeUnit)`→`nextMessage(long, TimeUnit)` (raw `>= 1`, i.e. 1 ns floor), add the guards, and add `nextMessageNoWait()` + `nextMessageWaitForever()`. (The `null`/`<=0` behaviors move from the timed params to the two named methods.) Update core doc refs.
2. **Internal callers** — migrate the sites in the audit table; confirm each plain-millis timeout origin is `>= 1`.
3. **JetStream reader/consumer parallel** (decided, in) — `JetStreamReader` + `IterableMessageConsumer` interfaces get the four methods; `JetStreamReaderImpl` (keep `track`) and `NatsIterableMessageConsumer` (keep status wrapper) implement them; `JetStreamSubscription` inherits. `FetchMessageConsumer.nextMessage()` untouched.
4. **Tests** — mechanical literal migration + the 6 null/0 special cases; leave `FetchMessageConsumer.nextMessage()` calls alone.
