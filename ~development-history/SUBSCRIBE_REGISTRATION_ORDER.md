# A message could be delivered before its handler was registered

Found 2026-08-26 while chasing a `JetStreamSubscribeTests.testJetStreamSubscribe` timeout where the message never reached the handler. The ordering has been wrong since `2f22b9160 "Start V3"`, and v2 has the same shape - see `SUBSCRIBE_REGISTRATION_ORDER_REVIEW.md` in the nats.java repo.

## The defect

`NatsDispatcher` registered the user handler **after** the SUB had gone to the server:

```java
NatsSubscription sub = connection._createSubscriptionByFactory(subject, queueName, this, nsf);
//                     ^ ends with sendSubscriptionMessage - the server can push from here
trackSubWithUserHandler(sub.getSID(), sub, handler);
//  ^ nonDefaultHandlerBySid.put(sid, handler) only now
```

The sid *was* in `subscriptions` before the send, so `deliverMessage` found the subscription and queued the message onto the dispatcher. The handler was not. The run loop resolves `nonDefaultHandlerBySid` then falls back to `defaultHandler`, and an async JetStream subscribe builds its dispatcher with `conn::createDispatcher` - the no-arg overload - so `defaultHandler` is **null**. No fallback, and no `else`: the message was discarded with no counter, no listener callback, no exception.

`reSubscribe` was worse - it sent the SUB *before* the sid was even in `subscriptions`, so a fast push was dropped by `deliverMessage` itself. That is the ordered push consumer reset path.

**Why it hid for so long.** The window is a few instructions, so the round trip - SUB out, server pushes, reader parses, deliver, queue, dispatcher pops - has to beat them. It only opens when the server has a message *already waiting*, so it pushes the instant it has the SUB. `testJetStreamSubscribe` publishes, creates a durable consumer, then subscribes with a handler - exactly that. Ordinary subscribe-then-publish tests never open it. It was never reproduced in 935 Windows runs plus 7 in WSL; it was found by reading, and pinned by asserting the ordering rather than racing it.

## The fix

**Subscribe - track inside the factory.** `NatsSubscriptionFactory` is already invoked by the connection once the sid exists and before the SUB is sent, which is precisely the safe window. So the dispatcher wraps its own factory rather than anything being threaded through the connection:

```java
NatsSubscriptionFactory base = nsf == null ? NatsSubscription::new : nsf;
return connection._createSubscriptionByFactory(subject, queueGroup, this,
    (sid, factorySubject, factoryQueueName, factoryConn, factoryDispatcher) -> {
        NatsSubscription sub = base.createNatsSubscription(...);
        trackSubWithUserHandler(sid, sub, handler);
        return sub;
    });
```

No new type, no new parameter, `_createSubscriptionByFactory` unchanged at four arguments, tracking still private to the dispatcher, and the connection still knows nothing about handlers. `NatsSubscription::new` matches the factory signature, so the null-factory case costs one line instead of duplicating construction.

**Resubscribe - the caller takes the sid.** There is no factory to hang it on, so `NatsDispatcher.reSubscribe` asks for the sid up front, tracks, and only then has the connection announce it. `NatsConnection.reSubscribe` takes the sid, and its `subscriptions.put` moved ahead of the send, fixing the second defect in the same edit.

**Unroutable messages are counted.** The run loop gained the missing `else`: no handler means `incrementDroppedCount()`. This is what would have surfaced the bug years earlier - it was losing messages without touching a single counter. The branch stays reachable legitimately, because `connection.remove(sub)` on the resubscribe path clears the dispatcher's maps without invalidating the subscription, so in-flight messages for the replaced sid land there and should be dropped.

## Two things found while in here

**`subscribe(String, String)` did not guard a null default handler** while `subscribe(String)` did, and both take the default-handler path. So on a `createDispatcher()` dispatcher - no default handler, which is what JetStream's consumer context, watch subscriptions and Service all use - this succeeded and returned a subscription that could never route anything:

```java
conn.createDispatcher().subscribe("subject", "queue");
```

The guard now sits at the `_subscribeCoreDefaultHandler` choke point so a future overload cannot forget it.

**The default-handler path needs no sid-keyed tracking.** `defaultHandler` is `final`, set in the constructor, so it is in place before the dispatcher can be subscribed with at all. Its subject-keyed map is management only - dedup, unsubscribe-by-subject, reconnect resend, drain - never routing. The asymmetry with the user-handler path is correct, not an oversight.

## Structure that came out of it

`_subscribeCore` was split into `_subscribeCoreDefaultHandler` and `_subscribeCoreUserHandler`. The two paths only looked alike: one needs a sid-keyed handler, the other categorically does not, and that is far easier to see split than behind a `handler == null` branch. Some validation is duplicated; worth it.

`NatsDispatcherWithExecutor` no longer overrides `run()`. The only difference between the two loops was that the final block ran as a task, so the base gained a `deliverToHandler(handler, msg, sub)` template method and the subclass is now:

```java
@Override
protected void deliverToHandler(MessageHandler handler, NatsMessage msg, NatsSubscription sub) {
    connection.getExecutor().execute(() -> super.deliverToHandler(handler, msg, sub));
}
```

48 duplicated lines down to four. Exception handling and the unsub-limit check are one implementation instead of two copies that happened to agree - they had already started to drift cosmetically.

## Tests

`core/src/test/java/io/synadia/client/impl/SubscribeRegistrationOrderTests.java` asserts the **ordering invariant** rather than trying to win the race: a `NatsConnection` subclass overrides `sendSubscriptionMessage` and records what the dispatcher's handler map held at that instant. One test per path. No server, no connection, no timing - the dispatcher is brought up with `startImpl(false)`.

They were added **before** the fix and failed deterministically on every attempt, then passed after it. That red-then-green is the evidence the fix does what it claims, given the original symptom could never be reproduced locally.

`DispatcherTests` covers the newly guarded `subscribe(subject, queueName)`.
