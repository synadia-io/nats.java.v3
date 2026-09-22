# nextMessage status exception - analysis of the testOverflow failure

Written 2026-09-21, prompted by `JetStreamPullTests.testOverflow` line 603 failing with `JetStreamStatusInternalException` where it asserts `JetStreamStatusException`. **Implemented the same day - see §8 for what actually landed.** §1-§7 are the analysis as written before the decision.

## 1. What the test hits

`testOverflow` pulls without a priority group, the server answers `400 Bad Request`, and `MessageManager.manage` returns `STATUS_ERROR`. On the `sub.nextMessage(1000)` path that lands at `JetStreamSubscription:217`, which throws `JetStreamStatusInternalException` - unchecked (`StatusException` -> `IllegalStateException`) and package-private. The test asserts the public checked `JetStreamStatusException`, so it fails.

The class javadoc on `JetStreamStatusInternalException` says it is "thrown and bridged to the user-facing `JetStreamStatusException` within this package; never exposed to callers." That is false on this path: `JetStreamSubscription.nextMessage` has no bridge, so the internal type reaches the caller verbatim. **The defect the test found is the leak, not a missing 400 special case.**

## 2. The proposed fix does not compile, and cannot be made to

The working-tree change at `JetStreamSubscription:214-216` throws the checked `JetStreamStatusException` from `_nextUnmanaged`, which would need `_nextUnmanaged` to declare it. Two call sites block that, both hard Java rules, not style:

1. `JetStreamSubscription.nextMessage(long)` (`:118`) and `nextMessage(long, TimeUnit)` (`:127`) are `@Override` of `io.synadia.client.Subscription.nextMessage` (`Subscription.java:76`, `:89`), declared `throws InterruptedException`. An override cannot add a checked exception, and core cannot declare a JetStream type. Same for `nextMessageNoWait` (`:137`) and `nextMessageWaitForever` (`:143`), which reach the sibling throw sites at `:181` and `:156`.
2. `JetStreamPullSubscription.iterate(...)` calls `_nextUnmanaged` from an anonymous `Iterator.hasNext()` (`:258`). `Iterator` cannot widen either.

Block 2 goes away if `PLAN_REMOVE_LEGACY_PULL_METHODS.md` lands (it removes `fetch`/`iterate`/`reader`). Block 1 stays regardless.

The two callers that *can* take a checked exception already declare it: `NatsFetchMessageConsumer.nextMessage` (`:90`, catch at `:137`) and `NatsIterableMessageConsumer.process` (`:41`, catch at `:49`).

## 3. "Handled" versus "raised to the user"

Measured: every throw of `JetStreamStatusInternalException` reaches the user. The only two catches of it (`NatsFetchMessageConsumer:137`, `NatsIterableMessageConsumer:49`) immediately rethrow `new JetStreamStatusException(e)`. Nothing swallows it. The type is a checked/unchecked bridge, not a handled/unhandled marker, so **there is nothing for `isBadRequest()` to differentiate** - a 409 on that path is as user-facing as a 400.

## 4. Recommendation

Do not special-case the code. Make the unchecked status exception public and user-facing, and keep the checked one for the simplified API that declares it:

1. Make `JetStreamStatusInternalException` public and rename it - `JetStreamStatusRuntimeException` pairs with the checked `JetStreamStatusException`. Nothing else about it changes.
2. Revert `JetStreamSubscription:214-216` and `Status.isBadRequest()` unless `isBadRequest` is wanted for its own sake; `_nextUnmanaged`'s signature then does not change and the whole call chain is untouched.
3. `testOverflow:603`/`:608` assert the renamed unchecked type.
4. The two bridge catches keep their current shape, so fetch/iterate/consumer-context users still see the checked `JetStreamStatusException`.

This is v2's split with clearer names: v2 has unchecked `JetStreamStatusException extends IllegalStateException` on the raw subscription path and checked `JetStreamStatusCheckedException` on the simplified API. v3 inverted the names, which is what made the ported assertion fail.

