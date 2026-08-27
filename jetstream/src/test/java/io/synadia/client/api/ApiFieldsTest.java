package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsConstants;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

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
        ConsumerLimits original = new ConsumerLimits(lj("{\"inactive_threshold\":60000000000,\"max_ack_pending\":100}"));
        ConsumerLimitsCreator fromCounterpart = new ConsumerLimitsCreator(original);
        assertEquals(Duration.ofSeconds(60), fromCounterpart.getInactiveThreshold());
        assertEquals(100L, fromCounterpart.getMaxAckPending());

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
        External original = new External(lj("{\"api\":\"api1\",\"deliver\":\"d1\"}"));
        ExternalCreator fromCounterpart = new ExternalCreator(original);
        assertEquals("api1", fromCounterpart.getApi());
        assertEquals("d1", fromCounterpart.getDeliver());

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
        External e = new External(lj("{}"));
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

        Mirror empty = new Mirror(lj("{}"));
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
        String json = "{"
            + "\"name\":\"m1\","
            + "\"opt_start_seq\":5,"
            + "\"filter_subject\":\"x.>\","
            + "\"external\":{\"api\":\"a\",\"deliver\":\"d\"},"
            + "\"subject_transforms\":[{\"src\":\"s\",\"dest\":\"d\"}]"
            + "}";
        Mirror original = new Mirror(lj(json));
        MirrorCreator fromCounterpart = new MirrorCreator(original);
        assertEquals("m1", fromCounterpart.getStreamName());
        assertEquals(5L, fromCounterpart.getStartSequence());
        assertEquals("x.>", fromCounterpart.getFilterSubject());
        assertNotNull(fromCounterpart.getExternalCreator());
        assertEquals(1, fromCounterpart.getSubjectTransformCreators().size());

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
        Placement original = new Placement(lj("{\"cluster\":\"c1\",\"tags\":[\"t1\",\"t2\"]}"));
        PlacementCreator fromCounterpart = new PlacementCreator(original);
        assertEquals("c1", fromCounterpart.getCluster());
        assertEquals(2, fromCounterpart.getTags().size());
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
            .subjects("the.subject")
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
            .subjects("a.>", "b.>", "c.>");
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
            .subjects("f.>")
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
        c.subjects("");
        assertNull(c.getFilterSubject());
        assertTrue(c.getFilterSubjects().isEmpty());
        c.subjects("a.>").subjects("");
        assertTrue(c.getFilterSubjects().isEmpty());

        // ---- filterSubjects array/list null -> clears ----
        c.subjects("a.>", "b.>");
        assertEquals(2, c.getFilterSubjects().size());
        assertThrows(NullPointerException.class, () -> c.subjects((String[]) null));
        assertTrue(c.getFilterSubjects().isEmpty());
        c.subjects("a.>", "b.>");
        c.subjects((List<String>) null);
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

        Republish empty = new Republish(lj("{}"));
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
        Republish original = new Republish(lj("{\"src\":\"s.>\",\"dest\":\"d.>\",\"headers_only\":true}"));
        RepublishCreator fromCounterpart = new RepublishCreator(original);
        assertEquals("s.>", fromCounterpart.getSource());
        assertEquals("d.>", fromCounterpart.getDestination());
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
        String json = "{"
            + "\"name\":\"s1\","
            + "\"opt_start_seq\":11,"
            + "\"filter_subject\":\"y.>\""
            + "}";
        Source original = new Source(lj(json));
        SourceCreator fromCounterpart = new SourceCreator(original);
        assertEquals("s1", fromCounterpart.getStreamName());
        assertEquals(11L, fromCounterpart.getStartSequence());
        assertEquals("y.>", fromCounterpart.getFilterSubject());

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
        s.sources(new Source(lj("{\"name\":\"s1\"}")), new Source(lj("{\"name\":\"s2\"}")));
        assertEquals(2, s.getSourceCreators().size());
        s.sources(List.of(new Source(lj("{\"name\":\"s3\"}"))));
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
        SubjectTransform original = new SubjectTransform(lj("{\"src\":\"a.>\",\"dest\":\"b.>\"}"));
        SubjectTransformCreator fromCounterpart = new SubjectTransformCreator(original);
        assertEquals("a.>", fromCounterpart.getSource());
        assertEquals("b.>", fromCounterpart.getDestination());

        SubjectTransform round = new SubjectTransform(lj(fromCounterpart.toJson()));
        assertEquals(original.getSource(), round.getSource());
        assertEquals(original.getDestination(), round.getDestination());

        SubjectTransform empty = new SubjectTransform(lj("{}"));
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
        c.sources(new Source(lj("{\"name\":\"s1\"}")));
        assertEquals(1, c.getSourceCreators().size());
        c.sources(new ArrayList<>());
        assertTrue(c.getSourceCreators().isEmpty());
        c.sources(new Source(lj("{\"name\":\"s1\"}")));
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
        c.subjects("a.>", "b.>");
        assertEquals(2, c.getFilterSubjects().size());
        c.subjects(new ArrayList<>());
        assertTrue(c.getFilterSubjects().isEmpty());
        c.subjects("a.>", "b.>");
        c.subjects();
        assertTrue(c.getFilterSubjects().isEmpty());
        // List form is @Nullable
        c.subjects("a.>", "b.>");
        c.subjects((List<String>) null);
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
    public void testAccountLimits() {
        String json = "{"
            + "\"max_memory\":1000,"
            + "\"max_storage\":2000,"
            + "\"max_streams\":50,"
            + "\"max_consumers\":75,"
            + "\"max_ack_pending\":42,"
            + "\"memory_max_stream_bytes\":3000,"
            + "\"storage_max_stream_bytes\":4000,"
            + "\"max_bytes_required\":true"
            + "}";
        AccountLimits limits = new AccountLimits(lj(json));
        assertEquals(1000L, limits.getMaxMemory());
        assertEquals(2000L, limits.getMaxStorage());
        assertEquals(50L, limits.getMaxStreams());
        assertEquals(75L, limits.getMaxConsumers());
        assertEquals(42L, limits.getMaxAckPending());
        assertEquals(3000L, limits.getMemoryMaxStreamBytes());
        assertEquals(4000L, limits.getStorageMaxStreamBytes());
        assertTrue(limits.isMaxBytesRequired());
        assertNotNull(limits.toString());

        // defaults / not present
        AccountLimits empty = new AccountLimits(lj("{}"));
        assertEquals(-1L, empty.getMaxMemory());
        assertEquals(-1L, empty.getMaxStorage());
        assertFalse(empty.isMaxBytesRequired());
    }

    @Test
    public void testAccountTier() {
        String json = "{"
            + "\"memory\":11,"
            + "\"storage\":22,"
            + "\"reserved_memory\":33,"
            + "\"reserved_storage\":44,"
            + "\"streams\":5,"
            + "\"consumers\":6,"
            + "\"limits\":{\"max_memory\":1000,\"max_storage\":2000}"
            + "}";
        AccountTier tier = new AccountTier(lj(json));
        assertEquals(11L, tier.getMemoryBytes());
        assertEquals(22L, tier.getStorageBytes());
        assertEquals(33L, tier.getReservedMemoryBytes());
        assertEquals(44L, tier.getReservedStorageBytes());
        assertEquals(5L, tier.getStreams());
        assertEquals(6L, tier.getConsumers());

        // exercises lazy _limits field
        AccountLimits limits = tier.getLimits();
        assertNotNull(limits);
        assertEquals(1000L, limits.getMaxMemory());
        assertEquals(2000L, limits.getMaxStorage());

        // second call returns cached instance
        AccountLimits limits2 = tier.getLimits();
        assertEquals(limits, limits2);
        assertNotNull(tier.toString());
    }

    @Test
    public void testApiStats() {
        String json = "{"
            + "\"level\":99,"
            + "\"total\":1234,"
            + "\"errors\":56,"
            + "\"inflight\":7"
            + "}";
        ApiStats stats = new ApiStats(lj(json));
        assertEquals(99, stats.getLevel());
        assertEquals(1234L, stats.getTotal());
        assertEquals(56L, stats.getErrors());
        assertEquals(7L, stats.getInFlight());
        assertNotNull(stats.toString());

        ApiStats empty = new ApiStats(lj("{}"));
        assertEquals(0, empty.getLevel());
        assertEquals(0L, empty.getTotal());
    }

    @Test
    public void testClusterInfo() {
        String json = "{"
            + "\"name\":\"clstr1\","
            + "\"raft_group\":\"raft1\","
            + "\"leader\":\"node-a\","
            + "\"leader_since\":\"2026-01-02T03:04:05Z\","
            + "\"system_account\":true,"
            + "\"traffic_account\":\"acct-x\","
            + "\"replicas\":["
            + "{\"name\":\"r1\",\"current\":true,\"offline\":false,\"active\":1000000000,\"lag\":5},"
            + "{\"name\":\"r2\",\"current\":false,\"offline\":true,\"active\":2000000000,\"lag\":10}"
            + "]"
            + "}";
        ClusterInfo ci = new ClusterInfo(lj(json));
        assertEquals("clstr1", ci.getName());
        assertEquals("raft1", ci.getRaftGroup());
        assertEquals("node-a", ci.getLeader());
        assertNotNull(ci.getLeaderSince());
        assertTrue(ci.isSystemAccount());
        assertEquals("acct-x", ci.getTrafficAccount());

        // exercises lazy _replicas
        List<Replica> replicas = ci.getReplicas();
        assertNotNull(replicas);
        assertEquals(2, replicas.size());
        assertEquals("r1", replicas.get(0).getName());
        assertTrue(replicas.get(0).isCurrent());
        assertFalse(replicas.get(0).isOffline());
        assertEquals(Duration.ofSeconds(1), replicas.get(0).getActive());
        assertEquals(5L, replicas.get(0).getLag());

        // second call returns cached
        assertEquals(replicas, ci.getReplicas());
        assertNotNull(ci.toString());

        // empty fields default behavior
        ClusterInfo empty = new ClusterInfo(lj("{}"));
        assertNull(empty.getName());
        assertNull(empty.getRaftGroup());
        assertNull(empty.getLeader());
        assertNull(empty.getLeaderSince());
        assertFalse(empty.isSystemAccount());
        assertNull(empty.getTrafficAccount());
        assertTrue(empty.getReplicas().isEmpty());
    }

    @Test
    public void testError() {
        // From JSON
        Error err = new Error(lj("{\"code\":400,\"err_code\":10003,\"description\":\"bad request\"}"));
        assertEquals(400, err.getCode());
        assertEquals(10003, err.getApiErrorCode());
        assertEquals("bad request", err.getDescription());
        assertTrue(err.toString().contains("bad request"));
        assertTrue(err.toString().contains("10003"));

        // Empty json - default description
        Error errEmpty = new Error(lj("{}"));
        assertEquals(Error.NOT_SET, errEmpty.getCode());
        assertEquals(Error.NOT_SET, errEmpty.getApiErrorCode());
        assertEquals("Unknown JetStream Error", errEmpty.getDescription());
        // toString: both NOT_SET should just be the description
        assertEquals("Unknown JetStream Error", errEmpty.toString());

        // optionalInstance
        assertNull(Error.optionalInstance(null));
        assertNotNull(Error.optionalInstance(lj("{}")));

        // code-only branch (apiErrorCode NOT_SET, code set)
        Error errCodeOnly = new Error(lj("{\"code\":500,\"description\":\"d\"}"));
        String s = errCodeOnly.toString();
        assertTrue(s.contains("d"));
        assertTrue(s.contains("500"));

        // err_code only, code NOT_SET branch
        Error errApi = new Error(lj("{\"err_code\":42,\"description\":\"e\"}"));
        assertEquals(Error.NOT_SET, errApi.getCode());
        assertEquals(42, errApi.getApiErrorCode());
        // toString: code is NOT_SET and apiErrorCode is set -> description
        assertEquals("e", errApi.toString());

        // Pre-defined errors
        assertEquals(400, Error.JsBadRequestErr.getCode());
        assertEquals(10003, Error.JsBadRequestErr.getApiErrorCode());
        assertEquals(404, Error.JsNoMessageFoundErr.getCode());
        assertEquals(10037, Error.JsNoMessageFoundErr.getApiErrorCode());
    }

    @Test
    public void testLostStreamData() {
        String json = "{\"msgs\":[1,2,3,4],\"bytes\":1024}";
        LostStreamData lost = new LostStreamData(lj(json));
        List<Long> msgs = lost.getMessages();
        assertEquals(4, msgs.size());
        assertEquals(1L, msgs.get(0));
        assertEquals(4L, msgs.get(3));
        Long bytes = lost.getBytes();
        assertNotNull(bytes);
        assertEquals(1024L, bytes);
        assertNotNull(lost.toString());

        // missing fields
        LostStreamData empty = new LostStreamData(lj("{}"));
        assertTrue(empty.getMessages().isEmpty());
        assertNull(empty.getBytes());
    }

    @Test
    public void testMirrorInfo() {
        String json = "{"
            + "\"name\":\"mname\","
            + "\"filter_subject\":\"x.>\","
            + "\"lag\":7,"
            + "\"active\":5000000000,"
            + "\"external\":{\"api\":\"api1\",\"deliver\":\"d1\"},"
            + "\"subject_transforms\":[{\"src\":\"s1\",\"dest\":\"d1\"}],"
            + "\"error\":{\"code\":500,\"err_code\":12345,\"description\":\"oops\"}"
            + "}";
        MirrorInfo info = new MirrorInfo(lj(json));
        assertEquals("mname", info.getName());
        assertEquals("x.>", info.getFilterSubject());
        assertEquals(7L, info.getLag());
        assertEquals(Duration.ofSeconds(5), info.getActive());

        MirrorInfo empty = new MirrorInfo(lj("{}"));
        assertEquals("", empty.getName());
        assertEquals(Duration.ZERO, empty.getActive());

        External ext = info.getExternal();
        assertNotNull(ext);
        assertEquals("api1", ext.getApi());
        assertEquals("d1", ext.getDeliver());

        List<SubjectTransform> sts = info.getSubjectTransforms();
        assertEquals(1, sts.size());
        assertEquals("s1", sts.get(0).getSource());

        Error err = info.getError();
        assertNotNull(err);
        assertEquals(500, err.getCode());
        assertEquals(12345, err.getApiErrorCode());
        assertEquals("oops", err.getDescription());

        assertNotNull(info.toString());

        // negative active returns null
        MirrorInfo neg = new MirrorInfo(lj("{\"name\":\"n\",\"active\":-1}"));
        assertEquals(Duration.ZERO, neg.getActive());
        assertNull(neg.getExternal());
        assertTrue(neg.getSubjectTransforms().isEmpty());
        assertNull(neg.getError());
    }

    @Test
    public void testPriorityGroupState() {
        String json = "{"
            + "\"group\":\"grp-A\","
            + "\"pinned_client_id\":\"cli-1\","
            + "\"pinned_ts\":\"2026-01-02T03:04:05Z\""
            + "}";
        PriorityGroupState pgs = new PriorityGroupState(lj(json));
        assertEquals("grp-A", pgs.getGroup());
        assertEquals("cli-1", pgs.getPinnedClientId());
        assertNotNull(pgs.getPinnedTime());
        assertNotNull(pgs.toString());

        PriorityGroupState minimal = new PriorityGroupState(lj("{\"group\":\"g\"}"));
        assertEquals("g", minimal.getGroup());
        assertNull(minimal.getPinnedClientId());
        assertNull(minimal.getPinnedTime());

        PriorityGroupState empty = new PriorityGroupState(lj("{}"));
        assertEquals("", empty.getGroup());
    }

    @Test
    public void testReplica() {
        String json = "{"
            + "\"name\":\"node-1\","
            + "\"current\":true,"
            + "\"offline\":false,"
            + "\"active\":10000000000,"
            + "\"lag\":2"
            + "}";
        Replica r = new Replica(lj(json));
        assertEquals("node-1", r.getName());
        assertTrue(r.isCurrent());
        assertFalse(r.isOffline());
        assertEquals(Duration.ofSeconds(10), r.getActive());
        assertEquals(2L, r.getLag());
        assertNotNull(r.toString());

        // no active -> Duration.ZERO
        Replica r2 = new Replica(lj("{\"name\":\"n\"}"));
        assertEquals("n", r2.getName());
        assertEquals(Duration.ZERO, r2.getActive());
        assertEquals(0L, r2.getLag());

        // no active -> Duration.ZERO
        Replica empty = new Replica(lj("{}"));
        assertEquals("", empty.getName());
        assertEquals(Duration.ZERO, empty.getActive());
    }

    @Test
    public void testSequenceInfo() {
        String json = "{"
            + "\"consumer_seq\":11,"
            + "\"stream_seq\":22,"
            + "\"last_active\":\"2026-01-02T03:04:05Z\""
            + "}";
        SequenceInfo si = new SequenceInfo(lj(json));
        assertEquals(11L, si.getConsumerSequence());
        assertEquals(22L, si.getStreamSequence());
        assertNotNull(si.getLastActive());
        assertNotNull(si.toString());

        SequenceInfo empty = new SequenceInfo(lj("{}"));
        assertEquals(0L, empty.getConsumerSequence());
        assertEquals(0L, empty.getStreamSequence());
        assertNull(empty.getLastActive());
    }

    @Test
    public void testSourceInfo() {
        String json = "{"
            + "\"name\":\"sname\","
            + "\"filter_subject\":\"y.>\","
            + "\"lag\":11,"
            + "\"active\":3000000000"
            + "}";
        SourceInfo info = new SourceInfo(lj(json));
        assertEquals("sname", info.getName());
        assertEquals("y.>", info.getFilterSubject());
        assertEquals(11L, info.getLag());
        assertEquals(Duration.ofSeconds(3), info.getActive());
        assertNull(info.getExternal());
        assertTrue(info.getSubjectTransforms().isEmpty());
        assertNull(info.getError());
        assertNotNull(info.toString());
    }

    @Test
    public void testStreamAlternate() {
        String json = "{"
            + "\"name\":\"alt-stream\","
            + "\"domain\":\"dom1\","
            + "\"cluster\":\"clstr-x\""
            + "}";
        StreamAlternate sa = new StreamAlternate(lj(json));
        assertEquals("alt-stream", sa.getName());
        assertEquals("dom1", sa.getDomain());
        assertEquals("clstr-x", sa.getCluster());
        assertNotNull(sa.toString());

        StreamAlternate empty = new StreamAlternate(lj("{}"));
        assertEquals("", empty.getName());
        assertEquals("", empty.getCluster());
    }

    @Test
    public void testStreamState() {
        String json = "{"
            + "\"messages\":100,"
            + "\"bytes\":2048,"
            + "\"first_seq\":1,"
            + "\"first_ts\":\"2026-01-02T03:04:05Z\","
            + "\"last_seq\":50,"
            + "\"last_ts\":\"2026-01-02T04:04:05Z\","
            + "\"consumer_count\":3,"
            + "\"num_subjects\":4,"
            + "\"num_deleted\":2,"
            + "\"deleted\":[5,6],"
            + "\"subjects\":{\"sub.a\":10,\"sub.b\":20},"
            + "\"lost\":{\"msgs\":[99],\"bytes\":256}"
            + "}";
        StreamState state = new StreamState(lj(json));
        assertEquals(100L, state.getMessageCount());
        assertEquals(2048L, state.getByteCount());
        assertEquals(1L, state.getFirstSequence());
        assertNotNull(state.getFirstTime());
        assertEquals(50L, state.getLastSequence());
        assertNotNull(state.getLastTime());
        assertEquals(3L, state.getConsumerCount());
        assertEquals(4L, state.getSubjectCount());
        assertEquals(2L, state.getDeletedCount());

        List<Long> deleted = state.getDeleted();
        assertEquals(2, deleted.size());
        assertEquals(5L, deleted.get(0));
        assertEquals(6L, deleted.get(1));

        // exercises lazy _subjects
        List<Subject> subjects = state.getSubjects();
        assertEquals(2, subjects.size());
        // second call should return cached
        assertEquals(subjects, state.getSubjects());

        // exercises lazy _subjectMap
        Map<String, Long> map = state.getSubjectMap();
        assertEquals(2, map.size());
        assertEquals(10L, map.get("sub.a"));
        assertEquals(20L, map.get("sub.b"));
        // cached
        assertEquals(map, state.getSubjectMap());

        // lost stream data
        LostStreamData lost = state.getLostStreamData();
        assertNotNull(lost);
        assertEquals(1, lost.getMessages().size());
        assertEquals(99L, lost.getMessages().get(0));
        assertEquals(256L, lost.getBytes());

        assertNotNull(state.toString());

        // empty — uint64 fields default to 0 (not -1; see UINT64_AUDIT.md)
        StreamState empty = new StreamState(lj("{}"));
        assertEquals(0L, empty.getMessageCount());
        assertEquals(BigInteger.ZERO, empty.getMessageCountAsBigInteger());
        assertNull(empty.getFirstTime());
        assertTrue(empty.getDeleted().isEmpty());
        assertTrue(empty.getSubjects().isEmpty());
        assertTrue(empty.getSubjectMap().isEmpty());
        assertNull(empty.getLostStreamData());

        // top-half uint64 (> Long.MAX_VALUE): the long getter returns the two's-complement bit
        // pattern (negative), the BigInteger getter returns the true non-negative value. This is
        // the case the old readLong path silently dropped to the default. See UINT64_AUDIT.md.
        BigInteger uint64Max = new BigInteger("18446744073709551615"); // 2^64 - 1
        BigInteger twoPow63 = new BigInteger("9223372036854775808");  // 2^63 (smallest top-half)
        StreamState topHalf = new StreamState(lj(
            "{\"messages\":18446744073709551615,\"first_seq\":9223372036854775808}"));
        assertEquals(-1L, topHalf.getMessageCount());                    // 2^64-1 as signed long
        assertEquals(uint64Max, topHalf.getMessageCountAsBigInteger());
        assertEquals(Long.MIN_VALUE, topHalf.getFirstSequence());       // 2^63 as signed long
        assertEquals(twoPow63, topHalf.getFirstSequenceAsBigInteger());
    }

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
    // PART 3: ApiResponse hierarchy
    // ====================================================================================================

    @Test
    public void testAccountStatistics() {
        String json = "{"
            + "\"type\":\"io.nats.jetstream.api.v1.account_info_response\","
            + "\"memory\":101,"
            + "\"storage\":102,"
            + "\"reserved_memory\":105,"
            + "\"reserved_storage\":106,"
            + "\"streams\":103,"
            + "\"consumers\":104,"
            + "\"limits\":{\"max_memory\":201,\"max_storage\":202},"
            + "\"domain\":\"ngs\","
            + "\"api\":{\"level\":303,\"total\":301,\"errors\":302,\"inflight\":304},"
            + "\"tiers\":{"
            + "\"R1\":{\"memory\":401,\"storage\":402},"
            + "\"R3\":{\"memory\":601,\"storage\":602}"
            + "}"
            + "}";
        AccountStatistics stats = new AccountStatistics(msg(json));
        assertFalse(stats.hasError());
        assertEquals("io.nats.jetstream.api.v1.account_info_response", stats.getType());

        assertEquals(101L, stats.getMemory());
        assertEquals(102L, stats.getStorage());
        assertEquals(105L, stats.getReservedMemory());
        assertEquals(106L, stats.getReservedStorage());
        assertEquals(103L, stats.getStreams());
        assertEquals(104L, stats.getConsumers());

        AccountLimits limits = stats.getLimits();
        assertEquals(201L, limits.getMaxMemory());
        assertEquals(202L, limits.getMaxStorage());

        assertEquals("ngs", stats.getDomain());

        ApiStats api = stats.getApiStats();
        assertEquals(303, api.getLevel());
        assertEquals(301L, api.getTotal());
        assertEquals(302L, api.getErrors());
        assertEquals(304L, api.getInFlight());

        Map<String, AccountTier> tiers = stats.getTiers();
        assertEquals(2, tiers.size());
        assertEquals(401L, tiers.get("R1").getMemoryBytes());
        assertEquals(601L, tiers.get("R3").getMemoryBytes());

        // Cached on second invocation
        assertEquals(tiers, stats.getTiers());
        assertEquals(api, stats.getApiStats());
    }

    @Test
    public void testApiResponse_baseBehaviors() throws JetStreamException {
        // Test the various ApiResponse constructors and accessors through SuccessApiResponse.
        // Null message ctor -> empty
        SuccessApiResponse nullMsg = new SuccessApiResponse(null);
        assertFalse(nullMsg.hasError());
        assertEquals(ApiResponse.NO_TYPE, nullMsg.getType());

        // Invalid JSON falls back to parse error JSON
        SuccessApiResponse bad = new SuccessApiResponse(msg("not json at all"));
        assertTrue(bad.hasError());
        assertEquals(500, bad.getErrorCode());
        assertEquals(-1, bad.getApiErrorCode());
        assertNotNull(bad.getError());
        assertNotNull(bad.getDescription());
        assertNotNull(bad.getErrorObject());

        // No error, getError returns null
        SuccessApiResponse ok = new SuccessApiResponse(msg("{\"success\":true}"));
        assertNull(ok.getError());
        assertNull(ok.getDescription());
        assertNull(ok.getErrorObject());
        assertEquals(Error.NOT_SET, ok.getErrorCode());
        assertEquals(Error.NOT_SET, ok.getApiErrorCode());
        assertNotNull(ok.getSourceLazyJsonValue());

        // throwOnHasError throws on error
        SuccessApiResponse err = new SuccessApiResponse(msg("{\"error\":{\"code\":401,\"description\":\"nope\"}}"));
        assertThrows(JetStreamApiException.class, err::throwOnHasError);
        // No-error case returns this
        try {
            assertEquals(ok, ok.throwOnHasError());
        }
        catch (JetStreamApiException e) {
            fail("Should not throw on non-error response");
        }
    }

    @Test
    public void testConsumerInfo() {
        String json = "{"
            + "\"type\":\"io.nats.jetstream.api.v1.consumer_info_response\","
            + "\"stream_name\":\"the-stream\","
            + "\"name\":\"the-consumer\","
            + "\"created\":\"2026-01-02T03:04:05Z\","
            + "\"ts\":\"2026-05-17T12:30:45Z\","
            + "\"config\":{\"name\":\"the-consumer\",\"durable_name\":\"the-consumer\"},"
            + "\"delivered\":{\"consumer_seq\":1,\"stream_seq\":2},"
            + "\"ack_floor\":{\"consumer_seq\":3,\"stream_seq\":4},"
            + "\"num_pending\":7,"
            + "\"num_waiting\":8,"
            + "\"num_ack_pending\":9,"
            + "\"num_redelivered\":10,"
            + "\"paused\":true,"
            + "\"pause_remaining\":15000000000,"
            + "\"cluster\":{\"name\":\"cl\"},"
            + "\"push_bound\":true,"
            + "\"priority_groups\":[{\"group\":\"g1\"},{\"group\":\"g2\"}]"
            + "}";
        // both Message-based and LazyJsonValue-based ctors
        ConsumerInfo ciMsg = new ConsumerInfo(msg(json));
        ConsumerInfo ci = new ConsumerInfo(lj(json));

        assertEquals("the-consumer", ci.getName());
        assertEquals("the-stream", ci.getStreamName());
        assertNotNull(ci.getCreationTime());
        assertNotNull(ci.getTimestamp());

        ConsumerConfiguration cfg = ci.getConsumerConfiguration();
        assertNotNull(cfg);
        assertEquals("the-consumer", cfg.getName());

        SequenceInfo delivered = ci.getDelivered();
        assertEquals(1L, delivered.getConsumerSequence());
        assertEquals(2L, delivered.getStreamSequence());

        SequenceInfo ackFloor = ci.getAckFloor();
        assertEquals(3L, ackFloor.getConsumerSequence());
        assertEquals(4L, ackFloor.getStreamSequence());

        assertEquals(7L, ci.getNumPending());
        assertEquals(8L, ci.getNumWaiting());
        assertEquals(9L, ci.getNumAckPending());
        assertEquals(10L, ci.getRedelivered());
        assertTrue(ci.getPaused());
        assertEquals(Duration.ofSeconds(15), ci.getPauseRemaining());

        ClusterInfo cluster = ci.getClusterInfo();
        assertNotNull(cluster);
        assertEquals("cl", cluster.getName());

        assertTrue(ci.isPushBound());

        List<PriorityGroupState> pgs = ci.getPriorityGroupStates();
        assertEquals(2, pgs.size());
        assertEquals("g1", pgs.get(0).getGroup());

        // getCalculatedPending = num_pending (7) + delivered consumer_seq (1)
        assertEquals(8L, ci.getCalculatedPending());

        // cached on second invocation
        assertEquals(cfg, ci.getConsumerConfiguration());
        assertEquals(delivered, ci.getDelivered());
        assertEquals(ackFloor, ci.getAckFloor());
        assertEquals(pgs, ci.getPriorityGroupStates());

        // Sanity: Message-based ctor produced equivalent state
        assertEquals(ci, ciMsg);

        // Error case: hasError -> default consumer configuration, EMPTY seq info
        String errJson = "{\"error\":{\"code\":500,\"description\":\"bad\"}}";
        ConsumerInfo errCi = new ConsumerInfo(lj(errJson));
        assertTrue(errCi.hasError());
        assertNotNull(errCi.getConsumerConfiguration());
        assertEquals(SequenceInfo.EMPTY, errCi.getDelivered());
        assertEquals(SequenceInfo.EMPTY, errCi.getAckFloor());

        // Missing config -> invalidJson causes default configuration
        ConsumerInfo missingCfg = new ConsumerInfo(lj("{\"name\":\"x\",\"stream_name\":\"s\"}"));
        assertNotNull(missingCfg.getConsumerConfiguration());
    }

    @Test
    public void testConsumerPauseResponse() {
        String json = "{"
            + "\"paused\":true,"
            + "\"pause_until\":\"2026-01-02T03:04:05Z\","
            + "\"pause_remaining\":10000000000"
            + "}";
        ConsumerPauseResponse resp = new ConsumerPauseResponse(msg(json));
        assertTrue(resp.isPaused());
        assertNotNull(resp.getPauseUntil());
        assertEquals(Duration.ofSeconds(10), resp.getPauseRemaining());

        ConsumerPauseResponse notPaused = new ConsumerPauseResponse(msg("{\"paused\":false}"));
        assertFalse(notPaused.isPaused());
        assertNull(notPaused.getPauseUntil());
        assertNull(notPaused.getPauseRemaining());
    }

    @Test
    public void testPublishAck() throws Exception {
        String json = "{"
            + "\"stream\":\"S1\","
            + "\"seq\":42,"
            + "\"domain\":\"dom-x\","
            + "\"duplicate\":true,"
            + "\"val\":\"7\","
            + "\"batch\":\"batch-1\","
            + "\"count\":3"
            + "}";
        PublishAck ack = new PublishAck(msg(json));
        assertEquals("S1", ack.getStream());
        assertEquals(42L, ack.getSequenceNumber());
        assertEquals("dom-x", ack.getDomain());
        assertTrue(ack.isDuplicate());
        assertEquals("7", ack.getVal());
        assertEquals("batch-1", ack.getBatchId());
        assertEquals(3, ack.getBatchSize());

        // Minimal valid ack
        PublishAck minimal = new PublishAck(msg("{\"stream\":\"x\",\"seq\":1}"));
        assertEquals("x", minimal.getStream());
        assertEquals(1L, minimal.getSequenceNumber());
        assertNull(minimal.getDomain());
        assertFalse(minimal.isDuplicate());
        assertNull(minimal.getVal());
        assertNull(minimal.getBatchId());
        assertEquals(-1, minimal.getBatchSize());

        // Error JSON -> throws JetStreamApiException
        assertThrows(JetStreamApiException.class,
            () -> new PublishAck(msg("{\"error\":{\"code\":500,\"description\":\"bad\"}}")));

        // Missing/empty stream -> IOException
        assertThrows(JetStreamProtocolException.class, () -> new PublishAck(msg("{\"seq\":1}")));

        // Missing/negative seq -> IOException
        assertThrows(JetStreamProtocolException.class, () -> new PublishAck(msg("{\"stream\":\"x\"}")));
    }

    @Test
    public void testPurgeResponse() {
        PurgeResponse resp = new PurgeResponse(msg("{\"success\":true,\"purged\":42}"));
        assertTrue(resp.isSuccess());
        assertEquals(42L, resp.getPurged());

        PurgeResponse fail = new PurgeResponse(msg("{\"success\":false}"));
        assertFalse(fail.isSuccess());
        assertEquals(0L, fail.getPurged());

        // Defaults
        PurgeResponse defaults = new PurgeResponse(msg("{}"));
        assertFalse(defaults.isSuccess());
        assertEquals(0L, defaults.getPurged());
    }

    @Test
    public void testStreamInfo() {
        String json = "{"
            + "\"type\":\"io.nats.jetstream.api.v1.stream_info_response\","
            + "\"created\":\"2026-01-02T03:04:05Z\","
            + "\"ts\":\"2026-05-17T12:30:45Z\","
            + "\"config\":{\"name\":\"the-stream\"},"
            + "\"state\":{\"messages\":42,\"bytes\":1024},"
            + "\"cluster\":{\"name\":\"cl\"},"
            + "\"mirror\":{\"name\":\"mref\"},"
            + "\"sources\":[{\"name\":\"src1\"},{\"name\":\"src2\"}],"
            + "\"alternates\":[{\"name\":\"alt1\",\"cluster\":\"cl1\"},{\"name\":\"alt2\",\"cluster\":\"cl2\"}],"
            + "\"did_create\":true"
            + "}";

        // both Message-based and LazyJsonValue-based ctors
        StreamInfo siMsg = new StreamInfo(msg(json));
        StreamInfo si = new StreamInfo(lj(json));

        assertNotNull(si.getCreateTime());
        assertNotNull(si.getTimestamp());
        assertEquals("the-stream", si.getConfiguration().getName());
        assertEquals(42L, si.getStreamState().getMessageCount());

        ClusterInfo cluster = si.getClusterInfo();
        assertNotNull(cluster);
        assertEquals("cl", cluster.getName());

        MirrorInfo mirror = si.getMirrorInfo();
        assertNotNull(mirror);
        assertEquals("mref", mirror.getName());

        List<SourceInfo> sources = si.getSources();
        assertEquals(2, sources.size());

        List<StreamAlternate> alternates = si.getAlternates();
        assertEquals(2, alternates.size());
        assertEquals("alt1", alternates.get(0).getName());
        assertEquals("alt2", alternates.get(1).getName());

        assertTrue(si.didCreate());

        // cached on second invocation
        assertSame(si.getConfiguration(), si.getConfiguration());
        assertSame(si.getStreamState(), si.getStreamState());
        assertSame(si.getSources(), si.getSources());
        assertSame(si.getAlternates(), si.getAlternates());

        // Sanity: Message-based ctor produced equivalent state
        assertEquals(si, siMsg);

        // Minimal JSON: missing cluster/mirror/did_create -> null/false; empty alternates/sources
        String minimal = "{\"created\":\"2026-01-02T03:04:05Z\",\"config\":{\"name\":\"min\"},\"state\":{}}";
        StreamInfo mini = new StreamInfo(lj(minimal));
        assertNull(mini.getClusterInfo());
        assertNull(mini.getMirrorInfo());
        assertNull(mini.getTimestamp());
        assertFalse(mini.didCreate());
        assertTrue(mini.getAlternates().isEmpty());
        assertTrue(mini.getSources().isEmpty());
    }

    @Test
    public void testSuccessApiResponse() {
        // Explicit success true
        SuccessApiResponse a = new SuccessApiResponse(msg("{\"success\":true}"));
        assertTrue(a.getSuccess());

        // Explicit success false
        SuccessApiResponse b = new SuccessApiResponse(msg("{\"success\":false}"));
        assertFalse(b.getSuccess());

        // No success field, no error -> infer success
        SuccessApiResponse c = new SuccessApiResponse(msg("{}"));
        assertTrue(c.getSuccess());

        // No success field, has error -> not success
        SuccessApiResponse d = new SuccessApiResponse(msg("{\"error\":{\"code\":500,\"description\":\"bad\"}}"));
        assertFalse(d.getSuccess());
        assertTrue(d.hasError());
        assertEquals(500, d.getErrorCode());
        assertEquals("bad", d.getDescription());
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
        SubscribeBehavior copy = new SubscribeBehavior().subscribeBehavior(sb);
        assertEquals(sb, copy);
        assertEquals(123L, copy.getMessageAlarmTime());
        assertEquals(456L, copy.getPendingMessageLimit());
        assertEquals(789L, copy.getPendingByteLimit());
    }
}
