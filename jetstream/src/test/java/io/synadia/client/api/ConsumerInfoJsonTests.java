package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumerInfoJsonTests {

    @Test
    public void testConsumerInfo() {
        String json = dataAsString("ConsumerInfo.json");
        LazyJsonValue vConsumerInfo = LazyJsonParser.parseUnchecked(json);
        ConsumerInfo ci = new ConsumerInfo(vConsumerInfo);

        assertEquals("foo-stream", ci.getStreamName());
        assertEquals("foo-name", ci.getName());
        assertEquals(DateTimeUtils.parseDateTime("2020-11-05T19:33:21.163377Z"), ci.getCreationTime());
        assertEquals(DateTimeUtils.parseDateTime("2023-08-29T19:33:21.163377Z"), ci.getTimestamp());

        SequenceInfo sqi = ci.getDelivered();
        assertEquals(1, sqi.getConsumerSequence());
        assertEquals(2, sqi.getStreamSequence());
        assertTrue(sqi.toString().contains("SequenceInfo")); // coverage

        assertEquals(1, sqi.getConsumerSequence());
        assertEquals(2, sqi.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T19:33:21.163377Z"), sqi.getLastActive());

        sqi = ci.getAckFloor();
        assertEquals(3, sqi.getConsumerSequence());
        assertEquals(4, sqi.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T20:33:21.163377Z"), sqi.getLastActive());

        assertEquals(24, ci.getNumPending());
        assertEquals(17, ci.getNumWaiting());
        assertEquals(42, ci.getNumAckPending());
        assertEquals(42, ci.getRedelivered());
        assertTrue(ci.getPaused());
        assertEquals(Duration.ofSeconds(20), ci.getPauseRemaining());
        assertTrue(ci.isPushBound());

        ConsumerConfiguration c = ci.getConsumerConfiguration();
        assertEquals("foo-desc", c.getDescription());
        assertEquals("foo-name", c.getDurable());
        assertEquals("foo-name", c.getName());
        assertEquals("deliver.subject", c.getDeliverSubject());
        assertEquals("deliver-group", c.getDeliverGroup());
        assertEquals(DeliverPolicy.All, c.getDeliverPolicy());
        assertEquals(42, c.getStartSequence());
        assertEquals(DateTimeUtils.parseDateTime("2020-11-05T19:33:21.163377Z"), c.getStartTime());
        assertEquals(AckPolicy.All, c.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), c.getAckWait());
        assertEquals(10, c.getMaxDeliver());
        assertEquals(42, c.getMaxAckPending());
        assertEquals(ReplayPolicy.Original, c.getReplayPolicy());
        assertEquals("sub.single", c.getFilterSubject());
        assertEquals(List.of("sub.single"), c.getFilterSubjects());
        assertFalse(c.hasMultipleFilterSubjects());
        assertEquals(List.of("pgroup1", "pgroup2"), c.getPriorityGroups());
        assertEquals("sample_freq-value", c.getSampleFrequency());
        assertEquals(73, c.getRateLimit());
        assertEquals(Duration.ofSeconds(20), c.getIdleHeartbeat());
        assertTrue(c.isFlowControl());
        assertEquals(128, c.getMaxPullWaiting());
        assertTrue(c.isHeadersOnly());
        assertTrue(c.isMemStorage());
        assertEquals(55, c.getMaxBatch());
        assertEquals(6666666666L, c.getMaxBytes());
        assertEquals(Duration.ofSeconds(40), c.getMaxExpires());
        assertEquals(Duration.ofSeconds(50), c.getInactiveThreshold());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)), c.getBackoff());
        assertEquals(Map.of("meta-test-key", "meta-test-value"), c.getMetadata());
        assertEquals(5, c.getNumReplicas());
        assertEquals(DateTimeUtils.parseDateTime("2024-03-02T10:43:32.062847087Z"), c.getPauseUntil());
        assertEquals(PriorityPolicy.Overflow, c.getPriorityPolicy());
        assertEquals(Duration.ofSeconds(60), c.getPriorityTimeout());

        ClusterInfo clusterInfo = ci.getClusterInfo();
        assertNotNull(clusterInfo);
        assertEquals("clustername", clusterInfo.getName());
        assertEquals("raftgroupname", clusterInfo.getRaftGroup());
        assertEquals("clusterleader", clusterInfo.getLeader());
        assertTrue(clusterInfo.isSystemAccount());
        assertEquals("trafficaccountname", clusterInfo.getTrafficAccount());
        assertEquals(DateTimeUtils.parseDateTime("2025-08-29T19:33:21.163377Z"), clusterInfo.getLeaderSince());
        List<Replica> reps = clusterInfo.getReplicas();
        assertNotNull(reps);
        assertEquals(2, reps.size());

        Replica r0 = reps.get(0);
        assertEquals("name0", r0.getName());
        assertTrue(r0.isCurrent());
        assertTrue(r0.isOffline());
        assertEquals(Duration.ofSeconds(230), r0.getActive());
        assertEquals(3, r0.getLag());

        Replica r1 = reps.get(1);
        assertEquals("name1", r1.getName());
        assertFalse(r1.isCurrent());
        assertFalse(r1.isOffline());
        assertEquals(Duration.ofSeconds(240), r1.getActive());
        assertEquals(4, r1.getLag());

        assertNotNull(ci.getPriorityGroupStates());
        assertEquals(2, ci.getPriorityGroupStates().size());
        PriorityGroupState pgs = ci.getPriorityGroupStates().get(0);
        assertEquals("group1", pgs.getGroup());

        assertEquals("pci1", pgs.getPinnedClientId());
        assertEquals(DateTimeUtils.parseDateTime("2025-09-24T16:01:01.163377Z"), pgs.getPinnedTime());
        pgs = ci.getPriorityGroupStates().get(1);
        assertEquals("group2", pgs.getGroup());
        assertEquals("pci2", pgs.getPinnedClientId());
        assertEquals(DateTimeUtils.parseDateTime("2025-09-24T16:02:02.163377Z"), pgs.getPinnedTime());
    }

    @Test
    public void testConsumerConfigurationMultipleFilterSubjects() {
        String json = "{\"filter_subjects\":[\"sub.a\",\"sub.b\"]}";
        ConsumerConfiguration c = new ConsumerConfiguration(LazyJsonParser.parseUnchecked(json));
        assertNull(c.getFilterSubject());
        assertEquals(List.of("sub.a", "sub.b"), c.getFilterSubjects());
        assertTrue(c.hasMultipleFilterSubjects());
    }
}
