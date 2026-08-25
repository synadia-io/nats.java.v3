# Plan: Fluent Subscribe/Subscription Builder for JetStream

## Status
Explored and implemented once, then **reverted** (this doc captures the work so it can be revisited). The reason for the revert: as built, the fluent builder was **not clearly better than the existing overloads** — see "Why it wasn't better" below. Any revisit should start from one of the "Candidate directions" rather than re-applying the reverted code as-is.

## Goal
`JetStream` has ~10 `pushSubscribe(...)` and ~10 `pullSubscribe(...)` overloads. Provide a fluent way to build a `JetStreamPushSubscription` / `JetStreamPullSubscription` that reads well and reduces the unwieldy overload count.

## What was built (the reverted implementation)
Two builder classes in `io.synadia.client.impl` (placed in `impl`, not `api`, so `subscribe()` could call `JetStream`'s package-private primitives directly):

- `PushSubscriptionBuilder`
- `PullSubscriptionBuilder`

Entry points on `JetStream` — **no-arg** factory methods only (the user explicitly did NOT want a `pushSubscribe(builder)` method to call):
```java
public PushSubscriptionBuilder pushSubscribe() { return new PushSubscriptionBuilder(this); }
public PullSubscriptionBuilder pullSubscribe() { return new PullSubscriptionBuilder(this); }
```

Each builder held:
- **Source setters** (the distinguishing args of the old overloads): `consumerInfo(ConsumerInfo)`, `stream(String)`, `consumerName(String)`, `subject(String)`, and creator setters.
- **Creator setters typed to their own side** (so cross-side is a compile error): push had `creator(PushConsumerCreator)` + `creator(PushOrderedConsumerCreator)`; pull had the two Pull equivalents. Two overloads (not one) because the plain and ordered creators live in separate hierarchies whose only common ancestor is `ConsumerCreator` — a single typed param couldn't cover both push types without also admitting pull types. Field/getter stored as `ConsumerCreator<?>`.
- **Behavior setters that delegate to a wrapped `SubscribeBehavior`** (single source of truth for normalization): `dispatcher`, `handler`, `messageAlarmTime`, `pendingMessageLimit`, `pendingByteLimit`, plus `subscribeBehavior(SubscribeBehavior)` to copy a whole one in. The builder held `private final SubscribeBehavior behavior = new SubscribeBehavior();` and each setter delegated to it.
- Getters for every source field.

`subscribe()` (the terminal) actually created the subscription — being in `impl` it called `js`'s package-private methods:
```java
public JetStreamPushSubscription subscribe() throws IOException, JetStreamApiException {
    // TODO validation: enforce exactly one source and validate subject/stream.
    ConsumerInfo ci = consumerInfo;
    if (ci == null) {
        if (creator != null)            ci = js._createConsumer(stream, creator, Create);
        else if (consumerName != null)  ci = js.strictGetConsumerInfo(stream, consumerName);
        else {
            String resolvedStream = js.lookupStreamBySubject(subject);
            if (resolvedStream == null) throw JsSubNoMatchingStreamForSubject.instance();
            ci = js._createConsumer(resolvedStream, new PushConsumerCreator().filterSubject(subject), Create);
        }
    }
    PushOrderedConsumerCreator ordered = creator instanceof PushOrderedConsumerCreator occ ? occ : null;
    return (JetStreamPushSubscription) js.createSubscription(ci, behavior, ordered, null);
}
```
(Pull mirrored this with `PullConsumerCreator` / `PullOrderedConsumerCreator`.) The ordered check used the **concrete** ordered type (not `AbstractOrderedConsumerCreator`) because the setters only accept concrete types; it still passes to `createSubscription`'s `AbstractOrderedConsumerCreator<?>` param via subtyping. Validation of arguments and of "exactly one source" was deliberately deferred.

Everything compiled (`:jetstream:compileJava` exit 0). No tests written yet.

## Why it wasn't better than the overloads
- **Source params just moved to setters** — the same `consumerInfo`/`stream`/`consumerName`/`subject`/`creator` that distinguished the overloads became five optional setters on a bag.
- **Lost the overloads' compile-time guardrails** — the old overloads only let you express *valid* source combinations. The no-arg builder lets you set `stream` + `subject` + `consumerName` together (nonsense), caught only by the deferred runtime `// TODO validation`.
- **The only real win was small** — folding `SubscribeBehavior`'s knobs into the fluent tail, which removes the need for a separate behavior object and the ×2 (with/without behavior) overloads.
- **Root cause diagnosis:** the overload count is unwieldy because of **(source variants) × (with/without SubscribeBehavior)** plus the separate behavior object — NOT the source variants themselves. The no-arg builder attacks the source axis (which was fine) and weakens it.

## Candidate directions (pick one on revisit)
1. **Source-first builder (recommended).** Keep the source as a *typed entry* and make only the behavior fluent:
   ```java
   js.pushSubscribe(stream, creator).handler(h).messageAlarmTime(5000).subscribe();
   ```
   ~5 entry overloads per side (ci / stream+name / subject / stream+creator / stream+orderedCreator) instead of ~10, **no** separate `SubscribeBehavior` object, no invalid-combination footgun (source fixed at the entry method). Rework the two builders to be behavior-only; add the typed entries to `JetStream`.
2. **Keep the no-arg builder.** Accept runtime validation for source-combination. Simplest single entry point, weakest guardrails. (This is what was built and reverted.)
3. **Drop the builder; trim overloads.** Collapse the ×2 by making `SubscribeBehavior` a nullable trailing param on one overload per source (~5 per side). Least new code, fully type-safe, but you still construct a `SubscribeBehavior` for behavior.

## Files touched (to re-apply or to confirm cleanly reverted)
- NEW (deleted on revert): `jetstream/.../impl/PushSubscriptionBuilder.java`, `jetstream/.../impl/PullSubscriptionBuilder.java`
- EDITED (revert the added no-arg entry methods): `jetstream/.../impl/JetStream.java` — added no-arg `pushSubscribe()` / `pullSubscribe()` returning the builders.
