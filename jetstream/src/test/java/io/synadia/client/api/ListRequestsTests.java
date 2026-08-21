package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.synadia.client.impl.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

public class ListRequestsTests extends JetStreamTestBase {

    @Test
    public void testConsumerListResponse() throws Exception {
        String json = dataAsString("ConsumerListResponse.json");
        ConsumerListReader clr = new ConsumerListReader();
        clr.process(getDataMessage(json));
        assertEquals(2, clr.getConsumers().size());

        ConsumerInfo ci = clr.getConsumers().get(0);
        assertEquals("stream-1", ci.getStreamName());
        assertEquals("cname1", ci.getName());
        assertEquals(DateTimeUtils.parseDateTime("2021-01-20T23:41:08.579594Z"), ci.getCreationTime());
        assertEquals(5, ci.getNumAckPending());
        assertEquals(6, ci.getRedelivered());
        assertEquals(7, ci.getNumWaiting());
        assertEquals(8, ci.getNumPending());

        ConsumerConfiguration cc = ci.getConsumerConfiguration();
        assertEquals("cname1", cc.getDurable());
        assertEquals("strm1-deliver", cc.getDeliverSubject());
        assertEquals(DeliverPolicy.All, cc.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, cc.getAckPolicy());
        assertEquals(Duration.ofSeconds(30), cc.getAckWait());
        assertEquals(99, cc.getMaxDeliver());
        assertEquals(ReplayPolicy.Instant, cc.getReplayPolicy());

        SequenceInfo sinfo = ci.getDelivered();
        assertEquals(1, sinfo.getConsumerSequence());
        assertEquals(2, sinfo.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T19:33:21.163377Z"), sinfo.getLastActive());

        sinfo = ci.getAckFloor();
        assertEquals(3, sinfo.getConsumerSequence());
        assertEquals(4, sinfo.getStreamSequence());
        assertEquals(DateTimeUtils.parseDateTime("2022-06-29T20:33:21.163377Z"), sinfo.getLastActive());

        clr = new ConsumerListReader();
        clr.process(getDataMessage("{}"));
        assertEquals(0, clr.getConsumers().size());
    }

    @Test
    public void testStreamListResponse() throws Exception {
        String json = dataAsString("StreamListResponse.json");
        StreamListReader slr = new StreamListReader();
        slr.process(getDataMessage(json));
        assertEquals(2, slr.getStreams().size());
        assertEquals("stream-0", slr.getStreams().get(0).getConfiguration().getName());
        assertEquals("stream-1", slr.getStreams().get(1).getConfiguration().getName());
    }

    @Test
    public void testListRequestEngine() throws Exception {
        TestableListRequestEngine tlr = new TestableListRequestEngine();
        assertTrue(tlr.hasMore());
        assertEquals("{\"offset\":0}", new String(tlr._nextJson()));
        assertEquals("{\"offset\":0}", new String(tlr._nextJson("name", null)));
        assertEquals("{\"offset\":0,\"name\":\"value\"}", new String(tlr._nextJson("name", "value")));
        tlr = new TestableListRequestEngine(getDataMessage(dataAsString("ListResponsePage1.json")));
        assertEquals(15, tlr.getTotal());
        assertEquals(10, tlr.getLimit());
        assertEquals(0, tlr.getLastOffset());

        assertTrue(tlr.hasMore());
        assertEquals("{\"offset\":10}", new String(tlr._nextJson()));
        assertEquals("{\"offset\":10,\"name\":\"value\"}", new String(tlr._nextJson("name", "value")));
        tlr = new TestableListRequestEngine(getDataMessage(dataAsString("ListResponsePage2.json")));
        assertEquals(15, tlr.getTotal());
        assertEquals(10, tlr.getLimit());
        assertEquals(10, tlr.getLastOffset());

        assertFalse(tlr.hasMore());
        assertNull(tlr._nextJson());
        assertNull(tlr._nextJson("name", "value"));

        String json = dataAsString("GenericErrorResponse.json");
        NatsMessage m = new NatsMessage("sub", null, json.getBytes(StandardCharsets.US_ASCII));
        assertThrows(JetStreamApiException.class, () -> new TestableListRequestEngine(m));
    }
}
