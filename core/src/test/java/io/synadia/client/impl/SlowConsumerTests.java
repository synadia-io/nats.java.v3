package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SlowConsumerTests extends TestBase {

    // From PR #1614 (zileongggg), kept so nothing from that PR is lost by porting #1615 alone. The
    // stronger form of this coverage - seeded queue, split assertions, the -1 contract,
    // DeliverabilityState - lives in NatsMessageSinkPendingTests.
    //
    // 0 and not -1 is correct here: the first lookup hands back a live but empty queue, so these
    // read a real 0. -1 is only for a sink with no queue at all.
    @Test
    public void testPendingCountsUseSingleQueueLookup() {
        assertEquals(0, new QueueInvalidatingSink().getPendingMessageCount());
        assertEquals(0, new QueueInvalidatingSink().getPendingByteCount());
    }

    @Test
    public void testDefaultPendingLimits() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            Dispatcher d = nc.createDispatcher((Message m) -> {});

            assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, sub.getPendingMessageLimit());
            assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, sub.getPendingByteLimit());

            assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, d.getPendingMessageLimit());
            assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, d.getPendingByteLimit());
            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testSlowSubscriberByMessages() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            int expectedPending = subject.length() + 12;
            NatsSubscription sub = nc.subscribe(subject);
            sub.setPendingLimits(1, -1);

            assertEquals(1, sub.getPendingMessageLimit());
            assertEquals(-1, sub.getPendingByteLimit());
            assertEquals(0, sub.getDroppedCount());

            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(3, sub.getDroppedCount());
            assertEquals(1, sub.getPendingMessageCount());
            assertEquals(expectedPending, sub.getPendingByteCount()); // "msg 1 subject 0" + crlf + crlf

            sub.clearDroppedCount();
            
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, sub.getDroppedCount());
            assertEquals(1, sub.getPendingMessageCount());
        });
    }

    @Test
    public void testSlowSubscriberByBytes() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            int maxBytes = subject.length() + 3;
            int expectedPending = maxBytes + 9;
            NatsSubscription sub = nc.subscribe(subject);
            sub.setPendingLimits(-1, maxBytes); // will take the first, not the second

            assertEquals(maxBytes, sub.getPendingByteLimit());
            assertEquals(-1, sub.getPendingMessageLimit());
            assertEquals(0, sub.getDroppedCount());

            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, sub.getDroppedCount());
            assertEquals(1, sub.getPendingMessageCount());
            assertEquals(expectedPending, sub.getPendingByteCount()); // "msg 1 subject 0" + crlf + crlf

            sub.clearDroppedCount();
            
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, sub.getDroppedCount());
            assertEquals(1, sub.getPendingMessageCount());
        });
    }

    @Test
    public void testSlowSDispatcherByMessages() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            int expectedPending = subject.length() + 12;

            final CompletableFuture<Void> ok = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                ok.complete(null);
                Thread.sleep(5 * 60 * 1000); // will wait until interrupted
            });

            d.setPendingLimits(1, -1);
            d.subscribe(subject);

            assertEquals(1, d.getPendingMessageLimit());
            assertEquals(-1, d.getPendingByteLimit());
            assertEquals(0, d.getDroppedCount());

            nc.publish(subject, null);

            ok.get(1000,TimeUnit.MILLISECONDS); // make sure we got the first one
            
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(1000);

            assertEquals(1, d.getDroppedCount());
            assertEquals(1, d.getPendingMessageCount());
            assertEquals(expectedPending, d.getPendingByteCount()); // "msg 1 subject 0" + crlf + crlf

            d.clearDroppedCount();
            
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, d.getDroppedCount());
            assertEquals(1, d.getPendingMessageCount());
        });
    }

    @Test
    public void testSlowSDispatcherByBytes() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            int maxBytes = subject.length() + 3;
            int expectedPending = maxBytes + 9;
            final CompletableFuture<Void> ok = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                ok.complete(null);
                Thread.sleep(5 * 60 * 1000); // will wait until interrupted
            });

            d.setPendingLimits(-1, maxBytes);
            d.subscribe(subject);

            assertEquals(-1, d.getPendingMessageLimit());
            assertEquals(maxBytes, d.getPendingByteLimit());
            assertEquals(0, d.getDroppedCount());

            nc.publish(subject, null);

            ok.get(1000,TimeUnit.MILLISECONDS); // make sure we got the first one
            
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, d.getDroppedCount());
            assertEquals(1, d.getPendingMessageCount());
            assertEquals(expectedPending, d.getPendingByteCount()); // "msg 1 subject 0" + crlf + crlf

            d.clearDroppedCount();
            
            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(1, d.getDroppedCount());
            assertEquals(1, d.getPendingMessageCount());
        });
    }

    @Test
    public void testDispatcherDeliveredCount() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            int count = 5;
            CountDownLatch latch = new CountDownLatch(count);
            Dispatcher d = nc.createDispatcher(msg -> latch.countDown());
            d.subscribe(subject);

            for (int x = 0; x < count; x++) {
                nc.publish(subject, null);
            }

            assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));
            // deliveredCount is bumped before the handler runs, so it has settled once the latch releases
            assertEquals(count, d.getDeliveredCount());
        });
    }

    static class SlowConsumerListener implements ErrorListener {
        public CompletableFuture<Boolean> future;
        public final List<Subscription> consumers = new ArrayList<>();

        public void waitForSlow() {
            future = new CompletableFuture<>();
        }

        @Override
        public void slowConsumerDetected(NatsConnection conn, Subscription slowConsumer) {
            consumers.add(slowConsumer);
            if (future != null) {
                future.complete(true);
            }
        }
    }

    @Test
    public void testSlowSubscriberNotification() throws Exception {
        SlowConsumerListener listener = new SlowConsumerListener();
        runInSharedOwnNc(listener, nc -> {
            String subject = random();
            NatsSubscription sub = (NatsSubscription)nc.subscribe(subject);
            sub.setPendingLimits(1, -1);

            listener.waitForSlow();

            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(5000);

            // Notification is in another thread, wait for it, or fail
            listener.future.get(3000, TimeUnit.MILLISECONDS);

            assertEquals(1, listener.consumers.size()); // should only appear once
            assertEquals(sub, listener.consumers.get(0));
            listener.consumers.clear();
            
            nc.publish(subject, null);
            nc.flush(1000);

            assertEquals(0, listener.consumers.size()); // no renotify

            listener.waitForSlow();
            // Clear the queue, we should become a non-slow consumer
            sub.nextMessage(1000L); // only 1 to get

            // Notification again on 2nd message
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.flush(1000);

            listener.future.get(3000, TimeUnit.MILLISECONDS);

            assertEquals(1, listener.consumers.size()); // should only appear once
            assertEquals(sub, listener.consumers.get(0));
        });
    }

    private static class QueueInvalidatingSink extends NatsMessageSink {
        private final AtomicInteger queueLookups = new AtomicInteger();

        QueueInvalidatingSink() {
            super(null);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        ConsumerMessageQueue getMessageQueue() {
            return queueLookups.getAndIncrement() == 0 ? new ConsumerMessageQueue() : null;
        }

        @Override
        void sendUnsubForDrain() {
        }

        @Override
        void cleanUpAfterDrain() {
        }
    }
}