## 5. If a checked exception on `sub.nextMessage` is actually wanted

The only route is to widen core: declare a checked base (say `io.synadia.client.api.NatsStatusException`) on `Subscription.nextMessage`, have `JetStreamStatusException` extend it, and drop the `Iterator` return from `iterate`. Cost: every caller of core `nextMessage` must now handle a checked exception a plain core subscription can never throw. Not recommended, recorded so the option is on the table rather than rediscovered.

## 6. Accepted from the working tree, with one correction

- `JetStreamSubscription:181` and `:217` - the note `"Pull Subject Mismatch"` is wrong. The branch is entered only when the subject **matches** (`expectedPullSubject == null || expectedPullSubject.equals(msg.getSubject())`). Dropping the note is right.
- `JetStreamStatusInternalException:32-34` - `@Nullable` note defaulting to `status.getMessage()` is right for the direct-throw case, but it doubles the text when bridged: `JetStreamStatusException(cause)` (`:33`) feeds the note into the 3-arg ctor, which builds `note + ": " + status.getMessageWithCode()` - "Bad Request: 400 Bad Request". Fix by keeping the internal note nullable (no defaulting in the ctor) and letting one `JetStreamStatusException` ctor take a `@Nullable` note: `message = note == null ? status.getMessageWithCode() : note + ": " + status.getMessageWithCode()`. The new public 2-arg ctor then delegates with `null` instead of duplicating the body.

## 7. Two unrelated items in the same diff

- `JetStreamSubscription:10` imports `io.synadia.client.utils.Debug`. `Debug*.java` is gitignored (`.gitignore:6`) and untracked, so this import breaks a fresh clone and CI. It is also unused in the file.
- `JetStreamSubscription:6` imports `io.synadia.client.api.Error`, unused.

## 8. What landed

No new exception type. `JetStreamException` stays checked, and the subscription paths throw the core `StatusException` (public, unchecked, `extends IllegalStateException`, carries the `Status`) that already existed.

- `JetStreamStatusInternalException` (package-private, unchecked) is deleted, along with its contract test. The one assertion in it that still meant something - the unchecked status type is outside the checked `JetStreamException` hierarchy - moved into `JetStreamExceptionTests.testCatchBaseCatchesAllSubtypes`, now stated about `StatusException`.
- `JetStreamSubscription._nextUnmanagedWaitForever`/`_nextUnmanagedNoWait`/`_nextUnmanaged` and the legacy `JetStreamPullSubscription.fetch` throw `new StatusException(msg.getStatus())`. No signature changed anywhere - the generics route (`Subscription<E>` plus a `NatsSubscriptionBase` split) was explored and reverted, since an unchecked exception needs none of it.
- The notes went with it: `StatusException` carries only the status, so `"Pull Subject Mismatch"` (which was wrong - that branch runs when the subject **matches**), `"Error during next message / wait forever"` and `"Error Fetching"` no longer exist. The exception message is the status, e.g. `400 Bad Request - Priority Group missing`.
- The `isBadRequest()` branch was dropped: with one unchecked type on every `STATUS_ERROR` there is nothing to differentiate. `Status.isBadRequest()` itself was left in place and now has no caller.
- `JetStreamStatusException` keeps its name and its place under the checked `JetStreamException`. Its note-taking constructor now accepts a `@Nullable` note and builds `"400 Bad Request"` rather than `"Bad Request: 400 Bad Request"`; the old bridge constructor that took the internal type is gone.
- `NatsFetchMessageConsumer` and `NatsIterableMessageConsumer` catch `StatusException` and rethrow `new JetStreamStatusException(e.getStatus(), sub)`, so fetch/iterate/consumer-context users still get the checked type, still carrying the subscription.
- `testOverflow:603`/`:608` assert `StatusException`.

