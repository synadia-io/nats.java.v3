package io.synadia.client.impl;

import io.synadia.client.ConnectionStatus;
import io.synadia.client.Subscription;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.impl.NatsMessageSink.DeliverabilityState.*;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

// invalidate() nulls NatsSubscription.incoming from another thread, so any method that reads the
// queue reference twice can see it live on the first read and null on the second. The harness below
// turns that timing problem into a deterministic one: the queue is handed out on the first lookup
// and never again. A method that reads once passes; a method that reads twice either throws or
// reports a value from the second, empty read.
public class NatsMessageSinkPendingTests extends TestBase {

    @Test
    public void testPendingMessageCountUsesSingleQueueLookup() {
        assertEquals(1, new SingleLookupSink(1).getPendingMessageCount());
    }

    @Test
    public void testPendingByteCountUsesSingleQueueLookup() {
        assertTrue(new SingleLookupSink(1).getPendingByteCount() > 0);
    }

    @Test
    public void testMarkUnsubedForDrainUsesSingleQueueLookup() {
        assertDoesNotThrow(new SingleLookupSink(1)::markUnsubedForDrain);
    }

    // -1 is the contract for "the queue is gone", as distinct from 0 meaning "the queue is there
    // and empty". isDrained() relies on being able to tell those apart.
    @Test
    public void testNoQueueReportsMinusOne() {
        NoQueueSink sink = new NoQueueSink();
        assertEquals(-1, sink.getPendingMessageCount());
        assertEquals(-1, sink.getPendingByteCount());
    }

    @Test
    public void testDeliverabilityStateAvailable() {
        assertEquals(AVAILABLE, new NoQueueSink().getDeliverabilityState(queueWith(1)));
    }

    @Test
    public void testDeliverabilityStateNotAvailableWithNoQueue() {
        assertEquals(NOT_AVAILABLE, new NoQueueSink().getDeliverabilityState(null));
    }

    @Test
    public void testDeliverabilityStateFullOnMessageLimit() {
        NoQueueSink sink = new NoQueueSink();
        sink.setPendingLimits(1, 0);
        assertEquals(FULL, sink.getDeliverabilityState(queueWith(1)));
    }

    @Test
    public void testDeliverabilityStateFullOnByteLimit() {
        NoQueueSink sink = new NoQueueSink();
        sink.setPendingLimits(0, 1);
        assertEquals(FULL, sink.getDeliverabilityState(queueWith(1)));
    }

    // Unlimited is normalised to Long.MAX_VALUE, so there are no > 0 guards to get wrong.
    @Test
    public void testDeliverabilityStateAvailableWhenUnlimited() {
        NoQueueSink sink = new NoQueueSink();
        sink.setPendingLimits(0, 0);
        assertEquals(AVAILABLE, sink.getDeliverabilityState(queueWith(1)));
    }

    // The NOT_AVAILABLE branch of deliverMessage is new behaviour: the message is dropped and
    // counted, but the sink is NOT marked slow, because a subscription that has gone away is not a
    // slow consumer. Invalidating the subscription directly leaves its sid registered on the
    // connection, so the reader still finds it - the exact state the reported race occurred in.
    @Test
    public void testDeliveryToUnavailableQueueIsDroppedNotMarkedSlow() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.flush(5000);

            sub.invalidate();
            long droppedBefore = sub.getDroppedCount();

            nc.publish(subject, null);
            nc.flush(5000);

            assertEquals(ConnectionStatus.CONNECTED, nc.getStatus(), "delivery took the connection down");
            assertEquals(droppedBefore + 1, sub.getDroppedCount(), "message was not counted as dropped");
            assertFalse(sub.isMarkedSlow(), "a gone subscription must not be reported as a slow consumer");
        });
    }

    // _nextMessage checks the queue for null and then dereferences it twice more - once for pop()
    // and once for isRunning(). Nothing here can force an invalidate into those exact gaps, so this
    // pins the contract instead: unsubscribing while another thread is blocked in nextMessage must
    // report IllegalStateException, never a NullPointerException. There was no coverage of this.
    @Test
    public void testUnsubscribeWhileBlockedInNextMessageReportsInactive() throws Exception {
        runInSharedOwnNc(nc -> {
            String subject = random();
            Subscription sub = nc.subscribe(subject);
            nc.flush(5000);

            AtomicReference<Throwable> thrown = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);

            Thread waiter = new Thread(() -> {
                started.countDown();
                try {
                    sub.nextMessage(10000L);
                }
                catch (Throwable t) {
                    thrown.set(t);
                }
            });
            waiter.start();

            assertTrue(started.await(5, TimeUnit.SECONDS));
            sleep(250); // let it get into the blocking pop

            sub.unsubscribe();
            waiter.join(5000);
            assertFalse(waiter.isAlive(), "nextMessage never returned after unsubscribe");

            Throwable t = thrown.get();
            assertNotNull(t, "nextMessage returned quietly instead of reporting the subscription went away");
            assertFalse(t instanceof NullPointerException, "unsubscribe raced nextMessage and produced an NPE: " + t);
            assertInstanceOf(IllegalStateException.class, t, "expected IllegalStateException, got " + t);
            // Either message is correct - which one depends on whether the waiter reached the
            // blocking pop before the unsubscribe landed, and that is not something the test can pin
            // down. What matters is that it is this pair and never an NPE.
            assertTrue("This subscription became inactive.".equals(t.getMessage())
                    || "This subscription is inactive.".equals(t.getMessage()),
                "unexpected message: " + t.getMessage());
        });
    }

    private static ConsumerMessageQueue queueWith(int messages) {
        ConsumerMessageQueue queue = new ConsumerMessageQueue();
        for (int x = 0; x < messages; x++) {
            queue.push(NatsMessage.builder().subject("subject").build());
        }
        return queue;
    }

    // Hands the queue out on the first lookup and null on every lookup after it.
    private static class SingleLookupSink extends NatsMessageSink {
        private final AtomicInteger lookups = new AtomicInteger();
        private final ConsumerMessageQueue queue;

        SingleLookupSink(int messages) {
            super(null);
            queue = queueWith(messages);
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        ConsumerMessageQueue getMessageQueue() {
            return lookups.getAndIncrement() == 0 ? queue : null;
        }

        @Override
        void sendUnsubForDrain() {
        }

        @Override
        void cleanUpAfterDrain() {
        }
    }

    // Never has a queue - a subscription that has been invalidated.
    private static class NoQueueSink extends NatsMessageSink {
        NoQueueSink() {
            super(null);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        ConsumerMessageQueue getMessageQueue() {
            return null;
        }

        @Override
        void sendUnsubForDrain() {
        }

        @Override
        void cleanUpAfterDrain() {
        }
    }
}
