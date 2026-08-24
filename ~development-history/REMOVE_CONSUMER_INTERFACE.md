# Plan: Remove the `Consumer` interface (INTERFACES_REPORT §5.2, option b)

## STATUS: DONE (standalone, without §5.1)
Done without §5.1 (Subscription stays an interface). Applied the D1b fallback to **both** interfaces: `Subscription` and `Dispatcher` each drop `extends Consumer` and declare `isActive()` + `drain(long)`. On `Subscription` the pending/count methods live only on the concrete class; on `Dispatcher` the full pending/observability group was restored to the interface (so async slow consumers are bounded/monitored on the dispatcher — see "RESOLVED — async pending limits" below). The impl base `NatsConsumer` was renamed to `NatsMessageSink` (package-private, impl-only). The two default constants live in the public `OptionsConstants` interface (`OptionsConstants.DEFAULT_MAX_MESSAGES` / `..._BYTES`) — reachable from jetstream's `SubscribeBehavior` (api) and tests without exposing any impl type. `slowConsumerDetected`/`processSlowConsumer`/`supplyMessage` take `Subscription`; `deliverMessage` passes `sub`. `SlowConsumerTests` dispatcher cases are typed to the `Dispatcher` interface (exercising the group through the interface). `MIGRATION_GUIDE.md` documents the removal (Core section). All modules compile; `SlowConsumerTests`, `ErrorListenerTests`, `DrainTests`, `JetStreamPushTests` pass. The plan text below is the original design.

---


## Goal
Delete `io.synadia.client.Consumer` entirely. Introduce **no** public shared replacement (no `MessageSink`). The abstract `NatsConsumer` stays in `.impl` as internal shared code (still the superclass of `NatsSubscription`/`NatsDispatcher`), but is never named in a public signature. The one polymorphic touch-point — `ErrorListener.slowConsumerDetected` — is retyped to take `Subscription`.

Rationale is in `INTERFACES_REPORT.md` §5.2 (member-usage table): no `Consumer` member is ever called through a `Consumer`-typed reference, so the interface buys nothing; its only job was the slow-consumer callback parameter, which will now be `Subscription`.

## Why the reported handle changes (behavior note)
`slowConsumerDetected` currently reports the **dispatcher** for a dispatched sub (`NatsConnection.deliverMessage`: `NatsConsumer c = (d==null) ? sub : d; ... processSlowConsumer(c)`). It will now report the **subscription** whose message hit the full queue — `deliverMessage` always has that `sub` (`subscribers.get(msg.getSID())`). Slow-state dedup stays on the queue owner (`c.markSlow()` / `c.isMarkedSlow()`), so it remains one notification per slow episode; only the handle handed to the listener changes. This is intended (the subscription names the subject; more useful than a shared dispatcher handle).

## Prerequisites / sequencing
- **§5.1 (Subscription interface → concrete class) should be done first.** With `Subscription` a concrete class extending `NatsConsumer`, it simply inherits the former-`Consumer` members (`drain`, `isActive`, pending/counts) — nothing to re-declare, and the two default constants have a natural public home on it. If §5.1 is *not* done first, `Subscription` is still an interface that `extends Consumer`, and deleting `Consumer` forces the same re-declaration treatment on `Subscription` that this plan applies to `Dispatcher` (see "If §5.1 is not done first" at the end). **Recommended: implement §5.1, then this.**
- This plan **forces a `Dispatcher` decision** (below), because `Dispatcher` is still an interface that `extends Consumer` and is used publicly (`Service`/`EndpointContext` call `d.drain(...)`).

## The `Dispatcher` decision (pick one; plan assumes D1b)
`Dispatcher` is `extends Consumer` and, per the usage scan, is relied on through the interface for: `drain` (main: `Service:257`, `EndpointContext:124`) and the pending/count methods (tests: `SlowConsumerTests` uses `Dispatcher d = nc.createDispatcher(...)` then `d.setPendingLimits`/`d.getPendingMessageCount`/`d.getDroppedCount`/`d.clearDroppedCount`/`d.drain`).