Measured after the change: `:jetstream:compileTestJava` green. `JetStreamExceptionTests` 5/5 pass. `testOverflow` clears both status assertions - it prints `io.synadia.client.impl.StatusException: 400 Bad Request - Priority Group missing` - and now fails further down at line 622 with `consumer already exists [10148]`, where the second `pullSubscribe` re-creates the named consumer. That is a porting gap in the test, not part of this change.

## 9. Javadoc pass, and why StatusException stays in core

`@throws StatusException` was added to every public entry point whose call path can surface it. The codebase already documents unchecked exceptions on these same methods (`@throws IllegalArgumentException`, `@throws IllegalStateException`), so this follows the existing convention.

- `JetStreamSubscription` - `nextMessage(long)`, `nextMessage(long, TimeUnit)`, `nextMessageNoWait()`, `nextMessageWaitForever()`
- `JetStreamPullSubscription` - `fetch(int, long)`, `iterate(int, long)` (the tag says the throw comes from the returned iterator)
- `JetStreamReader` - all four `nextMessage` methods
- `BaseConsumerContext` - `next()`, `next(long)`
- `KeyValue` - `keys()`, `keys(String)`, `keys(List)`, `history(String)`, `purgeDeletes()`, `purgeDeletes(KeyValuePurgeOptions)`
- `ObjectStore` - `get(String, OutputStream)`, `getList()`

Core's `Subscription` interface was left alone: a plain core subscription never throws it, and the JetStream overrides carry the tag themselves. `:jetstream:javadoc` is clean apart from pre-existing `no comment` warnings in the gitignored `DebugJs.java`.

**`StatusException` cannot move to the jetstream package or take the `JetStreamStatusException` name.** It is thrown from core: `NatsConnection.deliverReply` (`:1691`) completes a core request future with it when the reply is a 503 and the request's `CancelAction` is `REPORT`, which is the default. `RequestTests.testNoResponders` (`:370`) asserts exactly that, with no JetStream in the picture.

Two things found during the pass, neither changed:

- `KeyValue.consumeKeys` runs `visitSubject` on the options executor and catches only `JetStreamException` and `InterruptedException`. A `StatusException` there kills the task without offering a terminal `KeyResult`, so a caller polling the queue waits forever. That is why `consumeKeys` got no `@throws` tag - the exception never reaches the caller.
- `BaseConsumerContext.next()`/`next(long)` document `@throws JetStreamStatusException`, but nothing on that path converts anything: `NatsConsumerContext.next` returns `nnc.getMessage()` outside any catch, so what actually arrives is the unchecked `StatusException`. `fetch` and `iterate` bridge, `next` does not.

## 10. Final shape: JetStreamStatusException is unchecked and JetStream raises it

Decided and implemented 2026-09-21, after §8/§9. The wrap-only role the bridges gave `JetStreamStatusException` was the thing worth removing, and a JetStream-specific type is what JetStream APIs should raise, so the type was **re-parented from `JetStreamException` to `StatusException`** rather than deleted.

- `JetStreamStatusException extends StatusException` - unchecked, carrying the note and the subscription on top of the status. `catch (StatusException)` covers core and JetStream; `catch (JetStreamStatusException)` narrows to JetStream. Core's `StatusException` gained a `protected StatusException(String message, Status)` so a subtype can compose its own message.
- `JetStreamSubscription._nextUnmanaged*` (3 sites) and the legacy `JetStreamPullSubscription.fetch` raise `JetStreamStatusException` directly. `JetStream.processPublishResponse` already did.
- **Both bridges are gone.** `NatsFetchMessageConsumer` and `NatsIterableMessageConsumer` now rethrow the status unchanged. The catch had to stay, as `catch (StatusException e) { throw e; }`, because a status **is** an `IllegalStateException` and the catch below it - which returns null for a consumer that was stopped - would otherwise swallow it.
- No signature changed. The 10 `throws JetStreamStatusException` declarations are still legal and kept as documentation, and the `@throws` javadoc added in §9 was retargeted from `StatusException` to `JetStreamStatusException` on all 20 JetStream entry points.
- This reverses audit item A13, which had deliberately put the status type inside the checked hierarchy. `catch (JetStreamException)` no longer catches a status. Reviewed all 5 `catch (JetStreamException)` sites in main: `NatsMessageConsumer:165` also catches `RuntimeException` so it is unaffected; `NatsMessageConsumer:127`, `ObjectStore:157` and `KeyValue:510` are around management calls or an executor task, none of which read messages; `JetStream:486` is the publish future, whose one behavioral change is below.
- **Behavior change worth knowing:** `publishAsync` on a status response now completes the future with `JetStreamStatusException` itself instead of a `RuntimeException` wrapping it, because the lambda no longer has to launder a checked exception.

