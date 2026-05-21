package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.synadia.client.Message;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class NatsMessageJetStreamMetaDataTests extends TestBase {

    public static final String TestMetaV0 = "$JS.ACK.test-stream.test-consumer.1.2.3.1605139610113260000";
    public static final String TestMetaV1 = "$JS.ACK.test-stream.test-consumer.1.2.3.1605139610113260000.4";
    public static final String TestMetaV2 = "$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000.4";
    public static final String TestMetaVFuture = "$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000.4.dont.care.how.many.more";
    public static final String InvalidMetaNoAck = "$JS.nope.test-stream.test-consumer.1.2.3.1605139610113260000";
    public static final String InvalidMetaData = "$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000.not-a-number";
    public static final String InvalidMetaLt8Tokens = "$JS.ACK.less-than.8-tokens.1.2.3";
    public static final String InvalidMeta10Tokens = "$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1.2.3.1605139610113260000";

    public NatsMessage getTestNatsMessage() {
        return getTestMessage("replyTo", mockSid());
    }

    public NatsMessage getTestJsMessage() {
        return getTestMessage(TestMetaV2, mockSid());
    }

    public NatsMessage getTestJsMessage(long seq) {
        return getTestJsMessage(seq, mockSid());
    }

    public NatsMessage getTestJsMessage(long seq, String sid) {
        return getTestMessage("$JS.ACK.v2Domain.v2Hash.test-stream.test-consumer.1." + seq + "." + seq + ".1605139610113260000.4", sid);
    }

    private static final AtomicInteger MOCK_SID_HOLDER = new AtomicInteger(4273);
    public static String mockSid() {
        return "" + MOCK_SID_HOLDER.incrementAndGet();
    }

    public NatsMessage getTestMessage(String replyTo) {
        return new IncomingMessageFactory(mockSid(), "subj", replyTo, 0).getMessage();
    }

    public NatsMessage getTestMessage(String replyTo, String sid) {
        return new IncomingMessageFactory(sid, "subj", replyTo, 0).getMessage();
    }

    @Test
    public void testMetaData() {
        Message msg = getTestMessage(TestMetaV0);
        JetStreamMetaData meta = msg.metaData(); // first time, coverage lazy check is null
        assertNotNull(msg.metaData()); // 2nd time, coverage lazy check is not null
        assertNotNull(meta.toString()); // COVERAGE toString

        validateMeta(false, false, getTestMessage(TestMetaV0));
        validateMeta(true, false, getTestMessage(TestMetaV1));
        validateMeta(true, true, getTestMessage(TestMetaV2));
        validateMeta(true, true, getTestMessage(TestMetaVFuture));

        // since I can't make a JS message directly, do it indirectly
        NatsMessage nm = getTestMessage(InvalidMetaLt8Tokens);
        nm.replyTo = InvalidMetaNoAck;
        assertThrows(IllegalArgumentException.class, () -> nm.metaData().getStream());
    }

    private void validateMeta(boolean hasPending, boolean hasDomainHashToken, Message msg) {
        JetStreamMetaData meta = msg.metaData();
        assertEquals("test-stream", meta.getStream());
        assertEquals("test-consumer", meta.getConsumer());
        assertEquals(1, meta.deliveredCount());
        assertEquals(2, meta.streamSequence());
        assertEquals(3, meta.consumerSequence());

        ZonedDateTime localTs = meta.timestamp();
        assertEquals(2020, localTs.getYear());
        assertEquals(6, localTs.getMinute());
        assertEquals(113260000, localTs.getNano());
        
        ZonedDateTime utcTs = localTs.withZoneSameInstant(DateTimeUtils.ZONE_ID_UTC);
        assertEquals(0, utcTs.getHour());

        assertEquals(hasPending ? 4L : -1L, meta.pendingCount());

        if (hasDomainHashToken) {
            assertEquals("v2Domain", meta.getDomain());
            assertEquals("v2Hash", meta.getAccountHash());
        }
        else {
            assertNull(meta.getDomain());
            assertNull(meta.getAccountHash());
        }
    }

    @Test
    public void testNotInVersion() {
        assertEquals(-1, new JetStreamMetaData(getTestMessage(TestMetaV0)).pendingCount());
        assertNull(new JetStreamMetaData(getTestMessage(TestMetaV0)).getDomain());
        assertNull(new JetStreamMetaData(getTestMessage(TestMetaV0)).getAccountHash());
        assertNull(new JetStreamMetaData(getTestMessage(TestMetaV1)).getDomain());
        assertNull(new JetStreamMetaData(getTestMessage(TestMetaV1)).getAccountHash());
    }

    @Test
    public void testInvalidMetaData() {
        // ----------------------------------------------------------------------
        // have to request data, like calling .getStream() because it parses lazy
        // ----------------------------------------------------------------------

        assertThrows(IllegalArgumentException.class, () -> getTestMessage(InvalidMetaData).metaData().getStream());
        assertThrows(IllegalArgumentException.class, () -> getTestMessage(InvalidMetaLt8Tokens).metaData().getStream());
        assertThrows(IllegalArgumentException.class, () -> getTestMessage(InvalidMeta10Tokens).metaData().getStream());

        // InvalidMetaNoAck is actually not even a JS message
        assertThrows(IllegalStateException.class, () -> getTestMessage(InvalidMetaNoAck).metaData());

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.invalid.test-stream.test-consumer.1.2.3.1605139610113260000")));

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.ACK.not.enough.parts")).getStream());

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.ACK.test-stream.test-consumer.invalid.2.3.1605139610113260000")).getStream());

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.ACK.test-stream.test-consumer.1.invalid.3.1605139610113260000")).getStream());

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.ACK.test-stream.test-consumer.1.2.invalid.1605139610113260000")).getStream());

        assertThrows(IllegalArgumentException.class,
            () -> new JetStreamMetaData(getTestMessage("$JS.ACK.test-stream.test-consumer.1.2.3.1605139610113260000.invalid")).getStream());
    }
}
