# V3 review note — jnats V2 issue #1596 (ordered consumer create failure)

**State: COMPLETE (2026-08-13), archived 2026-08-14.** All five checks were done against the tree, not sampled. The reported V2 bug is structurally impossible in V3 (finding 1). Findings 2 and 3 were real and are fixed and pushed — `0537c485` (silent ordered-consumer death, the abandoned-subscription leak, and `ScheduledTask` recording rather than swallowing) and `c47bb0d7` (a consumer created for a subscribe that then failed is now deleted instead of orphaned server-side); both builds green on CI. Finding 4 needed no code and is recorded as field motivation in `REQUEST_BEHAVIOR_IMPROVEMENT.md` — a V3 user hitting this close race gets a `JetStreamTimeoutException` with no way to tell "connection closing" from "slow server", which is exactly what that plan's `RequestFailureReason.CONNECTION_CLOSING` fixes, and it is the one place V3 is currently behind V2. Finding 5 is dormant, not absent, and is flagged for whoever next writes cleanup code near `NatsSubscription.invalidate()`.

One thing found here but deliberately not fixed: the `SimplificationTests._testFetch` flake mechanism (a 100 ms `elapsed` budget) is written up in `FLAKY_TESTS_ANALYSIS.md` with a proposed fix, deferred with the rest of the flaky work.


Written 2026-08-12 from the V2 repo (`nats-io/nats.java`, `main` @ 830c46af). **This note is self-contained** — the complete V2 analysis is copied in below, so there is no need to open anything in the V2 repo or on GitHub to act on it.

## Why you are reading this

