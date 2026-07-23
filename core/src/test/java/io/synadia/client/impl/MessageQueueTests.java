package io.synadia.client.impl;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.client.utils.NatsConstants.OUTPUT_QUEUE_INTERRUPTED;
import static io.synadia.client.utils.NatsConstants.OUTPUT_QUEUE_IS_FULL;
import static io.synadia.client.utils.TestBase.random;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class MessageQueueTests {
    private static NatsMessage getTestMessage() {
        return new NatsMessage(random(), null, null);
    }

    private static final long TEST_MESSAGE_BYTES = getTestMessage().getSizeInBytes();

    @SuppressWarnings("SameParameterValue")
    private static void pushTestMessages(WriterMessageQueue q, int count) {
        for (int i = 0; i < count; i++) {
            q.push(getTestMessage());
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void pushTestMessages(ConsumerMessageQueue q, int count) {
        for (int i = 0; i < count; i++) {
            q.push(getTestMessage());
        }
    }

    private static WriterMessageQueue newWriterMessageQueue() {
        return newWriterMessageQueue(-1);
    }

    private static WriterMessageQueue newWriterMessageQueue(int maxMessagesInOutgoingQueue) {
        return new WriterMessageQueue(maxMessagesInOutgoingQueue, false, 500);
    }

    @Test
    public void testEmptyPopConsumer() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        NatsMessage msg = q.pop(null, TimeUnit.MILLISECONDS);
        assertNull(msg);
    }

    @Test
    public void testPushPopConsumer() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        NatsMessage expected = getTestMessage();
        q.push(expected);
        NatsMessage actual = q.pop(null, TimeUnit.MILLISECONDS);
        assertEquals(expected, actual);
    }

    @Test
    public void testTimeoutConsumer() throws InterruptedException {
        long waitTimeNanos = 100 * 1_000_000L;
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        long start = System.nanoTime();
        NatsMessage msg = q.pop(waitTimeNanos, TimeUnit.NANOSECONDS);
        long end = System.nanoTime();
        long elapsed = end - start;
        assertNull(msg);
        assertTrue(elapsed > waitTimeNanos);
    }

    @Test
    public void testTimeoutZeroConsumer() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        NatsMessage expected = getTestMessage();
        q.push(expected);
        NatsMessage actual = q.pop(0L, TimeUnit.MILLISECONDS);
        assertNotNull(actual);
        assertEquals(expected, actual);
    }

    @Test
    public void testPauseResumeConsumer() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        Thread t = new Thread(() -> {
            try { Thread.sleep(100); } catch (Exception ignore) {}
            q.pause();
        });
        t.start();
        NatsMessage msg = q.pop(0L, TimeUnit.MILLISECONDS);
        assertNull(msg);

        NatsMessage expected = getTestMessage();
        q.push(expected);

        msg = q.pop(0L, TimeUnit.MILLISECONDS);
        assertNull(msg); // Haven't reset yet

        q.resume();
        msg = q.pop(null, TimeUnit.MILLISECONDS);
        assertEquals(expected, msg);
    }

    @Test
    public void testDrainConsumer() throws InterruptedException {
        // Possible flaky test, since we can't be sure of thread timing
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        Thread t = new Thread(() -> {
            try { Thread.sleep(100); } catch (Exception ignore) {}
            q.drain();
        });
        t.start();
        NatsMessage msg = q.pop(0L, TimeUnit.MILLISECONDS);
        assertNull(msg);

        NatsMessage expected = getTestMessage();
        q.push(expected);

        msg = q.pop(0L, TimeUnit.MILLISECONDS);
        assertEquals(expected, msg);
    }

    @Test
    public void testMultipleWriters() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 10; j++) {
                    q.push(getTestMessage());
                    sleep(10);
                }
            });
        }
        for (int i = 0; i < 10; i++) {
            threads[i].start();
        }
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }

        validateAccumulate(100, q.accumulate(-1, 101, 500L));
    }

    @Test
    public void testMultipleReaders() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        AtomicInteger allCount = new AtomicInteger(0);
        AtomicInteger[] counts = new AtomicInteger[10];
        Thread[] threads = new Thread[10];

        pushTestMessages(q, 1000);

        for (int i = 0; i < 10; i++) {
            counts[i] = new AtomicInteger(0);
            final int ii = i;
            threads[i] = new Thread(() -> {
                try {
                    do {
                        if (q.pop(500L, TimeUnit.MILLISECONDS) == null) {
                            return;
                        }
                        allCount.incrementAndGet();
                        counts[ii].incrementAndGet();
                        sleep(10);
                    } while (allCount.get() < 100);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
        }
        for (int i = 0; i < 10; i++) {
            threads[i].start();
        }
        for (int i = 0; i < 10; i++) {
            threads[i].join();
        }

        for (int i = 0; i < 10; i++) {
            assertTrue(counts[i].get() > 1);
        }
    }

    private void validateAccumulate(int expected, NatsMessage head) {
        while (expected > 0) {
            assertNotNull(head);
            head = head.next;
            expected--;
        }
        assertNull(head);
    }

    private void validateAccumulate(NatsMessage head, NatsMessage... expecteds) {
        NatsMessage msg = head;
        for (NatsMessage expected : expecteds) {
            assertNotNull(msg);
            assertEquals(expected, msg);
            msg = msg.next;
        }
        assertNull(msg);
    }

    @Test
    public void testAccumulate() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        validateAccumulate(0, q.accumulate(-1, 10, null));

        NatsMessage expected1 = getTestMessage();
        NatsMessage expected2 = getTestMessage();

        q.push(expected1);
        validateAccumulate(q.accumulate(-1, 2, null), expected1);

        q.push(expected1);
        q.push(expected2);
        validateAccumulate(q.accumulate(-1, 2, null), expected1, expected2);
    }

    @Test
    public void testAccumulateLimitCount() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        pushTestMessages(q, 7);
        long maxBytesToAccumulate = TEST_MESSAGE_BYTES * 10;
        validateAccumulate(3, q.accumulate(maxBytesToAccumulate, 3, null));
        validateAccumulate(3, q.accumulate(maxBytesToAccumulate, 3, null));
        validateAccumulate(1, q.accumulate(maxBytesToAccumulate, 3, null));
    }

    @Test
    public void testAccumulateLimitBytes() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        pushTestMessages(q, 7);
        long maxBytesToAccumulate = TEST_MESSAGE_BYTES * 4 - 1;
        validateAccumulate(3, q.accumulate(maxBytesToAccumulate, 100, null));
        validateAccumulate(3, q.accumulate(maxBytesToAccumulate, 100, null));
        validateAccumulate(1, q.accumulate(maxBytesToAccumulate, 100, null));
    }

    @Test
    public void testLength() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        NatsMessage msg1 = getTestMessage();
        NatsMessage msg2 = getTestMessage();
        NatsMessage msg3 = getTestMessage();

        q.push(msg1);
        assertEquals(1, q.length());
        q.push(msg2);
        assertEquals(2, q.length());
        q.push(msg3);
        assertEquals(3, q.length());
        q.accumulate(-1, 1, 500L);
        assertEquals(2, q.length());
        q.accumulate(-1, 100, null);
        assertEquals(0, q.length());
    }

    @Test
    public void testSizeInBytes() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        NatsMessage msg1 = getTestMessage();
        NatsMessage msg2 = getTestMessage();
        NatsMessage msg3 = getTestMessage();
        MarkerMessage notCounted = new MarkerMessage("not-counted");

        q.push(msg1);
        assertEquals(TEST_MESSAGE_BYTES, q.sizeInBytes());

        q.push(msg2);
        assertEquals(TEST_MESSAGE_BYTES * 2, q.sizeInBytes());

        q.push(msg3);
        assertEquals(TEST_MESSAGE_BYTES * 3, q.sizeInBytes());

        q.queueMarkerMessage(notCounted);
        assertEquals(TEST_MESSAGE_BYTES * 3, q.sizeInBytes());

        validateAccumulate(q.accumulate(TEST_MESSAGE_BYTES + 1, 100, null), msg1);
        assertEquals(TEST_MESSAGE_BYTES * 2, q.sizeInBytes());

        validateAccumulate(q.accumulate(TEST_MESSAGE_BYTES * 3, 100, null), msg2, msg3, notCounted);
        assertEquals(0, q.sizeInBytes());
    }

    @Test
    public void testFilteringAndCounting() {
        NatsMessage test = getTestMessage();
        ProtocolMessage proto = new ProtocolMessage("proto".getBytes(), true);
        MarkerMessage marker = new MarkerMessage("marker");

        long sizeM = test.getSizeInBytes();
        long sizeP = proto.getSizeInBytes();

        WriterMessageQueue q = newWriterMessageQueue();
        q.push(test);
        q.push(proto);
        q.queueMarkerMessage(marker);

        assertEquals(3, q.queueSize()); // test, proto, marker
        assertEquals(2, q.length());    // test, proto
        assertEquals(sizeM + sizeP, q.sizeInBytes());

        q.pause(); // this poisons the queue, adding another Marker Message
        q.filter();
        q.resume();

        assertEquals(3, q.queueSize()); // test, marker, poison
        assertEquals(1, q.length());     // test
        assertEquals(sizeM, q.sizeInBytes());
    }

    @Test
    public void testFilterFirstIn() throws InterruptedException {
        _testFiltered(1);
    }

    @Test
    public void testFilterLastIn() throws InterruptedException {
        _testFiltered(3);
    }

    @Test
    public void testFilterMiddle() throws InterruptedException {
        _testFiltered(2);
    }

    private static NatsMessage getTestFilteredMessage(String id, final boolean filterOnStop) {
        if (filterOnStop) {
            return new ProtocolMessage(("customFilter" + id).getBytes(), true);
        }
        return new NatsMessage("customFilter" + id, null, null);
    }

    private static void _testFiltered(int filtered) throws InterruptedException {
        NatsMessage msg1 = getTestFilteredMessage("1", filtered == 1);
        NatsMessage msg2 = getTestFilteredMessage("2", filtered == 2);
        NatsMessage msg3 = getTestFilteredMessage("3", filtered == 3);

        long size1 = msg1.getSizeInBytes();
        long size2 = msg2.getSizeInBytes();
        long size3 = msg3.getSizeInBytes();
        long sizeAll = size1 + size2 + size3;
        long sizeAfter = filtered == 1 ? size2 + size3 : size1 * 2;

        WriterMessageQueue q = newWriterMessageQueue();
        q.push(msg1);
        q.push(msg2);
        q.push(msg3);

        assertEquals(3, q.length());
        assertEquals(sizeAll, q.sizeInBytes());

        q.pause();
        q.filter();
        q.resume();

        assertEquals(2, q.length());
        assertEquals(sizeAfter, q.sizeInBytes());

        NatsMessage head = q.accumulate(-1, 3, 500L);
        if (filtered != 1) {
            assertEquals(msg1, head);
            head = head.next;
        }
        if (filtered != 2) {
            assertEquals(msg2, head);
            head = head.next;
        }
        if (filtered != 3) {
            assertEquals(msg3, head);
        }
    }

    @Test
    public void testPausedAccumulate() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        q.pause();
        NatsMessage msg = q.accumulate(1, 1, null);
        assertNull(msg);
    }

    @Test
    public void testExceptionWhenQueueIsFull() {
        WriterMessageQueue q = newWriterMessageQueue(2);
        NatsMessage msg1 = getTestMessage();
        NatsMessage msg2 = getTestMessage();
        NatsMessage msg3 = getTestMessage();

        assertTrue(q.push(msg1));
        assertEquals(1, q.length());
        assertEquals(TEST_MESSAGE_BYTES, q.sizeInBytes());

        assertTrue(q.push(msg2));
        assertEquals(2, q.length());
        assertEquals(TEST_MESSAGE_BYTES * 2, q.sizeInBytes());

        try {
            q.push(msg3);
            fail("Expected " + IllegalStateException.class.getSimpleName());
        } catch (IllegalStateException e) {
            assertEquals(OUTPUT_QUEUE_IS_FULL + "2", e.getMessage());
        }
    }

    @Test
    public void testDiscardMessageWhenQueueFull() throws InterruptedException {
        WriterMessageQueue q = new WriterMessageQueue(2, true, 500);
        NatsMessage msg1 = getTestMessage();
        NatsMessage msg2 = getTestMessage();
        NatsMessage msg3 = getTestMessage();

        assertTrue(q.push(msg1));
        assertEquals(1, q.length());
        assertEquals(TEST_MESSAGE_BYTES, q.sizeInBytes());

        assertTrue(q.push(msg2));
        assertEquals(2, q.length());
        assertEquals(TEST_MESSAGE_BYTES * 2, q.sizeInBytes());

        assertFalse(q.push(msg3));
        assertEquals(2, q.length());
        assertEquals(TEST_MESSAGE_BYTES * 2, q.sizeInBytes());

        validateAccumulate(q.accumulate(-1, 10, null), msg1, msg2);
        assertEquals(0, q.length());
        assertEquals(0, q.sizeInBytes());

        validateAccumulate(0, q.accumulate(-1, 10, null));
    }

    // A pre-set interrupt status makes tryLock throw InterruptedException on entry, so this
    // exercises push's interrupt handling deterministically without racing a real interrupt.
    @Test
    public void testInterruptedPushThrows() {
        WriterMessageQueue q = newWriterMessageQueue();
        Thread.currentThread().interrupt();
        try {
            // A failed enqueue is the same whether the cause is a full/busy queue or an interrupt,
            // and it does not matter whether the message is internal — both throw the same way.
            IllegalStateException e1 = assertThrows(IllegalStateException.class, () -> q.push(getTestMessage()));
            assertEquals(OUTPUT_QUEUE_INTERRUPTED + "0", e1.getMessage());
            assertInstanceOf(InterruptedException.class, e1.getCause());
            assertTrue(Thread.currentThread().isInterrupted()); // status re-asserted for the caller

            IllegalStateException e2 = assertThrows(IllegalStateException.class, () -> q.push(getTestMessage(), true));
            assertEquals(OUTPUT_QUEUE_INTERRUPTED + "0", e2.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally {
            Thread.interrupted(); // clear so we don't poison other tests
        }
    }

    @Test
    public void testClear() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        NatsMessage msg = getTestMessage();

        assertTrue(q.push(msg));
        assertEquals(1, q.length());
        assertEquals(TEST_MESSAGE_BYTES, q.sizeInBytes());

        q.clear();
        assertEquals(0, q.length());
        assertEquals(0, q.sizeInBytes());

        validateAccumulate(0, q.accumulate(-1, 10, null));
    }

    @Test
    public void testStateWriter() throws InterruptedException {
        WriterMessageQueue q = newWriterMessageQueue();
        q.push(getTestMessage());
        assertTrue(q.isRunning());
        assertFalse(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.pause(); // this poisons the queue, adding another Marker Message
        assertFalse(q.isRunning());
        assertTrue(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.resume();
        assertTrue(q.isRunning());
        assertFalse(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.drain();
        assertTrue(q.isRunning()); // still running while draining
        assertFalse(q.isPaused());
        assertTrue(q.isDraining());
        assertFalse(q.isDrained());

        q.accumulate(-1, 1, 1000L);
        assertTrue(q.isRunning()); // still running while draining
        assertFalse(q.isPaused());
        assertTrue(q.isDraining());
        assertTrue(q.isDrained());
    }

    @Test
    public void testStateConsumer() throws InterruptedException {
        ConsumerMessageQueue q = new ConsumerMessageQueue();
        q.push(getTestMessage());
        assertTrue(q.isRunning());
        assertFalse(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.pause(); // this poisons the queue, adding another Marker Message
        assertFalse(q.isRunning());
        assertTrue(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.resume();
        assertTrue(q.isRunning());
        assertFalse(q.isPaused());
        assertFalse(q.isDraining());
        assertFalse(q.isDrained());

        q.drain();
        assertTrue(q.isRunning()); // still running while draining
        assertFalse(q.isPaused());
        assertTrue(q.isDraining());
        assertFalse(q.isDrained());

        q.pop(null, TimeUnit.MILLISECONDS);
        assertTrue(q.isRunning()); // still running while draining
        assertFalse(q.isPaused());
        assertTrue(q.isDraining());
        assertTrue(q.isDrained());
    }
}
