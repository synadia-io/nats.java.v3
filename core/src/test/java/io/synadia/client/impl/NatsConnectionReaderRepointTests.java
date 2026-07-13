package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Options;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.ReadListener;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Covers NatsConnectionReader.setConnection repointing the reader's connection, and the ReadListener
// being re-derived from the new connection's Options - rebuilding only when the configured listener
// actually changed. Uses unconnected connections: the reader and its callback executor exist after
// construction, so no server is needed.
public class NatsConnectionReaderRepointTests {

    static class RecordingReadListener implements ReadListener {
        final AtomicInteger protocolCount = new AtomicInteger();
        final AtomicInteger messageCount = new AtomicInteger();
        volatile CountDownLatch latch;

        @Override
        public void protocol(String op, String text) {
            protocolCount.incrementAndGet();
            CountDownLatch l = latch;
            if (l != null) { l.countDown(); }
        }

        @Override
        public void message(String op, Message message) {
            messageCount.incrementAndGet();
            CountDownLatch l = latch;
            if (l != null) { l.countDown(); }
        }
    }

    private static NatsConnection unconnected(ReadListener rl) {
        OptionsBuilder b = Options.builder();
        if (rl != null) {
            b.readListener(rl);
        }
        return new NatsConnection(b.build());
    }

    private static void closeQuietly(NatsConnection... conns) {
        for (NatsConnection c : conns) {
            c.close();
        }
    }

    @Test
    public void repoint_keepsListenerWhenUnchanged_rebuildsWhenChanged() throws Exception {
        RecordingReadListener rlA = new RecordingReadListener();
        RecordingReadListener rlB = new RecordingReadListener();

        NatsConnection connA = unconnected(rlA);       // has listener rlA
        NatsConnection connASameRl = unconnected(rlA); // different connection, SAME listener instance
        NatsConnection connB = unconnected(rlB);       // different listener
        NatsConnection connNull1 = unconnected(null);  // no listener
        NatsConnection connNull2 = unconnected(null);  // no listener
        try {
            NatsConnectionReader reader = connA.reader;

            ReadListener wrapperForA = reader.readListener();
            assertNotNull(wrapperForA);

            // Same underlying listener instance -> the current wrapper is still correct, keep it.
            // (This is the "readListener != null && newSuppliedUserRl == currentUserRl" keep path -
            //  proof it is reachable / not "never true".)
            reader.setConnection(connASameRl);
            assertSame(wrapperForA, reader.readListener());

            // Different listener -> rebuild.
            reader.setConnection(connB);
            ReadListener wrapperForB = reader.readListener();
            assertNotNull(wrapperForB);
            assertNotSame(wrapperForA, wrapperForB);

            // No listener -> rebuild to the no-op.
            reader.setConnection(connNull1);
            ReadListener noop = reader.readListener();
            assertNotNull(noop);
            assertNotSame(wrapperForB, noop);

            // Still no listener -> keep the no-op (null -> null keep path).
            reader.setConnection(connNull2);
            assertSame(noop, reader.readListener());
        }
        finally {
            closeQuietly(connA, connASameRl, connB, connNull1, connNull2);
        }
    }

    @Test
    public void repoint_dispatchesToTheNewConnectionsListener() throws Exception {
        RecordingReadListener rlA = new RecordingReadListener();
        RecordingReadListener rlB = new RecordingReadListener();

        NatsConnection connA = unconnected(rlA);
        NatsConnection connB = unconnected(rlB);
        try {
            NatsConnectionReader reader = connA.reader;

            // Before repoint: the reader's listener dispatches to connA's listener (rlA).
            rlA.latch = new CountDownLatch(1);
            reader.readListener().protocol("PONG", null);
            assertTrue(rlA.latch.await(2, TimeUnit.SECONDS), "rlA should have been called");
            assertEquals(1, rlA.protocolCount.get());
            assertEquals(0, rlB.protocolCount.get());

            // After repoint to connB: dispatch follows to connB's listener (rlB), not rlA.
            reader.setConnection(connB);
            rlB.latch = new CountDownLatch(1);
            reader.readListener().message("MSG", null);
            assertTrue(rlB.latch.await(2, TimeUnit.SECONDS), "rlB should have been called");
            assertEquals(1, rlB.messageCount.get());
            assertEquals(0, rlA.messageCount.get());
        }
        finally {
            closeQuietly(connA, connB);
        }
    }
}