Measured: `:core:compileTestJava` and `:jetstream:compileTestJava` green. `JetStreamExceptionTests` 5/5, `JsPublishTests` 1/1, `JetStreamPullTests.testOverflow` and `.testPinnedClient` pass. `SimplificationTests` 33 tests, 1 failure on the first attempt - `testOrderedBehaviorNext` timed out after 3 minutes blocked in `ConsumerMessageQueue.pop`, not on any status path - and passed on retry, build green. That class has a recorded history of timing flakes on this mount (A18).

## 11. Documentation coverage of the unchecked status

Asked 2026-09-22: is every public API that can surface a status documented? It was not - §9 covered only the read paths. It is now. The convention was already in place, since these same methods document `@throws IllegalArgumentException` / `@throws IllegalStateException`.

Read paths (from §9, retargeted to `JetStreamStatusException`): `JetStreamSubscription` 4, `JetStreamPullSubscription` 2, `JetStreamReader` 4, `BaseConsumerContext` 2, `KeyValue` 6, `ObjectStore` 2. `FetchMessageConsumer` 1 and `IterableMessageConsumer` 4 already documented it, being the methods that declare it.

Added now:

- **`JetStream.publish` x12** - `@throws JetStreamStatusException if the server replies with a status message instead of an ack`. `processPublishResponse` raises it, and until now no publish overload said so.
- **`JetStream.publishAsync` x10** - a `@throws` tag would be wrong on a future-returning method, so the sentence went on `@return`: the future completes exceptionally with it.
- **`KeyValue` writes x13** (`put` x3, `create` x2, `update` x2, `delete` x2, `purge` x4) and **`ObjectStore` writes x8** (`put` x4, `updateMeta`, `delete`, `addLink`, `addBucketLink`) - all reach `js.publish` through `_write` / `publishMeta`.
- **`NatsConnection` request futures x7** - `@return` gains: the future completes exceptionally with a `StatusException` when the reply is a 503 no-responders status and the cancel action is `REPORT`, which is the default.

Two places deliberately left undocumented, both because the caller never sees the exception:

- **Sync `NatsConnection.request(...)`** catches `ExecutionException` and returns **null** (`NatsConnection:1506`), so a 503 becomes a null return, not a throw. That is the hole `REQUEST_BEHAVIOR_IMPROVEMENT.md` exists to close, and the javadoc should change when it does.
- **JetStream management calls** go through the same sync `request`, so a status becomes a null response and `responseRequired` turns it into `JetStreamTimeoutException`. No status reaches a management caller.

Also fixed while verifying: `BaseConsumerContext.next()` and `next(long)` had ended up with **two** `@throws JetStreamStatusException` tags each - the §9 pass added one under the name `StatusException`, and the §10 retarget turned it into a duplicate of the tag that was already there. The pre-existing, more descriptive one was kept.

Totals after the pass: 60 `@throws JetStreamStatusException` tags across 9 files, plus 17 `@return` sentences for the future-returning methods. `:core:javadoc` and `:jetstream:javadoc` green.
