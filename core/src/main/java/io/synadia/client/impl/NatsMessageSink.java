package io.synadia.client.impl;

import io.synadia.client.OptionsConstants;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;

abstract class NatsMessageSink {

    protected NatsConnection connection;
    private volatile long maxMessages;
    private volatile long maxBytes;
    private final AtomicLong droppedMessages;
    private final AtomicLong messagesDelivered;
    private final AtomicBoolean slow;
    private final AtomicReference<CompletableFuture<Boolean>> drainingFuture;

    NatsMessageSink(NatsConnection conn) {
        this.connection = conn;
        this.maxMessages = OptionsConstants.DEFAULT_MAX_MESSAGES;
        this.maxBytes = OptionsConstants.DEFAULT_MAX_BYTES;
        this.droppedMessages = new AtomicLong();
        this.messagesDelivered = new AtomicLong(0);
        this.slow = new AtomicBoolean(false);
        this.drainingFuture = new AtomicReference<>();
    }

    /**
     * Set limits on the maximum number of messages, or maximum size of messages
     * this sink will hold before it starts to drop new messages waiting.
     * <p>
     * Messages are dropped as they encounter a full queue, which is to say, new
     * messages are dropped rather than old messages. If a queue is 10 deep and
     * fills up, the 11th message is dropped.
     * <p>
     * Any value less than or equal to zero means unlimited, which the getters report as -1.
     * @param maxMessages the maximum message count to hold, defaults to
     *                    {@value OptionsConstants#DEFAULT_MAX_MESSAGES}.
     * @param maxBytes    the maximum bytes to hold, defaults to
     *                    {@value OptionsConstants#DEFAULT_MAX_BYTES}.
     */
    public void setPendingLimits(long maxMessages, long maxBytes) {
        this.maxMessages = maxMessages <= 0 ? Long.MAX_VALUE : maxMessages;
        this.maxBytes = maxBytes <= 0 ? Long.MAX_VALUE : maxBytes;
    }

    /**
     * The maximum number of messages this sink will hold before dropping new ones.
     * @return the pending message limit set by {@link #setPendingLimits(long, long)
     *         setPendingLimits}, or -1 for unlimited.
     */
    public long getPendingMessageLimit() {
        return this.maxMessages == Long.MAX_VALUE ? -1 : this.maxMessages;
    }

    /**
     * The maximum total size of the messages this sink will hold before dropping new ones.
     * @return the pending byte limit set by {@link #setPendingLimits(long, long)
     *         setPendingLimits}, or -1 for unlimited.
     */
    public long getPendingByteLimit() {
        return this.maxBytes == Long.MAX_VALUE ? -1 : this.maxBytes;
    }

    /**
     * How full the sink currently is, in messages, relative to the pending message limit.
     * @return the number of messages waiting to be delivered/popped, or -1 if the queue is not available.
     */
    public long getPendingMessageCount() {
        ConsumerMessageQueue copy = getMessageQueue();
        return copy == null ? -1 : copy.length();
    }

    /**
     * How full the sink currently is, in bytes, relative to the pending byte limit.
     * @return the cumulative size of the messages waiting to be delivered/popped, or -1 if the queue is not available.
     */
    public long getPendingByteCount() {
        ConsumerMessageQueue copy = getMessageQueue();
        return copy == null ? -1 : copy.sizeInBytes();
    }

    /**
     * A lifetime counter that is never reset, unlike the dropped count.
     * @return the total number of messages delivered to this sink, for all
     *         time.
     */
    public long getDeliveredCount() {
        return this.messagesDelivered.get();
    }

    void incrementDeliveredCount() {
        this.messagesDelivered.incrementAndGet();
    }

    void incrementDroppedCount() {
        this.droppedMessages.incrementAndGet();
    }

    /**
     * How many messages arrived while the sink was already at its pending limits.
     * @return the number of messages dropped from this sink, since the last
     *         call to {@link #clearDroppedCount}.
     */
    public long getDroppedCount() {
        return this.droppedMessages.get();
    }

    /**
     * Reset the drop count to 0.
     */
    public void clearDroppedCount() {
        this.droppedMessages.set(0);
    }

    void markSlow() {
        this.slow.set(true);
    }

    void markNotSlow() {
        this.slow.set(false);
    }

    boolean isMarkedSlow() {
        return this.slow.get();
    }

