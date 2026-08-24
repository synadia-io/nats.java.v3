# Integration Plan — PR #1580 (Properly default idle heartbeat during setter)

> **STATUS: INTEGRATED — verified, committed, and pushed by the user.** `ConsumerCreator._idleHeartbeat(long)` now
> assigns `DURATION_UNSET` (= `null`) instead of `Duration.ZERO` for non-positive input
> (`ConsumerCreator.java:589`), matching the Duration path. Added `testIdleHeartbeatUnset` in
> `ConsumerConfigurationTests`. Took the v3-symbol form (`DURATION_UNSET`) over upstream's literal
> `null`, and kept `<= 0` (identical to upstream's `< 1` for the long path). Not run through gradle.

Goal: port upstream `nats-io/nats.java` PR #1580 into `nats.java.v3`. PR is **MERGED** upstream (2026-06-12).

Status: **Partially integrated.** The `Duration` setter path already defaults to `null` in v3 (via `DURATION_UNSET`, which v3 defines as `null`). The `long`/millis setter path still defaults to `Duration.ZERO` instead of `null` — this is the remaining divergence to fix.

## What the upstream bug was

In `ConsumerConfiguration.Builder`, the idle-heartbeat setters are meant to treat a "zero / unset / non-positive" input as "clear the value" (i.e. leave it unset). Before #1580, both setters assigned `DURATION_UNSET` for the non-positive case, and in upstream v2 `DURATION_UNSET = Duration.ZERO`. So after `idleHeartbeat(0)` (or `idleHeartbeat(Duration.ZERO)`), `getIdleHeartbeat()` returned `Duration.ZERO` rather than `null`. That is a "set to zero" value, not an "unset" value — it is not equivalent to never having called the setter, and it can serialize/compare differently than the absent field. PR #1580 changes both setters to assign `null` (true unset) and tightens the boundary test from `<= DURATION_UNSET_LONG` (`<= 0`) to `< 1` (numerically identical for the `long` path; for nanos `< 1` means "less than one nanosecond", also non-positive). Upstream tests now `assertNull(c.getIdleHeartbeat())` after `idleHeartbeat(Duration.ZERO)` and `idleHeartbeat(0)`.

Upstream diff (both setters, `src/main/java/io/nats/client/api/ConsumerConfiguration.java`):
```diff
-                if (nanos <= DURATION_UNSET_LONG) {
-                    this.idleHeartbeat = DURATION_UNSET;
+                if (nanos < 1) {
+                    this.idleHeartbeat = null;
...
-            if (idleHeartbeatMillis <= DURATION_UNSET_LONG) {
-                this.idleHeartbeat = DURATION_UNSET;
+            if (idleHeartbeatMillis < 1) {
+                this.idleHeartbeat = null;
```

## v3 status / evidence

In v3 the idle-heartbeat setters do **not** live on `ConsumerConfiguration` (which is a read-only reader: `jetstream/src/main/java/io/synadia/client/api/ConsumerConfiguration.java:181` only has `getIdleHeartbeat()`). They live on the shared base builder `ConsumerCreator`:

- `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java:569` — `_idleHeartbeat(Duration)`:
  ```java
  if (nanos <= 0) {
      this.idleHeartbeat = DURATION_UNSET;   // DURATION_UNSET == null in v3 → ALREADY correct
  }
  ```
- `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java:587` — `_idleHeartbeat(long)`:
  ```java
  if (idleHeartbeatMillis <= 0) {
      this.idleHeartbeat = Duration.ZERO;    // BUG: should be null
  }
  ```

Key v3 difference from v2: `core/src/main/java/io/synadia/client/utils/ApiUtils.java:24` defines `public static final Duration DURATION_UNSET = null;` (v2 had `DURATION_UNSET = Duration.ZERO`). `DURATION_UNSET` is in scope inside `ConsumerCreator` through `import static io.synadia.client.impl.JetStreamApiUtils.*` (`JetStreamApiUtils extends ApiUtils`). So the `Duration`-path assignment `this.idleHeartbeat = DURATION_UNSET` is already `= null` and matches the merged upstream behavior — only the spelling differs from upstream's literal `null`.

The **only functional gap** is the `long`/millis path at line 589, which hardcodes `Duration.ZERO`. After `idleHeartbeat(0)` v3 returns `Duration.ZERO`, whereas upstream (post-#1580) returns `null`.

v3 stores idleHeartbeat as a `Duration` field (`ConsumerCreator.java:75 protected @Nullable Duration idleHeartbeat;`), not millis — so no Duration→millis conversion concern here.

There is currently **no v3 test** asserting the `idleHeartbeat(0)` / `idleHeartbeat(Duration.ZERO)` → unset contract (grep of `jetstream/src/test/.../ConsumerConfigurationTests.java` shows only positive-value and a single `assertNull` for a default-unset ordered creator at line 450). This coverage should be added as part of the port.

## File inventory

### Modified files (2)
| Path | Why |
|---|---|
| `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java` | Fix the `long`-path default (`Duration.ZERO` → `null`); optionally normalize the `Duration`-path spelling to match. |
| `jetstream/src/test/java/io/synadia/client/api/ConsumerConfigurationTests.java` | Add the upstream-equivalent unset-contract assertions for both setters. |

No new files. No public-API change (return type and signatures unchanged; only the stored value for the zero/unset case changes from `Duration.ZERO` to `null`).

## Step-by-step

### Step 1 — Fix the millis setter
File: `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java:587`.

Current:
```java
protected void _idleHeartbeat(long idleHeartbeatMillis) {
    if (idleHeartbeatMillis <= 0) {
        this.idleHeartbeat = Duration.ZERO;
    }
    else if (idleHeartbeatMillis < MIN_IDLE_HEARTBEAT_MILLIS) {
        throw new IllegalArgumentException("Idle Heartbeat must be greater than or equal to " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
    }
    else {
        this.idleHeartbeat = Duration.ofMillis(idleHeartbeatMillis);
    }
}
```
Change the first branch body to assign null (matching upstream's intent and the Duration path):
```java
    if (idleHeartbeatMillis < 1) {
        this.idleHeartbeat = null;
    }
```
(`< 1` mirrors upstream exactly; `<= 0` is numerically identical for `long` — keep `< 1` for a literal upstream match.)

### Step 2 — (Optional, recommended) normalize the Duration setter spelling
File: `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java:569`.

Current line 576 is `this.idleHeartbeat = DURATION_UNSET;` which already equals `null` in v3. To match upstream literally and avoid a future foot-gun if `DURATION_UNSET` is ever redefined, change to:
```java
    long nanos = idleHeartbeat.toNanos();
    if (nanos < 1) {
        this.idleHeartbeat = null;
    }
```
This is behavior-preserving today (`DURATION_UNSET == null`). **Open decision** below — may be left as-is if the project prefers the `DURATION_UNSET` symbol.

### Step 3 — Verify `_flowControl` still throws correctly
File: `jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java:599` and `:607`.

`_flowControl(Duration)` and `_flowControl(long)` both call `_idleHeartbeat(...)` then check `if (idleHeartbeat == null) throw ...`. Before the fix, the millis path stored `Duration.ZERO` for a zero input, so `_flowControl(0)` would NOT throw (idleHeartbeat was non-null ZERO) and would incorrectly set `flowControl = true` with a zero heartbeat. After the fix the millis path stores `null`, so `_flowControl(0)` now correctly throws "Idle Heartbeat must set with flow control..." — this brings the millis flow-control path into line with the Duration path. This is a desirable side effect; confirm no existing test asserts the old (buggy) non-throwing behavior for `flowControl(0)`. (Grep shows no such test today.)

### Step 4 — Tests
File: `jetstream/src/test/java/io/synadia/client/api/ConsumerConfigurationTests.java`.

Add unset-contract coverage on a concrete creator (e.g. `PullConsumerCreator`/`PushConsumerCreator` — pick whichever the surrounding tests already use). Mirror the upstream assertions:
```java
// idleHeartbeat unset contract (PR #1580): non-positive input clears to null
cc = new PullConsumerCreator();
cc.idleHeartbeat((Duration) null);
assertNull(cc.getIdleHeartbeat());

cc = new PullConsumerCreator();
cc.idleHeartbeat(Duration.ZERO);
assertNull(cc.getIdleHeartbeat());          // was the Duration path — already null in v3

cc = new PullConsumerCreator();
cc.idleHeartbeat(0L);
assertNull(cc.getIdleHeartbeat());          // the fixed millis path

cc = new PullConsumerCreator();
cc.idleHeartbeat(MIN_IDLE_HEARTBEAT_MILLIS + 1);
assertEquals(Duration.ofMillis(MIN_IDLE_HEARTBEAT_MILLIS + 1), cc.getIdleHeartbeat());

assertThrows(IllegalArgumentException.class,
    () -> new PullConsumerCreator().idleHeartbeat(MIN_IDLE_HEARTBEAT_MILLIS - 1));
assertThrows(IllegalArgumentException.class,
    () -> new PullConsumerCreator().idleHeartbeat(Duration.ofMillis(MIN_IDLE_HEARTBEAT_MILLIS - 1)));
```
Adjust the concrete creator type and any builder-fluent vs setter style to match the existing test conventions in this file. Use `ConsumerCreator.MIN_IDLE_HEARTBEAT_MILLIS` for the constant. Keep test groups in the existing order of the file.

### Step 5 — Spot-checks (do NOT run gradle here per project rule; for the integrator)
1. Confirm both setters: `grep -n "idleHeartbeat = null\|idleHeartbeat = Duration.ZERO\|idleHeartbeat = DURATION_UNSET" jetstream/src/main/java/io/synadia/client/api/ConsumerCreator.java` — expect no remaining `Duration.ZERO`.
2. Compile `:jetstream`, run `*ConsumerConfigurationTests*`.
3. Confirm the ordered-creator default heartbeat (`AbstractOrderedConsumerCreator.java:47` calls `_idleHeartbeat(DEFAULT_ORDERED_HEARTBEAT)` with a positive value) is unaffected — `ConsumerConfigurationTests.java:515` still expects `Duration.ofMillis(DEFAULT_ORDERED_HEARTBEAT)`.

## Open decisions for review

1. **Spelling of the cleared value.** Use literal `null` (matches upstream merged code) vs keep `DURATION_UNSET` (the v3 symbol, which equals `null`). Recommend literal `null` in both branches for upstream parity and to remove the indirection, but this is cosmetic since `DURATION_UNSET == null` in v3.
2. **`flowControl(0)` now throws.** The fix changes `_flowControl(long)` from silently accepting zero to throwing. This is the correct/consistent behavior and matches the Duration path, but it is technically a behavior change on the millis flow-control path — confirm acceptable (no caller relies on the old no-throw behavior).
3. **Boundary literal.** Upstream uses `< 1`; v3 currently uses `<= 0`. Identical for `long`; recommend adopting `< 1` for a literal upstream match, but `<= 0` is equally correct if the project prefers it.

## Risks

- **Low.** The Duration path already produces the correct (`null`) result in v3; only the millis path and a side-effect in `_flowControl(long)` change. Field is already `@Nullable Duration`, so downstream null-handling (serialization at `ConsumerCreator.java:190 addFieldAsNanos`, equality at `:724`, hashCode at `:750`) already accommodates null.
- Watch for any existing test/fixture that round-trips `idleHeartbeat(0)` and currently expects a zero-valued (non-null) Duration — none found in the jetstream test tree today, but re-grep before applying.

## Size estimate

- 1–2 line change in `ConsumerCreator._idleHeartbeat(long)` (plus an optional 1-line cosmetic change in `_idleHeartbeat(Duration)`).
- ~10–15 LOC of test assertions.
- No new files, no API-signature changes, no fixture changes.
