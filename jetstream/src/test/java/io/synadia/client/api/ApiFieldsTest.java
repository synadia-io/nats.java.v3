package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsConstants;
import io.synadia.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive content/field-coverage and round-trip tests for the io.synadia.client.api package.
 *
 * <p>This complements {@link EqualityAndHashCodeCoverageTest} (equals/hashCode) and
 * {@link StreamCreatorConfigurationTests} (StreamCreator/StreamConfiguration round-trip).
 *
 * <p>All tests work offline — no NATS server needed.
 *
 * <p>Tests are organized one-per-object-type. Within each test, sub-sections are grouped as:
 * (1) construct + creator-side getters, (2) JSON round trip to counterpart,
 * (3) alternate constructors, (4) from-counterpart constructor,
 * (5) null-reset / numeric-reset coverage on setters.
 */
public class ApiFieldsTest {

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------

    /** Parse JSON without throwing checked exceptions. */
    private static LazyJsonValue lj(String json) {
        return LazyJsonParser.parseUnchecked(json);
    }

    /** Parse one of the shared json files from src/test/resources/data. */
    private static LazyJsonValue ljData(String fileName) {
        return lj(dataAsString(fileName));
    }

    /** Build a NatsMessage from JSON for ApiResponse Message-based constructors. */
    private static NatsMessage msg(String json) {
        return new NatsMessage("subj", null, json.getBytes(StandardCharsets.US_ASCII));
    }

    private static final ZonedDateTime ZDT_A = ZonedDateTime.of(2026, 1, 2, 3, 4, 5, 0, DateTimeUtils.ZONE_ID_UTC);
    private static final ZonedDateTime ZDT_B = ZonedDateTime.of(2026, 5, 17, 12, 30, 45, 0, DateTimeUtils.ZONE_ID_UTC);

    // ====================================================================================================
    // PART 1: Creators (one test per Creator)
    // ====================================================================================================

    @Test
    public void testConsumerLimitsCreator() {
        // ---- defaults (constructor only, no setters) ----
        ConsumerLimitsCreator dflt = new ConsumerLimitsCreator();
        assertNull(dflt.getInactiveThreshold());
        assertEquals(-1L, dflt.getMaxAckPending());

        // ---- construct + creator getters + round trip ----
        ConsumerLimitsCreator creator = new ConsumerLimitsCreator()
            .inactiveThreshold(Duration.ofSeconds(45))
            .maxAckPending(7777);
        assertEquals(Duration.ofSeconds(45), creator.getInactiveThreshold());
        assertEquals(7777L, creator.getMaxAckPending());

        ConsumerLimits limits = new ConsumerLimits(lj(creator.toJson()));
        assertEquals(Duration.ofSeconds(45), limits.getInactiveThreshold());
        assertEquals(7777, limits.getMaxAckPending());

        // ---- millis form ----
        ConsumerLimitsCreator c2 = new ConsumerLimitsCreator().inactiveThreshold(12000L);
        ConsumerLimits l2 = new ConsumerLimits(lj(c2.toJson()));
        assertEquals(Duration.ofMillis(12000), l2.getInactiveThreshold());

        // ---- from-counterpart constructor ----
        ConsumerLimits original = new ConsumerLimits(ljData("ConsumerLimits.json"));
        ConsumerLimitsCreator fromCounterpart = new ConsumerLimitsCreator(original);
        assertEquals(Duration.ofNanos(61000000000L), fromCounterpart.getInactiveThreshold());
        assertEquals(62L, fromCounterpart.getMaxAckPending());

        ConsumerLimits round = new ConsumerLimits(lj(fromCounterpart.toJson()));
        assertEquals(original.getInactiveThreshold(), round.getInactiveThreshold());
        assertEquals(original.getMaxAckPending(), round.getMaxAckPending());

        // ---- null-reset / numeric-reset coverage ----
        ConsumerLimitsCreator c = new ConsumerLimitsCreator()
            .inactiveThreshold(Duration.ofSeconds(10))
            .maxAckPending(50);
        assertEquals(Duration.ofSeconds(10), c.getInactiveThreshold());
        assertEquals(50L, c.getMaxAckPending());

        // inactiveThreshold(null) -> null (uses normalizeDuration with dftl=null)
        c.inactiveThreshold(null);
        assertNull(c.getInactiveThreshold());
        // inactiveThreshold(Duration.ZERO) -> null
        c.inactiveThreshold(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getInactiveThreshold());
        c.inactiveThreshold(Duration.ZERO);
        assertNull(c.getInactiveThreshold());
        // inactiveThreshold(negative Duration) -> null
        c.inactiveThreshold(Duration.ofSeconds(5));
        c.inactiveThreshold(Duration.ofMillis(-1));
        assertNull(c.getInactiveThreshold());
        // inactiveThreshold(millis 0) -> null
        c.inactiveThreshold(1000L);
        assertEquals(Duration.ofMillis(1000), c.getInactiveThreshold());
        c.inactiveThreshold(0L);
        assertNull(c.getInactiveThreshold());
        // inactiveThreshold(millis negative) -> null
        c.inactiveThreshold(1000L);
        c.inactiveThreshold(-1L);
        assertNull(c.getInactiveThreshold());

        // maxAckPending: normalizeLong(l, 1). l < 1 -> -1.
        c.maxAckPending(100);
        assertEquals(100L, c.getMaxAckPending());
        c.maxAckPending(-1);
        assertEquals(-1L, c.getMaxAckPending());
        c.maxAckPending(100);
        c.maxAckPending(-99);
        assertEquals(-1L, c.getMaxAckPending());
        c.maxAckPending(0);
        assertEquals(-1L, c.getMaxAckPending());
    }

    @Test
    public void testExternalCreator() {
        // ---- defaults (constructor only, no setters) ----
        ExternalCreator creator = new ExternalCreator("api-prefix");
        assertEquals("api-prefix", creator.getApi());
        assertNull(creator.getDeliver());

        // ---- construct + creator getters + JSON round trip ----
        creator = new ExternalCreator("api-prefix", "deliver-subject");
        assertEquals("api-prefix", creator.getApi());
        assertEquals("deliver-subject", creator.getDeliver());

        External external = new External(lj(creator.toJson()));
        assertEquals("api-prefix", external.getApi());
        assertEquals("deliver-subject", external.getDeliver());

        // ---- two-arg constructor ----
        ExternalCreator twoArg = new ExternalCreator("api2", "deliver2");
        External ext2 = new External(lj(twoArg.toJson()));
        assertEquals("api2", ext2.getApi());
        assertEquals("deliver2", ext2.getDeliver());

        // ---- from-counterpart constructor ----
        External original = new External(ljData("External.json"));
        ExternalCreator fromCounterpart = new ExternalCreator(original);
        assertEquals("ext-api", fromCounterpart.getApi());
        assertEquals("ext-deliver", fromCounterpart.getDeliver());

        External round = new External(lj(fromCounterpart.toJson()));
        assertEquals(original.getApi(), round.getApi());
        assertEquals(original.getDeliver(), round.getDeliver());

        // ---- null-reset coverage ----
        ExternalCreator c = new ExternalCreator("a", "d");
        assertEquals("a", c.getApi());
        assertEquals("d", c.getDeliver());
        //noinspection DataFlowIssue
        assertThrows(IllegalArgumentException.class, () -> c.api(null));
        c.deliver(null);
        assertNull(c.getDeliver());

        // ---- default string coverage ----
        External e = new External(ljData("Empty.json"));
        assertEquals("", e.getApi());
    }

