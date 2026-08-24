# Integration Plan — PR #1562 (Reset Consumer cluster: +#1567 +#1568)

> **STATUS: INTEGRATED — verified, committed, and pushed by the user.** `JSAPI_CONSUMER_RESET`
> (`JetStreamConstants.java:87`), both `resetConsumer(...)` overloads returning `ConsumerInfo`
> (`JetStreamManagement.java:561, 575`), `VersionUtils.atLeast2_14()` (`VersionUtils.java:55`), and the
> consolidated `testResetConsumer` (`JetStreamManagementTests.java:739`). Done.

Goal: port the upstream `nats-io/nats.java` **Reset Consumer** feature into `nats.java.v3`. This plan consolidates three merged upstream PRs into one final state:

- **#1562 "2.14 Reset Consumer"** (MERGED 2026-05-04) — the base feature. Adds `resetConsumer(...)` overloads, the `JSAPI_CONSUMER_RESET = "CONSUMER.RESET.%s.%s"` subject constant, the `atLeast2_14` test guard, and the `testResetConsumer` integration test.
- **#1568 "Reset Consumer correct API response and improve test"** (MERGED 2026-05-07) — **the correction that this plan folds in**: changes the return type from `boolean` (`SuccessApiResponse`) to **`ConsumerInfo`** (the server returns the post-reset consumer), and asserts the returned `ConsumerInfo` in the test.
- **#1567 "Reset Consumer Test Flapper"** (MERGED 2026-05-06) — test-only stability fix: read consumer state via `jsm.getConsumerInfo(...)` outside the `try`-with-resources fetch block, and (combined with #1568) use `ackSync` + small `sleep(200)` settle pauses on slow CI.

