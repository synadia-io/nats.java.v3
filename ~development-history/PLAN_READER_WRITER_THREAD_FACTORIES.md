# Plan: individual thread factories / executors for reader and writer (port of jnats PR #1583)

Port [nats-io/nats.java#1583](https://github.com/nats-io/nats.java/pull/1583) — "Optional individual thread factories for reader and writer" — into V3. Reference version: jnats **2.26.0**.

## Goal

Today the reader and writer are both submitted to the single shared connection executor:
- `NatsConnectionReader.start()` → `connection.getExecutor().submit(this, ...)` (`NatsConnectionReader.java:105`)
- `NatsConnectionWriter.start()` → `connection.getExecutor().submit(this, ...)` (`NatsConnectionWriter.java:98`)

Add four opt-in knobs so users can isolate the two long-lived protocol-I/O threads (naming, priority, daemon flag, virtual threads, or a bounded executor) **without** shrinking the general `executor` — which must stay multi-threaded because the reader loop, the writer loop, and every `Dispatcher` all run on it concurrently. The four knobs, mirroring the existing `connect*`/`callback*` pairs:

- `readerThreadFactory(ThreadFactory)` / `writerThreadFactory(ThreadFactory)`
- `readerExecutor(ExecutorService)` / `writerExecutor(ExecutorService)`

Precedence per side: `*Executor` wins over `*ThreadFactory`; if neither is set, fall back to the **shared general executor** (today's behavior — no new threads, fully backward compatible).

Ownership: a user-supplied `*Executor` is used as-is and never shut down (caller owns it). An executor we build from a supplied `*ThreadFactory` is dedicated/internal and we shut it down. This exactly matches the existing `connect`/`callback` rules (`Options.java` `callbackExecutorIsInternal`/`connectExecutorIsInternal`).

## V3 differences from the PR (do NOT copy the PR verbatim)

1. V3's builder is a **separate `OptionsBuilder` class**, not an inner `Options.Builder`.
2. V3 property keys are **camelCase**, not the PR's dot-form. Follow the existing V3 convention in `OptionsProperties.java:271-283`:
   - `readerExecutorServiceClass`, `writerExecutorServiceClass`, `readerThreadFactoryClass`, `writerThreadFactoryClass`
   - (the PR used `reader.executor.service.class` etc. — ignore those.)
3. V3 resolves `connect`/`callback` factories via `Executors.newSingleThreadExecutor(factory)` and defaults via `DEFAULT_SINGLE_THREAD_EXECUTOR.get()`. For **reader/writer** we want a per-task-thread executor built from the factory (see decision below), not a single-thread one.
4. Ordering convention (per repo style): keep parallel groups alphabetical and mirrored. `callback` < `connect` < `reader` < `writer`, so add reader/writer entries **after** the connect entries in every parallel spot, reader before writer.

## Design decision: what executor to build from a supplied reader/writer factory

When only a `*ThreadFactory` is supplied (no `*Executor`), build a dedicated executor from it. Recommendation: **mirror the PR** — add an `_getInternalExecutor(ThreadFactory)` overload (the cached `ThreadPoolExecutor(0, MAX, 500ms, SynchronousQueue, factory)`), and use it for reader/writer — rather than V3's `Executors.newSingleThreadExecutor(factory)` used for connect/callback.

Rationale: the reader/writer tasks are **re-submitted on every reconnect** (`start()` runs again). A cached SynchronousQueue pool spins a fresh thread per submit, so a restart never serializes behind a not-yet-fully-returned prior task. A single-thread executor could (in a tight reconnect window) queue the new reader/writer behind the old one. The cached pool with a `500ms` keepalive idles back to zero threads between reconnects, so it's not wasteful. (Note this is a deliberate deviation from the connect/callback single-thread style — call it out in review.)

## File-by-file changes

### `core/src/main/java/io/synadia/client/OptionsProperties.java`
Add four constants after the connect/callback ones (`:283`):
```java
String PROP_READER_EXECUTOR_SERVICE_CLASS = PFX + "readerExecutorServiceClass";
String PROP_WRITER_EXECUTOR_SERVICE_CLASS = PFX + "writerExecutorServiceClass";
String PROP_READER_THREAD_FACTORY_CLASS   = PFX + "readerThreadFactoryClass";
String PROP_WRITER_THREAD_FACTORY_CLASS   = PFX + "writerThreadFactoryClass";
```

### `core/src/main/java/io/synadia/client/OptionsBuilder.java`
- Fields (after `:115`): `ExecutorService userReaderExecutor = null; ExecutorService userWriterExecutor = null; ThreadFactory userReaderThreadFactory = null; ThreadFactory userWriterThreadFactory = null;`
- `properties(...)` wiring (after `:262`):
```java
classnameProperty(props, PROP_READER_EXECUTOR_SERVICE_CLASS, o -> readerExecutor((ExecutorService) o));
classnameProperty(props, PROP_WRITER_EXECUTOR_SERVICE_CLASS, o -> writerExecutor((ExecutorService) o));
classnameProperty(props, PROP_READER_THREAD_FACTORY_CLASS, o -> readerThreadFactory((ThreadFactory) o));
classnameProperty(props, PROP_WRITER_THREAD_FACTORY_CLASS, o -> writerThreadFactory((ThreadFactory) o));
```
- Four builder setters mirroring `connectExecutor`/`connectThreadFactory` (`:1053`, `:1077`), placed after the connect ones: `readerExecutor`, `writerExecutor`, `readerThreadFactory`, `writerThreadFactory`. Javadoc should state the precedence + "falls back to the shared connection executor when neither is set" and the ownership/lifecycle note.
- Copy constructor (after `:1497`): copy all four new fields from `o`.

### `core/src/main/java/io/synadia/client/Options.java`
- `final` user fields (after `:115`): `userReaderExecutor`, `userWriterExecutor`, `userReaderThreadFactory`, `userWriterThreadFactory`.
- Lazy resolved fields (after `:128`): `ExecutorService resolvedReaderExecutor; ExecutorService resolvedWriterExecutor;`
- Assign from builder in the `Options(Builder)` ctor (after `:239`).
- Add `_getInternalExecutor(ThreadFactory threadFactory)` overload; refactor the existing no-arg `_getInternalExecutor()` to call it with `new DefaultThreadFactory(threadPrefix)`.
- `getReaderExecutor()` / `getWriterExecutor()` — return `getExecutor()` when neither user field is set; otherwise lazily resolve under `executorsLock` to `userReaderExecutor` (if set) else `_getInternalExecutor(userReaderThreadFactory)`; cache in `resolvedReaderExecutor`. Mirror for writer. (Shape = PR's `getReaderExecutor`/`getWriterExecutor`.)
- `readerExecutorIsInternal()` / `writerExecutorIsInternal()` → `userReaderExecutor == null && userReaderThreadFactory != null` (dedicated one we built and must shut down). Add after `connectExecutorIsInternal()` (`:393`).
- `shutdownExecutors()` — add two blocks (after the connect block at `:436`), inside the same `executorUseCount == 0` guard, shutting down `resolvedReaderExecutor`/`resolvedWriterExecutor` when the corresponding `*IsInternal()` is true:
```java
if (resolvedReaderExecutor != null && readerExecutorIsInternal()) {
    ExecutorService es = resolvedReaderExecutor; resolvedReaderExecutor = null; es.shutdownNow();
}
if (resolvedWriterExecutor != null && writerExecutorIsInternal()) {
    ExecutorService es = resolvedWriterExecutor; resolvedWriterExecutor = null; es.shutdownNow();
}
```

### `core/src/main/java/io/synadia/client/impl/NatsConnection.java`
- Fields (after `:93`): `protected ExecutorService readerExecutor; protected ExecutorService writerExecutor;`
- Assign in ctor (after `:162`): `this.readerExecutor = options.getReaderExecutor(); this.writerExecutor = options.getWriterExecutor();`
- Null on close (after `:860`): `readerExecutor = null; writerExecutor = null;`
- Getters near `getExecutor()` (`:2264`): `protected ExecutorService getReaderExecutor() { return readerExecutor; }` and `getWriterExecutor()`.
- (Optional, for symmetry with the other `*IsClosed()` helpers at `:875-877`: `readerExecutorIsClosed()`/`writerExecutorIsClosed()` — add only if something references them; the PR added them but they may be unused in V3.)

### `core/src/main/java/io/synadia/client/impl/NatsConnectionReader.java`
`start()` (`:105`): `connection.getExecutor()` → `connection.getReaderExecutor()`.

### `core/src/main/java/io/synadia/client/impl/NatsConnectionWriter.java`
`start()` (`:98`): `connection.getExecutor()` → `connection.getWriterExecutor()`.

## Edge cases / invariants to preserve

- **Default path unchanged:** neither knob set → reader/writer run on the shared general executor exactly as today. No behavioral change, no new threads.
- **Shared `Options` across connections:** resolved reader/writer executors are cached on `Options` and gated by `executorUseCount` in `shutdownExecutors()` — same sharing semantics as the existing executors. A dedicated (factory-built) reader/writer executor is a cached-pool that creates a thread per submit, so sharing an `Options` across connections won't starve them.
- **User-supplied `*Executor` is never shut down** — `*IsInternal()` is false for it.
- **Reconnect:** reader/writer `start()` re-submits; the cached-pool overload avoids serializing a restart (see decision above).
- **Precedence:** `*Executor` beats `*ThreadFactory` (getter checks `userXExecutor != null` first).

## Tests

- `OptionsTests`: `testReaderExecutor` / `testWriterExecutor` — set a named `ThreadFactory`, assert a task submitted to `options.getReaderExecutor()` runs on a thread with that name; assert the copy constructor (`new OptionsBuilder(options)`) preserves the factory. (Direct port of the PR's two tests.)
- `OptionsTests`: neither-set case → `getReaderExecutor() == getExecutor()` (same instance); `*Executor` set → returned as-is and precedence over `*ThreadFactory`.
- Properties round-trip: setting `readerThreadFactoryClass` / `writerExecutorServiceClass` etc. via `properties(...)` resolves to the expected instances (follow existing connect/callback property tests).
- Shutdown: a factory-built reader/writer executor is shut down on connection close; a user-supplied one is **not** (assert `!isShutdown()` after close). Mirror existing connect/callback shutdown tests.
- Connection-level (in the impl connection test): build a connection with named reader+writer factories and assert the reader/writer threads carry those names (the PR added an analogous test in `NatsConnectionImplTests`).

## Docs

- Update `MIGRATION_GUIDE_OPTIONS.md` (and/or README) with the four new options and the "don't shrink the general executor; isolate I/O with reader/writer executors instead" guidance from the PR's new README section. Keep it short.

## Risk

Low. Additive, opt-in, default path byte-for-byte unchanged. The only judgment call is single-thread vs cached-pool for the factory-built executors (recommended: cached-pool, to match the PR and be reconnect-safe). Touches `Options`/`OptionsBuilder`/`OptionsProperties` (public API additions) plus two one-line changes in reader/writer `start()`.
