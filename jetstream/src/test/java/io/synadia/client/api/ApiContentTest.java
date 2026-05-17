package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.MessageHandler;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive content/field-coverage and round-trip tests for the io.synadia.client.api package.
 *
 * <p>This complements {@link ApiEqualityAndHashCodeCoverageTest} (equals/hashCode) and
 * {@link StreamCreatorConfigurationTests} (StreamCreator/StreamConfiguration round-trip).
 *
 * <p>All tests work offline — no NATS server needed.
 */
public class ApiContentTest {

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
    // PART 1: Round-trip tests for Creator <-> Counterpart pairs
    // ====================================================================================================

    /**
     * StreamCreator <-> StreamConfiguration round trip is covered comprehensively in
     * StreamCreatorConfigurationTests. We only add a tiny smoke test here so this class
     * is self-contained.
     */
    @Test
    public void testStreamCreatorRoundTrip_smoke() {
        // Full coverage lives in StreamCreatorConfigurationTests; keep this small.
        StreamCreator sc = new StreamCreator("smoke-stream")
            .subjects("a.>", "b.>")
            .description("smoke");
        StreamConfiguration cfg = new StreamConfiguration(lj(sc.toJson()));
        assertEquals("smoke-stream", cfg.getName());
        assertEquals("smoke", cfg.getDescription());
        assertEquals(2, cfg.getSubjects().size());
    }

    /**
     * Verifies the 5 DEFAULT_* constants in StreamCreator survive a JSON round-trip.
     * Because each is serialized only when different from its default, an unset Creator
     * produces JSON without those keys, and StreamConfiguration getters must fall back to
     * the same constants.
     */
    @Test
    public void testStreamCreatorDefaultsRoundTrip() {
        StreamCreator sc = new StreamCreator("defaults-stream");

        // Creator side reports the defaults directly
        assertEquals(StreamCreator.DEFAULT_RETENTION_POLICY, sc.getRetentionPolicy());
        assertEquals(StreamCreator.DEFAULT_COMPRESSION_OPTION, sc.getCompressionOption());
        assertEquals(StreamCreator.DEFAULT_STORAGE_TYPE, sc.getStorageType());
        assertEquals(StreamCreator.DEFAULT_DISCARD_POLICY, sc.getDiscardPolicy());
        // persistMode default at the Creator level is null (server fills in DEFAULT_PERSIST_MODE)
        assertNull(sc.getPersistMode());

        // Counterpart side returns the same constants after JSON round-trip
        StreamConfiguration cfg = new StreamConfiguration(lj(sc.toJson()));
        assertEquals(StreamCreator.DEFAULT_RETENTION_POLICY, cfg.getRetentionPolicy());
        assertEquals(StreamCreator.DEFAULT_COMPRESSION_OPTION, cfg.getCompressionOption());
        assertEquals(StreamCreator.DEFAULT_STORAGE_TYPE, cfg.getStorageType());
        assertEquals(StreamCreator.DEFAULT_DISCARD_POLICY, cfg.getDiscardPolicy());
        assertEquals(StreamCreator.DEFAULT_PERSIST_MODE, cfg.getPersistMode());
    }

    @Test
    public void testExternalCreatorRoundTrip() {
        ExternalCreator creator = new ExternalCreator()
            .api("api-prefix")
            .deliver("deliver-subject");

        External external = new External(lj(creator.toJson()));

        assertEquals("api-prefix", external.getApi());
        assertEquals("deliver-subject", external.getDeliver());

        // Also exercise the two-arg constructor
        ExternalCreator twoArg = new ExternalCreator("api2", "deliver2");
        External ext2 = new External(lj(twoArg.toJson()));
        assertEquals("api2", ext2.getApi());
        assertEquals("deliver2", ext2.getDeliver());
    }

