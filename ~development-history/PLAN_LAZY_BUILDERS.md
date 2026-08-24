# PLAN: Adopt `LazyMapBuilder` / `LazyArrayBuilder`

> **STATUS: APPLIED.** All candidates C1–C8 converted. Production: `ConsumerConfiguration.getDefaultInstance()` (C1, dropped the `try/catch` + `JsonParseException`/`LazyJsonParser` imports) and `StreamState.EMPTY` (C2, collapsed the `static{}` block to a field initializer, dropped the `LazyJsonParser` import). Tests: `ApiUtilsTests` C3–C8 (5 maps + 1 array). The "not candidates" (NC1–NC7) were left as-is. (Two similar trivial literals at `ApiUtilsTests:219,232` were not in the C-list and were left untouched.)

## Intro

`jnats-json` (merged PR #37 "Lazy Builders for maps and arrays") added two builders that
construct an `io.nats.json.LazyJsonValue` **directly from values**, without serializing to a
JSON string and re-parsing it:

- **`io.nats.json.LazyMapBuilder`** — `new LazyMapBuilder()` (drops nulls by default, same as
  `addField`) or `LazyMapBuilder.instance(boolean putNulls)`; `.put(String key, @Nullable Object value)`
  (chainable, converts the value to a `LazyJsonValue`); `.putEntries(Map)`; `.build()` →
  `LazyJsonValue` (also exposed as public field `.jv`).
- **`io.nats.json.LazyArrayBuilder`** — `new LazyArrayBuilder()` (drops nulls by default) or
  `LazyArrayBuilder.instance(boolean addNulls)`; `.add(@Nullable Object value)`; `.addItems(Collection)`;
  `.build()` → `LazyJsonValue` (also `.jv`).

v3 already depends on `io.nats:jnats-json-jdk21:3.0.10-SNAPSHOT`, which contains these — no
dependency change is required.

### Key distinction (do NOT confuse the two builders)

- `LazyMapBuilder` / `LazyArrayBuilder` build a **`LazyJsonValue`** (the lazy read model).
- The eager `MapBuilder` / `ArrayBuilder` build a **`JsonValue`** (the eager model).

Only sites that ultimately want a **`LazyJsonValue`** are candidates. Sites that legitimately
need the eager `JsonValue` (e.g. `ApiUtils.jvNameUndefined()`) must stay on `MapBuilder`.

### Null-handling semantics

The default `new LazyMapBuilder()` / `new LazyArrayBuilder()` (and the no-arg `instance()`
overloads) **drop nulls** — equivalent to `addField`. To keep nulls present in the output you
must use `LazyMapBuilder.instance(true)` / `LazyArrayBuilder.instance(true)`. None of the
candidates below build literals that contain a JSON `null`, so the default (null-dropping)
behavior is safe for every one. This is flagged per-site anyway.

---

## Candidate sites

### C1 — `ConsumerConfiguration.getDefaultInstance()` — `jetstream/.../api/ConsumerConfiguration.java:33-34` — **SAFE (high value)**

Current:
```java
DEFAULT_INSTANCE = new ConsumerConfiguration(
    LazyJsonParser.parse("{\"name\":\"" + UNDEFINED + "\"}"));
```
Builds a JSON string by concatenating `UNDEFINED` into a string literal, then parses it. The
whole `try/catch (JsonParseException)` exists only because `parse` declares the checked
exception (the comment says "parse will always work").

Proposed:
```java
DEFAULT_INSTANCE = new ConsumerConfiguration(
    new LazyMapBuilder().put(NAME, UNDEFINED).build());
```
- Add `import io.nats.json.LazyMapBuilder;` and use the existing `NAME` constant
  (`ApiConstants.NAME` — already statically imported via `ApiConstants.*`).
- The `try/catch (JsonParseException ignore)` block can be removed entirely (builder does not
  throw a checked exception), which also removes the now-unused `JsonParseException` import.
- **Null handling:** `UNDEFINED` is a non-null `String` constant — null-drop default is fine.
- **Escaping:** `UNDEFINED` is a fixed, safe constant (no quotes/backslashes), so the current
  concatenation has no escaping bug today; the builder additionally makes any future value safe.
- **Risk:** low. This mirrors `ApiUtils.jvNameUndefined()` but for the *lazy* model — keep them
  conceptually paired (see NC1). Recommend keeping the field name `DEFAULT_INSTANCE`.

### C2 — `StreamState.EMPTY` — `jetstream/.../api/StreamState.java:24-27` — **SAFE (high value)**

Current:
```java
static {
    try { EMPTY = new StreamState(io.nats.json.LazyJsonParser.parse("{}")); }
    catch (Exception e) { throw new RuntimeException(e); }
}
```
Parses the empty-object literal `"{}"` just to get an empty `LazyJsonValue`.

Proposed:
```java
static final StreamState EMPTY = new StreamState(new LazyMapBuilder().build());
```
- Add `import io.nats.json.LazyMapBuilder;`. The whole `static {}` block and its
  `try/catch (Exception)` go away (builder is exception-free), so the field can become a simple
  initializer.
- **Null handling:** empty map, no nulls — trivially safe.
- **Escaping:** none involved.
- **Risk:** low. Confirm `new LazyMapBuilder().build()` yields a `LazyJsonValue` whose
  `getMap()` is empty/non-null exactly as a parsed `"{}"` does (expected — that is the builder's
  purpose). Mark **needs a quick confirm** only on that equivalence, otherwise safe.

### C3 — `ApiUtilsTests.readString_lazyJsonValue_presentAndMissing` — `core/.../utils/ApiUtilsTests.java:185` — **SAFE (test, low value)**

Current:
```java
LazyJsonValue ljv = LazyJsonParser.parseUnchecked("{\"k\":\"v\"}");
```
Proposed:
```java
LazyJsonValue ljv = new LazyMapBuilder().put("k", "v").build();
```
- `io.nats.json.*` is already imported, so `LazyMapBuilder` needs no new import.
- This is the lazy mirror of the eager `MapBuilder.instance().put("k","v").jv` used two methods
  up (`readString_jsonValue_presentAndMissing`, line 178). Rewriting it makes the lazy/eager
  test pair structurally symmetric.
- **Null handling / escaping:** trivial single string field, safe.
- **Risk:** low. Pure test readability; behavior identical.

### C4 — `ApiUtilsTests.readStringOrEmpty_presentAndAbsent` — `core/.../utils/ApiUtilsTests.java:260` — **SAFE (test, low value)**

Current:
```java
LazyJsonValue ljv = LazyJsonParser.parseUnchecked("{\"k\":\"v\"}");
```
Proposed: same as C3 — `new LazyMapBuilder().put("k", "v").build();`. Same reasoning, same low
risk.

### C5 — `ApiUtilsTests.readIntegerOrMinusOne_presentAndAbsent` — `core/.../utils/ApiUtilsTests.java:244` — **SAFE (test, low value)**

Current:
```java
LazyJsonValue ljv = LazyJsonParser.parseUnchecked("{\"n\":42}");
```
Proposed:
```java
LazyJsonValue ljv = new LazyMapBuilder().put("n", 42).build();
```
- Number field; builder converts the `int` to the right `LazyJsonValue`. Safe.

### C6 — `ApiUtilsTests.readLongOrMinusOne_presentAndAbsent` — `core/.../utils/ApiUtilsTests.java:252` — **NEEDS-REVIEW (test, low value)**

Current:
```java
LazyJsonValue ljv = LazyJsonParser.parseUnchecked("{\"n\":9000000000}");
```
Proposed:
```java
LazyJsonValue ljv = new LazyMapBuilder().put("n", 9_000_000_000L).build();
```
- This is the lazy mirror of the eager `MapBuilder.instance().put("n", 9_000_000_000L).jv` at
  line 207. **Before applying, verify** that `LazyMapBuilder.put` serializes a `long` outside the
  `int` range the same way the parser stores the literal `9000000000`, so `readLongOrMinusOne`
  still returns `9_000_000_000L`. The eager `MapBuilder` already does this at line 207, which is
  strong evidence the lazy one does too, but confirm before committing. **needs-review** only
  for that numeric-width check.

### C7 — `ApiUtilsTests.mapToList_appliesMapperOverArray` — `core/.../utils/ApiUtilsTests.java:46` — **SAFE (test, low value, array)**

Current:
```java
LazyJsonValue arrayLjv = LazyJsonParser.parseUnchecked("[\"a\",\"b\",\"c\"]");
```
Proposed:
```java
LazyJsonValue arrayLjv = new LazyArrayBuilder().add("a").add("b").add("c").build();
// or: new LazyArrayBuilder().addItems(Arrays.asList("a", "b", "c")).build();
```
- Add nothing — `io.nats.json.*` already imported. Demonstrates `LazyArrayBuilder` (the only
  array candidate found).
- **Null handling:** no nulls; default null-drop is fine.
- **Risk:** low. Test stays equivalent.

### C8 — `ApiUtilsTests.mapToList_nullOrEmptyArray` (the map branch) — `core/.../utils/ApiUtilsTests.java:40` — **OPTIONAL (test, low value)**

Current:
```java
LazyJsonValue mapLjv = LazyJsonParser.parseUnchecked("{\"a\":1}");
```
Could become `new LazyMapBuilder().put("a", 1).build();`. Purely cosmetic; the test only needs
"a non-array `LazyJsonValue`". Include only if doing a sweep of this file for consistency.

---

## Not candidates (and why)

- **NC1 — `ApiUtils.jvNameUndefined()` (`core/.../utils/ApiUtils.java:94-101`).** Returns an
  **eager `JsonValue`**, built with the eager `MapBuilder`. This is the correct builder for its
  return type. **Keep as-is.** (It is the eager twin of candidate C1; do not unify them.)

- **NC2 — `KeyValueEntry.toString()` `:149`, `KeyValueStatus.toString()` `:184`,
  `ObjectStoreStatus.toString()` `:149`.** Each does `"... " + new MapBuilder()....toJson()` to
  produce a human-readable `toString()` **String**. They need the eager `MapBuilder`'s string
  output, never a `LazyJsonValue`. **Keep as-is.**

- **NC3 — `ApiUtilsTests` eager-`MapBuilder` sites `:178, :192, :200, :207`.** These deliberately
  test the **eager `JsonValue`** `readString/readBoolean/readInteger/readLong(JsonValue, …)`
  overloads. They must keep using `MapBuilder` (the eager builder) — they are the eager half of
  the lazy/eager pairs. **Keep as-is** (they are the reference for the lazy rewrites C3/C5/C6).

- **NC4 — `ServerInfo` constructor (`core/.../api/ServerInfo.java:41`).** Parses an arbitrary
  inbound `INFO {...}` protocol string from the server (note `json.indexOf("{")` to skip the
  `INFO ` prefix). There is no value map to build — it must parse real wire JSON. **Keep as-is.**

- **NC5 — All `JsonSerializable` `toJson()` writers/creators** (`beginJson`/`addField`/`endJson`
  string builders, e.g. the `*Creator` classes). They return **`String`**, not `LazyJsonValue`,
  so the lazy builders do not apply.

- **NC6 — The large body of `LazyJsonParser.parse(...)` / `parseUnchecked(...)` calls in tests**
  (e.g. `JsonParsingTests`, `StreamCreatorConfigurationTests`, `ApiCreatorsToDtoRoundTripTests`,
  `ConsumerInfoJsonTests`, `StreamInfoJsonTests`, `ApiFieldsTest`, `EqualityAndHashCodeCoverageTest`,
  `ApiResponseTests`, and most of `ConsumerConfigurationTests`). These fall into two groups, both
  **NOT candidates**:
  1. **Round-trip / serialization tests** that parse the output of a real `toJson()` (e.g.
     `new Source(LazyJsonParser.parseUnchecked(c1.toJson()))`). The parse step *is the thing under
     test* — replacing it would defeat the test's purpose.
  2. **Fixture parses** of multi-field literal/data-file JSON that intentionally exercises the
     parser against representative server payloads (`JsonParsingTests` etc.). Rebuilding these as
     builder calls would lose the wire-format coverage and add noise. **Keep as-is.**
  Only the handful of *trivial single-purpose* literals in `ApiUtilsTests` (C3–C8) are worth
  converting, and only for symmetry with the adjacent eager-builder tests.

- **NC7 — `ConsumerSource(String, String)`.** This was the original `LazyMapBuilder` example, but
  the value/copy constructors were since **removed** entirely (`ConsumerSource` only ever comes from
  the server, so it keeps just the `LazyJsonValue` ctor). No longer relevant.

---

## Suggested order & effort estimate

Do the two production-code wins first (they remove dead `try/catch` and a checked-exception
import, so they are the highest value), then the test cleanups as one symmetry pass.

| Order | Sites | What | Effort | Confidence |
|------:|-------|------|--------|-----------|
| 1 | **C1** | `ConsumerConfiguration.getDefaultInstance()` → `LazyMapBuilder`; drop try/catch + `JsonParseException` import | ~10 min | high / safe |
| 2 | **C2** | `StreamState.EMPTY` → `LazyMapBuilder`; collapse `static {}` to a field initializer | ~10 min | high / safe (confirm empty-map equivalence) |
| 3 | **C3, C4, C5, C7** | `ApiUtilsTests` trivial literals → lazy builders (3 maps + 1 array), pairing with the eager tests | ~15 min | high / safe |
| 4 | **C6** | `ApiUtilsTests` long-width literal → `LazyMapBuilder` | ~5 min | medium (verify 64-bit `put` matches parsed literal first) |
| 5 | **C8** | optional cosmetic `ApiUtilsTests:40` map literal | ~5 min | low priority |

Total: roughly **30–45 minutes**, no API/behavior change. After C1 and C2, double-check there are
no remaining `import io.nats.json.LazyJsonParser;` / `JsonParseException` references left unused
in those two files (they should be removable). Run the existing `ApiUtilsTests`,
`ConsumerConfigurationTests`, and any `StreamState`-touching tests to confirm equivalence — but
do **not** run gradle here; defer the build to the user.

> Per task instructions, this is a plan only — **no code was changed.**
