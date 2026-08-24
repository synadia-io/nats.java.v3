# Poll / nextMessage — nanosecond-behavior changes (RESOLVED)

**Status: FIXED — as a clean millis chain. The original "precision loss" worry is moot.** Sub-millisecond timeouts are impractical in this application: every timeout reflects a network request or processing data that arrived across the network, so sub-ms resolution has no physical meaning (confirmed by the maintainers after discussion). Millis is therefore the *correct* unit, not a compromise — the ≤ ~1 ms granularity on a final partial wait is not a meaningful loss.

Every method in the chain takes **`long …Millis`** and **no caller converts to nanos**. The conversion is handled only at the lowest level, by the method that owns the wait: `_poll` hands the unit straight to `queue.poll(timeoutMillis, TimeUnit.MILLISECONDS)` (no arithmetic). Deadline-tracking loops keep their countdown in millis. This is the design the maintainers required (callers pass millis; the place that does the work converts — scattering `* NANOS_PER_MILLI` across callers is error-prone). **C** and **D** sentinels are kept (`< 0` immediate, `0` forever, `> 0` wait). This document is retained only as the record of the journey; the sub-ms-vs-millis question is closed.

## The fix (everything is millis; only the wait primitive handles the unit)

The whole chain — `nextMessage → nextMessageInternal → pop → _poll`, `accumulate → _poll`, JetStream `_nextUnmanaged`/`_fetch`/`_iterate`, `NatsConnection.waitWhile`, `Discovery` — takes **`long …Millis`**. No call site multiplies by `NANOS_PER_MILLI`.
- `_poll(long timeoutMillis)` → `queue.poll(timeoutMillis, TimeUnit.MILLISECONDS)`. The JDK does the unit; `_poll` does no arithmetic.
- `pop`, `accumulate`, `nextMessageInternal`, `nextMessage` are plain millis pass-throughs. Dispatchers call `pop(waitForMessageMillis)`; the writer calls `accumulate(…, outgoingTimeoutMillis)` — no conversion.
- JetStream `_nextUnmanaged`/`_fetch` track their countdown in millis: `timeLeftMillis = timeoutMillis - (nanoTime() - start) / NANOS_PER_MILLI` (converting **elapsed** nanos→millis for the countdown — they never pass nanos onward). `while (timeLeftMillis > 0)` guarantees ≥ 1, so the old `Math.max(1, …)` floor hack is gone. `_iterate` is millis too (its `expiresIn` wire field is built with `Duration.ofMillis(maxWaitMillis)`).
- Request chain (same principle, separate file): the `request`/`requestAsync` workhorses take `long timeoutMillis`; the blocking get is `incoming.get(getMillis, TimeUnit.MILLISECONDS)`; `NatsRequestCompletableFuture`'s constructor takes millis and does the single `* NANOS_PER_MILLI` itself (it owns the `nanoTime`-based `timeOutAfterNanoTime`). `Discovery` tracks millis and calls `nextMessage(timeLeftMillis)`.

## Original problem (for the record)

When starting on `MessageQueueBase` you said: *"Don't break the fact that it itself wants nanos, that is a timing loop. There is a constant `NatsConstants.NANOS_PER_MILLI` to use for conversion."* The first pass kept the internal `queue.poll(…, NANOSECONDS)` loop but changed `_poll`'s **input** to `long millis`, which floored several callers that were tracking remaining time in nanoseconds. The sections below catalog each, now annotated with its resolution.

---

## Summary of behavior changes

| # | Where | Pre-existing (nanos) behavior | New behavior | Impact |
|---|---|---|---|---|
| A | `MessageQueueBase._poll` input | took `Duration` → `queue.poll(timeout.toNanos(), NANOSECONDS)` | takes `long millis` → `queue.poll(millis * NANOS_PER_MILLI, NANOSECONDS)` | Smallest expressible wait is now **1 ms**; sub-ms waits impossible to request. Internal poll is still nanos. |
| B | JetStream fetch/next loops + service Discovery | tracked remaining time in **nanos**, passed `Duration.ofNanos(timeLeftNanos)` to poll → exact partial wait | pass `Math.max(1, timeLeftNanos / NANOS_PER_MILLI)` ms | Final partial wait is **floored to whole ms** and clamped to a **1 ms minimum** → a deadline can **overshoot by up to ~1 ms**. |
| C | `nextMessage(long)` negative arg | negative → **wait forever** | negative → **return immediately (poll once)** | Semantics of a negative timeout **flipped**. |
| D | `_poll` sentinel mapping | `null` = poll once; `Duration.ZERO`/negative = forever | `< 0` = poll once; `0` = forever | "poll once" moved from `null` to `< 0`. Internal, but the contract changed. |

