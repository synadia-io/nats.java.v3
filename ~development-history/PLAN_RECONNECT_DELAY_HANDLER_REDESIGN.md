# Plan — ReconnectDelayHandler redesign (v3)

Goal: redesign `ReconnectDelayHandler` so the handler is the single point of policy for reconnect timing, receives full context (round + options + lame-duck state), and supports a `LameDuckAware` mode that delays before round 1 only when the server has explicitly signaled lame duck. Folds the work-in-progress `ReconnectDelayBehavior` (BeforeSubsequentRounds / BeforeAllRounds) into this richer model.

## Goals

1. **One handler, called unconditionally before every round.** The connection's reconnect loop stops branching on a behavior enum; it just asks the handler "how long should I wait before round N?"
2. **Handler receives full context; returns `long` ms.** Today's interface takes just `long totalTries` and returns `Duration`; the new one takes the round number, the connection's `Options`, and a `secure` flag for TLS-vs-non-TLS jitter, and returns a `long` millisecond delay. Sub-millisecond resolution doesn't matter at this granularity — switching to a primitive return drops the `Duration` allocation per call, the null-check at the call site, and the `Duration`/`Nanos` math in `NatsConnection`.
3. **Connection owns the LDM signal; handler receives it as a parameter.** The connection is the only thing that observes `INFO {ldm:true}`, so it's the natural owner. A `volatile boolean lameDuckTriggered` lives on `NatsConnection`; it's set when LDM arrives, consumed and cleared on the round-1 call to the handler, and passed to `getWaitTime(...)` as a fourth parameter. The handler stays stateless, which means it's safe to share across multiple connections — `DefaultReconnectDelayHandler` exposes a singleton `INSTANCE`. The interface stays SAM (one abstract method); lambdas work for stateless custom handlers.
4. **Rename `totalTries` → `round`, 1-based.** `round = 1` is the first round after disconnect; `round = 2+` are full-pool-cycle wrap-arounds. Cleaner semantics than today's "totalTries (which is actually rounds)" naming.
5. **Recommend `LameDuckAware` as the new default.** Strictly safer than today's BeforeSubsequentRounds (delays only when the server has explicitly told us to), without the regression-on-every-failover cost of BeforeAllRounds. See the cross-client survey at `RECONNECT_DELAY_CLIENT_SURVEY.md` for context — this gives JNats a unique-but-defensible default that hits the "polite on LDM, fast on transient failures" sweet spot.

## Update the existing `ReconnectDelayHandler` interface

File: `core/src/main/java/io/synadia/client/ReconnectDelayHandler.java`. This is an **in-place modification** of the existing interface, not a new one. The interface stays single-abstract-method (so lambdas continue to work) — only the abstract method's signature changes (more params, primitive return). A `static` helper for the standard wait+jitter math is added alongside. The handler is stateless by design: the LDM signal is tracked on the connection and passed in as a parameter, so the same handler instance is safe to share across many connections. Standard behaviour for the default path lives in the new concrete (stateless, singleton-exposing) `DefaultReconnectDelayHandler` class (see next section).

### Today

```java
public interface ReconnectDelayHandler {
    Duration getWaitTime(long totalTries);
}
```

### After

```java
package io.synadia.client;

/**
 * Drives the reconnect-attempt cadence. The connection invokes this handler
 * before every reconnect round, including round 1 (the first round after a
 * disconnect). Implementations decide how long to wait by inspecting the
 * round number, the connection {@link Options}, and the lame-duck flag the
 * connection passes in (the connection tracks the LDM signal itself and
 * consumes it on the round-1 call).
 *
 * <p>Implementations are expected to be stateless — all relevant inputs
 * come in as parameters, so there's nothing to remember between calls.
 * One handler instance can be safely shared across multiple connections.
 *
 * <p>For the standard behaviour, use
 * {@link io.synadia.client.impl.DefaultReconnectDelayHandler#INSTANCE}
 * (which is what {@link OptionsBuilder} falls back to when no handler is
 * supplied). For a custom strategy, implement this interface — a lambda
 * works since the interface is single-abstract-method.
 */
public interface ReconnectDelayHandler {

    /**
     * Compute the wait before the given reconnect round, in milliseconds.
     *
     * @param round              1-based round number; {@code 1} is the first round
     *                           after a disconnect, {@code 2+} are pool wrap-arounds.
     * @param options            the immutable {@link Options} for the connection.
     * @param secure             whether the active server pool contains a TLS URL.
     * @param lameDuckTriggered  whether this reconnect campaign was triggered by a
     *                           server-sent lame-duck signal ({@code INFO {"ldm": true}}).
     *                           The connection consumes this flag on the {@code round == 1}
     *                           call, so subsequent rounds within the same campaign always
     *                           see {@code false}.
     * @return the duration to wait before this round, in milliseconds.
     *         A non-positive value means no wait.
     */
    long getWaitTime(long round, Options options, boolean secure, boolean lameDuckTriggered);
}
```