- **D1a — preserve API, re-declare all.** Move every former-`Consumer` method declaration onto the `Dispatcher` interface (10 methods). Zero API/behavior/test change; just relocates the clutter onto `Dispatcher`.
- **D1b — trim (recommended).** Declare on `Dispatcher` only its genuine public contract: `drain(long)` and `isActive()`. The pending/count methods drop off the *interface* and remain on the concrete `NatsDispatcher` (inherited from `NatsConsumer`). Retype the few tests that exercise them through a `Dispatcher` ref to `NatsDispatcher`. Small, deliberate API trim consistent with "pending is advanced/rare."
- **D2 — collapse `Dispatcher`→concrete.** `NatsDispatcher` becomes the public `Dispatcher`; inherits everything; no re-declaration, no test retype. Cleanest end-state but out of scope here (touches `NatsDispatcherWithExecutor`, every `Dispatcher` reference, and the deferred vertx concern). Track separately.

**This plan implements D1b.** If you prefer D1a (no API change) or D2 (full collapse), the step list changes only at step 4.

## Implementation steps

1. **Delete the interface.** Remove `core/src/main/java/io/synadia/client/Consumer.java`.

2. **`NatsConsumer` (abstract base) stays, drops the interface.**
   - `core/.../impl/NatsConsumer.java`: change `abstract class NatsConsumer implements Consumer` → `abstract class NatsConsumer` (remove `import ...Consumer`). Its methods are already public/concrete; they remain the shared implementation inherited by `NatsSubscription` and `NatsDispatcher`. No behavior change.

3. **Move the two default constants to a public home.**
   - `DEFAULT_MAX_MESSAGES = 512 * 1024` and `DEFAULT_MAX_BYTES = 64 * 1024 * 1024` currently live on `Consumer`. Put them on the public `Subscription` class (post-§5.1 concrete, `io.synadia.client.impl`) as `public static final long` — the natural public home for the pending-queue defaults. (Alternative: a tiny public constants holder; avoid putting them only on `NatsConsumer`, which stays impl-only and awkward to reference from `api`.)
   - Update references: `SubscribeBehavior.java` (`Consumer.DEFAULT_MAX_MESSAGES`/`..._BYTES` at field inits + the `{@code ...}` in the two pending javadocs) and tests (`SlowConsumerTests`, `ApiFieldsTest`, `JetStreamPushTests`) → the new home.

4. **`Dispatcher` interface (D1b).**
   - `core/.../Dispatcher.java`: remove `extends Consumer` (and the `import`). Declare `drain(long timeoutMillis)` and `isActive()` directly on `Dispatcher` (copy the javadoc from the old `Consumer`). Leave the pending/count methods off the interface.
   - They still exist on `NatsDispatcher` (via `NatsConsumer`), so advanced/test code uses the concrete type.

5. **Retype the slow-consumer path to `Subscription`.**
   - `ErrorListener.java`: `slowConsumerDetected(NatsConnection, Consumer slowConsumer)` → `(NatsConnection, Subscription slowConsumer)`; `supplyMessage(..., @Nullable Consumer slowConsumer, @Nullable Subscription sub, ...)` → the `slowConsumer` param becomes `Subscription` (consider merging it with the existing `Subscription sub` param — both would be `Subscription`; the body logs `slowConsumer.hashCode()` at line 154 and `sub.getSID()` at line 157, so switch the slow-consumer log to `getSID()` and collapse if the two are always the same handle). Remove the `import ...Consumer` and the two `{@link Consumer#...}` javadoc refs (point them at `Subscription`/`Statistics` as appropriate).
   - `NatsConnection.java`: `processSlowConsumer(Consumer)` → `processSlowConsumer(Subscription)`. In `deliverMessage` (~2024) pass `sub` (not `c`) to `processSlowConsumer`; keep `c.markSlow()` / `c.isMarkedSlow()` / `c.incrementDroppedCount()` on the owner `c`.
   - Impls of the callback: `ErrorListenerConsoleImpl.java`, `utils/DebugListener.java`, `utils/DebugErrorListener.java` — change the `Consumer` param type to `Subscription`, drop the `import ...Consumer`.

