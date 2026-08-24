# Plan: decouple `StatisticsCollector` from `Statistics` (write model vs read model)

## Problem

`StatisticsCollector extends Statistics`. The collector is the **write** model (`incrementPingCount`, `registerRead`, …); `Statistics` is the **read** model (`getPings`, …). Making the collector a subtype of the read view is backwards (a gatherer is not a kind of readout), and to make that inheritance tolerable `StatisticsCollector` re-declares all 16 getters as `default { return 0; }`. That block exists only to pay for the `extends`, and it creates a silent-all-zeros trap: a collector that implements only the write side reports zero for every metric with no compile-time signal. `DebugStatsCollector` is a live example — it implements the write side + a couple getters and inherits `return 0` for the other 13.

Keep the read/write **split** (we do NOT want `getStatistics()` to hand users a type with mutators). Drop only the `extends`.

## The key question this plan answers: given a collector, how do we get the read side?

Put the bridge **on the collector**, not in the connection via `instanceof`. It is an **abstract, `@NonNull`** method — deliberately NOT defaulted, and the library does NOT provide a canned empty `Statistics`. Every collector must build and return its own read view (backward compatibility is a non-goal here):

```java
public interface StatisticsCollector {          // no longer extends Statistics
    ... increment*/register*/decrement*/setAdvancedTracking (unchanged) ...

    /**
     * The read-only {@link Statistics} view of the metrics this collector has gathered.
     * A collector that also tracks the read side returns {@code this} (implementing {@link Statistics});
     * otherwise it returns its own {@link Statistics} instance.
     * @return the statistics view, never null
     */
    @NonNull Statistics getStatistics();
}
```

- No interface default, and **no `Statistics.EMPTY`** — providing a shared empty read view is not the library's responsibility. Each implementer supplies a real `Statistics`.
- `@NonNull`: the writer must return something; null is not a valid answer.
- The full collector (`NatsStatistics`) implements both interfaces and returns `this`.
- The connection reads via the bridge: `nc.getStatistics()` → `collector.getStatistics()`. No `instanceof`, no cast.

(Name chosen for symmetry with `Connection.getStatistics()`. If snapshot semantics are wanted later, this method is the natural place to return an immutable copy instead of `this` — out of scope here; current behavior stays "live".)

## File-by-file changes

### `core/src/main/java/io/synadia/client/Statistics.java`
No change. (No `EMPTY` constant — the library does not provide a canned read view.)

### `core/src/main/java/io/synadia/client/StatisticsCollector.java`
- Change `public interface StatisticsCollector extends Statistics` → `public interface StatisticsCollector`.
- Delete the entire "Statistics defaults" block (16 `default long getX() { return 0; }`).
- Add the **abstract `@NonNull`** `Statistics getStatistics();` bridge (above) — no default body. Import `org.jspecify.annotations.NonNull`.

### `core/src/main/java/io/synadia/client/impl/NatsStatistics.java`
- `class NatsStatistics implements StatisticsCollector` → `implements StatisticsCollector, Statistics` (it already implements all 16 getters, so it is a full `Statistics`).
- Add `@Override public Statistics getStatistics() { return this; }`.
- **Add `/** {@inheritDoc} *&#47;` to the 16 `Statistics` getters and to `getStatistics()`** — this is where the read-side API doc now lives (it used to hang off the `StatisticsCollector` default getters we're deleting; `NatsStatistics` becomes the concrete `Statistics` that inherits the `Statistics` interface doc).
- `toString()` unchanged (so `nc.getStatistics().toString()` still yields the summary — see `NatsStatisticsTests:31`).

### `core/src/main/java/io/synadia/client/impl/NatsConnection.java`
`getStatistics()` (`:2154`) → `return this.statistics.getStatistics();`. Field stays `StatisticsCollector statistics`; writes still go straight to it. (Confirmed: no internal code reads metrics via the collector field — reads only flow through `getStatistics()`.)

### `core/src/main/java/io/synadia/client/utils/DebugStatsCollector.java`
Must now supply a `@NonNull Statistics getStatistics()`. Make it `implements StatisticsCollector, Statistics` and `return this`, implementing all 16 `Statistics` getters — real values for what it tracks (`getOutMsgs`/`getOutBytes`), `0` for the rest. (This keeps its `getOutMsgs`/`getOutBytes` as genuine `@Override`s of `Statistics`.)

### `core/src/test/java/io/synadia/client/impl/CoverageStatisticsCollector.java`
Must now supply a `getStatistics()` too — it has to **build its own `Statistics`** (no `EMPTY` to lean on). Simplest: `implements StatisticsCollector, Statistics`, `return this`, implement the 16 getters (real values for the couple it tracks, `0` otherwise). This is the intended consequence of removing the freebie — a collector, even a test one, must state a full read view.

## Behavior / migration notes

Backward compatibility is a **non-goal** — this is a deliberate breaking change on the public `StatisticsCollector` contract (fine while V3 is unreleased). Every implementer of `StatisticsCollector` must now supply `getStatistics()`; there is no default.

- **Default path unchanged at runtime:** a real connection uses `NatsStatistics`; `getStatistics()` returns it (live), every read works exactly as today.
- **Every existing collector must build a `getStatistics()`:** `NatsStatistics`, `DebugStatsCollector`, and `CoverageStatisticsCollector` each `implements Statistics` + `return this`. There is no shared empty instance to fall back on. Any external/user collector will now fail to compile until it supplies (and builds) its own read view — intentional; that's the point.
- **Read via the connection:** `nc.getStatistics()` reflects whatever the collector returns from its `@NonNull` bridge.

## Alternatives considered

- **Provide a shared `Statistics.EMPTY`** (constant, or an interface default returning it) — lower friction, but it's not the library's job to hand implementers a read view, and it lets a collector ship an all-zeros view without thinking. **Rejected** by decision: no `EMPTY`, the method is abstract and `@NonNull`, each collector builds its own.
- **`instanceof` in the connection** (`(collector instanceof Statistics) ? (Statistics) collector : EMPTY`) — implicit and undiscoverable; the collector can't express its own read view. Rejected.
- **Merge into one interface** — would expose mutators on the user-facing read type. Rejected; the split is the good part.
- **Snapshot semantics** (`getStatistics()` returns an immutable copy) — arguably more correct than handing out the live mutating object, but a behavior change; leave the door open, out of scope here.

## Risk

Low, aside from the intentional source break. Deletes ~16 lines of zero-default ceremony, adds one abstract method + its implementations, touches no internal read path, runtime behavior identical for the default `NatsStatistics`.
