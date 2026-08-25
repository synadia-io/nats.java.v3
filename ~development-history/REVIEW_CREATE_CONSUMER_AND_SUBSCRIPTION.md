# Review — `_createConsumerAndSubscription` (JetStream.java, working tree)

Reviewed against `8ab3288f`. The reviewed change is now committed as `da93e73a` ("improving consumer and subscription creating, part 1"). All findings from the earlier passes are applied, plus the dispatcher cleanup in `_createJsSubscription`, which is on top of that commit and uncommitted.

## The method as it stands

```java
NatsSubscription _createConsumerAndSubscription(String stream, ConsumerCreator<?> creator, @Nullable SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
    ConsumerInfo ci = _createConsumer(stream, creator, Create);
    try {
        if (creator instanceof AbstractOrderedConsumerCreator<?> orderedCreator) {
            return _createJsSubscription(ci, subscribeBehavior, orderedCreator, null);
        }
        return _createJsSubscription(ci, subscribeBehavior, null, null);
    }
    catch (RuntimeException e) {
        try {
            _deleteConsumer(stream, ci.getName());
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            e.addSuppressed(ie);
        }
        catch (Exception de) {
            e.addSuppressed(de);
        }
        throw e;
    }
}
```

Every exception path resolves correctly:

* **`_createConsumer` throws `JetStreamException` / `InterruptedException`** — outside the try, so it goes straight to the caller through the declared `throws`. Nothing was created, nothing to undo.
* **`_createConsumer` throws unchecked** — same, straight out. This is the case that motivated hoisting it: inside the try it was caught only to be rethrown unchanged, which cost a nullable `ci` and a null guard to express a no-op.
* **`_createJsSubscription` throws unchecked** — the consumer exists and the subscription does not, so the delete runs and the original exception is what propagates. `catch (Exception de)` covers both halves of what the delete can throw: `JetStreamException` from the API response, and `IllegalStateException` from `conn.request` when the connection is closed or draining (`NatsConnection:1139`/`:1142`, via `validateNotClosed` called from `requestAsync:1614`) — the case where the same closing connection failed the subscribe and now blocks the delete. Either way it lands as suppressed and never replaces `e`.
* **`_createJsSubscription` declares no checked exceptions**, so there is no fourth case.

`ci` is a plain non-null local, the try covers exactly the region that has something to undo, and the interrupt flag is restored before the suppressed attach.

## Verified across the diff

* **9 call sites converted, each passing exactly what it passed before** — push at `:863`, `:879`, `:927`; pull at `:1081`, `:1098`, `:1114`, `:1128`, `:1146`, `:1162`.
* **The `instanceof` dispatch reproduces the old explicit `orderedCreator` argument exactly.** `PushConsumerCreator` / `PullConsumerCreator` extend `AbstractEphemeralConsumerCreator`; `PushOrderedConsumerCreator` / `PullOrderedConsumerCreator` extend `AbstractOrderedConsumerCreator`. Disjoint branches under `ConsumerCreator`, so no non-ordered creator can match the pattern, and both ordered overloads still pass theirs through.
* **Dropping the `pmmInstance` parameter loses nothing** — all 9 sites passed `null`.
* **No creator-based subscribe overload was left behind.** The other `_createConsumer` callers do not subscribe: `JetStreamManagement:201/213/225` (management API) and `JetStream:1201` (`createConsumer`, which returns a context). `PushOrderedMessageManager:82` is the ordered-restart path and re-subscribes *before* creating the consumer — a different shape.
* **The `ConsumerInfo`-bound and `(stream, consumerName)`-bound overloads still call `_createJsSubscription` directly** — correct; a consumer the caller already owned must not be deleted on a failed subscribe.
* **`mergeNum` / `mergeString` gaining `@Nullable` returns is correct** — both can return the nullable `h` unchanged, and their caller `mergePublishOptions` is already `@Nullable Headers`. Placement follows the convention.
* `:jetstream:compileJava` and `:jetstream:compileTestJava` clean. Not yet run against a server.

