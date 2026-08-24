# Integration Plan — PR #1565 (2.14 Stream Consumer Source support)

> **STATUS: INTEGRATED — verified, committed, and pushed by the user.** New `ConsumerSource` (reader) +
> `ConsumerSourceCreator` (writer); `ApiConstants.CONSUMER`; `StreamSource.getConsumerSource()`;
> `StreamSourceCreator` field/setter/getter/copy-ctors/toJson/equals/hashCode; fixture `consumer`
> blocks + `testConsumerSource` and `validateSource`/`validateTestStreamConfiguration` assertions.
> Took the add-only fixture option (kept existing field names). Not run through gradle.

Goal: port upstream `nats-io/nats.java` PR #1565 (MERGED 2026-05-06) into `nats.java.v3`. Status carried over from `INTEGRATION_PLAN_OVERVIEW.md`: **Not integrated** — none of the symbols exist in v3.

PR is **merged** upstream, so names/semantics are stable. The only re-sync concern is v3's split reader/writer model (see below).

## What the PR does (upstream)

Adds a `ConsumerSource` domain type to the stream Source/Mirror config: a durable-consumer name + a deliver subject, serialized under a new `"consumer"` JSON key inside each `source`/`mirror` block.

Upstream lands it as a **single** class `io.nats.client.api.ConsumerSource` (reader+writer+builder in one), wired into `SourceBase` (read in the `JsonValue` ctor, written in `toJson()`, exposed via `getConsumerSource()`, set via the `consumerSource(...)` builder method, and folded into `equals`/`hashCode`). Fields:
- `name` — JSON key `NAME` (`"name"`), validated with `validateConsumerName(name, true)`.
- `deliverSubject` — JSON key `DELIVER_SUBJECT` (`"deliver_subject"`), validated with `validateSubject(deliverSubject, true)`.

The PR also **renamed several existing fixture fields** in `StreamConfiguration.json` and the matching test (`eman`→`mirror_name`, `mfsub`→`mirror_fsub`, `apithing`→`mirror_ext_api`, `dlvrsub`→`mirror_ext_deliver`, `s0api`→`s0_ext_api`, `s0dlvrsub`→`s0_ext_deliver`, etc.) and added a `validateExternal(...)` helper. These renames are cosmetic and orthogonal to the feature — see Open decisions.

## v3 structural differences that shape this port

- **Package** `io.synadia.client.*`; this work is entirely in the `jetstream/` module plus one constant in `core/`.
- **v2 `SourceBase` is split in v3** into `StreamSource` (abstract lazy-JSON **reader**, extended by `Mirror`/`Source`) and `StreamSourceCreator<T>` (abstract **writer/builder**, extended by `MirrorCreator`/`SourceCreator`). So upstream's one `ConsumerSource` becomes **two classes** in v3, mirroring the existing `External` / `ExternalCreator` pair exactly:
  - `ConsumerSource` extends `LazyApiObject` (reader), modeled on `External.java`.
  - `ConsumerSourceCreator` implements `JsonSerializable` (writer + fluent setters), modeled on `ExternalCreator.java`.
