# Plan — Round-trip equivalence for API DTOs

You asked whether round-trip was already done. **Partially yes.** The current state and the
gap are documented here, plus a concrete test-by-test plan to close the gap.

## What "round-trip" means in this codebase

Two distinct round-trip flavours:

1. **Creator → JSON → Reader → Creator** — the *writer* round-trip. Build a `FooCreator`, call
   `toJson()`, parse with `new Foo(parse(json))`, call `new FooCreator(foo)`, and assert the
   two creators are equal. Validates that nothing is lost or transformed across the
   serialize → deserialize → re-build path.
2. **JSON → Reader → JSON → Reader** — the *reader* round-trip. Start with a known JSON
   string, parse, re-serialize via `reader.toJson()`, parse again, assert the two readers
   are equal. Validates that the reader is fully derived from its underlying `LazyJsonValue`
   and doesn't add or lose information on re-emit. Because `LazyApiObject.equals` already
   compares the wrapped `ljv` exactly, this collapses to "is the re-emitted JSON byte-identical
   or value-equivalent?" — useful when readers cache derived state.

## What exists today

### Covered (or partially covered)

| Type | Test file | Test name(s) | What it does |
|---|---|---|---|
| `External` / `ExternalCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `externalRoundTrip` | In-memory Creator → JSON → DTO → Creator. Added 2026-06-09. |
| `Republish` / `RepublishCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `republishRoundTrip` | Same. Added 2026-06-09. |
| `SubjectTransform` / `SubjectTransformCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `subjectTransformRoundTrip` | Same. Added 2026-06-09. |
| `Placement` / `PlacementCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `placementRoundTrip`, `placementRoundTripEmptyClusterNormalized` | Two variants — populated + `emptyAsNull` edge case. Added 2026-06-09. |
| `Mirror` / `MirrorCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `mirrorRoundTrip` | Includes nested `ExternalCreator` + `SubjectTransformCreator`. Added 2026-06-09. |
| `Source` / `SourceCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `sourceRoundTrip` | Includes nested external + multiple transforms. Added 2026-06-09. |
| `ConsumerLimits` / `ConsumerLimitsCreator` | `ApiCreatorsToDtoRoundTripTests.java` | `consumerLimitsRoundTrip`, `consumerLimitsRoundTripEmpty` | Populated + all-defaults. Added 2026-06-09. |
| `ConsumerConfiguration` (consumer creators, half-trip) | `ApiCreatorsToDtoRoundTripTests.java` | `pullConsumerCreatorJsonRoundTrip`, `pushConsumerCreatorJsonRoundTrip`, `pullOrderedConsumerCreatorJsonRoundTrip`, `pushOrderedConsumerCreatorJsonRoundTrip` | Half-trip (Creator → JSON → ConsumerConfiguration → JSON) only — see "Half-trip note" below. Added 2026-06-09. |
| `StreamConfiguration` / `StreamCreator` (in-memory) | `ApiCreatorsToDtoRoundTripTests.java` | `streamCreatorRoundTrip` | In-memory complement to the live-server version. Added 2026-06-09. |
| `StreamConfiguration` / `StreamCreator` (live) | `StreamCreatorConfigurationTests.java` | `testRoundTrip` + helpers | Loads `StreamConfiguration.json` fixture, builds a `StreamCreator` from it, mutates, re-serializes, re-parses. End-to-end against a live server in `runInSharedCustom(...)`. |
| `Mirror`, `Source` (reader-only paths) | `StreamCreatorConfigurationTests.java` | Inside `testRoundTrip` block at lines 211–227 | `assertEquals(m1, new Mirror(LazyJsonParser.parseUnchecked(m1.toJson())));` — true reader round-trip. |
| `External` (reader-only paths) | `StreamCreatorConfigurationTests.java` | Lines 243–251 | Same shape. |
| `Source` (creator round-trip, live) | `StreamCreatorConfigurationTests.java` | Line 187 | `new Source(LazyJsonParser.parseUnchecked(originalCreator.toJson()));` — partial. |
| `ApiResponse` shape | `ApiResponseTests.java` | Reader-side smoke test | Constructs from JSON, doesn't assert round-trip. |
| `ConsumerInfo` JSON shape | `ConsumerInfoJsonTests.java` | Reader-side smoke test | Parse + accessor checks; not a round-trip. |
| `StreamInfo` JSON shape | `StreamInfoJsonTests.java` | Reader-side smoke test | Same. |
| `MessageInfo` | `MessageInfoTest.java` | Reader-side smoke test | Same. |

### Half-trip note for consumer creators

Pull / Push / PullOrdered / PushOrdered consumer creators don't yet have from-reader
constructors accepting a `ConsumerConfiguration`. The half-trip tests above validate:

