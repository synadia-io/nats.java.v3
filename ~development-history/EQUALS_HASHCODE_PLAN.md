# `equals` / `hashCode` Audit and Plan — `jetstream` api package

## Scope

Files under `jetstream/src/main/java/io/synadia/client/api/`. Goal: figure out which classes
need `equals` / `hashCode`, in what style, and document `@NullMarked` status (so we add
`@Nullable Object` on the parameter where required).

## Part 1 — JSON library improvements ✅ done

`AbstractIndexedJsonValue` now has value-based `equals` / `hashCode` (`instanceof`-based,
symmetric across `LazyJsonValue` and `IndexedJsonValue`). Tests in
`jnats-json/src/test/java/io/nats/json/IndexedJsonValueEqualsTests.java` cover string
(including escapes), bool, null, integer/long/double, type-mismatch, map (ordered and
unordered), array, empty containers, reflexivity, null parameter, non-`AbstractIndexedJsonValue`
comparison, and cross-subclass hashCode consistency.



You own `c:\nats\jnats-json` (path here: `/mnt/c/nats/jnats-json`). Three files matter:

- `AbstractIndexedJsonValue<SELF>` — common base for lazy values
- `IndexedJsonValue` — extends the base; eager tree, lazy leaves
- `LazyJsonValue` — extends the base; lazy tree, lazy leaves

### Current state
- None of the three has `equals` / `hashCode`. Comparison is reference-only.
- The eager counterpart `JsonValue` already has a working value-based `equals`/`hashCode`
  (switches on `type` and compares the corresponding materialized field). That's the template.

### Why this matters for the api package
~28 server-response classes in `api/` are thin wrappers around a single `LazyJsonValue ljv`
field (e.g. `External`, `SubjectTransform`, `StreamSource`, `Placement`, `ConsumerConfiguration`,
`ConsumerInfo`, etc.). With value-based equality on `LazyJsonValue`, each wrapper's
`equals`/`hashCode` collapses to a one-liner:
```java
return Objects.equals(this.ljv, that.ljv);
```
Without it, every wrapper has to call each getter to materialize values and compare them
individually — which is what `External`, `SubjectTransform`, and `StreamSource` currently do
by hand (lots of boilerplate, easy to miss a field when new ones are added).

### Recommended jnats-json change

Add value-based `equals` / `hashCode` to `AbstractIndexedJsonValue`. It already knows the
type and exposes all accessors. The implementation mirrors `JsonValue.equals/hashCode`:

```java
@Override
public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractIndexedJsonValue<?> that)) return false;
    if (type != that.type) return false;

    switch (type) {
        case STRING:      return Objects.equals(getString(), that.getString());
        case BOOL:        return Objects.equals(getBoolean(), that.getBoolean());
        case INTEGER:
        case LONG:
        case DOUBLE:
        case FLOAT:
        case BIG_DECIMAL:
        case BIG_INTEGER: return Objects.equals(getNumber(), that.getNumber());
        case MAP:         return Objects.equals(getMap(), that.getMap());
        case ARRAY:       return Objects.equals(getArray(), that.getArray());
        case NULL:        return true;
    }
    return false;
}

@Override
public int hashCode() {
    int hc;
    switch (type) {
        case STRING:      hc = Objects.hashCode(getString()); break;
        case BOOL:        hc = Objects.hashCode(getBoolean()); break;
        case INTEGER:
        case LONG:
        case DOUBLE:
        case FLOAT:
        case BIG_DECIMAL:
        case BIG_INTEGER: hc = Objects.hashCode(getNumber()); break;
        case MAP:         hc = Objects.hashCode(getMap()); break;
        case ARRAY:       hc = Objects.hashCode(getArray()); break;
        default:          hc = 0;
    }
    return 31 * hc + type.hashCode();
}
```

### Design decisions to confirm

1. **Symmetry across subclasses.** Using `instanceof AbstractIndexedJsonValue<?>` means
   `LazyJsonValue.equals(indexedJsonValue)` can return true when they hold the same value.
   The alternative is `getClass() == that.getClass()`, which makes them inequivalent. Recommend
   `instanceof` for the abstract base. (If you want strict type equality, leave abstract base
   without `equals` and add it per-subclass.)
2. **Materialization side effects.** Calling `equals` on an unresolved `LazyJsonValue` will
   trigger map/array resolution. Acceptable cost — value equality is inherently a deep operation.
   Worth a Javadoc note that the call may resolve the value.
