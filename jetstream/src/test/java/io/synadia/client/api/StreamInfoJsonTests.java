package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class StreamInfoJsonTests {
    static String STREAM_INFO_JSON = dataAsString("StreamInfo.json");

    @Test
    public void testStreamInfo() {
        StreamInfo si = new StreamInfo(getDataMessage(STREAM_INFO_JSON));
        verifyStreamInfo(si);
        assertNotNull(si.toString()); // coverage
    }

    @Test
    public void testStreamInfoMirrorAndSourceErrors() {
        String json = dataAsString("StreamInfoWithMirrorError.json");
        StreamInfo si = new StreamInfo(getDataMessage(json));

        MirrorInfo mi = si.getMirrorInfo();
        assertNotNull(mi);
        Error mirrorError = mi.getError();
        assertNotNull(mirrorError);
        assertEquals(503, mirrorError.getCode());
        assertEquals(4001, mirrorError.getApiErrorCode());
        assertEquals("mirror unavailable", mirrorError.getDescription());

        List<SourceInfo> sources = si.getSources();
        assertEquals(2, sources.size());

        Error sourceError = sources.get(0).getError();
        assertNotNull(sourceError);
        assertEquals(504, sourceError.getCode());
        assertEquals(4002, sourceError.getApiErrorCode());
        assertEquals("source timeout", sourceError.getDescription());

        // second source has no error
        assertNull(sources.get(1).getError());
    }

    @Test
    public void testStreamInfoApiError() {
        String json = dataAsString("StreamInfoApiError.json");
        StreamInfo si = new StreamInfo(getDataMessage(json));

        assertTrue(si.hasError());
        Error errorObject = si.getErrorObject();
        assertNotNull(errorObject);
        assertEquals(500, errorObject.getCode());
        assertEquals(10001, errorObject.getApiErrorCode());
        assertEquals("stream not found", errorObject.getDescription());

        assertEquals(500, si.getErrorCode());
        assertEquals(10001, si.getApiErrorCode());
        assertEquals("stream not found", si.getDescription());
        assertEquals("stream not found [10001]", si.getError());
        assertEquals("io.nats.jetstream.api.v1.stream_info_response", si.getType());
    }

    @Test
    public void testStreamInfoFromLazyJsonValue() {
        LazyJsonValue ljv = LazyJsonParser.parseUnchecked(STREAM_INFO_JSON);
        StreamInfo si = new StreamInfo(ljv);

        assertEquals("io.nats.jetstream.api.v1.stream_create_response", si.getType());
        assertEquals("streamName", si.getConfiguration().getName());
        assertEquals(DateTimeUtils.parseDateTime("2021-01-25T20:09:10.6225191Z"), si.getCreateTime());
    }

    private void verifyStreamInfo(StreamInfo si) {
        assertEquals("io.nats.jetstream.api.v1.stream_create_response", si.getType());
        assertTrue(si.didCreate());
        assertEquals(DateTimeUtils.parseDateTime("2021-01-25T20:09:10.6225191Z"), si.getCreateTime());
        assertEquals(DateTimeUtils.parseDateTime("2023-08-29T19:33:21.163377Z"), si.getTimestamp());

        StreamConfiguration sc = si.getConfiguration();
        assertEquals("streamName", sc.getName());
        assertEquals("stream description", sc.getDescription());
        assertEquals(3, sc.getSubjects().size());
        assertEquals("sub0", sc.getSubjects().get(0));
        assertEquals("sub1", sc.getSubjects().get(1));
        assertEquals("x.>", sc.getSubjects().get(2));
        assertEquals(82942, sc.getFirstSequence());

        assertEquals(RetentionPolicy.Limits, sc.getRetentionPolicy());
        assertEquals(CompressionOption.S2, sc.getCompressionOption());
        assertEquals(DiscardPolicy.Old, sc.getDiscardPolicy());
        assertEquals(StorageType.Memory, sc.getStorageType());
        assertEquals(PersistMode.Async, sc.getPersistMode());

        assertNotNull(si.getConfiguration());
        assertNotNull(si.getStreamState());
        assertEquals(1, sc.getMaxConsumers());
        assertEquals(2, sc.getMaxMessages());
        assertEquals(50, sc.getMaxMessagesPerSubject());
        assertEquals(3, sc.getMaxBytes());
        assertEquals(4, sc.getMaxMessageSize());
        assertEquals(5, sc.getReplicas());
        assertTrue(sc.getNoAck());
        assertEquals("tmplOwner", sc.getTemplateOwner());

        assertEquals(Duration.ofSeconds(100), sc.getMaxAge());
        assertEquals(Duration.ofSeconds(120), sc.getDuplicateWindow());
        assertEquals(Duration.ofSeconds(300), sc.getSubjectDeleteMarkerTtl());

        assertTrue(sc.getSealed());
        assertTrue(sc.getAllowRollup());
        assertTrue(sc.getAllowDirect());
        assertTrue(sc.getMirrorDirect());
        assertTrue(sc.getDenyDelete());
        assertTrue(sc.getDenyPurge());
        assertTrue(sc.isDiscardNewPerSubject());
        assertTrue(sc.getAllowMessageTtl());
        assertTrue(sc.getAllowMessageSchedules());
        assertTrue(sc.getAllowMessageCounter());
        assertTrue(sc.getAllowAtomicPublish());
        assertTrue(sc.getAllowBatched());

        Map<String, String> metadata = sc.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals("val1", metadata.get("meta1"));
        assertEquals("val2", metadata.get("meta2"));

        Republish rp = sc.getRepublish();
        assertNotNull(rp);
        assertEquals("rsrc", rp.getSource());
        assertEquals("rdest", rp.getDestination());
        assertTrue(rp.isHeadersOnly());

        SubjectTransform st = sc.getSubjectTransform();
        assertNotNull(st);
        assertEquals("stsrc", st.getSource());
        assertEquals("stdest", st.getDestination());

        ConsumerLimits cl = sc.getConsumerLimits();
        assertNotNull(cl);
        assertNotNull(cl.toString()); // coverage
        assertEquals(Duration.ofSeconds(60), cl.getInactiveThreshold());
        assertEquals(25, cl.getMaxAckPending());

        Mirror cfgMirror = sc.getMirror();
        assertNotNull(cfgMirror);
        assertEquals("cfgMirrorName", cfgMirror.getStreamName());
        assertEquals(1001, cfgMirror.getStartSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-01-01T00:00:00Z"), cfgMirror.getStartTime());
        assertEquals("cfgMirrorFs", cfgMirror.getFilterSubject());
        External cfgMirrorExt = cfgMirror.getExternal();
        assertNotNull(cfgMirrorExt);
        assertEquals("cfgMirrorApi", cfgMirrorExt.getApi());
        assertEquals("cfgMirrorDlvr", cfgMirrorExt.getDeliver());
        StreamCreatorConfigurationTests.validateSubjectTransforms(cfgMirror.getSubjectTransforms(), 2, "cfgMirror");

        assertEquals(1, sc.getSources().size());
        Source cfgSource = sc.getSources().get(0);
        assertEquals("cfgSourceName", cfgSource.getStreamName());
        assertEquals(2002, cfgSource.getStartSequence());
        assertEquals(DateTimeUtils.parseDateTime("2024-02-01T00:00:00Z"), cfgSource.getStartTime());
        assertEquals("cfgSourceFs", cfgSource.getFilterSubject());
        External cfgSourceExt = cfgSource.getExternal();
        assertNotNull(cfgSourceExt);
        assertEquals("cfgSourceApi", cfgSourceExt.getApi());
        assertEquals("cfgSourceDlvr", cfgSourceExt.getDeliver());
        StreamCreatorConfigurationTests.validateSubjectTransforms(cfgSource.getSubjectTransforms(), 2, "cfgSource");

        StreamState ss = si.getStreamState();
        assertEquals(11, ss.getMessageCount());
        assertEquals(12, ss.getByteCount());
        assertEquals(13, ss.getFirstSequence());
        assertEquals(14, ss.getLastSequence());

        assertEquals(15, ss.getConsumerCount());
        assertEquals(3, ss.getSubjectCount());
        assertNotNull(ss.getSubjects());
        assertEquals(3, ss.getSubjects().size());

        Map<String, Subject> map = new HashMap<>();
        for (Subject su : ss.getSubjects()) {
            map.put(su.getName(), su);
        }

        Subject s = map.get("sub0");
        assertNotNull(s);
        assertEquals(1, s.getCount());

        s = map.get("sub1");
        assertNotNull(s);
        assertEquals(2, s.getCount());

        s = map.get("x.foo");
        assertNotNull(s);
        assertEquals(3, s.getCount());

        Map<String, Long> subjectMap = ss.getSubjectMap();
        assertNotNull(subjectMap);
        assertEquals(3, subjectMap.size());
        assertEquals(1L, subjectMap.get("sub0"));
        assertEquals(2L, subjectMap.get("sub1"));
        assertEquals(3L, subjectMap.get("x.foo"));

        assertEquals(6, ss.getDeletedCount());
        assertNotNull(ss.getDeleted());
        assertEquals(6, ss.getDeleted().size());
        for (long x = 91; x <= 96; x++) {
            assertTrue(ss.getDeleted().contains(x));
        }

        LostStreamData lost = ss.getLostStreamData();
        assertNotNull(lost);
        assertNotNull(lost.toString()); // coverage
        assertEquals(3, lost.getMessages().size());
        for (long x = 101; x <= 103; x++) {
            assertTrue(lost.getMessages().contains(x));
        }
        assertEquals(104, lost.getBytes());

        assertEquals(DateTimeUtils.parseDateTime("0001-01-01T00:00:00Z"), ss.getFirstTime());
        assertEquals(DateTimeUtils.parseDateTime("0002-01-01T00:00:00Z"), ss.getLastTime());

        Placement pl = si.getConfiguration().getPlacement();
        assertNotNull(pl);
        assertTrue(pl.hasData());
        assertEquals("placementclstr", pl.getCluster());
        assertNotNull(pl.getTags());
        assertEquals(2, pl.getTags().size());
        assertEquals("ptag1", pl.getTags().get(0));
        assertEquals("ptag2", pl.getTags().get(1));

        ClusterInfo cli = si.getClusterInfo();
        assertNotNull(cli);
        assertNotNull(cli.toString()); // coverage
        assertEquals("clustername", cli.getName());
        assertEquals("raftgroupname", cli.getRaftGroup());
        assertEquals("clusterleader", cli.getLeader());
        assertTrue(cli.isSystemAccount());
        assertEquals("trafficaccountname", cli.getTrafficAccount());
        assertEquals(DateTimeUtils.parseDateTime("2025-08-29T19:33:21.163377Z"), cli.getLeaderSince());

        assertNotNull(cli.getReplicas()); // coverage
        assertEquals(2, cli.getReplicas().size());
        assertNotNull(cli.getReplicas().get(0).toString()); // coverage
        assertEquals("name0", cli.getReplicas().get(0).getName());
        assertTrue(cli.getReplicas().get(0).isCurrent());
        assertTrue(cli.getReplicas().get(0).isOffline());
        assertEquals(Duration.ofNanos(230000000000L), cli.getReplicas().get(0).getActive());
        assertEquals(3, cli.getReplicas().get(0).getLag());

        assertEquals("name1", cli.getReplicas().get(1).getName());
        assertFalse(cli.getReplicas().get(1).isCurrent());
        assertFalse(cli.getReplicas().get(1).isOffline());
        assertEquals(Duration.ofNanos(240000000000L), cli.getReplicas().get(1).getActive());
        assertEquals(4, cli.getReplicas().get(1).getLag());

        MirrorInfo mi = si.getMirrorInfo();
        assertNotNull(mi);
        assertNotNull(mi.toString()); // coverage
        assertEquals("mname", mi.getName());
        assertEquals("mfs", mi.getFilterSubject());
        assertEquals(16, mi.getLag());
        assertEquals(Duration.ofNanos(160000000000L), mi.getActive());
        assertNull(mi.getError());
        validateExternal(mi.getExternal(), 16);
        StreamCreatorConfigurationTests.validateSubjectTransforms(mi.getSubjectTransforms(), 2, "16");

        assertNotNull(si.getSources());
        assertEquals(3, si.getSources().size());
        validateSourceInfo(si.getSources().get(0), 17, true);
        validateSourceInfo(si.getSources().get(1), 18, false);
        validateSourceInfo(si.getSources().get(2), 19, false);

        assertNotNull(si.getAlternates());
        assertEquals(2, si.getAlternates().size());
        validateStreamAlternate(si.getAlternates().get(0), 19);
        validateStreamAlternate(si.getAlternates().get(1), 20);

        si = new StreamInfo(LazyJsonValue.EMPTY_MAP);

        // when there is no actual error, because this is lazy,
        // error is not set until after a failure occurs
        // this is just coverage
        assertFalse(si.hasError());
        assertNotNull(si.getConfiguration());
        assertTrue(si.hasError());

        assertNotNull(si.getStreamState());
        assertEquals(DateTimeUtils.DEFAULT_TIME, si.getCreateTime());
        assertNull(si.getClusterInfo());
        assertNull(si.getMirrorInfo());
        assertNotNull(si.getSources());
        assertTrue(si.getSources().isEmpty());

        assertNotNull(Replica.listOf(null));
        assertNotNull(Replica.listOf(LazyJsonValue.NULL));
        assertNotNull(Replica.listOf(LazyJsonValue.EMPTY_ARRAY));
    }

    private static void validateSourceInfo(StreamSourceInfo streamSourceInfo, int id, boolean hasActive) {
        assertNotNull(streamSourceInfo.toString()); // coverage
        assertEquals("sname" + id, streamSourceInfo.getName());
        assertEquals("sfs" + id, streamSourceInfo.getFilterSubject());
        assertEquals(id, streamSourceInfo.getLag());
        if (hasActive) {
            assertEquals(Duration.ofNanos(id * 10000000000L), streamSourceInfo.getActive());
        }
        else {
            assertEquals(Duration.ZERO, streamSourceInfo.getActive());
        }
        validateExternal(streamSourceInfo.getExternal(), id);
        StreamCreatorConfigurationTests.validateSubjectTransforms(streamSourceInfo.getSubjectTransforms(), 2, "" + id);
    }

    private static void validateStreamAlternate(StreamAlternate streamAlternate, int id) {
        assertNotNull(streamAlternate.toString()); // coverage
        assertEquals("alt" + id, streamAlternate.getName());
        assertEquals("domain" + id, streamAlternate.getDomain());
        assertEquals("cluster" + id, streamAlternate.getCluster());
    }

    private static void validateExternal(External e, int id) {
        assertNotNull(e);
        assertNotNull(e.toString()); // coverage
        assertEquals("api" + id, e.getApi());
        assertEquals("dlvr" + id, e.getDeliver());
    }
}
