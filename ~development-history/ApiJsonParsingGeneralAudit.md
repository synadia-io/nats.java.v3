# API JSON Parsing — General Audit

## Summary of additions

Extended `jetstream/src/test/java/io/synadia/client/api/ApiJsonParsingTests.java` with
dedicated, single-class JSON parsing tests. Each new test owns its own fixture JSON
under `jetstream/src/test/resources/data/`. Distinctive small-integer counters and
unique ISO-8601 timestamps are used per fixture to make cross-file confusion obvious.

### New tests added (20)

In alphabetical order — each method has the form `test<ClassName>`:

| Test method | Class under test | Fixture file (new) |
| --- | --- | --- |
| `testAccountLimits` | `AccountLimits` | `AccountLimits.json` |
| `testAccountTier` | `AccountTier` | `AccountTier.json` |
| `testApiStats` | `ApiStats` | `ApiStats.json` |
| `testClusterInfo` | `ClusterInfo` (+ `Replica`) | `ClusterInfo.json` |
| `testConsumerLimits` | `ConsumerLimits` | `ConsumerLimits.json` |
| `testError` | `Error` | `Error.json` |
| `testExternal` | `External` | `External.json` |
| `testLostStreamData` | `LostStreamData` | `LostStreamData.json` |
| `testMirror` | `Mirror` (extends `StreamSource`) | `Mirror.json` |
| `testMirrorInfo` | `MirrorInfo` (extends `StreamSourceInfo`) | `MirrorInfo.json` |
| `testPlacement` | `Placement` | `Placement.json` |
| `testPriorityGroupState` | `PriorityGroupState` | `PriorityGroupState.json` |
| `testReplica` | `Replica` (extends `PeerInfo`) | `Replica.json` |
| `testRepublish` | `Republish` | `Republish.json` |
| `testSequenceInfo` | `SequenceInfo` | `SequenceInfo.json` |
| `testSource` | `Source` (extends `StreamSource`) | `Source.json` |
| `testSourceInfo` | `SourceInfo` (extends `StreamSourceInfo`) | `SourceInfo.json` |
| `testStreamAlternate` | `StreamAlternate` | `StreamAlternate.json` |
| `testStreamState` | `StreamState` (+ `LostStreamData`, `Subject`) | `StreamState.json` |
| `testSubjectTransform` | `SubjectTransform` | `SubjectTransform.json` |

20 new test methods. 20 new JSON fixture files. 0 reused fixture files in the new
additions (each new class got its own self-contained fixture for clarity).

### Existing tests (unchanged, only re-ordered alphabetically)

- `testAccountStatsImpl` (covers `AccountStatistics` + inline `AccountLimits` +
  `AccountTier` + `ApiStats` validation)
- `testConsumerPauseResponse`
- `testConsumerPauseResumeResponse`
- `testPurgeResponse`

## Where every server-parsed API class is tested