Changes **A** and **B** were the nanosecond-precision losses — **now fixed** (A; B1, B2; B3 stays millis by design). **C** and **D** are sentinel/contract changes that rode along with the signature change and are **kept** as the intended public contract. The table rows describe the intermediate millis state; see each section for the resolution.

---

## A. `_poll` input resolution: nanos → millis  — **RESOLVED**

**RESOLVED:** `_poll` now takes `long timeoutNanos` and calls `queue.poll(timeoutNanos, NANOSECONDS)` directly. Sub-millisecond waits are expressible again; the smallest wait is 1 ns. The signature below is the (now-superseded) intermediate millis form.

**File:** `core/src/main/java/io/synadia/client/impl/MessageQueueBase.java`

**Before (HEAD):**
```java
NatsMessage _poll(Duration timeout) throws InterruptedException {
    NatsMessage msg = null;
    if (timeout == null || this.isDraining()) {        // try immediately
        msg = queue.poll();
    }
    else {
        long nanos = timeout.toNanos();
        if (nanos < 1) {                               // ZERO or negative => forever
            while (isRunning()) {
                msg = queue.poll(3650, TimeUnit.DAYS);
                if (msg != null) break;
            }
        }
        else {
            msg = queue.poll(nanos, TimeUnit.NANOSECONDS);   // <-- nanosecond-precise wait
        }
    }
    return msg == null || msg == POISON_PILL ? null : msg;
}
```

**After (current):**
```java
NatsMessage _poll(long timeoutMillis) throws InterruptedException {
    NatsMessage msg = null;
    if (timeoutMillis < 0 || this.isDraining()) {      // try immediately
        msg = queue.poll();
    }
    else if (timeoutMillis == 0) {                     // forever
        while (isRunning()) {
            msg = queue.poll(3650, TimeUnit.DAYS);
            if (msg != null) break;
        }
    }
    else {
        msg = queue.poll(timeoutMillis * NANOS_PER_MILLI, TimeUnit.NANOSECONDS);  // still nanos, but millis-granular input
    }
    return msg == null || msg == POISON_PILL ? null : msg;
}
```

**The behavior change:** the internal `queue.poll(…, NANOSECONDS)` is still nanosecond-based (as you asked), but because the *parameter* is now `long millis`, the smallest non-immediate wait that can be expressed is **1 ms** (`1 * NANOS_PER_MILLI`). Previously a caller could pass `Duration.ofNanos(500_000)` (0.5 ms) and get a genuine sub-millisecond wait. That is no longer possible.

---

## B. Nanosecond deadline loops now floor to whole ms (the important one)

**RESOLVED for B1 and B2:** `_nextUnmanaged`/`_fetch` now track their countdown in **millis** (`timeLeftMillis = timeoutMillis - (nanoTime()-start)/NANOS_PER_MILLI`) and call `nextMessageInternal(timeLeftMillis)`. The `Math.max(1, …)` floor hack is gone because `while (timeLeftMillis > 0)` guarantees ≥ 1. Waits are millis-granular (≤ ~1 ms overshoot on the final partial wait), which is correct here — sub-ms timeouts are impractical for network-bound waits (see header). **B3 (`Discovery`) likewise millis** (see B3).

Three pre-existing loops tracked their **remaining time in nanoseconds** and passed that exact value into the poll. They now divide to millis with a `Math.max(1, …)` floor.

### B1. `JetStreamSubscription._nextUnmanaged`
**File:** `jetstream/src/main/java/io/synadia/client/impl/JetStreamSubscription.java`

- **Before (HEAD):** `Message msg = nextMessageInternal( Duration.ofNanos(timeLeftNanos) );`
- **After (current, ~line 146):** `Message msg = nextMessageInternal( Math.max(1, timeLeftNanos / NANOS_PER_MILLI) );`