- **JSON read** via `io.nats.json.LazyJsonValueUtils.readString` / `readValue`; **JSON write** via `io.nats.json.JsonWriteUtils.addField` / `beginJson` / `endJson`.
- **Validation:** `validateConsumerName` lives in `jetstream/.../utils/JsValidator`; `validateSubject` lives in `core/.../utils/Validator`. `JsValidator extends Validator`, so a single static import of `JsValidator.validateConsumerName` and `Validator.validateSubject` (or both via `JsValidator`) works.
- **Conventions:** `@NullMarked` on class; `@Nullable` after the access modifier; `equals()` is `final` and uses the `instanceof X that` pattern; lazy cached fields use `_foo` (not needed here); order parallel groups alphabetically.
- **Mirror/Source/MirrorCreator/SourceCreator do NOT override `equals`/`hashCode`** — they delegate to the base (`StreamSource` via `LazyApiObject`'s ljv-based equality; `StreamSourceCreator` via its own final equals). So the new field only needs to be added in the two base classes, **not** in the four leaf classes. (Verified: no `equals` in any of the four leaf files.)

## v3 status (verified 2026-06-17)

| Marker searched | Result |
|---|---|
| `ConsumerSource` / `consumerSource` / `getConsumerSource` (any `.java`) | **not found** anywhere in v3 |
| `CONSUMER = "consumer"` constant in `core/.../utils/ApiConstants.java` | **not found** (file has `CONSUMER_COUNT`/`CONSUMER_SEQ`/`CONSUMER_LIMITS`/`CONSUMERS` at lines 36–39, but no bare `CONSUMER`) |
| `DELIVER_SUBJECT` constant | **present** — `ApiConstants.java:49` (`"deliver_subject"`), reusable as-is |
| Test `testConsumerSource` | **not found** |
| Old fixture names `eman`/`mfsub`/`apithing`/`dlvrsub` | **still present** — `jetstream/src/test/resources/data/StreamConfiguration.json:53,56,58,59,...` and asserted in `StreamCreatorConfigurationTests.java:464,467,470,471,510–512` |

Classification: **Not integrated.**

## File inventory

### New files (2)
| Path | Purpose |
|---|---|
| `jetstream/src/main/java/io/synadia/client/api/ConsumerSource.java` | Reader. Extends `LazyApiObject`. Models `External.java`. |
| `jetstream/src/main/java/io/synadia/client/api/ConsumerSourceCreator.java` | Writer/builder. Implements `JsonSerializable`. Models `ExternalCreator.java`. |

### Modified files (4)
| Path | Why |
|---|---|
| `core/src/main/java/io/synadia/client/utils/ApiConstants.java` | Add `CONSUMER = "consumer"` (alphabetical, before `CONSUMER_COUNT`). |
| `jetstream/src/main/java/io/synadia/client/api/StreamSource.java` | Add `getConsumerSource()` reader getter. |
| `jetstream/src/main/java/io/synadia/client/api/StreamSourceCreator.java` | Add `consumerSourceCreator` field + setter + copy-ctors + `toJson()` + `equals`/`hashCode`. |
| `jetstream/src/test/java/io/synadia/client/api/StreamCreatorConfigurationTests.java` | New `testConsumerSource`; assert `getConsumerSource()` on mirror + sources. |

### Modified fixture (1)
| Path | Why |
|---|---|
| `jetstream/src/test/resources/data/StreamConfiguration.json` | Add a `"consumer"` block under `mirror` and each `sources[]` entry. (Optional field-rename reconciliation — see Open decisions.) |

## Step-by-step

Order chosen so the tree compiles after every step.

### Step 1 — `ApiConstants.CONSUMER`
File: `core/src/main/java/io/synadia/client/utils/ApiConstants.java`, insert before line 36 (`CONSUMER_COUNT`), keeping the column alignment of the surrounding constants:
```java
    /** consumer */                  String CONSUMER                      = "consumer";
```

### Step 2 — New reader `ConsumerSource.java`
Create `jetstream/src/main/java/io/synadia/client/api/ConsumerSource.java`. Mirror `External.java` (lines 1–49). `getName()` reads `NAME`; `getDeliverSubject()` reads `DELIVER_SUBJECT`. Both are server-supplied and may be absent → `@Nullable` via `readString` (which returns null when absent). The class is package-private-extendable but `public` like `External`:
```java
package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.utils.ApiConstants.DELIVER_SUBJECT;
import static io.synadia.client.utils.ApiConstants.NAME;

/**
 * Consumer information for durable sourcing. Dictates that a durable consumer with a
 * specific name is used for sourcing. Returned from the server.
 */
@NullMarked
public class ConsumerSource extends LazyApiObject {

    @Nullable
    static ConsumerSource optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new ConsumerSource(v);
    }

    ConsumerSource(LazyJsonValue v) {
        super(v);
    }

    /**
     * The durable consumer name used for sourcing.
     * @return the consumer name
     */
    @Nullable
    public String getName() {
        return readString(ljv, NAME);
    }

    /**
     * The subject to deliver messages to.
     * @return the deliver subject
     */
    @Nullable
    public String getDeliverSubject() {
        return readString(ljv, DELIVER_SUBJECT);
    }

    @Override
    public String toString() {
        return "ConsumerSource " + ljv.toJson();
    }
}
```
Note: `External.getApi()` uses `readStringOrEmpty` because `api` is a required field per the REQUIRED_FIELDS_POLICY. For `ConsumerSource`, both fields are required-when-present at the writer layer but optional on read; `readString` (nullable) matches the simplest interpretation. If the project's required-fields policy says `name`/`deliver_subject` are never-absent-when-the-object-exists, switch `getName()` to `readStringOrEmpty(ljv, NAME)` to match `External.getApi()` — flagged in Open decisions.

### Step 3 — New writer `ConsumerSourceCreator.java`
Create `jetstream/src/main/java/io/synadia/client/api/ConsumerSourceCreator.java`. Mirror `ExternalCreator.java` (lines 1–111). Upstream validates in `Builder.build()`; v3's `ExternalCreator` validates **eagerly in the constructor and setters** (`Validator.required(api, "api")`). Match that v3 pattern — validate `name` and `deliverSubject` in the ctor and in each setter so the object is never in an invalid state:
```java
package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.DELIVER_SUBJECT;
import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.JsValidator.validateConsumerName;
import static io.synadia.client.utils.Validator.validateSubject;

/**
 * ConsumerSourceCreator is used to create the consumer information for durable sourcing.
 */
@NullMarked
public class ConsumerSourceCreator implements JsonSerializable {
    private String name;
    private String deliverSubject;

    /**
     * Construct a ConsumerSourceCreator
     * @param name the durable consumer name
     * @param deliverSubject the deliver subject
     */
    public ConsumerSourceCreator(String name, String deliverSubject) {
        this.name = validateConsumerName(name, true);
        this.deliverSubject = validateSubject(deliverSubject, true);
    }

    /**
     * Construct a ConsumerSourceCreator from a ConsumerSource (server response)
     * @param cs the consumer source to copy from
     */
    ConsumerSourceCreator(ConsumerSource cs) {
        this(cs.getName(), cs.getDeliverSubject());
    }

    /**
     * Set the consumer name.
     * @param name the consumer name
     * @return this instance for chaining
     */
    public ConsumerSourceCreator name(String name) {
        this.name = validateConsumerName(name, true);
        return this;
    }

    /**
     * Set the deliver subject.
     * @param deliverSubject the deliver subject
     * @return this instance for chaining
     */
    public ConsumerSourceCreator deliverSubject(String deliverSubject) {
        this.deliverSubject = validateSubject(deliverSubject, true);
        return this;
    }

    /**
     * The durable consumer name used for sourcing.
     * @return the consumer name
     */
    public String getName() {
        return name;
    }

    /**
     * The subject to deliver messages to.
     * @return the deliver subject
     */
    public String getDeliverSubject() {
        return deliverSubject;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, NAME, name);
        addField(sb, DELIVER_SUBJECT, deliverSubject);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ConsumerSourceCreator" + toJson();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof ConsumerSourceCreator that)) return false;
        return Objects.equals(name, that.name)
            && Objects.equals(deliverSubject, that.deliverSubject);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(deliverSubject);
        return result;
    }
}
```
Note `validateConsumerName(name, true)` (from `JsValidator`) rejects dots — `JsValidator.java:24-26` → `validatePrintableExceptWildDotGtSlashes`. `validateSubject` (from core `Validator`, `Validator.java:91`) allows dots but rejects spaces/control chars. This matches upstream's `name(HAS_DOT)`→throws and `deliverSubject(HAS_SPACE)`→throws test cases.

### Step 4 — `StreamSource` reader getter
File: `jetstream/src/main/java/io/synadia/client/api/StreamSource.java`. Add after `getExternal()` (ends line 78), before `getSubjectTransforms()` — alphabetical/parallel with the upstream attach-after-external placement:
```java
    /**
     * Get the consumer source for durable sourcing
     * @return the consumer source, or null if not configured
     */
    @Nullable
    public ConsumerSource getConsumerSource() {
        return ConsumerSource.optionalInstance(readValue(ljv, CONSUMER));
    }
```
`readValue` and `CONSUMER` are already covered by the existing wildcard static imports (`LazyJsonValueUtils.*` line 11, `ApiConstants.*` line 13). No new import needed.

### Step 5 — `StreamSourceCreator` writer wiring
File: `jetstream/src/main/java/io/synadia/client/api/StreamSourceCreator.java`. Five edits, all parallel to how `externalCreator` is handled:

5a. Field — after line 28 (`private @Nullable ExternalCreator externalCreator;`):
```java
    private @Nullable ConsumerSourceCreator consumerSourceCreator;
```

5b. Basis copy-ctor — after line 49 (`this.externalCreator = basis.externalCreator;`):
```java
        this.consumerSourceCreator = basis.consumerSourceCreator;
```

5c. From-`StreamSource` ctor — after line 63 (`this.externalCreator = ext == null ? null : new ExternalCreator(ext);`):
```java
        ConsumerSource cs = ss.getConsumerSource();
        this.consumerSourceCreator = cs == null ? null : new ConsumerSourceCreator(cs);
```

5d. Fluent setter — after `externalCreator(...)` (ends line 107), before `domain(...)`:
```java
    /**
     * Set the consumer source for durable sourcing
     * @param consumerSourceCreator the consumer source
     * @return this instance for chaining
     */
    public T consumerSource(@Nullable ConsumerSourceCreator consumerSourceCreator) {
        this.consumerSourceCreator = consumerSourceCreator;
        return (T) this;
    }
```

5e. Getter — after `getExternalCreator()` (line 157):
```java
    /** @return the consumer source */
    @Nullable public ConsumerSourceCreator getConsumerSourceCreator() { return consumerSourceCreator; }
```

5f. `toJson()` — after line 169 (`addField(sb, EXTERNAL, externalCreator);`):
```java
        addField(sb, CONSUMER, consumerSourceCreator);
```

5g. `equals` — extend the chain (currently ends line 183 with `&& subjectTransformCreators.equals(...)`):
```java
            && subjectTransformCreators.equals(that.subjectTransformCreators)
            && Objects.equals(consumerSourceCreator, that.consumerSourceCreator);
```

5h. `hashCode` — after line 193 (`result = 31 * result + subjectTransformCreators.hashCode();`):
```java
        result = 31 * result + Objects.hashCode(consumerSourceCreator);
```
`CONSUMER` is already covered by the `ApiConstants.*` wildcard import (line 15). No new import.

### Step 6 — Fixture `StreamConfiguration.json`
File: `jetstream/src/test/resources/data/StreamConfiguration.json`.

Add a `"consumer"` block as the last member of `mirror` (after the `subject_transforms` array, line ~63) and of each `sources[]` entry:
```json
    "subject_transforms": [
      {"src":"m_st_src0","dest":"m_st_dest0"},
      {"src":"m_st_src1","dest":"m_st_dest1"}
    ],
    "consumer": {
      "name": "mirror_con_name",
      "deliver_subject": "mirror_con_deliver"
    }
```
For `sources[0]`: `s0_con_name` / `s0_con_deliver`; for `sources[1]`: `s1_con_name` / `s1_con_deliver`. (Remember to add a comma after the closing `]` of `subject_transforms` in each block.)

**Do NOT rename `eman`/`mfsub`/`apithing`/`dlvrsub`/`s0api`/`s0dlvrsub` unless also updating the matching assertions** — see Open decisions. The minimal, self-consistent port keeps those names as-is and only adds the new `consumer` blocks.

### Step 7 — Tests
File: `jetstream/src/test/java/io/synadia/client/api/StreamCreatorConfigurationTests.java`.

7a. Add a unit test for the creator (model on upstream `testConsumerSource`, adapted to v3's eager-validation creator — no `.builder()`/`.build()`; validation throws from the ctor/setters). Needs imports of `HAS_DOT`/`HAS_SPACE` (in `core/.../utils/TestBase.java:47,51`) — the v3 test currently extends `JetStreamTestBase`; confirm that chains to `TestBase` (it does), so the constants are in scope. Add near `testRepublish`/`testSubjectTransform`:
```java
    @Test
    public void testConsumerSource() {
        ConsumerSourceCreator cs = new ConsumerSourceCreator("csname", "csdeliver");
        assertEquals("csname", cs.getName());
        assertEquals("csdeliver", cs.getDeliverSubject());

        // round-trip through JSON read side
        ConsumerSource read = ConsumerSource.optionalInstance(
            LazyJsonParser.parse(cs.toJson()));      // adapt to v3's parse entry point
        assertNotNull(read);
        assertEquals("csname", read.getName());
        assertEquals("csdeliver", read.getDeliverSubject());

        // validation
        assertThrows(IllegalArgumentException.class, () -> new ConsumerSourceCreator(null, "supplied"));
        assertThrows(IllegalArgumentException.class, () -> new ConsumerSourceCreator("supplied", null));
        assertThrows(IllegalArgumentException.class, () -> new ConsumerSourceCreator(HAS_DOT, "supplied"));   // name rejects dots
        assertThrows(IllegalArgumentException.class, () -> new ConsumerSourceCreator("supplied", HAS_SPACE)); // subject rejects spaces
        assertThrows(IllegalArgumentException.class, () -> cs.name(HAS_DOT));
        assertThrows(IllegalArgumentException.class, () -> cs.deliverSubject(HAS_SPACE));
    }
```
(Confirm the exact lazy-parse helper used elsewhere in this test class — it already imports `LazyJsonParser`; use the same call other tests use to turn a JSON string into a `LazyJsonValue`.)

7b. Extend the fixture validation in `validateTestStreamConfiguration` / `validateSource`:
- After `validateSubjectTransforms(mirror.getSubjectTransforms(), 2, "m");` (line ~477):
  ```java
  assertNotNull(mirror.getConsumerSource());
  assertEquals("mirror_con_name", mirror.getConsumerSource().getName());
  assertEquals("mirror_con_deliver", mirror.getConsumerSource().getDeliverSubject());
  ```
- In `validateSource` (after line 512), add:
  ```java
  assertNotNull(source.getConsumerSource());
  assertEquals(name + "_con_name", source.getConsumerSource().getName());
  assertEquals(name + "_con_deliver", source.getConsumerSource().getDeliverSubject());
  ```
  (Here `name` is `s0`/`s1`, matching the fixture keys.)

Optionally factor a `validateConsumerSource(ConsumerSource, String)` helper to mirror upstream — keep it consistent with the existing `validateSubjectTransforms` static helper style in this file.

### Step 8 — Sanity spot-checks (do NOT run gradle — per project memory)
1. Grep proves the new symbols land:
   ```bash
   grep -rn "ConsumerSource\|getConsumerSource\|CONSUMER\b" jetstream/src/main core/src/main/java/io/synadia/client/utils/ApiConstants.java --include="*.java"
   ```
2. Verify no leaf class (`Mirror`/`Source`/`MirrorCreator`/`SourceCreator`) needs an `equals` touch — already confirmed none override it.
3. Confirm `LazyJsonParser` usage in the new test matches the existing pattern in `StreamCreatorConfigurationTests`.

## Open decisions for review

1. **Reader nullability of `getName()`/`getDeliverSubject()`.** `External.getApi()` uses `readStringOrEmpty` (required-field policy) while `getDeliver()` uses nullable `readString`. This plan uses nullable `readString` for both `ConsumerSource` getters (simplest, and upstream marks them `@NonNull` only because the writer guarantees them). If the v3 required-fields policy treats `name` as never-absent-when-present, switch `getName()` to `readStringOrEmpty` to match `External.getApi()`. Low-stakes; confirm with maintainer.
2. **Fixture field renames.** Upstream PR 1565 renamed `eman`/`mfsub`/`apithing`/`dlvrsub`/`s0api`/`s0dlvrsub` → `mirror_name`/`mirror_fsub`/`mirror_ext_api`/`mirror_ext_deliver`/`s0_ext_api`/`s0_ext_deliver` and introduced a `validateExternal(...)` helper. These are cosmetic and **decoupled** from the ConsumerSource feature. Recommendation: keep v3's existing names and only add the `consumer` blocks (smaller, lower-risk diff), OR do the full rename to stay byte-aligned with upstream for easier future diffing. Pick one; this plan documents both and defaults to "add-only."
3. **Where validation lives.** Upstream validates in `Builder.build()`. v3 has no builder for these small creators — `ExternalCreator` validates eagerly in ctor+setters. This plan follows the v3 (`ExternalCreator`) convention. Confirm that's the desired API shape (no separate `ConsumerSource.builder()`).

## Risks

- **`Mirror`/`Source` equality.** Verified the four leaf classes don't override `equals`/`hashCode`, so adding the field only to the two base classes is sufficient. If a future refactor adds leaf-level equality, this field must be included there too.
- **Lazy-parse helper in the new test.** The exact call to turn a JSON string into a `LazyJsonValue` must match what `StreamCreatorConfigurationTests` already uses (`LazyJsonParser` is imported). Using the wrong entry point compiles but may not parse identically — copy an existing call site in that file.
- **`readValue(ljv, CONSUMER)` key collision.** The new bare `CONSUMER = "consumer"` constant sits beside `CONSUMER_COUNT`/`CONSUMER_SEQ`/etc.; ensure the literal `"consumer"` isn't already produced anywhere with different semantics (it is not — grep confirmed no bare `consumer` JSON key in v3 today).
- **Required-field reader policy.** See Open decision #1 — picking nullable vs empty-string readers wrong only affects how absent fields surface (null vs ""), not correctness of the round-trip, but should match the project's documented policy.

## Size estimate

- 2 new files (~50 LOC reader, ~100 LOC writer).
- ~30 LOC across 3 modified main files (`ApiConstants` 1 line, `StreamSource` ~9, `StreamSourceCreator` ~20).
- ~10 fixture lines (3 `consumer` blocks) + ~40 LOC of test additions.
- No public-API-breaking changes; no cross-module dependency added (all jetstream-local except the one `core` constant, which already lives in the shared `ApiConstants`).
