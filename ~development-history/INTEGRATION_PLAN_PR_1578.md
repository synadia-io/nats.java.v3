# Integration Plan — PR #1578 (Reconnect Delay Behavior + options cleanup)

**State: COMPLETE (2026-08-12) — every step below shipped and is committed; verified against the tree, `:core:compileJava` + `:core:compileTestJava` green.** The two "open decisions" were both resolved in the direction this plan recommended: camelCase property names, and `Options.reconnectDelayBehavior()` with no `get` prefix. Step 3a is moot — `PROP_NO_SUBJECT_VALIDATION` / `PROP_STRICT_SUBJECT_VALIDATION` were removed outright rather than deprecated, so there was nothing left to mark. Step 9's overview flip is the only item that was never done, and it is handled by the follow-up below.

**Absorbed and extended by `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`** (also in `z-claude-done/`), which took this plan's enum further: `ReconnectDelayHandler` became `long getWaitTimeMillis(long round, Options, boolean secure, boolean lameDuckTriggered)`, `DefaultReconnectDelayHandler.INSTANCE` made the getter never-null, a third constant `LameDuckAware` became the default, and the reconnect loop stopped branching on the enum entirely — so **step 6 below no longer exists in the code**, replaced by an unconditional per-round handler call that lets the handler decide. Not related to `PLAN_FORCE_RECONNECT_READER_STOP.md`, which is a different subject (the `forceReconnectImpl` reader/writer stop race) despite both touching `NatsConnection.java`.

Residual drift left behind by that redesign — stale javadoc, a `getWaitTime`/`getWaitTimeMillis` mismatch in `MIGRATION_GUIDE.md`, the `lameDuckTriggered` consume contract, and the missing connection-side LDM tests — is tracked in **`PLAN_RECONNECT_DELAY_FOLLOWUP.md`**.

---

**Original plan below, unchanged.**

Goal: port the still-missing parts of upstream `nats-io/nats.java` PR #1578 into `nats.java.v3`. Status carried over from `INTEGRATION_PLAN_OVERVIEW.md` (**Partially integrated** — most underlying machinery already present in v3).

PR is **Open** upstream — re-check names/semantics before final integration.

## Goals

1. New `ReconnectDelayBehavior` enum with `BeforeSubsequentRounds` (current behavior, default) and `BeforeAllRounds` (new — invoke handler before round 0 too).
2. Property-driven configuration:
   - `PROP_SUBJECT_VALIDATION_TYPE` — single enum-name property; precedence over the two legacy booleans when all three are set.
   - `PROP_RECONNECT_DELAY_BEHAVIOR` — selects the new enum.
   - `PROP_RECONNECT_DELAY_HANDLER_CLASS` — reflection-instantiate a `ReconnectDelayHandler`.
3. Static `get(String)` factories on `SubjectValidationType` and `ReconnectDelayBehavior` (case-insensitive name match, default for null/unknown).
4. Hook the new behavior into `NatsConnection.reconnectImplConnect()`.
5. `@Deprecated` on the two legacy property constants (legacy builder methods are already gone in v3).
6. Tests.

## File inventory

