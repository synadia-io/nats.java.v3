# Integration Plan — PR #1563 (Flow Control $JS.FC support)

> **STATUS: INTEGRATED — verified, committed, and pushed by the user.** `JS_FC_SUBJECT_PREFIX`
> (`NatsConstants.java:118`), FC routing in `IncomingMessageFactory.java:47`, `metaType` field +
> `getMetaType()` in `JetStreamMetaData.java:120`, and FC test coverage in
> `NatsMessageJetStreamMetaDataTests`. Done.

Goal: port upstream `nats-io/nats.java` PR #1563 ("Flow Control $JS.FC support", MERGED 2026-05-05) into `nats.java.v3`. Status: **Not integrated** — none of the FC markers exist in v3.

PR is **Merged** upstream — names/semantics are final.

## Goals

1. New constant `JS_FC_SUBJECT_PREFIX = "$JS.FC."` alongside the existing `JS_ACK_SUBJECT_PREFIX`.
2. `IncomingMessageFactory` must route a message whose `replyTo` starts with **either** `$JS.ACK.` **or** `$JS.FC.` to the JetStream message subclass (so `isJetStream()` is true and metadata can parse).
3. `JetStreamMetaData` must:
   - accept `FC` as a valid second token (`parts[1]`), not just `ACK`;
   - expose the second token via a new `metaType` field + `getMetaType()` accessor (returns `"ACK"` or `"FC"`);
   - on a non-`ACK`/non-`FC` second token, throw `IllegalArgumentException` (v2 behavior) — see Open decision #2 re: v3's current `IllegalStateException` path.
4. Tests: validate FC metadata parses identically to ACK and that `getMetaType()` returns the right token.

## Mapping: upstream file → v3 file

| Upstream (v2) | v3 equivalent |
|---|---|
| `support/NatsJetStreamConstants.java` (`JS_FC_SUBJECT_PREFIX`) | `core/src/main/java/io/synadia/client/utils/NatsConstants.java` (NOT `jetstream/.../JetStreamConstants.java` — the ACK prefix lives in `NatsConstants`) |
| `impl/IncomingMessageFactory.java` | `core/src/main/java/io/synadia/client/impl/IncomingMessageFactory.java` |
| `impl/NatsJetStreamMetaData.java` | `core/src/main/java/io/synadia/client/impl/JetStreamMetaData.java` |
| `test/impl/JetStreamTestBase.java` (test constants) | inlined in `core/src/test/java/io/synadia/client/impl/NatsMessageJetStreamMetaDataTests.java` (v3 keeps these constants in the test class itself, not a shared base) |
| `test/impl/NatsJetStreamMetaDataTests.java` | `core/src/test/java/io/synadia/client/impl/NatsMessageJetStreamMetaDataTests.java` |

Key v3 structural differences from v2 to honor:
- v3 `JetStreamMetaData` parses **lazily** in `parse()` (called from each getter), whereas v2 parses **eagerly** in the constructor. The `metaType` field must therefore be assigned inside `parse()`, and `getMetaType()` must call `parse()` first.
- The class/method are named `JetStreamMetaData` / `getMetaType` (no `NatsJetStream` prefix).
- v3 routes JS messages to `JetStreamMessage` (not `NatsJetStreamMessage`).

## File inventory

### New files (0)
None.

### Modified files (4)
| Path | Why |
|---|---|
| `core/src/main/java/io/synadia/client/utils/NatsConstants.java` | Add `JS_FC_SUBJECT_PREFIX`. |
| `core/src/main/java/io/synadia/client/impl/IncomingMessageFactory.java` | Route `$JS.FC.` replyTo to `JetStreamMessage`. |
| `core/src/main/java/io/synadia/client/impl/JetStreamMetaData.java` | Accept `FC`, add `metaType` field + `getMetaType()`. |
| `core/src/test/java/io/synadia/client/impl/NatsMessageJetStreamMetaDataTests.java` | Add FC test constants + assert `getMetaType()`. |

## Step-by-step

Order chosen so the tree compiles after every step.

### Step 1 — Add the FC prefix constant
File: `core/src/main/java/io/synadia/client/utils/NatsConstants.java`, line 117.

Current:
```java
    String JS_ACK_SUBJECT_PREFIX = "$JS.ACK.";
```
Change to:
```java
    String JS_ACK_SUBJECT_PREFIX = "$JS.ACK.";
    String JS_FC_SUBJECT_PREFIX = "$JS.FC.";
```

