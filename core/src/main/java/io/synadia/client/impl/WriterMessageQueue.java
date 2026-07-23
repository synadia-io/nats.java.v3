package io.synadia.client.impl;

import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.client.OptionsConstants.MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT;
import static io.synadia.client.impl.MarkerMessage.POISON_PILL;
import static io.synadia.client.utils.NatsConstants.*;

class WriterMessageQueue extends MessageQueueBase {
    protected static final long MIN_PUSH_TIMEOUT_NANOS = MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT * NANOS_PER_MILLI;

    protected final int maxMessagesInOutgoingQueue;
    protected final boolean discardWhenFull;
    protected final Lock editLock;
    protected final long pushTimeoutNanos;

    WriterMessageQueue(long pushTimeoutMillis) {
        this(-1, false, pushTimeoutMillis);
    }

    WriterMessageQueue(int maxMessagesInOutgoingQueue, boolean discardWhenFull, long pushTimeoutMillis) {
        super(maxMessagesInOutgoingQueue);
        this.maxMessagesInOutgoingQueue = queueCapacity;
        this.discardWhenFull = discardWhenFull;
        this.pushTimeoutNanos = Math.max(MIN_PUSH_TIMEOUT_NANOS, pushTimeoutMillis * NANOS_PER_MILLI);
        this.editLock = new ReentrantLock();
    }

    boolean push(NatsMessage msg) {
        return push(msg, false);
    }

    boolean push(NatsMessage msg, boolean internal) {
        try {
            long startNanos = NatsSystemClock.nanoTime();
            if (editLock.tryLock(pushTimeoutNanos, TimeUnit.NANOSECONDS)) {
                try {
                    // offer with no timeout returns true if the queue was not full
                    if (!internal && discardWhenFull) {
                        if (queue.offer(msg)) {
                            sizeInBytes.getAndAdd(msg.getSizeInBytes());
                            length.incrementAndGet();
                            return true;
                        }
                        return false;
                    }

                    // offer with timeout
                    long timeoutNanosLeft = Math.max(
                        MIN_PUSH_TIMEOUT_NANOS,
                        pushTimeoutNanos - (NatsSystemClock.nanoTime() - startNanos)
                    );
                    if (queue.offer(msg, timeoutNanosLeft, TimeUnit.NANOSECONDS)) {
                        sizeInBytes.getAndAdd(msg.getSizeInBytes());
                        length.incrementAndGet();
                        return true;
                    }
                    throw new IllegalStateException(OUTPUT_QUEUE_IS_FULL + queue.size());
                }
                finally {
                    editLock.unlock();
                }
            }
            else {
                throw new IllegalStateException(OUTPUT_QUEUE_BUSY + queue.size());
            }
        }
        catch (InterruptedException e) {
            // A failed enqueue is a failed enqueue regardless of cause: an interrupt is handled the
            // same as the OUTPUT_QUEUE_IS_FULL / OUTPUT_QUEUE_BUSY throws above, with no internal-vs-user
            // special case. Re-assert the interrupt status (we can't propagate the checked
            // InterruptedException here) and throw; a caller that cares whether it was specifically an
            // interrupt checks Thread.interrupted().
            Thread.currentThread().interrupt();
            throw new IllegalStateException(OUTPUT_QUEUE_INTERRUPTED + queue.size(), e);
        }
    }

    /**
     * Marking the queue, like POISON, is a message we don't want to count.
     * Intended to only be used with an unbounded queue. Use at your own risk.
     * @param msg the mark
     */
    @SuppressWarnings("SameParameterValue")
    void queueMarkerMessage(MarkerMessage msg) {
        queue.offer(msg);
    }

    // Waits up to the timeout to try to accumulate multiple messages
    // Use the NatsMessage.next field to read the entire set accumulated.
    // maxBytesToAccumulate and maxMessagesToAccumulate are both checked
    // and if either is exceeded the method returns.
    //
    // timeoutMillis follows the shared _poll convention (it is the head-message wait):
    //   null          -> poll once for the head message and return immediately
    //   <= 0 (e.g. 0) -> wait forever for the head message (or until the queue is stopped/drained)
    //   > 0           -> wait up to that many millis for the head message
    //
    // Only works in writer mode, because we want to maintain order.
    // accumulate reads off the concurrent queue one at a time, so if multiple
    // readers are present, you could get out of order message delivery.
    NatsMessage accumulate(long maxBytesToAccumulate, long maxMessagesToAccumulate, @Nullable Long timeoutMillis)
        throws InterruptedException {

        if (!isRunning()) {
            return null;
        }

        // _poll returns null if no messages or was a POISON_PILL
        // MarkerMessage is a termination, is not counted, but is returned
        NatsMessage headMessage = _poll(timeoutMillis, TimeUnit.MILLISECONDS);
        if (headMessage == null || headMessage instanceof MarkerMessage) {
            return headMessage;
        }

        if (maxBytesToAccumulate < 1) {
            maxBytesToAccumulate = Long.MAX_VALUE; // this just makes it easier to loop
        }

        // these will be used to call count() after the loop ends
        long accumulatedMessages = 1;
        long accumulatedSize = headMessage.getSizeInBytes();

        // We need a cursor for the chain of messages
        // and we return the head message
        NatsMessage cursor = headMessage;

        // If the message wants to flushImmediatelyAfterPublish, don't accumulate more
        // If the accumulatedMessages is >= maxMessagesToAccumulate, don't accumulate more
        while (!cursor.flushImmediatelyAfterPublish && accumulatedMessages < maxMessagesToAccumulate) {
            // We are allowed to try more messages. Peek first to see what we are dealing with
            NatsMessage peeked = queue.peek();

            if (peeked == null) {
                break; // no messages in the queue so we are done.
            }

            if (peeked instanceof MarkerMessage) {
                // - Get the message out of the queue b/c we only peeked
                // - POISON_PILL does not get added to the cursor.next chain
                //   but all other MarkerMessages do.
                // - We are done.
                queue.poll();
                if (peeked != POISON_PILL) {
                    cursor.next = peeked;
                }
                break;
            }

            // How big is the message we just peeked at? Will it put us over maxBytesToAccumulate?
            long size = peeked.getSizeInBytes();
            if (accumulatedSize + size > maxBytesToAccumulate) {
                break; // Too many bytes, so we are done.
            }

            // We can add the peeked message to the chain...
            // - Get the message out of the queue b/c we only peeked
            // - Track the message and the bytes for later counting and the while loop
            // - Add the message to the chain
            queue.poll();
            accumulatedMessages++;
            accumulatedSize += size;
            cursor.next = peeked;

            // Move the cursor. It's okay if the while terminates at it's
            // next check, we don't need the cursor outside the loop
            cursor = peeked;
        }

        length.addAndGet(-accumulatedMessages);
        sizeInBytes.addAndGet(-accumulatedSize);
        return headMessage;
    }

    void filter() {
        if (this.isRunning()) {
            throw new IllegalStateException("Filter is only supported when the queue is paused");
        }
        editLock.lock();
        try {
            ArrayList<NatsMessage> temp = new ArrayList<>();
            queue.drainTo(temp);
            for (NatsMessage cursor : temp) {
                if (cursor.isFilterOnStop()) {
                    sizeInBytes.addAndGet(-cursor.getSizeInBytes());
                    length.decrementAndGet();
                }
                else {
                    queue.offer(cursor);
                }
            }
        }
        finally {
            editLock.unlock();
        }
    }

    void clear() {
        editLock.lock();
        try {
            this.queue.clear();
            length.set(0);
            sizeInBytes.set(0);
        } finally {
            editLock.unlock();
        }
    }
}
