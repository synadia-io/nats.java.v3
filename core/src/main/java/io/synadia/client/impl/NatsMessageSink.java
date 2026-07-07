package io.synadia.client.impl;

import io.synadia.client.OptionsConstants;
import io.synadia.client.global.NatsSystemClock;

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
     * @return the pending message limit set by {@link #setPendingLimits(long, long)
     *         setPendingLimits}.
     */
    public long getPendingMessageLimit() {
        return this.maxMessages == Long.MAX_VALUE ? -1 : this.maxMessages;
    }

    /**
     * @return the pending byte limit set by {@link #setPendingLimits(long, long)
     *         setPendingLimits}.
     */
    public long getPendingByteLimit() {
        return this.maxBytes == Long.MAX_VALUE ? -1 : this.maxBytes;
    }

    /**
     * @return the number of messages waiting to be delivered/popped,
     *         {@link #setPendingLimits(long, long) setPendingLimits}.
     */
    public long getPendingMessageCount() {
        return this.getMessageQueue() != null ? this.getMessageQueue().length() : 0;
    }

    /**
     * @return the cumulative size of the messages waiting to be delivered/popped,
     *         {@link #setPendingLimits(long, long) setPendingLimits}.
     */
    public long getPendingByteCount() {
        return this.getMessageQueue() != null ? this.getMessageQueue().sizeInBytes() : 0;
    }

    /**
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

    boolean hasReachedPendingLimits() {
        return getPendingMessageCount() >= maxMessages || getPendingByteCount() >= maxBytes;
    }

    void markDraining(CompletableFuture<Boolean> future) {
        this.drainingFuture.set(future);
    }

    void markUnsubedForDrain() {
        if (this.getMessageQueue() != null) {
            this.getMessageQueue().drain();
        }
    }

    CompletableFuture<Boolean> getDrainingFuture() {
        return this.drainingFuture.get();
    }

    boolean isDraining() {
        return this.drainingFuture.get() != null;
    }

    boolean isDrained() {
        return isDraining() && this.getPendingMessageCount() == 0;
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
