# String Setter Audit — Creator Classes

Scope: every public `(String <name>)` / `(@Nullable String <name>)` chainable setter on `*Creator.java`
under `jetstream/src/main/java/io/synadia/client/api/` plus `KeyValueConfigurationCreator` and
`ObjectStoreConfigurationCreator`. Skipped: `String...` and `List<String>` setters; validator/util
classes themselves; constructors. Main sources only (no `/test/`, `build/`, `out/`, `tdb/`, `zclaude/`).

The reference pattern is `ConsumerCreator.filterSubject(String)`:
```java
this.filterSubjects.clear();
String fs = emptyAsNull(filterSubject);
if (fs != null) { this.filterSubjects.add(filterSubject); }
return (T)this;
```
Bucket key:
- **A** — Empty-safe via `emptyAsNull` (gold standard).
- **B** — Empty-safe via a validator that throws on empty.
- **C** — Empty-safe via an inline check.
- **D** — Unsafe: empty string passes through. The field will hold `""` afterwards.
- **E** — Unsafe sub-case of D: direct field assignment, no check whatsoever.
- **N/A** — empty has a deliberate, distinct meaning.

## JSON-serializer note (load-bearing for the report)

`io.nats.json.JsonWriteUtils.addField(StringBuilder, String, String)`
(`/mnt/c/nats/jnats-json/src/main/java/io/nats/json/JsonWriteUtils.java` line 165) gates with:
```java
if (value != null && !value.isEmpty()) { ... }
```
So the `String` overload of `addField` **already suppresses both null and empty**. The sibling
`addFieldAlways` does NOT, but **`addFieldAlways` is not called from any audited Creator's `toJson()`**
(verified by grep on the audited directories — zero hits).

Net effect: a D/E setter does NOT today emit `"foo":""` to the wire, but it **does** corrupt:
- `getX()` — returns `""` instead of `null` after `creator.foo("")`.
- `equals()` / `hashCode()` — `Objects.equals("", null)` is false, breaking round-trips between
  a default-constructed creator and one that explicitly received `""`.
- copy constructors — they propagate `""` instead of `null`.
- ToString/debug output — surfaces `""`.
- Any future call site that uses `addFieldAlways` (e.g. an `update` API) would suddenly emit `""`.

So the bug is real, just not visible in current JSON output. Treat D/E as latent bugs.

## Validator behavior (load-bearing)

Validators referenced by audited setters:
- `Validator.emptyAsNull(String)` → null/empty → null, otherwise passthrough.
- `JsValidator.validateConsumerName/validateDurable/validateStreamName` → all delegate to
  `validatePrintableExceptWildDotGtSlashes` → `Validator._validate`, which calls
  `emptyAsNull(s)` first and (when `required=false`) returns `null` on empty without throwing.
- `JsValidator.validateBucketName` → `validateIsRestrictedTerm` → same `_validate` wrapper.
- `JsValidator.validatePrefixOrDomain` → same `_validate` wrapper.

So when these validators are called with `required=false`, an empty input becomes `null` —
empty-safe (treated as **A**). With `required=true`, they throw on empty — that's **B**.

---

## Headline summary

| Bucket | Count |
|--------|------:|
| A — `emptyAsNull` (or validator with `required=false` equivalent) | 14 |
| B — Validator that throws on empty | 0 |
| C — Inline empty check | 0 |
| D — Unsafe passthrough (non-direct) | 0 |
| E — Unsafe direct assignment | 4 |
| N/A | 0 |
| **Total audited** | **18** |

Classes with no qualifying String setters (skipped from per-class tables): `ConsumerLimitsCreator`,
`SourceCreator`, `MirrorCreator`, `PullOrderedConsumerCreator`, `SubjectTransformCreator`,
`RepublishCreator` (constructor-only with `Validator.required`).

---

## Per-class breakdown

### `ConsumerCreator` (abstract base)

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `description(String)` | `this.description = emptyAsNull(description);` | A | `null` | No | yes |
| `filterSubject(String)` | (model pattern) `emptyAsNull` + clear+add | A | `null` (list empty) | No | n/a (list) |
| `sampleFrequency(String)` | `this.sampleFrequency = emptyAsNull(frequency);` | A | `null` | No | yes |