    @Test
    public void testMirrorCreator() {
        // ---- defaults (constructor only, no setters) ----
        MirrorCreator dflt = new MirrorCreator("dflt-mirror");
        assertEquals("dflt-mirror", dflt.getStreamName());
        assertEquals(0L, dflt.getStartSequence());
        assertNull(dflt.getStartTime());
        assertNull(dflt.getFilterSubject());
        assertNull(dflt.getExternalCreator());
        assertTrue(dflt.getSubjectTransformCreators().isEmpty());

        // ---- construct + creator getters + round trip ----
        MirrorCreator creator = new MirrorCreator("mirror-stream")
            .startSequence(99)
            .startTime(ZDT_A)
            .filterSubject("filter.>")
            .externalCreator(new ExternalCreator("apiX", "dlvX"))
            .subjectTransforms(
                new SubjectTransformCreator("m_src0", "m_dst0"),
                new SubjectTransformCreator("m_src1", "m_dst1"));

        assertEquals("mirror-stream", creator.getStreamName());
        assertEquals(99L, creator.getStartSequence());
        assertEquals(ZDT_A, creator.getStartTime());
        assertEquals("filter.>", creator.getFilterSubject());
        assertNotNull(creator.getExternalCreator());
        assertEquals(2, creator.getSubjectTransformCreators().size());

        Mirror mirror = new Mirror(lj(creator.toJson()));
        assertEquals("mirror-stream", mirror.getStreamName());
        assertEquals(99, mirror.getStartSequence());
        assertEquals(ZDT_A, mirror.getStartTime());
        assertEquals("filter.>", mirror.getFilterSubject());

        Mirror empty = new Mirror(ljData("Empty.json"));
        assertEquals("", empty.getStreamName());

        External ext = mirror.getExternal();
        assertNotNull(ext);
        assertEquals("apiX", ext.getApi());
        assertEquals("dlvX", ext.getDeliver());

        List<SubjectTransform> transforms = mirror.getSubjectTransforms();
        assertEquals(2, transforms.size());
        assertEquals("m_src0", transforms.get(0).getSource());
        assertEquals("m_dst0", transforms.get(0).getDestination());
        assertEquals("m_src1", transforms.get(1).getSource());
        assertEquals("m_dst1", transforms.get(1).getDestination());

        // ---- from-counterpart constructor ----
        Mirror original = new Mirror(ljData("Mirror.json"));
        MirrorCreator fromCounterpart = new MirrorCreator(original);
        assertEquals("mirror-name", fromCounterpart.getStreamName());
        assertEquals(91L, fromCounterpart.getStartSequence());
        assertEquals("mirror.filter", fromCounterpart.getFilterSubject());
        assertNotNull(fromCounterpart.getExternalCreator());
        assertEquals(2, fromCounterpart.getSubjectTransformCreators().size());

        // ---- copy-rename constructor ----
        MirrorCreator renamed = new MirrorCreator("m2", fromCounterpart);
        assertEquals("m2", renamed.getStreamName());
        assertEquals(fromCounterpart.getStartSequence(), renamed.getStartSequence());
        assertEquals(fromCounterpart.getFilterSubject(), renamed.getFilterSubject());

        // ---- domain(...) sets/clears externalCreator ----
        MirrorCreator dm = new MirrorCreator("ms").domain("my-domain");
        ExternalCreator dEc = dm.getExternalCreator();
        assertNotNull(dEc);
        assertNotNull(dEc.getApi());

        MirrorCreator dmNull = new MirrorCreator("ms").domain(null);
        assertNull(dmNull.getExternalCreator());

        MirrorCreator dmEmpty = new MirrorCreator("ms").domain("");
        assertNull(dmEmpty.getExternalCreator());

        // ---- null-reset coverage ----
        MirrorCreator c = new MirrorCreator("nc")
            .startTime(ZDT_A)
            .filterSubject("f.>")
            .externalCreator(new ExternalCreator("a", "d"))
            .subjectTransforms(new SubjectTransformCreator("s", "t"));
        assertEquals(ZDT_A, c.getStartTime());
        assertEquals("f.>", c.getFilterSubject());
        assertNotNull(c.getExternalCreator());
        assertEquals(1, c.getSubjectTransformCreators().size());

        c.startTime(null);
        assertNull(c.getStartTime());
        c.filterSubject(null);
        assertNull(c.getFilterSubject());
        c.externalCreator(null);
        assertNull(c.getExternalCreator());
        // subjectTransforms varargs null is an error
        assertThrows(NullPointerException.class, () -> c.subjectTransforms((SubjectTransformCreator[]) null));
        assertTrue(c.getSubjectTransformCreators().isEmpty());
        // re-set then clear via list null
        c.subjectTransforms(new SubjectTransformCreator("s", "t"));
        assertEquals(1, c.getSubjectTransformCreators().size());
        c.subjectTransforms((List<SubjectTransformCreator>) null);
        assertTrue(c.getSubjectTransformCreators().isEmpty());
    }

    @Test
    public void testPlacementCreator() {
        // ---- defaults (constructor only, no setters) ----
        PlacementCreator dflt = new PlacementCreator();
        assertNull(dflt.getCluster());
        assertTrue(dflt.getTags().isEmpty());
        assertFalse(dflt.hasData());

        // ---- construct + creator getters + JSON round trip ----
        PlacementCreator creator = new PlacementCreator()
            .cluster("east-1")
            .tags("tag-a", "tag-b", "tag-c");
        assertEquals("east-1", creator.getCluster());
        assertEquals(3, creator.getTags().size());

        Placement placement = new Placement(lj(creator.toJson()));
        assertEquals("east-1", placement.getCluster());
        List<String> tags = placement.getTags();
        assertNotNull(tags);
        assertEquals(3, tags.size());
        assertEquals("tag-a", tags.get(0));
        assertEquals("tag-b", tags.get(1));
        assertEquals("tag-c", tags.get(2));
        assertTrue(placement.hasData());

        // ---- alternate constructor: cluster + List ----
        PlacementCreator c2 = new PlacementCreator("west-2", Arrays.asList("t1", "t2"));
        Placement p2 = new Placement(lj(c2.toJson()));
        assertEquals("west-2", p2.getCluster());
        assertEquals(2, p2.getTags().size());

        // ---- tags(List) setter form ----
        PlacementCreator c3 = new PlacementCreator().cluster("c").tags(Arrays.asList("x", "y"));
        Placement p3 = new Placement(lj(c3.toJson()));
        assertEquals(2, p3.getTags().size());

        // ---- from-counterpart constructor ----
        Placement original = new Placement(ljData("Placement.json"));
        PlacementCreator fromCounterpart = new PlacementCreator(original);
        assertEquals("placement-cluster", fromCounterpart.getCluster());
        assertEquals(3, fromCounterpart.getTags().size());
        Placement round = new Placement(lj(fromCounterpart.toJson()));
        assertEquals(original.getCluster(), round.getCluster());
        assertEquals(original.getTags(), round.getTags());

        // ---- null-reset coverage ----
        PlacementCreator c = new PlacementCreator().cluster("cx").tags("a", "b");
        assertEquals("cx", c.getCluster());
        assertEquals(2, c.getTags().size());
        c.cluster(null);
        assertNull(c.getCluster());
        // tags(null) is an error
        assertThrows(NullPointerException.class, () -> c.tags((String[]) null));
        assertTrue(c.getTags().isEmpty());
        // re-set, then clear via List form
        c.tags("a", "b");
        assertEquals(2, c.getTags().size());
        c.tags((List<String>) null);
        assertTrue(c.getTags().isEmpty());
    }

    @Test
    public void testPullConsumerCreator() {
        // ---- defaults (constructor only, no setters) ----
        PullConsumerCreator dflt = new PullConsumerCreator();
        assertFalse(dflt.isPush());
        assertEquals(ConsumerCreator.DEFAULT_DELIVER_POLICY, dflt.getDeliverPolicy());
        assertEquals(ConsumerCreator.DEFAULT_ACK_POLICY, dflt.getAckPolicy());
        assertEquals(ConsumerCreator.DEFAULT_REPLAY_POLICY, dflt.getReplayPolicy());
        assertEquals(ConsumerCreator.DEFAULT_PRIORITY_POLICY, dflt.getPriorityPolicy());
        assertNull(dflt.getDescription());
        assertNull(dflt.getDurable());
        assertNull(dflt.getName());
        assertNull(dflt.getDeliverSubject());
        assertNull(dflt.getDeliverGroup());
        assertNull(dflt.getSampleFrequency());
        assertNull(dflt.getStartTime());
        assertNull(dflt.getAckWait());
        assertNull(dflt.getIdleHeartbeat());
        assertNull(dflt.getMaxExpires());
        assertNull(dflt.getInactiveThreshold());
        assertNull(dflt.getPauseUntil());
        assertNull(dflt.getPriorityTimeout());
        assertEquals(0L, dflt.getStartSequence());
        assertEquals(0L, dflt.getRateLimit());
        assertEquals(-1L, dflt.getMaxDeliver());
        assertEquals(-1L, dflt.getMaxAckPending());
        assertEquals(-1L, dflt.getMaxPullWaiting());
        assertEquals(-1L, dflt.getMaxBatch());
        assertEquals(-1L, dflt.getMaxBytes());
        assertEquals(-1L, dflt.getNumReplicas());
        assertFalse(dflt.isFlowControl());
        assertFalse(dflt.isHeadersOnly());
        assertFalse(dflt.isMemStorage());
        assertNull(dflt.getFilterSubject());
        assertTrue(dflt.getFilterSubjects().isEmpty());
        assertFalse(dflt.hasMultipleFilterSubjects());
        assertTrue(dflt.getBackoff().isEmpty());
        assertTrue(dflt.getMetadata().isEmpty());
        assertTrue(dflt.getPriorityGroups().isEmpty());

        // ---- construct + creator-side setters + JSON round trip ----
        PullConsumerCreator creator = new PullConsumerCreator()
            .durable("dur-1")
            .description("desc-1")
            .deliverPolicy(DeliverPolicy.New)
            .ackPolicy(AckPolicy.Explicit)
            .ackWait(Duration.ofSeconds(30))
            .maxDeliver(7)
            .maxAckPending(42)
            .replayPolicy(ReplayPolicy.Instant)
            .sampleFrequency("100")
            .rateLimit(1024L)
            .idleHeartbeat(Duration.ofSeconds(15))
            .headersOnly(true)
            .maxPullWaiting(64L)
            .maxBatch(100L)
            .maxBytes(2048L)
            .maxExpires(Duration.ofSeconds(60))
            .inactiveThreshold(Duration.ofSeconds(120))
            .backoff(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3))
            .numReplicas(3)
            .pauseUntil(ZDT_A)
            .memStorage(true)
            .filterSubjects("the.subject")
            .priorityGroups("pg-1", "pg-2")
            .priorityPolicy(PriorityPolicy.PinnedClient)
            .priorityTimeout(Duration.ofSeconds(20))
            .name("dur-1");

        assertFalse(creator.isPush());
        assertEquals("dur-1", creator.getName());
        assertTrue(creator.isHeadersOnly());
        assertTrue(creator.isMemStorage());
        assertEquals(Duration.ofSeconds(15), creator.getIdleHeartbeat());
        assertEquals(3, creator.getBackoff().size());

        ConsumerConfiguration cc = new ConsumerConfiguration(lj(creator.toJson()));

