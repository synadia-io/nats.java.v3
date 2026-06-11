package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class JsonParsingTests {
    // ====================================================================================================
    // AccountLimits
    // ====================================================================================================
    @Test
    public void testAccountLimits() {
        String json = dataAsString("AccountLimits.json");
        AccountLimits al = new AccountLimits(LazyJsonParser.parseUnchecked(json));
        assertEquals(11, al.getMaxMemory());
        assertEquals(12, al.getMaxStorage());
        assertEquals(13, al.getMaxStreams());
        assertEquals(14, al.getMaxConsumers());
        assertEquals(15, al.getMaxAckPending());
        assertEquals(16, al.getMemoryMaxStreamBytes());
        assertEquals(17, al.getStorageMaxStreamBytes());
        assertTrue(al.isMaxBytesRequired());
        assertNotNull(al.toString()); // COVERAGE
    }

    // ====================================================================================================
    // AccountStatistics
    // ====================================================================================================
    @Test
    public void testAccountStatsImpl() {
        String json = dataAsString("AccountStatistics.json");
        AccountStatistics as = new AccountStatistics(getDataMessage(json));
        assertEquals(101, as.getMemory());
        assertEquals(102, as.getStorage());
        assertEquals(103, as.getStreams());
        assertEquals(104, as.getConsumers());
        assertEquals(105, as.getReservedMemory());
        assertEquals(106, as.getReservedStorage());
        validateAccountLimits(as.getLimits(), 200);

        assertEquals("ngs", as.getDomain());

        ApiStats api = as.getApiStats();
        assertEquals(301, api.getTotal());
        assertEquals(302, api.getErrors());
        assertEquals(303, api.getLevel());
        assertEquals(304, api.getInFlight());

        Map<String, AccountTier> tiers = as.getTiers();
        validateTier(tiers.get("R1"), 400, 500);
        validateTier(tiers.get("R3"), 600, 700);

        assertNotNull(as.toString()); // COVERAGE

        as = new AccountStatistics(getDataMessage("{}"));
        assertEquals(0, as.getMemory());   // uint64 -> 0 when absent
        assertEquals(0, as.getStorage());  // uint64 -> 0 when absent
        assertEquals(-1, as.getStreams());   // signed count -> -1 when absent
        assertEquals(-1, as.getConsumers()); // signed count -> -1 when absent

        AccountLimits al = as.getLimits();
        assertEquals(-1, al.getMaxMemory());    // -1 = unlimited/unset
        assertEquals(-1, al.getMaxStorage());
        assertEquals(-1, al.getMaxStreams());
        assertEquals(-1, al.getMaxConsumers());
        assertEquals(0, al.getMaxAckPending());
        assertEquals(0, al.getMemoryMaxStreamBytes());
        assertEquals(0, al.getStorageMaxStreamBytes());
        assertFalse(al.isMaxBytesRequired());

        api = as.getApiStats();
        assertNotNull(api);
        assertEquals(0, api.getTotal());
        assertEquals(0, api.getErrors());
        assertEquals(0, api.getLevel());
        assertEquals(0, api.getInFlight());
    }

    @Test
    public void testAccountStatsImplError() {
        String json = dataAsString("AccountStatisticsError.json");
        AccountStatistics as = new AccountStatistics(getDataMessage(json));
        assertTrue(as.hasError());
        Error err = as.getErrorObject();
        assertNotNull(err);
        assertEquals(503, err.getCode());
        assertEquals(10118, err.getApiErrorCode());
        assertEquals("account not found", err.getDescription());
        assertEquals(503, as.getErrorCode());
        assertEquals(10118, as.getApiErrorCode());
        assertEquals("account not found", as.getDescription());
        assertNotNull(as.getError());
        assertNotNull(as.toString()); // COVERAGE
    }

    private void validateTier(AccountTier tier, int tierBase, int limitsIdBase) {
        assertNotNull(tier);
        assertEquals(tierBase + 1, tier.getMemoryBytes());
        assertEquals(tierBase + 2, tier.getStorageBytes());
        assertEquals(tierBase + 3, tier.getStreams());
        assertEquals(tierBase + 4, tier.getConsumers());
        assertEquals(tierBase + 5, tier.getReservedMemoryBytes());
        assertEquals(tierBase + 6, tier.getReservedStorageBytes());
        validateAccountLimits(tier.getLimits(), limitsIdBase);
    }

    private static void validateAccountLimits(AccountLimits al, int limitsIdBase) {
        assertNotNull(al);
        assertEquals(limitsIdBase + 1, al.getMaxMemory());
        assertEquals(limitsIdBase + 2, al.getMaxStorage());
        assertEquals(limitsIdBase + 3, al.getMaxStreams());
        assertEquals(limitsIdBase + 4, al.getMaxConsumers());
        assertEquals(limitsIdBase + 5, al.getMaxAckPending());
        assertEquals(limitsIdBase + 6, al.getMemoryMaxStreamBytes());
        assertEquals(limitsIdBase + 7, al.getStorageMaxStreamBytes());
        assertTrue(al.isMaxBytesRequired());
    }

    // ====================================================================================================
    // AccountTier
    // ====================================================================================================
    @Test
    public void testAccountTier() {
        String json = dataAsString("AccountTier.json");
        AccountTier tier = new AccountTier(LazyJsonParser.parseUnchecked(json));
        assertEquals(21, tier.getMemoryBytes());
        assertEquals(22, tier.getStorageBytes());
        assertEquals(23, tier.getStreams());
        assertEquals(24, tier.getConsumers());
        assertEquals(25, tier.getReservedMemoryBytes());
        assertEquals(26, tier.getReservedStorageBytes());

        AccountLimits al = tier.getLimits();
        assertNotNull(al);
        assertEquals(31, al.getMaxMemory());
        assertEquals(32, al.getMaxStorage());
        assertEquals(33, al.getMaxStreams());
        assertEquals(34, al.getMaxConsumers());
        assertEquals(35, al.getMaxAckPending());
        assertEquals(36, al.getMemoryMaxStreamBytes());
        assertEquals(37, al.getStorageMaxStreamBytes());
        assertTrue(al.isMaxBytesRequired());

        assertNotNull(tier.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ApiStats
    // ====================================================================================================
    @Test
    public void testApiStats() {
        String json = dataAsString("ApiStats.json");
        ApiStats api = new ApiStats(LazyJsonParser.parseUnchecked(json));
        assertEquals(41, api.getLevel());
        assertEquals(42, api.getTotal());
        assertEquals(43, api.getErrors());
        assertEquals(44, api.getInFlight());
        assertNotNull(api.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ClusterInfo
    // ====================================================================================================
    @Test
    public void testClusterInfo() {
        String json = dataAsString("ClusterInfo.json");
        ClusterInfo ci = new ClusterInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals("ci-name", ci.getName());
        assertEquals("ci-raft", ci.getRaftGroup());
        assertEquals("ci-leader", ci.getLeader());
        assertEquals(DateTimeUtils.parseDateTime("2024-01-15T10:11:12.000000051Z"), ci.getLeaderSince());
        assertTrue(ci.isSystemAccount());
        assertEquals("ci-traffic", ci.getTrafficAccount());

        List<Replica> replicas = ci.getReplicas();
        assertNotNull(replicas);
        assertEquals(2, replicas.size());

        Replica r0 = replicas.get(0);
        assertEquals("ci-rep0", r0.getName());
        assertTrue(r0.isCurrent());
        assertTrue(r0.isOffline());
        assertEquals(Duration.ofNanos(51000000000L), r0.getActive());
        assertEquals(52, r0.getLag());

        Replica r1 = replicas.get(1);
        assertEquals("ci-rep1", r1.getName());
        assertFalse(r1.isCurrent());
        assertFalse(r1.isOffline());
        assertEquals(Duration.ofNanos(53000000000L), r1.getActive());
        assertEquals(54, r1.getLag());

        assertNotNull(ci.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ConsumerLimits
    // ====================================================================================================
    @Test
    public void testConsumerLimits() {
        String json = dataAsString("ConsumerLimits.json");
        ConsumerLimits cl = new ConsumerLimits(LazyJsonParser.parseUnchecked(json));
        assertEquals(Duration.ofNanos(61000000000L), cl.getInactiveThreshold());
        assertEquals(62, cl.getMaxAckPending());
        assertNotNull(cl.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ConsumerPauseResponse
    // ====================================================================================================
    @Test
    public void testConsumerPauseResponse() {
        String json = dataAsString("ConsumerPauseResponse.json");
        ConsumerPauseResponse pr = new ConsumerPauseResponse(getDataMessage(json));
        assertTrue(pr.isPaused());
        assertEquals(DateTimeUtils.parseDateTime("2024-03-02T13:21:45.198423724Z"), pr.getPauseUntil());
        assertEquals(Duration.ofSeconds(30), pr.getPauseRemaining());
        assertNotNull(pr.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ConsumerPauseResponse Error
    // ====================================================================================================
    @Test
    public void testConsumerPauseResponseError() {
        String json = dataAsString("ConsumerPauseResponseError.json");
        ConsumerPauseResponse pr = new ConsumerPauseResponse(getDataMessage(json));
        assertTrue(pr.hasError());
        assertFalse(pr.isPaused());
        assertEquals(DateTimeUtils.DEFAULT_TIME, pr.getPauseUntil());
        assertNull(pr.getPauseRemaining());
        assertNotNull(pr.toString()); // COVERAGE
    }

    // ====================================================================================================
    // ConsumerPauseResponse Resume
    // ====================================================================================================
    @Test
    public void testConsumerPauseResumeResponse() {
        String json = dataAsString("ConsumerPauseResumeResponse.json");
        ConsumerPauseResponse pr = new ConsumerPauseResponse(getDataMessage(json));
        assertFalse(pr.isPaused());
        assertEquals(DateTimeUtils.parseDateTime("0001-01-01T00:00:00Z"), pr.getPauseUntil());
        assertNull(pr.getPauseRemaining());
        assertNotNull(pr.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Error
    // ====================================================================================================
    @Test
    public void testError() {
        String json = dataAsString("Error.json");
        Error err = new Error(LazyJsonParser.parseUnchecked(json));
        assertEquals(71, err.getCode());
        assertEquals(72, err.getApiErrorCode());
        assertEquals("error-description", err.getDescription());
        assertEquals("error-description [72]", err.toString());

        // optionalInstance: null in -> null out, non-null -> instance
        assertNull(Error.optionalInstance(null));
        Error opt = Error.optionalInstance(LazyJsonParser.parseUnchecked(json));
        assertNotNull(opt);
        assertEquals(71, opt.getCode());
    }

    @Test
    public void testErrorApiCodeOnly() {
        // apiErrorCode set, code NOT_SET -> toString returns description
        String json = dataAsString("ErrorApiCodeOnly.json");
        Error err = new Error(LazyJsonParser.parseUnchecked(json));
        assertEquals(Error.NOT_SET, err.getCode());
        assertEquals(9001, err.getApiErrorCode());
        assertEquals("api-only", err.getDescription());
        assertEquals("api-only", err.toString());
    }

    @Test
    public void testErrorCodeOnly() {
        // code set, apiErrorCode NOT_SET, no description -> default description with code suffix
        String json = dataAsString("ErrorCodeOnly.json");
        Error err = new Error(LazyJsonParser.parseUnchecked(json));
        assertEquals(401, err.getCode());
        assertEquals(Error.NOT_SET, err.getApiErrorCode());
        assertEquals("Unknown JetStream Error", err.getDescription());
        assertEquals("Unknown JetStream Error (401)", err.toString());
    }

    @Test
    public void testErrorDescriptionOnly() {
        // both code and apiErrorCode NOT_SET -> toString returns description
        String json = dataAsString("ErrorDescriptionOnly.json");
        Error err = new Error(LazyJsonParser.parseUnchecked(json));
        assertEquals(Error.NOT_SET, err.getCode());
        assertEquals(Error.NOT_SET, err.getApiErrorCode());
        assertEquals("desc-only", err.getDescription());
        assertEquals("desc-only", err.toString());
    }

    // ====================================================================================================
    // External
    // ====================================================================================================
    @Test
    public void testExternal() {
        String json = dataAsString("External.json");
        External ext = new External(LazyJsonParser.parseUnchecked(json));
        assertEquals("ext-api", ext.getApi());
        assertEquals("ext-deliver", ext.getDeliver());
        assertNotNull(ext.toString()); // COVERAGE
    }

    // ====================================================================================================
    // LostStreamData
    // ====================================================================================================
    @Test
    public void testLostStreamData() {
        String json = dataAsString("LostStreamData.json");
        LostStreamData lsd = new LostStreamData(LazyJsonParser.parseUnchecked(json));
        List<Long> msgs = lsd.getMessages();
        assertNotNull(msgs);
        assertEquals(3, msgs.size());
        assertEquals(81L, msgs.get(0));
        assertEquals(82L, msgs.get(1));
        assertEquals(83L, msgs.get(2));
        assertEquals(Long.valueOf(84L), lsd.getBytes());
        assertNotNull(lsd.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Mirror
    // ====================================================================================================
    @Test
    public void testMirror() {
        String json = dataAsString("Mirror.json");
        Mirror mirror = new Mirror(LazyJsonParser.parseUnchecked(json));
        assertEquals("mirror-name", mirror.getStreamName());
        assertEquals(91, mirror.getStartSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-02-16T11:12:13.000000092Z"), mirror.getStartTime());
        assertEquals("mirror.filter", mirror.getFilterSubject());

        External ext = mirror.getExternal();
        assertNotNull(ext);
        assertEquals("mirror-ext-api", ext.getApi());
        assertEquals("mirror-ext-deliver", ext.getDeliver());

        List<SubjectTransform> sts = mirror.getSubjectTransforms();
        assertNotNull(sts);
        assertEquals(2, sts.size());
        assertEquals("mirror.src.0", sts.get(0).getSource());
        assertEquals("mirror.dest.0", sts.get(0).getDestination());
        assertEquals("mirror.src.1", sts.get(1).getSource());
        assertEquals("mirror.dest.1", sts.get(1).getDestination());

        assertNotNull(mirror.toString()); // COVERAGE
    }

    @Test
    public void testMirrorMinimal() {
        String json = dataAsString("MirrorMinimal.json");
        Mirror mirror = new Mirror(LazyJsonParser.parseUnchecked(json));
        assertEquals("minimal-mirror-name", mirror.getStreamName());
        assertEquals(0, mirror.getStartSequence());
        assertNull(mirror.getStartTime());
        assertNull(mirror.getFilterSubject());
        assertNull(mirror.getExternal());
        List<SubjectTransform> sts = mirror.getSubjectTransforms();
        assertNotNull(sts);
        assertTrue(sts.isEmpty());
        assertNotNull(mirror.toString()); // COVERAGE
    }

    // ====================================================================================================
    // MirrorInfo
    // ====================================================================================================
    @Test
    public void testMirrorInfo() {
        String json = dataAsString("MirrorInfo.json");
        MirrorInfo mi = new MirrorInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals("mi-name", mi.getName());
        assertEquals("mi.filter", mi.getFilterSubject());
        assertEquals(101, mi.getLag());
        assertEquals(Duration.ofNanos(102000000000L), mi.getActive());

        External ext = mi.getExternal();
        assertNotNull(ext);
        assertEquals("mi-ext-api", ext.getApi());
        assertEquals("mi-ext-deliver", ext.getDeliver());

        List<SubjectTransform> sts = mi.getSubjectTransforms();
        assertNotNull(sts);
        assertEquals(1, sts.size());
        assertEquals("mi.src.0", sts.get(0).getSource());
        assertEquals("mi.dest.0", sts.get(0).getDestination());

        Error err = mi.getError();
        assertNotNull(err);
        assertEquals(103, err.getCode());
        assertEquals(104, err.getApiErrorCode());
        assertEquals("mi-error", err.getDescription());

        assertNotNull(mi.toString()); // COVERAGE

        // optionalInstance: null in -> null out, non-null -> instance
        assertNull(MirrorInfo.optionalInstance(null));
        MirrorInfo opt = MirrorInfo.optionalInstance(LazyJsonParser.parseUnchecked(json));
        assertNotNull(opt);
        assertEquals("mi-name", opt.getName());
    }

    @Test
    public void testMirrorInfoNoError() {
        String json = dataAsString("MirrorInfoNoError.json");
        MirrorInfo mi = new MirrorInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals("mi-no-error-name", mi.getName());
        assertEquals("mi.no.error.filter", mi.getFilterSubject());
        assertEquals(181, mi.getLag());
        assertEquals(Duration.ofNanos(182000000000L), mi.getActive());
        assertNull(mi.getExternal());
        assertTrue(mi.getSubjectTransforms().isEmpty());
        assertNull(mi.getError());
        assertNotNull(mi.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Placement
    // ====================================================================================================
    @Test
    public void testPlacement() {
        String json = dataAsString("Placement.json");
        Placement p = new Placement(LazyJsonParser.parseUnchecked(json));
        assertTrue(p.hasData());
        assertEquals("placement-cluster", p.getCluster());

        List<String> tags = p.getTags();
        assertNotNull(tags);
        assertEquals(3, tags.size());
        assertEquals("ptag-a", tags.get(0));
        assertEquals("ptag-b", tags.get(1));
        assertEquals("ptag-c", tags.get(2));

        assertNotNull(p.toString()); // COVERAGE
    }

    @Test
    public void testPlacementClusterOnly() {
        String json = dataAsString("PlacementClusterOnly.json");
        Placement p = new Placement(LazyJsonParser.parseUnchecked(json));
        assertTrue(p.hasData());
        assertEquals("only-cluster", p.getCluster());
        assertNull(p.getTags());
        assertNotNull(p.toString()); // COVERAGE
    }

    @Test
    public void testPlacementEmpty() {
        String json = dataAsString("PlacementEmpty.json");
        Placement p = new Placement(LazyJsonParser.parseUnchecked(json));
        assertFalse(p.hasData());
        assertNull(p.getCluster());
        assertNull(p.getTags());
        assertNotNull(p.toString()); // COVERAGE
    }

    @Test
    public void testPlacementEmptyCluster() {
        String json = dataAsString("PlacementEmptyCluster.json");
        Placement p = new Placement(LazyJsonParser.parseUnchecked(json));
        // empty cluster string is stripped to null
        assertNull(p.getCluster());
        assertNull(p.getTags());
        assertFalse(p.hasData());
        assertNotNull(p.toString()); // COVERAGE
    }

    @Test
    public void testPlacementTagsOnly() {
        String json = dataAsString("PlacementTagsOnly.json");
        Placement p = new Placement(LazyJsonParser.parseUnchecked(json));
        assertTrue(p.hasData());
        assertNull(p.getCluster());
        List<String> tags = p.getTags();
        assertNotNull(tags);
        assertEquals(2, tags.size());
        assertEquals("only-tag-a", tags.get(0));
        assertEquals("only-tag-b", tags.get(1));
        assertNotNull(p.toString()); // COVERAGE
    }

    // ====================================================================================================
    // PriorityGroupState
    // ====================================================================================================
    @Test
    public void testPriorityGroupState() {
        String json = dataAsString("PriorityGroupState.json");
        PriorityGroupState pgs = new PriorityGroupState(LazyJsonParser.parseUnchecked(json));
        assertEquals("pgs-group", pgs.getGroup());
        assertEquals("pgs-client", pgs.getPinnedClientId());
        assertEquals(DateTimeUtils.parseDateTime("2024-03-17T12:13:14.000000111Z"), pgs.getPinnedTime());
        assertNotNull(pgs.toString()); // COVERAGE
    }

    // ====================================================================================================
    // PurgeResponse
    // ====================================================================================================
    @Test
    public void testPurgeResponse() {
        String json = dataAsString("PurgeResponse.json");
        PurgeResponse pr = new PurgeResponse(getDataMessage(json));
        assertTrue(pr.isSuccess());
        assertEquals(5, pr.getPurged());
        assertNotNull(pr.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Replica
    // ====================================================================================================
    @Test
    public void testReplica() {
        String json = dataAsString("Replica.json");
        Replica r = new Replica(LazyJsonParser.parseUnchecked(json));
        assertEquals("replica-name", r.getName());
        assertTrue(r.isCurrent());
        assertTrue(r.isOffline());
        assertEquals(Duration.ofNanos(121000000000L), r.getActive());
        assertEquals(122, r.getLag());
        assertNotNull(r.toString()); // COVERAGE
    }

    @Test
    public void testReplicaMinimal() {
        String json = dataAsString("ReplicaMinimal.json");
        Replica r = new Replica(LazyJsonParser.parseUnchecked(json));
        assertEquals("minimal-replica-name", r.getName());
        assertFalse(r.isCurrent());
        assertFalse(r.isOffline());
        assertEquals(Duration.ZERO, r.getActive());
        assertEquals(0, r.getLag());
        assertNotNull(r.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Republish
    // ====================================================================================================
    @Test
    public void testRepublish() {
        String json = dataAsString("Republish.json");
        Republish rp = new Republish(LazyJsonParser.parseUnchecked(json));
        assertEquals("rep.src.>", rp.getSource());
        assertEquals("rep.dest.>", rp.getDestination());
        assertTrue(rp.isHeadersOnly());
        assertNotNull(rp.toString()); // COVERAGE
    }

    // ====================================================================================================
    // SequenceInfo
    // ====================================================================================================
    @Test
    public void testSequenceInfo() {
        String json = dataAsString("SequenceInfo.json");
        SequenceInfo si = new SequenceInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals(131, si.getConsumerSequence());
        assertEquals(132, si.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-04-18T13:14:15.000000133Z"), si.getLastActive());
        assertNotNull(si.toString()); // COVERAGE
    }

    // ====================================================================================================
    // Source
    // ====================================================================================================
    @Test
    public void testSource() {
        String json = dataAsString("Source.json");
        Source source = new Source(LazyJsonParser.parseUnchecked(json));
        assertEquals("source-name", source.getStreamName());
        assertEquals(141, source.getStartSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-05-19T14:15:16.000000142Z"), source.getStartTime());
        assertEquals("source.filter", source.getFilterSubject());

        External ext = source.getExternal();
        assertNotNull(ext);
        assertEquals("source-ext-api", ext.getApi());
        assertEquals("source-ext-deliver", ext.getDeliver());

        List<SubjectTransform> sts = source.getSubjectTransforms();
        assertNotNull(sts);
        assertEquals(2, sts.size());
        assertEquals("source.src.0", sts.get(0).getSource());
        assertEquals("source.dest.0", sts.get(0).getDestination());
        assertEquals("source.src.1", sts.get(1).getSource());
        assertEquals("source.dest.1", sts.get(1).getDestination());

        assertNotNull(source.toString()); // COVERAGE
    }

    @Test
    public void testSourceMinimal() {
        String json = dataAsString("SourceMinimal.json");
        Source source = new Source(LazyJsonParser.parseUnchecked(json));
        assertEquals("minimal-source-name", source.getStreamName());
        assertEquals(0, source.getStartSequence());
        assertNull(source.getStartTime());
        assertNull(source.getFilterSubject());
        assertNull(source.getExternal());
        List<SubjectTransform> sts = source.getSubjectTransforms();
        assertNotNull(sts);
        assertTrue(sts.isEmpty());
        assertNotNull(source.toString()); // COVERAGE
    }

    // ====================================================================================================
    // SourceInfo
    // ====================================================================================================
    @Test
    public void testSourceInfo() {
        String json = dataAsString("SourceInfo.json");
        SourceInfo si = new SourceInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals("si-name", si.getName());
        assertEquals("si.filter", si.getFilterSubject());
        assertEquals(151, si.getLag());
        assertEquals(Duration.ofNanos(152000000000L), si.getActive());

        External ext = si.getExternal();
        assertNotNull(ext);
        assertEquals("si-ext-api", ext.getApi());
        assertEquals("si-ext-deliver", ext.getDeliver());

        List<SubjectTransform> sts = si.getSubjectTransforms();
        assertNotNull(sts);
        assertEquals(1, sts.size());
        assertEquals("si.src.0", sts.get(0).getSource());
        assertEquals("si.dest.0", sts.get(0).getDestination());

        Error err = si.getError();
        assertNotNull(err);
        assertEquals(153, err.getCode());
        assertEquals(154, err.getApiErrorCode());
        assertEquals("si-error", err.getDescription());

        assertNotNull(si.toString()); // COVERAGE
    }

    @Test
    public void testSourceInfoNoActive() {
        String json = dataAsString("SourceInfoNoActive.json");
        SourceInfo si = new SourceInfo(LazyJsonParser.parseUnchecked(json));
        assertEquals("si-no-active-name", si.getName());
        assertEquals("si.no.active.filter", si.getFilterSubject());
        assertEquals(191, si.getLag());
        // absent/negative active -> Duration.ZERO (readDurationOrZero)
        assertEquals(Duration.ZERO, si.getActive());
        assertNull(si.getExternal());
        assertTrue(si.getSubjectTransforms().isEmpty());
        assertNull(si.getError());
        assertNotNull(si.toString()); // COVERAGE
    }

    // ====================================================================================================
    // StreamAlternate
    // ====================================================================================================
    @Test
    public void testStreamAlternate() {
        String json = dataAsString("StreamAlternate.json");
        StreamAlternate sa = new StreamAlternate(LazyJsonParser.parseUnchecked(json));
        assertEquals("sa-name", sa.getName());
        assertEquals("sa-domain", sa.getDomain());
        assertEquals("sa-cluster", sa.getCluster());
        assertNotNull(sa.toString()); // COVERAGE
    }

    // ====================================================================================================
    // StreamState
    // ====================================================================================================
    @Test
    public void testStreamState() {
        String json = dataAsString("StreamState.json");
        StreamState ss = new StreamState(LazyJsonParser.parseUnchecked(json));
        assertEquals(161, ss.getMessageCount());
        assertEquals(162, ss.getByteCount());
        assertEquals(163, ss.getFirstSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-06-20T15:16:17.000000164Z"), ss.getFirstTime());
        assertEquals(165, ss.getLastSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-06-21T16:17:18.000000166Z"), ss.getLastTime());
        assertEquals(167, ss.getConsumerCount());
        assertEquals(3, ss.getSubjectCount());

        List<Subject> subjects = ss.getSubjects();
        assertNotNull(subjects);
        assertEquals(3, subjects.size());

        Map<String, Long> subjectMap = ss.getSubjectMap();
        assertNotNull(subjectMap);
        assertEquals(3, subjectMap.size());
        assertEquals(Long.valueOf(168L), subjectMap.get("ss.sub.a"));
        assertEquals(Long.valueOf(169L), subjectMap.get("ss.sub.b"));
        assertEquals(Long.valueOf(170L), subjectMap.get("ss.sub.c"));

        assertEquals(4, ss.getDeletedCount());
        List<Long> deleted = ss.getDeleted();
        assertNotNull(deleted);
        assertEquals(4, deleted.size());
        assertTrue(deleted.contains(171L));
        assertTrue(deleted.contains(172L));
        assertTrue(deleted.contains(173L));
        assertTrue(deleted.contains(174L));

        LostStreamData lost = ss.getLostStreamData();
        assertNotNull(lost);
        assertEquals(2, lost.getMessages().size());
        assertTrue(lost.getMessages().contains(175L));
        assertTrue(lost.getMessages().contains(176L));
        assertEquals(Long.valueOf(177L), lost.getBytes());

        assertNotNull(ss.toString()); // COVERAGE
    }

    @Test
    public void testStreamStateMinimal() {
        String json = dataAsString("StreamStateMinimal.json");
        StreamState ss = new StreamState(LazyJsonParser.parseUnchecked(json));
        assertEquals(261, ss.getMessageCount());
        assertEquals(262, ss.getByteCount());
        assertEquals(263, ss.getFirstSequence());
        assertEquals(265, ss.getLastSequence());
        assertEquals(267, ss.getConsumerCount());

        // no subjects key -> empty list and empty map
        List<Subject> subjects = ss.getSubjects();
        assertNotNull(subjects);
        assertTrue(subjects.isEmpty());

        Map<String, Long> subjectMap = ss.getSubjectMap();
        assertNotNull(subjectMap);
        assertTrue(subjectMap.isEmpty());

        // no lost key -> null
        assertNull(ss.getLostStreamData());

        assertNotNull(ss.toString()); // COVERAGE
    }

    // ====================================================================================================
    // SubjectTransform
    // ====================================================================================================
    @Test
    public void testSubjectTransform() {
        String json = dataAsString("SubjectTransform.json");
        SubjectTransform st = new SubjectTransform(LazyJsonParser.parseUnchecked(json));
        assertEquals("st.src.>", st.getSource());
        assertEquals("st.dest.>", st.getDestination());
        assertNotNull(st.toString()); // COVERAGE
    }
}