### `AbstractEphemeralConsumerCreator`

Delegates one String setter to `ConsumerCreator._name`, which does
`this.name = validateConsumerName(emptyAsNull(name), false);`.

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `name(@Nullable String)` | `_name(name)` → `validateConsumerName(emptyAsNull(name), false)` | A | `null` | No | yes |

### `AbstractOrderedConsumerCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `namePrefix(@Nullable String)` | `this.namePrefix = namePrefix; _name(generateConsumerName(namePrefix));` | **E** | `""` (raw store) | No (`namePrefix` not serialized; downstream `name` becomes `"-<random>"`) | yes |

Note on `namePrefix("")`: the field is stored as `""` (so `getNamePrefix()` returns `""` instead of
`null`). `generateConsumerName("")` returns `"-<random>"` (a degenerate name) because its check is
`prefix == null` only — empty-string slips past. This is a subtle real bug beyond just JSON.

### `PullConsumerCreator`

Inherits `description`, `filterSubject`, `sampleFrequency`, `name`. Adds:

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `durable(@Nullable String)` | `_durable(durable)` → `validateDurable(emptyAsNull(durable), false)` | A | `null` | No | yes |

### `PullOrderedConsumerCreator`

No String setters of its own — all inherit (all A).

### `PushConsumerCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `durable(@Nullable String)` | `_durable(durable)` → `validateDurable(emptyAsNull(...), false)` | A | `null` | No | yes |
| `deliverGroup(@Nullable String)` | `_deliverGroup(group)` → `this.deliverGroup = emptyAsNull(group);` | A | `null` | No | yes |
| `deliverSubject(@Nullable String)` | `_deliverSubject(deliverSubject)` → `this.deliverSubject = emptyAsNull(subject);` | A | `null` | No | yes |

### `PushOrderedConsumerCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `deliverSubject(@Nullable String)` | `_deliverSubject(deliverSubject)` → `emptyAsNull` | A | `null` | No | yes |

### `ExternalCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `api(String)` | `this.api = api;` | **E** | `""` | No (suppressed by `addField`) | yes |
| `deliver(String)` | `this.deliver = deliver;` | **E** | `""` | No | yes |

### `PlacementCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `cluster(String)` | `this.cluster = cluster;` | **E** | `""` | No | yes |

Note: the constructor `PlacementCreator(String cluster, List tags)` DOES normalize empty:
`this.cluster = cluster == null || cluster.isEmpty() ? null : cluster;` — so the setter is
inconsistent with the constructor in the same class.

### `StreamCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `description(@Nullable String)` | `this.description = emptyAsNull(description);` | A | `null` | No | yes |
| `templateOwner(@Nullable String)` | `this.templateOwner = emptyAsNull(templateOwner);` | A | `null` | No | yes |

### `StreamSourceCreator` (abstract base for Mirror/Source)

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `filterSubject(@Nullable String)` | `this.filterSubject = filterSubject;` | **E** | `""` | No (suppressed by `addField`) | yes |
| `domain(String)` | `String prefix = convertDomainToPrefix(domain); externalCreator = prefix == null ? null : new ExternalCreator().api(prefix);` | A | n/a (no String field for domain itself; sets/clears `externalCreator` via `validatePrefixOrDomain` which calls `emptyAsNull`) | n/a | n/a |

Note: `StreamSourceCreator.filterSubject` is the most striking inconsistency in this audit —
`ConsumerCreator.filterSubject` is the gold-standard A pattern (cited in the task), but the
identically-named setter on `StreamSourceCreator` is a raw assignment.

### `MirrorCreator` / `SourceCreator`

No String setters beyond what they inherit from `StreamSourceCreator` (so they inherit the
`filterSubject` E-bug too).

### `RepublishCreator`, `SubjectTransformCreator`, `ConsumerLimitsCreator`

No setters (or no String setters).

### `KeyValueConfigurationCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `description(String)` | `streamCreator.description(description)` → `emptyAsNull` | A | `null` | No | n/a (delegated) |

