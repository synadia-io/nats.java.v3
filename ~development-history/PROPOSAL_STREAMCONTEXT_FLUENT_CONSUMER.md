# Proposal — Fluent consumer creation from `StreamContext`

## Problem

Today, creating a consumer from a `StreamContext` forces the caller to repeat the stream name that the context already knows:

```java
streamContext.createOrUpdateConsumer(
    new PullConsumerCreator(streamContext.getStreamName())   // <-- redundant
        .durable("my-consumer")
        .maxBatch(100)
);
```

Same shape for `createOrderedConsumer(...)`. Mildly annoying every time, and easy to get wrong (someone passes the wrong stream name into a creator built for a different stream).

## What we want

A fluent chain that:
- Starts from `StreamContext` (which already knows the stream).
- Lets the caller configure the consumer.
- Ends in a terminal `.create()` that returns the `ConsumerContext` / `OrderedConsumerContext`.

```java
ConsumerContext ctx = streamContext.newConsumer()
    .durable("my-consumer")
    .maxBatch(100)
    .priorityPolicy(PriorityPolicy.PinnedClient)
    .create();
```

`StreamContext.newCreator()` / `newOrderedCreator()` (returning a pre-filled creator and then re-feeding it to `createOrUpdateConsumer(...)`) was considered and ruled out — still two-step.

---

## Variant 1 — Dedicated fluent builders bound to the context (recommended)

### New types in `io.synadia.client.api`

```java
public interface PullConsumerBuilder {
    PullConsumerBuilder durable(@Nullable String durable);
    PullConsumerBuilder maxExpires(@Nullable Duration maxExpires);
    PullConsumerBuilder maxExpires(long maxExpires);
    PullConsumerBuilder maxPullWaiting(long maxPullWaiting);
    PullConsumerBuilder maxBatch(long maxBatch);
    PullConsumerBuilder maxBytes(@Nullable Long maxBytes);
    PullConsumerBuilder maxBytes(long maxBytes);
    PullConsumerBuilder priorityGroups(String... priorityGroups);
    PullConsumerBuilder priorityGroups(@Nullable List<String> priorityGroups);
    PullConsumerBuilder priorityPolicy(@Nullable PriorityPolicy policy);
    PullConsumerBuilder priorityTimeout(@Nullable Duration priorityTimeout);
    PullConsumerBuilder priorityTimeout(long priorityTimeoutMillis);

    /** Terminal: equivalent to {@link StreamContext#createOrUpdateConsumer(PullConsumerCreator)}. */
    @NonNull ConsumerContext create() throws IOException, JetStreamApiException;
}

public interface PullOrderedConsumerBuilder {
    // same shape as PullOrderedConsumerCreator's setters
    PullOrderedConsumerBuilder maxExpires(@Nullable Duration maxExpires);
    PullOrderedConsumerBuilder maxExpires(long maxExpires);
    PullOrderedConsumerBuilder maxPullWaiting(long maxPullWaiting);
    PullOrderedConsumerBuilder maxBatch(long maxBatch);
    PullOrderedConsumerBuilder maxBytes(@Nullable Long maxBytes);
    PullOrderedConsumerBuilder maxBytes(long maxBytes);
    PullOrderedConsumerBuilder priorityGroups(String... priorityGroups);
    PullOrderedConsumerBuilder priorityGroups(@Nullable List<String> priorityGroups);
    PullOrderedConsumerBuilder priorityPolicy(@Nullable PriorityPolicy policy);
    PullOrderedConsumerBuilder priorityTimeout(@Nullable Duration priorityTimeout);
    PullOrderedConsumerBuilder priorityTimeout(long priorityTimeoutMillis);

    /** Terminal: equivalent to {@link StreamContext#createOrderedConsumer(PullOrderedConsumerCreator)}. */
    @NonNull OrderedConsumerContext create() throws IOException, JetStreamApiException;
}
```

### New methods on `StreamContext`

```java
@NonNull PullConsumerBuilder        newConsumer();
@NonNull PullOrderedConsumerBuilder newOrderedConsumer();
```

### Implementations in `io.synadia.client.impl`

Thin wrappers — hold the pre-populated creator and a back-reference to the `StreamContext`. Each setter forwards to the underlying creator and returns `this`; `.create()` calls back into the context:

