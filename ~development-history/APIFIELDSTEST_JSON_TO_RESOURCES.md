# ApiFieldsTest: JSON to resources, and de-duplication against existing tests

All inline JSON is out of `ApiFieldsTest`. Every test that was duplicating an existing test was deleted in favor of that existing test, with the genuine gaps folded into it. `ApiFieldsTest` went from 2357 to 1595 lines.

## What moved where

`ApiFieldsTest` PART 2 (API-only classes) and PART 3 (ApiResponse hierarchy) were almost entirely duplicates of `JsonParsingTests`, `ConsumerInfoJsonTests`, `StreamInfoJsonTests`, `ApiResponseTests`, and `PublishAckTests`. All 21 of those tests are gone. Only `testSubject` remains in PART 2 — it is a pure POJO test with no JSON, and nothing else covers `Subject.compareTo`.

| Deleted from ApiFieldsTest | Now lives in | Gap folded in |
| --- | --- | --- |
| testAccountLimits | JsonParsingTests.testAccountLimits | new testAccountLimitsEmpty |
| testAccountTier | JsonParsingTests.testAccountTier | cached getLimits() |
| testApiStats | JsonParsingTests.testApiStats | new testApiStatsEmpty |
| testClusterInfo | JsonParsingTests.testClusterInfo | cached getReplicas(); new testClusterInfoEmpty |
| testError | JsonParsingTests.testError + 3 variants | new testErrorEmpty, testErrorPredefined |
| testLostStreamData | JsonParsingTests.testLostStreamData | new testLostStreamDataEmpty |
| testMirrorInfo | JsonParsingTests.testMirrorInfo, testMirrorInfoNoError | new testMirrorInfoNegativeActive, testMirrorInfoEmpty |
| testPriorityGroupState | JsonParsingTests.testPriorityGroupState | new testPriorityGroupStateMinimal, testPriorityGroupStateEmpty |
| testReplica | JsonParsingTests.testReplica, testReplicaMinimal | new testReplicaEmpty |
| testSequenceInfo | JsonParsingTests.testSequenceInfo | new testSequenceInfoEmpty |
| testSourceInfo | JsonParsingTests.testSourceInfo, testSourceInfoNoActive | none — already fully covered |
| testStreamAlternate | JsonParsingTests.testStreamAlternate | new testStreamAlternateEmpty |
| testStreamState | JsonParsingTests.testStreamState, testStreamStateMinimal | cached getSubjects()/getSubjectMap(); new testStreamStateEmpty, testStreamStateUnsigned |
| testAccountStatistics | JsonParsingTests.testAccountStatsImpl | cached getApiStats()/getTiers()/getLimits() |
| testConsumerPauseResponse | JsonParsingTests.testConsumerPauseResponse + 2 variants | new testConsumerPauseResponseEmpty |
| testPurgeResponse | JsonParsingTests.testPurgeResponse | new testPurgeResponseFailure, testPurgeResponseEmpty |
| testConsumerInfo | ConsumerInfoJsonTests.testConsumerInfo | getCalculatedPending, lazy-field caching, Message-ctor equivalence; new testConsumerInfoError, testConsumerInfoNoConfig |
| testStreamInfo | StreamInfoJsonTests | new testStreamInfoLazyFieldsAndMinimal (caching, LazyJsonValue-ctor equivalence, minimal json) |
| testApiResponse_baseBehaviors | ApiResponseTests | new testNullMessageAndNoErrorAccessors |
| testSuccessApiResponse | ApiResponseTests.testSuccessApiResponseCoverage | new testSuccessApiResponseError |
| testPublishAck | PublishAckTests | none — already fully covered |

## Data files

Reused for what remains in `ApiFieldsTest` PART 1 (creator round trips), with expectations adjusted to the file's values: `ConsumerLimits.json`, `External.json`, `Mirror.json`, `Placement.json`, `Republish.json`, `Source.json`, `SourceMinimal.json`, `SubjectTransform.json`.

Added, because no file held that data:

- `Empty.json` — `{}`. Serves every empty-defaults case across all the test classes.
- `MirrorInfoNegativeActive.json` — mirrors the existing `SourceInfoNoActive.json` shape.
- `PriorityGroupStateMinimal.json`
- `StreamStateUnsigned.json` — the top-half uint64 case from UINT64_AUDIT.md.
- `ConsumerInfoNoConfig.json`
- `PurgeResponseFailure.json`
- `StreamInfoMinimal.json`

Removed: `PlacementEmpty.json`. Its content was `{}`, identical to the new `Empty.json`; `JsonParsingTests.testPlacementEmpty` now reads `Empty.json`.

`GenericErrorResponse.json` was reused rather than adding a new error fixture, for both the ConsumerInfo error case and the SuccessApiResponse error case.

## Test results

`:jetstream:test` — 390 tests. Two failures, neither from this work:

1. `ConsumerFieldsSetterCoverageTest.consumerCreatorAndConfigurationHaveSameGetters` / `allProtectedSettersExposedInSubclasses` — "ConsumerCreator is missing getter: isPullConsumer(), isPushConsumer()". This comes from the uncommitted addition of `isPushConsumer()`/`isPullConsumer()` to `ConsumerConfiguration`; that test enforces getter parity between `ConsumerCreator` and `ConsumerConfiguration`. Either add the two getters to `ConsumerCreator` or exclude them from the parity check.
2. `JetStreamPushTests.testDeliveryPolicy` — failed once in the full run (`expected: <data-3> but was: <data-1>`), passed on two clean re-runs. Timing-sensitive ByStartTime assertion, unrelated.

Everything in `io.synadia.client.api` passes apart from item 1.
