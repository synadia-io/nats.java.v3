package io.synadia.client.api;

import io.synadia.client.support.DateTimeUtils;
import io.synadia.client.support.JsonParser;
import io.synadia.client.support.JsonValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumerInfoTests {

    static JsonValue vConsumerInfo = JsonParser.parseUnchecked(dataAsString("ConsumerInfo.json"));

    @Test
    public void testConsumerInfo() {
        ConsumerInfo ci = new ConsumerInfo(vConsumerInfo);
        assertEquals("foo-stream", ci.getStreamName());
        assertEquals("foo-name", ci.getName());
        assertEquals(DateTimeUtils.parseDateTime("2020-11-05T19:33:21.163377Z"), ci.getCreationTime());
        assertEquals(DateTimeUtils.parseDateTime("2023-08-29T19:33:21.163377Z"), ci.getTimestamp());

        SequencePair sp = ci.getDelivered();
        assertEquals(1, sp.getConsumerSequence());
        assertEquals(2, sp.getStreamSequence());
        assertTrue(sp.toString().contains("SequenceInfo")); // coverage

        //noinspection CastCanBeRemovedNarrowingVariableType
        SequenceInfo sinfo = (SequenceInfo)sp;
        assertEquals(1, sinfo.getConsumerSequence());
        assertEquals(2, sinfo.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T19:33:21.163377Z"), sinfo.getLastActive());

        sp = ci.getAckFloor();
        assertEquals(3, sp.getConsumerSequence());
        assertEquals(4, sp.getStreamSequence());

        //noinspection CastCanBeRemovedNarrowingVariableType
        sinfo = (SequenceInfo)sp;
        assertEquals(3, sinfo.getConsumerSequence());
        assertEquals(4, sinfo.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T20:33:21.163377Z"), sinfo.getLastActive());

        assertEquals(24, ci.getNumPending());
        assertEquals(42, ci.getNumAckPending());
        assertEquals(42, ci.getRedelivered());
        assertTrue(ci.getPaused());
        assertEquals(Duration.ofSeconds(20), ci.getPauseRemaining());

        ConsumerConfiguration c = ci.getConsumerConfiguration();
        assertEquals("foo-name", c.getDurable());
        assertEquals("bar", c.getDeliverSubject());
        assertEquals(DeliverPolicy.All, c.getDeliverPolicy());
        assertEquals(AckPolicy.All, c.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), c.getAckWait());
        assertEquals(10, c.getMaxDeliver());
        assertEquals(ReplayPolicy.Original, c.getReplayPolicy());
        assertEquals(DateTimeUtils.parseDateTime("2024-03-02T10:43:32.062847087Z"), c.getPauseUntil());

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

        assertNotNull(pgs.toString()); // COVERAGE

        ci = new ConsumerInfo(JsonValue.EMPTY_MAP);
        assertTrue(ci.hasError());
        assertEquals("", ci.getStreamName());
        assertEquals("", ci.getName());
        assertEquals(DateTimeUtils.DEFAULT_TIME, ci.getCreationTime());
        assertNotNull(ci.getConsumerConfiguration());
        assertNotNull(ci.getDelivered());
        assertNotNull(ci.getAckFloor());
        assertEquals(0, ci.getNumPending());
        assertEquals(0, ci.getNumWaiting());
        assertEquals(0, ci.getNumAckPending());
        assertEquals(0, ci.getRedelivered());
    }

    @Test
    public void testToString() {
        // COVERAGE
        assertNotNull(new ConsumerInfo(vConsumerInfo).toString());
    }
}
