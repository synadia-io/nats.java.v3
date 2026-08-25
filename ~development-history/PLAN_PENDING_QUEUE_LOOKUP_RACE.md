# Plan: pending queue lookup race in message delivery — V3 port of nats.java PR #1615

Source: [nats-io/nats.java#1615](https://github.com/nats-io/nats.java/pull/1615) (**merged 2026-08-19**; replaces #1614, which is closed unmerged — do not port both). Reported via Synadia support ticket #2038 against 2.25.3.

**The bug is present in V3, unchanged in kind.** Everything the V2 PR describes has a direct counterpart here, including the escalation path that makes it expensive: `NatsConnectionReader:466` throws `new IOException("Gather Message Data", ex)`, so an NPE on the reader thread is relabelled as an IO failure and turns a dropped message into a forced disconnect and reconnect on an otherwise healthy connection.

The reporter's trigger was `KeyValue.keys()` on a 5 second timer — a synchronous push subscription built and torn down in a `finally`, on the order of 17,000 subscription teardowns a day per instance. Whatever V3's KV surface ends up being, the shape (build a subscription, read, invalidate) is ordinary and the window is hit by volume, not by anything exotic.

## Step 0 — DONE, verified 2026-08-25 against `d8756916`

**All four claims still hold and all six sites are still double reads.** Nothing in the plan's shape needs re-deriving. Two things did move:

**Upstream #1615 is merged** (2026-08-19), not open as recorded below. The V2 diff is now final rather than a moving target, so it can be read as the settled source of truth. #1614 is closed unmerged, as expected - still do not port both.

**Line numbers, refreshed** (`433bab1d` and `081902d7` moved several). Use these, not the ones in the table further down:

| # | Site | Then | Now |
|---|---|---|---|
| 1 | `NatsMessageSink` pending message count | `:76` | `:76` |
| 2 | `NatsMessageSink` pending byte count | `:84` | `:84` |
| 3 | `NatsMessageSink` drain | `:141` | `:141-142` |
| 4 | `NatsSubscription.invalidate` | `:90-91` | `:93-94` |
| 5 | `NatsSubscription._nextMessage` | `:180-184` | `:174-180` |
| 6 | `NatsConnection.deliverMessage` | `:2137-2151` | `:2148-2156` |

Site 6 was rewritten by the ownership change and came through unchanged in kind: `q` is still read once from `sub.getMessageQueue()`/`d.getMessageQueue()`, then `s.hasReachedPendingLimits()` still re-reads the field through sites 1 and 2, so the verdict is still formed against a different read than the push. The only difference is that the sink and the dispatcher now come off the connection's `SubscriptionInfo` record rather than off the subscription.

Claim-by-claim: `NatsDispatcher.incoming` is still `protected final` (`:29`) and `NatsSubscription.incoming` is still a nullable `private` field (`:27`), so `-1`/`NOT_AVAILABLE` stays subscription-only. `setPendingLimits` still normalises to `Long.MAX_VALUE` (`NatsMessageSink:49-50`), so V2's `> 0` guards stay dead code here. `cleanUpAfterDrain()` (`:204`) still runs before `tracker.complete(this.isDrained())` (`:209`), so the `== 0` -> `<= 0` change at `NatsMessageSink:155` is still mandatory. And `:133`/`:155` are still the only main-source comparisons of a pending count.

`NatsMessageSinkPendingTests` does not exist yet; nothing from this plan has been implemented.

<details>
<summary>Original Step 0 instructions, kept for the greps</summary>

### Re-verify against current code before changing anything


**Everything below was measured against the working tree on 2026-08-17/18, with the internal-marker naming work uncommitted and `Under Review`.** That review lands next, and it touches `NatsConnection.java`, `NatsSubscription.java`, `NatsDispatcher.java` and `MessageQueueBase.java` — every file this plan edits except `NatsMessageSink.java`. **Every line number here should be assumed stale, and at least one name may have moved.** Do not open this plan and start editing at the quoted line numbers.

Re-run these first. They are the checks the plan was built from, and each one is a claim that has to still hold:

```bash
# the 6 defect sites - confirm each is still a double read
grep -n "getMessageQueue()" core/src/main/java/io/synadia/client/impl/NatsMessageSink.java
grep -n "incoming" core/src/main/java/io/synadia/client/impl/NatsSubscription.java
grep -n "hasReachedPendingLimits\|getMessageQueue()" core/src/main/java/io/synadia/client/impl/NatsConnection.java

# the three facts the design leans on
grep -n "ConsumerMessageQueue incoming" core/src/main/java/io/synadia/client/impl/NatsDispatcher.java   # still 'protected final'?
grep -n "maxMessages <= 0\|maxBytes <= 0" core/src/main/java/io/synadia/client/impl/NatsMessageSink.java # still normalised to Long.MAX_VALUE?
grep -n "cleanUpAfterDrain\|tracker.complete" core/src/main/java/io/synadia/client/impl/NatsMessageSink.java # invalidate still before complete?

# nothing new compares pending against 0
grep -rn "getPendingMessageCount()\|getPendingByteCount()" --include="*.java" core jetstream service | grep -v /build/
```

Four claims to confirm, because the plan's shape changes if any of them moved:

1. **`NatsDispatcher.incoming` is still `protected final`.** If it became nullable, `-1` and `NOT_AVAILABLE` stop being subscription-only and the blast radius section is wrong.
2. **`setPendingLimits` still normalises unlimited to `Long.MAX_VALUE`.** If it went back to a non-positive sentinel, `getDeliverabilityState` needs V2's `> 0` guards after all.
3. **`cleanUpAfterDrain()` still runs before `tracker.complete(this.isDrained())`.** This is the whole reason `== 0` → `<= 0` is mandatory. If the ordering changed, re-derive that step rather than applying it.
4. **`isDrained()` still has exactly one `== 0` comparison in main sources**, and no new caller compares a pending count against `0`.

Also re-read [PR #1615](https://github.com/nats-io/nats.java/pull/1615) itself — it was **open and unmerged** when this was written, so it may have changed or been superseded. The V2 diff is the source of truth for intent; this plan is only the V3 translation of it.

</details>

## Name mapping

V3 renamed most of the participants, so the PR does not read across directly.

| V2 | V3 | Note |
|---|---|---|
| `NatsConsumer` | `NatsMessageSink` | `core/.../impl/NatsMessageSink.java` |
| `Consumer` (interface) | `Dispatcher` | only `Dispatcher` declares the pending getters; `Subscription` does not |
| `nextMessageInternal` | `_nextMessage` | renamed by the naming audit |
| `NatsConsumerPendingTests` | `NatsMessageSinkPendingTests` | new file, `core/src/test/java/io/synadia/client/impl/` |

## The defect sites in V3

Every one is the same shape: **the queue reference is read twice, and can be nulled by `invalidate()` on another thread between the two reads.**

| # | Site | Current code | Failure |
|---|---|---|---|
| 1 | `NatsMessageSink:76` | `getMessageQueue() != null ? getMessageQueue().length() : 0` | **NPE** on `.length()` |
| 2 | `NatsMessageSink:84` | `getMessageQueue() != null ? getMessageQueue().sizeInBytes() : 0` | **NPE** on `.sizeInBytes()` |
| 3 | `NatsMessageSink:141` | `if (getMessageQueue() != null) getMessageQueue().drain();` | **NPE** on `.drain()` |
| 4 | `NatsSubscription:90-91` | `if (this.incoming != null) this.incoming.pause();` | **NPE** on `.pause()` |
| 5 | `NatsSubscription:180-184` | `else if (this.incoming == null) throw;` … `incoming.pop(...)` | **NPE** on `.pop()` |
| 6 | `NatsConnection:2137-2151` | reads `q` once, then `s.hasReachedPendingLimits()` re-reads it twice more | judged queue ≠ pushed queue |

Site 6 is the one the PR is named for. `q` is already read exactly once at `:2137`, which looks safe — but `hasReachedPendingLimits()` (`NatsMessageSink:132`) goes back to the field via sites 1 and 2, so the verdict is formed against a *different* read than the push. If the field is nulled after `q` is captured, the getters return `0`, `hasReachedPendingLimits()` is false, and the `else if (q != null)` branch pushes to a queue that has already been paused and abandoned. The message is silently lost and **not counted as dropped**.

## The changes

### 1. `NatsMessageSink` — single-read getters, and `-1` for "no queue"

```java
public long getPendingMessageCount() {
    ConsumerMessageQueue copy = getMessageQueue();
    return copy == null ? -1 : copy.length();
}

public long getPendingByteCount() {
    ConsumerMessageQueue copy = getMessageQueue();
    return copy == null ? -1 : copy.sizeInBytes();
}
```

Same treatment for `markUnsubedForDrain()` (site 3).

### 2. `NatsMessageSink` — replace `hasReachedPendingLimits()` with a caller-supplied-queue state function

```java
enum DeliverabilityState { AVAILABLE, FULL, NOT_AVAILABLE }

// The queue is supplied by the caller rather than looked up here, so the caller's single read
// is the only read on the delivery path - the queue this judges is provably the same object the
// caller then pushes to. Looking it up again here would let an invalidate() on another thread
// come between the verdict and the push.
DeliverabilityState getDeliverabilityState(ConsumerMessageQueue queue) {
    if (queue == null) {
        return DeliverabilityState.NOT_AVAILABLE;
    }
    if (queue.length() >= maxMessages || queue.sizeInBytes() >= maxBytes) {
        return DeliverabilityState.FULL;
    }
    return DeliverabilityState.AVAILABLE;
}
```

**This is simpler than the V2 patch, and deliberately so.** V2 needs `ml > 0` / `bl > 0` guards because it stores "unlimited" as a non-positive sentinel. V3 normalises in `setPendingLimits` (`NatsMessageSink:49-50`) — `maxMessages <= 0 ? Long.MAX_VALUE : maxMessages` — so unlimited is `Long.MAX_VALUE` and `length() >= Long.MAX_VALUE` is simply false. Do not copy V2's guards; they would be dead code here.

Naming: no `_` marker. It is package-private but it is not the internal half of a public method, so it takes a plain composed name, consistent with the `hasReachedPendingLimits` it replaces and with `markSlow` / `isDrained` / `isDraining` beside it. See the settled rule in `z-claude-done/UNDERSCORE_INTERNAL_NAMING_AUDIT.md`.

### 3. `NatsConnection.deliverMessage` — switch on the state

Replace the `if (hasReachedPendingLimits()) / else if (q != null)` pair at `:2139-2156` with a switch over `s.getDeliverabilityState(q)`, keeping `q` as the single read it already is. `AVAILABLE` → `markNotSlow()`, run `getBeforeQueueProcessor()`, push. `FULL` → count the drop and notify slow once. `NOT_AVAILABLE` → count the drop, and **do not mark slow** — a subscription that has gone away is not a slow consumer.

This closes a real gap: today the lost-queue case silently drops without incrementing either counter.

### 4. `NatsSubscription` — copy before dereferencing

`invalidate()` (site 4) and `_nextMessage` (site 5) each take a local copy first. The post-pop check needs both halves and the reason is not obvious, so it carries the comment:

```java
// the field read catches invalidate() nulling it, the isRunning() catches pause(). Both are
// needed: invalidate() pauses before nulling, but the pause is a CAS from RUNNING, so a queue
// that was already DRAINING stays DRAINING and isRunning() alone would miss it. Reading the
// field is only a null comparison - copy is what gets dereferenced, and copy cannot be null here.
if (this.incoming == null || !copy.isRunning()) {
```

Confirmed against V3's `MessageQueueBase`: `pause()` is `running.compareAndSet(RUNNING, PAUSED)` and `drain()` is `compareAndSet(RUNNING, DRAINING)`, so the CAS-from-RUNNING reasoning holds here exactly as in V2.

### 5. `isDrained()` — `== 0` must become `<= 0`

`NatsMessageSink:155` reads `isDraining() && getPendingMessageCount() == 0`. **This is not optional and it is not cosmetic.** The ordering in `drain()` makes it load-bearing:

`NatsMessageSink:204` calls `cleanUpAfterDrain()` → `NatsSubscription:234` calls `connection.invalidate(this)` → `NatsConnection:1192` calls `sub.invalidate()` → `incoming = null`. Only *then*, in the `finally` at `:209`, does `tracker.complete(this.isDrained())` run.

So on every subscription drain the deciding `isDrained()` is evaluated with the queue already gone. With `-1` and an unchanged `== 0`, **every subscription drain future would complete `false`** — a straightforward regression. With `<= 0` it completes `true`, which is also what it does today.

Worth recording that today's `true` is partly a false success, exactly as the V2 PR notes: because the count is forced to `0` once the queue is gone, a drain that timed out with messages still queued still reports `true`. `<= 0` preserves that behaviour rather than fixing it. Fixing it is a separate question and should not ride along here.

## Blast radius of the `-1` change

Deliberately narrow in V3, and narrower than in V2:

* **`NatsDispatcher.incoming` is `protected final`** (`NatsDispatcher:29`) and assigned in the constructor, so a dispatcher's queue is never null. `NOT_AVAILABLE` and `-1` are reachable **only through a `NatsSubscription`**, whose `incoming` is private and non-final (`NatsSubscription:24`). The `Dispatcher` interface javadoc should still be updated for accuracy, but no dispatcher will return `-1`.
* **The only main-source comparison against `0` is `isDrained()`**, handled above. Nothing in `jetstream` or `service` calls either getter.
* **Existing tests are unaffected.** All eleven assertions in `SlowConsumerTests` are on live queues expecting `1` or a byte count; none exercises the gone-queue case.

Javadoc to update: `Dispatcher:87` and `:93`, plus `NatsMessageSink:73-74` and `:81-82`, each gaining "or -1 if the queue is not available".

## Tests

New `core/src/test/java/io/synadia/client/impl/NatsMessageSinkPendingTests.java`, porting #1615's `NatsConsumerPendingTests`:

1. **The single-lookup contract.** A harness whose `getMessageQueue()` hands out the queue on the first call and `null` on every call after — which is precisely what a concurrent `invalidate()` looks like to a method that reads twice. This is the test that actually pins the fix; without it the change is invisible.
2. **The `-1` contract** for both getters.
3. **All three `DeliverabilityState` outcomes**, including that `NOT_AVAILABLE` counts a drop and does *not* mark slow.
4. **The `NOT_AVAILABLE` delivery path end to end** through `deliverMessage`.
5. **Unsubscribe while another thread is blocked in `nextMessage`** — site 5.

Add #1614's regression test to `SlowConsumerTests` as the PR does, so nothing is lost by porting #1615 alone.

~~**Add a drain assertion too**, which the V2 PR does not have: assert the subscription drain future still completes `true` after the `<= 0` change. That is the one place where getting the port half-right produces a silent behavioural regression, and no existing test covers it.~~

**Not needed - that claim was wrong.** `DrainTests` already covers it three times over. Verified by putting `== 0` back with everything else in place: `testSimpleSubDrain`, `testDrainWithZeroTimeout` and `testDrainWithLotsOfMessages` all fail, on every retry. A redundant assertion in the new file would add nothing, so none was written.

## Sequencing and risk

* **All four main-source files are already dirty** — `NatsConnection.java`, `NatsSubscription.java`, `NatsMessageSink.java` is not but `NatsDispatcher.java` and `MessageQueueBase.java` are — and the naming work is `Under Review`. Land this after that review closes, or expect to hand-merge `deliverMessage` and `_nextMessage`.
* **PR #1615 is still open upstream.** If it changes before merge, re-read it rather than trusting this plan; the V2 diff is the source of truth for intent.
* **`processSlowConsumer` takes different arguments in the two codebases** — V2 passes the consumer (`c`, which may be the dispatcher), V3 passes `sub` unconditionally (`NatsConnection:2147`, signature at `:2189` takes a `Subscription`). Do not "fix" this while porting. It is a real discrepancy worth its own look, and conflating it with a concurrency fix would make both harder to review.

## Implemented 2026-08-25

All five changes are in the working tree, matching the merged #1615 diff. Both halves were verified by reverting them in isolation with everything else in place:

* **The single-read getters.** With the double read restored, `NatsMessageSinkPendingTests.testPendingMessageCountUsesSingleQueueLookup`, `testPendingByteCountUsesSingleQueueLookup` and `SlowConsumerTests.testPendingCountsUseSingleQueueLookup` all fail with `NullPointerException: Cannot invoke "ConsumerMessageQueue.length()" because the return value of "NatsMessageSink.getMessageQueue()" is null` - the reported crash, reproduced deterministically. `testNoQueueReportsMinusOne` fails too, since the old form returns `0`.
* **`isDrained()` `<= 0`.** See above - three existing `DrainTests` fail with `== 0`.

Two V3-specific notes for anyone reading this against the V2 diff:

* `getDeliverabilityState` has no `> 0` limit guards, on purpose. V2 needs them because it stores unlimited as a non-positive sentinel; V3 normalises to `Long.MAX_VALUE` in `setPendingLimits`, so `length() >= Long.MAX_VALUE` is simply false. Copying V2's guards would be dead code.
* `deliverMessage` uses an arrow `switch` rather than V2's `case`/`break` form, matching `NatsConnection:2585`.

`processSlowConsumer(sub)` was left alone as the plan says - V3 passes the subscription unconditionally where V2 passes the consumer. Still a real discrepancy, still its own question.