```java
final class PullConsumerBuilderImpl implements PullConsumerBuilder {
    private final StreamContext sc;
    private final PullConsumerCreator creator;

    PullConsumerBuilderImpl(StreamContext sc) {
        this.sc = sc;
        this.creator = new PullConsumerCreator(sc.getStreamName());
    }

    public PullConsumerBuilder durable(String durable)        { creator.durable(durable);    return this; }
    public PullConsumerBuilder maxBatch(long n)               { creator.maxBatch(n);         return this; }
    public PullConsumerBuilder maxExpires(Duration d)         { creator.maxExpires(d);       return this; }
    public PullConsumerBuilder maxExpires(long ms)            { creator.maxExpires(ms);      return this; }
    public PullConsumerBuilder maxPullWaiting(long n)         { creator.maxPullWaiting(n);   return this; }
    public PullConsumerBuilder maxBytes(Long n)               { creator.maxBytes(n);         return this; }
    public PullConsumerBuilder maxBytes(long n)               { creator.maxBytes(n);         return this; }
    public PullConsumerBuilder priorityGroups(String... g)    { creator.priorityGroups(g);   return this; }
    public PullConsumerBuilder priorityGroups(List<String> g) { creator.priorityGroups(g);   return this; }
    public PullConsumerBuilder priorityPolicy(PriorityPolicy p) { creator.priorityPolicy(p); return this; }
    public PullConsumerBuilder priorityTimeout(Duration d)    { creator.priorityTimeout(d);  return this; }
    public PullConsumerBuilder priorityTimeout(long ms)       { creator.priorityTimeout(ms); return this; }

    public ConsumerContext create() throws IOException, JetStreamApiException {
        return sc.createOrUpdateConsumer(creator);
    }
}
```

(Same shape for `PullOrderedConsumerBuilderImpl`, wrapping `PullOrderedConsumerCreator` and terminating in `sc.createOrderedConsumer(creator)`.)

### Existing API stays

`createOrUpdateConsumer(PullConsumerCreator)`, `createOrderedConsumer(PullOrderedConsumerCreator)`, and the creator classes themselves are unchanged. The new path is a strict add: useful when you have a creator already in hand (deserialization, reuse, tests, code that builds creators in one place and submits them in another).

### Pros / Cons

**Pros**
- Compile-time clarity: a `PullConsumerBuilder` always has a back-reference. You cannot accidentally call `.create()` on a creator that has no context.
- No coupling between `PullConsumerCreator` (a config DTO) and `StreamContext` (a connection-bound thing). They stay clean and independent.
- Future-proof: if the builder ever needs methods the creator shouldn't have (e.g. `.withDefaults()` shortcuts), you can add them only on the builder.

**Cons**
- Forwarding boilerplate. Roughly 12 forwarding methods × 2 builders = ~24 one-liners. Mechanical, easy to keep in sync, but it's there.

---

## Variant 1b — Subclass the existing creator (recommended, simpler than 1)

Same idea as Variant 1 — a separate type bound to the `StreamContext`, with a terminal `.create()` — but **no interface**. The new type is a concrete subclass of `PullConsumerCreator`. The existing `AbstractEphemeralConsumerCreator<T>` already uses F-bounded self-typing, so all the fluent setters need is covariant return overrides on the subclass.

### New concrete classes in `io.synadia.client.api`

