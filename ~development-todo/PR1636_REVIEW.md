# Review: nats-io/nats.java PR 1636 (branch `connection-review-fixes`)

Written 2026-10-07. Line numbers are from `NatsConnection.java` on the PR branch. The lines the PR adds are correct. All 3 findings are paths the fix does not cover. Each was verified by reading the branch code. The new test was not run.

## 1. High: queueing that returns `false` leaves the future in `pongQueue`

The PR removes the future only when queueing throws `RuntimeException` (line 1763). Queueing can also fail by returning `false`, with no exception:
- `WriterMessageQueue.push` catches `InterruptedException` and returns `false`.
- With `discardMessagesWhenOutgoingQueueFull`, a non-internal push returns `false` when the queue is full.

Neither return value reaches `sendPing`. `NatsConnectionWriter.queueInternalMessage` is `void` and ignores the result of `push`. `queueOutgoing` (line 1891) only fires `messageDiscarded`.

- Scenario A: `flush()` uses `sendPing(true)`, which takes the internal path. If `flush()` is called while the thread's interrupt flag is set, `editLock.tryLock` throws `InterruptedException` and `push` returns `false`. The future stays in `pongQueue` and no PING is sent. The next PONG completes that leftover future, so the next live `flush()` times out. The PR description says the interrupt case is fixed, but this path still leaves the future behind.
- Scenario B: the ping timer calls `softPing()`, which is `sendPing(false)` (line 671). When discard-when-full is on and the queue is full, each timer tick leaves one future in `pongQueue`. These count toward `maxPingsOut` (line 1755). The client then raises "Max outgoing Ping count exceeded." and reconnects, even though the server is healthy.

Fix: make `queueInternalMessage` return the result of `push`, and make `queueOutgoing` and `queueInternalOutgoing` return it as well. In `sendPing`, remove the future when the result is `false`, the same as in the existing catch.

## 2. Medium: `RTT()` has the same defect and the PR does not change it

`RTT()` adds `pongFuture` to `pongQueue` (line 1716), then calls `writer.queue(...)`.
- If `writer.queue` throws `IllegalStateException` (output queue full or busy), the exception is not caught. Only `ExecutionException`, `TimeoutException` and `InterruptedException` are caught.
- If `writer.queue` returns `false`, `RTT()` waits until the timeout.

In both cases the future stays in `pongQueue` with no PING sent, and the next PONG goes to it instead of to a live `flush()` or `RTT()` waiter.

## 3. Low: the test covers only the throw path

The new test in `NatsConnectionImplTests.java` (line 373) forces `IllegalStateException` through the push timeout. It does not cover the interrupt path or the discard-when-full `softPing` path from finding 1.

## Applied 2026-10-07

V2 (`/mnt/c/nats/nats.java`, branch `connection-review-fixes`, uncommitted):
- `NatsConnectionWriter.queueInternalMessage` returns the result of `push`.
- `sendPing` calls the writer directly and checks the result. When the PING is not queued, it removes the future, fires `messageDiscarded` on the non-internal path, and returns the future completed exceptionally (`IllegalStateException("PING was not queued.")`), so a `flush()` waiting on it fails with `TimeoutException` instead of reporting success. The `protected` `queueOutgoing` / `queueInternalOutgoing` signatures are unchanged.
- `RTT()` removes the future when `writer.queue` throws, and throws `IOException("RTT PING was not queued.")` when it returns `false`.
- Tests: `testSendPingDoesNotLeavePongFutureWhenQueueingFails` (now also `RTT`), new `testSendPingDoesNotLeavePongFutureWhenInterrupted`, new `testPingDoesNotLeavePongFutureWhenDiscarded`. With the fix: `NatsConnectionImplTests` 8/8, `PingTests` 9/9. Against the previous branch code all 3 new or extended tests fail (5 of 5 attempts each).

V3 (`/mnt/c/nats/nats.java.v3`, uncommitted). Here an interrupt already throws `IllegalStateException` in both implementations, so only the discard path was broken:
- Classic and V3 `queueOutgoing` / `queueInternalOutgoing` return whether the message was queued. They are `protected`, so a subclass that overrides them must change its return type to `boolean`. The classic `NatsConnectionWriter.queueInternalMessage` returns the result of `push`.
- Classic `sendPing` and the `NatsConnectionV3.sendPing` override both use the new `NatsConnection.pingNotQueued`: it removes the future and completes it exceptionally.
- `RTT(long)` throws `IOException("RTT PING was not queued.")` when the PING is not queued (its `finally` already removed the future on any failure).
- Tests: the existing queueing-fails test now also covers `RTT`; new `testSendPingDoesNotLeavePongFutureWhenInterrupted` and `testPingDoesNotLeavePongFutureWhenDiscarded`. `NatsConnectionImplTests` 9/9, `PingTests` 8/8, `OutboundBufferTests` 17/17 on both V3 (default) and classic. Against the pre-fix code `testPingDoesNotLeavePongFutureWhenDiscarded` fails 5 of 5 attempts on both implementations. All modules `compileTestJava` clean.