3. **`@Nullable` on `equals` param.** The abstract class isn't `@NullMarked` today. If you add
   `@NullMarked` later (or just want to be explicit), the parameter needs `@Nullable Object`.
4. **`IndexedJsonValue` vs `LazyJsonValue`.** No subclass overrides needed — both can fully
   share the base implementation.

### Effect on api package
- Replace ~28 wrapper-class hand-written `equals`/`hashCode` blocks with `Objects.equals(ljv, ...)`.
- Existing wrappers (`External`, `SubjectTransform`, `StreamSource`) can be simplified.
- Future-proof: adding a new field to a wrapper no longer requires touching `equals`/`hashCode`.

## Part 2 — Style preferences (IntelliJ-generated)

### Creator style (no `LazyJsonValue`)
See `StreamSourceCreator`, `SubjectTransformCreator`. Pattern matches IntelliJ defaults with
modern `instanceof` pattern variable:

```java
@Override
public final boolean equals(@Nullable Object o) {
    if (!(o instanceof MyCreator that)) return false;
    return primitiveField == that.primitiveField
        && Objects.equals(refField1, that.refField1)
        && Objects.equals(refField2, that.refField2)
        && collectionField.equals(that.collectionField);
}

@Override
public int hashCode() {
    int result = Objects.hashCode(refField1);
    result = 31 * result + Long.hashCode(primitiveLongField);
    result = 31 * result + Objects.hashCode(refField2);
    result = 31 * result + collectionField.hashCode();
    return result;
}
```