```java
PullConsumerCreator c1 = new PullConsumerCreator().durable("...").maxBatch(50);
ConsumerConfiguration dto = new ConsumerConfiguration(parseUnchecked(c1.toJson()));
assertJsonEquivalent(c1.toJson(), dto.toJson());
```

This proves the JSON survives parse + re-emit through the DTO unchanged (structurally —
field order in `LazyJsonValue`'s emit is not stable, so equality is via parsed-map equals).
It does **not** validate the from-reader constructor path because that ctor doesn't exist
yet. To close to a full round-trip, add
`PullConsumerCreator(ConsumerConfiguration)` /
`PushConsumerCreator(ConsumerConfiguration)` /
`PullOrderedConsumerCreator(ConsumerConfiguration)` /
`PushOrderedConsumerCreator(ConsumerConfiguration)` constructors, then upgrade those
four tests to the full Creator → DTO → Creator shape (the existing test methods carry
TODO comments pointing at this).

### Not covered (gap) — reader-side only

The Creator-side gap items were all closed by `ApiCreatorsToDtoRoundTripTests` (added
2026-06-09). The remaining items are reader-only DTOs that have no creator pair — those
belong in `RoundTripReaderTests` (still unwritten):

| Type | Why it should be tested | Severity |
|---|---|---|
| `ClusterInfo`, `PeerInfo`, `Replica` | Server-response readers; no creator pair, but reader round-trip is still meaningful. | Should add |
| `AccountStatistics`, `AccountTier`, `ApiStats`, `AccountLimits` | Same — server-response only. | Should add |
| `MessageInfo` | Server response. Round-trip would verify the `subject`/`seq`/`time` required fields are stable. | **Should add** |
| `PublishAck` | Server response. Pre-throw at construction time, so round-trip is straightforward. | Should add |
| `PurgeResponse`, `ConsumerPauseResponse`, `SuccessApiResponse` | Server responses. Quick wins. | Should add |
| `StreamInfo`, `ConsumerInfo` | Sub-objects (`StreamConfiguration`, `ConsumerConfiguration`, `StreamState`, `SequenceInfo`, `ClusterInfo`). | Should add |
| `StreamAlternate`, `StreamSourceInfo`, `MirrorInfo`, `SourceInfo`, `LostStreamData` | Server-response readers. | Should add |
| `Error` | Read at construction. | Should add |
| `PriorityGroupState` | Reader-only. | Should add |
| `SequenceInfo` | Used inside `ConsumerInfo`. | Should add |
| `StreamState` | Used inside `StreamInfo`. | Should add |

## Proposed file layout

One new test file per category, mirroring the existing `Tests.java` style:

```
jetstream/src/test/java/io/synadia/client/api/
    RoundTripCreatorTests.java        — creator → JSON → reader → creator → equals
    RoundTripReaderTests.java         — reader → JSON → reader → equals (via LazyApiObject.equals)
```

Two files keeps the suite browsable. Both use table-driven test methods so adding a new pair
is a 3-line edit.

### `RoundTripCreatorTests.java` skeleton

```java
class RoundTripCreatorTests {
    @Test void republishRoundTrip() {
        RepublishCreator c1 = new RepublishCreator("src.>", "dest.>", true);
        Republish r = new Republish(LazyJsonParser.parseUnchecked(c1.toJson()));
        RepublishCreator c2 = new RepublishCreator(r);   // assumes the from-reader ctor exists; add if not
        assertEquals(c1, c2);
    }
    @Test void subjectTransformRoundTrip() { /* same shape */ }
    @Test void placementRoundTrip() { /* covers emptyAsNull normalisation */ }
    @Test void externalRoundTrip() { /* already covered in StreamCreatorConfigurationTests but worth a focused test */ }
    @Test void mirrorRoundTrip() { /* via StreamSourceCreator */ }
    @Test void sourceRoundTrip() { /* via StreamSourceCreator */ }
    @Test void consumerLimitsRoundTrip() { /* no-required-field optional-everywhere case */ }
    @Test void streamCreatorRoundTrip() { /* duplicates the existing live-server test — add an in-memory variant */ }

    // Consumer family — F-bounded hierarchy, many setters, easy to break
    @Test void pullConsumerCreatorRoundTrip() { /* exercise every setter */ }
    @Test void pullOrderedConsumerCreatorRoundTrip() { /* same */ }
    @Test void pushConsumerCreatorRoundTrip() { /* same */ }
    @Test void pushOrderedConsumerCreatorRoundTrip() { /* same */ }
}
```

### `RoundTripReaderTests.java` skeleton

```java
class RoundTripReaderTests {
    /** Reusable helper: parse, re-emit, re-parse, assert equal. */
    static <T extends LazyApiObject> void assertReaderRoundTrip(T r, Function<LazyJsonValue, T> ctor) {
        T r2 = ctor.apply(LazyJsonParser.parseUnchecked(r.toJson()));
        assertEquals(r, r2);
    }

    @Test void republishReader()         { assertReaderRoundTrip(parseFixture("republish.json", Republish::new), Republish::new); }
    @Test void subjectTransformReader()  { /* same */ }
    @Test void placementReader()         { /* same */ }
    @Test void priorityGroupStateReader(){ /* would surface defect #6 */ }
    @Test void peerInfoReader()          { /* via Replica subclass; would surface defect #7 */ }
    @Test void clusterInfoReader()       { /* nested PeerInfo list */ }
    @Test void accountLimitsReader()     { /* no required strings, exercise numeric/boolean defaults */ }
    @Test void accountTierReader()       { /* nested AccountLimits — would surface defect #10 */ }
    @Test void apiStatsReader()          { }
    @Test void sequenceInfoReader()      { }
    @Test void streamAlternateReader()   { }
    @Test void streamStateReader()       { }
    @Test void streamSourceInfoReader()  { /* and Mirror/Source via inheritance */ }
    @Test void lostStreamDataReader()    { }
    @Test void errorReader()             { /* would surface defect #11 */ }
    @Test void messageInfoReader()       { /* would surface defect #12 */ }
    @Test void publishAckReader()        { }
    @Test void purgeResponseReader()     { }
    @Test void streamInfoReader()        { /* would surface defect #13 */ }
    @Test void consumerInfoReader()      { /* would surface defect #14 */ }
    @Test void accountStatisticsReader() { /* would surface defect #15 */ }
    @Test void consumerPauseResponseReader() { }
}
```

## Fixture corpus

The existing `StreamConfiguration.json` resource is the only sizeable fixture today. The
plan adds small JSON fixtures (one per reader) under
`jetstream/src/test/resources/api-round-trip/`:

```
api-round-trip/
    republish.json
    subject_transform.json
    placement.json
    priority_group_state.json
    peer_info.json
    cluster_info.json
    account_limits.json
    account_tier.json
    api_stats.json
    sequence_info.json
    stream_alternate.json
    stream_state.json
    stream_source_info.json
    lost_stream_data.json
    error.json
    message_info.json
    publish_ack.json
    purge_response.json
    stream_info.json
    consumer_info.json
    account_statistics.json
    consumer_pause_response.json
```

Each fixture should be **schema-complete** — every field the schema declares, populated with
a non-default sample value, so the round-trip exercise actually compares meaningful data
rather than passing trivially on a default-everywhere blob.

Suggested source: hand-write small files (most are <30 lines), or extract real samples by
running `nats stream info ... --json` against a live server with each feature configured.

## Synergy with the required-fields audit

The reader round-trip tests will **automatically surface** several of the open defects in
`AUDIT_REQUIRED_FIELDS.md`:

| Round-trip test | Surfaces defect |
|---|---|
| `priorityGroupStateReader` | #6 — `getGroup()` returns null on missing |
| `peerInfoReader` (via `Replica`) | #7 — `getName()` returns null on missing; #8 — `getActive()` Duration |
| `accountTierReader` | #10 — `getLimits()` non-`@Nullable` for required sub-object |
| `errorReader` | #11 — `getDescription()` nullability |
| `messageInfoReader` | #12 — needs survey |
| `streamInfoReader` | #13 — required sub-object behaviour |
| `consumerInfoReader` | #14 — required field behaviour |
| `accountStatisticsReader` | #15 — required sub-object behaviour |

This makes the round-trip suite a regression net for the audit's open items: closing each
test typically closes a defect.

## Implementation order

1. **Write `RoundTripReaderTests` first.** Lower scope (no creator wiring), broader coverage,
   and immediately validates the defect list from the required-fields audit. Each test is
   ~5 lines + a fixture.
2. **Write `RoundTripCreatorTests` second.** Some creators don't yet have from-reader
   constructors (verify per pair); add them where missing. The existing
   `StreamCreatorConfigurationTests.testRoundTrip` stays as the "live server" version.
3. **Close defects #6, #7, #11 and any latent ones #8–#15 surfaces** by reading the failures
   from step 1 and fixing in `AUDIT_REQUIRED_FIELDS.md`.

## What this plan does NOT do

- **Server-driven round-trip** for every type. The existing `StreamCreator.testRoundTrip`
  is the template; adding server-driven variants for every type is a separate, larger
  scope (would roughly triple the test runtime).
- **Schema validation.** Comparing the emitted JSON against the upstream JSON schema is
  a separate audit (would catch additional shape issues but requires hooking up a JSON
  Schema validator in tests).
- **Numeric required-field convention overhaul.** Discussed in
  `AUDIT_REQUIRED_FIELDS.md` under "Discussion items"; out of scope here.