    enum DeliverabilityState {
        AVAILABLE,
        FULL,
        NOT_AVAILABLE
    }

    // The queue is supplied by the caller rather than looked up here, so the caller's single read
    // is the only read on the delivery path - the queue this judges is provably the same object the
    // caller then pushes to. Looking it up again here would let an invalidate() on another thread
    // come between the verdict and the push.
    // No > 0 guards on the limits: setPendingLimits normalises unlimited to Long.MAX_VALUE, so
    // length() >= Long.MAX_VALUE is simply false.
    DeliverabilityState getDeliverabilityState(@Nullable ConsumerMessageQueue queue) {
        if (queue == null) {
            return DeliverabilityState.NOT_AVAILABLE;
        }
        if (queue.length() >= maxMessages || queue.sizeInBytes() >= maxBytes) {
            return DeliverabilityState.FULL;
        }
        return DeliverabilityState.AVAILABLE;
    }

    void markDraining(CompletableFuture<Boolean> future) {
        this.drainingFuture.set(future);
    }

    void markUnsubedForDrain() {
        ConsumerMessageQueue copy = getMessageQueue();
        if (copy != null) {
            copy.drain();
        }
    }

    CompletableFuture<Boolean> getDrainingFuture() {
        return this.drainingFuture.get();
    }

    boolean isDraining() {
        return this.drainingFuture.get() != null;
    }

    boolean isDrained() {
        // <= 0 not == 0: the count is -1 once the queue is gone, and a sink with no queue has
        // nothing left to drain. cleanUpAfterDrain() invalidates before drain() completes its
        // future, so this is read in exactly that state on every subscription drain.
        return isDraining() && getPendingMessageCount() <= 0;
    }

    /**
    * Drain tells the sink to process in flight, or cached messages, but stop receiving new ones. The library will
    * flush the unsubscribe call(s) insuring that any publish calls made by this client are included. When all messages
    * are processed the sink effectively becomes unsubscribed.
    * 
    * @param timeoutMillis The time in milliseconds to wait for the drain to succeed, pass 0 or less to wait
    *                    forever. Drain involves moving messages to and from the server
    *                    so a very short timeout is not recommended.
    * @return A future that can be used to check if the drain has completed
    * @throws InterruptedException if the thread is interrupted
    */
   public CompletableFuture<Boolean> drain(long timeoutMillis) throws InterruptedException {
       if (!this.isActive() || this.connection==null) {
           throw new IllegalStateException("Message sink is closed");
       }

       if (isDraining()) {
           return this.getDrainingFuture();
       }

       final CompletableFuture<Boolean> tracker = new CompletableFuture<>();
       this.markDraining(tracker);
       this.sendUnsubForDrain();

       try {
            this.connection.flush(timeoutMillis); // Flush and wait up to the timeout
       } catch (TimeoutException e) {
           this.connection.processException(e);
       }

       this.markUnsubedForDrain();

        // Wait for the timeout or sink is drained
        // Skipped if conn is draining
        connection.getExecutor().submit(() -> {
            try {
                long timeoutNanos = timeoutMillis <= 0 ? Long.MAX_VALUE : timeoutMillis * NANOS_PER_MILLI;
                long startTime = System.nanoTime();
                while (NatsSystemClock.nanoTime() - startTime < timeoutNanos && !Thread.interrupted()) {
                    if (this.isDrained()) {
                        break;
                    }
                    //noinspection BusyWait
                    Thread.sleep(1); // Sleep 1 milli
                }

                this.cleanUpAfterDrain();
            } catch (InterruptedException e) {
                this.connection.processException(e);
                Thread.currentThread().interrupt();
            } finally {
                tracker.complete(this.isDrained());
            }
       });

       return getDrainingFuture();
   }

    /**
     * Whether the sink can still take messages. Once false it never becomes true again.
     * @return whether this sink is still processing messages. For a
     *         subscription the answer is false after unsubscribe. For a dispatcher,
     *         false after stop.
     */
    public abstract boolean isActive();

    abstract ConsumerMessageQueue getMessageQueue();

    /**
     * Called during drain to tell the sink to send appropriate unsub requests
     * to the connection.
     * A subscription will unsub itself, while a dispatcher will unsub all of its
     * subscriptions.
     */
    abstract void sendUnsubForDrain();

    /**
     * Abstract method, called by the connection when the drain is complete.
     */
    abstract void cleanUpAfterDrain();
}