V2 issue [#1596](https://github.com/nats-io/nats.java/issues/1596): a user created an ordered consumer, the create timed out, and instead of the real failure they got `IllegalStateException("This subscription is inactive.")` thrown from the *cleanup* code. The V2 sequence is: create the delivery subscription, then create the consumer; if the consumer create fails, unsubscribe and rethrow. When a connection close races the create, the subscription has already been invalidated, so the unsubscribe itself throws and replaces the real exception.

The V2 symptom is fixed (2.26.1), but the analysis turned up three related gaps. The question for V3 is whether any of it applies here.

## What I already checked in V3

Bounded look only — I did not review the code, just located the analogous constructs. Treat all of this as a starting point to verify, not as findings.

**The specific bug appears to be structurally absent.** V3 inverted the ordering: `JetStream.createSubscription` takes a `ConsumerInfo` as its first parameter (`jetstream/.../impl/JetStream.java:536`), so the consumer is created *before* the subscription on every path I sampled — e.g. `createSubscription(_createConsumer(stream, creator, Create), ...)` at `JetStream.java:995`, and `NatsConsumerContext.java:75` creates the consumer then `:96` creates the subscription. There is no "subscription exists, consumer create failed, now unsubscribe" window, and no `_createConsumerUnsubscribeOnException` equivalent exists in the tree.

**The surrounding machinery is all still there,** in `io.synadia.client.impl`: `NatsSubscription` still has the `"This subscription is inactive."` throw, `NatsConnection` has `invalidate`, `subscribers.forEach`, and `cleanResponses`, and `NatsMessageConsumer` / `PullOrderedMessageManager` still have `pullTerminatedByError`.

## What I found in V3 — review completed 2026-08-13

All five checks done against the working tree. Verdict up front: **the reported V2 bug cannot happen in V3, but two of the three underlying gaps are present, and one of them leaves V3 worse off than V2.** No code was changed — these are findings, and items 2 and 3 are product changes in the JetStream consumer path that want a decision before anyone writes them.

| # | Check | Result |
|---|---|---|
| 1 | Consumer-first ordering universal | **Confirmed** — the V2 bug is structurally impossible here |
| 2 | Mirror-image consumer leak | **Real, and worse than V2's** — no cleanup attempt exists at all |
| 3 | Silent ordered-consumer death on an unchecked exception | **Applies, both halves** |
| 4 | Close-ordering race window | **Identical to V2** — but V3 has no way to diagnose it |
| 5 | `getDispatcher()` trap | **Structurally present, not currently reachable** — a landmine for the fix to item 2 |

### 1. Consumer-first ordering is universal — confirmed, not sampled

All 24 `createSubscription(...)` call sites pass a `ConsumerInfo` as the first argument, so it is fully evaluated before the subscription is touched. They fall into two groups:

* **Bind/get paths** — an existing `consumerInfo` parameter, or `strictGetConsumerInfo(stream, consumerName)`. No consumer is created by us, so there is nothing to leak.
* **Create paths** — `_createConsumer(stream, creator, Create)` inline in the argument list (`JetStream.java:995, 1012, 1028, 1042, 1060, 1076`), and `NatsConsumerContext.java:75` creating before `:96` subscribes.

There is no `_createConsumerUnsubscribeOnException` equivalent, and no path anywhere that creates a subscription and then a consumer. **The exception the reporter saw has no code path in V3** — there is no cleanup unsubscribe for it to be thrown from.

### 2. The mirror-image leak is real, and V3 is in a worse position than V2

`JetStream.createSubscription` (`JetStream.java:536-588`) has **no try/catch at all**. Once the consumer exists, three separate things in it can throw, and all three are the close/drain race:

| Throw site | Exception |
|---|---|
| `new JetStreamSubscribeConfig(..., conn::createDispatcher)` at `:541` — dispatcher creation | `IllegalStateException("NatsConnection is Closed" / "is Draining")` — `NatsConnection:1732, 1735` |
| `conn.createSubscriptionInternal(...)` at `:581` | the same two — `NatsConnection:1243, 1246` |
| `dispatcher.subscribeImplByFactory(...)` at `:586` | `IllegalStateException("Dispatcher is closed" / "is draining")` — `NatsDispatcher:294, 298` |

In every case the just-created consumer is **orphaned on the server** and the caller gets an ISE. Severity depends on what was created: an ordered consumer carries an `inactiveThreshold` so the server reaps it, but a durable or long-threshold consumer from any of the six `_createConsumer(... Create)` paths simply persists.

Note the asymmetry with V2. V2 leaks a *client-side* subscription — recoverable, and gone entirely once the connection closes — and at least *attempts* cleanup. V3 leaks *server-side* state and attempts nothing. V2's gap 1 was "the cleanup only fires for checked exceptions"; V3's is "there is no cleanup".

### 3. The silent-death gap applies, both halves

**The catch clauses are too narrow.** `NatsMessageConsumer.doSub` (`:141-163`) catches only `JetStreamException`, and `pullTerminatedByError` (`:117-139`) catches only `JetStreamException` and `InterruptedException`. An unchecked exception — precisely the ISEs from finding 2, since `doSub` calls straight back into `subscriptionMaker.subscribe(...)` — escapes both. By then `shutdownSub()` has already run and `resetOnException()` never will, so the consumer holds no subscription and no heartbeat timer, while `stopped` and `finished` both still report `false`. It silently stops delivering, permanently, with no notification. Same as V2 — and the deliberate `InterruptedException` arm shows the catch list was reasoned about without unchecked exceptions in mind.

**The scheduled-task wrapper swallows nothing.** `ScheduledTask.run()` (`:96-105`) is `try { ... } finally { executing.set(false); }` with no `catch`, and `:104` schedules through `scheduleAtFixedRate`. An exception escaping the heartbeat alarm permanently cancels that task — identical to V2's `ScheduledTask.java:82`. This is the one finding that is not JetStream-specific: it applies to every `ScheduledTask` user in core.

### 4. The close-ordering window is identical — and V3 has no way to diagnose it

`NatsConnection.close` runs in exactly V2's order:

```
:874  dispatchers.forEach(d -> d.stop(false));
:876  subscribers.forEach((sid, sub) -> sub.invalidate());
:879  subscribers.clear();
:890  cleanResponses(true);        // cancels in-flight requests
:896  updateStatus(CLOSED);        // only now is isClosed() true
```

The guards that would have produced an honest error test `isClosed()` — `createSubscriptionInternal` at `:1243` and `createDispatcher` at `:1732` — so throughout `:874`-`:896` they do not fire. The misleading-timeout window is the same size and shape as V2's.

**Where V3 differs is the remedy, and not in its favour.** V2's answer for the reporter is `advancedRequestBehavior()`, which turns the generic timeout into a `RequestFailureException` carrying `CONNECTION_CLOSING`. **V3 has no equivalent** — PR #1582 was never ported; `REQUEST_BEHAVIOR_IMPROVEMENT.md` redesigns rather than ports it and is still an unimplemented plan. V3 does throw a dedicated `JetStreamTimeoutException` rather than V2's generic `IOException("Timeout or no response...")`, which is a better *type*, but it carries no reason — a V3 user in this exact scenario cannot distinguish "the connection was closing" from "the server was slow". That is a concrete, user-visible argument for that plan's `RequestFailureReason.CONNECTION_CLOSING`, and worth attaching to it as motivation.

### 5. The `getDispatcher()` trap is present but currently unreachable

`NatsSubscription.invalidate()` (`:79-85`) nulls `dispatcher` and `incoming` together, exactly as V2 does, so an invalidated dispatcher-backed subscription still misreports its provenance. Both `"This subscription is inactive."` throws are still there (`:181`, `:215`).

But the dangerous pattern has exactly one occurrence in the whole tree — `NatsConnection.java:441`:

```java
this.subscribers.forEach((sid, sub) -> {
    if (sub.getDispatcher() == null && !sub.isDraining()) {
        sendSubscriptionMessage(sub.getSID(), sub.getSubject(), sub.getQueueName(), true);
    }
});
```

That is the reconnect resubscribe path, not cleanup, and it does not run against invalidated subscriptions: invalidation happens in `close`, which also clears `subscribers` at `:879`. **Nothing to fix today.**

It matters anyway, because the fix for finding 2 is exactly the kind of code that would step on it. Whoever writes that must branch on `isActive()` first, never on `getDispatcher() == null`. The correct pattern already exists in the tree — `NatsMessageConsumerBase.shutdownSub()` (`:108`) tests `sub.isActive()` before choosing.

## Implementation status

**This review is complete (2026-08-13).** Findings 2 and 3 implemented and pushed (`0537c485`, `c47bb0d7`); finding 4 recorded as motivation in `REQUEST_BEHAVIOR_IMPROVEMENT.md`; findings 1 and 5 needed no change.

### Finding 3 — done

Two changes, each verified by a test that fails without it:

* **`NatsMessageConsumer.doSub` (`:159`)** — `catch (JetStreamException e)` widened to `catch (JetStreamException | RuntimeException e)`. Precise rethrow keeps the `first == true` path propagating exactly as before, so a construction-time failure still reaches the caller; only the reset path (`first == false`) now recovers instead of dying. Test: `SimplificationTests.testResetSurvivesUncheckedSubscribeFailure` wraps a real `NatsConsumerContext` in a `SimplifiedSubscriptionMaker` that succeeds once and then throws `IllegalStateException`, then drives `pullTerminatedByError()`. **Narrow the catch back and it fails** with `Unexpected exception thrown: java.lang.IllegalStateException: simulated: NatsConnection is Closed`.
* **`ScheduledTask.run()` (`:131`)** — **records the exception and rethrows it unchanged**; it does not swallow. An exception out of a `scheduleAtFixedRate` task ends the schedule, which is the executor's contract and the right outcome, because a runnable that throws has a coding error and hiding it would be worse than stopping. What the executor does *not* do is report it: it keeps the exception only on the scheduled future, and `shutdown()` releases that future, so the exception became unreadable exactly when someone would go looking. It is now captured into a field on the way out and exposed by a new `getException()`, so it survives `shutdown()`. `Throwable` rather than `Exception`, so an `Error` that kills a timer is recorded too. Tests: `ScheduledTaskTests.testThrowingRunnableEndsTheScheduleAndKeepsTheExceptionReadable` asserts the schedule really does end (`runs == 1`, `isDone()`), that the exception is the same instance, and that it is **still readable after `shutdown()`**; a companion asserts null for a live task and for a cleanly shut down one. **Delete the `exceptionRef.set(t)` and it fails** with `expected: <IllegalStateException...> but was: <null>`.
* **`NatsMessageConsumer.doSub` — the abandoned subscription (`:165-171`)** — `doSub` runs two more statements after the subscribe (`fullResetPending()`, `rePull()`). If either throws, the attempt is abandoned while holding a live subscription, and on the `first == true` path the consumer never reaches the caller, so the subscription and its heartbeat timer are unreachable and leak. A `subInitialized` flag now records whether the subscribe succeeded, and the catch calls `shutdownSub()` when it did. **The flag is necessary, not cosmetic:** `shutdownSub()` dereferences `sub` with no null check, and on the reset path `sub` still refers to the previous, already shut down subscription, so an unguarded cleanup would act on the wrong object or NPE. Test: `SimplificationTests.testSubscriptionIsCleanedUpWhenDoSubFailsAfterSubscribing` subclasses the consumer to throw from `rePull()`, captures the subscription the maker handed out, and asserts it is inactive after the constructor fails. **Disable the cleanup and it fails** with `the abandoned subscription must have been cleaned up ==> expected: <false> but was: <true>`.

`:core:test` and `:jetstream:test` both fully green. Javadoc 0 warnings / 0 errors.

### Finding 2 — done

The orphaned consumer. `JetStream.createSubscription` still has no try/catch and does not need one - it creates nothing itself. The knowledge of "this call created a consumer" lives at the call sites, so the cleanup does too, in a new `JetStream.subscribeDeleteConsumerOnException(stream, consumerInfo, ...)` that wraps the subscribe and deletes the consumer if it fails. The name mirrors V2's `_createConsumerUnsubscribeOnException` - the same operation inverted, since V2 creates a consumer and unsubscribes on failure while V3 subscribes and deletes the consumer on failure. The short name does not carry the precondition, so it is stated in bold at the top of the javadoc: only pass a consumer this call created, because passing a bound one would delete a consumer the caller owns.

**Why the split matters.** Of the 24 `createSubscription` call sites, only 10 create the consumer they subscribe to; the other 14 bind to one the caller already owns (`consumerInfo` parameter or `strictGetConsumerInfo`) and must never delete it. Passing a flag through `createSubscription` would have put that decision in the one place that cannot answer it - a created and a bound `ConsumerInfo` are indistinguishable by then. Converted: nine sites in `JetStream`, all written the same way - `ConsumerInfo ci = _createConsumer(stream, creator, Create);` on its own line, then the wrapper with `ci` - plus `NatsConsumerContext.subscribe`, where `isOrdered` is exactly the condition under which the method created the consumer, so it selects the wrapper. Six of the nine had the create inline in the argument list; they were pulled out so every create-then-subscribe pair reads identically and the two steps are visibly separate. `JetStream.createConsumer` (`:1115`) creates a consumer but never subscribes, so it is untouched - that consumer is the caller's deliverable, not a subscription's backing.

**Deleting is right even for a named durable.** The action is `Create`, so a successful create proves the consumer did not exist beforehand. Deleting restores the state the caller started in, and it means a retry is not met with "consumer already exists" - the alternative to deleting is a half-finished call the caller cannot repeat.

**The cleanup never replaces the original failure.** A delete that throws is attached with `addSuppressed`, and an `InterruptedException` from it also restores the interrupt flag. Letting cleanup mask the real exception is precisely the bug jnats V2 #1596 was filed for, and this is the same shape of code, so it does not repeat it.

**It is best effort, and honestly so.** The two causes are not equal. A closing or draining *connection* fails the subscribe and also prevents the delete from being sent, so the orphan survives - though in that case the connection is going away anyway. A closed or draining *dispatcher* fails the subscribe while the connection stays healthy, and there the delete goes through. The second case is the one worth having, and it is what the test exercises.

`_deleteConsumer(stream, consumerName)` was added to `JetStreamImpl` next to `_createConsumer`, and `JetStreamManagement.deleteConsumer` delegates to it rather than duplicating the subject and response handling. **The internal one deliberately does not validate** - there is no point splitting it out if it just repeats what the public method does. Every caller already knows both arguments are good: the public method validates before delegating, and the delete-on-failed-subscribe path takes its stream from a create that already ran `validateStreamName` and its name from the `ConsumerInfo` that create returned, which is never null once a consumer exists. The public method's own validation is unchanged, so its behavior is identical to before the split.

Test: `SimplificationTests.testConsumerIsDeletedWhenTheSubscribeFails` closes a dispatcher, subscribes through it with a named durable, asserts the `IllegalStateException` still reaches the caller, and asserts the durable is absent from `getConsumerNames()`. **Remove the cleanup and it fails** with `a consumer created for a subscribe that failed must be deleted, not orphaned ==> expected: <false> but was: <true>`.

### A flaky-test finding that came out of verifying this

`SimplificationTests.testFetchDurable` failed 5/5 in the first full run, which looked like a regression. It is not. The assertion is `assertTrue(elapsed < 100)` at `SimplificationTests.java:286` — a **100 ms budget for a fetch round trip** — and it is load-sensitive, not correctness-sensitive.

Proven rather than assumed: with my source reverted to be byte-identical to `HEAD`, the test still failed **3/3** in this working tree, while a `git worktree` at the same commit passed **3/3** concurrently. Same code, same machine, opposite results — so the variable is machine load, not the change. Reverting the build directories and cleaning made no difference. On a later run `testFetchDurable` passed and a sibling, `testOverflowFetch`, flaked instead and passed on retry; that wandering between tests in the same class is the signature.

`testFetchDurable` is already on the watch list in `FLAKY_TESTS_ANALYSIS.md`. This adds the mechanism: **the 100 ms elapsed-time assertion in `_testFetch` cases 1A/1B/2B**, which is the tightest timing budget in that file. Worth recording there, and a candidate for widening on its own merits.

## Recommendation

Findings 2 and 3 are worth fixing; 1 and 5 need nothing; 4 is a pointer into an existing plan rather than work of its own.

1. **Finding 3 first — smallest, most self-contained, highest severity.** Widening two catch clauses to include `RuntimeException` turns a permanent silent stall into a recovery, and adding a `catch` to `ScheduledTask.run()` stops one bad alarm from killing a timer forever. Neither changes a public signature. The `ScheduledTask` half reaches every user of it in core, so it wants its own look rather than being folded in.
2. **Finding 2 next**, since it is the source of the unchecked exceptions finding 3 has to survive. The shape is a try/catch in `createSubscription` that deletes the consumer it just created — but only on the paths that created one, which means separating the create paths from the bind/get paths. That is a design decision, not a mechanical edit.
3. ~~**Finding 4** — record the "cannot tell CONNECTION_CLOSING from a slow server" argument in `REQUEST_BEHAVIOR_IMPROVEMENT.md` as motivation.~~ **Done 2026-08-13** — written up there as "Motivation from the field", including that V3 is currently *behind* V2 on this, since V2 can answer it with `advancedRequestBehavior()` and V3 has no equivalent until that plan lands.

None of this is urgent: all of it requires losing a race with a connection close, which is why V2 ran for years before anyone reported it.
---

The complete V2 analysis follows, verbatim. All file, line, and commit references in it are V2 (`nats-io/nats.java`) unless stated otherwise.

---

# Issue #1596 — "Ordered consumer create failures throw unrelated exception"

Reported against 2.26.0 by jpsugar-flow. Analysis date 2026-08-12, against `main` @ 830c46af.

## Verdict

The reported symptom is real and is **already fixed on `main` / in 2.26.1** by commit `cb97bc8a` ("Better exception raising on create consumer", 2026-07-14 — the day after the issue was filed). It has no test and no release note, which is probably why the issue is still open.

The root cause underneath the symptom is a **connection close racing an in-flight consumer-create**, and there are three related soft spots that `cb97bc8a` does not cover. Details and suggested hardening below.

## What the stack trace actually says

Every frame maps cleanly onto the 2.26.0 tag:

| Frame | 2.26.0 source line |
|---|---|
| `NatsOrderedConsumerContext.consume:133` | `return impl.consume(handler);` |
| `NatsConsumerContext.consume:304` | `return consume(DEFAULT_CONSUME_OPTIONS, null, handler);` |
| `NatsConsumerContext.consume:342` | `trackConsume(new NatsMessageConsumer(...))` |
| `NatsMessageConsumer.<init>:59` | `doSub(true);` |
| `NatsMessageConsumer.doSub:158` | `super.initSub(subscriptionMaker.subscribe(mh, userDispatcher, pmm, null), !first);` |
| `NatsConsumerContext.subscribe:126` | `js.createSubscription(null, null, pso, null, (NatsDispatcher) d, messageHandler, false, optionalPmm)` — **the dispatcher branch** |
| `NatsJetStream.createSubscription:507` | `_createConsumerUnsubscribeOnException(settledStream, settledCC, sub);` |
| `NatsJetStreamImpl._createConsumerUnsubscribeOnException:142` | `sub.unsubscribe();` — the **`getDispatcher() == null`** branch |
| `NatsJetStreamSubscription:30` | class declaration line — a synthetic/bridge frame |
| `NatsSubscription.unsubscribe:183` | `throw new IllegalStateException("This subscription is inactive.");` (the `incoming == null` branch of the no-arg `unsubscribe()`) |

That combination is the interesting part. The subscription was created **with a dispatcher** (frame at `NatsConsumerContext:126`), yet by the time cleanup ran, `sub.getDispatcher()` returned `null` *and* `sub.incoming` was `null`. In `NatsSubscription` those two fields are nulled together in exactly one place — `invalidate()`:

```java
void invalidate() {
    if (this.incoming != null) { this.incoming.pause(); }
    this.dispatcher = null;
    this.incoming = null;
}
```

So the subscription had been invalidated between step 7 (create the delivery subscription) and step 8 (create the consumer) of `NatsJetStream.createSubscription`. Note the knock-on effect: once invalidated, a dispatcher-backed subscription lies about its provenance, so the cleanup code picks the *non*-dispatcher branch and calls `sub.unsubscribe()`, which is then guaranteed to throw. The branch test `sub.getDispatcher() == null` is not safe to use on a possibly-invalidated subscription.

## Root cause: connection close racing the create

`NatsSubscription.invalidate()` is reachable from only a few places, and only one of them can hit a subscription that was created milliseconds earlier and has never received a message: `NatsConnection.close(boolean, boolean)`:

```java
this.dispatchers.forEach((nuid, d) -> d.stop(false));
this.subscribers.forEach((sid, sub) -> sub.invalidate());   // line 915
this.dispatchers.clear();
this.subscribers.clear();
...
cleanResponses(true);                                        // line 929  -> cancels in-flight requests
...
updateStatus(Status.CLOSED);                                 // line 935  -> only now is isClosed() true
```

The ordering explains everything, including why the user saw a *timeout* rather than "Connection is Closed":

1. `consume()` publishes `$JS.API.CONSUMER.CREATE...` and blocks on the response future.
2. Another thread closes the connection (explicit `close()`, or reconnects exhausted / terminal failure, which routes through `closeSocket` → `close()`).
3. Line 915 invalidates **all** subscriptions, including the brand-new ordered-consumer inbox subscription.
4. Line 929 `cleanResponses(true)` cancels the pending request future (`future.cancelClosing()`).
5. `requestInternal` catches the `CancellationException` and returns `null`; `responseRequired(null)` throws `IOException("Timeout or no response waiting for NATS JetStream server")`.
6. `_createConsumerUnsubscribeOnException` catches it, tries to unsubscribe, and the invalidated subscription throws `IllegalStateException("This subscription is inactive.")` — which replaces the `IOException` on the way out.

Status is not set to `CLOSED` until line 935, so throughout this window `isClosed()` is `false` and the `IllegalStateException("Connection is Closed")` guards in `request`/`createDispatcher` never fire. The window is as wide as the reader/writer stop timeouts plus executor shutdown — easily long enough to lose a race, which matches the reporter's terse "create an ordered consumer that times out".

This is consistent with the reporter's environment: the ordered consumer is created while the connection is going down, so the create never gets an answer.

## Current state of the fix

`cb97bc8a` wraps the cleanup in a swallow-all try/catch:

```java
catch (IOException | JetStreamApiException e) {
    try {
        if (sub.getDispatcher() == null) { sub.unsubscribe(); }
        else { sub.getDispatcher().unsubscribe(sub); }
    }
    catch (Exception eUnSub) {
        // this exception is from the unsubscribe and isn't very useful.
        // capture this so we can return the original
    }
    throw e;
}
```

That resolves the reported complaint: the caller now gets the real `IOException`/`JetStreamApiException`. It shipped in **2.26.1** (tag dated 2026-07-29). The commit touched only `NatsJetStreamImpl.java` — no test was added.

### Provenance: no cleanup code was ever lost

The unsubscribe-on-create-failure cleanup has been continuously present since it was first needed. It was added inline in `NatsJetStream.createSubscription` step 7 by `ddad68db0` (#639, "subscription before consumer" — the change that made the delivery subscription get created *before* the consumer, which is what created the need for cleanup in the first place):

```java
catch (IOException | JetStreamApiException e) {
    // create consumer can fail, unsubscribe and then throw the exception to the user
    if (dispatcher == null) { sub.unsubscribe(); }
    else { dispatcher.unsubscribe(sub); }
    throw e;
}
```

`4f109272` (#654, "Ordered consumer improvements") extracted that block into `NatsJetStreamImplBase._createConsumerUnsubscribeOnException` (later `NatsJetStreamImpl`) with one change: the branch test moved from the caller's local `dispatcher` variable to `sub.getDispatcher()`. Nothing was deleted.

That swap is worth knowing about — it made the branch test unreliable after `invalidate()` — but it is **not** what caused this issue. In the close-race scenario all three forms throw:

- `sub.unsubscribe()` → `IllegalStateException("This subscription is inactive.")`, which is what was reported;
- `dispatcher.unsubscribe(sub)` on an invalidated sub → `IllegalStateException("Subscription is not managed by this Dispatcher")`, because `NatsDispatcher.unsubscribe(Subscription, int)` tests `subscription.getDispatcher() != this` and `getDispatcher()` is now null;
- and if the dispatcher was already stopped — which `NatsConnection.close` does at line 913, two lines *before* it invalidates the subscriptions — `IllegalStateException("Dispatcher is closed")` from `checkBeforeSubImpl`.

So the defect was never a missing cleanup call. It was the absence of a try/catch around one, which is exactly what `cb97bc8a` added.

The codebase used to carry an explicit note about this same trap: `#564` added, and `#654` removed, this line in `NatsSubscription.reSubscribe` — `NatsDispatcher d = dispatcher; // unsubscribe eventually calls invalidate which nulls out dispatcher`.

## Remaining gaps

### 1. Only checked exceptions trigger cleanup — the subscription leaks otherwise

`_createConsumer` can throw unchecked exceptions, and the same connection-close race is precisely when it does: `requestFutureInternal` throws `IllegalStateException("Connection is Closed")` / `("Connection is Draining")`, and `NatsDispatcher.checkBeforeSubImpl` throws `IllegalStateException("Dispatcher is closed")` / `("Dispatcher is draining")`. None of those are caught, so the just-created delivery subscription is never unsubscribed. It stays in `subscribers` (and in the dispatcher's maps), and for a JetStream subscription `invalidate()` is also what calls `manager.shutdown()` — so the message manager's heartbeat `ScheduledTask` keeps running against a dead subscription. If the connection is fully closed everything gets cleared anyway, but on the draining path and on any other unchecked failure this is a genuine leak.

### 2. The cleanup discards the secondary exception entirely

The `catch (Exception eUnSub)` block is empty. `addSuppressed` costs nothing and keeps the diagnostic. Also, the `getDispatcher() == null` test should be preceded by an `isActive()` guard — that is already the established pattern in `NatsMessageConsumerBase.shutdownSub()`:

```java
protected void shutdownSub() {
    try {
        if (sub.isActive()) {
            if (sub.getNatsDispatcher() != null) { sub.getDispatcher().unsubscribe(sub); }
            else { sub.unsubscribe(); }
        }
    }
    catch (Throwable ignore) { }
    ...
}
```

Suggested replacement for `NatsJetStreamImpl:134`:

```java
void _createConsumerUnsubscribeOnException(String stream, ConsumerConfiguration cc, NatsJetStreamSubscription sub) throws IOException, JetStreamApiException {
    try {
        ConsumerInfo ci = _createConsumer(stream, cc, ConsumerCreateRequest.Action.CreateOrUpdate);
        sub.setConsumerName(ci.getName());
    }
    catch (IOException | JetStreamApiException | RuntimeException e) {
        // create consumer can fail. unsubscribe, then throw the original exception to the user
        try {
            if (sub.isActive()) {
                // a subscription that has been invalidated reports a null dispatcher even
                // when it was created with one, so isActive must be checked first
                if (sub.getDispatcher() == null) {
                    sub.unsubscribe();
                }
                else {
                    sub.getDispatcher().unsubscribe(sub);
                }
            }
        }
        catch (Exception eUnSub) {
            // the unsubscribe exception is not the interesting one, but keep it for diagnostics
            e.addSuppressed(eUnSub);
            // the unsubscribe did not happen, so clean up the client side directly. invalidate
            // cannot throw, it removes the sub from the connection and from its dispatcher, and
            // it is what shuts the message manager's heartbeat timer down
            conn.invalidate(sub);
        }
        throw e;
    }
}
```

The multi-catch with `RuntimeException` still compiles under Java 8 — precise rethrow infers the union, and `RuntimeException` is unchecked so the `throws` clause does not need to change.

The `conn.invalidate(sub)` fallback matters because the `isActive()` guard alone does not close the leak. A subscription can be perfectly active while the *dispatcher* is closed or draining, in which case `dispatcher.unsubscribe(sub)` throws and the subscription is still registered. `NatsConnection.invalidate` is `protected` in the same package, so `NatsJetStreamImpl` can call it on its `conn` field, and it cannot throw: `remove(sub)` plus `sub.invalidate()`, and `MessageManager.shutdown()` is idempotent (it only calls `shutdownHeartbeatTimer`, which null-checks the task ref). The one thing it does not do is send `UNSUB` to the server, so server-side interest on the inbox lingers — but in every case where the real unsubscribe threw, the connection is closing or closed anyway and the interest dies with it.

### 3. The ordered-consumer auto-reset path can die silently on an unchecked exception

This is the same defect one level up, and it is the more consequential one.

`PullOrderedMessageManager.manage()` calls `pullManagerObserver.pullTerminatedByError()` whenever the consumer sequence gaps — the normal ordered-consumer reset. `NatsMessageConsumer.pullTerminatedByError()` does `shutdownSub(); doSub(false);`, and `doSub` catches only checked exceptions:

```java
catch (JetStreamApiException | IOException e) {
    if (first) { throw e; }
    resetOnException();
}
```

On the checked path the consumer recovers: `resetOnException()` re-arms the heartbeat timer, which alarms again and retries the reset. On an **unchecked** path (the ISEs from gap 1, and in 2.26.0 the `IllegalStateException` from this very issue) the exception escapes `doSub`:

- `consume(handler)` — `manage()` runs inside `AsyncMessageHandler.onMessage` on the dispatcher thread; `NatsDispatcher` catches it and routes to `connection.processException(...)`. The consumer is now permanently dead — `shutdownSub()` already ran, `resetOnException()` never did, so there is no subscription and no heartbeat timer — yet `isStopped()` and `isFinished()` both still report `false`. It silently stops delivering, forever.
- `iterate()` / `next()` — `manage()` runs on the caller's `nextMessage()` thread, so the user gets an unrelated `IllegalStateException` out of `nextMessage()`. Same class of complaint as this issue.
- heartbeat alarm — `MessageManager.initOrResetHeartbeatTimer` runs the alarm inside a `ScheduledTask`, whose `run()` has a `try/finally` but no `catch`. An exception propagating out of a `scheduleAtFixedRate` task permanently cancels that task.

Minimal fix, mirroring the above:

```java
catch (JetStreamApiException | IOException | RuntimeException e) {
    if (first) { throw e; }
    resetOnException();
}
```

Worth considering alongside it: `resetOnException()` is completely silent, so a repeatedly-failing ordered-consumer reset gives the user nothing to look at. A `conn.notifyErrorListener(...)` there would help. `NatsFetchConsumer` and `NatsNextConsumer` are not affected — their `pullTerminatedByError` just calls `fullClose()` and does not re-subscribe.

## For the reporter

Two things to tell jpsugar-flow:

1. **Upgrade to 2.26.1+.** The `IllegalStateException` no longer masks the real failure.
2. **Enable `advancedRequestBehavior()`** (added in 2.26.0, `Options.Builder.advancedRequestBehavior()`). Without it the underlying failure is the generic `IOException("Timeout or no response waiting for NATS JetStream server")`, which is barely more informative than the exception it replaced. With it, JetStream API calls that get no response throw `RequestFailureException` (an `IOException`, so it is still caught and rethrown by `_createConsumerUnsubscribeOnException`) carrying a `RequestFailureReason` plus the connection status and last protocol error. If the root-cause theory above is right, the reason will come back as **`CONNECTION_CLOSING`** — `cleanResponses(true)` calls `future.cancelClosing()`, and `RequestFailureMessage.classify` maps that to `CONNECTION_CLOSING`. That single field confirms or refutes the diagnosis without any further server-side information.

It is also worth asking whether they close the connection from another thread, or whether they saw a `ConnectionListener` CLOSED / reconnect-exhausted event around the same timestamp.

## Suggested tests

None of this is currently covered.

- Unit-level, deterministic, no timing race: build a JetStream subscription, invalidate it (`conn.invalidate(sub)`), then drive `_createConsumerUnsubscribeOnException` with a `_createConsumer` that throws. Assert the thrown exception is the original, and that the `IllegalStateException` is present in `getSuppressed()`. Tests already live in `io.nats.client.impl`, so the package-private surface is reachable.
- Leak coverage for gap 1: same setup, but have the create throw `IllegalStateException`, and assert the delivery subscription is gone from `subscribers` afterwards.
- Recovery coverage for gap 3: force `doSub(false)` to fail with an unchecked exception on the reset path and assert the consumer either recovers or reports itself stopped — never silently idles.

## File/line reference

- `src/main/java/io/nats/client/impl/NatsJetStreamImpl.java:134` — `_createConsumerUnsubscribeOnException`
- `src/main/java/io/nats/client/impl/NatsJetStream.java:507` — the only caller
- `src/main/java/io/nats/client/impl/NatsSubscription.java:79` — `invalidate()`, and `:178` — `unsubscribe()`
- `src/main/java/io/nats/client/impl/NatsConnection.java:915` — invalidate-all during close; `:929` — `cleanResponses(true)`; `:935` — status set to CLOSED
- `src/main/java/io/nats/client/impl/NatsMessageConsumer.java:130` — `pullTerminatedByError`; `:150` — `doSub`
- `src/main/java/io/nats/client/impl/PullOrderedMessageManager.java:61` — the reset trigger
- `src/main/java/io/nats/client/support/ScheduledTask.java:82` — `run()` with no catch