## Noted, not raised as findings

* **A failed async subscribe left the dispatcher this call created — fixed in the working tree.** For a subscribe with a handler and no user-supplied dispatcher, `JetStreamSubscribeConfig:66` calls the `Supplier<NatsDispatcher>` it was constructed with, which `JetStream:616` binds to `conn::createDispatcher`; that registers the dispatcher in `dispatchers` and starts its thread (`NatsConnection:1738-1739`). Everything from there to the end of `_createJsSubscription` can throw — the message manager factories, and `dispatcher._subscribeByFactory`, which rejects a closed or draining dispatcher (`requireActiveConnection`) and then a closed or draining connection (`NatsConnection:1243`/`:1246`), and which also runs the subscription factory and `manager.startup` inside itself. `_createJsSubscription` now wraps that whole region and closes the dispatcher on a `RuntimeException` when `jssc.internalDispatcher` is set, best-effort and suppressed onto the original exception the same way the consumer delete is: `conn.closeDispatcher` itself throws `IllegalStateException` on a closed connection (`NatsConnection:1755`) and `IllegalArgumentException` on an already-closed dispatcher, which is exactly the case that caused the failure. No interrupt handling is needed — unlike `_deleteConsumer` it is not a server round trip. The guard covers the sync path for free, since a subscribe with no handler never has an internal dispatcher.
* **The success path closes it too — fixed in the working tree.** `internalDispatcher` was written and never read, so a subscription that ended normally left its dispatcher registered with its thread running. `JetStreamSubscription` now keeps the dispatcher when `subConf.internalDispatcher` is set and closes it in `invalidate()`, which is the one choke point every ending goes through: `unsubscribe()` -> `NatsDispatcher.unsubscribe` -> `connection.unsubscribe` -> `invalidate`; the auto-unsub limit from the dispatcher run loop (`NatsDispatcher:111`); and `drain` through `cleanUpAfterDrain` (`NatsSubscription:235`). The close is best-effort for the same reason the other two cleanups are - `closeDispatcher` throws `IllegalStateException` on a closed connection and `IllegalArgumentException` on a dispatcher that is already gone, and `invalidate` runs on the dispatcher thread and the drain path, where a cleanup failure must not escape. Connection close needs no special case: `close` stops every dispatcher and clears its subscription maps before it invalidates the subscribers (`NatsConnection:874-876`), so the close that follows finds nothing to do. The one way a caller could have reached that dispatcher was `Subscription.getDispatcher()`, so it was removed from the interface and `NatsSubscription.getDispatcher()` is package-private now - a dispatcher the caller made is already in their hands, and one the subscribe made is not theirs to subscribe on. That also took `NatsMessageConsumerBase.shutdownSub`'s dispatcher branch with it, since `NatsSubscription.unsubscribe(int)` already routes through the dispatcher when there is one, and moved `NatsDispatcher.unsubscribe`'s `instanceof NatsSubscription` guard above the ownership check that now needs it.
* `_deleteConsumer` returns `boolean` and the result is dropped, so a `false` leaves an orphan with no trace on the original exception.
* The method is package-private but only called from `JetStream.java`, so `private` would fit. `_merge:515` shows the `_` prefix is used on private members here, so the marker would not have to change with it. Belongs to the visibility pass in `PLAN_CORE_JETSTREAM_BOUNDARY.md` §3a/§3b.
* No javadoc on the method — worth adding when the surrounding refactor settles, since the delete-on-failed-subscribe rule is the kind of thing a future reader will otherwise re-derive from the catch block.

## Follow-up: can both sides invalidate the same subscription, and is `JetStreamSubscription:98` enough?

**Yes, two paths reached `invalidate()` twice. The `internalDispatcher != null` check on `:98` was not what made that safe — the `catch (RuntimeException)` under it was. Both are fixed now; the analysis below is what the code looked like before.**