    @Test
    public void testPlacementCreatorRoundTrip() {
        PlacementCreator creator = new PlacementCreator()
            .cluster("east-1")
            .tags("tag-a", "tag-b", "tag-c");

        Placement placement = new Placement(lj(creator.toJson()));

        assertEquals("east-1", placement.getCluster());
        List<String> tags = placement.getTags();
        assertNotNull(tags);
        assertEquals(3, tags.size());
        assertEquals("tag-a", tags.get(0));
        assertEquals("tag-b", tags.get(1));
        assertEquals("tag-c", tags.get(2));
        assertTrue(placement.hasData());

        // Test ctor that takes list and cluster
        PlacementCreator c2 = new PlacementCreator("west-2", Arrays.asList("t1", "t2"));
        Placement p2 = new Placement(lj(c2.toJson()));
        assertEquals("west-2", p2.getCluster());
        assertEquals(2, p2.getTags().size());

        // tags(List) form
        PlacementCreator c3 = new PlacementCreator().cluster("c").tags(Arrays.asList("x", "y"));
        Placement p3 = new Placement(lj(c3.toJson()));
        assertEquals(2, p3.getTags().size());
    }

    @Test
    public void testRepublishCreatorRoundTrip() {
        RepublishCreator creator = new RepublishCreator("src.>", "dest.>", true);

        Republish republish = new Republish(lj(creator.toJson()));

        assertEquals("src.>", republish.getSource());
        assertEquals("dest.>", republish.getDestination());
        assertTrue(republish.isHeadersOnly());

        // headers-only false branch via two-arg constructor
        RepublishCreator creator2 = new RepublishCreator("s.>", "d.>");
        Republish republish2 = new Republish(lj(creator2.toJson()));
        assertEquals("s.>", republish2.getSource());
        assertEquals("d.>", republish2.getDestination());
        assertFalse(republish2.isHeadersOnly());
    }

    @Test
    public void testConsumerLimitsCreatorRoundTrip() {
        ConsumerLimitsCreator creator = new ConsumerLimitsCreator()
            .inactiveThreshold(Duration.ofSeconds(45))
            .maxAckPending(7777);

        ConsumerLimits limits = new ConsumerLimits(lj(creator.toJson()));

        assertEquals(Duration.ofSeconds(45), limits.getInactiveThreshold());
        assertEquals(7777, limits.getMaxAckPending());

        // millis form
        ConsumerLimitsCreator c2 = new ConsumerLimitsCreator().inactiveThreshold(12_000L);
        ConsumerLimits l2 = new ConsumerLimits(lj(c2.toJson()));
        assertEquals(Duration.ofMillis(12_000), l2.getInactiveThreshold());
    }

    @Test
    public void testSubjectTransformCreatorRoundTrip() {
        SubjectTransformCreator creator = new SubjectTransformCreator("a.>", "b.>");

        SubjectTransform st = new SubjectTransform(lj(creator.toJson()));

        assertEquals("a.>", st.getSource());
        assertEquals("b.>", st.getDestination());
    }

    @Test
    public void testMirrorCreatorRoundTrip() {
        MirrorCreator creator = new MirrorCreator("mirror-stream")
            .startSequence(99)
            .startTime(ZDT_A)
            .filterSubject("filter.>")
            .externalCreator(new ExternalCreator("apiX", "dlvX"))
            .subjectTransforms(
                new SubjectTransformCreator("m_src0", "m_dst0"),
                new SubjectTransformCreator("m_src1", "m_dst1"));

        Mirror mirror = new Mirror(lj(creator.toJson()));

        assertEquals("mirror-stream", mirror.getStreamName());
        assertEquals(99, mirror.getStartSequence());
        assertEquals(ZDT_A, mirror.getStartTime());
        assertEquals("filter.>", mirror.getFilterSubject());

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
    }

