package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.Subscription;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * The standard subscription implementation. A subscription either owns a queue that the application
 * pulls messages from, or belongs to a dispatcher that pushes messages to a handler, never both.
 */
public class NatsSubscription extends NatsMessageSink implements Subscription {

    private String subject;
    private final String queueName;
    private String sid;

    // Null means this subscription is synchronous and owns its own queue. Deliberately not exposed:
    // a dispatcher the subscribe made is owned by the subscription, not by the caller, and handing
    // it out invites subscribing on something that dies with this subscription.
    @Nullable NatsDispatcher dispatcher;
    private ConsumerMessageQueue incoming;

    private final AtomicLong unSubMessageLimit;

    private Function<NatsMessage, Boolean> beforeQueueProcessor;

    /**
     * Create a subscription. A null dispatcher makes this a synchronous subscription, which gets
     * its own incoming message queue.
     * @param sid the connection unique subscription id
     * @param subject the subject subscribed to
     * @param queueName the queue group name, or null if this is not a queue subscription
     * @param connection the connection the subscription belongs to
     * @param dispatcher the dispatcher delivering the messages, or null for a synchronous subscription
     */
    public NatsSubscription(String sid, String subject, String queueName, NatsConnection connection, NatsDispatcher dispatcher) {
        super(connection);
        this.subject = subject;
        this.queueName = queueName;
        this.sid = sid;
        this.dispatcher = dispatcher;
        this.unSubMessageLimit = new AtomicLong(-1);

        if (this.dispatcher == null) {
            this.incoming = new ConsumerMessageQueue();
        }

        setBeforeQueueProcessorFunction(null);
    }

    void reSubscribe(String newDeliverSubject) {
        connection.sendUnsub(this, 0);
        // The handler is keyed by the sid being replaced, so it has to be read before the remove clears it.
        MessageHandler handler = dispatcher == null ? null : dispatcher.getNonDefaultHandlerBySid(sid);
        connection.remove(this); // the old sid, out of the connection's map and the dispatcher's
        if (dispatcher == null) {
            // No dispatcher means nothing is keyed to the sid beyond the connection's own map,
            // so there is nothing to register before the connection announces it.
            sid = connection.getNextSid();
            connection.reSubscribe(sid, this, newDeliverSubject, queueName, null);
        }
        else {
            sid = dispatcher.reSubscribe(this, newDeliverSubject, queueName, handler);
        }
        subject = newDeliverSubject;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isActive() {
        return (dispatcher != null || incoming != null);
    }

    void setBeforeQueueProcessorFunction(@Nullable Function<NatsMessage, Boolean> beforeQueueProcessor) {
        this.beforeQueueProcessor = beforeQueueProcessor == null ? m -> true : beforeQueueProcessor;
    }

    /**
     * The function run on the reader thread as each message arrives, before the message reaches the
     * queue or the dispatcher. Returning false drops the message. Defaults to a function that
     * accepts everything.
     * @return the before queue processor
     */
    public Function<NatsMessage, Boolean> getBeforeQueueProcessor() {
        return beforeQueueProcessor;
    }

    void invalidate() {
        ConsumerMessageQueue copy = incoming;
        if (copy != null) {
            copy.pause();
        }
        this.dispatcher = null;
        this.incoming = null;
    }

    void setUnsubLimit(long cd) {
        unSubMessageLimit.set(cd);
    }

    boolean reachedUnsubLimit() {
        long max = unSubMessageLimit.get();
        long recv = getDeliveredCount();
        return (max > 0) && (max <= recv);
    }

    @Override
    ConsumerMessageQueue getMessageQueue() {
        return incoming;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String getSubject() {
        return subject;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public String getQueueName() {
        return queueName;
    }

    /**
     * Get the subscription unique id
     * @return the id
     */
    @NonNull
    public String getSID() {
        return sid;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessage(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 millisecond.");
        }
        return _nextMessage(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 " + unit + ".");
        }
        return _nextMessage(timeout, unit);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageNoWait() throws InterruptedException {
        return _nextMessage(null, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageWaitForever() throws InterruptedException {
        return _nextMessage(0L, TimeUnit.MILLISECONDS);
    }

    // Raw primitive: null = poll once (no wait), <= 0 = wait forever, > 0 = wait that long. No arg validation.
    // Public nextMessage* methods validate and delegate here; JetStream subclasses use it for unmanaged reads.
    @Nullable Message _nextMessage(@Nullable Long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        if (dispatcher != null) {
            throw new IllegalStateException("Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.");
        }

        ConsumerMessageQueue copy = incoming;
        if (copy == null) {
            throw new IllegalStateException("This subscription is inactive.");
        }

        NatsMessage msg = copy.pop(timeout, timeoutUnit);

        // The field read catches invalidate() nulling it, the isRunning() catches pause(). Both are
        // needed: invalidate() pauses before nulling, but the pause is a CAS from RUNNING, so a queue
        // that was already DRAINING stays DRAINING and isRunning() alone would miss it. Reading the
        // field is only a null comparison - copy is what gets dereferenced, and copy cannot be null here.
        if (incoming == null || !copy.isRunning()) { // We were unsubscribed while waiting
            throw new IllegalStateException("This subscription became inactive.");
        }

        if (msg != null) {
            incrementDeliveredCount();
        }

        if (reachedUnsubLimit()) {
            connection.invalidate(this);
        }

        return msg;
    }

    /** {@inheritDoc} */
    @Override
    public void unsubscribe() {
        unsubscribe(-1);
    }

    /** {@inheritDoc} */
    @Override
    public Subscription unsubscribe(int after) {
        if (dispatcher == null) {
            if (isDraining()) { // No op while draining
                return this;
            }
            if (incoming == null) {
                throw new IllegalStateException("This subscription is inactive.");
            }

            connection.unsubscribe(this, after);
            return this;
        }

        dispatcher.unsubscribe(this, after);
        return this;
    }

    /** {@inheritDoc} */
    @Override
    void sendUnsubForDrain() {
        connection.sendUnsub(this, -1);
    }

    /** {@inheritDoc} */
    @Override
    void cleanUpAfterDrain() {
        connection.invalidate(this);
    }
}