All three are **MERGED** upstream; the V2 HEAD reflects the final state. **This plan describes the FINAL consolidated behavior** (1562 base + 1568's `ConsumerInfo` return type), so the v3 port should be written directly to that shape — do not implement the intermediate `boolean` version from 1562.

Status carried over from `INTEGRATION_PLAN_OVERVIEW.md`: **Not integrated** in v3 (no `resetConsumer`, no `JSAPI_CONSUMER_RESET` in v3 `core/`/`jetstream/` source; only mentioned in the report doc itself).

## Final upstream API surface (after #1568)

Two overloads on the JSM type:

```java
ConsumerInfo resetConsumer(String streamName, String consumerName) throws IOException, JetStreamApiException;
ConsumerInfo resetConsumer(String streamName, String consumerName, long sequence) throws IOException, JetStreamApiException;
```

- The no-sequence overload delegates: `return resetConsumer(streamName, consumerName, -1);`
- Subject: `String.format(JSAPI_CONSUMER_RESET, streamName, consumerName)` → `CONSUMER.RESET.<stream>.<consumer>`.
- Payload: `sequence < 1` → empty JSON `{}`; otherwise `{"seq":<sequence>}` (no spaces). Upstream uses `JsonUtils.EMPTY_JSON` + ISO-8859-1 bytes; v3 has no `EMPTY_JSON` constant, so use the `"{}"` literal.
- Response: parse the reply as a **`ConsumerInfo`** and `throwOnHasError()` it — the server returns the consumer's post-reset state. (This is the #1568 correction; the original #1562 returned `boolean` from `SuccessApiResponse`.)

## v3 structural mapping (v3 DIFFERS from v2)

| Concern | v2 (upstream) | v3 (target) |
|---|---|---|
| Package | `io.nats.client.*` | `io.synadia.client.*` |
| JSM type | interface `JetStreamManagement` + impl `NatsJetStreamManagement` | **single concrete class** `io.synadia.client.impl.JetStreamManagement` (no interface) — add both overloads here, no `@Override`/`{@inheritDoc}` |
| Subject constants | `support/NatsJetStreamConstants.java` | `jetstream/src/main/java/io/synadia/client/impl/JetStreamConstants.java` |
| Empty-JSON | `JsonUtils.EMPTY_JSON` | no equivalent — use `"{}"` literal |
| Validation | `Validator.validateNotNull` | `validateNotNull` (already statically imported from `io.synadia.client.utils.Validator` at `JetStreamManagement.java:14`) |
| Response type | `ConsumerInfo` (extends `ApiResponse`) | `io.synadia.client.api.ConsumerInfo` (`extends ApiResponse<ConsumerInfo>`, `ApiResponse.java:27`; `throwOnHasError()` returns `T` at `ApiResponse.java:171`) — already in scope via `import io.synadia.client.api.*;` (`JetStreamManagement.java:4`) |

Model the new method **directly on the existing `unpinConsumer`** at `JetStreamManagement.java:542-550`, which is the closest analog (subject-format + raw JSON payload + `makeRequestResponseRequired` + response wrap).

## Integration status of markers (v3)

| Symbol | v3 status | Anchor |
|---|---|---|
| `resetConsumer` | **not found** in `core/`/`jetstream/` source | — |
| `JSAPI_CONSUMER_RESET` | **not found** in source (only in `INTEGRATION_PLAN_OVERVIEW.md`) | — |
| `CONSUMER.RESET` | **not found** in source (only in the report doc) | — |
| `JSAPI_CONSUMER_UNPIN` (template) | present | `JetStreamConstants.java:83-84` |
| `unpinConsumer` (template) | present | `JetStreamManagement.java:542-550` |

Classification: **Not integrated.**

## File inventory

### Modified files (2 main + 1 test)
| Path | Why |
|---|---|
| `jetstream/src/main/java/io/synadia/client/impl/JetStreamConstants.java` | Add `JSAPI_CONSUMER_RESET` constant. |
| `jetstream/src/main/java/io/synadia/client/impl/JetStreamManagement.java` | Add the two `resetConsumer(...)` overloads (final `ConsumerInfo` return). |
| `jetstream/src/test/.../JetStreamManagementTests.java` (or the v3 JSM test home) | Add `testResetConsumer` (after #1567+#1568 consolidation). Confirm the actual v3 test class/path before writing — see Step 3. |

No new files. No interface to touch (v3 has none). No public-API breakage.

## Step-by-step

Order chosen so the tree compiles after every step.

### Step 1 — `JSAPI_CONSUMER_RESET` constant
File: `jetstream/src/main/java/io/synadia/client/impl/JetStreamConstants.java`, immediately after `JSAPI_CONSUMER_UNPIN` (line 84):

```java
    // JSAPI_CONSUMER_RESET is the endpoint to reset a consumer
    String JSAPI_CONSUMER_RESET = "CONSUMER.RESET.%s.%s";
```

### Step 2 — `resetConsumer(...)` overloads
File: `jetstream/src/main/java/io/synadia/client/impl/JetStreamManagement.java`, immediately after `unpinConsumer(...)` (after line 550, inside the class, before the closing brace at line 551).

No new imports needed: `ConsumerInfo` is covered by `import io.synadia.client.api.*;` (line 4), `StandardCharsets` is imported (line 10), `validateNotNull` is statically imported (line 14), `JSAPI_CONSUMER_RESET` is a member of the implemented constants interface like `JSAPI_CONSUMER_UNPIN`.

```java
    /**
     * Reset a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return the current consumer after the reset
     */
    public ConsumerInfo resetConsumer(String streamName, String consumerName) throws IOException, JetStreamApiException {
        return resetConsumer(streamName, consumerName, -1);
    }

    /**
     * Reset a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @param sequence ack floor stream sequence
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return the current consumer after the reset
     */
    public ConsumerInfo resetConsumer(String streamName, String consumerName, long sequence) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_RESET, streamName, consumerName);
        byte[] payload = (sequence < 1 ? "{}" : String.format("{\"seq\":%d}", sequence)).getBytes(StandardCharsets.ISO_8859_1);
        Message resp = makeRequestResponseRequired(subj, payload, getTimeout());
        return new ConsumerInfo(resp).throwOnHasError();
    }
```

Notes:
- **Final return type is `ConsumerInfo`** (folds in #1568). Do NOT use `SuccessApiResponse`/`boolean` — that was the superseded #1562 form. `unpinConsumer` (template) still uses `SuccessApiResponse`; do not copy that part.
- `"{}"` literal stands in for upstream's `JsonUtils.EMPTY_JSON` (v3 has no such constant; verified absent).
- `StandardCharsets.ISO_8859_1` mirrors upstream byte encoding. (`unpinConsumer` uses bare `.getBytes()`; the seq payload is ASCII so either is safe, but match upstream's explicit charset.)
- No `@Override` / `{@inheritDoc}` — v3 has no interface (unlike v2's `NatsJetStreamManagement`).

### Step 3 — Test (`testResetConsumer`, consolidated #1562+#1567+#1568)

First locate v3's JSM integration test class — confirm the path before editing:
```bash
grep -rln "unpinConsumer\|class JetStreamManagementTests" jetstream/src/test --include="*.java"
```
(In v2 the test lives in `src/test/java/io/nats/client/impl/JetStreamManagementTests.java`. The `unpinConsumer` integration test in v3 currently lives in `tdb/io/synadia/client/impl/JetStreamPullTests.java:1451` — so the v3 JSM test home may differ from v2. Place `testResetConsumer` in v3's equivalent JSM-management test class.)

Port the **final** test (after #1568 + #1567), translating to v3 names/APIs. The final-state shape:
- Guard with v3's equivalent of `atLeast2_14` (server ≥ 2.14; upstream impl is `isSameOrNewerThanVersion("2.13.99")`). If v3 has no `atLeast2_14` helper, add it next to the existing `atLeast2_*` helpers in v3's `TestBase`, mirroring upstream `TestBase.java:166+`.
- Create a memory stream + 20 messages; create a named consumer with `ackWait(60s)`.
- Fetch 20, ack the first 5 (`ackSync(Duration.ofSeconds(1))` per #1568); `sleep(200)` settle; assert `numPending==0`, `numAckPending==15` via `jsm.getConsumerInfo(...)` (per #1567 — read from `jsm`, not `ctx`, and outside the fetch `try`).
- `ci = jsm.resetConsumer(stream, consumer);` then assert the **returned** `ci` has `numPending==15`, `numAckPending==0` (per #1568 — assert on the return value, no spaces in the comment-form).
- Re-fetch 15 from seq 6, ack through 10, settle, assert `0/10` via `jsm.getConsumerInfo`.
- `ci = jsm.resetConsumer(stream, consumer, 1);` assert returned `ci` is `20/0`.
- Re-fetch 20 from seq 1, ack all, settle, assert `0/0`.

Key v3 translation points to verify while porting:
- `jsm.getConsumerInfo(stream, consumer)` exists at `JetStreamManagement.java:279`.
- `ConsumerInfo.getNumPending()` / `getNumAckPending()` — confirm v3 getter names on `io.synadia.client.api.ConsumerInfo`.
- ConsumerContext/FetchConsumer fetch API — use v3's equivalent (`NatsConsumerContext`); see existing v3 fetch tests for the idiom.
- `m.metaData().streamSequence()` / `m.ackSync(...)` — confirm v3 `Message` ack + metadata API.

### Step 4 — Sanity spot-checks (do NOT run gradle)

Grep to prove the symbols landed:
```bash
grep -rn "resetConsumer\|JSAPI_CONSUMER_RESET\|CONSUMER.RESET" jetstream/src/main jetstream/src/test --include="*.java"
```
Expect: constant in `JetStreamConstants.java`, two overloads + return-type `ConsumerInfo` in `JetStreamManagement.java`, and `testResetConsumer` in the JSM test class.

Manual review checklist:
1. Return type is `ConsumerInfo` (not `boolean`) on both overloads — #1568 folded in.
2. Payload branch: `< 1` → `"{}"`, else `{"seq":n}` (no space after colon).
3. No `@Override`/`{@inheritDoc}` (no v3 interface).
4. Test reads state via `jsm.getConsumerInfo` outside the fetch `try` (#1567) and asserts on the `resetConsumer` return value (#1568).

### Step 5 — Report update
In `INTEGRATION_PLAN_OVERVIEW.md`, flip the Reset Consumer cluster (1562/1567/1568) rows from **Not integrated** → **Integrated**, citing the final anchors. (Do not edit shared files `todo.md` / this report unless that is your explicit task — per instructions, this plan does not modify them.)

## Open decisions for review

1. **Charset for the seq payload.** Upstream uses `ISO_8859_1`; v3 `unpinConsumer` uses bare `.getBytes()` and other v3 JSM calls use `StandardCharsets.UTF_8`. Payload is pure ASCII so all three are equivalent. Plan matches upstream (`ISO_8859_1`); a maintainer may prefer `UTF_8` for v3 consistency.
2. **`"{}"` literal vs a shared constant.** v3 has no `EMPTY_JSON`. If one is later added, swap the literal.
3. **Test home + `atLeast2_14` helper.** v3's JSM test layout differs from v2 (v3 `unpinConsumer` test is in `tdb/.../JetStreamPullTests.java`). Confirm the correct v3 JSM-management test class and whether an `atLeast2_14` guard helper already exists before adding one.

## Risks

- **Low.** Pure additive feature: one constant, two methods, one test. No interface, no existing-signature change, no public-API break.
- The only correctness subtlety is the **#1568 return-type correction** — implementing the wrong (superseded `boolean`) shape would compile but mis-model the API. The plan pins the final `ConsumerInfo` form.

## Size estimate

- 0 new files.
- ~3 LOC (`JetStreamConstants`) + ~33 LOC (two overloads + javadoc, `JetStreamManagement`).
- ~70 LOC test (+ possibly a ~8 LOC `atLeast2_14` helper in `TestBase`).
- No fixture changes. No public-API-breaking changes.
