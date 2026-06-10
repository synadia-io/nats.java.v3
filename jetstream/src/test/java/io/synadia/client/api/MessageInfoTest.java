package io.synadia.client.api;

import io.synadia.client.Message;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.MessageInfo;
import io.synadia.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Base64;

import static io.nats.json.DateTimeUtils.DEFAULT_TIME;
import static io.nats.json.JsonWriteUtils.beginJson;
import static io.synadia.client.api.Status.EOB;
import static io.synadia.client.utils.NatsConstants.*;
import static org.junit.jupiter.api.Assertions.*;

public class MessageInfoTest {

    @Test
    public void testMessageInfoComprehensive() {
        // Test 1: Constructor with Status
        Status testStatus = new Status(404, "Not Found");
        String streamName = "test-stream";
        MessageInfo statusMessageInfo = new MessageInfo(testStatus, streamName);

        assertTrue(statusMessageInfo.isStatus());
        assertFalse(statusMessageInfo.isMessage());
        assertFalse(statusMessageInfo.isEobStatus());
        assertTrue(statusMessageInfo.isErrorStatus());
        assertEquals(testStatus, statusMessageInfo.getStatus());
        assertEquals(streamName, statusMessageInfo.getStream());
        assertNull(statusMessageInfo.getSubject());
        assertEquals(-1, statusMessageInfo.getSequence());
        assertNull(statusMessageInfo.getData());
        assertNull(statusMessageInfo.getTime());
        assertNull(statusMessageInfo.getHeaders());
        assertEquals(-1, statusMessageInfo.getLastSequence());
        assertEquals(-1, statusMessageInfo.getNumPending());

        // Test 2: Constructor with EOB Status
        MessageInfo eobMessageInfo = new MessageInfo(EOB, streamName);
        assertTrue(eobMessageInfo.isEobStatus());
        assertFalse(eobMessageInfo.isErrorStatus());

        // Test 3: Constructor with direct parsing (Message with headers)
        Headers headers = new Headers();
        headers.put(NATS_SUBJECT, "test.subject");
        headers.put(NATS_SEQUENCE, "12345");
        headers.put(NATS_TIMESTAMP, "2023-01-01T12:00:00Z");
        headers.put(NATS_STREAM, "direct-stream");
        headers.put(NATS_LAST_SEQUENCE, "67890");
        headers.put(NATS_NUM_PENDING, "6");
        headers.put("custom-header", "custom-value");

        byte[] testData = "test message data".getBytes();
        Message directMessage = new NatsMessage("test.subject", null, headers, testData);

        MessageInfo testInfo = new MessageInfo(directMessage, "override-stream", true);

        assertEquals("test.subject", testInfo.getSubject());
        assertEquals(12345L, testInfo.getSequence());
        assertArrayEquals(testData, testInfo.getData());
        assertNotNull(testInfo.getTime());
        assertEquals("direct-stream", testInfo.getStream());
        assertEquals(67890L, testInfo.getLastSequence());
        assertEquals(5L, testInfo.getNumPending()); // NUM_PENDING - 1
        assertNotNull(testInfo.getHeaders());
        assertEquals("custom-value", testInfo.getHeaders().getFirst("custom-header"));
        assertNull(testInfo.getHeaders().getFirst(NATS_SUBJECT)); // Control headers removed
        assertTrue(testInfo.isMessage());
        assertFalse(testInfo.isStatus());
        assertNull(testInfo.getStatus());

        // Test 4: Constructor with direct parsing (Message without headers)
        // This pathway us a no-header direct
        Message noHeaderMessage = new NatsMessage("test.subject", null, null, testData);
        MessageInfo noHeaderMessageInfo = new MessageInfo(noHeaderMessage, streamName, true);

        assertNull(noHeaderMessageInfo.getSubject());
        assertEquals(-1L, noHeaderMessageInfo.getSequence());
        assertArrayEquals(testData, noHeaderMessageInfo.getData());
        assertNull(noHeaderMessageInfo.getTime());
        assertEquals(streamName, noHeaderMessageInfo.getStream());
        assertEquals(-1L, noHeaderMessageInfo.getLastSequence());
        assertEquals(-1L, noHeaderMessageInfo.getNumPending());
        assertNotNull(noHeaderMessageInfo.getHeaders());
        assertTrue(noHeaderMessageInfo.isMessage());

        // Test 5: Constructor with JSON parsing (simulated with valid JSON message)
        String jsonPayload = beginJson()
            .append("\"message\":{")
            .append("\"subject\":\"json.subject\",")
            .append("\"seq\":54321,")
            .append("\"data\":\"").append(Base64.getEncoder().encodeToString("json data".getBytes())).append("\",")
            .append("\"time\":\"2023-01-01T15:30:00Z\"")
            .append("}")
            .append("}").toString();

        Message jsonMessage = new NatsMessage("response.subject", null, null, jsonPayload.getBytes());
        MessageInfo jsonMessageInfo = new MessageInfo(jsonMessage, "json-stream", false);

        assertEquals("json.subject", jsonMessageInfo.getSubject());
        assertEquals(54321L, jsonMessageInfo.getSequence());
        assertArrayEquals("json data".getBytes(), jsonMessageInfo.getData());
        assertNotNull(jsonMessageInfo.getTime());
        assertEquals("json-stream", jsonMessageInfo.getStream());
        assertTrue(jsonMessageInfo.isMessage());

        // Test 6: Test toString() method for different types
        String statusString = statusMessageInfo.toString();
        assertTrue(statusString.contains("MessageInfo"));
        assertTrue(statusString.contains("status_code"));

        String messageString = testInfo.toString();
        assertTrue(messageString.contains("MessageInfo"));
        assertTrue(messageString.contains("seq"));
        assertTrue(messageString.contains("subject"));

        // Test 7: Edge cases with invalid sequence numbers
        Headers invalidHeaders = new Headers();
        invalidHeaders.put(NATS_SEQUENCE, "invalid");
        invalidHeaders.put(NATS_LAST_SEQUENCE, "invalid");
        invalidHeaders.put(NATS_NUM_PENDING, "invalid");
        invalidHeaders.put(NATS_TIMESTAMP, "invalid");
        Message invalidMessage = new NatsMessage("test.subject", null, invalidHeaders, testData);
        MessageInfo mi = new MessageInfo(invalidMessage, streamName, true);
        assertEquals(-1, mi.getSequence());
        assertEquals(-1, mi.getLastSequence());
        assertEquals(-1, mi.getNumPending());
        assertEquals(DEFAULT_TIME, mi.getTime());

        // Test 8: Test with error message
        String errorJson = "{\"error\":{\"code\":400,\"description\":\"Bad Request\"}}";
        Message errorMessage = new NatsMessage("error.subject", null, null, errorJson.getBytes());
        MessageInfo errorMessageInfo = new MessageInfo(errorMessage, streamName, false);

        assertFalse(errorMessageInfo.isMessage());
        assertTrue(errorMessageInfo.hasError());

        // Test 9: Verify all method combinations work correctly
        assertNotNull(testInfo.getSubject());
        assertTrue(testInfo.getSequence() > 0);
        assertNotNull(testInfo.getData());
        assertNotNull(testInfo.getTime());
        assertNotNull(testInfo.getHeaders());
        assertNotNull(testInfo.getStream());
        assertTrue(testInfo.getLastSequence() > 0);
        assertTrue(testInfo.getNumPending() >= 0);
        assertNull(testInfo.getStatus());
        assertTrue(testInfo.isMessage());
        assertFalse(testInfo.isStatus());
        assertFalse(testInfo.isEobStatus());
        assertFalse(testInfo.isErrorStatus());
        assertFalse(testInfo.hasError());
    }

