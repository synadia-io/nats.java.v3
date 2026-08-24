# API JSON Parsing — Consolidated Work List

Aggregated from `ApiJsonParsingStreamInfoAudit.md`, `ApiJsonParsingConsumerInfoAudit.md`, and `ApiJsonParsingGeneralAudit.md`.

Strikethrough items as they're completed. Items grouped by priority.

---

## P0 — Must fix (data bug)

| # | Source | Item |
|---|---|---|
| 1 | ~~ConsumerInfo `deliver_subject` mismatch~~ | ✅ Already aligned — both fixture and test agree on `"deliver.subject"`. Audit was stale. |

---

## P1 — Missing assertions for fields already present in the JSON

| # | Source | Item |
|---|---|---|
| 2 | ~~StreamInfo `StreamState.getSubjectMap()`~~ | ✅ Already asserted (`ApiStreamInfoJsonTests.java:148-153`). |
| 3 | ~~StreamInfo `StreamInfo.getType()`~~ | ✅ Already asserted (`ApiStreamInfoJsonTests.java:26`). |
| 4 | ~~StreamInfo `Placement.hasData()`~~ | ✅ Already asserted (`ApiStreamInfoJsonTests.java:176`). |
| 5 | ~~ConsumerInfo 22 `ConsumerConfiguration` fields~~ | ✅ All asserted (`ApiConsumerInfoJsonTests.java:51-84`); 35 config asserts total. |
| 6 | ~~ConsumerInfo `ClusterInfo.getReplicas()` per-element~~ | ✅ All five fields asserted on both replicas (`ApiConsumerInfoJsonTests.java:98-110`). |

---

## P2 — Missing fields in the JSON (extend fixture + assert)

| # | Source | Item |
|---|---|---|
| 7 | ~~ConsumerInfo `getNumWaiting()` / `isPushBound()`~~ | ✅ In JSON (`ConsumerInfo.json:52,57`) and asserted (`ApiConsumerInfoJsonTests.java:43,48`). |
| 8 | ~~StreamInfo 13 boolean flags~~ | ✅ All 13 in JSON and asserted (`ApiStreamInfoJsonTests.java:61-72`). |
| 9 | ~~StreamInfo `config.mirror` block~~ | ✅ Full `StreamSource` getter coverage (`ApiStreamInfoJsonTests.java:96-106`). |
| 10 | ~~StreamInfo `config.sources`~~ | ✅ Asserted on element[0] (`ApiStreamInfoJsonTests.java:108-118`). |
| 11 | ~~StreamInfo `config.consumer_limits`~~ | ✅ Both fields asserted (`ApiStreamInfoJsonTests.java:90-94`). |
| 12 | ~~StreamInfo `config.republish`~~ | ✅ All three fields asserted (`ApiStreamInfoJsonTests.java:79-83`). |
| 13 | ~~StreamInfo `config.subject_transform`~~ | ✅ Both fields asserted (`ApiStreamInfoJsonTests.java:85-88`). |
| 14 | ~~StreamInfo scalars (description, compression, max_msgs_per_subject, metadata, template_owner, subject_delete_marker_ttl, persist_mode)~~ | ✅ All asserted. |
| 15 | ~~StreamInfo `did_create`~~ | ✅ Asserted (`ApiStreamInfoJsonTests.java:27`). |

---

## P3 — Branch / variant coverage (alternate fixtures)

| # | Source | Item |
|---|---|---|
| 16 | ~~ConsumerInfo `getFilterSubjects()` plural~~ | ✅ Already covered by `testConsumerConfigurationMultipleFilterSubjects` in `ApiConsumerInfoJsonTests`. |
| 17 | ~~ConsumerPauseResponse error-payload~~ | ✅ `testConsumerPauseResponseError` + `ConsumerPauseResponseError.json`. |
| 18 | ~~Error toString branches~~ | ✅ `testErrorApiCodeOnly` / `testErrorCodeOnly` / `testErrorDescriptionOnly` + 3 fixtures; `testError` extended with exact-string assertion. |
| 19 | ~~Placement variants~~ | ✅ `testPlacementClusterOnly` / `testPlacementTagsOnly` / `testPlacementEmptyCluster` / `testPlacementEmpty` + 4 fixtures. |
| 20 | ~~StreamState no-subjects + no-lost~~ | ✅ `testStreamStateMinimal` + `StreamStateMinimal.json`. |
| 21 | ~~PeerInfo/Replica minimal~~ | ✅ `testReplicaMinimal` + `ReplicaMinimal.json`. |
| 22 | ~~StreamSource/Mirror/Source minimal~~ | ✅ `testMirrorMinimal` / `testSourceMinimal` + 2 fixtures. |
| 23 | ~~StreamSourceInfo/MirrorInfo/SourceInfo no-error + active-absent~~ | ✅ `testMirrorInfoNoError` / `testSourceInfoNoActive` + 2 fixtures. |
| 24 | ~~MirrorInfo/SourceInfo optionalInstance branches~~ | ✅ `testMirrorInfo` extended with `optionalInstance(null)` + non-null. `SourceInfo` has no factory — N/A. |
| 25 | ~~AccountStatistics error-payload~~ | ✅ `testAccountStatsImplError` + `AccountStatisticsError.json`. |
| 26 | ~~StreamInfo StreamSourceInfo.getError() populated~~ | ✅ `testStreamInfoMirrorAndSourceErrors` + `StreamInfoWithMirrorError.json`. |
| 27 | ~~StreamInfo ApiResponse populated-error~~ | ✅ `testStreamInfoApiError` + `StreamInfoApiError.json`. |
| 28 | ~~StreamInfo(LazyJsonValue) ctor~~ | ✅ `testStreamInfoFromLazyJsonValue`. |

---

## P4 — Cross-package, lower priority

| # | Source | Item |
|---|---|---|
| 29 | ~~ConsumerListReader paging test~~ | ✅ `io/synadia/client/impl/ConsumerListReaderTests.java` — 6 test methods covering single-page, empty, multi-page paging (with and without items), and error/filter branches. |

---

## Known leftover

✅ Resolved. `ListRequestsTests.testListRequestEngine` (in `io.synadia.client.impl`) uses a `TestListRequestEngine` subclass to exercise `internalNextJson(fieldName, filter)` directly with `hasMore` true/false × filter null/non-null, plus the populated-error constructor branch. Also adds a `testStreamListResponse` happy-path that the prior work didn't cover.
