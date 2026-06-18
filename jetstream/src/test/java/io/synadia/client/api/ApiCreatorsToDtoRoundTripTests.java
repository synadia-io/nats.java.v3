package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Creator → JSON → DTO → Creator round-trip tests.
 * Closes the "Should add" gap items in {@code AUDIT_ROUND_TRIP_PLAN.md} for every
 * creator/DTO pair that has a from-reader constructor today.
 * <p>
 * Each test:
 * <ol>
 *   <li>builds a meaningfully-populated {@code *Creator},
 *   <li>serializes it via {@code toJson()},
 *   <li>parses the JSON back into the matching DTO,
 *   <li>rebuilds the {@code *Creator} from the DTO via its from-reader constructor,
 *   <li>asserts the rebuilt creator's JSON (and the creator instance) equals the original.
 * </ol>
 * <p>
 * Consumer creators (PullConsumerCreator / PushConsumerCreator / PullOrderedConsumerCreator /
 * PushOrderedConsumerCreator) do not yet have from-reader constructors that accept a
 * {@code ConsumerConfiguration}. Until those are added, the consumer tests below cover only
 * the half-trip: {@code Creator → JSON → ConsumerConfiguration → JSON}, asserting the JSON
 * survives a parse/re-emit through the DTO.
 * <p>
 * Test methods are ordered alphabetically by the object name they exercise.
 */
public class ApiCreatorsToDtoRoundTripTests extends TestBase {