### Step 2 — Route FC messages in `IncomingMessageFactory`
File: `core/src/main/java/io/synadia/client/impl/IncomingMessageFactory.java`.

2a. Add the static import (line 6):
```java
import static io.synadia.client.utils.NatsConstants.JS_ACK_SUBJECT_PREFIX;
import static io.synadia.client.utils.NatsConstants.JS_FC_SUBJECT_PREFIX;
```

2b. Widen the branch (line 46):
```java
        else if (replyTo != null && replyTo.startsWith(JS_ACK_SUBJECT_PREFIX)) {
            message = new JetStreamMessage(data);
        }
```
to:
```java
        else if (replyTo != null && (replyTo.startsWith(JS_ACK_SUBJECT_PREFIX) || replyTo.startsWith(JS_FC_SUBJECT_PREFIX))) {
            message = new JetStreamMessage(data);
        }
```
This is what makes `isJetStream()` true for FC messages (v3 `isJetStream()` is `true` only on `JetStreamMessage`; `JetStreamMetaData`'s constructor guards on `isJetStream()`), so without this change an FC message would never reach metadata parsing.

### Step 3 — `JetStreamMetaData`: accept FC + add `metaType`
File: `core/src/main/java/io/synadia/client/impl/JetStreamMetaData.java`.

3a. Add the field after `prefix` (line 17):
```java
    private String prefix;
    private String metaType;
    private String domain;
```
Note: not `final` — v3 fields here are populated lazily in `parse()`, unlike v2's `final` constructor-assigned fields.

3b. Add `metaType` to `toString()` after `prefix` (line 32). `toString()` already calls `parse()` first, so the value is populated:
```java
        return "JetStreamMetaData{" +
            "prefix='" + prefix + '\'' +
            ", metaType='" + metaType + '\'' +
            ", domain='" + domain + '\'' +
```

3c. Update the doc comment block (after line 48) to add the FC form:
```java
    v2 <prefix>.ACK.<domain>.<account hash>.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
    v2 <prefix>.FC.<domain>.<account hash>.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
```

3d. Replace the second-token guard in `parse()` (lines 63-65). Current:
```java
            String[] parts = replyTo.split("\\.");
            if (parts.length < 8 || !"ACK".equals(parts[1])) {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }
```
Change to (mirrors v2's two-stage check):
```java
            String[] parts = replyTo.split("\\.");
            if (parts.length < 8) {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }

            metaType = "ACK".equals(parts[1]) || "FC".equals(parts[1]) ? parts[1] : null;
            if (metaType == null) {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }
```

3e. Update the inline comment in the assignment block (line 91):
```java
                prefix = parts[0];
                // metaType = parts[1], checked and set above
```

3f. Add the accessor near the other getters (after `getDomain()`, before `getStream()`, to keep the parallel-group order matching v2 which places it first):
```java
    /**
     * Get the meta type of this message's reply subject, either {@code ACK} or {@code FC}.
     * @return the meta type
     */
    public String getMetaType() {
        parse();
        return metaType;
    }
```

### Step 4 — Tests
File: `core/src/test/java/io/synadia/client/impl/NatsMessageJetStreamMetaDataTests.java`.

4a. Add FC test constants. v3 keeps the existing `TestMetaV2` name (v2 renamed it to `TestMetaV2ACK`). To minimize churn, keep `TestMetaV2` and add an FC sibling (line 17 area):
```java
    public static final String TestMetaV2 = "$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000.4";
    public static final String TestMetaV2FC = "$JS.FC.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000.4";
```
(Open decision #1: whether to also rename `TestMetaV2` → `TestMetaV2ACK` to match v2. Recommend keeping `TestMetaV2` to avoid touching unrelated call sites at lines 29, 62; just add the FC constant.)

4b. Thread `metaType` through `validateMeta` (lines 60-63 and 71). Change the helper signature:
```java
    private void validateMeta(boolean hasPending, boolean hasDomainHashToken, String metaType, Message msg) {
        JetStreamMetaData meta = msg.metaData();
        assertEquals("test-stream", meta.getStream());
        ...
        // at the end of the method, after the domain/hash block:
        assertEquals(metaType, meta.getMetaType());
    }
```
And update the calls (lines 60-63):
```java
        validateMeta(false, false, "ACK", getTestMessage(TestMetaV0));
        validateMeta(true, false, "ACK", getTestMessage(TestMetaV1));
        validateMeta(true, true, "ACK", getTestMessage(TestMetaV2));
        validateMeta(true, true, "FC", getTestMessage(TestMetaV2FC));
        validateMeta(true, true, "ACK", getTestMessage(TestMetaVFuture));
```

4c. (Optional, matches v2) Add a `getMetaType()` smoke assertion in `testMiscMetaDataCoverage` or a dedicated FC routing test confirming an FC replyTo produces an `isJetStream()` message:
```java
        Message fcMsg = getTestMessage(TestMetaV2FC);
        assertTrue(fcMsg.isJetStream());
        assertEquals("FC", fcMsg.metaData().getMetaType());
```

### Step 5 — Sanity checks
1. `grep -rn "JS_FC_SUBJECT_PREFIX\|getMetaType\|metaType\|\\$JS\\.FC" core/src/main core/src/test --include="*.java"` shows the new symbols.
2. Compile `:core` main + test.
3. Run `*NatsMessageJetStreamMetaDataTests*` — all existing tests plus the new FC assertions pass.
4. Update `INTEGRATION_PLAN_OVERVIEW.md`: flip PR 1563 row to **Integrated** (do not edit shared files as part of this plan — leave for the report owner).

## Open decisions for review

1. **`TestMetaV2` rename.** v2 renamed `TestMetaV2` → `TestMetaV2ACK`. v3 can keep `TestMetaV2` and just add `TestMetaV2FC` to avoid editing unrelated call sites. Plan recommends keeping `TestMetaV2`.
2. **Exception type for a bad second token.** v3's existing test `testInvalidMetaData` asserts `IllegalStateException` for `InvalidMetaNoAck` (`$JS.nope...`) — but that path actually throws from the `JetStreamMetaData` *constructor*'s `isJetStream()` guard (an FC/ACK-prefix routing decision in `IncomingMessageFactory`), not from `parse()`. A `$JS.nope.` replyTo never becomes a `JetStreamMessage`, so `new JetStreamMetaData(...)` throws because `isJetStream()` is false. After this PR, `$JS.FC.` routes to `JetStreamMessage`, but a hypothetical `$JS.zzz.`-with-8+-tokens that somehow reached `parse()` would throw `IllegalArgumentException` (matching v2). Confirm the v3 maintainer wants to keep the existing `IllegalStateException` test for the non-JS-prefix case unchanged (it still holds — that string is not routed to a JS message) and only add `IllegalArgumentException` coverage for malformed FC/ACK subjects. No behavior change is needed to the constructor.
3. **Constant placement.** Upstream put `JS_FC_SUBJECT_PREFIX` in `NatsJetStreamConstants`. v3's `JS_ACK_SUBJECT_PREFIX` lives in core `NatsConstants` (used by `IncomingMessageFactory` in the `core` module). Place the FC constant in the same file for symmetry and to avoid a `core → jetstream` dependency. Confirmed: `jetstream/.../JetStreamConstants.java` does NOT hold the ACK prefix.

## Risks

- **Low risk; isolated.** The only behavioral widening is in `IncomingMessageFactory`: messages with a `$JS.FC.` replyTo now become `JetStreamMessage` instead of `IncomingMessage`. Anything that previously treated such a message as plain (none expected — FC subjects are JetStream-internal) would change type. No production v3 code currently keys off `$JS.FC.`.
- **Lazy-parse subtlety.** `getMetaType()` must call `parse()` (unlike v2 where the field is final and constructor-set). Forgetting `parse()` would return `null` before any other getter is touched. Covered by Step 3f.
- **`equals()` is final in v3 metadata?** — `JetStreamMetaData` has no `equals()` override, so the new field needs no `equals`/`hashCode` update. (Verified: file has only `toString()`.)

## Size estimate

- 0 new files.
- ~12 LOC across 3 main files (1 constant, 1 import + 1 conditional in the factory, ~10 in the metadata class).
- ~8 LOC of test changes (1 constant, helper signature + 1 assertion + threaded `metaType` args).
- No fixture changes. No public-API-breaking changes (purely additive: new constant, new `getMetaType()` method, widened routing).
</content>
</invoke>