Conventions:
- `equals` is `final` (Creator hierarchy isn't designed for further override).
- No `this == o` short-circuit (IntelliJ omits it with the pattern variable form).
- Pattern variable `that` (Java 16+).
- `Objects.equals` for nullable fields; `field.equals` for non-null fields.
- `==` for primitives; `Long.hashCode(...)` / `Integer.hashCode(...)` for longs/ints in `hashCode`.

### LazyJsonValue-backed style (incoming server data)
See `External`, `SubjectTransform`, `StreamSource`. Pattern:

```java
@Override
public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Thing that = (Thing) o;
    return Objects.equals(getField1(), that.getField1())
        && Objects.equals(getField2(), that.getField2());
}

@Override
public int hashCode() {
    int result = Objects.hashCode(getField1());
    result = 31 * result + Objects.hashCode(getField2());
    return result;
}
```

Conventions:
- Uses getters (so values get materialized).
- Includes `this == o` short-circuit and `getClass()` check (older IntelliJ style; matches
  what's already in `External`).
- If Part 1 lands, the entire body becomes `return Objects.equals(ljv, that.ljv);`.

### `@Nullable` on the `equals` parameter
Every class flagged `@NullMarked` below should declare `equals(@Nullable Object o)`.

> **Defect found:** `StreamSourceCreator` is `@NullMarked` but currently declares
> `public final boolean equals(Object o)` — should be `(@Nullable Object o)`. Fix
> when you do the broader sweep. (`SubjectTransformCreator` and `External` already
> have it correct.)

## Part 3 — Audit by file

Legend:
- **kind**: class / enum / interface
- **NM**: `@NullMarked` (Y/N)
- **eq/hc**: existing `equals` / `hashCode` (Y/N)
- **action**: what's needed

### 3a. Enums — nothing to do (Java handles equals/hashCode for enums)

| File | NM | Notes |
|---|---|---|
| `AckPolicy` | Y | — |
| `CompressionOption` | Y | — |
| `DeliverPolicy` | Y | — |
| `DiscardPolicy` | Y | — |
| `PersistMode` | Y | — |
| `PriorityPolicy` | Y | — |
| `ReplayPolicy` | Y | — |
| `RetentionPolicy` | Y | — |
| `StorageType` | Y | — |

### 3b. Interfaces — nothing to do

| File | NM | Notes |
|---|---|---|
| `PushDeliverSubjectInterface` | N | Marker / interface only |
| `Watcher` | Y | Interface |

### 3c. Already implemented — simplified ✅

| File | NM | eq/hc | Notes |
|---|---|---|---|
| `External` | Y | Y | Simplified to `Objects.equals(ljv, that.ljv)` ✅ |
| `StreamSource` | Y | Y | Same ✅ |
| `SubjectTransform` | Y | Y | Same ✅ (removed redundant getter calls) |
| `StreamSourceCreator` | Y | Y | Creator style. `@Nullable` on `equals` param added ✅ |
| `SubjectTransformCreator` | Y | Y | Creator style. `@Nullable` on `equals` param added ✅ (also added the missing `Nullable` import) |

### 3d. Creators ✅ done

All are `@NullMarked`. Use the IntelliJ Creator pattern (instanceof pattern variable, `final`
equals for closed classes, direct `.equals()` on non-null fields, `Objects.equals` on
`@Nullable` fields, primitive wrappers for primitives in hashCode).

| File | Status | Notes |
|---|---|---|
| `StreamCreator` | ✅ | 39 fields. `equals` is `final`. |
| `PlacementCreator` | ✅ | 2 fields (`cluster` nullable, `tags` non-null list). |
| `RepublishCreator` | ✅ | 3 fields (`source`, `destination` non-null, `headersOnly` boolean). |
| `ConsumerLimitsCreator` | ✅ | 2 fields (`inactiveThreshold` nullable, `maxAckPending` long). |
| `ExternalCreator` | ✅ | 2 fields (`api`, `deliver` both nullable). |
| `ConsumerCreator` | ✅ | 34 fields. Non-final equals; uses `getClass() != o.getClass()` for type strictness so symmetry holds across the hierarchy. |
| `AbstractEphemeralConsumerCreator` | ✅ | No fields added — inherits from `ConsumerCreator`. |
| `AbstractOrderedConsumerCreator` | ✅ | Adds `namePrefix`. Overrides `equals` calling `super.equals(o)`, then checks `namePrefix`. `hashCode` is `31 * super.hashCode() + Objects.hashCode(namePrefix)`. |
| `PullConsumerCreator` | ✅ | No fields added — inherits from `AbstractEphemeralConsumerCreator`. |
| `PushConsumerCreator` | ✅ | No fields added — inherits from `AbstractEphemeralConsumerCreator`. |
| `PullOrderedConsumerCreator` | ✅ | No fields added — inherits from `AbstractOrderedConsumerCreator`. |
| `PushOrderedConsumerCreator` | ✅ | No fields added — inherits from `AbstractOrderedConsumerCreator`. |
| `MirrorCreator` | ✅ | Extends `StreamSourceCreator` which has `final equals`. Inherited. |
| `SourceCreator` | ✅ | Same — extends `StreamSourceCreator`. Inherited. |

**Hierarchy decision (decided)**: For the consumer creators we used **option 2** — non-final
`equals` on `ConsumerCreator` with a `getClass()` check, and `AbstractOrderedConsumerCreator`
overrides to chain `super.equals(o)` plus its `namePrefix` field. This keeps symmetry
(`PullConsumerCreator` ≠ `PushConsumerCreator` even though their base fields are identical) and
makes adding new subclass fields straightforward.

### 3e. LazyJsonValue-backed wrappers ✅ done

All wrap a `LazyJsonValue ljv` (directly or via an abstract base). Implementation uses the
one-liner pattern `Objects.equals(ljv, that.ljv)` / `Objects.hashCode(ljv)`, relying on the
value-based equality added to `AbstractIndexedJsonValue` in Part 1.

| File | How |
|---|---|
| `ApiResponse` (base) | Direct ✅ — covers all `ApiResponse<T>` subclasses below by inheritance. |
| `AccountStatistics` | Inherits from `ApiResponse` ✅ |
| `ConsumerInfo` | Inherits from `ApiResponse` ✅ |
| `ConsumerPauseResponse` | Inherits from `ApiResponse` ✅ |
| `PublishAck` | Inherits from `ApiResponse` ✅ |
| `PurgeResponse` | Inherits from `ApiResponse` ✅ |
| `SuccessApiResponse` | Inherits from `ApiResponse` ✅ |
| `PeerInfo` (base) | Direct ✅ — covers `Replica` by inheritance. |
| `Replica` | Inherits from `PeerInfo` ✅ |
| `StreamSourceInfo` (base) | Direct ✅ — covers `MirrorInfo` and `SourceInfo` by inheritance. |
| `MirrorInfo` | Inherits from `StreamSourceInfo` ✅ |
| `SourceInfo` | Inherits from `StreamSourceInfo` ✅ |
| `Mirror` | Inherits from `StreamSource` ✅ |
| `Source` | Inherits from `StreamSource` ✅ |
| `AccountLimits` | Direct ✅ |
| `AccountTier` | Direct ✅ |
| `ApiStats` | Direct ✅ |
| `ClusterInfo` | Direct ✅ |
| `ConsumerConfiguration` | Direct ✅ |
| `ConsumerLimits` | Direct ✅ |
| `LostStreamData` | Direct ✅ |
| `Placement` | Direct ✅ |
| `PriorityGroupState` | Direct ✅ |
| `Republish` | Direct ✅ |
| `SequenceInfo` | Direct ✅ |
| `StreamAlternate` | Direct ✅ |
| `StreamConfiguration` | Direct ✅ |
| `StreamState` | Direct ✅ |

Plain-POJO classes from the original §3e list (not LazyJsonValue-backed; got field-based equals):

| File | How |
|---|---|
| `Subject` | Field-based ✅ (`name`, `count`) |
| `Error` | Field-based ✅ (`code`, `apiErrorCode`, `description`) |
| `StreamInfo` | (Verify — see §3f.) |

### 3f. POJOs without LazyJsonValue ✅ done

All are `@NullMarked`. Creator-style equals/hashCode using IntelliJ pattern (`final`,
instanceof pattern variable). Missing public Javadoc was also filled in during this pass.

| File | Fields | Javadoc |
|---|---|---|
| `ConsumerPauseRequest` | ✅ 1 field (`pauseUntil`) | already complete |
| `MessageDeleteRequest` | ✅ 2 fields (`sequence`, `erase`) | 6 new Javadoc blocks added (constructor, getters, Builder class) |
| `MessageGetRequest` | ✅ 4 fields (`sequence` + 3 nullable refs) | 13 new Javadoc blocks; also added `@Nullable` to 3 field declarations + protected constructor parameters to match getter contracts |
| `StreamInfoOptions` | ✅ 2 fields | already complete |
| `SubscribeBehavior` | ✅ 5 fields | 2 new Javadoc blocks |

**Note** on `SubscribeBehavior`: includes `dispatcher` and `handler` (callback references) in
identity. Comparison falls back to `Object.equals` (reference identity) for those. If callers
expect "behavior" to be config-only, those two could be excluded.
| `SubscribeBehavior` | Options bag. |
| `StreamInfo` | If contains plain fields (not just LazyJsonValue) — verify; may belong in 3e. |

## Recommended order of execution

1. **Add `equals`/`hashCode` to `AbstractIndexedJsonValue` in jnats-json** (Part 1).
   - Single PR. Add tests covering string/number/bool/map/array/null equality and cross-subtype
     equality (Lazy vs Indexed with same content).
   - Cut a release.
2. **Bump jnats-json dependency in nats.java.v3.**
3. **Simplify the 3 already-implemented wrappers** (`External`, `SubjectTransform`, `StreamSource`)
   to the one-liner `Objects.equals(ljv, ...)` form. Quick smoke test.
4. **Add equals/hashCode to the remaining LazyJsonValue-backed wrappers** (§3e) using the
   one-liner pattern. Mechanical, low risk.
5. **Add the missing `@Nullable Object` parameter** to `StreamSourceCreator` and
   `SubjectTransformCreator`. Two tiny edits.
6. **Add equals/hashCode to the Creators and POJOs** (§3d, §3f) using the Creator pattern.
   Decide the consumer-hierarchy strategy first (final-equals vs override-and-super.equals).

## Quick-reference: count of work items (all ✅ done)

| Bucket | Count | Status |
|---|---|---|
| Enums / interfaces | 11 | nothing to do |
| §3c — simplified / `@Nullable` fix | 5 | ✅ |
| §3d — Creator-style additions | 14 | ✅ |
| §3e — LazyJsonValue wrappers | ~28 | ✅ (via base classes + `LazyApiObject`) |
| §3f — Plain POJOs | 5 | ✅ |

## Part 4 — `LazyApiObject` base class ✅ done

A new abstract base `io.synadia.client.api.LazyApiObject` (in
`jetstream/src/main/java/io/synadia/client/api/LazyApiObject.java`) consolidates the
common shape of every "LazyJsonValue wrapper" class:

```java
@NullMarked
public abstract class LazyApiObject implements JsonSerializable {
    protected final LazyJsonValue ljv;

    protected LazyApiObject(LazyJsonValue ljv) {
        this.ljv = ljv;
    }

    @Override public String toJson() { return ljv.toJson(); }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return ljv.equals(((LazyApiObject) o).ljv);
    }

    @Override
    public int hashCode() { return ljv.hashCode(); }
}
```

### What it covers
- `protected final LazyJsonValue ljv` field shared by every subclass.
- A default `toJson()` returning `ljv.toJson()`.
- Value-based `equals` and `hashCode` (delegating to `LazyJsonValue` equality, which is now
  value-based after Part 1).

### Side effect: all subclasses become `JsonSerializable`
Classes that were not previously `JsonSerializable` (`AccountLimits`, `AccountTier`, `ApiStats`,
`ClusterInfo`, `LostStreamData`, `PriorityGroupState`, `SequenceInfo`, `StreamAlternate`,
`StreamState`) now are. Their wrapped `ljv` is already a valid JSON representation, so this is
just exposing what they already did internally. **Decision accepted: yes, promote them.**

### Migrated subclasses (16 total) ✅

| File | Notes |
|---|---|
| `AccountLimits` | extends LazyApiObject |
| `AccountTier` | extends LazyApiObject; keeps `_limits` cache field |
| `ApiStats` | extends LazyApiObject |
| `ClusterInfo` | extends LazyApiObject; keeps `_replicas` cache field |
| `ConsumerConfiguration` | extends LazyApiObject; keeps `DEFAULT_INSTANCE` |
| `ConsumerLimits` | extends LazyApiObject; keeps `optionalInstance` factory |
| `External` | extends LazyApiObject; keeps `optionalInstance` factory |
| `LostStreamData` | extends LazyApiObject; keeps `optionalInstance` factory |
| `Placement` | extends LazyApiObject; keeps `optionalInstance` factory |
| `PriorityGroupState` | extends LazyApiObject; keeps `listOf` factory |
| `Republish` | extends LazyApiObject; keeps `optionalInstance` factory |
| `SequenceInfo` | extends LazyApiObject; keeps `EMPTY` static |
| `StreamAlternate` | extends LazyApiObject; keeps `listOf` factory |
| `StreamConfiguration` | extends LazyApiObject; keeps `EMPTY` static |
| `StreamState` | extends LazyApiObject; keeps `EMPTY` static, `_subjects` / `_subjectMap` caches |
| `SubjectTransform` | extends LazyApiObject; keeps `optionalInstance` and `listOf` factories |

Each subclass got smaller (typically -20 lines): the `ljv` field, the `equals` / `hashCode` /
`toJson` methods, and the now-unused `JsonSerializable` / `Objects` / `LazyJsonValue` /
`Nullable` imports (where applicable) were removed.

### Abstract bases folded into `LazyApiObject`

| Abstract base | Status | Notes |
|---|---|---|
| `PeerInfo` | ✅ done | Extends `LazyApiObject`. `type` field eliminated; `toString` now uses `getClass().getSimpleName()`. Subclass `Replica` updated. |
| `StreamSource` | ✅ done | Same treatment. Subclasses `Mirror` and `Source` updated. The `IllegalStateException` in `getStreamName()` now uses `getClass().getSimpleName()` for its message. |
| `StreamSourceInfo` | ✅ done | Folded in. Subclasses `MirrorInfo` and `SourceInfo` updated to `super(v)`. `toString` now uses `getClass().getSimpleName()`. The `IllegalStateException` in `getName()` likewise. |
| `ApiResponse<T>` | intentionally left as-is | Highest blast radius. Generic type parameter, has `type` and `error` extra fields, constructor paths that resolve to `LazyJsonValue.EMPTY_MAP`, public `getOriginalJsonValue()`. Decision: don't fold in. |

## Part 5 — Coverage tests ✅ done

`jetstream/src/test/java/io/synadia/client/api/ApiEqualityAndHashCodeCoverageTest.java`
contains 51 `@Test` methods that exercise the equals/hashCode contract for every API class
in the package. Uses [EqualsVerifier](https://jqno.nl/equalsverifier/) (`nl.jqno.equalsverifier:equalsverifier:4.2.2`)
wherever it works cleanly; falls back to manual assertion-based tests for the cases that
EqualsVerifier can't model (cross-subclass equality in the `StreamSourceCreator` hierarchy,
the `ConsumerCreator` hierarchy's `getClass()`+`super.equals()` chain, `ApiResponse` subclasses
that need a `Message`, and `StreamCreator`'s 39-field validating constructor).

Purpose:
- **Correctness** — verify the equals/hashCode contract (reflexive, symmetric, transitive,
  consistent, null-safe, foreign-object-safe) for every API class.
- **Coverage** — every equals/hashCode method is exercised, so regressions show up
  immediately in CI.

## Closed items

- **Cached `hashCode` on `AbstractIndexedJsonValue`** (reviewer's low-severity item from
  jnats-json PR #35): decided not to do. `equals` already triggers map/array resolution; if
  hash-based-collection performance ever becomes a measured issue, revisit then.
