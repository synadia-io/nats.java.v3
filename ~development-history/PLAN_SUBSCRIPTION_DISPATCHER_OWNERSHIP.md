# Plan — who owns the subscription/dispatcher link

Written 2026-08-23, from the question "get rid of `getDispatcher`; the connection should keep a map of subscription sids to dispatcher, and the dispatcher should be aware of the subscriptions it's dispatching to."

`Subscription.getDispatcher()` is removed from the interface and `NatsSubscription.getDispatcher()` is gone entirely, so nothing outside the class can reach a dispatcher it did not create. The rest of this is about the *internal* link - who records which dispatcher serves which sid.

## The dispatcher half already exists

`NatsDispatcher` tracks its subscriptions in four maps, all `ConcurrentHashMap`:

| map | key -> value | what it is for |
| --- | --- | --- |
| `subWithDefaultHandlerBySubject` | subject -> sub | the one default-handler sub per subject |
| `subWithNonDefaultHandlerBySid` | sid -> sub | every sub made with its own handler |
| `subsBySidNonDefaultHandlersBySubject` | subject -> (sid -> sub) | the same, grouped by subject, for `unsubscribe(String subject)` |
| `nonDefaultHandlerBySid` | sid -> handler | the handler to call for a delivered message |

`remove(NatsSubscription)` clears a sub out of all four, `hasNoSubs()` reports empty, `resendSubscriptions()` and `sendUnsubForDrain()` walk them. Nothing needed to be added for "the dispatcher should be aware of the subscriptions it's dispatching to" - it already was. The one place that asked the *subscription* instead was `NatsDispatcher.unsubscribe(Subscription, int)`; see below.

## What the field was doing

Before this change `NatsSubscription.dispatcher` had six readers inside the class and four in the connection.

In `NatsSubscription`:
1. constructor - a null dispatcher means synchronous, so allocate `incoming` (`ConsumerMessageQueue`)
2. `isActive()` - `dispatcher != null || incoming != null`
3. `_nextMessage` - throws if the sub belongs to a dispatcher
4. `unsubscribe(int after)` - route to `dispatcher.unsubscribe(this, after)` or `connection.unsubscribe(this, after)`
5. `reSubscribe(String)` - route the re-subscribe through the dispatcher, carrying the handler across
6. `invalidate()` - null it out

In `NatsConnection`, all four now gone:
7. `:441` reconnect - resend only subs with no dispatcher
8. `:1199` `remove(sub)` - also remove from the dispatcher's maps
9. `:2135` `deliverMessage` - pick the sink and queue: the dispatcher's, or the sub's own
10. `:2688` `drain` - `pureSubscribers.removeIf(s -> s.dispatcher != null)` (the local is `pureSubscriptions` now)

## Decided and done: the connection owns the link

The record is `SubscriptionInfo` and the map is `subscriptions` (2026-08-24). It was `Subscriber` first, which read as "a thing that subscribes" - a peer of `NatsSubscription`; then `SubDispatchEntry`, which was accurate and unreadable. Locals and lambda parameters holding one are `subscription` - not `info`, which is already a `ServerInfo` local three times in this class.

Removing the accessor was not the point - the connection was still asking the subscription what it belonged to, at `:441`, `:1199`, `:2135` and `:2688`, and a field read is the same question as a getter call. The connection now keeps the link itself.

`subscriptions` maps a sid to a record instead of to a subscription:

```java
protected static final class SubscriptionInfo {
    final NatsSubscription sub;
    final @Nullable NatsDispatcher dispatcher;   // null = the sub owns its own queue
}
protected final Map<String, SubscriptionInfo> subscriptions;
```

One record rather than a second parallel map, because `deliverMessage` runs per message and already does one `subscribers.get(sid)`: it reads `subscription.dispatcher` off the record it already fetched, so the delivery path pays nothing, and there is one structure to keep in step instead of two. The entry is built where the dispatcher is already known - `_createSubscriptionByFactory` has it as a parameter, and `reSubscribe` now takes it (null from `NatsSubscription`, `this` from `NatsDispatcher`).

Sites converted:

| site | was | now |
| --- | --- | --- |
| `:441` reconnect | `sub.dispatcher == null` | `subscription.dispatcher == null` |
| `:876` close | `sub.invalidate()` | `subscription.sub.invalidate()` |
| `:1195` `remove` | ask the sub, then `dispatcher.remove` | take the record out of the map, remove through its dispatcher |
| `:2135` `deliverMessage` | `sub.dispatcher` | `subscription.dispatcher`, same single lookup |
| `:2688` drain | `values()` then `removeIf(dispatcher != null)` | build the pure set from the records |

**The subscription keeps its own copy.** It needs one for its own work - `unsubscribe(int)` and `reSubscribe` route through it, and the constructor, `isActive()` and the `_nextMessage` guard read it to know it is not synchronous. The connection's record is the connection's; the field is the subscription's.

**The dispatcher answers ownership from its own maps.** `NatsDispatcher.unsubscribe(Subscription, int)` was the last outside reader of the field - `ns.dispatcher != this`. It now calls a private `owns(NatsSubscription)` that checks `subWithNonDefaultHandlerBySid` by sid, then `subWithDefaultHandlerBySubject` by subject with the sid double-checked, the same guard rails `remove` already uses. The exception it throws is unchanged.

That leaves `NatsSubscription.dispatcher` read only by `NatsSubscription`. Nothing else asks a subscription what it belongs to.