| Class | Tested in |
| --- | --- |
| `AccountLimits` | `ApiJsonParsingTests.testAccountLimits` (and inline by `testAccountStatsImpl`) |
| `AccountStatistics` | `ApiJsonParsingTests.testAccountStatsImpl` |
| `AccountTier` | `ApiJsonParsingTests.testAccountTier` (and inline by `testAccountStatsImpl`) |
| `ApiResponse` (abstract) | `ApiResponseTests` |
| `ApiStats` | `ApiJsonParsingTests.testApiStats` (and inline by `testAccountStatsImpl`) |
| `ClusterInfo` | `ApiJsonParsingTests.testClusterInfo` (and via `ApiStreamInfoJsonTests`, `ApiConsumerInfoJsonTests`) |
| `ConsumerConfiguration` | `ConsumerConfigurationTests` |
| `ConsumerInfo` | `ApiConsumerInfoJsonTests` |
| `ConsumerLimits` | `ApiJsonParsingTests.testConsumerLimits` (and via `StreamCreatorConfigurationTests`) |
| `ConsumerPauseResponse` | `ApiJsonParsingTests.testConsumerPauseResponse` and `testConsumerPauseResumeResponse` |
| `Error` | `ApiJsonParsingTests.testError` (and `ApiResponseTests` for error-response behavior) |
| `External` | `ApiJsonParsingTests.testExternal` (and via `ApiStreamInfoJsonTests`, `StreamCreatorConfigurationTests`) |
| `LazyApiObject` (abstract base) | exercised by every concrete `LazyApiObject` subclass test |
| `LostStreamData` | `ApiJsonParsingTests.testLostStreamData` and `testStreamState` (and via `ApiStreamInfoJsonTests`) |
| `MessageInfo` | `MessageInfoTest` |
| `Mirror` | `ApiJsonParsingTests.testMirror` (and via `StreamCreatorConfigurationTests`) |
| `MirrorInfo` | `ApiJsonParsingTests.testMirrorInfo` (and via `ApiStreamInfoJsonTests`) |
| `PeerInfo` (abstract) | exercised via `Replica` |
| `Placement` | `ApiJsonParsingTests.testPlacement` (and via `ApiStreamInfoJsonTests`, `StreamCreatorConfigurationTests`) |
| `PriorityGroupState` | `ApiJsonParsingTests.testPriorityGroupState` (and via `ApiConsumerInfoJsonTests`) |
| `PublishAck` | `PublishAckTests` |
| `PurgeResponse` | `ApiJsonParsingTests.testPurgeResponse` |
| `Replica` | `ApiJsonParsingTests.testReplica` and `testClusterInfo` (and via the StreamInfo / ConsumerInfo tests) |
| `Republish` | `ApiJsonParsingTests.testRepublish` (and via `StreamCreatorConfigurationTests`) |
| `SequenceInfo` | `ApiJsonParsingTests.testSequenceInfo` (and via `ApiConsumerInfoJsonTests`) |
| `Source` | `ApiJsonParsingTests.testSource` (and via `StreamCreatorConfigurationTests`) |
| `SourceInfo` | `ApiJsonParsingTests.testSourceInfo` (and via `ApiStreamInfoJsonTests`) |
| `StreamAlternate` | `ApiJsonParsingTests.testStreamAlternate` (and via `ApiStreamInfoJsonTests`) |
| `StreamConfiguration` | `StreamCreatorConfigurationTests` |
| `StreamInfo` | `ApiStreamInfoJsonTests` |
| `StreamSource` (abstract) | exercised via `Mirror` and `Source` |
| `StreamSourceInfo` (abstract) | exercised via `MirrorInfo` and `SourceInfo` |
| `StreamState` | `ApiJsonParsingTests.testStreamState` (and via `ApiStreamInfoJsonTests`) |
| `Subject` | exercised via `StreamState` (in `testStreamState` and `ApiStreamInfoJsonTests`) — not constructed directly from `LazyJsonValue` |
| `SubjectTransform` | `ApiJsonParsingTests.testSubjectTransform` (and via `ApiStreamInfoJsonTests`, `StreamCreatorConfigurationTests`) |
| `SuccessApiResponse` | `ApiResponseTests.testSuccessApiResponseCoverage` |

## Classes not directly tested

| Class | Why no direct test |
| --- | --- |
| `AbstractListReader` | Package-private, abstract; `process(Message)` is package-private and only accessible from `io.synadia.client.impl`. Exercised in production code paths via `ConsumerListReader` / `StreamListReader`. |
| `ConsumerListReader` | Public class, but `process(Message)` (the JSON-parse entry point) is package-private; cannot be invoked from `io.synadia.client.api` test package. Would need a sibling test file inside `io.synadia.client.impl`. |
| `StreamListReader` | Package-private class entirely; not visible outside `io.synadia.client.impl`. |
| `StringListReader` | Package-private abstract helper; same constraints as above. |
| `ListRequestEngine` | Public class, but both constructors (no-arg and `Message`) are package-private. Same package-isolation issue. |
| `MessageDeleteRequest`, `MessageGetRequest` | Client-side request objects — they serialize OUT to the server, they do not parse server JSON in. Out of scope. |
| `Subject` | Not constructed from a `LazyJsonValue` directly; it is materialized inside `StreamState.getSubjects()` from a `{subject:count}` map. Coverage flows through `testStreamState`. |