    @Test
    public void testSourceCreatorRoundTrip() {
        SourceCreator creator = new SourceCreator("source-stream")
            .startSequence(123)
            .startTime(ZDT_B)
            .filterSubject("src.filter")
            .externalCreator(new ExternalCreator("apiY", "dlvY"))
            .subjectTransforms(Collections.singletonList(
                new SubjectTransformCreator("s_src", "s_dst")));

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
    }

    @Test
    public void testPullConsumerCreatorRoundTrip() {
        PullConsumerCreator creator = new PullConsumerCreator("pull-stream")
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
            .filterSubject("the.subject")
            .priorityGroups("pg-1", "pg-2")
            .priorityPolicy(PriorityPolicy.PinnedClient)
            .priorityTimeout(Duration.ofSeconds(20));

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
    }

    @Test
    public void testPullConsumerCreatorRoundTrip_multipleFilterSubjects() {
        PullConsumerCreator creator = new PullConsumerCreator("ms-stream")
            .filterSubjects("a.>", "b.>", "c.>");

        ConsumerConfiguration cc = new ConsumerConfiguration(lj(creator.toJson()));

        assertNull(cc.getFilterSubject());
        assertTrue(cc.hasMultipleFilterSubjects());
        List<String> filterSubjects = cc.getFilterSubjects();
        assertEquals(3, filterSubjects.size());
        assertEquals("a.>", filterSubjects.get(0));
        assertEquals("b.>", filterSubjects.get(1));
        assertEquals("c.>", filterSubjects.get(2));
    }

    @Test
    public void testPushConsumerCreatorRoundTrip() {
        Map<String, String> meta = new HashMap<>();
        meta.put("k1", "v1");
        meta.put("k2", "v2");

        PushConsumerCreator creator = new PushConsumerCreator("push-stream")
            .durable("push-dur")
            .deliverSubject("deliver.here")
            .deliverGroup("group-1")
            .description("push-desc")
            .startSequence(500L)
            .startTime(ZDT_B)
            .flowControl(Duration.ofSeconds(20))
            .metadata(meta);

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
    }

    // ====================================================================================================
    // PART 2: Field coverage for API-only classes (no Creator counterpart)
    // ====================================================================================================

    @Test
    public void testAccountLimits_fieldCoverage() {
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
        assertEquals(0L, empty.getMaxMemory());
        assertEquals(0L, empty.getMaxStorage());
        assertFalse(empty.isMaxBytesRequired());
    }