### What changed, item by item

1. **`Duration getWaitTime(long totalTries)` → `long getWaitTime(long round, Options options, boolean secure, boolean lameDuckTriggered)`** — still single-abstract-method, still SAM.
   - Parameter renamed `totalTries` → `round` (full-pool cycles, not individual attempts). Same `long` type.
   - Three new parameters carry all the context the handler needs: the `Options` (so handlers can read configured wait/jitter values rather than carrying their own copy), a `secure` flag (TLS vs non-TLS jitter selection without poking at connection internals), and `lameDuckTriggered` (the LDM signal, tracked by the connection and consumed on round 1).
   - **Return type changed from `Duration` to `long` milliseconds.** Sub-millisecond resolution doesn't matter for reconnect timing. A primitive return eliminates the `Duration` allocation, the null-check at the call site, and the `toNanos()` conversion in `NatsConnection`. Method name stays `getWaitTime` (no unit suffix); the millisecond unit is documented in the javadoc.

The interface holds only the contract — no helpers, no callbacks. The standard wait+jitter calculation lives as a `public static` helper on `DefaultReconnectDelayHandler` (see next section). Custom handlers that want to reuse it call `DefaultReconnectDelayHandler.computeWaitMillis(options, secure)`.

There is no `onLameDuck` callback. The connection tracks `lameDuckTriggered` itself and passes it as a parameter on every call — so the handler stays stateless and shareable.

Existing user-supplied handlers update mechanically: same `long` round, plus three new parameters, plus `Duration` → `long` ms. A test lambda like `l -> Duration.ofSeconds(l)` becomes `(round, opts, secure, ld) -> round * 1000`. The interface stays SAM so lambdas continue to compile.

## Default implementation — `DefaultReconnectDelayHandler` (new concrete class)

New file: `core/src/main/java/io/synadia/client/impl/DefaultReconnectDelayHandler.java`. Stateless — it just branches on `Options.reconnectDelayBehavior()` and the `lameDuckTriggered` parameter the connection passes in. Exposes a singleton `INSTANCE` that `OptionsBuilder.build()` falls back to when the user hasn't supplied a custom handler.

```java
package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.ReconnectDelayBehavior;
import io.synadia.client.ReconnectDelayHandler;

import java.util.concurrent.ThreadLocalRandom;

public final class DefaultReconnectDelayHandler implements ReconnectDelayHandler {

    /** Stateless singleton, safe to share across all connections. */
    public static final DefaultReconnectDelayHandler INSTANCE = new DefaultReconnectDelayHandler();

    @Override
    public long getWaitTime(long round, Options options, boolean secure, boolean lameDuckTriggered) {
        boolean firstRound = round <= 1;

        switch (options.reconnectDelayBehavior()) {
            case BeforeAllRounds:
                return computeWaitMillis(options, secure);

            case LameDuckAware:
                if (firstRound && !lameDuckTriggered) {
                    return 0L;
                }
                return computeWaitMillis(options, secure);

            case BeforeSubsequentRounds:
            default:
                return firstRound ? 0L : computeWaitMillis(options, secure);
        }
    }

    /**
     * Standard wait calculation in milliseconds: {@link Options#getReconnectWait()}
     * plus a uniform random jitter from {@link Options#getReconnectJitter()}
     * (or {@link Options#getReconnectJitterTls()} when {@code secure} is true).
     * Negative results are clamped to zero.
     *
     * <p>Exposed as {@code public static} so custom {@link ReconnectDelayHandler}
     * implementations can reuse the math without copy-pasting.
     */
    public static long computeWaitMillis(Options options, boolean secure) {
        long wait = options.getReconnectWait();
        long jitter = secure ? options.getReconnectJitterTls() : options.getReconnectJitter();
        if (jitter > 0) {
            wait += ThreadLocalRandom.current().nextLong(jitter);
        }
        return wait < 0 ? 0L : wait;
    }
}
```