The outer loop still computes `timeLeftNanos = timeoutNanos - (nanoTime() - start)` in nanos and loops while `timeLeftNanos > 0`, so the *overall* deadline is still nanosecond-checked. But each individual poll wait is now:
- **floored** to whole ms (integer division truncates — e.g. `1_900_000` ns → `1` ms), and
- **clamped to 1 ms minimum** via `Math.max(1, …)`. When less than 1 ms remains (`0 < timeLeftNanos < 1_000_000`), integer division yields `0` (which would mean "forever" under the new `_poll`), so I forced it to `1` ms. **Consequence:** on the final sliver of the deadline the code now waits a full 1 ms instead of the sub-ms remainder, so the total can **overshoot the requested timeout by up to ~1 ms.**

### B2. `JetStreamPullSubscription` fetch loop (`_fetch`)
**File:** `jetstream/src/main/java/io/synadia/client/impl/JetStreamPullSubscription.java`

- **Before (HEAD, ~line 187):** `Message msg = nextMessageInternal( Duration.ofNanos(timeLeftNanos) );` (with `long maxWaitNanos = maxWaitMillis * 1_000_000; long timeLeftNanos = maxWaitNanos;`)
- **After (current, line 187):** `Message msg = nextMessageInternal( Math.max(1, timeLeftNanos / NANOS_PER_MILLI) );`

Same flooring + 1 ms-minimum overshoot as B1. (Note: the `_iterate` path and the `expiresIn(Duration.ofNanos(maxWaitNanos))` pull-request field are **unchanged** — `expiresIn` is a wire-nanos field and was not touched.)

### B3. `Discovery` (service module)
**File:** `service/src/main/java/io/synadia/service/Discovery.java`

- **Before (HEAD, line 200):** `Message msg = sub.nextMessage(Duration.ofNanos(timeLeft));` (with `timeLeft` tracked in nanos against `maxTimeNanos`)
- **After (current, line 200):** `Message msg = sub.nextMessage(Math.max(1, timeLeft / NANOS_PER_MILLI));`

Same flooring + 1 ms-minimum overshoot. The discovery wait-loop still tracks `maxTimeNanos`/`timeLeft` in nanos for the outer deadline; only the per-poll wait is millis-floored.

**Decision: B3 cleaned to a pure millis loop.** `Discovery` reaches the subscription only through the **public** `Subscription.nextMessage(long timeoutMillis)` API (millis; the `nextMessage(Duration)` overload was removed), so it's millis-granular by design — fine for service discovery (seconds-scale waits, ≤1 ms overshoot). It now tracks `timeLeftMillis` directly (field is `maxTimeMillis`, request uses `Duration.ofMillis(maxTimeMillis)`), and `while (resultsLeft > 0 && timeLeftMillis > 0)` guarantees the value passed is ≥ 1 — so the `Math.max(1, …)` clamp was **removed** (no longer needed; nothing converts to nanos here anymore).

---

## C. `nextMessage(long)` negative-argument semantics flipped

**Files:** `core/.../NatsSubscription.java`, `jetstream/.../JetStreamSubscription.java`

**Before (HEAD):**
- `NatsSubscription.nextMessage(long timeoutMillis)` → `nextMessageInternal(Duration.ofMillis(timeoutMillis))`. A negative `timeoutMillis` becomes a negative `Duration`, whose `toNanos() < 1`, which in `_poll` means **wait forever**. (`nextMessage(0)` also = forever.)
- `JetStreamSubscription.nextMessage(long timeoutMillis)`: `if (timeoutMillis <= 0) return _nextUnmanagedWaitForever(...)` — negative = **wait forever**.

**After (current):**
- Negative `timeoutMillis` → **return immediately (poll once)** (`< 0` is the new "poll once" sentinel); `0` → wait forever.

**Consequence:** any pre-existing caller that passed a negative value to `nextMessage(long)` expecting "wait forever" now gets "return immediately." (`nextMessage(0)` = forever is unchanged.) This also became the public contract documented on `Subscription.nextMessage(long)` ("negative returns immediately").

---

## D. `_poll` sentinel remap (`null` → `< 0` for poll-once)

The "poll once, return whatever is buffered" path moved from `Duration timeout == null` to `timeoutMillis < 0`. Call sites that previously passed `null` (e.g. `_nextUnmanagedNoWait` → `nextMessageInternal(null)`, `pop(null)` in tests, `JetStreamPullSubscription` no-wait fetch) now pass `-1`. Functionally equivalent **where the callers were updated**, but the sentinel value/contract changed, so any external or missed caller relying on `null` no longer compiles (good) — and any caller relying on `0`/`ZERO` for "poll once" would now get "forever" (the two were already distinct before, so this is only a risk if some caller conflated them).