The path most people expect - unsubscribe, then connection close - is *not* one of them. `NatsConnection.unsubscribe` with `after <= 0` calls `invalidate(sub)`, which is `remove(sub)` then `sub.invalidate()`, and `remove` takes the sid out of `subscriptions`. Close then iterates `subscriptions` and never sees it. Same for the auto-unsub limit (`NatsDispatcher:108`), `_nextMessage`'s limit check (`NatsSubscription:189`) and `cleanUpAfterDrain` (`NatsSubscription:229`) - all four go through `connection.invalidate`, which removes first.

**1. `unsubscribe(sub, after)` with `after > 0` and the limit already reached.** This is the deterministic one:

```java
else {
    sub.setUnsubLimit(after);
    if (sub.reachedUnsubLimit()) {
        sub.invalidate();          // <- no connection.remove(sub)
    }
}
```

`sub.invalidate()` is called directly, so the sid stays in `subscriptions` **and** the sub stays in its dispatcher's maps, because `connection.remove` is the only thing that calls `dispatcher.remove(sub)`. At connection close, `subscriptions.forEach((sid, subscription) -> subscription.sub.invalidate())` (`NatsConnection:876`) invalidates it a second time. Reachable from any `unsubscribe(n)` where at least `n` messages have already been delivered.

That stale entry is the same shape as the `reSubscribe` leak this changeset fixed, and it is a defect on its own terms, independent of JetStream: `getSinkCount()` counts it, `drain` builds `pureSubscriptions` from it, and `deliverMessage` still finds it (harmless in practice - the `UNSUB sid n` goes out at the end of the same method and the server has already met the count - but it is a dead sid held by a live map).

**Fixed:** the branch calls `invalidate(sub)` rather than `sub.invalidate()`, so it removes the sid and the dispatcher entry the same way the `after <= 0` branch above it already did. `sendUnsub` at the end of the method is unaffected - it reads `sub.getSID()`, which `remove` does not touch. Covered by `NatsConnectionImplTests.testUnsubscribeWithLimitAlreadyReachedRemovesTheSid`, which fails `expected: <0> but was: <1>` with the old line in place.

**2. `drain` racing connection close.** `cleanUpAfterDrain` -> `connection.invalidate(this)` removes and invalidates, but if `close` runs its `subscriptions.forEach` first, the sub is invalidated there and again when the drain finishes. A race rather than a certainty, but the same double call.

### Why `:98` holds anyway

All three statements in `JetStreamSubscription.invalidate()` survive a second pass, but only one of them by design:

| statement | second call |
| --- | --- |
| `manager.shutdown()` | idempotent - `shutdownHeartbeatTimer` takes `stateChangeLock`, null-checks `heartbeatTaskRef` and clears it |
| `super.invalidate()` | idempotent - null-checks `incoming`, then re-nulls `incoming` and `dispatcher` |
| `connection.closeDispatcher(internalDispatcher)` | **threw every time, and the catch ate it** |

`internalDispatcher` is `final` and never cleared, so `:98` is true on every pass - the check distinguishes "this subscribe made its own dispatcher" from "the caller supplied one", and nothing else. The second close throws `IllegalArgumentException("Dispatcher is already closed.")` on a live connection, or `IllegalStateException("NatsConnection is Closed")` during close, and both land in the catch. Correct outcome, reached by throwing and swallowing rather than by not trying. Closing twice can never hit the wrong dispatcher, since an internal one belongs to exactly this subscription.

**Fixed:** the field is an `AtomicReference<NatsDispatcher>` and `invalidate` takes it with `getAndSet(null)`, so exactly one caller ever sees the dispatcher and every later pass is a no-op. An `AtomicBoolean` or a plain field write would have left the drain-racing-close path able to close twice, since those two callers genuinely overlap - `getAndSet` is the one-shot primitive that closes that window. The `catch (RuntimeException)` stays: even on the first and only close, `close` stops the dispatchers before it invalidates the subscribers, so `closeDispatcher` still legitimately throws `IllegalStateException` on a connection that is already down, and `invalidate` runs where an escape is not acceptable. What changed is that the catch no longer routinely absorbs a self-inflicted second close, so what it hides now is only the connection-is-going-away case.