No fields, no `volatile`, no consume-on-round-1 bookkeeping — the connection owns that. Because the handler is stateless, `INSTANCE` is genuinely shareable: many connections can hold the same reference and there are no race conditions on a per-campaign flag.

A user who wants a custom strategy implements the interface as a lambda (`(round, opts, secure, ld) -> ...`) or a class, and can call `DefaultReconnectDelayHandler.computeWaitMillis(options, secure)` if they want the standard wait+jitter calculation as a building block.

## `ReconnectDelayBehavior` — add `LameDuckAware`, make it the default

File: `core/src/main/java/io/synadia/client/ReconnectDelayBehavior.java`.

```java
public enum ReconnectDelayBehavior {
    /**
     * Historical behaviour. No wait before round 1; wait between rounds.
     * Use when you want the fastest possible failover and your operators
     * never use NATS lame duck mode.
     */
    BeforeSubsequentRounds,

    /**
     * Always wait, including before round 1. Use when you prefer to back off
     * before any reconnect attempt — for example to avoid thundering-herd on
     * a known-unhealthy cluster.
     */
    BeforeAllRounds,

    /**
     * Wait before round 1 only when the server has signalled lame duck mode
     * (via {@code INFO {"ldm": true}}). Otherwise behave as
     * {@link #BeforeSubsequentRounds}. Wait between rounds in either case.
     *
     * <p>This is the default. It preserves fast cluster-failover semantics
     * for transient disconnects while honouring the operator's explicit
     * "I'm going down, please back off" signal.
     */
    LameDuckAware;

    public static ReconnectDelayBehavior get(String value) {
        if (value != null) {
            for (ReconnectDelayBehavior rdb : ReconnectDelayBehavior.values()) {
                if (rdb.name().equalsIgnoreCase(value)) {
                    return rdb;
                }
            }
        }
        return LameDuckAware;   // CHANGED — was BeforeSubsequentRounds
    }
}
```

Update the field default in `OptionsBuilder.java:106`:
```java
ReconnectDelayBehavior reconnectDelayBehavior = ReconnectDelayBehavior.LameDuckAware;
```
And in the null-reset path of `OptionsBuilder.reconnectDelayBehavior(...)` at line ~928:
```java
this.reconnectDelayBehavior = reconnectDelayBehavior == null
    ? ReconnectDelayBehavior.LameDuckAware
    : reconnectDelayBehavior;
```

## `Options` — always carry a handler

Today: `options.getReconnectDelayHandler()` can be `null`; `NatsConnection` then inlines the wait math. With the new design the handler *is* the policy, so it must always be present.

Wire-up — done in `OptionsBuilder.build()`, not in `Options`'s constructor or at field declaration. This matches the convention already applied to `tokenSupplier` and `inboxPrefix`: when the default is a runtime instance (rather than a simple constant), resolve it in `build()` and leave the field as an explicit `null` sentinel until then.
- `OptionsBuilder.java:105` field stays as `ReconnectDelayHandler reconnectDelayHandler = null;` (explicit null per §R2 of the audit).
- In `build()`, before `new Options(this)`:
  ```java
  if (reconnectDelayHandler == null) {
      reconnectDelayHandler = DefaultReconnectDelayHandler.INSTANCE;
  }
  ```
- `Options(OptionsBuilder b)` stays a straight copy — `this.reconnectDelayHandler = b.reconnectDelayHandler;`. No fallback inside the Options constructor.
- Update `Options.getReconnectDelayHandler()` Javadoc to say "never null."

Because `DefaultReconnectDelayHandler.INSTANCE` is stateless, sharing one `Options` across multiple `Nats.connect(...)` calls is perfectly safe — the same handler reference is reused, but each connection tracks its own `lameDuckTriggered` flag locally. Users who supply a custom handler are equally safe as long as their handler is stateless; a stateful custom handler shared across connections is the user's call to make.

## `NatsConnection` wiring

### Reconnect loop simplification

File: `core/src/main/java/io/synadia/client/impl/NatsConnection.java`, lines 420–434.

Replace the current behavior-enum branching:
```java
while ((cur = serverPool.nextServer()) != null) {
    if (first == null) {
        first = cur;
        if (options.reconnectDelayBehavior() == ReconnectDelayBehavior.BeforeAllRounds) {
            invokeReconnectDelayHandler(0);
        }
    }
    else if (first.equals(cur)) {
        invokeReconnectDelayHandler(++totalRounds);
    }
```
with:
```java
long round = 0;
while ((cur = serverPool.nextServer()) != null) {
    if (first == null) {
        first = cur;
        invokeReconnectDelayHandler(++round);   // always; round becomes 1
    }
    else if (first.equals(cur)) {
        invokeReconnectDelayHandler(++round);   // round 2, 3, ...
    }
```
The behavior decision moves from the connection into the handler. Existing handler users still work — for a custom handler, round=1 is just a new value it'll see that it never saw before. (See migration section.)