---

## Things that did NOT change (verified, for completeness)

- The internal `queue.poll(…, TimeUnit.NANOSECONDS)` wait is **still nanos** — the timing loop itself was preserved as instructed.
- `WriterMessageQueue` push timeout: constructor changed `Duration` → `long millis`, but its source (`options.getWriteQueuePushTimeout()`) was already millisecond-resolution, so no precision was lost there.
- Dispatcher `waitForMessage` (5 min) and writer `outgoing`(2 min)/`reconnect`(1 ms) timeouts: same effective values after conversion.
- `_iterate`'s `expiresIn(Duration.ofNanos(maxWaitNanos))` pull-request field — untouched (wire-nanos).

---

## Other Duration→millis conversions this session (lower risk, for completeness)

These are **not** internal nanos deadline-loops like the poll chain — they are API-surface `Duration` values (user-set timeouts) that became `long millis` as part of the deliberate, separately-documented timeout migrations. The internal machinery they feed is still nanos. Listed here so nothing nanos-adjacent is hidden:

- **`SocketDataPortWithWriteTimeout` (socket write timeout).** Before: `writeTimeoutNanos = options.getSocketWriteTimeout().toNanos()` (Duration → nanos). After: `writeTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(millis)`. The configured value was a `Duration` (could be nanos-precise) and is now `long` millis; `writeTimeoutNanos` itself is still nanos. Minute-scale value; part of the Options millis migration (`MIGRATION_GUIDE_OPTIONS.md` §7).
- **`JetStreamImpl.makeRequest*` (JetStream API/request timeout). — RESOLVED, no allocation, no caller conversion.** The `Duration.ofMillis(timeoutMillis)` wrapper is gone. `conn.request`/`requestAsync` (the 6-arg workhorses) take **`long timeoutMillis`**; `makeRequest*` just passes the `timeoutMillis` it already has: `conn.request(subject, headers, data, timeoutMillis, cancelAction, conn.isForceFlushOnRequest())`. The blocking get is `incoming.get(getMillis, TimeUnit.MILLISECONDS)` — no arithmetic. The single `* NANOS_PER_MILLI` lives inside `NatsRequestCompletableFuture`'s constructor (it owns the `nanoTime`-based `timeOutAfterNanoTime`), which now takes millis. `-1` = "not specified" so each consumer applies its own default (connection-timeout for the get, request-cleanup-interval for the future). Public `Duration` overloads convert `Duration → millis` via `timeout == null ? -1 : Math.max(0, timeout.toMillis())` (sub-ms Duration precision truncated — request timeouts are millis-granular). No `Duration` is allocated on the JS request path.
- **`NatsRequestCompletableFuture` — now takes `long timeoutNanos`.** Its constructor was changed from `Duration` to `long timeoutNanos` and computes `timeOutAfterNanoTime = nanoTime() + HYDRATION_TIME + timeoutNanos`. The `request`/`requestAsync` workhorses pass it the resolved nanos (`< 0` → the request-cleanup-interval default). Full nanosecond resolution preserved.

So all three pre-existing **internal nanosecond deadline loops** from section **B** are now resolved except service `Discovery` (B3), which stays millis by design (public-API boundary). The JetStream loops (`_nextUnmanaged`, `_fetch`) are nanos-exact again.

## Decision points — resolved

1. **~1 ms overshoot (B):** resolved for the JetStream loops — `_nextUnmanaged`/`_fetch` now use the nanos-precise internal poll (`nextMessageInternal`/`pop`/`_poll` carry nanos), so only the *public millis API* is millis-granular. `Discovery` (B3) keeps the millis floor by design (it only has the public millis API; ≤1 ms is negligible for service discovery).
2. **Negative `nextMessage(long)` (C):** kept as "return immediately" — this is the documented public contract (`< 0` immediate, `0` forever, `> 0` wait), preserved through the millis→nanos multiply.
3. **The 1 ms minimum (`Math.max(1, …)`):** removed everywhere, including `Discovery`. All deadline loops now track a millis countdown guarded by `while (timeLeftMillis > 0)`, which guarantees the value passed is ≥ 1, so the `0` = "forever" trap can't occur.
