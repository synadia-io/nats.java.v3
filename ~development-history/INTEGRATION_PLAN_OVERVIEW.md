# Upstream PR Integration Report

Tracks whether the PRs listed in `todo.md` (section `## PR`) from `nats-io/nats.java` are integrated in this `nats.java.v3` project. v3 uses package `io.synadia.client.*` (not `io.nats.client.*`), so status is judged by symbol/marker presence, not file paths.

Detailed, step-by-step porting instructions for each non-trivial PR live in a per-PR file named `INTEGRATION_PLAN_PR_nnnn.md` (see the **Plan** column). This document is the status overview; the plan files are canonical for the *how*.

| PR | Title | Merged | Status in v3 | Plan |
|---|---|---|---|---|
| [#1561](https://github.com/nats-io/nats.java/pull/1561) | ~~2.14 Stream Configuration (`allow_batched` + `Nats-Schedule-Rollup`)~~ | 2026-05-03 | **Integrated** | none |
| [#1562](https://github.com/nats-io/nats.java/pull/1562) | ~~2.14 Reset Consumer~~ | 2026-05-04 | **Integrated** | `INTEGRATION_PLAN_PR_1562.md` (verified + pushed) |
| [#1563](https://github.com/nats-io/nats.java/pull/1563) | ~~Flow Control `$JS.FC` support~~ | 2026-05-05 | **Integrated** | `INTEGRATION_PLAN_PR_1563.md` (verified + pushed) |
| [#1565](https://github.com/nats-io/nats.java/pull/1565) | ~~2.14 Stream Consumer Source support~~ | 2026-05-06 | **Integrated** | `INTEGRATION_PLAN_PR_1565.md` (verified + pushed) |
| [#1567](https://github.com/nats-io/nats.java/pull/1567) | ~~Reset Consumer Test Flapper~~ | 2026-05-06 | **Integrated** | folded into `INTEGRATION_PLAN_PR_1562.md` |
| [#1568](https://github.com/nats-io/nats.java/pull/1568) | ~~Reset Consumer correct API response & improve test~~ | 2026-05-07 | **Integrated** | folded into `INTEGRATION_PLAN_PR_1562.md` |
| [#1573](https://github.com/nats-io/nats.java/pull/1573) | ~~Handle consume initial subscription failure~~ | 2026-05-11 | **Integrated** | none — `INTEGRATION_PLAN_PR_1573.md` (evidence) |
| [#1578](https://github.com/nats-io/nats.java/pull/1578) | ~~Reconnect Delay Behavior and options cleanup~~ | 2026-06-04 | **Integrated** | `INTEGRATION_PLAN_PR_1578.md` (shipped; superseded by `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md`) |
| [#1580](https://github.com/nats-io/nats.java/pull/1580) | ~~Properly default idle heartbeat during setter~~ | 2026-06-12 | **Integrated** | `INTEGRATION_PLAN_PR_1580.md` (verified + pushed) |

Net: 9 integrated (1561, 1562, 1563, 1565, 1567, 1568, 1573, 1578, 1580) — all of them. PR 1582 was reframed into a standalone redesign — tracked in `RequestBehaviorImprovement.md`, not here. The Reset Consumer cluster (1562 + 1567 + 1568) was one unit of work — now done (`INTEGRATION_PLAN_PR_1562.md`).

---

## PR 1561 — Integrated

All markers present:

- `ALLOW_BATCHED = "allow_batched"` — `core/src/main/java/io/synadia/client/utils/ApiConstants.java:13`
- `NATS_SCHEDULE_ROLLUP_HDR = "Nats-Schedule-Rollup"` — `jetstream/src/main/java/io/synadia/client/impl/JetStreamConstants.java:106`
- `StreamConfiguration.getAllowBatched()` — `jetstream/src/main/java/io/synadia/client/api/StreamConfiguration.java:348`
- `StreamCreator.allowBatched()` builder + field/equals/hashCode/JSON
- Fixtures `"allow_batched": true` in `StreamConfiguration.json` / `StreamInfo.json`; coverage in `ApiFieldsTest` and `StreamCreatorConfigurationTests`.

## PR 1562 — Integrated

"2.14 Reset Consumer" (merged 2026-05-04). Implemented per `INTEGRATION_PLAN_PR_1562.md` (consolidated 1562 + 1568 final shape + 1567 test fix):

- `JSAPI_CONSUMER_RESET = "CONSUMER.RESET.%s.%s"` — `jetstream/.../impl/JetStreamConstants.java:87`
- `JetStreamManagement.resetConsumer(stream, consumer)` / `resetConsumer(stream, consumer, sequence)` — `jetstream/.../impl/JetStreamManagement.java:561, 575`. **Returns `ConsumerInfo`** (the #1568 correction, not the superseded `boolean`); payload `{}` for `sequence < 1`, else `{"seq":n}`; modeled on `unpinConsumer`.
- Integration test `testResetConsumer` — `jetstream/src/test/.../JetStreamManagementTests.java:739`, gated on new `VersionUtils.atLeast2_14()` (`core/src/test/.../VersionUtils.java:55`).

## PR 1563 — Integrated

Flow Control (`$JS.FC`) reply-subject support, ported per `INTEGRATION_PLAN_PR_1563.md`:

- `JS_FC_SUBJECT_PREFIX = "$JS.FC."` — `core/.../utils/NatsConstants.java:118` (alongside the ACK prefix).
- `IncomingMessageFactory` routes a `$JS.ACK.` **or** `$JS.FC.` `replyTo` to `JetStreamMessage` — `core/.../impl/IncomingMessageFactory.java:47`.
- `JetStreamMetaData` accepts `FC` as well as `ACK` for the second token, records it in a `metaType` field (assigned in `parse()`, the lazy v3 path), and exposes it via `getMetaType()` — `core/.../impl/JetStreamMetaData.java:120`.
- Tests: `TestMetaV2FC` constant + `metaType` threaded through `validateMeta` + an FC-routing assertion in `NatsMessageJetStreamMetaDataTests`.

Verified against the upstream diff (FC uses the same token layout as ACK). Additive only; existing invalid-metadata tests unaffected (`$JS.nope.`/`$JS.invalid.` still route to non-JS). Not run through gradle.

## PR 1565 — Integrated

Stream Consumer Source support, ported per `INTEGRATION_PLAN_PR_1565.md`. v2's single `ConsumerSource` became **two** classes mirroring v3's reader/writer split:

- `ConsumerSource` (reader, extends `LazyApiObject`; model `External`) and `ConsumerSourceCreator` (writer/builder, `JsonSerializable`; model `ExternalCreator`, eager validation in ctor + setters via `validateConsumerName`/`validateSubject`).
- `ApiConstants.CONSUMER = "consumer"` (`ApiConstants.java:36`).
- `StreamSource.getConsumerSource()` (`StreamSource.java:85`); `StreamSourceCreator` gets the `consumerSourceCreator` field + `consumerSourceCreator(...)` setter (named to match `externalCreator(...)`) + `getConsumerSourceCreator()` + both copy-ctors + `toJson` (`addField(sb, CONSUMER, …)`) + `equals`/`hashCode`. The four leaf classes don't override equality, so no changes there.
- Tests: `consumer` blocks added to `StreamConfiguration.json` (mirror + both sources, named `<name>_con_name`/`<name>_con_deliver`), validated via a `validateConsumerSource(cs, name)` helper called from the mirror block + `validateSource` (mirrors v2 `StreamConfigurationTests`), and a dedicated `testConsumerSource` (creator getters, JSON round-trip, copy-ctor equals, validation throws, and the `consumerSourceCreator(...)` setter/getter + null-clear).

Took the plan's "add-only" option for the fixture (kept v3's existing `eman`/`apithing`/etc. names rather than doing upstream's cosmetic renames). Reader getters are nullable `readString` (open decision #1). Not run through gradle; `StreamConfiguration.json` is read only by `StreamCreatorConfigurationTests`, so no other test is affected.

## PR 1567 — Integrated (folded into 1562)

"Reset Consumer Test Flapper" (merged 2026-05-06). Test-only stability fix on `testResetConsumer`: read consumer state via `jsm.getConsumerInfo(...)` outside the fetch try-with-resources block. Realized in the consolidated `testResetConsumer` (`JetStreamManagementTests.java:739`).

## PR 1568 — Integrated (folded into 1562)

"Reset Consumer correct API response and improve test" (merged 2026-05-07). The final API shape — `resetConsumer` returns **`ConsumerInfo`** (not `boolean`/`SuccessApiResponse`) — is what was implemented (`JetStreamManagement.java:561, 575`), and the test asserts on the returned `ConsumerInfo`.

## PR 1573 — Integrated

"Handle consume initial subscription failure" (merged 2026-05-11). Single-file change to `NatsMessageConsumer.doSub(boolean first)`: when the initial subscription (`first == true`) throws `JetStreamApiException`/`IOException`, rethrow instead of swallowing it via `resetOnException()`, so the failure propagates out of the `consume()` constructor rather than returning a silently-dead consumer.

v3 already contains the exact guard at `jetstream/src/main/java/io/synadia/client/impl/NatsMessageConsumer.java:150-155` (`if (first) throw e;`), and the surrounding `doSub`/constructor/`pullTerminatedByError` flow matches upstream (differing only in package and `NatsDispatcher` vs `Dispatcher`). No action required. Upstream shipped no test; an optional regression test could assert propagation from a failing initial `consume()`. See `INTEGRATION_PLAN_PR_1573.md` for the evidence.

## PR 1578 — Integrated

**Closed out 2026-08-12.** Everything in the gap below shipped and is committed, then was absorbed and extended by `PLAN_RECONNECT_DELAY_HANDLER_REDESIGN.md` — so the code no longer looks like the upstream shape: the handler is `long getWaitTimeMillis(long round, Options, boolean secure, boolean lameDuckTriggered)`, `Options.getReconnectDelayHandler()` is never null (falls back to `DefaultReconnectDelayHandler.INSTANCE`), a third enum constant `LameDuckAware` is the default, and the reconnect loop calls the handler unconditionally every round instead of branching on the enum. The `@Deprecated` item below is moot: the two legacy property constants were removed outright rather than deprecated. Residual doc/test drift is tracked in `PLAN_RECONNECT_DELAY_FOLLOWUP.md`.

A mix of new behavior (`ReconnectDelayBehavior`), new properties, and deprecation cleanup around `SubjectValidationType`. (Upstream PR was Open when first surveyed; it **merged 2026-06-04** — names/semantics are now final.)

**Already present in v3:** `SubjectValidationType` enum (top-level, not nested in `Options`); `OptionsBuilder.subjectValidationType(...)` (null-safe default `Lenient`); legacy props `PROP_NO_SUBJECT_VALIDATION` / `PROP_STRICT_SUBJECT_VALIDATION`; `ReconnectDelayHandler` + its builder setter; `NatsConnection.reconnectImplConnect()` already invokes the handler between rounds (= upstream's `BeforeSubsequentRounds`). Upstream's deprecated `noSubjectValidation()` / `strictSubjectValidation()` builder methods already don't exist in v3.

**Missing (the gap) — all of this has since shipped; kept for the record:** the `ReconnectDelayBehavior` enum (`BeforeSubsequentRounds`/`BeforeAllRounds`) + builder/options field/getter/copy-ctor wiring; the three new property constants (`PROP_RECONNECT_DELAY_BEHAVIOR`, `PROP_RECONNECT_DELAY_HANDLER_CLASS`, `PROP_SUBJECT_VALIDATION_TYPE`) and their `properties()` parsing (subject-validation prop processed *after* the legacy booleans so it wins); `get(String)` factories on both enums; the pre-first-round handler invocation when behavior is `BeforeAllRounds`; `@Deprecated` on the two legacy property constants; tests. Full steps in `INTEGRATION_PLAN_PR_1578.md`.

## PR 1580 — Integrated

The idle-heartbeat unset defaulting bug: the setters should treat a non-positive input as "unset", but the millis path stored a zero-valued `Duration` instead of clearing the field, so `getIdleHeartbeat()` returned `Duration.ZERO` instead of `null` after `idleHeartbeat(0)`.

Fix: `ConsumerCreator._idleHeartbeat(long)` now assigns `DURATION_UNSET` (= `null` in v3) for a non-positive input instead of `Duration.ZERO` (`ConsumerCreator.java:589`), matching the `Duration` path right above it (which already did). One-token production change; both clearing paths now resolve to `null` and are symmetric. Side effect (desirable, no test relied on the old behavior): `_flowControl(long)` with a zero input now correctly throws, in line with the Duration path. Added `testIdleHeartbeatUnset` (`ConsumerConfigurationTests`) asserting both setter paths clear to `null` on non-positive/`Duration.ZERO`/null input, still set a valid value, and throw for positive-but-below-minimum. Not run through gradle.

---

## Per-PR integration plans

| Plan file | Covers | Effort |
|---|---|---|
| `INTEGRATION_PLAN_PR_1562.md` | 1562 + 1567 + 1568 (Reset Consumer, consolidated) | small |
| `INTEGRATION_PLAN_PR_1563.md` | 1563 (Flow Control `$JS.FC`) | small (~20 LOC) |
| `INTEGRATION_PLAN_PR_1565.md` | 1565 (Consumer Source — 2 new classes) | medium |
| `INTEGRATION_PLAN_PR_1573.md` | 1573 (already integrated — evidence/optional test) | none |
| `INTEGRATION_PLAN_PR_1578.md` | 1578 (Reconnect Delay Behavior + options cleanup — done) | medium |
| `INTEGRATION_PLAN_PR_1580.md` | 1580 (idle-heartbeat `long` setter default) | tiny |

(PR 1582 was reframed into a standalone redesign — `RequestBehaviorImprovement.md` — and is no longer tracked here.)

### Remaining integration

**None — everything surveyed here is integrated.** 1578 was the last one open; it shipped and was closed out 2026-08-12 (see the PR 1578 section above). This overview is finished.