```java
public final class StreamPullConsumerBuilder extends PullConsumerCreator {
    private final StreamContext sc;

    StreamPullConsumerBuilder(StreamContext sc) {     // package-private — only StreamContext can construct
        super(sc.getStreamName());
        this.sc = sc;
    }

    // Covariant return overrides — each setter forwards to super and re-types the return.
    @Override public StreamPullConsumerBuilder durable(String d)         { super.durable(d);          return this; }
    @Override public StreamPullConsumerBuilder maxExpires(Duration d)    { super.maxExpires(d);       return this; }
    @Override public StreamPullConsumerBuilder maxExpires(long ms)       { super.maxExpires(ms);      return this; }
    @Override public StreamPullConsumerBuilder maxPullWaiting(long n)    { super.maxPullWaiting(n);   return this; }
    @Override public StreamPullConsumerBuilder maxBatch(long n)          { super.maxBatch(n);         return this; }
    @Override public StreamPullConsumerBuilder maxBytes(Long n)          { super.maxBytes(n);         return this; }
    @Override public StreamPullConsumerBuilder maxBytes(long n)          { super.maxBytes(n);         return this; }
    @Override public StreamPullConsumerBuilder priorityGroups(String... g) { super.priorityGroups(g); return this; }
    @Override public StreamPullConsumerBuilder priorityGroups(List<String> g) { super.priorityGroups(g); return this; }
    @Override public StreamPullConsumerBuilder priorityPolicy(PriorityPolicy p) { super.priorityPolicy(p); return this; }
    @Override public StreamPullConsumerBuilder priorityTimeout(Duration d) { super.priorityTimeout(d); return this; }
    @Override public StreamPullConsumerBuilder priorityTimeout(long ms)  { super.priorityTimeout(ms); return this; }

    public ConsumerContext create() throws IOException, JetStreamApiException {
        return sc.createOrUpdateConsumer(this);
    }
}
```

(Same shape for `StreamPullOrderedConsumerBuilder extends PullOrderedConsumerCreator`, terminating in `sc.createOrderedConsumer(this)`.)

### New methods on `StreamContext`

```java
@NonNull StreamPullConsumerBuilder        newConsumer();
@NonNull StreamPullOrderedConsumerBuilder newOrderedConsumer();
```

Implementation: one-liner each — `return new StreamPullConsumerBuilder(this);`.

### Call site (identical to Variant 1)

```java
ConsumerContext ctx = streamContext.newConsumer()
    .durable("my-consumer")
    .maxBatch(100)
    .priorityPolicy(PriorityPolicy.PinnedClient)
    .create();
```

### Bonus — the subclass IS-A creator

Because `StreamPullConsumerBuilder extends PullConsumerCreator`, the builder is also a valid argument to the existing `createOrUpdateConsumer(PullConsumerCreator)`. So you can split the build and submit:

```java
StreamPullConsumerBuilder b = streamContext.newConsumer().durable("X").maxBatch(100);
// pick whichever terminal you like:
b.create();                                  // fluent
streamContext.createOrUpdateConsumer(b);     // also works — b IS-A PullConsumerCreator
```

This is genuinely useful when the build site and submit site live in different layers of your app.

### "Placeholder stream name" tangent

In your phrasing you considered "uses a placeholder for the stream name and requires the stream context as a parameter to create" — i.e. construct the builder first, supply the context at `.create(sc)` time. That works too, but it means re-setting the stream name on an already-constructed creator and complicates the chain (you have to thread the `sc` into the terminal). The package-private-constructor-with-context approach above avoids both issues — the builder is born already bound to a context, and the only legal way to obtain one is via `StreamContext.newConsumer()`.

### Pros / Cons (vs Variant 1)

**Pros**
- No interfaces — two fewer public types.
- Subclass IS-A creator → composes with existing `createOrUpdateConsumer(...)` for free, no duplication.
- Same call-site ergonomics as Variant 1.
- Same compile-time safety as Variant 1 (the package-private constructor + covariant return makes `.create()` only reachable on instances obtained from `StreamContext.newConsumer()`).

**Cons**
- Inherits the creator's entire public surface, so the builder is necessarily a superset of the creator's API. If you ever want builder-only methods that shouldn't appear on the creator (e.g. a `.withDefaults()` shortcut), they end up on the builder via subclass — fine in practice but a subtle one-way coupling. Variant 1's interfaces would let you split.
- Sub-classing a concrete class is mildly less idiomatic than implementing an interface in some shops. Counter-argument: the IS-A relationship is genuine here.

### Boilerplate cost

Twelve covariant return overrides per builder × two builders = ~24 one-liners. Same count as Variant 1's forwarding methods, just a different idiom.

---

## Variant 2 — Make the creator context-aware (lighter, less type-safe)

### Modifications to `PullConsumerCreator` and `PullOrderedConsumerCreator`

Add a nullable back-reference to `StreamContext` and a `create()` terminal that uses it. Existing public constructor stays standalone; a new package-private constructor takes a `StreamContext`.

