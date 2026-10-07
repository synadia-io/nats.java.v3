// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.utils.ByteArrayBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static io.synadia.client.utils.NatsConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class OutboundBufferTests {

    private static OutboundBuffer buffer() {
        return new OutboundBuffer(Options.builder().build());
    }

    private static boolean publish(OutboundBuffer ob, String subject, String replyTo, Headers headers, byte[] data) {
        return ob.publish(subject, replyTo, headers, data, false, false, -1, 4096, true);
    }

    // What the classic writer puts on the wire for the same message
    private static byte[] classicWire(String subject, String replyTo, Headers headers, byte[] data) {
        NatsMessage m = new InternalPublishableMessage(data, subject, replyTo, headers, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayBuilder bab = m.getProtocolBab();
        out.write(bab.internalArray(), 0, bab.length());
        out.write(CR);
        out.write(LF);
        byte[] h = new byte[m.getPayloadSize() + 16];
        int hl = m.copyNotEmptyHeaders(0, h);
        out.write(h, 0, hl);
        out.write(data, 0, data.length);
        out.write(CR);
        out.write(LF);
        return out.toByteArray();
    }

    private static void assertSameAsClassic(String subject, String replyTo, Headers headers, byte[] data) {
        OutboundBuffer ob = buffer();
        assertTrue(publish(ob, subject, replyTo, headers, data));
        assertArrayEquals(classicWire(subject, replyTo, headers, data), ob.dataBytesForTest(),
            "subject=" + subject + " replyTo=" + replyTo + " headers=" + headers);
        assertEquals(1, ob.pendingCount());
    }

    @Test
    public void testPublishEncodingMatchesClassic() {
        byte[] data = "hello".getBytes(UTF_8);
        Headers h = new Headers().put("key", "value").put("multi", "a", "b");
        assertSameAsClassic("subject", null, null, data);
        assertSameAsClassic("subject", "reply.to", null, data);
        assertSameAsClassic("subject", null, h, data);
        assertSameAsClassic("subject", "reply.to", h, data);
        assertSameAsClassic("subject", null, null, EMPTY_BODY);
        assertSameAsClassic("subject", "reply.to", h, EMPTY_BODY);
        assertSameAsClassic("sübjéct.ü", "rëply.ü", h, data);
        assertSameAsClassic("subject", null, null, new byte[123_456]);
    }

    @Test
    public void testProtocolEncoding() {
        OutboundBuffer ob = buffer();
        ob.openData();
        assertTrue(ob.sub("sub.ject", null, "12", false, 4096, true));
        assertTrue(ob.sub("sub.jéct", "queue", "13", false, 4096, true));
        assertTrue(ob.unsub("12", 0));
        assertTrue(ob.unsub("13", 5));
        assertTrue(ob.protocol(OP_PING_BYTES, OP_PING_BYTES.length, true, false, false));
        String expected = "SUB sub.ject 12\r\nSUB sub.jéct queue 13\r\nUNSUB 12\r\nUNSUB 13 5\r\nPING\r\n";
        assertEquals(expected, new String(ob.dataBytesForTest(), UTF_8));
        assertEquals(5, ob.pendingCount());
    }

    @Test
    public void testPongGoesToControlLane() {
        OutboundBuffer ob = buffer();
        ob.openData();
        assertTrue(publish(ob, "subject", null, null, EMPTY_BODY));
        assertTrue(ob.protocol(OP_PONG_BYTES, OP_PONG_BYTES.length, true, true, true));
        assertEquals("PONG\r\n", new String(ob.controlBytesForTest(), UTF_8));
        assertEquals(1, ob.pendingCount());
    }

    @Test
    public void testInternalGoesToControlLaneWhileDataHeldBack() {
        OutboundBuffer ob = buffer(); // data lane starts held back
        assertTrue(ob.sub("resub", null, "1", true, 4096, true));
        assertTrue(ob.sub("user", null, "2", false, 4096, true));
        assertEquals("SUB resub 1\r\n", new String(ob.controlBytesForTest(), UTF_8));
        assertEquals("SUB user 2\r\n", new String(ob.dataBytesForTest(), UTF_8));

        ob.openData();
        assertTrue(ob.sub("internal", null, "3", true, 4096, true));
        assertEquals("SUB user 2\r\nSUB internal 3\r\n", new String(ob.dataBytesForTest(), UTF_8));
    }

    @Test
    public void testDisconnectedFiltersProtocolAndKeepsPublishes() {
        OutboundBuffer ob = buffer();
        ob.openData();
        assertTrue(publish(ob, "one", null, null, "1".getBytes(UTF_8)));
        assertTrue(ob.sub("sub", null, "7", false, 4096, true));
        assertTrue(publish(ob, "two", null, null, "2".getBytes(UTF_8)));
        assertTrue(ob.protocol(OP_PING_BYTES, OP_PING_BYTES.length, true, false, false));
        assertTrue(ob.unsub("7", 0));
        assertTrue(publish(ob, "three", null, null, "3".getBytes(UTF_8)));
        assertTrue(ob.protocol(OP_PONG_BYTES, OP_PONG_BYTES.length, true, true, true));
        assertEquals(6, ob.pendingCount());

        ob.disconnected();
        assertEquals("PUB one 1\r\n1\r\nPUB two 1\r\n2\r\nPUB three 1\r\n3\r\n", new String(ob.dataBytesForTest(), UTF_8));
        assertEquals(0, ob.controlBytesForTest().length);
        assertEquals(3, ob.pendingCount());
        assertEquals(ob.dataBytesForTest().length, ob.pendingBytes());
    }

    @Test
    public void testHeadersAndDataAreCopiedAtPublish() {
        OutboundBuffer ob = buffer();
        Headers h = new Headers().put("key", "value");
        byte[] data = "abc".getBytes(UTF_8);
        assertTrue(publish(ob, "subject", null, h, data));
        byte[] before = ob.dataBytesForTest();
        h.add("key", "more");
        h.put("other", "x");
        data[0] = 'z';
        assertArrayEquals(before, ob.dataBytesForTest());
    }

    @Test
    public void testDiscardWhenFull() {
        OutboundBuffer ob = new OutboundBuffer(Options.builder()
            .maxMessagesInOutgoingQueue(2).discardMessagesWhenOutgoingQueueFull().build());
        ob.openData();
        assertTrue(publish(ob, "a", null, null, EMPTY_BODY));
        assertTrue(publish(ob, "b", null, null, EMPTY_BODY));
        assertFalse(publish(ob, "c", null, null, EMPTY_BODY));
        assertFalse(ob.sub("s", null, "1", false, 4096, true)); // user protocol can be discarded
        assertEquals(2, ob.pendingCount());
    }

    @Test
    public void testFullWithoutDiscardTimesOut() {
        OutboundBuffer ob = new OutboundBuffer(Options.builder()
            .maxMessagesInOutgoingQueue(1).writeQueuePushTimeout(100).build());
        ob.openData();
        assertTrue(publish(ob, "a", null, null, EMPTY_BODY));
        long start = System.nanoTime();
        IllegalStateException ise = assertThrows(IllegalStateException.class, () -> publish(ob, "b", null, null, EMPTY_BODY));
        assertTrue(ise.getMessage().startsWith(OUTPUT_QUEUE_IS_FULL));
        assertTrue(System.nanoTime() - start >= 90 * NANOS_PER_MILLI);
    }

    @Test
    public void testReconnectBuffer() {
        OutboundBuffer ob = new OutboundBuffer(Options.builder().reconnectBufferSize(64).build());
        assertTrue(ob.publish("subject", null, null, new byte[10], false, true, -1, 4096, true));
        assertThrows(IllegalStateException.class,
            () -> ob.publish("subject", null, null, new byte[50], false, true, -1, 4096, true));
        // not checked when connected
        assertTrue(ob.publish("subject", null, null, new byte[50], false, false, -1, 4096, true));
    }

    @Test
    public void testClientSideLimits() {
        OutboundBuffer ob = buffer();
        assertThrows(IllegalArgumentException.class,
            () -> ob.publish("subject", null, null, new byte[11], false, false, 10, 4096, true));
        assertThrows(IllegalArgumentException.class,
            () -> ob.publish("a.long.subject", null, null, new byte[1], false, false, 10, 10, true));
        assertTrue(ob.publish("a.long.subject", null, null, new byte[11], false, false, 10, 10, false));
        assertEquals(1, ob.pendingCount());
    }

    @Test
    public void testSubControlLineLimit() {
        OutboundBuffer ob = buffer();
        ob.openData();
        // "SUB abcdef 1\r\n" is 14 bytes
        assertTrue(ob.sub("abcdef", null, "1", false, 14, true));
        assertThrows(IllegalArgumentException.class, () -> ob.sub("abcdefg", null, "1", false, 14, true));
        assertTrue(ob.sub("abcdefg", null, "1", false, 14, false)); // not checked when limit checks are off
        assertEquals(2, ob.pendingCount());
    }

    @Test
    public void testClosed() {
        OutboundBuffer ob = buffer();
        ob.close();
        assertThrows(IllegalStateException.class, () -> publish(ob, "subject", null, null, EMPTY_BODY));
        assertThrows(IllegalStateException.class, () -> ob.sub("subject", null, "1", true, 4096, true));
        assertTrue(ob.protocol(OP_PONG_BYTES, OP_PONG_BYTES.length, true, true, true));
    }

    @Test
    public void testIntegerEncoding() {
        byte[] b = new byte[16];
        for (int v : new int[]{0, 1, 9, 10, 99, 100, 12345, Integer.MAX_VALUE}) {
            int end = OutboundBuffer.putInt(b, 0, v);
            assertEquals(Integer.toString(v), new String(b, 0, end, UTF_8));
            assertEquals(Integer.toString(v).length(), OutboundBuffer.digits(v));
        }
    }

    @Test
    public void testDataLaneIsBlocksOfBufferSize() {
        OutboundBuffer ob = new OutboundBuffer(Options.builder().bufferSize(100).build());
        ob.openData();
        byte[] data = new byte[30]; // "PUB s 30\r\n" (10) + 30 + 2 = 42 bytes per entry
        for (int i = 0; i < 5; i++) {
            assertTrue(publish(ob, "s", null, null, data));
        }
        // 42 + 42 fit in 100, the third starts a new block: 2 + 2 + 1
        assertEquals(3, ob.dataBlockCountForTest());
        assertEquals(5, ob.pendingCount());
        assertEquals(5 * 42, ob.pendingBytes());

        // a single entry bigger than bufferSize gets a block of its own
        assertTrue(publish(ob, "s", null, null, new byte[500]));
        assertEquals(4, ob.dataBlockCountForTest());
        assertTrue(publish(ob, "s", null, null, data));
        assertEquals(5, ob.dataBlockCountForTest());
    }

    @Test
    public void testFlushEntryEndsItsBlock() {
        OutboundBuffer ob = new OutboundBuffer(Options.builder().bufferSize(1000).build());
        ob.openData();
        assertTrue(publish(ob, "a", null, null, EMPTY_BODY));
        assertTrue(ob.publish("b", null, null, EMPTY_BODY, true, false, -1, 4096, true));
        assertTrue(publish(ob, "c", null, null, EMPTY_BODY));
        assertEquals(2, ob.dataBlockCountForTest());
    }

    @Test
    public void testWriterTakesOneBlockPerPass() throws Exception {
        OutboundBuffer ob = new OutboundBuffer(Options.builder().bufferSize(100).build());
        ob.openData();
        byte[] data = new byte[30];
        for (int i = 0; i < 5; i++) {
            assertTrue(publish(ob, "s", null, null, data));
        }
        assertTrue(ob.sub("x", null, "9", true, 4096, true)); // data lane open, so this is data, not control

        OutboundBuffer.Batch batch = ob.newBatch();
        assertTrue(ob.take(batch, () -> true));
        assertNotNull(batch.data);
        assertEquals(2, batch.data.count);
        assertEquals(84, batch.data.length);
        assertEquals(4, ob.pendingCount()); // 3 publishes and the SUB
        assertEquals(2, ob.dataBlockCountForTest());

        // the taken block is lost if its write fails; everything still queued survives the disconnect
        ob.recycle(batch.data);
        batch.data = null;
        ob.disconnected();
        assertEquals(3, ob.pendingCount());
        String remaining = new String(ob.dataBytesForTest(), UTF_8);
        assertEquals(3, remaining.split("PUB s 30", -1).length - 1);
        assertFalse(remaining.contains("SUB x 9"));
    }

    @Test
    public void testLargePoolKeepsBiggestUpToMax() throws Exception {
        OutboundBuffer ob = new OutboundBuffer(Options.builder().bufferSize(100).build());
        ob.openData();
        OutboundBuffer.Batch batch = ob.newBatch();

        // "PUB s 500\r\n" (11) + 500 + 2 = 513 bytes; nothing is kept before setLargePoolMax
        writeOne(ob, batch, 500);
        assertEquals(0, ob.largePoolForTest().length);

        ob.setLargePoolMax(1100);
        ob.setLargePoolMax(5000); // only the first call has effect
        writeOne(ob, batch, 500);
        assertArrayEquals(new int[]{513}, ob.largePoolForTest());

        // 813 does not fit next to 513, so the smaller block makes room
        writeOne(ob, batch, 800);
        assertArrayEquals(new int[]{813}, ob.largePoolForTest());

        // the kept block is reused for an entry it can hold
        assertTrue(publish(ob, "s", null, null, new byte[700]));
        assertEquals(0, ob.largePoolForTest().length);

        // 313 does not fit in what is left of the 813 block, so it gets its own block,
        // and when both come back the smaller one is not kept
        assertTrue(publish(ob, "s", null, null, new byte[300]));
        assertEquals(2, ob.dataBlockCountForTest());
        takeAndRecycle(ob, batch);
        takeAndRecycle(ob, batch);
        assertArrayEquals(new int[]{813}, ob.largePoolForTest());

        // bigger than the max is never kept
        writeOne(ob, batch, 1200);
        assertArrayEquals(new int[]{813}, ob.largePoolForTest());
    }

    private static void writeOne(OutboundBuffer ob, OutboundBuffer.Batch batch, int payloadSize) throws InterruptedException {
        assertTrue(publish(ob, "s", null, null, new byte[payloadSize]));
        takeAndRecycle(ob, batch);
    }

    private static void takeAndRecycle(OutboundBuffer ob, OutboundBuffer.Batch batch) throws InterruptedException {
        assertTrue(ob.take(batch, () -> true));
        assertNotNull(batch.data);
        ob.recycle(batch.data);
        batch.data = null;
    }
}