### Track the LDM flag on the connection

Add a field on `NatsConnection` next to the other connection-state fields:
```java
private volatile boolean lameDuckTriggered = false;
```
At line 1901, when the server signals LDM, set the flag:
```java
if (serverInfo.isLameDuckMode()) {
    processConnectionEvent(ConnectionEvents.LAME_DUCK, uriDetail(currentServer));
    this.lameDuckTriggered = true;
}
```

The handler is no longer notified directly — it learns about LDM via the parameter the connection passes on its next `getWaitTime` call. This keeps the handler stateless and shareable.

### Rewrite `invokeReconnectDelayHandler`

Lines 2322–2355. Replace the null-check + inlined math with a single call into the handler. Consume the LDM flag on the round-1 call. Native `long` ms means we can drop the `Duration` conversion entirely and use `TimeUnit.MILLISECONDS` on the waiter:

```java
protected void invokeReconnectDelayHandler(long round) {
    ReconnectDelayHandler handler = options.getReconnectDelayHandler();   // never null
    boolean secure = serverPool.hasSecureServer();

    boolean ld = false;
    if (round <= 1) {
        ld = this.lameDuckTriggered;
        this.lameDuckTriggered = false;   // consume on the campaign boundary
    }

    long currentWaitMillis = handler.getWaitTime(round, options, secure, ld);
    if (currentWaitMillis <= 0) {
        return;
    }

    this.reconnectWaiter = new CompletableFuture<>();
    long start = NatsSystemClock.nanoTime();
    while (currentWaitMillis > 0 && !isDisconnectingOrClosed() && !isConnected() && !this.reconnectWaiter.isDone()) {
        try {
            this.reconnectWaiter.get(currentWaitMillis, TimeUnit.MILLISECONDS);
        } catch (Exception exp) {
            // ignore, try to loop again
        }
        long elapsedMillis = (NatsSystemClock.nanoTime() - start) / 1_000_000L;
        currentWaitMillis -= elapsedMillis;
        start = NatsSystemClock.nanoTime();
    }
}
```

(The clock-tracking is still in nanos for accuracy of `elapsedMillis`, but everything user-facing is ms.)

## Tests

File: `core/src/test/java/io/synadia/client/OptionsTests.java` and a new `core/src/test/java/io/synadia/client/ReconnectDelayHandlerTests.java` (note: under the `io.synadia.client` test package, not `impl`, since the interface lives in the API package).

### Existing `testReconnectDelayBehavior` updates
- Default is now `LameDuckAware` — flip the assertion at the top of the test.
- `null` reset now resets to `LameDuckAware`.
- Property test: parsing `"LameDuckAware"` (mixed case) returns the new value; unknown values default to `LameDuckAware`.
- Static factory: `ReconnectDelayBehavior.get(null)` → `LameDuckAware`.

### Existing `testReconnectDelayHandler` updates
- The current lambda `l -> Duration.ofSeconds(l * 2)` no longer compiles — wait signature has changed (more params, primitive return).
- Replace with the four-arg shape: `(round, opts, secure, ld) -> round * 2000`. Interface stays SAM so the lambda form survives.

### New `DefaultReconnectDelayHandlerTests`
Direct unit tests of `DefaultReconnectDelayHandler.INSTANCE`. Pure function — each row is one stateless call:

| behavior | round | lameDuckTriggered | secure | expected wait (ms) |
|---|---|---|---|---|
| BeforeSubsequentRounds | 1 | false | false | 0 |
| BeforeSubsequentRounds | 1 | true  | false | 0 (LDM ignored) |
| BeforeSubsequentRounds | 2 | false | false | within [wait, wait+jitter) |
| BeforeSubsequentRounds | 2 | false | true  | within [wait, wait+jitterTls) |
| BeforeAllRounds       | 1 | false | false | within [wait, wait+jitter) |
| BeforeAllRounds       | 1 | true  | false | within [wait, wait+jitter) |
| LameDuckAware         | 1 | false | false | 0 |
| LameDuckAware         | 1 | true  | false | within [wait, wait+jitter) |
| LameDuckAware         | 2 | false | false | within [wait, wait+jitter) |