## Found while tracing this: `reSubscribe` leaks the old sid

`NatsSubscription.reSubscribe` (`:54-65`) unsubscribes the old sid and takes a new one. The two branches are not symmetric:

```java
if (dispatcher == null) {
    connection.remove(this);                                   // removes the OLD sid from subscribers
    sid = connection.reSubscribe(this, newDeliverSubject, queueName);
}
else {
    MessageHandler handler = dispatcher.getNonDefaultHandlerBySid(sid);
    dispatcher.remove(this);                                   // dispatcher maps only
    sid = dispatcher.reSubscribe(this, newDeliverSubject, queueName, handler);
}
```

`connection.remove(sub)` does `subscribers.remove(sid)` *and* `dispatcher.remove(sub)`; the else branch calls only the second half, and `NatsConnection.reSubscribe` then `put`s the new sid. `subscribers.remove` exists at exactly one place (`:1197`), so nothing else cleans it up: **every reset of an async ordered push consumer leaves a stale sid -> sub entry in `connection.subscribers`.**

`PushOrderedMessageManager:74` is the only caller of `reSubscribe`, so the reach is ordered push consumers, and only the ones with a handler. Consequences: the map grows one entry per reset for the life of the connection, `getConsumerCount()` (`:2321`) counts the stale entries, and `drain` (`:2688`) snapshots them. Delivery is not affected - the server was sent `UNSUB <old sid> 0` first, so nothing arrives on the old sid.

### Fixed

`connection.remove(this)` already does both halves - it takes the record out of `subscribers` and, through that record, removes the sub from its dispatcher's maps - so the fix is to hoist it out of the branch rather than duplicate it:

```java
void reSubscribe(String newDeliverSubject) {
    connection.sendUnsub(this, 0);
    // The handler is keyed by the sid being replaced, so it has to be read before the remove clears it.
    MessageHandler handler = dispatcher == null ? null : dispatcher.getNonDefaultHandlerBySid(sid);
    connection.remove(this); // the old sid, out of the connection's map and the dispatcher's
    if (dispatcher == null) {
        sid = connection.reSubscribe(this, newDeliverSubject, queueName, null);
    }
    else {
        sid = dispatcher.reSubscribe(this, newDeliverSubject, queueName, handler);
    }
    subject = newDeliverSubject;
}
```

The handler read has to stay ahead of the remove: `NatsDispatcher.remove` clears `nonDefaultHandlerBySid`, which is where `getNonDefaultHandlerBySid(sid)` finds it.

`DispatcherTests.testReSubscribeReplacesTheSid` covers it - it takes `getSinkCount()` before the re-subscribe and requires it unchanged after, then checks the handler came across to the new subject and the old subject delivers nothing. Confirmed against a clean `b4e15f4a` worktree, where it fails `expected: <2> but was: <3>` - the leaked sid.

## Follow-on: the connection holds dispatchers in a set, and a dispatcher has no id

`NatsConnection.dispatchers` was `Map<String, NatsDispatcher>` keyed by a NUID the dispatcher carried. **The key was never read.** All ten sites either derived it from the dispatcher already in hand or ignored it:

| site | was | now |
| --- | --- | --- |
| `:446` reconnect | `forEach((nuid, d) -> ...)` | `forEach(d -> ...)`, key was unused |
| `:874` close | `forEach((nuid, d) -> d.stop(false))` | `forEach(d -> d.stop(false))`, key was unused |
| `:1638` inbox dispatcher | `put(d.getId(), d)` | `add(d)` |
| `:1752` `createDispatcher` | `put(dispatcher.getId(), dispatcher)` | `add(dispatcher)` |
| `:1779` `closeDispatcher` | `containsKey(nd.getId())` | `contains(nd)` |
| `:1788` `cleanupDispatcher` | `remove(nd.getId())` | `remove(nd)` |
| `:1792` `getDispatchers` | `unmodifiableMap` -> `Map<String, Dispatcher>` | `unmodifiableSet` -> `Set<Dispatcher>` |
| `:878` / `:2334` / `:2710` | `clear()` / `size()` / `values()` | `clear()` / `size()` / the set itself |

So it is `Set<NatsDispatcher>`, built with `ConcurrentHashMap.newKeySet()` - same concurrent iteration behavior the comment on the field asked for. `NatsDispatcher` does not override `equals`/`hashCode`, so the set is identity-based, which is exactly what a unique NUID per instance was simulating.

That was `getId()`'s only caller, so **`NatsDispatcher.id` and `getId()` are gone**, along with the `NUID` import. The id was already the odd one out - it had just been moved off `start(String)` into the constructor so the dispatcher would make its own; the map was the only reason it existed at all.

Test side: `getDispatchers()` returns `Set<Dispatcher>`, so `NatsPackageScopeWorkarounds.getDispatchers` changes type and `ServiceTests.testDispatchers` swaps two `containsValue` calls for `contains`. Everything else there was `size()`. `NatsPackageScopeWorkarounds` also moved from core's test sources to `service/src/test/java/io/synadia/client/impl/` - `ServiceTests` is its only caller. It keeps the `io.synadia.client.impl` package, which is what lets it reach the `protected` `getDispatchers()`; service already depends on core's `testOutput` jar for the shared test base, so nothing in the build changed. `:service:test` 15 green, `DispatcherTests` 24 green.
