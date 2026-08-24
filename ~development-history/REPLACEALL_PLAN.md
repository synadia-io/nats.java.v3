# `replaceAll` Adoption Plan — Creator Classes

Audit of every `*Creator.java` under `jetstream/src/main/java/io/synadia/client/api/` for places that
manage internal collections, and a plan for migrating them to `JetStreamApiUtils.replaceAll`.

## Reference: current `replaceAll` overloads

In `JetStreamApiUtils`:

1. `<T> void replaceAll(List<T> target, @Nullable Collection<T> source)`
2. `<T> void replaceAll(List<T> target, @Nullable T @Nullable[] source)`
3. `<T,R> void replaceAll(List<R> target, @Nullable Collection<T> source, Function<@NonNull T,R> converter)`
4. `<T,R> void replaceAll(List<R> target, @Nullable T @Nullable[] source, Function<@NonNull T,R> converter)`
5. `<K,V> void replaceAll(Map<K,V> target, @Nullable Map<K,V> source)`
6. `void replaceAllStrings(List<String> target, @Nullable Collection<String> source)`
7. `void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source)`
8. `void replaceAllStrings(List<String> target, @Nullable Collection<String> source, Function<String,String> validator)`
9. `void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source, Function<String,String> validator)`

Semantics for list/array variants (#1-#4): `target.clear()` → if source is non-null, for each
non-null item, add to target if not already present (`!target.contains(...)`).

Semantics for Map variant (#5): `target.clear()` → if source is non-null and non-empty,
`target.putAll(source)`.

Semantics for String variants (#6-#9): `target.clear()` → if source is non-null, for each
non-null and non-empty string, optionally apply `validator` (which may throw
`IllegalArgumentException`), then add to target if not already present.

## Per-class inventory

### `StreamCreator.java` ✅ done
- `subjects(String...)`, `subjects(Collection<String>)` — uses `replaceAllStrings` with `validateSubjectTermStrict` validator ✅
- `sourceCreators(...)` (varargs + collection) — uses `replaceAll` ✅
- `sources(...)` (varargs + collection) — uses `replaceAll` with `SourceCreator::new` converter ✅
- `metadata(@Nullable Map<String,String>)` — uses `replaceAll` Map overload ✅

### `ConsumerCreator.java` 🟡 4 collections
Collection fields: `filterSubjects` (List<String>), `backoff` (List<Duration>), `metadata` (Map<String,String>), `priorityGroups` (List<String>).

| Method | Lines | Current pattern | Fit |
|---|---|---|---|
| `filterSubjects(String...)` | 433 | uses `replaceAllStrings` with `validateSubjectTermStrict` validator ✅ | **Done** |
| `filterSubjects(List<String>)` | 445 | same ✅ | **Done** |
| `metadata(@Nullable Map<String,String>)` | 569 | clear + `putAll` if non-null and non-empty | **Need new `replaceAll` Map overload** |
| `_backoff(Duration...)` | 708 | clear + loop + **throws** on `d.toNanos() < 0` | **Done** — leaving as-is; validation/throw behavior is unique and doesn't fit `replaceAll`. |
| `_backoff(long...)` | 720 | clear + loop + throws + converts long→Duration | **Done** — same reason. |
| `_priorityGroups(String...)` | 718 | uses `replaceAllStrings` (no validator) ✅ | **Done** — also adds dedup behavior (drops duplicate group names silently). |
| `_priorityGroups(List<String>)` | 722 | same ✅ | **Done** |

### `PlacementCreator.java` ✅ done
- Field `tags` changed from `@Nullable List<String>` to non-null `final List<String>`.
- `hasData()` now uses `!tags.isEmpty()`.
- `getTags()` returns non-null (may be empty).
- `tags(String...)` and `tags(@Nullable List<String>)` use `replaceAllStrings` (no validator — matches the original `nullOrEmpty` per-tag filter).
- Two-arg constructor `PlacementCreator(@Nullable String, @Nullable List<String>)` also routes through `replaceAllStrings`.

### `PullConsumerCreator.java` ⚪ pass-through
- `priorityGroups(String...)` (136), `priorityGroups(List<String>)` (147) — both delegate to `ConsumerCreator._priorityGroups`. Migrates implicitly when `ConsumerCreator` migrates.

### `PullOrderedConsumerCreator.java` ⚪ pass-through
- `priorityGroups(String...)` (118), `priorityGroups(List<String>)` (129) — same as above.

### `AbstractEphemeralConsumerCreator.java` ⚪ pass-through
- `backoff(Duration...)` (172), `backoff(long...)` (183) — both delegate to `ConsumerCreator._backoff`. Migrates implicitly.

### `StreamSourceCreator.java` 🟡 1 collection — semantics change
(Parent of `MirrorCreator`, `SourceCreator`.)
Field: `subjectTransforms` (List<SubjectTransformCreator>).

| Method | Lines | Current pattern | Fit |
|---|---|---|---|
| `subjectTransforms(SubjectTransformCreator...)` | 125 | delegates to List overload | — |
| `subjectTransforms(List<SubjectTransformCreator>)` | 134 | clear + `addAll` if non-null/non-empty — **no null-element filtering, no dedup** | **Functional fit** — migrating to `replaceAll` would *add* null filtering and `!contains` dedup. Behavior change worth confirming. |

### Other Creators ⚪ no collections
- `ConsumerLimitsCreator`, `ExternalCreator`, `MirrorCreator`, `RepublishCreator`, `SourceCreator`, `SubjectTransformCreator`, `AbstractOrderedConsumerCreator`, `PushConsumerCreator`, `PushOrderedConsumerCreator` — no collection fields.

## Plan

### Phase 1 — drop-in migrations (no semantic change)
Nothing in this bucket. Even the `StreamSourceCreator.subjectTransforms` case changes behavior
(adds null/dedup filtering).

### Phase 2 — String-specific migration (solved via `replaceAllStrings`)
The String-specific `replaceAllStrings` overloads (#6-#9 above) skip null and empty strings; the
validator overloads also pass each kept string through a `Function<String,String>` that may
normalize or throw. Migrations:
- `StreamCreator.subjects(...)` (both overloads) — uses `replaceAllStrings` with `validateSubjectTermStrict` ✅
- `PlacementCreator.tags(...)` (both overloads + 2-arg constructor) — uses `replaceAllStrings` (no validator) ✅
- `ConsumerCreator.filterSubjects(String...)` and `filterSubjects(List<String>)` — uses `replaceAllStrings` with `validateSubjectTermStrict` ✅
- `ConsumerCreator._priorityGroups(String...)` and `_priorityGroups(List<String>)` — uses `replaceAllStrings` (no validator) ✅

### Phase 3 — migrations requiring new overload shapes

1. **Map support** for `metadata`. Added ✅:
   ```java
   <K,V> void replaceAll(Map<K,V> target, @Nullable Map<K,V> source)
   ```
   `StreamCreator.metadata(Map)` migrated ✅. `ConsumerCreator.metadata(Map)` still pending.

2. **`StreamSourceCreator.subjectTransforms(List)`** — confirm with the team whether adding
   null-skip + dedup is desired. If yes, migrate to overload #1 of `replaceAll`. (Varargs form
   delegates, so changes automatically.)[EQUALS_HASHCODE_PLAN.md](EQUALS_HASHCODE_PLAN.md)

### Phase 4 — leave alone (poor fit)

1. **`ConsumerCreator._backoff(Duration...)` and `_backoff(long...)`** ✅ — leaving as-is. Current
   code throws `IllegalArgumentException` on negative values; `replaceAll` has no error path, and
   the validation/throw behavior is unique to this method (no other Creator collection needs
   numeric range validation).

2. ~~**`PlacementCreator.tags(List)`** — field-shape concern resolved by changing `tags` to non-null `final List<String>` and migrating to `replaceAllStrings`. ✅~~

## Summary

| File | Action |
|---|---|
| `StreamCreator` | Fully migrated ✅ (subjects via `replaceAllStrings` + validator, sourceCreators, sources, metadata). |
| `PlacementCreator` | Fully migrated ✅ (field shape changed; tags via `replaceAllStrings`). |
| `ConsumerCreator` | `filterSubjects`, `_priorityGroups`, `_backoff` (intentionally left as-is) all resolved ✅. `metadata` still pending (Map overload exists; trivial to migrate). |
| `PlacementCreator` | Decide on field-shape change first; otherwise leave. |
| `StreamSourceCreator` | Migrate `subjectTransforms` only if added null-skip + dedup behavior is desired. |
| `PullConsumerCreator`, `PullOrderedConsumerCreator`, `AbstractEphemeralConsumerCreator` | No work — they delegate; updates flow through `ConsumerCreator`. |
| Other Creators | No collections — nothing to do. |

## Suggested additions to `JetStreamApiUtils`

```java
// Map variant (added ✅)
public static <K,V> void replaceAll(Map<K,V> target, @Nullable Map<K,V> source);

// String-specific variants (added ✅)
public static void replaceAllStrings(List<String> target, @Nullable Collection<String> source);
public static void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source);
public static void replaceAllStrings(List<String> target, @Nullable Collection<String> source, Function<String,String> validator);
public static void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source, Function<String,String> validator);
```