    @Test
    public void testUnsignedSequences() {
        BigInteger twoPow63 = new BigInteger("9223372036854775808"); // 2^63, smallest top-half uint64

        // JSON path (readUnsignedLong): seq in the top half of the uint64 range
        String json = beginJson()
            .append("\"message\":{\"subject\":\"s\",\"seq\":9223372036854775808}")
            .append("}").toString();
        MessageInfo jsonMi = new MessageInfo(new NatsMessage("r", null, null, json.getBytes()), "stream", false);
        assertEquals(Long.MIN_VALUE, jsonMi.getSequence());           // two's-complement bit pattern
        assertEquals(twoPow63, jsonMi.getSequenceAsBigInteger());     // true non-negative value

        // direct header path (safeParseLong -> Long.parseUnsignedLong fallback)
        Headers h = new Headers();
        h.put(NATS_SEQUENCE, "9223372036854775808");      // 2^63
        h.put(NATS_LAST_SEQUENCE, "9223372036854775808"); // 2^63
        h.put(NATS_NUM_PENDING, "6");
        MessageInfo mi = new MessageInfo(new NatsMessage("s", null, h, new byte[0]), "stream", true);
        assertEquals(Long.MIN_VALUE, mi.getSequence());
        assertEquals(twoPow63, mi.getSequenceAsBigInteger());
        assertEquals(Long.MIN_VALUE, mi.getLastSequence());
        assertEquals(twoPow63, mi.getLastSequenceAsBigInteger());
        assertEquals(5L, mi.getNumPending());                        // header (6) - 1
        assertEquals(BigInteger.valueOf(5), mi.getNumPendingAsBigInteger());

        // -1 "not known" sentinel preserved by the BigInteger companions
        MessageInfo status = new MessageInfo(new Status(404, "Not Found"), "stream");
        assertEquals(BigInteger.valueOf(-1), status.getSequenceAsBigInteger());
        assertEquals(BigInteger.valueOf(-1), status.getLastSequenceAsBigInteger());
        assertEquals(BigInteger.valueOf(-1), status.getNumPendingAsBigInteger());
    }
}