    @Test
    public void testAccountTier_fieldCoverage() {
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
    public void testApiStats_fieldCoverage() {
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
    public void testClusterInfo_fieldCoverage() {
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
    public void testLostStreamData_fieldCoverage() {
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
    public void testPriorityGroupState_fieldCoverage() {
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
    }

    @Test
    public void testSequenceInfo_fieldCoverage() {
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
    public void testStreamAlternate_fieldCoverage() {
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

        // missing name should throw
        StreamAlternate noName = new StreamAlternate(lj("{\"cluster\":\"c\"}"));
        assertThrows(IllegalStateException.class, noName::getName);

        // missing cluster should throw
        StreamAlternate noCluster = new StreamAlternate(lj("{\"name\":\"n\"}"));
        assertThrows(IllegalStateException.class, noCluster::getCluster);
    }

    @Test
    public void testStreamState_fieldCoverage() {
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

        // empty
        StreamState empty = new StreamState(lj("{}"));
        assertEquals(0L, empty.getMessageCount());
        assertNull(empty.getFirstTime());
        assertTrue(empty.getDeleted().isEmpty());
        assertTrue(empty.getSubjects().isEmpty());
        assertTrue(empty.getSubjectMap().isEmpty());
        assertNull(empty.getLostStreamData());
    }

    @Test
    public void testMirrorInfo_fieldCoverage() {
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

        // missing name throws
        MirrorInfo noName = new MirrorInfo(lj("{}"));
        assertThrows(IllegalStateException.class, noName::getName);
        // negative active returns null
        MirrorInfo neg = new MirrorInfo(lj("{\"name\":\"n\",\"active\":-1}"));
        assertNull(neg.getActive());
        assertNull(neg.getExternal());
        assertTrue(neg.getSubjectTransforms().isEmpty());
        assertNull(neg.getError());
    }

    @Test
    public void testSourceInfo_fieldCoverage() {
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
    public void testReplica_fieldCoverage() {
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
    }

    @Test
    public void testSubject_fieldCoverage() {
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

    @Test
    public void testError_fieldCoverage() {
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

    // ====================================================================================================
    // PART 3: ApiResponse subclasses
    // ====================================================================================================

    @Test
    public void testAccountStatistics_fieldCoverage() {
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
    public void testConsumerInfo_fieldCoverage() {
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
    public void testConsumerPauseResponse_fieldCoverage() {
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
    public void testPublishAck_fieldCoverage() throws Exception {
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
        assertThrows(IOException.class, () -> new PublishAck(msg("{\"seq\":1}")));

        // Missing/negative seq -> IOException
        assertThrows(IOException.class, () -> new PublishAck(msg("{\"stream\":\"x\"}")));
    }

    @Test
    public void testPurgeResponse_fieldCoverage() {
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
    public void testSuccessApiResponse_fieldCoverage() {
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

    @Test
    public void testApiResponse_baseBehaviors() {
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
        assertNotNull(ok.getOriginalJsonValue());

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

    // ====================================================================================================
    // PART 4: Plain POJO constructor / builder / factory coverage
    // ====================================================================================================

    @Test
    public void testConsumerPauseRequest() {
        ZonedDateTime t = ZDT_A;
        ConsumerPauseRequest req = new ConsumerPauseRequest(t);
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
    public void testMessageGetRequest_factoryMethods() {
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
    public void testStreamInfoOptions_builderAndFactories() {
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
    public void testSubscribeBehavior_setters() {
        SubscribeBehavior sb = new SubscribeBehavior();
        assertNull(sb.getDispatcher());
        assertNull(sb.getHandler());
        assertEquals(0L, sb.getMessageAlarmTime());
        assertEquals(0L, sb.getPendingMessageLimit());
        assertEquals(0L, sb.getPendingByteLimit());

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
    }

    // ====================================================================================================
    // PART 5: Bidirectional copy-construct round-trips for Creators with counterpart-accepting ctors
    // ====================================================================================================

    @Test
    public void testPlacementCreatorFromPlacement() {
        Placement original = new Placement(lj("{\"cluster\":\"c1\",\"tags\":[\"t1\",\"t2\"]}"));
        PlacementCreator creator = new PlacementCreator(original);
        assertEquals("c1", creator.getCluster());
        assertEquals(2, creator.getTags().size());
        // Round trip
        Placement round = new Placement(lj(creator.toJson()));
        assertEquals(original.getCluster(), round.getCluster());
        assertEquals(original.getTags(), round.getTags());
    }

    @Test
    public void testExternalCreatorFromExternal() {
        External original = new External(lj("{\"api\":\"api1\",\"deliver\":\"d1\"}"));
        ExternalCreator creator = new ExternalCreator(original);
        assertEquals("api1", creator.getApi());
        assertEquals("d1", creator.getDeliver());

        External round = new External(lj(creator.toJson()));
        assertEquals(original.getApi(), round.getApi());
        assertEquals(original.getDeliver(), round.getDeliver());
    }

    @Test
    public void testRepublishCreatorFromRepublish() {
        Republish original = new Republish(lj("{\"src\":\"s.>\",\"dest\":\"d.>\",\"headers_only\":true}"));
        RepublishCreator creator = new RepublishCreator(original);
        assertEquals("s.>", creator.getSource());
        assertEquals("d.>", creator.getDestination());
        assertTrue(creator.isHeadersOnly());

        Republish round = new Republish(lj(creator.toJson()));
        assertEquals(original.getSource(), round.getSource());
        assertEquals(original.getDestination(), round.getDestination());
        assertEquals(original.isHeadersOnly(), round.isHeadersOnly());
    }

    @Test
    public void testConsumerLimitsCreatorFromConsumerLimits() {
        ConsumerLimits original = new ConsumerLimits(lj("{\"inactive_threshold\":60000000000,\"max_ack_pending\":100}"));
        ConsumerLimitsCreator creator = new ConsumerLimitsCreator(original);
        assertEquals(Duration.ofSeconds(60), creator.getInactiveThreshold());
        assertEquals(100L, creator.getMaxAckPending());

        ConsumerLimits round = new ConsumerLimits(lj(creator.toJson()));
        assertEquals(original.getInactiveThreshold(), round.getInactiveThreshold());
        assertEquals(original.getMaxAckPending(), round.getMaxAckPending());
    }

    @Test
    public void testSubjectTransformCreatorFromSubjectTransform() {
        SubjectTransform original = new SubjectTransform(lj("{\"src\":\"a.>\",\"dest\":\"b.>\"}"));
        SubjectTransformCreator creator = new SubjectTransformCreator(original);
        assertEquals("a.>", creator.getSource());
        assertEquals("b.>", creator.getDestination());

        SubjectTransform round = new SubjectTransform(lj(creator.toJson()));
        assertEquals(original.getSource(), round.getSource());
        assertEquals(original.getDestination(), round.getDestination());
    }

    @Test
    public void testMirrorCreatorFromMirror() {
        String json = "{"
            + "\"name\":\"m1\","
            + "\"opt_start_seq\":5,"
            + "\"filter_subject\":\"x.>\","
            + "\"external\":{\"api\":\"a\",\"deliver\":\"d\"},"
            + "\"subject_transforms\":[{\"src\":\"s\",\"dest\":\"d\"}]"
            + "}";
        Mirror original = new Mirror(lj(json));
        MirrorCreator creator = new MirrorCreator(original);
        assertEquals("m1", creator.getStreamName());
        assertEquals(5L, creator.getStartSequence());
        assertEquals("x.>", creator.getFilterSubject());
        assertNotNull(creator.getExternalCreator());
        assertEquals(1, creator.getSubjectTransformCreators().size());

        // Copy-rename constructor
        MirrorCreator renamed = new MirrorCreator("m2", creator);
        assertEquals("m2", renamed.getStreamName());
        assertEquals(creator.getStartSequence(), renamed.getStartSequence());
        assertEquals(creator.getFilterSubject(), renamed.getFilterSubject());
    }

    @Test
    public void testSourceCreatorFromSource() {
        String json = "{"
            + "\"name\":\"s1\","
            + "\"opt_start_seq\":11,"
            + "\"filter_subject\":\"y.>\""
            + "}";
        Source original = new Source(lj(json));
        SourceCreator creator = new SourceCreator(original);
        assertEquals("s1", creator.getStreamName());
        assertEquals(11L, creator.getStartSequence());
        assertEquals("y.>", creator.getFilterSubject());

        // Copy-rename constructor
        SourceCreator renamed = new SourceCreator("s2", creator);
        assertEquals("s2", renamed.getStreamName());
    }

    @Test
    public void testStreamSourceCreator_domainSetsExternal() {
        // The domain(...) setter on StreamSourceCreator subclasses converts to an ExternalCreator.
        MirrorCreator mc = new MirrorCreator("ms").domain("my-domain");
        ExternalCreator ec = mc.getExternalCreator();
        assertNotNull(ec);
        assertNotNull(ec.getApi());

        // Clearing
        MirrorCreator mc2 = new MirrorCreator("ms").domain(null);
        assertNull(mc2.getExternalCreator());

        // Empty domain
        MirrorCreator mc3 = new MirrorCreator("ms").domain("");
        assertNull(mc3.getExternalCreator());
    }
}