No state to drive, no order dependencies, no fixtures to reset — `INSTANCE` is a pure function from `(round, options, secure, lameDuckTriggered)` to `long`. A shareable-singleton sanity test (`assertSame(DefaultReconnectDelayHandler.INSTANCE, DefaultReconnectDelayHandler.INSTANCE)`) is the smallest possible proof that two connections built from the same `Options` aren't racing.

Also test `DefaultReconnectDelayHandler.computeWaitMillis(options, secure)` directly (covered fully in `PLAN_DURATION_TO_MILLIS.md` — jitter-only wait, negative clamp, TLS jitter selection, default-options values).

### `NatsConnection` lame-duck wiring tests
The existing reconnect tests should keep passing (BeforeSubsequentRounds semantics still hold by default for non-LDM disconnects). Add tests covering the connection-side flag tracking:

1. **LDM signal sets the flag.** Use a small lambda handler that records its received parameters (`(round, opts, secure, ld) -> { recordedLd = ld; return 0L; }`) and assert that an LDM INFO before disconnect causes the next round-1 call to observe `lameDuckTriggered == true`.
2. **Consume-on-round-1.** After (1), a subsequent disconnect/reconnect campaign with no further LDM signal should observe `lameDuckTriggered == false` — the flag was consumed by the round-1 call of the previous campaign.
3. **No LDM → no round-1 delay** under `LameDuckAware` (sanity check that the default doesn't regress healthy failover).

If the existing test harness doesn't make end-to-end coverage easy, defer integration tests to a follow-up but capture the gap in `todo.md`.

## Migration / breaking change footprint

This is a breaking interface change. v3 is unreleased, so we don't owe API stability — but we do owe a clean migration story:

- **The interface signature changes from `Duration getWaitTime(long)` to `long getWaitTime(long, Options, boolean, boolean)`.** Any user-supplied handler must update both the parameter list and the return type. Common idiom `return Duration.ofSeconds(5);` becomes `return 5000;`. The interface stays single-abstract-method, so existing lambda handlers translate cleanly to the new signature.
- **`Options.getReconnectDelayHandler()` is now never null.** When no custom handler is set, `OptionsBuilder.build()` falls back to the stateless `DefaultReconnectDelayHandler.INSTANCE` singleton. Sharing one `Options` (and therefore the same handler reference) across multiple connections is safe — the LDM flag lives on `NatsConnection`, not on the handler.
- **`OptionsBuilder` reconnect setters now take `long millis`, not `Duration`.** `reconnectWait(Duration.ofSeconds(2))` becomes `reconnectWait(2000)`. Same for `reconnectJitter(...)` and `reconnectJitterTls(...)`. Property files don't change format — `durationProperty` still accepts both ISO-8601 and integer milliseconds, the conversion to `long` ms happens in the builder callback. Full details in `PLAN_DURATION_TO_MILLIS.md`.
- **`Options` reconnect getters renamed and retyped.** `Options.getReconnectWait()` (returns `Duration`) → `Options.getReconnectWait()` (returns `long`). Same for `getReconnectJitter()` / `getReconnectJitterTls()`. Constants in `OptionsConstants`: `DEFAULT_RECONNECT_WAIT` → `DEFAULT_RECONNECT_WAIT` (and the two jitter siblings).
- **The default behaviour changes from BeforeSubsequentRounds to LameDuckAware.** Observable only for connections to servers that send LDM — operators of NATS clusters that issue LDM during planned shutdowns will get a 2-second wait before round 1 of reconnect (matches what they probably want). Operators whose servers don't issue LDM see no change.
- **`PROP_RECONNECT_DELAY_BEHAVIOR` parsing falls back to `LameDuckAware` for unknown values.** Existing property files setting it to `BeforeSubsequentRounds` or `BeforeAllRounds` keep working.

Document the changes in `MIGRATION_GUIDE.md` (under Core — new bullet alongside the existing builder-method removals) and in `MIGRATION_GUIDE_OPTIONS.md` (renamed reconnect constants).

## Open decisions for review

1. **Four-arg signature `(round, options, secure, lameDuckTriggered)` vs context object.** I'm proposing four-arg for simplicity and zero allocation. If we expect the context to grow (e.g. last-attempted URL, last exception, statistics snapshot), introducing a `ReconnectContext` record now would future-proof — but it's premature until we know what else we need.

2. **`LameDuckAware` as the default.** Strictly safer than `BeforeSubsequentRounds` (no extra delay for non-LDM disconnects), and reflects the modern operator workflow where LDM *is* the expected drain signal. The trade-off: it makes JNats behave subtly differently from nats.go / nats.c (which have no LDM-aware delay at all). Worth discussing with the maintainer.

3. **Where to flip `lameDuckTriggered`.** Today the connection emits the `LAME_DUCK` connection event but does not disconnect — it's a notification; the actual disconnect comes seconds later when the server closes the connection. Setting the flag at the notification (line 1901) means the next reconnect campaign (which is what LDM triggers) sees the correct value when it asks the handler. This is the right semantic.

4. **Long vs int for `round`.** Keeping `long` matches the current `totalTries` type. `int` is more than enough in practice (you'd need decades of continuous reconnect-loop activity to overflow at any realistic round cadence). Sticking with `long` avoids a second breaking change later if we change our minds.

## Implementation order (build-safe)

1. Add `LameDuckAware` constant to the existing `ReconnectDelayBehavior.java` enum (does not break anything).
2. Convert the three reconnect-delay fields on `Options` / `OptionsBuilder` / `OptionsConstants` from `Duration` to `long` ms per `PLAN_DURATION_TO_MILLIS.md` (fields, setters, getters, `OptionsBuilder.properties(...)` callbacks).
3. Rewrite `ReconnectDelayHandler.java` interface — single abstract method `long getWaitTime(long, Options, boolean, boolean)`. SAM preserved. **This breaks `testReconnectDelayHandler` and any internal callers.**
4. Add new file `DefaultReconnectDelayHandler.java` in `io.synadia.client.impl` — stateless concrete class with a public `INSTANCE` singleton, the behaviour-switching `getWaitTime`, and the `public static long computeWaitMillis(Options, boolean)` helper.
5. Update `OptionsBuilder.build()` to fall back to `DefaultReconnectDelayHandler.INSTANCE` when `reconnectDelayHandler == null`.
6. Update `OptionsBuilder` field default at line 106 to `LameDuckAware`; update the null-reset path in `reconnectDelayBehavior(...)`.
7. Update `ReconnectDelayBehavior.get(...)` factory to default to `LameDuckAware`.
8. Rewrite `NatsConnection.invokeReconnectDelayHandler(long)` body — single handler call, no null-check; track `lameDuckTriggered` locally and consume on round 1.
9. Add `this.lameDuckTriggered = true;` at line 1901, next to the existing LDM event emission.
10. Rewrite the reconnect loop at lines 420–434 — drop the behavior-enum branch, always call before round 1, increment `round` instead of `totalRounds`.
11. Update `testReconnectDelayHandler` to new signature; update `testReconnectDelayBehavior` defaults.
12. Add `DefaultReconnectDelayHandlerTests` (pure-function table) and the small `NatsConnection` LDM-flag-tracking test.
13. Update `MIGRATION_GUIDE.md` (Core section — new bullets for the handler-signature change and Duration→long ms reconnect setters/getters) and `MIGRATION_GUIDE_OPTIONS.md` (renamed reconnect constants).

After step 3 the tree won't compile until step 11 lands. Acceptable for a single integration branch — but if you want each commit to compile, bundle steps 3-11 as one commit.

## Risks

- **Reconnect loop is critical code.** The new design changes when `invokeReconnectDelayHandler` is called (always before round 1, where before it was only called when `BeforeAllRounds` was set). At default (`LameDuckAware`), no LDM signal means the handler returns 0 — net behaviour is identical to today. Verify with existing reconnect-loop tests that they still pass.
- **`DefaultReconnectDelayHandler` is stateless and shareable.** The singleton `INSTANCE` is the default, so sharing one `Options` across many connections does not introduce any race on per-campaign LDM state — the flag lives on each `NatsConnection`. A custom handler is also safe to share *if* it's written stateless; a stateful custom handler shared across connections is the user's risk to take.
- **`volatile boolean lameDuckTriggered` on `NatsConnection` is the only shared state.** Single writer (the LDM event handler at line 1901), single reader (the reconnect loop, on round-1 calls only). Volatile is sufficient — no atomic needed.

## Out of scope

- Per-attempt backoff (the nats.rs / nats.net model). If we ever want that, it's a separate handler implementation a user can supply — the new interface supports it directly. Default impl stays round-based.
- Custom jitter strategies beyond uniform. Same: a user can implement one.
- Hooking LDM into `ConnectionListener` / `ErrorListener` — those are separate observer interfaces and already receive the `LAME_DUCK` connection event; they stay as they are.