    // ----------------------------------------------------------------------------------------------------
    // Full round-trip: Creator → JSON → DTO → Creator (alpha by object name)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void consumerLimitsRoundTrip() {
        ConsumerLimitsCreator c1 = new ConsumerLimitsCreator()
            .inactiveThreshold(Duration.ofSeconds(30))
            .maxAckPending(500);
        ConsumerLimits dto = new ConsumerLimits(LazyJsonParser.parseUnchecked(c1.toJson()));
        ConsumerLimitsCreator c2 = new ConsumerLimitsCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void consumerLimitsRoundTripEmpty() {
        // Both fields default/unset; trivial round-trip
        ConsumerLimitsCreator c1 = new ConsumerLimitsCreator();
        ConsumerLimits dto = new ConsumerLimits(LazyJsonParser.parseUnchecked(c1.toJson()));
        ConsumerLimitsCreator c2 = new ConsumerLimitsCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void externalRoundTrip() {
        ExternalCreator c1 = new ExternalCreator("$JS.API.CONSUMER.>", "deliver.subject");
        External dto = new External(LazyJsonParser.parseUnchecked(c1.toJson()));
        ExternalCreator c2 = new ExternalCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void mirrorRoundTrip() {
        MirrorCreator c1 = new MirrorCreator("mirror-stream")
            .startSequence(100)
            .filterSubject("mirror.filter.>")
            .externalCreator(new ExternalCreator("$JS.API.>", "deliver.>"))
            .subjectTransforms(
                new SubjectTransformCreator("from.>", "to.>"));
        Mirror dto = new Mirror(LazyJsonParser.parseUnchecked(c1.toJson()));
        MirrorCreator c2 = new MirrorCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void placementRoundTrip() {
        PlacementCreator c1 = new PlacementCreator()
            .cluster("east-1")
            .tags("ssd", "nvme");
        Placement dto = new Placement(LazyJsonParser.parseUnchecked(c1.toJson()));
        PlacementCreator c2 = new PlacementCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void placementRoundTripEmptyClusterNormalized() {
        // emptyAsNull normalization: empty cluster should round-trip as absent
        PlacementCreator c1 = new PlacementCreator()
            .cluster("")
            .tags(List.of("only-tag"));
        Placement dto = new Placement(LazyJsonParser.parseUnchecked(c1.toJson()));
        PlacementCreator c2 = new PlacementCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void republishRoundTrip() {
        RepublishCreator c1 = new RepublishCreator("src.>", "dest.>", true);
        Republish dto = new Republish(LazyJsonParser.parseUnchecked(c1.toJson()));
        RepublishCreator c2 = new RepublishCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void sourceRoundTrip() {
        SourceCreator c1 = new SourceCreator("source-stream")
            .startSequence(200)
            .filterSubject("source.filter.>")
            .externalCreator(new ExternalCreator("$JS.API.>", null))
            .subjectTransforms(
                new SubjectTransformCreator("a.>", "b.>"),
                new SubjectTransformCreator("c.>", "d.>"));
        Source dto = new Source(LazyJsonParser.parseUnchecked(c1.toJson()));
        SourceCreator c2 = new SourceCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    @Test
    public void streamCreatorRoundTrip() {
        // In-memory complement to StreamCreatorConfigurationTests.testRoundTrip (which exercises
        // a live server). Same round-trip path, no NATS connection required.
        StreamCreator c1 = new StreamCreator("test-stream")
            .description("a test stream")
            .subjects("orders.>", "events.>")
            .retentionPolicy(RetentionPolicy.WorkQueue)
            .maxConsumers(10)
            .maxMessages(1_000_000)
            .maxBytes(1_073_741_824L)
            .maxAge(Duration.ofHours(24))
            .storageType(StorageType.File)
            .replicas(3)
            .placementCreator(new PlacementCreator().cluster("east-1").tags("ssd"))
            .republishCreator(new RepublishCreator("orders.>", "audit.>", false))
            .subjectTransformCreator(new SubjectTransformCreator("in.>", "out.>"))
            .consumerLimitsCreator(new ConsumerLimitsCreator().maxAckPending(500))
            .sourceCreators(
                new SourceCreator("upstream")
                    .startSequence(1)
                    .filterSubject("upstream.>"));

        StreamConfiguration dto = new StreamConfiguration(LazyJsonParser.parseUnchecked(c1.toJson()));
        StreamCreator c2 = new StreamCreator(dto);
        // StreamCreator.equals compares Java-side state that may differ across construction
        // paths even when the JSON is byte-identical; JSON equivalence is the meaningful contract.
        assertJsonEquivalent(c1.toJson(), c2.toJson());
    }

    @Test
    public void subjectTransformRoundTrip() {
        SubjectTransformCreator c1 = new SubjectTransformCreator("src.>", "dest.>");
        SubjectTransform dto = new SubjectTransform(LazyJsonParser.parseUnchecked(c1.toJson()));
        SubjectTransformCreator c2 = new SubjectTransformCreator(dto);
        assertCreatorRoundTrip(c1, c2);
    }

    // ----------------------------------------------------------------------------------------------------
    // Consumer creators: half-trip only (alpha by object name).
    // TODO: Once {Pull,PullOrdered,Push,PushOrdered}ConsumerCreator gain from-reader constructors
    // accepting ConsumerConfiguration, upgrade these to full Creator → JSON → DTO → Creator tests.
    // The half-trip below validates that the JSON each creator emits survives parse + re-emit
    // through ConsumerConfiguration unchanged.
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void pullConsumerCreatorJsonRoundTrip() {
        PullConsumerCreator c1 = new PullConsumerCreator()
            .durable("pull-durable")
            .maxBatch(50)
            .maxBytes(1_048_576L)
            .maxPullWaiting(10);
        assertConsumerCreatorJsonRoundTrip(c1.toJson());
    }

    @Test
    public void pullOrderedConsumerCreatorJsonRoundTrip() {
        PullOrderedConsumerCreator c1 = new PullOrderedConsumerCreator()
            .maxBatch(25)
            .maxPullWaiting(5);
        assertConsumerCreatorJsonRoundTrip(c1.toJson());
    }

    @Test
    public void pushConsumerCreatorJsonRoundTrip() {
        PushConsumerCreator c1 = new PushConsumerCreator()
            .durable("push-durable")
            .deliverSubject("deliver.subject")
            .deliverGroup("group-a");
        assertConsumerCreatorJsonRoundTrip(c1.toJson());
    }

    @Test
    public void pushOrderedConsumerCreatorJsonRoundTrip() {
        PushOrderedConsumerCreator c1 = new PushOrderedConsumerCreator()
            .deliverSubject("ordered.deliver");
        assertConsumerCreatorJsonRoundTrip(c1.toJson());
    }

    private static void assertConsumerCreatorJsonRoundTrip(String creatorJson) {
        // Field order of LazyJsonValue's re-emit is not stable, so we compare structurally
        // via LazyJsonValue.equals (map equality, order-independent) rather than as raw strings.
        ConsumerConfiguration dto = new ConsumerConfiguration(LazyJsonParser.parseUnchecked(creatorJson));
        assertJsonEquivalent(creatorJson, dto.toJson());
    }

    // ----------------------------------------------------------------------------------------------------
    // Shared assertion helpers
    // ----------------------------------------------------------------------------------------------------

    /**
     * Assert that two JSON strings are structurally equivalent. Uses {@code LazyJsonValue.equals}
     * (map equality, order-independent) since {@code LazyJsonValue}'s emitted field order is not
     * stable across parse/re-emit cycles.
     */
    private static void assertJsonEquivalent(String expected, String actual) {
        LazyJsonValue ev = LazyJsonParser.parseUnchecked(expected);
        LazyJsonValue av = LazyJsonParser.parseUnchecked(actual);
        assertEquals(ev, av, () -> "JSON not structurally equivalent.\n  expected: " + expected + "\n  actual:   " + actual);
    }

    /**
     * Assert a full creator round-trip: the creator's JSON survives parse → DTO → re-built
     * creator → emit, and the rebuilt creator's instance equals the original.
     */
    private static <C extends JsonSerializable> void assertCreatorRoundTrip(C original, C rebuilt) {
        assertJsonEquivalent(original.toJson(), rebuilt.toJson());
        assertEquals(original, rebuilt);
    }
}
