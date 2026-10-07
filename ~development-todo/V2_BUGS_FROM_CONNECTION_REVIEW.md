# V2 bugs found during the v3 connection review

Written 2026-10-06. Bugs only. Improvements found in the same review (allocation, buffer sizing, write ordering, connect speed, queue design) are deliberately left out, since V2 is not getting features or improvements. Changing a `Headers` object after publish is not listed: it is a documented limitation of the V2 connection.

Checked against V2 (`/mnt/c/nats/nats.java`) `main` at `f74fc83f`. Each entry says whether the bug is confirmed or suspected, and how.

## 1. `sendPing` leaves its pong future behind when queueing the PING throws — confirmed from the code

**Where:** `NatsConnection.java:1760-1768`.

**Mechanism:** the pong future is added to `pongQueue` before `queueInternalOutgoing` / `queueOutgoing`, and either can throw (outgoing queue full, busy, or the thread interrupted). The future stays in the queue with no PING sent for it. PONGs complete futures in order, so the next PONG completes the orphan instead of the waiter it was meant for. A `flush()` can then time out even though the server answered. This is the same bug class as the one already fixed in `RTT`.

**Fix:** if queueing throws, remove the future from `pongQueue` and rethrow.

**Test:** stop the writer, fill the outgoing queue (`maxMessagesInOutgoingQueue(1)`, short `writeQueuePushTimeout`), assert that `sendPing()` throws and that `pongQueue` has not grown. That test exists in v3 (`NatsConnectionImplTests.testSendPingDoesNotLeavePongFutureWhenQueueingFails`): it fails 5 of 5 without the fix and passes with it.

## 2. A late reader or writer thread from the previous socket can stop or disturb the current one — suspected, not reproduced

**Decision 2026-10-07: not fixed.** The window needs a reader or writer thread to outlive the `connectionTimeout` join in `closeSocketImpl` after its socket is closed; not reproduced. The per-run flag added complexity for that case only, so it was removed from both the V2 branch and classic in v3.
