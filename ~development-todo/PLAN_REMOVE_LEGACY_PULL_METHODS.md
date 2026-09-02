# Plan — Remove the legacy advanced pull behaviors from v3

**Status: planned, not started.**

Remove the v2 pull-wrapping convenience layer from `JetStreamPullSubscription` and the types that exist only to support it. The simplification API (`ConsumerContext`) has parallel and better implementations of all three behaviors, so v3 carries two ways to do the same thing, one of them worse.

The behavior is not being thrown away — it is being handed to a future **V2 JetStream Facade**. See `PLAN_V2_FACADE.md`, whose "legacy advanced pull behaviors" section holds the v2 originals to lift from and the semantics worth preserving. **No copy needs to be made here**; the originals live in `nats.java` and stay there.

## What comes out

| Removed | Location |
|---|---|
| `fetch(int batchSize, long maxWaitMillis)` | `jetstream/src/main/java/io/synadia/client/impl/JetStreamPullSubscription.java:118` |
| `iterate(int batchSize, long maxWaitMillis)` (+ private `_iterate`) | same file, `:217` / `:222` |
| `reader(int batchSize, int repullAt)` | same file, `:349` |
| `JetStreamReaderImpl` (nested class) | same file, `:290` |
| `JetStreamReader` (interface) | `jetstream/src/main/java/io/synadia/client/impl/JetStreamReader.java` |

## What stays

The primitive pull API on the same class: `pull(int)`, `pull(PullRequestOptions)`, `pullNoWait(int)`, `pullNoWait(int, long)`, `pullExpiresIn(int, long)`. Those are the raw protocol operation, not a behavior wrapper — the removed methods were built on top of them.

## Collateral — verified, not guessed

**Becomes orphaned, delete with the rest:**

- `drainAlreadyBuffered(int)` — `JetStreamPullSubscription.java:173`, called only by `fetch` (`:120`) and `_iterate` (`:223`)
- `JetStreamSubscription.MIN_EXPIRE_MILLIS` — `:28`, referenced only by `fetch` at `:130` (and by its own javadoc at `:25`)

**Looks orphaned but is not — leave alone:**

- `durationGtZeroRequired(long, String)` (`:198`) is also called by `pullNoWait` (`:81`) and `pullExpiresIn` (`:102`)
- `JetStreamSubscription.EXPIRE_ADJUSTMENT` (`:22`) is also used by `NatsNextConsumer:20`
- `_nextUnmanaged` / `_nextUnmanagedNoWait` are the shared read path; `NatsFetchMessageConsumer` uses both

## Javadoc that must be reworded

Five javadoc lines on the **staying** pull methods point at the methods being removed — `"! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader."` at `JetStreamPullSubscription.java:33, 43, 62, 74, 95`. They need to point at `ConsumerContext` instead, or the primitive API is left recommending three methods that no longer exist.

`JetStreamReader`'s own javadoc already says it is superseded by `ConsumerContext`, which is the argument for this removal in the first place.

## The replacement mapping

For the migration guide and for anyone reading a removal diff:

| Removed | v3 replacement |
|---|---|
| `sub.fetch(batch, maxWait)` | `consumerContext.fetch(FetchConsumeOptions)` / `fetchMessages(int)` / `fetchBytes(int)` → `FetchMessageConsumer` |
| `sub.iterate(batch, maxWait)` | `consumerContext.iterate()` / `iterate(ConsumeOptions)` → `IterableMessageConsumer` |
| `sub.reader(batch, repullAt)` | `consumerContext.iterate(...)` for an endless pull-backed iterator, or `consume(...)` for a handler |

`reader` is the weakest of the three and the clearest case: it is a hand-rolled endless pull that re-pulls a fixed `batchSize` once the caller has read `repullAt` messages of the current batch, with no real flow control and no expiry handling. `IterableMessageConsumer` does that job properly.

## Test impact

- `jetstream/src/test/java/io/synadia/client/impl/JetStreamPullTests.java` — 9 tests, 3 of them touch the removed API: `testFetch` and `testIterate` go entirely, and `testPullExpires` uses `sub.fetch` (`:389`) and `sub.iterate` (`:401`) to observe expiry and has to be rewritten on the primitive `pull*` methods, which is what it is actually testing.
- `tdb/io/synadia/client/impl/OldJetStreamPullTests.java` — `testReader` (`:435`, helper `getReaderThread` at `:455`) is the only `reader` coverage and is still unported. It should be dropped from the port list rather than ported, and it is the natural seed for the facade's test suite.

## Other docs to update

- `~development-todo/INTERFACES_REPORT.md` — `JetStreamReader` has a row at `:38` and a section at `:216`; both go stale.
- `MIGRATION_GUIDE.md` — needs a JetStream section entry for the removal, with the mapping table above and a pointer at the facade as the eventual fallback.

## Steps

1. Delete `JetStreamReader.java` and the five members listed under **What comes out**.
2. Delete the two orphans; leave the three near-misses alone.
3. Reword the five primitive-API javadoc lines.
4. Cut `testFetch` / `testIterate`; rewrite `testPullExpires` against `pullExpiresIn`.
5. Drop `OldJetStreamPullTests.testReader` from the `tdb/` port list, noting where it went.
6. Update `INTERFACES_REPORT.md` and `MIGRATION_GUIDE.md`.
7. Record the removal in `PLAN_V2_FACADE.md`'s collector section (already written there).
