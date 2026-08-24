# Integration Plan — PR #1609 (status update ordering in closeSocket)

Goal: port upstream `nats-io/nats.java` **issue [#1608](https://github.com/nats-io/nats.java/issues/1608)** / **PR [#1609](https://github.com/nats-io/nats.java/pull/1609)** into `nats.java.v3`.

**Status: DONE (2026-08-10), all three steps, under review.** `closeSocket` ordering, the PR #1547 `updateStatus` port and the `volatile status` field are all in, plus the new test. One existing test needed adjusting — see "Fallout" below. Full suite green: 934 tests, 0 failures across core/jetstream/service.

While confirming the defect, two further gaps turned up in the same method family — v3 forked before upstream PR #1547 and never picked up its `updateStatus` changes. See "Also missing" below; they are worth doing in the same pass since they touch the same twenty lines.

## The defect

`Connection.getStatus()` and `getConnectedUrl()` read two separate fields that are not published together, so the order they are updated in decides whether an application sampling the connection can observe a combination that was never true.

`core/src/main/java/io/synadia/client/impl/NatsConnection.java` `closeSocket`, line 760:

```java
this.disconnecting = true;
this.exceptionDuringConnectChange = null;
wasConnected = (this.status == CONNECTED);
statusChanged.signalAll();
// ... statusLock released ...

closeSocketImpl(forceClose);        // <-- clearCurrentServer() is its first statement, line 922

statusLock.lock();
try {
    updateStatus(DISCONNECTED);     // <-- status only catches up here
```

`closeSocketImpl` clears the current server first, then waits on the reader and writer stop futures (`readStop.get(1, TimeUnit.SECONDS)`, `writeStop.get(1, TimeUnit.SECONDS)`). For that whole window the connection reports `CONNECTED` with a null connected url. Normally sub-millisecond; bounded by those two stop timeouts, so a reader that does not stop promptly stretches it towards two seconds.

`forceReconnectImpl` already does it the right way — `closeSocketLock.lock()`, `updateStatus(DISCONNECTED)`, *then* `reader.stop(false)`. So the two teardown paths in v3 disagree with each other, exactly as they did upstream.

Reached by a communication failure: reader or writer raises an `IOException`, `handleCommunicationIssue` submits a task, the task calls `closeSocket(true, true)`. **Not** reached by `forceReconnect()`, which matters for how the test is written.

## Goals

1. Move `updateStatus(DISCONNECTED)` in `closeSocket` ahead of `closeSocketImpl(forceClose)`, into the block that already sets `disconnecting = true`, positioned after `wasConnected` is captured.
2. Port the missing PR #1547 `updateStatus` changes (see below).
3. A test that fails before and passes after.

## File inventory

### New files (1)
| Path | Purpose |
|---|---|
| `core/src/test/java/io/synadia/client/impl/ConnectionStateConsistencyTests.java` | Polls the status/url pair from a thread during a communication-failure teardown and asserts it is never inconsistent. |

### Modified files (2)
| Path | Why |
|---|---|
| `core/src/main/java/io/synadia/client/impl/NatsConnection.java` | `closeSocket` ordering; `updateStatus` event selection; `volatile status`. |
| `core/src/test/java/io/synadia/client/impl/ReconnectTests.java` | `testSocketDataPortTimeout` was reading the socket-write-timeout count straight after the DISCONNECTED event, which only worked because of the window this fix closes. See "Fallout". |

## Step-by-step

### 1. `closeSocket` ordering — DONE

In `closeSocket` (line 760), the first `statusLock` block becomes:

```java
this.disconnecting = true;
this.exceptionDuringConnectChange = null;
wasConnected = (this.status == CONNECTED);

// Update the status before tearing the socket down, not after. closeSocketImpl
// clears the current server as its first act and can then block for as long as
// the reader and writer stop timeouts allow, so updating afterwards leaves a
// window where the connection reports CONNECTED with a null connected url.
// This is also the order forceReconnectImpl already uses.
updateStatus(DISCONNECTED);

statusChanged.signalAll();
```

and the block after `closeSocketImpl(forceClose)` loses its `updateStatus(DISCONNECTED)` line, keeping the rest:

```java
statusLock.lock();
try {
    this.exceptionDuringConnectChange = null; // Ignore IOExceptions during closeSocketImpl()
    this.disconnecting = false;
    statusChanged.signalAll();
}
finally {
    statusLock.unlock();
}
```

Points to check while doing it:

* `wasConnected` must still be captured **before** the status update, or the `else if (wasConnected && tryReconnectIfConnected)` reconnect branch stops firing.
* `updateStatus` takes `statusLock`, which is reentrant, and was already being taken this way in the block being emptied.
* `updateStatus(DISCONNECTED)` derives `uriDetail` from `currentServer == null ? lastServer : currentServer`. Running before `clearCurrentServer()` means it now reads `currentServer` directly rather than the `lastServer` fallback — same string, since `clearCurrentServer()` copies one into the other.
* The `DISCONNECTED` event now fires before the reader and writer are stopped rather than after. Delivery is asynchronous on the callback executor so nothing is tightly sequenced against it, but it is the one observable change.

### 2. The test — DONE

Mirror upstream `ConnectionStateConsistencyTests`, adjusted for v3 conventions — package `io.synadia.client.impl`, `extends TestBase` (`io.synadia.client.utils.TestBase`), `NatsTestServer` from `io.synadia.client` with `nextPort()` and the `(int port)` constructor, `ConnectionStatus` rather than a nested `Status`, and `NatsConnection` directly since v3 has no `Connection` interface (`NatsConnection implements AutoCloseable`). Match a neighbouring test such as `ConnectionListenerTests` for the exact connect/close helpers out of `ConnectionUtils` / `OptionsUtils`.

Shape:

1. Connect to a `NatsTestServer` on a known port, `maxReconnects(-1)`, short reconnect wait, an error listener that swallows the expected io noise.
2. Start a daemon thread tight-looping on `getStatus()` and `getConnectedUrl()`, counting samples where status is `CONNECTED` and url is null.
3. **Kill the server** — `ts.close()`. Do not use `forceReconnect()`; that path goes through `forceReconnectImpl`, which is already correctly ordered, and such a test passes with or without the fix.
4. Wait for the status to leave `CONNECTED`, settle, stop the poller.
5. Assert the sample count is large enough to mean anything, then assert zero inconsistent observations.

Upstream numbers for calibration, on one machine: roughly 210 million samples per run, **128 to 445 inconsistent observations before the fix, 5 runs out of 5 failing**, zero after. Confirm it fails on unfixed v3 before accepting it — a test of this kind that passes on the unfixed tree is measuring the wrong path.

**Result on v3 (WSL):** ~230 million samples per run. Before the fix it failed **10 attempts out of 10** (two runs, the retry plugin taking five attempts each), though with a much narrower window than upstream — exactly **1** inconsistent observation per run rather than hundreds. After the fix, zero. The window is real and reproducible here, just shorter, because nothing in this test makes the reader or writer slow to stop.

### 3. Also missing — upstream PR #1547 (`status access`), released upstream in 2.25.3 — DONE

v3's `updateStatus(ConnectionStatus newStatus, String uriDetail)` (line ~2427) is the **pre-#1547** version:

```java
ConnectionStatus oldStatus = this.status;      // read OUTSIDE the lock

statusLock.lock();
try {
    if (oldStatus == CLOSED || newStatus == oldStatus) { return; }
    this.status = newStatus;
} finally {
    statusChanged.signalAll();
    statusLock.unlock();
}

if (this.status == DISCONNECTED) {             // re-read AFTER unlocking
    processConnectionEvent(ConnectionEvents.DISCONNECTED, uriDetail);
}
else if (this.status == CLOSED) { ...
```

Two problems, both fixed upstream:

**a. The event is chosen by re-reading the field after the lock is released.** If another thread moves the status in between — which is exactly what a fast reconnect does — this raises the event for the *newer* status, or falls through all four branches and raises nothing. Worked example: thread A calls `updateStatus(DISCONNECTED)`, writes, unlocks; thread B calls `updateStatus(RECONNECTING)` and writes; A re-reads `RECONNECTING`, matches no branch, raises nothing; B's `oldStatus` was `DISCONNECTED` and it also re-reads `RECONNECTING`, raises nothing. The `DISCONNECTED` event is lost. The field stays correct throughout — it is the notification that goes missing, and an application tracking connectedness from events can end up believing it is disconnected while it is connected. This was analysed in detail on upstream issue #1606.

**b. `status` is not volatile.** v3 line 49 is `private ConnectionStatus status;` where upstream is `private volatile Status status;`. `getStatus()` (line 2391) returns it with no lock held, so a reader has no happens-before edge to the write.

The upstream shape to port:

```java
ConnectionStatus oldStatus;
statusLock.lock();
try {
    oldStatus = this.status;
    if (oldStatus == CLOSED || newStatus == oldStatus) { return; }
    this.status = newStatus;
    statusChanged.signalAll();
}
finally {
    statusLock.unlock();
}

if (newStatus == DISCONNECTED) { ... }
else if (newStatus == CLOSED) { ... }
else if (oldStatus == RECONNECTING && newStatus == CONNECTED) { ... }
else if (newStatus == CONNECTED) { ... }
```

plus `private volatile ConnectionStatus status;`.

Note the `signalAll()` also moves inside the lock in the upstream version.

This half has no test upstream — the window is between an unlock and four comparisons and there is no way to force it through the public api. It is a code-reading fix. Take it on those terms or leave it, but it should not be *silently* left, since it is the mechanism behind the symptom reported in #1606.

### 4. Related, deliberately not fixed upstream — NOT TAKEN, still open

`currentServer` (v3 line 55) is not volatile either, is written outside `statusLock` by `clearCurrentServer()` from both `tryToConnect` and `closeSocketImpl`, and is read unsynchronised by `getConnectedUrl()` (line 2381). Ordering the writes correctly, which is what step 1 does, does not by itself guarantee a reader observes them in that order. Upstream logged this on #1608 as related and out of scope. Same call here — worth a separate look, not part of this port.

## Fallout — `ReconnectTests.testSocketDataPortTimeout`

The one test the change broke, 5 attempts out of 5, where it passed 4 out of 4 on an unfixed worktree at `e8b6775a`. Bisected to step 1 alone; step 3 is not involved.

It failed on `assertTrue(listener.getSocketWriteTimeoutCount() > 0)` — the DISCONNECTED and RECONNECTED events still arrived, but the socket write timeout callback had not been raised yet at the moment the count was read.

Instrumenting the unfixed tree with timestamped prints showed what the test had actually been depending on. The disconnect in this test is **not** driven by the write watch at all — it is driven by the reader: with the writer blocked and the outgoing queue capped at 100, the server's PING cannot be answered, the reader throws `IOException: Parse Protocol OP_PING`, and that goes to `handleCommunicationIssue` → `closeSocket`. The simulator's write watch fires independently a few hundred milliseconds later. So two unsequenced things race, and the test read the count as though one implied the other:

```
7.4s  handleCommunicationIssue java.io.IOException: Parse Protocol OP_PING
      closeSocket enter
      ... closeSocketImpl: readStop.get(1s) + writeStop.get(1s), and the writer is
          parked inside a 60s simulated write, so this is where the ~1-2s went ...
+0.3s simulator watch fired  ->  socketWriteTimeout callback   (count becomes 1)
+1-2s updateStatus CONNECTED -> DISCONNECTED                   (test wakes, count already 1)
```

Move the status update to the top of `closeSocket` and those last two lines swap: the test wakes on DISCONNECTED roughly 300 ms before the watch fires, and reads 0.

The test was reading a count that only happened to be populated because the status update was late — the exact defect being fixed. Fix in the test, not the product: queue the socket write timeout on the `Listener` (`queueSocketWriteTimeout`, which already existed and had no callers) and `validate()` it, so it waits for the notification instead of inferring it from an unrelated event. Passes 3 out of 3 after.

## Build and verify

* `closeSocket` is the core disconnect path. Run the connect, reconnect, drain and listener test classes at minimum, not just the new test.
* Upstream ran its full suite: 955 passed, and the 10 failures reproduced identically with and without the change, so none were attributable to it. Do the same comparison here rather than reading a failure list cold — establish the baseline on the unfixed tree first.

**Done, on WSL, nats-server v2.14.4.** The listed classes first (`ConnectTests`, `ReconnectTests`, `DrainTests`, `ConnectionListenerTests`, `ErrorListenerTests`, `TLSConnectTests`, `WebsocketConnectTests`, `ListenerIdTests`, `AuthAndConnectTests`, `NatsConnectionImplTests` and the rest of the connect/reconnect family, 178 tests), which is what turned up the `testSocketDataPortTimeout` failure. Baseline for it was established on a detached worktree at `e8b6775a` rather than by touching the working tree. Then the full suite: **934 tests, 0 failures** — core 542, jetstream 377, service 15. No test from the flaky watch list failed.