### `ObjectStoreConfigurationCreator`

| Setter | Body excerpt | Bucket | `getX()` after `foo("")` | `toJson` emits `""`? | Field `@Nullable`? |
|---|---|:-:|:-:|:-:|:-:|
| `description(String)` | `streamCreator.description(description)` → `emptyAsNull` | A | `null` | No | n/a (delegated) |

---

## Cross-class summary — all D/E setters (the action items)

| # | Class.method | Field type | Bucket | Field `@Nullable`? | Notes |
|---|---|---|:-:|:-:|---|
| 1 | `StreamSourceCreator.filterSubject(@Nullable String)` | `String` | E | yes | Same name as the A-pattern model setter in `ConsumerCreator`. Inherited by `MirrorCreator` and `SourceCreator`. **Highest-priority fix** by virtue of name collision with the stated reference. |
| 2 | `PlacementCreator.cluster(String)` | `String` | E | yes | Constructor in same class normalizes empty; setter does not. |
| 3 | `ExternalCreator.api(String)` | `String` | E | yes | Used both directly and via `StreamSourceCreator.domain(...)` (the latter path is safe; the direct setter is not). |
| 4 | `ExternalCreator.deliver(String)` | `String` | E | yes | Same shape as `api`. |
| 5 | `AbstractOrderedConsumerCreator.namePrefix(@Nullable String)` | `String` | E | yes | Stores `""` raw. Worse: `JetStreamApiUtils.generateConsumerName("")` returns `"-<random>"` (a malformed name) because the null-check there does not catch empty. Two-line fix in two places. |

---

## Recommendation

**Adopt a single uniform pattern: rewrite the 4 E-bucket setters to use `emptyAsNull` (the A
pattern).** Do not add a new helper — `emptyAsNull` already exists in
`core/src/main/java/io/synadia/client/utils/Validator.java` and is already imported across the
audited classes (it's used by 14 of the 18 audited setters, and is the literal model the task
references). Adding `ApiUtils.emptyAsNullOr(...)` would just duplicate it.

Rationale:
1. **The pattern already dominates.** 14/18 setters (~78%) already do `emptyAsNull`. The 4 outliers
   look like oversights, not deliberate design — and in `PlacementCreator` the constructor
   contradicts the setter inside the same class.
2. **Latent bug surface.** `addField(String,String,String)` happens to suppress `""` today, but
   `addFieldAlways` does not — any future toJson change or new API surface (e.g. update calls)
   could expose the `""` immediately. Fixing at the source removes the trap.
3. **Behavioral correctness right now.** Even with the JSON suppressed, `getX()`, `equals()`,
   `hashCode()`, and copy constructors all observably differ between `creator.foo(null)` and
   `creator.foo("")` for E-bucket setters. That violates the principle implied by the @NullMarked
   package + `emptyAsNull` convention.
4. **`namePrefix` has a second latent bug** (`generateConsumerName("")` → `"-<random>"`) that an
   `emptyAsNull` fix at the setter eliminates without touching `JetStreamApiUtils`.

### Concrete fix (drop-in)

For each of the 4 E setters, change the body to assign `emptyAsNull(arg)`:

```java
// StreamSourceCreator
public T filterSubject(@Nullable String filterSubject) {
    this.filterSubject = emptyAsNull(filterSubject);
    return (T) this;
}

// PlacementCreator
public PlacementCreator cluster(@Nullable String cluster) {
    this.cluster = emptyAsNull(cluster);
    return this;
}

// ExternalCreator
public ExternalCreator api(@Nullable String api)     { this.api = emptyAsNull(api);         return this; }
public ExternalCreator deliver(@Nullable String dlv) { this.deliver = emptyAsNull(dlv);     return this; }

// AbstractOrderedConsumerCreator
public T namePrefix(@Nullable String namePrefix) {
    String np = emptyAsNull(namePrefix);
    this.namePrefix = np;
    _name(JetStreamApiUtils.generateConsumerName(np));
    return (T) this;
}
```

Each file already imports `Validator.emptyAsNull` (either directly or via `JsValidator`'s
inheritance from `Validator`), so no new imports are needed in any case.