6. **Purge remaining `Consumer` references.**
   - Grep `io.synadia.client.Consumer` and `\bConsumer\b` (excluding `NatsConsumer`, `MessageConsumer`, `ConsumerContext`, `*ConsumerCreator`, `java.util.function.Consumer`, JetStream domain "consumer") across main + tests; fix stragglers (imports, javadoc `{@link Consumer...}`).

7. **Tests.**
   - `SlowConsumerTests.java`: dispatcher cases that call pending/count methods through `Dispatcher d` → retype those locals to `NatsDispatcher d` (D1b). Subscription cases already use `sub` (concrete post-§5.1) — fine.
   - Anything asserting `slowConsumerDetected`'s argument type / identity: update to expect a `Subscription`. (`ErrorListenerTests`, `SlowConsumerTests`.)
   - Update `Consumer.DEFAULT_*` constant references (step 3).

## Files touched (checklist)
- Delete: `core/.../client/Consumer.java`
- `core/.../impl/NatsConsumer.java` (drop `implements Consumer`)
- `core/.../impl/NatsSubscription.java` → becomes `Subscription` per §5.1; host the two constants (step 3)
- `core/.../Dispatcher.java` (drop `extends Consumer`; declare `drain`, `isActive`)
- `core/.../ErrorListener.java`, `impl/ErrorListenerConsoleImpl.java`, `utils/DebugListener.java`, `utils/DebugErrorListener.java` (retype callback param)
- `core/.../impl/NatsConnection.java` (`processSlowConsumer(Subscription)`; pass `sub`)
- `jetstream/.../api/SubscribeBehavior.java` (constant refs)
- Tests: `SlowConsumerTests`, `ErrorListenerTests`, `DrainTests` (only if it relied on `Dispatcher` pending methods), `ApiFieldsTest`, `JetStreamPushTests`

## Verification
- Compile: `./gradlew :core:compileJava :jetstream:compileJava :service:compileJava`.
- Grep confirms no remaining `io.synadia.client.Consumer` usage.
- Run: `SlowConsumerTests`, `DrainTests`, `ErrorListenerTests`, `JetStreamPushTests` (push pending + slow-consumer coverage), plus `service` drain tests. Confirm the slow-consumer callback fires with a `Subscription` and dedup still yields one notification per episode.

## Risks / notes
- **API change (D1b):** `Dispatcher` loses the pending/count methods from its *interface* (still on `NatsDispatcher`). If undesired, use D1a.
- **Behavior change:** `slowConsumerDetected` now hands back the subscription, not the dispatcher (documented above). Update any migration notes.
- **Constants relocation** is a source break for anyone referencing `Consumer.DEFAULT_MAX_MESSAGES` — expected in v3.
- **If §5.1 is not done first:** `Subscription` is still an interface `extends Consumer`; apply the D1b treatment to it too (drop `extends Consumer`; it already declares its own methods; add `drain`/`isActive` decls it must keep public), and host the constants on a public holder instead of the concrete class. Cleaner to just do §5.1 first.

## Out of scope
- §5.1 (Subscription interface removal) — prerequisite, separate.
- D2 full `Dispatcher` collapse — separate (vertx-tied).
- Any change to the pending/drop mechanism itself (§5.3 kept it as-is).

## RESOLVED (2026-07-07) — async pending limits

