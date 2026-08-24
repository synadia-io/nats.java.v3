# Plan: stop `NatsConnection.close()` from throwing `InterruptedException`

## Problem

`NatsConnection implements AutoCloseable` (`NatsConnection.java:32`), and its public method is:

```java
public void close() throws InterruptedException
```

`AutoCloseable.close()`'s own javadoc strongly advises implementers **not** to throw `InterruptedException`: try-with-resources can *suppress* the exception (when the body also throws), and a suppressed `InterruptedException` silently discards the thread's interrupt status, which leads to hard-to-diagnose runtime misbehavior. We want `close()` to honor that contract.

## Where the exception actually comes from

The checked exception is never thrown by close logic directly — it propagates up from a blocking wait:

```
close()
  -> close(true, false)                 // protected workhorse
       -> waitForDisconnectOrClose(...)  // only when draining or already disconnecting/closed
            -> waitWhile(...)
                 -> Condition.await()    // throws InterruptedException
```

So the only way `close()` throws is when the calling thread blocks waiting for an in-progress drain/disconnect to finish and is interrupted while waiting (`NatsConnection.java:2310`/`2322`). The rest of the close path (`closeSocketImpl`, dispatcher/subscriber teardown, executor shutdown) does not throw checked exceptions.

## Design

1. Make the **public** `close()` swallow `InterruptedException`, restore the interrupt status, and return — so it no longer declares `throws`.
2. Keep the **protected** `close(boolean checkDrainStatus, boolean forceClose)` throwing. It is the internal workhorse used by the connect/reconnect/closeSocket machinery, all of which already declare `throws InterruptedException` and legitimately propagate interruption.
3. Repoint the internal callers that currently call the no-arg `close()` to the protected `close(true, false)`, so they keep propagating interruption up the connect/reconnect chain unchanged. After this, the public `close()` is the single point that swallows.

This keeps behavior identical for internal flows and only changes the public contract.

## Changes

### 1. `NatsConnection.close()` (NatsConnection.java:770-780)

Replace:

```java
/**
 * ...
 * @throws InterruptedException if the thread, or one owned by the connection is interrupted during the close
 */
public void close() throws InterruptedException {
    this.close(true, false);
}
```

with:

```java
/**
 * Close the connection and release all blocking calls like {@link #flush flush}
 * and {@link Subscription#nextMessage(long) nextMessage}.
 * If close() is called after {@link #drain(Duration) drain} it will wait up to the connection timeout
 * to return, but it will not initiate a close. The drain takes precedence and will initiate the close.
 * <p>If the calling thread is interrupted while close is waiting, the close stops waiting,
 * the thread's interrupt status is restored, and the method returns.
 */
@Override
public void close() {
    try {
        this.close(true, false);
    }
    catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // restore interrupt status; do not propagate from close()
    }
}
```

Notes:
- Add `@Override` (it implements `AutoCloseable`).
- Restoring the interrupt flag is the correct way to "handle" a swallowed `InterruptedException` so cooperative cancellation still works for the caller.

### 2. Internal callers: `close()` -> `close(true, false)`

Each of these is already inside a method that declares `throws InterruptedException`, so propagation is preserved exactly:

- `NatsConnection.java:264` — `connectImpl(...)` failure path
- `NatsConnection.java:388` — `reconnectImpl()` (maxReconnect == 0)
- `NatsConnection.java:399` — `reconnectImpl()` (not connected)
- `NatsConnection.java:756` — `closeSocket(...)` when `isClosing()`

### 3. Update the stale comment / suppression on `close(boolean, boolean)` (NatsConnection.java:782-784)

The comment "there might be multiple paths ... but it turns out there isn't" is now outright wrong — after step 2 it has several callers. Update the comment. The `@SuppressWarnings("SameParameterValue")` still technically holds (every caller passes `forceClose=false` and `checkDrainStatus=true`); decide whether to keep it or drop it. Recommended: update the comment to note the callers and keep the suppression only if the IDE still flags it.

### 4. Fix external callers

- `core/src/test/java/io/synadia/client/utils/ConnectionUtils.java:117-122` — the `try { conn.close(); } catch (InterruptedException e)` catch becomes unreachable (compile error). Simplify to:
  ```java
  public static void close(NatsConnection conn) {
      conn.close();
  }
  ```
- `try-with-resources` and `catch (Exception ignore)` callers (`TestBase.java:247/249`, `JetStreamTestBase.java:450/452`, `SharedServer.java:168`, etc.) — **no change needed**; removing a thrown checked exception never breaks these.
- `tdb/io/synadia/client/impl/JetStreamPullTests.java:53` — under `tdb/` (scratch, not a build source set). Confirm it is excluded from compilation; if so, leave it. If it is compiled, apply the same simplification.
- Optional cleanup: methods elsewhere that declared `throws InterruptedException` *solely* to relay `close()` can drop it, but a now-unnecessary `throws` is not a compile error, so this is non-essential and can be skipped to keep the diff small.

### 5. Unaffected on purpose

- `drain(...)` uses the protected `close(false, false)` and declares its own `throws InterruptedException` — no change.
- The reader/writer stop waits in `closeSocketImpl` already catch their exceptions internally — no change.

## Verification

- Compile `core` (and `jetstream`, which depends on it) to confirm the only fallout is the `ConnectionUtils` catch (fixed in step 4).
- Run the close/drain-focused tests (e.g. `DrainTests`, `NatsConnectionImplTests`, `ReconnectTests`) to confirm no behavioral regression.
- (Per project convention, gradle is not run automatically — run these when ready.)

## Out of scope

- No change to `drain()`, `request()`, `flush()`, or other methods that throw `InterruptedException` — the guidance is specific to `AutoCloseable.close()`.