```java
public class PullConsumerCreator extends AbstractEphemeralConsumerCreator<PullConsumerCreator> {
    @Nullable private final StreamContext parent;

    public PullConsumerCreator(String stream) { super(stream); this.parent = null; }
    PullConsumerCreator(StreamContext parent) { super(parent.getStreamName()); this.parent = parent; }

    public ConsumerContext create() throws IOException, JetStreamApiException {
        if (parent == null) {
            throw new IllegalStateException(
                "create() is only available when the creator was obtained via StreamContext.newConsumer(); " +
                "for standalone creators, pass to StreamContext.createOrUpdateConsumer(creator) instead");
        }
        return parent.createOrUpdateConsumer(this);
    }
}
```

`StreamContext.newConsumer()` returns the result of `new PullConsumerCreator(this)`. The F-bounded self-typing in `AbstractEphemeralConsumerCreator<PullConsumerCreator>` already makes setters return `PullConsumerCreator`, so the fluent chain preserves `.create()` access — no setter overrides needed.

### Call site (identical to Variant 1)

```java
ConsumerContext ctx = streamContext.newConsumer()
    .durable("my-consumer")
    .maxBatch(100)
    .create();
```

### Pros / Cons

**Pros**
- Almost no new code — two constructor variants and one `create()` method per creator class.
- Same call-site ergonomics as Variant 1.

**Cons**
- `create()` is callable on standalone creators but throws at runtime. Documentable, but not type-safe.
- Mild API smell: `PullConsumerCreator` is now mostly-config-DTO with one method that depends on hidden state.

---

## Naming nit (applies to either variant)

The terminal call is `.create()` but it maps to `createOrUpdateConsumer` semantically. Options:

- **`.create()`** — short, fluent-reads-naturally. Mild semantic mismatch (the underlying call also updates). Recommended.
- **`.createOrUpdate()`** — explicit, verbose, ugly in a fluent chain. Avoid.
- **`.commit()`** / **`.apply()`** — neutral words. Either works but is less discoverable than `.create()`.

The existing public API already has this dance (a public `createOrUpdateConsumer` exists; users routinely call it "create" in conversation). Keep `.create()`; document the create-or-update semantic in the Javadoc.

---

## Recommendation

**Variant 1b.** Same call-site ergonomics as Variant 1, same compile-time safety, ~same boilerplate count — but two fewer public types and the bonus that the builder composes with the existing `createOrUpdateConsumer(...)` for free. The "subclassing a concrete class is mildly unidiomatic" smell is outweighed by avoiding the interface proliferation.

Variant 1 (interface + impl split) is worth it only if you genuinely expect the builder API to diverge from the creator API. Today it doesn't.

Variant 2 is a defensible "ship it Monday" choice if even 1b's boilerplate feels too heavy — but the next time someone constructs a creator standalone and calls `.create()`, they'll hit the runtime exception and wish for 1b.

---

## Implementation sketch (Variant 1b)

1. Add `StreamPullConsumerBuilder` in `jetstream/src/main/java/io/synadia/client/api/`, extending `PullConsumerCreator`. Package-private constructor takes `StreamContext`. Twelve covariant return overrides. One `create()` terminal.
2. Add `StreamPullOrderedConsumerBuilder` in the same package, extending `PullOrderedConsumerCreator`. Same shape.
3. Add `newConsumer()` / `newOrderedConsumer()` methods to the `StreamContext` interface (`jetstream/src/main/java/io/synadia/client/impl/StreamContext.java`).
4. Implement them in `NatsStreamContext` (or wherever `StreamContext` is currently implemented). One-liner each — `return new StreamPullConsumerBuilder(this);`.
5. Tests in `jetstream/src/test/java/io/synadia/client/impl/` covering: every setter forwards correctly (assert the field on the underlying creator after each call), `.create()` returns the expected `ConsumerContext`, the resulting consumer has the configured options on the server, and the bonus path (`streamContext.createOrUpdateConsumer(builder)`) works.
6. Add a usage example to `JetStream` Javadoc (or wherever the existing recommended-usage example lives).
7. No migration-guide entry needed — this is a strict-add API; existing code keeps compiling.

Out of scope:
- Replacing the existing `createOrUpdateConsumer(PullConsumerCreator)` and `createOrderedConsumer(PullOrderedConsumerCreator)` methods. They stay.
- Touching `PullConsumerCreator` / `PullOrderedConsumerCreator` themselves. They stay as standalone config DTOs.