**Decision: keep it push-sync only (back to the original apply logic), and give users a direct way to set limits on the dispatcher for the async case.**
- `JetStream` applies the behavior's pending limits **only to non-dispatched (sync) push** subs — unchanged `if (lDispatcher == null)` guard; pull and async apply nothing. (We briefly extended the guard to pull-sync, then reverted — pull is bounded by its batch size and doesn't use pending limits, per the `SubscribeBehavior` javadoc.)
- Restored the **full pending/observability group on the public `Dispatcher` interface** (trimmed to concrete `NatsDispatcher` by D1b): `setPendingLimits`, `getPendingMessageLimit`/`getPendingByteLimit`, `getPendingMessageCount`/`getPendingByteCount`, `getDeliveredCount`, `getDroppedCount`, `clearDroppedCount`. For async, the dispatcher owns the single shared queue, so you bound and monitor a slow async consumer on the dispatcher directly. `setPendingLimits` javadoc documents *how it matters*: the server controls flow, so a slow `MessageHandler` backs up the dispatcher queue → drops + `slowConsumerDetected`. Coverage: existing `SlowConsumerTests` dispatcher tests retyped to `Dispatcher d` (exercise the group via the interface) + new `testDispatcherDeliveredCount`.
- `SubscribeBehavior.pendingMessageLimit/ByteLimit` javadoc (authored by Scott) already states it correctly: applies to sync push; async is bounded by the dispatcher's own limits; pull doesn't use it.
- Test `testPendingLimits` now asserts a dispatched sub keeps DEFAULT (its own limit is never used for async). All green.
- **Not mirrored onto the `Subscription` interface** (deliberate): a sync sub is also a queue owner, but the `Subscription` interface is slated to collapse into concrete `NatsSubscription` (§5.1), and sync users already reach the group via the concrete type `nc.subscribe` returns — so adding it to the interface would just be churn on a type that's going away.
- Rationale for push-sync-only: on push the server controls flow (sync *or* async can fall behind), but the async queue lives on the dispatcher, not the sub — so the sub-level behavior limit is meaningless for async; the dispatcher is the right place. On pull the user controls flow via batch size, so the limit is effectively unused.

Original write-up (kept for context):

## UNFINISHED BUSINESS — async pending limits (RESOLVED above)

While renaming/cleaning up `NatsMessageSink` we added an async case to `JetStreamPushTests.testPendingLimits` (a `bhAsyncNonDefaultValid` behavior that sets `pendingMessageLimit`/`pendingByteLimit` to a non-default value) and it fails. This is NOT related to the Consumer removal or the unlimited-sentinel (`-1`) change — those are done and green. It's a pre-existing behavior gap the new test exposed.

### The question
Should the pending message/byte limits be settable in ALL subscription cases, or only push+sync as today? Right now `JetStream` applies them only to sync subs:

```java
// JetStream.java ~428, inside the push subFactory
if (lDispatcher == null) {                 // only when there is NO dispatcher (sync)
    sub.setPendingLimits(jssc.getPendingMessageLimit(), jssc.getPendingByteLimit());
}
```

For an async (dispatcher-owned) push sub the subscription's own queue is null — the dispatcher owns the single shared queue and its pending limits — so `setPendingLimits` is skipped and `subAsync.getPendingMessageLimit()` returns DEFAULT. Hence the test's `assertEquals(maxMessages, subAsync.getPendingMessageLimit())` (where `maxMessages = DEFAULT_MAX_MESSAGES - 1024 = 523264`) fails with actual `524288`.

### Possible answers
- **(A) Test should expect DEFAULT.** Treat today's behavior as intended: for async the dispatcher owns the queue/limits, so a dispatcher-owned sub ignores the behavior's pending limit and stays at DEFAULT. Change the async assertions to expect `DEFAULT_MAX_MESSAGES`/`DEFAULT_MAX_BYTES` (this then documents "limit is ignored for async", distinct from `bhAsyncDefault`). Smallest change, but arguably enshrines the gap.
- **(B) Fix the behavior — make it settable in all cases (LEANING THIS WAY).** Apply the behavior's pending limit for async too — most likely by pushing it onto the owning dispatcher (that's where the async queue actually lives), so a per-subscription pending limit on an async behavior takes effect. Leave the test expecting the non-default value. This is a code/behavior change, not just a test fix.
- **(C) Assert on the dispatcher.** Keep the code as-is but point the async assertion at the queue owner (`d.getPendingMessageLimit()`), since the test already calls `d.setPendingLimits(1000, ...)`. This tests the real owner but doesn't make per-behavior async limits work.

### Scott's inclination / reasoning (2026-07-05, decide next session)
- Leaning toward **(B): it should be settable in all cases.** This also explains the original understanding that pending limits "only mattered in push+sync" — that was the assumption baked into the current guard, not necessarily correct.
- If this is a real gap, the reason nobody has reported it is that the feature isn't well advertised or easy to use, so it's probably rarely set.
- Before applying it in all cases, think through the ramifications — is a per-subscription pending limit on a shared dispatcher a footgun (multiple subs on one dispatcher each trying to set the shared queue's limits)? Maybe, maybe not.
- Consider that this may have been wrong in **v2 the whole time** — if so, go back and fix v2 as well.