## Recommendations for additional test variants

The "basic happy path" is covered. The following would meaningfully add coverage:

1. **ConsumerPauseResponse**: the existing tests cover the "paused" and "resumed" (server-state-`false`) variants. A third variant where the server returned an error
   payload (so `hasError()` is true) would round it out — verify that `isPaused()` returns
   `false`, `getPauseUntil()` is the default time, `getPauseRemaining()` is null.

2. **Error**: cover all three `toString()` branches:
   - both code and apiErrorCode set (current `testError` covers `[apiErrorCode]` format),
   - code set, apiErrorCode == NOT_SET (`description (code)` format),
   - code == NOT_SET (just `description`).
   The toString rendering changes per branch. Consider parameterized variants or
   additional JSON fixtures named `ErrorCodeOnly.json`, `ErrorDescriptionOnly.json`.

3. **Placement**: the current fixture exercises `cluster + tags`. Useful variants:
   - cluster only (no tags) — `hasData()` true, `getTags()` null,
   - tags only (no cluster) — `hasData()` true, `getCluster()` null,
   - empty cluster string — `getCluster()` returns null (the implementation strips empty),
   - neither — `hasData()` false.

4. **StreamState**: the fixture uses the subject-count map representation. The other
   variant the server emits is when there are no subjects at all (`num_subjects` 0,
   no `subjects` key) — verify `getSubjects()` returns empty list, `getSubjectMap()`
   returns empty map. Likewise an unloaded "no `lost`" variant — `getLostStreamData()`
   returns null.

5. **PeerInfo / Replica**: cover the "missing optional fields" variant — `active` not
   present should default `getActive()` to `Duration.ZERO`, etc.

6. **StreamSource / Mirror / Source**: cover the "minimal" variant — only `name`
   present — to confirm null/zero defaults for everything else.

7. **StreamSourceInfo / MirrorInfo / SourceInfo**: cover the "no error" path
   (`getError()` returns null) and the "active not present / negative" path
   (`getActive()` returns null per the `< 0` guard in `StreamSourceInfo.getActive()`).

8. **List response paging**: a `ConsumerListReader` test (in
   `io.synadia.client.impl`) parsing `ConsumerListResponse.json` and a paged
   continuation `ListResponsePage1.json` / `ListResponsePage2.json` would cover
   `ListRequestEngine`'s `hasMore` / `nextJson` logic, which is currently uncovered.

9. **MirrorInfo / SourceInfo error subobject**: confirmed via `testMirrorInfo` and
   `testSourceInfo`. A variant where `error` is absent (so `getError()` returns
   null) would round out the optional-instance code path.

10. **AccountStatistics**: the existing test covers the "happy path" plus an empty
    JSON `{}` defaults case. A third variant with an error payload would cover the
    error path of `ApiResponse`.

## Inadequacies / minor notes on already-covered areas

- `ApiStreamInfoJsonTests` and `StreamCreatorConfigurationTests` test `Mirror`,
  `Source`, `MirrorInfo`, `SourceInfo` etc. transitively through their parent
  objects, but they do not exercise the `optionalInstance(null)` factory branches
  for these classes. Worth adding (one-liner asserts).
- `ConsumerConfiguration`'s test file is `ConsumerConfigurationTests`; spot-check
  that its asserts cover every parseable field on the class. (Not re-audited here
  per the instructions to skip already-covered classes.)
- `StreamConfiguration`'s test file is `StreamCreatorConfigurationTests`; same
  note. (Not re-audited.)
