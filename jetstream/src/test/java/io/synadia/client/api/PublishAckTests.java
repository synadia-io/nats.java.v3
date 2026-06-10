package io.synadia.client.api;

import io.synadia.client.impl.JetStreamApiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;

import static io.synadia.client.utils.TestBase.getDataMessage;
import static org.junit.jupiter.api.Assertions.*;

public class PublishAckTests {
    @Test
    public void testAllFieldsSet() {
        String json = "{" +
            "\"stream\":\"test-stream\"," +
            "\"seq\":42," +
            "\"domain\":\"test-domain\"," +
            "\"duplicate\":true," +
            "\"val\":\"-73\"," +
            "\"batch\":\"batch-id\"," +
            "\"count\":66" +
            "}";

        try {
            PublishAck ack = new PublishAck(getDataMessage(json));
            assertEquals("test-stream", ack.getStream());
            assertEquals("test-domain", ack.getDomain());
            assertEquals(42, ack.getSequenceNumber());
            assertTrue(ack.isDuplicate());
            assertEquals("-73", ack.getVal());
            assertEquals("batch-id", ack.getBatchId());
            assertEquals(66, ack.getBatchSize());
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + e.getMessage());
        }
    }

    @Test
    public void testRequiredFieldsSet() {
        String json = "{\"stream\":\"test-stream\",\"seq\":0}";
        try {
            PublishAck ack = new PublishAck(getDataMessage(json));
            assertEquals("test-stream", ack.getStream());
            assertEquals(0, ack.getSequenceNumber());
            assertNull(ack.getDomain());
            assertFalse(ack.isDuplicate());
            assertNull(ack.getVal());
            assertNull(ack.getBatchId());
            assertEquals(-1, ack.getBatchSize());
        }
        catch (Exception e) {
            fail("Unexpected Exception: " + e.getMessage());
        }
    }

    @Test
    public void testUnsignedFields() throws Exception {
        BigInteger twoPow63 = new BigInteger("9223372036854775808"); // 2^63, smallest top-half uint64

        // count (uint64) in the top half of the range: the long getter returns the two's-complement
        // bit pattern (negative); the BigInteger companion returns the true non-negative value.
        PublishAck topHalf = new PublishAck(getDataMessage(
            "{\"stream\":\"s\",\"seq\":42,\"count\":9223372036854775808}"));
        assertEquals(Long.MIN_VALUE, topHalf.getBatchSize());
        assertEquals(twoPow63, topHalf.getBatchSizeAsBigInteger());
        assertEquals(BigInteger.valueOf(42), topHalf.getSequenceNumberAsBigInteger());

        // absent count -> -1 "not a batch publish" sentinel, preserved by both getters
        PublishAck noBatch = new PublishAck(getDataMessage("{\"stream\":\"s\",\"seq\":1}"));
        assertEquals(-1L, noBatch.getBatchSize());
        assertEquals(BigInteger.valueOf(-1), noBatch.getBatchSizeAsBigInteger());

        // EDGE: uint64 max (2^64-1) reads as long -1, colliding with the "not a batch" sentinel, so
        // both getters report -1 rather than 2^64-1. A real batch count never approaches this.
        PublishAck maxCount = new PublishAck(getDataMessage(
            "{\"stream\":\"s\",\"seq\":1,\"count\":18446744073709551615}"));
        assertEquals(-1L, maxCount.getBatchSize());
        assertEquals(BigInteger.valueOf(-1), maxCount.getBatchSizeAsBigInteger());

        // a top-half seq trips the seq<0 validation (edge of the -1 sentinel design) and throws
        IOException ioe = assertThrows(IOException.class, () -> new PublishAck(getDataMessage(
            "{\"stream\":\"s\",\"seq\":9223372036854775808}")));
        assertEquals("Invalid JetStream ack.", ioe.getMessage());
    }

    @Test
    public void testThrowsOnGarbage() {
        assertThrows(JetStreamApiException.class, () -> new PublishAck(getDataMessage("notjson")));
    }
      
    @Test
    public void testThrowsOnERR() {
        String json ="{" +
                "  \"type\": \"io.nats.jetstream.api.v1.pub_ack_response\"," +
                "  \"error\": {" +
                "    \"code\": 500," +
                "    \"description\": \"the description\"" +
                "  }" +
                "}";

        JetStreamApiException jsapi = assertThrows(JetStreamApiException.class,
                () -> new PublishAck(getDataMessage(json)).throwOnHasError());
        assertEquals(500, jsapi.getErrorCode());
    }

    @Test
    public void testInvalidResponse() {
        IOException ioe = assertThrows(IOException.class,
            () -> new PublishAck(getDataMessage("{\"stream\":\"no sequence\"}")));
        assertEquals("Invalid JetStream ack.", ioe.getMessage());

        ioe = assertThrows(IOException.class,
            () -> new PublishAck(getDataMessage("{\"seq\":1}")));
        assertEquals("Invalid JetStream ack.", ioe.getMessage());
    }
}