        assertEquals("desc-1", cc.getDescription());
        assertEquals("dur-1", cc.getDurable());
        assertEquals(DeliverPolicy.New, cc.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, cc.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), cc.getAckWait());
        assertEquals(7, cc.getMaxDeliver());
        assertEquals(42, cc.getMaxAckPending());
        assertEquals(ReplayPolicy.Instant, cc.getReplayPolicy());
        assertEquals("100", cc.getSampleFrequency());
        assertEquals(1024L, cc.getRateLimit());
        assertEquals(Duration.ofSeconds(15), cc.getIdleHeartbeat());
        assertTrue(cc.isHeadersOnly());
        assertEquals(64L, cc.getMaxPullWaiting());
        assertEquals(100L, cc.getMaxBatch());
        assertEquals(2048L, cc.getMaxBytes());
        assertEquals(Duration.ofSeconds(60), cc.getMaxExpires());
        assertEquals(Duration.ofSeconds(120), cc.getInactiveThreshold());
        assertEquals(3, cc.getBackoff().size());
        assertEquals(Duration.ofSeconds(1), cc.getBackoff().get(0));
        assertEquals(3L, cc.getNumReplicas());
        assertEquals(ZDT_A, cc.getPauseUntil());
        assertTrue(cc.isMemStorage());
        assertEquals("the.subject", cc.getFilterSubject());
        List<String> priorityGroups = cc.getPriorityGroups();
        assertNotNull(priorityGroups);
        assertEquals(2, priorityGroups.size());
        assertEquals("pg-1", priorityGroups.get(0));
        assertEquals(PriorityPolicy.PinnedClient, cc.getPriorityPolicy());
        assertEquals(Duration.ofSeconds(20), cc.getPriorityTimeout());

        // ---- multiple filter subjects ----
        PullConsumerCreator multi = new PullConsumerCreator()
            .filterSubjects("a.>", "b.>", "c.>");
        assertTrue(multi.hasMultipleFilterSubjects());
        ConsumerConfiguration multiCc = new ConsumerConfiguration(lj(multi.toJson()));
        assertNull(multiCc.getFilterSubject());
        assertTrue(multiCc.hasMultipleFilterSubjects());
        List<String> filterSubjects = multiCc.getFilterSubjects();
        assertEquals(3, filterSubjects.size());
        assertEquals("a.>", filterSubjects.get(0));
        assertEquals("b.>", filterSubjects.get(1));
        assertEquals("c.>", filterSubjects.get(2));

        // ---- copy constructor ----
        PullConsumerCreator copy = new PullConsumerCreator(creator);
        assertEquals("dur-1", copy.getDurable());
        assertEquals("desc-1", copy.getDescription());

        // ---- null-reset / numeric-reset coverage ----
        PullConsumerCreator c = new PullConsumerCreator()
            .description("d")
            .durable("dur")
            .ackWait(Duration.ofSeconds(5))
            .idleHeartbeat(Duration.ofSeconds(1))
            .maxExpires(Duration.ofSeconds(10))
            .inactiveThreshold(Duration.ofSeconds(20))
            .priorityTimeout(Duration.ofSeconds(2))
            .sampleFrequency("50")
            .pauseUntil(ZDT_A)
            .filterSubjects("f.>")
            .priorityGroups("g1", "g2")
            .deliverPolicy(DeliverPolicy.New)
            .ackPolicy(AckPolicy.None)
            .replayPolicy(ReplayPolicy.Original)
            .priorityPolicy(PriorityPolicy.PinnedClient);

        // ---- description/durable/sampleFrequency: null/empty -> null ----
        c.description(null);
        assertNull(c.getDescription());
        c.description("x").description("");
        assertNull(c.getDescription());

        c.durable(null);
        assertNull(c.getDurable());
        c.durable("x").durable("");
        assertNull(c.getDurable());

        c.sampleFrequency(null);
        assertNull(c.getSampleFrequency());
        c.sampleFrequency("9").sampleFrequency("");
        assertNull(c.getSampleFrequency());

        // ---- pauseUntil: simple null reset ----
        c.pauseUntil(null);
        assertNull(c.getPauseUntil());

        // ---- filterSubject(empty) clears (filterSubjects list becomes empty) ----
        // Setup chain set filterSubject("f.>"); verify empty resets it.
        assertEquals("f.>", c.getFilterSubject());
        c.filterSubjects("");
        assertNull(c.getFilterSubject());
        assertTrue(c.getFilterSubjects().isEmpty());
        c.filterSubjects("a.>").filterSubjects("");
        assertTrue(c.getFilterSubjects().isEmpty());

        // ---- filterSubjects array/list null -> clears ----
        c.filterSubjects("a.>", "b.>");
        assertEquals(2, c.getFilterSubjects().size());
        assertThrows(NullPointerException.class, () -> c.filterSubjects((String[]) null));
        assertTrue(c.getFilterSubjects().isEmpty());
        c.filterSubjects("a.>", "b.>");
        c.filterSubjects((List<String>) null);
        assertTrue(c.getFilterSubjects().isEmpty());

        // ---- priorityGroups null -> clears ----
        c.priorityGroups("g1", "g2");
        assertEquals(2, c.getPriorityGroups().size());
        assertThrows(NullPointerException.class, () -> c.priorityGroups((String[]) null));
        assertTrue(c.getPriorityGroups().isEmpty());

        // ---- policies: null -> default ----
        c.deliverPolicy(null);
        assertEquals(ConsumerCreator.DEFAULT_DELIVER_POLICY, c.getDeliverPolicy());
        c.replayPolicy(null);
        assertEquals(ConsumerCreator.DEFAULT_REPLAY_POLICY, c.getReplayPolicy());
        c.priorityPolicy(null);
        assertEquals(ConsumerCreator.DEFAULT_PRIORITY_POLICY, c.getPriorityPolicy());
        c.ackPolicy(null);
        assertEquals(ConsumerCreator.DEFAULT_ACK_POLICY, c.getAckPolicy());

        // ---- Duration setters: null Duration, Duration.ZERO, negative Duration, or millis < 1 all nullify ----
        c.ackWait(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getAckWait());
        c.ackWait((Duration) null);
        assertNull(c.getAckWait());
        c.ackWait(Duration.ofSeconds(5));
        c.ackWait(Duration.ZERO);
        assertNull(c.getAckWait());
        c.ackWait(Duration.ofSeconds(5));
        c.ackWait(Duration.ofMillis(-1));
        assertNull(c.getAckWait());
        c.ackWait(Duration.ofSeconds(5));
        c.ackWait(0L);
        assertNull(c.getAckWait());
        c.ackWait(Duration.ofSeconds(5));
        c.ackWait(-1L);
        assertNull(c.getAckWait());

        // maxExpires: same null-reset semantics
        c.maxExpires(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getMaxExpires());
        c.maxExpires((Duration) null);
        assertNull(c.getMaxExpires());
        c.maxExpires(Duration.ofSeconds(5));
        c.maxExpires(Duration.ZERO);
        assertNull(c.getMaxExpires());

        // priorityTimeout: same null-reset semantics
        c.priorityTimeout(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getPriorityTimeout());
        c.priorityTimeout((Duration) null);
        assertNull(c.getPriorityTimeout());
        c.priorityTimeout(Duration.ofSeconds(5));
        c.priorityTimeout(Duration.ZERO);
        assertNull(c.getPriorityTimeout());

        // inactiveThreshold: null Duration or millis < 1 nullifies the field.
        c.inactiveThreshold(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getInactiveThreshold());
        c.inactiveThreshold((Duration) null);
        assertNull(c.getInactiveThreshold());
        c.inactiveThreshold(Duration.ofSeconds(5));
        c.inactiveThreshold(Duration.ZERO);
        assertNull(c.getInactiveThreshold());
        c.inactiveThreshold(Duration.ofSeconds(5));
        c.inactiveThreshold(Duration.ofMillis(-1));
        assertNull(c.getInactiveThreshold());
        // millis form: positive kept, < 1 nullifies
        c.inactiveThreshold(1000L);
        assertEquals(Duration.ofMillis(1000), c.getInactiveThreshold());
        c.inactiveThreshold(0L);
        assertNull(c.getInactiveThreshold());
        c.inactiveThreshold(1000L);
        c.inactiveThreshold(-1L);
        assertNull(c.getInactiveThreshold());

        // ---- numeric resets ----
        // All these fields use normalizeLong(l, 1). l < 1 -> -1.
        c.maxDeliver(7);
        assertEquals(7L, c.getMaxDeliver());
        c.maxDeliver(0);
        assertEquals(-1L, c.getMaxDeliver());
        c.maxDeliver(5);
        c.maxDeliver(-1);
        assertEquals(-1L, c.getMaxDeliver());

        c.maxAckPending(50);
        assertEquals(50L, c.getMaxAckPending());
        c.maxAckPending(0);
        assertEquals(-1L, c.getMaxAckPending());
        c.maxAckPending(-1);
        assertEquals(-1L, c.getMaxAckPending());

        c.maxPullWaiting(10L);
        assertEquals(10L, c.getMaxPullWaiting());
        c.maxPullWaiting(0L);
        assertEquals(-1L, c.getMaxPullWaiting());
        c.maxPullWaiting(-1L);
        assertEquals(-1L, c.getMaxPullWaiting());

        c.maxBatch(10L);
        assertEquals(10L, c.getMaxBatch());
        c.maxBatch(0L);
        assertEquals(-1L, c.getMaxBatch());
        c.maxBatch(-5L);
        assertEquals(-1L, c.getMaxBatch());

        c.maxBytes(2048L);
        assertEquals(2048L, c.getMaxBytes());
        c.maxBytes(0L);
        assertEquals(-1L, c.getMaxBytes());
        c.maxBytes(-1L);
        assertEquals(-1L, c.getMaxBytes());
        c.maxBytes(-5L);
        assertEquals(-1L, c.getMaxBytes());

        // startSequence: normalizeULong. negative -> 0 (ULONG_UNSET).
        c.startSequence(100L);
        assertEquals(100L, c.getStartSequence());
        c.startSequence(-1L);
        assertEquals(0L, c.getStartSequence());

        // rateLimit: same normalizeULong.
        c.rateLimit(1000L);
        assertEquals(1000L, c.getRateLimit());
        c.rateLimit(-5L);
        assertEquals(0L, c.getRateLimit());

        // numReplicas: < 1 -> UNSET (-1); otherwise validateNumberOfReplicas (1..5).
        c.numReplicas(3);
        assertEquals(3L, c.getNumReplicas());
        c.numReplicas(0);
        assertEquals(-1L, c.getNumReplicas());
        c.numReplicas(-1);
        assertEquals(-1L, c.getNumReplicas());
        assertThrows(IllegalArgumentException.class, () -> c.numReplicas(6));

        // ---- additional overloads not covered above ----
        // idleHeartbeat(long)
        c.idleHeartbeat(20000L);
        assertEquals(Duration.ofMillis(20000), c.getIdleHeartbeat());

        // flowControl(long) — sets flowControl=true and delegates to idleHeartbeat
        c.flowControl(15000L);
        assertTrue(c.isFlowControl());
        assertEquals(Duration.ofMillis(15000), c.getIdleHeartbeat());

        // backoff(long...)
        c.backoff(1000L, 2000L);
        assertEquals(2, c.getBackoff().size());
        assertEquals(Duration.ofSeconds(1), c.getBackoff().get(0));

        // maxExpires(long): positive kept, <= 0 nullifies
        c.maxExpires(5000L);
        assertEquals(Duration.ofMillis(5000), c.getMaxExpires());
        c.maxExpires(0L);
        assertNull(c.getMaxExpires());

        // priorityTimeout(long): same semantics
        c.priorityTimeout(5000L);
        assertEquals(Duration.ofMillis(5000), c.getPriorityTimeout());
        c.priorityTimeout(0L);
        assertNull(c.getPriorityTimeout());

        // boxed-Long overloads: null clears to UNSET (-1)
        c.maxBatch(10L);
        assertEquals(10L, c.getMaxBatch());
        c.maxBatch(Long.MIN_VALUE);
        assertEquals(-1L, c.getMaxBatch());

        c.maxBytes(2048L);
        assertEquals(2048L, c.getMaxBytes());
        c.maxBytes(Long.MIN_VALUE);
        assertEquals(-1L, c.getMaxBytes());

        c.maxPullWaiting(10L);
        assertEquals(10L, c.getMaxPullWaiting());
        c.maxPullWaiting(Long.MIN_VALUE);
        assertEquals(-1L, c.getMaxPullWaiting());
    }

    @Test
    public void testPullOrderedConsumerCreator() {
        // The setters exercised here are pull-ordered-only; they delegate to the
        // same protected setters already covered by testPullConsumerCreator, so
        // we focus on overload coverage rather than re-asserting reset semantics.

        // ---- defaults (constructor only, no setters) ----
        // The ordered creator's constructor seeds a fixed policy set via commonInit().
        PullOrderedConsumerCreator dflt = new PullOrderedConsumerCreator();
        assertFalse(dflt.isPush());
        assertNull(dflt.getNamePrefix());
        assertNotNull(dflt.getName()); // auto-generated random name
        assertEquals(AckPolicy.None, dflt.getAckPolicy());
        assertEquals(1L, dflt.getMaxDeliver());
        assertEquals(Duration.ofHours(22), dflt.getAckWait());
        assertTrue(dflt.isMemStorage());
        assertEquals(1L, dflt.getNumReplicas());
        assertNull(dflt.getIdleHeartbeat()); // heartbeat default is push-ordered only
        assertEquals(ConsumerCreator.DEFAULT_DELIVER_POLICY, dflt.getDeliverPolicy());

        PullOrderedConsumerCreator c = new PullOrderedConsumerCreator()
            .namePrefix("pfx")
            .maxExpires(Duration.ofSeconds(60))
            .maxPullWaiting(64L)
            .maxBatch(100L)
            .maxBytes(2048L)
            .priorityGroups("g1", "g2")
            .priorityPolicy(PriorityPolicy.PinnedClient)
            .priorityTimeout(Duration.ofSeconds(10));

        assertFalse(c.isPush());
        assertEquals("pfx", c.getNamePrefix());
        assertNotNull(c.getName());
        assertTrue(c.getName().startsWith("pfx-"));
        assertEquals(Duration.ofSeconds(60), c.getMaxExpires());
        assertEquals(64L, c.getMaxPullWaiting());
        assertEquals(100L, c.getMaxBatch());
        assertEquals(2048L, c.getMaxBytes());
        assertEquals(Arrays.asList("g1", "g2"), c.getPriorityGroups());
        assertEquals(PriorityPolicy.PinnedClient, c.getPriorityPolicy());
        assertEquals(Duration.ofSeconds(10), c.getPriorityTimeout());

        // alternate overloads
        c.maxExpires(45000L);
        assertEquals(Duration.ofMillis(45000), c.getMaxExpires());

        c.maxBytes(null);
        assertEquals(-1L, c.getMaxBytes());

        c.priorityGroups(Arrays.asList("g3", "g4"));
        assertEquals(Arrays.asList("g3", "g4"), c.getPriorityGroups());

        c.priorityTimeout(5000L);
        assertEquals(Duration.ofMillis(5000), c.getPriorityTimeout());

        // priorityPolicy(null) -> default
        c.priorityPolicy(null);
        assertEquals(ConsumerCreator.DEFAULT_PRIORITY_POLICY, c.getPriorityPolicy());

        // ---- copy constructor with lastStreamSeq ----
        PullOrderedConsumerCreator copy = new PullOrderedConsumerCreator(c, 100L, null);
        assertNotNull(copy);
        // lastStreamSeq > 0 mutates the *source* creator's start policy
        assertEquals(DeliverPolicy.ByStartSequence, copy.getDeliverPolicy());
        assertEquals(101L, copy.getStartSequence());
    }

    @Test
    public void testPushConsumerCreator() {
        // ---- defaults (constructor only, no setters) ----
        PushConsumerCreator dflt = new PushConsumerCreator();
        assertTrue(dflt.isPush());
        assertEquals(ConsumerCreator.DEFAULT_DELIVER_POLICY, dflt.getDeliverPolicy());
        assertEquals(ConsumerCreator.DEFAULT_ACK_POLICY, dflt.getAckPolicy());
        assertNull(dflt.getDurable());
        assertNull(dflt.getName());
        assertNull(dflt.getDeliverSubject());
        assertNull(dflt.getDeliverGroup());
        assertNull(dflt.getStartTime());
        assertNull(dflt.getIdleHeartbeat());
        assertFalse(dflt.isFlowControl());
        assertFalse(dflt.isHeadersOnly());
        assertEquals(0L, dflt.getStartSequence());
        assertEquals(-1L, dflt.getMaxAckPending());
        assertEquals(-1L, dflt.getNumReplicas());
        assertTrue(dflt.getMetadata().isEmpty());

        // ---- construct + creator setters + round trip ----
        Map<String, String> meta = new HashMap<>();
        meta.put("k1", "v1");
        meta.put("k2", "v2");

        PushConsumerCreator creator = new PushConsumerCreator()
            .durable("push-dur")
            .deliverSubject("deliver.here")
            .deliverGroup("group-1")
            .description("push-desc")
            .startSequence(500L)
            .startTime(ZDT_B)
            .flowControl(Duration.ofSeconds(20))
            .metadata(meta)
            .name("push-dur");

        assertTrue(creator.isPush());
        assertEquals("push-dur", creator.getName());
        assertTrue(creator.isFlowControl());

        ConsumerConfiguration cc = new ConsumerConfiguration(lj(creator.toJson()));

        assertEquals("push-dur", cc.getDurable());
        assertEquals("deliver.here", cc.getDeliverSubject());
        assertEquals("group-1", cc.getDeliverGroup());
        assertEquals("push-desc", cc.getDescription());
        assertEquals(500L, cc.getStartSequence());
        assertEquals(ZDT_B, cc.getStartTime());
        assertTrue(cc.isFlowControl());
        assertEquals(Duration.ofSeconds(20), cc.getIdleHeartbeat());

        Map<String, String> roundMeta = cc.getMetadata();
        assertEquals(2, roundMeta.size());
        assertEquals("v1", roundMeta.get("k1"));
        assertEquals("v2", roundMeta.get("k2"));

        // ---- copy constructor ----
        PushConsumerCreator copy = new PushConsumerCreator(creator);
        assertEquals("push-dur", copy.getDurable());
        assertEquals("deliver.here", copy.getDeliverSubject());

        // ---- null-reset coverage ----
        PushConsumerCreator c = new PushConsumerCreator()
            .durable("dur")
            .deliverSubject("ds")
            .deliverGroup("dg")
            .description("d")
            .startTime(ZDT_B);

        c.durable(null);
        assertNull(c.getDurable());
        c.deliverSubject(null);
        assertNull(c.getDeliverSubject());
        c.deliverSubject("x").deliverSubject("");
        assertNull(c.getDeliverSubject());
        c.deliverGroup(null);
        assertNull(c.getDeliverGroup());
        c.deliverGroup("x").deliverGroup("");
        assertNull(c.getDeliverGroup());
        c.description(null);
        assertNull(c.getDescription());
        c.startTime(null);
        assertNull(c.getStartTime());

        // metadata null/empty clears
        c.metadata(meta);
        assertEquals(2, c.getMetadata().size());
        c.metadata(null);
        assertTrue(c.getMetadata().isEmpty());
        c.metadata(meta);
        c.metadata(new HashMap<>());
        assertTrue(c.getMetadata().isEmpty());
    }

    @Test
    public void testRepublishCreator() {
        // ---- 3-arg constructor (headersOnly=true) + round trip ----
        RepublishCreator creator = new RepublishCreator("src.>", "dest.>", true);
        assertEquals("src.>", creator.getSource());
        assertEquals("dest.>", creator.getDestination());
        assertTrue(creator.isHeadersOnly());

        Republish republish = new Republish(lj(creator.toJson()));
        assertEquals("src.>", republish.getSource());
        assertEquals("dest.>", republish.getDestination());
        assertTrue(republish.isHeadersOnly());

        Republish empty = new Republish(ljData("Empty.json"));
        assertEquals("", empty.getSource());
        assertEquals("", empty.getDestination());

        // ---- 2-arg constructor (headersOnly defaults to false) ----
        RepublishCreator creator2 = new RepublishCreator("s.>", "d.>");
        assertFalse(creator2.isHeadersOnly());
        Republish republish2 = new Republish(lj(creator2.toJson()));
        assertEquals("s.>", republish2.getSource());
        assertEquals("d.>", republish2.getDestination());
        assertFalse(republish2.isHeadersOnly());

        // ---- 3-arg constructor with headersOnly=false ----
        RepublishCreator creator3 = new RepublishCreator("s2.>", "d2.>", false);
        assertFalse(creator3.isHeadersOnly());

        // ---- from-counterpart constructor ----
        Republish original = new Republish(ljData("Republish.json"));
        RepublishCreator fromCounterpart = new RepublishCreator(original);
        assertEquals("rep.src.>", fromCounterpart.getSource());
        assertEquals("rep.dest.>", fromCounterpart.getDestination());
        assertTrue(fromCounterpart.isHeadersOnly());

        Republish round = new Republish(lj(fromCounterpart.toJson()));
        assertEquals(original.getSource(), round.getSource());
        assertEquals(original.getDestination(), round.getDestination());
        assertEquals(original.isHeadersOnly(), round.isHeadersOnly());

        // RepublishCreator has no nullable setters (all fields are final).
    }

    @Test
    public void testSourceCreator() {
        // ---- defaults (constructor only, no setters) ----
        SourceCreator dflt = new SourceCreator("dflt-source");
        assertEquals("dflt-source", dflt.getStreamName());
        assertEquals(0L, dflt.getStartSequence());
        assertNull(dflt.getStartTime());
        assertNull(dflt.getFilterSubject());
        assertNull(dflt.getExternalCreator());
        assertTrue(dflt.getSubjectTransformCreators().isEmpty());

        // ---- construct + creator getters + round trip ----
        SourceCreator creator = new SourceCreator("source-stream")
            .startSequence(123)
            .startTime(ZDT_B)
            .filterSubject("src.filter")
            .externalCreator(new ExternalCreator("apiY", "dlvY"))
            .subjectTransforms(Collections.singletonList(
                new SubjectTransformCreator("s_src", "s_dst")));

        assertEquals("source-stream", creator.getStreamName());
        assertEquals(123L, creator.getStartSequence());
        assertEquals(ZDT_B, creator.getStartTime());
        assertEquals("src.filter", creator.getFilterSubject());

        Source source = new Source(lj(creator.toJson()));
        assertEquals("source-stream", source.getStreamName());
        assertEquals(123, source.getStartSequence());
        assertEquals(ZDT_B, source.getStartTime());
        assertEquals("src.filter", source.getFilterSubject());

        External ext = source.getExternal();
        assertNotNull(ext);
        assertEquals("apiY", ext.getApi());
        assertEquals("dlvY", ext.getDeliver());

        List<SubjectTransform> transforms = source.getSubjectTransforms();
        assertEquals(1, transforms.size());
        assertEquals("s_src", transforms.get(0).getSource());
        assertEquals("s_dst", transforms.get(0).getDestination());

        // ---- from-counterpart constructor ----
        Source original = new Source(ljData("Source.json"));
        SourceCreator fromCounterpart = new SourceCreator(original);
        assertEquals("source-name", fromCounterpart.getStreamName());
        assertEquals(141L, fromCounterpart.getStartSequence());
        assertEquals("source.filter", fromCounterpart.getFilterSubject());

        // ---- copy-rename constructor ----
        SourceCreator renamed = new SourceCreator("s2", fromCounterpart);
        assertEquals("s2", renamed.getStreamName());

        // ---- domain(...) sets/clears externalCreator ----
        SourceCreator dsc = new SourceCreator("d1").domain("my-domain");
        assertNotNull(dsc.getExternalCreator());

        // ---- null-reset coverage (inherited from StreamSourceCreator) ----
        SourceCreator c = new SourceCreator("nc")
            .startTime(ZDT_B)
            .filterSubject("y.>")
            .externalCreator(new ExternalCreator("a", "d"))
            .subjectTransforms(new SubjectTransformCreator("s", "t"));
        assertEquals(ZDT_B, c.getStartTime());
        assertEquals("y.>", c.getFilterSubject());
        assertNotNull(c.getExternalCreator());
        assertEquals(1, c.getSubjectTransformCreators().size());

        c.startTime(null);
        assertNull(c.getStartTime());
        c.filterSubject(null);
        assertNull(c.getFilterSubject());
        c.externalCreator(null);
        assertNull(c.getExternalCreator());
        assertThrows(NullPointerException.class, () -> c.subjectTransforms((SubjectTransformCreator[]) null));
        assertTrue(c.getSubjectTransformCreators().isEmpty());
    }

    /**
     * StreamCreator smoke test + defaults round trip + focused null/numeric reset.
     * Full coverage of all setters lives in StreamCreatorConfigurationTests.
     */
    @Test
    public void testStreamCreator() {
        // ---- tiny smoke test ----
        StreamCreator sc = new StreamCreator("smoke-stream")
            .subjects("a.>", "b.>")
            .description("smoke");
        StreamConfiguration cfg = new StreamConfiguration(lj(sc.toJson()));
        assertEquals("smoke-stream", cfg.getName());
        assertEquals("smoke", cfg.getDescription());
        assertEquals(2, cfg.getSubjects().size());

        // ---- defaults round trip (StreamCreator's DEFAULT_* constants survive JSON) ----
        StreamCreator dflt = new StreamCreator("defaults-stream");
        // Creator side reports the defaults directly
        assertEquals(StreamCreator.DEFAULT_RETENTION_POLICY, dflt.getRetentionPolicy());
        assertEquals(StreamCreator.DEFAULT_COMPRESSION_OPTION, dflt.getCompressionOption());
        assertEquals(StreamCreator.DEFAULT_STORAGE_TYPE, dflt.getStorageType());
        assertEquals(StreamCreator.DEFAULT_DISCARD_POLICY, dflt.getDiscardPolicy());
        // persistMode default at the Creator level is null (server fills in DEFAULT_PERSIST_MODE)
        assertNull(dflt.getPersistMode());
        // remaining constructor defaults (no setters called)
        assertEquals("defaults-stream", dflt.getName());
        assertNull(dflt.getDescription());
        assertTrue(dflt.getSubjects().isEmpty());
        assertEquals(-1L, dflt.getMaxConsumers());
        assertEquals(-1L, dflt.getMaxMessages());
        assertEquals(-1L, dflt.getMaxMessagesPerSubject());
        assertEquals(-1L, dflt.getMaxBytes());
        assertEquals(-1, dflt.getMaxMessageSize());
        assertNull(dflt.getMaxAge());
        assertEquals(1, dflt.getReplicas());
        assertFalse(dflt.getNoAck());
        assertNull(dflt.getTemplateOwner());
        assertNull(dflt.getDuplicateWindow());
        assertNull(dflt.getPlacementCreator());
        assertNull(dflt.getRepublishCreator());
        assertNull(dflt.getSubjectTransformCreator());
        assertNull(dflt.getConsumerLimitsCreator());
        assertNull(dflt.getMirrorCreator());
        assertTrue(dflt.getSourceCreators().isEmpty());
        assertFalse(dflt.getSealed());
        assertFalse(dflt.getAllowRollup());
        assertFalse(dflt.getAllowDirect());
        assertFalse(dflt.getMirrorDirect());
        assertFalse(dflt.getDenyDelete());
        assertFalse(dflt.getDenyPurge());
        assertFalse(dflt.isDiscardNewPerSubject());
        assertTrue(dflt.getMetadata().isEmpty());
        assertEquals(1L, dflt.getFirstSequence());
        assertNull(dflt.getSubjectDeleteMarkerTtl());
        assertFalse(dflt.getAllowMessageTtl());
        assertFalse(dflt.getAllowMessageSchedules());
        assertFalse(dflt.getAllowMessageCounter());
        assertFalse(dflt.getAllowAtomicPublish());
        assertFalse(dflt.getAllowBatched());

        StreamConfiguration dfltCfg = new StreamConfiguration(lj(dflt.toJson()));
        assertEquals(StreamCreator.DEFAULT_RETENTION_POLICY, dfltCfg.getRetentionPolicy());
        assertEquals(StreamCreator.DEFAULT_COMPRESSION_OPTION, dfltCfg.getCompressionOption());
        assertEquals(StreamCreator.DEFAULT_STORAGE_TYPE, dfltCfg.getStorageType());
        assertEquals(StreamCreator.DEFAULT_DISCARD_POLICY, dfltCfg.getDiscardPolicy());
        assertEquals(StreamCreator.DEFAULT_PERSIST_MODE, dfltCfg.getPersistMode());

        // ---- null-reset coverage for nullable string/object setters ----
        StreamCreator c = new StreamCreator("nr-stream")
            .description("desc")
            .templateOwner("tmpl")
            .placementCreator(new PlacementCreator().cluster("x"))
            .republishCreator(new RepublishCreator("s", "d"))
            .subjectTransformCreator(new SubjectTransformCreator("a", "b"))
            .consumerLimitsCreator(new ConsumerLimitsCreator().inactiveThreshold(Duration.ofSeconds(1)))
            .mirrorCreator(new MirrorCreator("mref"))
            .persistMode(PersistMode.Async)
            .maxAge(Duration.ofSeconds(30))
            .duplicateWindow(Duration.ofSeconds(15));

        assertEquals("desc", c.getDescription());
        assertEquals("tmpl", c.getTemplateOwner());
        assertNotNull(c.getPlacementCreator());
        assertNotNull(c.getRepublishCreator());
        assertNotNull(c.getSubjectTransformCreator());
        assertNotNull(c.getConsumerLimitsCreator());
        assertNotNull(c.getMirrorCreator());
        assertEquals(PersistMode.Async, c.getPersistMode());
        assertEquals(Duration.ofSeconds(30), c.getMaxAge());
        assertEquals(Duration.ofSeconds(15), c.getDuplicateWindow());

        c.description(null);
        assertNull(c.getDescription());
        // empty string also resets to null (emptyAsNull)
        c.description("x").description("");
        assertNull(c.getDescription());

        c.templateOwner(null);
        assertNull(c.getTemplateOwner());

        c.placementCreator(null);
        assertNull(c.getPlacementCreator());
        // PlacementCreator without data also clears
        c.placementCreator(new PlacementCreator());
        assertNull(c.getPlacementCreator());

        c.republishCreator(null);
        assertNull(c.getRepublishCreator());
        c.subjectTransformCreator(null);
        assertNull(c.getSubjectTransformCreator());
        c.consumerLimitsCreator(null);
        assertNull(c.getConsumerLimitsCreator());
        c.mirrorCreator(null);
        assertNull(c.getMirrorCreator());
        c.persistMode(null);
        assertNull(c.getPersistMode());

        // maxAge(null) -> null; maxAge(0L millis) -> null
        c.maxAge((Duration) null);
        assertNull(c.getMaxAge());
        c.maxAge(Duration.ofSeconds(5));
        assertEquals(Duration.ofSeconds(5), c.getMaxAge());
        c.maxAge(0L);
        assertNull(c.getMaxAge());

        // duplicateWindow(null) -> null
        c.duplicateWindow((Duration) null);
        assertNull(c.getDuplicateWindow());
        c.duplicateWindow(0L);
        // millis form with 0 returns null per validateDurationNotRequiredGtOrEqZero(long, null)
        assertNull(c.getDuplicateWindow());

        // ---- numeric-reset coverage ----
        // All Stream max* fields use normalizeLong(x, 1): values < 1 -> UNSET (-1).
        StreamCreator n = new StreamCreator("num-stream");
        n.maxConsumers(50);
        assertEquals(50L, n.getMaxConsumers());
        n.maxConsumers(-1);
        assertEquals(-1L, n.getMaxConsumers());
        n.maxConsumers(0);
        assertEquals(-1L, n.getMaxConsumers());
        n.maxConsumers(-2);
        assertEquals(-1L, n.getMaxConsumers());

        n.maxMessages(100);
        assertEquals(100L, n.getMaxMessages());
        n.maxMessages(0);
        assertEquals(-1L, n.getMaxMessages());
        n.maxMessages(-1);
        assertEquals(-1L, n.getMaxMessages());

        n.maxMessagesPerSubject(10);
        assertEquals(10L, n.getMaxMessagesPerSubject());
        n.maxMessagesPerSubject(0);
        assertEquals(-1L, n.getMaxMessagesPerSubject());
        n.maxMessagesPerSubject(-1);
        assertEquals(-1L, n.getMaxMessagesPerSubject());

        n.maxBytes(2048);
        assertEquals(2048L, n.getMaxBytes());
        n.maxBytes(0);
        assertEquals(-1L, n.getMaxBytes());
        n.maxBytes(-1);
        assertEquals(-1L, n.getMaxBytes());

        n.maxMessageSize(512);
        assertEquals(512, n.getMaxMessageSize());
        n.maxMessageSize(0);
        assertEquals(-1, n.getMaxMessageSize());
        n.maxMessageSize(-1);
        assertEquals(-1, n.getMaxMessageSize());

        // firstSequence: values <= 1 clamp to 1
        n.firstSequence(100);
        assertEquals(100L, n.getFirstSequence());
        n.firstSequence(0);
        assertEquals(1L, n.getFirstSequence());
        n.firstSequence(-5);
        assertEquals(1L, n.getFirstSequence());

        // replicas: must be 1..5; 0 or 6 throws
        n.replicas(3);
        assertEquals(3, n.getReplicas());
        assertThrows(IllegalArgumentException.class, () -> n.replicas(0));
        assertThrows(IllegalArgumentException.class, () -> n.replicas(6));

        // ---- sources / sourceCreators / allowBatched() coverage ----
        StreamCreator s = new StreamCreator("src-stream");

        // sources(Source...) and sources(Collection<Source>) both end up in the
        // sourceCreators list (Source is converted via SourceCreator::new).
        s.sources(new Source(ljData("SourceMinimal.json")), new Source(ljData("Source.json")));
        assertEquals(2, s.getSourceCreators().size());
        s.sources(List.of(new Source(ljData("SourceMinimal.json"))));
        assertEquals(1, s.getSourceCreators().size());

        // sourceCreators(SourceCreator...)
        s.sourceCreators(new SourceCreator("a"), new SourceCreator("b"), new SourceCreator("c"));
        assertEquals(3, s.getSourceCreators().size());

        // sourceCreators(Collection<SourceCreator>)
        s.sourceCreators(Arrays.asList(new SourceCreator("x"), new SourceCreator("y")));
        assertEquals(2, s.getSourceCreators().size());

        // null/empty clears
        s.sourceCreators((Collection<SourceCreator>) null);
        assertTrue(s.getSourceCreators().isEmpty());

        // allowBatched() no-arg sets the flag
        StreamCreator b = new StreamCreator("batched-stream");
        assertFalse(b.getAllowBatched());
        b.allowBatched();
        assertTrue(b.getAllowBatched());
        // boolean overload toggles
        b.allowBatched(false);
        assertFalse(b.getAllowBatched());
    }

    @Test
    public void testSubjectTransformCreator() {
        // ---- construct + creator getters + round trip ----
        SubjectTransformCreator creator = new SubjectTransformCreator("a.>", "b.>");
        assertEquals("a.>", creator.getSource());
        assertEquals("b.>", creator.getDestination());

        SubjectTransform st = new SubjectTransform(lj(creator.toJson()));
        assertEquals("a.>", st.getSource());
        assertEquals("b.>", st.getDestination());

        // ---- from-counterpart constructor ----
        SubjectTransform original = new SubjectTransform(ljData("SubjectTransform.json"));
        SubjectTransformCreator fromCounterpart = new SubjectTransformCreator(original);
        assertEquals("st.src.>", fromCounterpart.getSource());
        assertEquals("st.dest.>", fromCounterpart.getDestination());

        SubjectTransform round = new SubjectTransform(lj(fromCounterpart.toJson()));
        assertEquals(original.getSource(), round.getSource());
        assertEquals(original.getDestination(), round.getDestination());

        SubjectTransform empty = new SubjectTransform(ljData("Empty.json"));
        assertEquals("", empty.getSource());
        assertEquals("", empty.getDestination());
    }

    // ----------------------------------------------------------------------------------------------------
    // Collection invariants: for every collection field on a Creator class, verify that
    //   (1) a default instance returns an empty (non-null) collection, and
    //   (2) the collection can be cleared by passing an empty collection / empty varargs.
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testStreamCreatorCollectionInvariants() {
        // ---- subjects ----
        StreamCreator c = new StreamCreator("x");
        assertNotNull(c.getSubjects());
        assertTrue(c.getSubjects().isEmpty());
        c.subjects("a.>", "b.>");
        assertEquals(2, c.getSubjects().size());
        c.subjects(new ArrayList<>());
        assertTrue(c.getSubjects().isEmpty());
        c.subjects("a.>", "b.>");
        c.subjects();
        assertTrue(c.getSubjects().isEmpty());
        // setter is @Nullable on Collection form
        c.subjects("a.>", "b.>");
        c.subjects((Collection<String>) null);
        assertTrue(c.getSubjects().isEmpty());

        // ---- sourceCreators ----
        c = new StreamCreator("x");
        assertNotNull(c.getSourceCreators());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sourceCreators(new SourceCreator("a"), new SourceCreator("b"));
        assertEquals(2, c.getSourceCreators().size());
        c.sourceCreators(new ArrayList<>());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sourceCreators(new SourceCreator("a"), new SourceCreator("b"));
        c.sourceCreators();
        assertTrue(c.getSourceCreators().isEmpty());
        // Collection form is @Nullable
        c.sourceCreators(new SourceCreator("a"));
        c.sourceCreators((Collection<SourceCreator>) null);
        assertTrue(c.getSourceCreators().isEmpty());
        // sources(Source...) and sources(Collection<Source>) also populate sourceCreators
        c.sources(new Source(ljData("SourceMinimal.json")));
        assertEquals(1, c.getSourceCreators().size());
        c.sources(new ArrayList<>());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sources(new Source(ljData("SourceMinimal.json")));
        c.sources();
        assertTrue(c.getSourceCreators().isEmpty());

        // ---- metadata ----
        c = new StreamCreator("x");
        assertNotNull(c.getMetadata());
        assertTrue(c.getMetadata().isEmpty());
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        c.metadata(m);
        assertEquals(1, c.getMetadata().size());
        c.metadata(new HashMap<>());
        assertTrue(c.getMetadata().isEmpty());
        c.metadata(m);
        c.metadata(null);
        assertTrue(c.getMetadata().isEmpty());
    }

    @Test
    public void testConsumerCreatorCollectionInvariants() {
        // ConsumerCreator is abstract; PullConsumerCreator exposes all collection setters.

        // ---- filterSubjects ----
        PullConsumerCreator c = new PullConsumerCreator();
        assertNotNull(c.getFilterSubjects());
        assertTrue(c.getFilterSubjects().isEmpty());
        c.filterSubjects("a.>", "b.>");
        assertEquals(2, c.getFilterSubjects().size());
        c.filterSubjects(new ArrayList<>());
        assertTrue(c.getFilterSubjects().isEmpty());
        c.filterSubjects("a.>", "b.>");
        c.filterSubjects();
        assertTrue(c.getFilterSubjects().isEmpty());
        // List form is @Nullable
        c.filterSubjects("a.>", "b.>");
        c.filterSubjects((List<String>) null);
        assertTrue(c.getFilterSubjects().isEmpty());

        // ---- backoff (both Duration... and long... varargs overloads exist; no-arg
        // form would be ambiguous, so we clear via empty typed arrays) ----
        c = new PullConsumerCreator();
        assertNotNull(c.getBackoff());
        assertTrue(c.getBackoff().isEmpty());
        c.backoff(Duration.ofSeconds(1), Duration.ofSeconds(2));
        assertEquals(2, c.getBackoff().size());
        c.backoff(new Duration[0]);
        assertTrue(c.getBackoff().isEmpty());
        // long... form: clears via empty array
        c.backoff(1000L, 2000L);
        assertEquals(2, c.getBackoff().size());
        c.backoff(new long[0]);
        assertTrue(c.getBackoff().isEmpty());

        // ---- metadata ----
        c = new PullConsumerCreator();
        assertNotNull(c.getMetadata());
        assertTrue(c.getMetadata().isEmpty());
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        c.metadata(m);
        assertEquals(1, c.getMetadata().size());
        c.metadata(new HashMap<>());
        assertTrue(c.getMetadata().isEmpty());
        // setter is @Nullable
        c.metadata(m);
        c.metadata(null);
        assertTrue(c.getMetadata().isEmpty());

        // ---- priorityGroups ----
        c = new PullConsumerCreator();
        assertNotNull(c.getPriorityGroups());
        assertTrue(c.getPriorityGroups().isEmpty());
        c.priorityGroups("g1", "g2");
        assertEquals(2, c.getPriorityGroups().size());
        c.priorityGroups(new ArrayList<>());
        assertTrue(c.getPriorityGroups().isEmpty());
        c.priorityGroups("g1", "g2");
        c.priorityGroups();
        assertTrue(c.getPriorityGroups().isEmpty());
        // List form is @Nullable
        c.priorityGroups("g1", "g2");
        c.priorityGroups((List<String>) null);
        assertTrue(c.getPriorityGroups().isEmpty());
    }

    @Test
    public void testPlacementCreatorCollectionInvariants() {
        // ---- tags ----
        PlacementCreator c = new PlacementCreator();
        assertNotNull(c.getTags());
        assertTrue(c.getTags().isEmpty());
        c.tags("a", "b");
        assertEquals(2, c.getTags().size());
        c.tags(new ArrayList<>());
        assertTrue(c.getTags().isEmpty());
        c.tags("a", "b");
        c.tags();
        assertTrue(c.getTags().isEmpty());
        // List form is @Nullable
        c.tags("a", "b");
        c.tags((List<String>) null);
        assertTrue(c.getTags().isEmpty());
    }

    @Test
    public void testStreamSourceCreatorCollectionInvariants() {
        // StreamSourceCreator is abstract; both MirrorCreator and SourceCreator inherit it.

        // ---- MirrorCreator.subjectTransformCreators ----
        MirrorCreator m = new MirrorCreator("m");
        assertNotNull(m.getSubjectTransformCreators());
        assertTrue(m.getSubjectTransformCreators().isEmpty());
        m.subjectTransforms(new SubjectTransformCreator("a", "b"), new SubjectTransformCreator("c", "d"));
        assertEquals(2, m.getSubjectTransformCreators().size());
        m.subjectTransforms(new ArrayList<>());
        assertTrue(m.getSubjectTransformCreators().isEmpty());
        m.subjectTransforms(new SubjectTransformCreator("a", "b"));
        m.subjectTransforms();
        assertTrue(m.getSubjectTransformCreators().isEmpty());
        // List form is @Nullable
        m.subjectTransforms(new SubjectTransformCreator("a", "b"));
        m.subjectTransforms((List<SubjectTransformCreator>) null);
        assertTrue(m.getSubjectTransformCreators().isEmpty());

        // ---- SourceCreator.subjectTransformCreators ----
        SourceCreator s = new SourceCreator("s");
        assertNotNull(s.getSubjectTransformCreators());
        assertTrue(s.getSubjectTransformCreators().isEmpty());
        s.subjectTransforms(new SubjectTransformCreator("a", "b"), new SubjectTransformCreator("c", "d"));
        assertEquals(2, s.getSubjectTransformCreators().size());
        s.subjectTransforms(new ArrayList<>());
        assertTrue(s.getSubjectTransformCreators().isEmpty());
        s.subjectTransforms(new SubjectTransformCreator("a", "b"));
        s.subjectTransforms();
        assertTrue(s.getSubjectTransformCreators().isEmpty());
        s.subjectTransforms(new SubjectTransformCreator("a", "b"));
        s.subjectTransforms((List<SubjectTransformCreator>) null);
        assertTrue(s.getSubjectTransformCreators().isEmpty());
    }

    // ====================================================================================================
    // PART 2: API-only classes (no Creator counterpart) — server-side wrappers
    // ====================================================================================================

    @Test
    public void testSubject() {
        Subject s = new Subject("foo.bar", 42L);
        assertEquals("foo.bar", s.getName());
        assertEquals(42L, s.getCount());
        assertNotNull(s.toString());

        // Compare
        Subject other = new Subject("foo.aaa", 1L);
        assertTrue(s.compareTo(other) > 0);
        assertTrue(other.compareTo(s) < 0);
        assertEquals(0, s.compareTo(new Subject("foo.bar", 99L))); // name-only comparison
    }

    // ====================================================================================================
    // PART 4: Request POJOs
    // ====================================================================================================

    @Test
    public void testConsumerPauseRequest() {
        ConsumerPauseRequest req = new ConsumerPauseRequest(ZDT_A);
        String json = req.toJson();
        assertNotNull(json);
        assertTrue(json.contains("pause_until"));
    }

    @Test
    public void testMessageDeleteRequest() {
        // Direct constructor
        MessageDeleteRequest direct = new MessageDeleteRequest(42L, true);
        assertEquals(42L, direct.getSequence());
        assertTrue(direct.isErase());
        assertFalse(direct.isNoErase());
        String json = direct.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"seq\":42"));

        // erase=false
        MessageDeleteRequest noErase = new MessageDeleteRequest(7L, false);
        assertFalse(noErase.isErase());
        assertTrue(noErase.isNoErase());

        // Builder default state
        MessageDeleteRequest defaultBuilt = MessageDeleteRequest.builder().build();
        assertEquals(-1L, defaultBuilt.getSequence());
        assertTrue(defaultBuilt.isErase()); // default

        // Builder set sequence
        MessageDeleteRequest seqBuilt = MessageDeleteRequest.builder().sequence(100L).build();
        assertEquals(100L, seqBuilt.getSequence());

        // Builder sequence < 1 clamps to -1
        MessageDeleteRequest clamped = MessageDeleteRequest.builder().sequence(0L).build();
        assertEquals(-1L, clamped.getSequence());

        // Builder erase() and noErase()
        MessageDeleteRequest erased = MessageDeleteRequest.builder().sequence(5L).erase().build();
        assertTrue(erased.isErase());

        MessageDeleteRequest notErased = MessageDeleteRequest.builder().sequence(5L).noErase().build();
        assertFalse(notErased.isErase());
    }

    @Test
    public void testMessageGetRequest() {
        // forSequence
        MessageGetRequest forSeq = MessageGetRequest.forSequence(42L);
        assertEquals(42L, forSeq.getSequence());
        assertNull(forSeq.getLastBySubject());
        assertNull(forSeq.getNextBySubject());
        assertNull(forSeq.getStartTime());
        assertTrue(forSeq.isSequenceOnly());
        assertFalse(forSeq.isLastBySubject());
        assertFalse(forSeq.isNextBySubject());

        // lastForSubject
        MessageGetRequest last = MessageGetRequest.lastForSubject("a.>");
        assertEquals(-1L, last.getSequence());
        assertEquals("a.>", last.getLastBySubject());
        assertTrue(last.isLastBySubject());
        assertFalse(last.isSequenceOnly());

        // firstForSubject
        MessageGetRequest first = MessageGetRequest.firstForSubject("b.>");
        assertEquals(-1L, first.getSequence());
        assertEquals("b.>", first.getNextBySubject());
        assertTrue(first.isNextBySubject());

        // firstForStartTime
        MessageGetRequest forStart = MessageGetRequest.firstForStartTime(ZDT_A);
        assertEquals(-1L, forStart.getSequence());
        assertEquals(ZDT_A, forStart.getStartTime());

        // firstForStartTimeAndSubject
        MessageGetRequest forStartAndSubj = MessageGetRequest.firstForStartTimeAndSubject(ZDT_A, "c.>");
        assertEquals(ZDT_A, forStartAndSubj.getStartTime());
        assertEquals("c.>", forStartAndSubj.getNextBySubject());

        // nextForSubject
        MessageGetRequest next = MessageGetRequest.nextForSubject(7L, "d.>");
        assertEquals(7L, next.getSequence());
        assertEquals("d.>", next.getNextBySubject());
        assertTrue(next.isNextBySubject());
        assertFalse(next.isSequenceOnly()); // has nextBySubject

        // toJson never null
        assertNotNull(next.toJson());
        assertTrue(next.toJson().contains("\"seq\":7"));
    }

    @Test
    public void testStreamInfoOptions() {
        // Default build
        StreamInfoOptions empty = StreamInfoOptions.builder().build();
        assertNull(empty.getSubjectsFilter());
        assertFalse(empty.isDeletedDetails());

        // filterSubjects
        StreamInfoOptions filter = StreamInfoOptions.filterSubjects("a.>");
        assertEquals("a.>", filter.getSubjectsFilter());
        assertFalse(filter.isDeletedDetails());

        // empty string filter -> null
        StreamInfoOptions emptyFilter = StreamInfoOptions.builder().filterSubjects("").build();
        assertNull(emptyFilter.getSubjectsFilter());

        // allSubjects
        StreamInfoOptions all = StreamInfoOptions.allSubjects();
        assertEquals(">", all.getSubjectsFilter());

        // deletedDetails
        StreamInfoOptions deleted = StreamInfoOptions.deletedDetails();
        assertTrue(deleted.isDeletedDetails());

        // Combined builder
        StreamInfoOptions both = StreamInfoOptions.builder()
            .filterSubjects("x.>")
            .deletedDetails()
            .build();
        assertEquals("x.>", both.getSubjectsFilter());
        assertTrue(both.isDeletedDetails());

        String json = both.toJson();
        assertNotNull(json);
        assertTrue(json.contains("x.>"));
    }

    @Test
    public void testSubscribeBehavior() {
        SubscribeBehavior sb = new SubscribeBehavior();
        assertNull(sb.getDispatcher());
        assertNull(sb.getHandler());
        assertEquals(0L, sb.getMessageAlarmTime());
        assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, sb.getPendingMessageLimit());
        assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, sb.getPendingByteLimit());

        MessageHandler handler = m -> { /* no-op */ };

        sb.handler(handler)
            .messageAlarmTime(123L)
            .pendingMessageLimit(456L)
            .pendingByteLimit(789L);

        assertEquals(handler, sb.getHandler());
        assertEquals(123L, sb.getMessageAlarmTime());
        assertEquals(456L, sb.getPendingMessageLimit());
        assertEquals(789L, sb.getPendingByteLimit());

        // Copy via subscribeBehavior
        SubscribeBehavior copy = new SubscribeBehavior(sb);
        assertEquals(sb, copy);
        assertEquals(123L, copy.getMessageAlarmTime());
        assertEquals(456L, copy.getPendingMessageLimit());
        assertEquals(789L, copy.getPendingByteLimit());
    }

    @Test
    public void testAckPolicyCoverage() {
        assertEquals("none", AckPolicy.None.toString());
        assertEquals("all", AckPolicy.All.toString());
        assertEquals("explicit", AckPolicy.Explicit.toString());
        assertEquals("flow_control", AckPolicy.FlowControl.toString());

        assertEquals(AckPolicy.None, AckPolicy.get("none", AckPolicy.All));
        assertEquals(AckPolicy.All, AckPolicy.get("all", AckPolicy.None));
        assertEquals(AckPolicy.Explicit, AckPolicy.get("explicit", AckPolicy.None));
        assertEquals(AckPolicy.FlowControl, AckPolicy.get("flow_control", AckPolicy.None));

        // null and unknown both fall back to the default
        assertEquals(AckPolicy.None, AckPolicy.get(null, AckPolicy.None));
        assertEquals(AckPolicy.None, AckPolicy.get("no-match", AckPolicy.None));
    }

    @Test
    public void testCompressionOptionCoverage() {
        assertEquals("none", CompressionOption.None.toString());
        assertEquals("s2", CompressionOption.S2.toString());

        assertEquals(CompressionOption.None, CompressionOption.get("none", CompressionOption.S2));
        assertEquals(CompressionOption.S2, CompressionOption.get("s2", CompressionOption.None));

        // null and unknown both fall back to the default
        assertEquals(CompressionOption.None, CompressionOption.get(null, CompressionOption.None));
        assertEquals(CompressionOption.None, CompressionOption.get("no-match", CompressionOption.None));
    }

    @Test
    public void testDeliverPolicyCoverage() {
        assertEquals("all", DeliverPolicy.All.toString());
        assertEquals("last", DeliverPolicy.Last.toString());
        assertEquals("new", DeliverPolicy.New.toString());
        assertEquals("by_start_sequence", DeliverPolicy.ByStartSequence.toString());
        assertEquals("by_start_time", DeliverPolicy.ByStartTime.toString());
        assertEquals("last_per_subject", DeliverPolicy.LastPerSubject.toString());

        assertEquals(DeliverPolicy.All, DeliverPolicy.get("all", DeliverPolicy.Last));
        assertEquals(DeliverPolicy.Last, DeliverPolicy.get("last", DeliverPolicy.All));
        assertEquals(DeliverPolicy.New, DeliverPolicy.get("new", DeliverPolicy.All));
        assertEquals(DeliverPolicy.ByStartSequence, DeliverPolicy.get("by_start_sequence", DeliverPolicy.All));
        assertEquals(DeliverPolicy.ByStartTime, DeliverPolicy.get("by_start_time", DeliverPolicy.All));
        assertEquals(DeliverPolicy.LastPerSubject, DeliverPolicy.get("last_per_subject", DeliverPolicy.All));

        // null and unknown both fall back to the default
        assertEquals(DeliverPolicy.All, DeliverPolicy.get(null, DeliverPolicy.All));
        assertEquals(DeliverPolicy.All, DeliverPolicy.get("no-match", DeliverPolicy.All));
    }

    @Test
    public void testDiscardPolicyCoverage() {
        assertEquals("new", DiscardPolicy.New.toString());
        assertEquals("old", DiscardPolicy.Old.toString());

        assertEquals(DiscardPolicy.New, DiscardPolicy.get("new", DiscardPolicy.Old));
        assertEquals(DiscardPolicy.Old, DiscardPolicy.get("old", DiscardPolicy.New));

        // null and unknown both fall back to the default
        assertEquals(DiscardPolicy.New, DiscardPolicy.get(null, DiscardPolicy.New));
        assertEquals(DiscardPolicy.New, DiscardPolicy.get("no-match", DiscardPolicy.New));
    }

    @Test
    public void testPersistModeCoverage() {
        assertEquals("default", PersistMode.Default.toString());
        assertEquals("async", PersistMode.Async.toString());

        assertEquals(PersistMode.Default, PersistMode.get("default", PersistMode.Async));
        assertEquals(PersistMode.Async, PersistMode.get("async", PersistMode.Default));

        // null and unknown both fall back to the default
        assertEquals(PersistMode.Default, PersistMode.get(null, PersistMode.Default));
        assertEquals(PersistMode.Default, PersistMode.get("no-match", PersistMode.Default));
    }

    @Test
    public void testPriorityPolicyCoverage() {
        assertEquals("none", PriorityPolicy.None.toString());
        assertEquals("overflow", PriorityPolicy.Overflow.toString());
        assertEquals("prioritized", PriorityPolicy.Prioritized.toString());
        assertEquals("pinned_client", PriorityPolicy.PinnedClient.toString());

        assertEquals(PriorityPolicy.None, PriorityPolicy.get("none", PriorityPolicy.Overflow));
        assertEquals(PriorityPolicy.Overflow, PriorityPolicy.get("overflow", PriorityPolicy.None));
        assertEquals(PriorityPolicy.Prioritized, PriorityPolicy.get("prioritized", PriorityPolicy.None));
        assertEquals(PriorityPolicy.PinnedClient, PriorityPolicy.get("pinned_client", PriorityPolicy.None));

        // null and unknown both fall back to the default
        assertEquals(PriorityPolicy.None, PriorityPolicy.get(null, PriorityPolicy.None));
        assertEquals(PriorityPolicy.None, PriorityPolicy.get("no-match", PriorityPolicy.None));
    }

    @Test
    public void testReplayPolicyCoverage() {
        assertEquals("instant", ReplayPolicy.Instant.toString());
        assertEquals("original", ReplayPolicy.Original.toString());

        assertEquals(ReplayPolicy.Instant, ReplayPolicy.get("instant", ReplayPolicy.Original));
        assertEquals(ReplayPolicy.Original, ReplayPolicy.get("original", ReplayPolicy.Instant));

        // null and unknown both fall back to the default
        assertEquals(ReplayPolicy.Instant, ReplayPolicy.get(null, ReplayPolicy.Instant));
        assertEquals(ReplayPolicy.Instant, ReplayPolicy.get("no-match", ReplayPolicy.Instant));
    }

    @Test
    public void testRetentionPolicyCoverage() {
        assertEquals("limits", RetentionPolicy.Limits.toString());
        assertEquals("interest", RetentionPolicy.Interest.toString());
        assertEquals("workqueue", RetentionPolicy.WorkQueue.toString());

        assertEquals(RetentionPolicy.Limits, RetentionPolicy.get("limits", RetentionPolicy.Interest));
        assertEquals(RetentionPolicy.Interest, RetentionPolicy.get("interest", RetentionPolicy.Limits));
        assertEquals(RetentionPolicy.WorkQueue, RetentionPolicy.get("workqueue", RetentionPolicy.Limits));

        // null and unknown both fall back to the default
        assertEquals(RetentionPolicy.Limits, RetentionPolicy.get(null, RetentionPolicy.Limits));
        assertEquals(RetentionPolicy.Limits, RetentionPolicy.get("no-match", RetentionPolicy.Limits));
    }

    @Test
    public void testStorageTypeCoverage() {
        assertEquals("file", StorageType.File.toString());
        assertEquals("memory", StorageType.Memory.toString());

        assertEquals(StorageType.File, StorageType.get("file", StorageType.Memory));
        assertEquals(StorageType.Memory, StorageType.get("memory", StorageType.File));

        // null and unknown both fall back to the default
        assertEquals(StorageType.File, StorageType.get(null, StorageType.File));
        assertEquals(StorageType.File, StorageType.get("no-match", StorageType.File));
    }
}