### New files (2)
| Path | Purpose |
|---|---|
| `core/src/main/java/io/synadia/client/ReconnectDelayBehavior.java` | New top-level enum (mirrors `SubjectValidationType` placement, **not** upstream's nested-in-Options form). |
| `core/src/test/java/io/synadia/client/utils/CoverageReconnectDelayHandler.java` | Test-only concrete `ReconnectDelayHandler` for the class-name property test. Mirrors `CoverageServerPool` style. |

### Modified files (6)
| Path | Why |
|---|---|
| `core/src/main/java/io/synadia/client/SubjectValidationType.java` | Add `get(String)` static factory. |
| `core/src/main/java/io/synadia/client/OptionsProperties.java` | Add three new property constants, `@Deprecated` the two legacy ones. |
| `core/src/main/java/io/synadia/client/OptionsBuilder.java` | New field + setter + properties-parser hook + copy-constructor entry. |
| `core/src/main/java/io/synadia/client/Options.java` | New `final` field + constructor assign + getter. |
| `core/src/main/java/io/synadia/client/impl/NatsConnection.java` | Insert pre-first-round invocation when behavior is `BeforeAllRounds`. |
| `core/src/test/java/io/synadia/client/OptionsTests.java` | Extend `testPropertiesSubjectValidationType`, add `testReconnectDelayBehavior` and `testPropertyReconnectDelayHandlerClass`. |

## Step-by-step

Order is chosen so the tree compiles after every step (no broken intermediate state). Run a build after each step.

### Step 1 — `SubjectValidationType.get(String)`
File: `core/src/main/java/io/synadia/client/SubjectValidationType.java` (currently lines 1–20).

Add inside the enum body, after the final constant `Strict;`:
```java
/**
 * Resolve a {@link SubjectValidationType} from a string (case-insensitive name match).
 * Returns {@link #Lenient} if the value is null or does not match any constant.
 *
 * @param value the string value
 * @return the matching type, or {@link #Lenient} as the default
 */
public static SubjectValidationType get(String value) {
    if (value != null) {
        for (SubjectValidationType svt : SubjectValidationType.values()) {
            if (svt.name().equalsIgnoreCase(value)) {
                return svt;
            }
        }
    }
    return Lenient;
}
```

### Step 2 — New `ReconnectDelayBehavior` enum
Create `core/src/main/java/io/synadia/client/ReconnectDelayBehavior.java`:
```java
package io.synadia.client;

/**
 * Controls when the {@link ReconnectDelayHandler} is invoked during reconnect attempts.
 */
public enum ReconnectDelayBehavior {
    /**
     * Invoke the reconnect delay only before subsequent rounds, never before the first round.
     * The first round of reconnect attempts runs immediately; the delay applies between rounds
     * after a full round has been attempted and failed. This is the historical default.
     * {@link ReconnectDelayHandler#getWaitTime(long)} will only be called with
     * {@code totalTries} greater than or equal to 1.
     */
    BeforeSubsequentRounds,
    /**
     * Invoke reconnect delay behavior before each full round of reconnect attempts.
     * {@link ReconnectDelayHandler#getWaitTime(long)} will be called with
     * {@code totalTries} greater than or equal to 0.
     */
    BeforeAllRounds;

    /**
     * Resolve a {@link ReconnectDelayBehavior} from a string (case-insensitive name match).
     * Returns {@link #BeforeSubsequentRounds} if the value is null or does not match any constant.
     *
     * @param value the string value
     * @return the matching behavior, or {@link #BeforeSubsequentRounds} as the default
     */
    public static ReconnectDelayBehavior get(String value) {
        if (value != null) {
            for (ReconnectDelayBehavior rdb : ReconnectDelayBehavior.values()) {
                if (rdb.name().equalsIgnoreCase(value)) {
                    return rdb;
                }
            }
        }
        return BeforeSubsequentRounds;
    }
}
```

(No copyright header — matches `SubjectValidationType.java` and `CoverageServerPool.java`.)

### Step 3 — Property constants in `OptionsProperties`
File: `core/src/main/java/io/synadia/client/OptionsProperties.java`.

3a. Mark the two legacy constants `@Deprecated` (at lines 138 and 142):
```java
/**
 * Property used to configure noSubjectValidation. {@value}
 * @deprecated use {@link #PROP_SUBJECT_VALIDATION_TYPE} with value {@code None} instead
 */
@Deprecated
String PROP_NO_SUBJECT_VALIDATION = PFX + "noSubjectValidation";
/**
 * Property used to configure strictSubjectValidation. {@value}
 * @deprecated use {@link #PROP_SUBJECT_VALIDATION_TYPE} with value {@code Strict} instead
 */
@Deprecated
String PROP_STRICT_SUBJECT_VALIDATION = PFX + "strictSubjectValidation";
```

3b. Add `PROP_SUBJECT_VALIDATION_TYPE` immediately after `PROP_STRICT_SUBJECT_VALIDATION` (around line 142):
```java
/**
 * Property used to set the {@link SubjectValidationType}. {@value} The value is the case-insensitive
 * name of a {@link SubjectValidationType} constant (e.g. {@code None}, {@code Lenient}, {@code Strict}).
 * Unrecognized or missing values fall back to {@link SubjectValidationType#Lenient}.
 * Processed after {@link #PROP_NO_SUBJECT_VALIDATION} and {@link #PROP_STRICT_SUBJECT_VALIDATION},
 * so when set this property takes precedence over those legacy boolean properties.
 */
String PROP_SUBJECT_VALIDATION_TYPE = PFX + "subjectValidationType";
```

3c. Add the two reconnect-delay constants. v3 already uses camelCase for related newer properties (`hostnameResolveMode`, `reconnectBufSize`) — pick a placement near `PROP_RECONNECT_JITTER_TLS` (line 101). Suggested camelCase names that are consistent with the rest of `OptionsProperties`:
```java
/**
 * Property used to set the class name for the {@link ReconnectDelayHandler} implementation. {@value}
 * The class must have a public no-arg constructor.
 */
String PROP_RECONNECT_DELAY_HANDLER_CLASS = PFX + "reconnectDelayHandlerClass";
/**
 * Property used to set the {@link ReconnectDelayBehavior}. {@value} The value is the case-insensitive
 * name of a {@link ReconnectDelayBehavior} constant (e.g. {@code BeforeSubsequentRounds}, {@code BeforeAllRounds}).
 * Unrecognized or missing values fall back to {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
 */
String PROP_RECONNECT_DELAY_BEHAVIOR = PFX + "reconnectDelayBehavior";
```
**Open decision:** upstream uses dotted names (`reconnect.delay.handler.class`, `reconnect.delay.behavior`). v3 has been consistent in camelCase here — recommend matching the v3 file. Flag for review.

### Step 4 — `OptionsBuilder`
File: `core/src/main/java/io/synadia/client/OptionsBuilder.java`.

4a. Add the field, next to `reconnectDelayHandler` (after line 105):
```java
ReconnectDelayBehavior reconnectDelayBehavior = ReconnectDelayBehavior.BeforeSubsequentRounds;
```

4b. Wire properties parsing in the `properties()` method.

After the legacy boolean lines (currently 204–205):
```java
booleanPropertyIfTrue(props, PROP_NO_SUBJECT_VALIDATION, b -> subjectValidationType = SubjectValidationType.None);
booleanPropertyIfTrue(props, PROP_STRICT_SUBJECT_VALIDATION, b -> subjectValidationType = SubjectValidationType.Strict);
stringProperty(props, PROP_SUBJECT_VALIDATION_TYPE, s -> this.subjectValidationType = SubjectValidationType.get(s));   // NEW — must come after the booleans to take precedence
```

After the reconnect-related block (currently ends around line 220, after `longProperty(props, PROP_RECONNECT_BUF_SIZE, ...)`):
```java
classnameProperty(props, PROP_RECONNECT_DELAY_HANDLER_CLASS,
                  o -> this.reconnectDelayHandler = (ReconnectDelayHandler) o);
stringProperty(props, PROP_RECONNECT_DELAY_BEHAVIOR,
               s -> this.reconnectDelayBehavior = ReconnectDelayBehavior.get(s));
```
(`classnameProperty` helper is already used at line 187 — no new helper needed.)

4c. Add the fluent setter — model on `subjectValidationType(...)` (line 336). Place near `reconnectDelayHandler(...)` at line 910:
```java
/**
 * Set the {@link ReconnectDelayBehavior} that controls when the
 * {@link ReconnectDelayHandler} is invoked during reconnect attempts. Defaults to
 * {@link ReconnectDelayBehavior#BeforeSubsequentRounds}. A null value resets to
 * {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
 *
 * @param reconnectDelayBehavior the behavior
 * @return the Builder for chaining
 */
public OptionsBuilder reconnectDelayBehavior(ReconnectDelayBehavior reconnectDelayBehavior) {
    this.reconnectDelayBehavior = reconnectDelayBehavior == null
        ? ReconnectDelayBehavior.BeforeSubsequentRounds
        : reconnectDelayBehavior;
    return this;
}
```

4d. Add the entry in the `OptionsBuilder(Options o)` copy-constructor — right after the existing `this.reconnectDelayHandler = o.reconnectDelayHandler;` at line 1380:
```java
this.reconnectDelayBehavior = o.reconnectDelayBehavior;
```

### Step 5 — `Options`
File: `core/src/main/java/io/synadia/client/Options.java`.

5a. Add the `final` field, after line 100 (next to `reconnectDelayHandler`):
```java
final ReconnectDelayBehavior reconnectDelayBehavior;
```

5b. Assign in the `Options(OptionsBuilder b)` constructor, after line 229 (`this.reconnectDelayHandler = b.reconnectDelayHandler;`):
```java
this.reconnectDelayBehavior = b.reconnectDelayBehavior;
```

5c. Add the getter near `getReconnectDelayHandler()` (line 525). Match the existing naming style of the file (`getReconnectDelayHandler()` uses `get*` prefix, while `subjectValidationType()` does not — pick a hill to die on; this plan matches upstream's `reconnectDelayBehavior()` no-prefix style for consistency with the upstream API surface):
```java
/**
 * The reconnect delay behavior. Defaults to {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
 * See {@link OptionsBuilder#reconnectDelayBehavior(ReconnectDelayBehavior) reconnectDelayBehavior()} in the builder doc.
 * @return the behavior, never null
 */
public ReconnectDelayBehavior reconnectDelayBehavior() {
    return this.reconnectDelayBehavior;
}
```

### Step 6 — `NatsConnection.reconnectImplConnect()`
File: `core/src/main/java/io/synadia/client/impl/NatsConnection.java`, lines 420–430.

Current code:
```java
int totalRounds = 0;
NatsUri first = null;
NatsUri cur;
while ((cur = serverPool.nextServer()) != null) {
    if (first == null) {
        first = cur;
    }
    else if (first.equals(cur)) {
        // went around the pool an entire time
        invokeReconnectDelayHandler(++totalRounds);
    }
```

Change to:
```java
int totalRounds = 0;
NatsUri first = null;
NatsUri cur;
while ((cur = serverPool.nextServer()) != null) {
    if (first == null) {
        first = cur;
        if (options.reconnectDelayBehavior() == ReconnectDelayBehavior.BeforeAllRounds) {
            invokeReconnectDelayHandler(0);
        }
    }
    else if (first.equals(cur)) {
        // went around the pool an entire time
        invokeReconnectDelayHandler(++totalRounds);
    }
```

Add the import at the top of `NatsConnection.java`:
```java
import io.synadia.client.ReconnectDelayBehavior;
```

### Step 7 — Test helper
Create `core/src/test/java/io/synadia/client/utils/CoverageReconnectDelayHandler.java`. Match v3 style (no header, no top-level Javadoc — mirrors `CoverageServerPool`):
```java
package io.synadia.client.utils;

import io.synadia.client.ReconnectDelayHandler;

import java.time.Duration;

/**
 * Concrete ReconnectDelayHandler used to test setting it via PROP_RECONNECT_DELAY_HANDLER_CLASS.
 * Requires a public no-arg constructor for reflective instantiation.
 */
public class CoverageReconnectDelayHandler implements ReconnectDelayHandler {
    @Override
    public Duration getWaitTime(long totalTries) {
        return Duration.ofMillis(totalTries);
    }
}
```

### Step 8 — Tests
File: `core/src/test/java/io/synadia/client/OptionsTests.java`.

8a. Extend `testPropertiesSubjectValidationType` (line 653) — append before the final `assertEquals(SubjectValidationType.Lenient, ...)` on line 679:
```java
// PROP_SUBJECT_VALIDATION_TYPE — case-insensitive enum name match
props.clear();
props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "None");
o = new OptionsBuilder(props).build();
assertEquals(SubjectValidationType.None, o.subjectValidationType());

props.clear();
props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "lenient");
o = new OptionsBuilder(props).build();
assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());

props.clear();
props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "STRICT");
o = new OptionsBuilder(props).build();
assertEquals(SubjectValidationType.Strict, o.subjectValidationType());

// Unknown value → default Lenient
props.clear();
props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "bogus");
o = new OptionsBuilder(props).build();
assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());

// Precedence: PROP_SUBJECT_VALIDATION_TYPE wins over the two legacy booleans
props.clear();
props.setProperty(PROP_NO_SUBJECT_VALIDATION, "true");
props.setProperty(PROP_STRICT_SUBJECT_VALIDATION, "true");
props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "None");
o = new OptionsBuilder(props).build();
assertEquals(SubjectValidationType.None, o.subjectValidationType());
```
Plus a parallel `assertEquals(SubjectValidationType.X, SubjectValidationType.get("x"))` set in a fresh `testSubjectValidationTypeGet` method (covers the static factory directly: `null`, `""`, all three names lower/upper, and "bogus" → `Lenient`).

8b. Add `testReconnectDelayBehavior` near `testReconnectDelayHandler` (line 1248):
```java
@Test
public void testReconnectDelayBehavior() {
    // Default
    Options o = new OptionsBuilder().build();
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, o.reconnectDelayBehavior());

    // Explicit setter
    o = new OptionsBuilder().reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds).build();
    assertEquals(ReconnectDelayBehavior.BeforeAllRounds, o.reconnectDelayBehavior());

    // null resets to default
    o = new OptionsBuilder().reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds)
                            .reconnectDelayBehavior(null).build();
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, o.reconnectDelayBehavior());

    // Property — case-insensitive
    Properties props = new Properties();
    props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "BeforeAllRounds");
    o = new OptionsBuilder(props).build();
    assertEquals(ReconnectDelayBehavior.BeforeAllRounds, o.reconnectDelayBehavior());

    props.clear();
    props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "beforesubsequentrounds");
    o = new OptionsBuilder(props).build();
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, o.reconnectDelayBehavior());

    // Unknown value → default
    props.clear();
    props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "bogus");
    o = new OptionsBuilder(props).build();
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, o.reconnectDelayBehavior());

    // Copy-constructor preserves the value
    Options copy = new OptionsBuilder(o).reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds).build();
    assertEquals(ReconnectDelayBehavior.BeforeAllRounds, copy.reconnectDelayBehavior());
    Options copyOfCopy = new OptionsBuilder(copy).build();
    assertEquals(ReconnectDelayBehavior.BeforeAllRounds, copyOfCopy.reconnectDelayBehavior());

    // Static factory direct coverage
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, ReconnectDelayBehavior.get(null));
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, ReconnectDelayBehavior.get(""));
    assertEquals(ReconnectDelayBehavior.BeforeAllRounds, ReconnectDelayBehavior.get("beforeallrounds"));
    assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, ReconnectDelayBehavior.get("bogus"));
}
```

8c. Add `testPropertyReconnectDelayHandlerClass`:
```java
@Test
public void testPropertyReconnectDelayHandlerClass() {
    Properties props = new Properties();
    props.setProperty(PROP_RECONNECT_DELAY_HANDLER_CLASS,
                      CoverageReconnectDelayHandler.class.getCanonicalName());

    Options o = new OptionsBuilder(props).build();
    ReconnectDelayHandler handler = o.getReconnectDelayHandler();
    assertNotNull(handler);
    assertEquals(7, handler.getWaitTime(7).toMillis());
}
```

8d. Imports to add at the top of `OptionsTests.java`:
```java
import io.synadia.client.ReconnectDelayBehavior;
import io.synadia.client.utils.CoverageReconnectDelayHandler;
// PROP_SUBJECT_VALIDATION_TYPE and PROP_RECONNECT_DELAY_BEHAVIOR and PROP_RECONNECT_DELAY_HANDLER_CLASS
// should be picked up by the existing static import of OptionsProperties.*
```

### Step 9 — Sanity build / spot-checks

Run after each step where feasible. Final spot-checks:
1. `./gradlew :core:compileJava :core:compileTestJava` — both succeed.
2. `./gradlew :core:test --tests "*OptionsTests*"` — `testPropertiesSubjectValidationType`, the new `testReconnectDelayBehavior`, `testPropertyReconnectDelayHandlerClass`, and the existing `testReconnectDelayHandler` all pass.
3. Grep proves the new symbols land in v3:
   ```bash
   grep -rn "ReconnectDelayBehavior\|PROP_RECONNECT_DELAY_BEHAVIOR\|PROP_RECONNECT_DELAY_HANDLER_CLASS\|PROP_SUBJECT_VALIDATION_TYPE\|BeforeAllRounds" core/src/main core/src/test --include="*.java"
   ```
4. Confirm the existing reconnect-loop test (`testReconnectDelayHandler`) still passes — the new `BeforeAllRounds` branch must not be exercised when the behavior is the default.
5. Update `INTEGRATION_PLAN_OVERVIEW.md`: flip PR 1578 row from **Partially integrated** → **Integrated**, and remove the corresponding plan block at the bottom (or replace it with an "Integration notes" block citing the final symbol locations).

## Open decisions for review

1. **Property naming**: camelCase (matches v3 file) vs dotted (matches upstream). Plan recommends camelCase for v3 consistency — confirm with the maintainer.
2. **Getter naming**: `reconnectDelayBehavior()` (no `get` prefix, matches upstream + v3's `subjectValidationType()`) vs `getReconnectDelayBehavior()` (matches v3's `getReconnectDelayHandler()`). Plan picks the no-prefix form for API symmetry with upstream.
3. **Wait for upstream merge.** PR 1578 is still Open. If names/semantics change before merge, re-sync this plan against the final diff before applying.

## Risks

- **Reconnect loop is hot/critical code.** The change to `reconnectImplConnect()` is one if-branch but it does add a handler invocation point. Verify no test asserts that the handler is *never* called with `totalTries == 0` (would now fail under `BeforeAllRounds` only — but should not change default-mode behavior at all).
- **Reflection-loaded class.** `classnameProperty` requires a public no-arg constructor on the supplied class. `CoverageReconnectDelayHandler` satisfies that. If the upstream helper throws on missing constructor (rather than silently falling back), the test will surface that — fine.
- **`@Deprecated` in OptionsProperties** propagates to anywhere those constants are imported. Audit `core/src/main` and `core/src/test` for current uses; add `@SuppressWarnings("deprecation")` only where deliberate.

## Size estimate

- ~2 new files (≈20 LOC each).
- ~140 LOC added across 5 modified files (Options + OptionsBuilder + OptionsProperties + NatsConnection + SubjectValidationType).
- ~80 LOC of tests.
- No fixture changes. No public-API-breaking changes.
