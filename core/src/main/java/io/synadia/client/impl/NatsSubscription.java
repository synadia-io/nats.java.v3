package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.Subscription;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class NatsSubscription extends NatsMessageSink implements Subscription {

    private String subject;
    private final String queueName;
    private String sid;

    private NatsDispatcher dispatcher;
    private ConsumerMessageQueue incoming;

    private final AtomicLong unSubMessageLimit;

    private Function<NatsMessage, Boolean> beforeQueueProcessor;

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
        if (dispatcher == null) {
            connection.remove(this);
            sid = connection.reSubscribe(this, newDeliverSubject, queueName);
        }
        else {
            MessageHandler handler = dispatcher.getNonDefaultHandlerBySid(sid);
            dispatcher.remove(this);
            sid = dispatcher.reSubscribe(this, newDeliverSubject, queueName, handler);
        }
        subject = newDeliverSubject;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isActive() {
        return (this.dispatcher != null || this.incoming != null);
    }

    void setBeforeQueueProcessorFunction(@Nullable Function<NatsMessage, Boolean> beforeQueueProcessor) {
        this.beforeQueueProcessor = beforeQueueProcessor == null ? m -> true : beforeQueueProcessor;
    }

    public Function<NatsMessage, Boolean> getBeforeQueueProcessor() {
        return beforeQueueProcessor;
    }

    void invalidate() {
        if (this.incoming != null) {
            this.incoming.pause();
        }
        this.dispatcher = null;
        this.incoming = null;
    }

    void setUnsubLimit(long cd) {
        this.unSubMessageLimit.set(cd);
    }

    boolean reachedUnsubLimit() {
        long max = this.unSubMessageLimit.get();
        long recv = this.getDeliveredCount();
        return (max > 0) && (max <= recv);
    }

    @Override
    ConsumerMessageQueue getMessageQueue() {
        return this.incoming;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public String getSubject() {
        return this.subject;
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public String getQueueName() {
        return this.queueName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public NatsDispatcher getDispatcher() {
        return this.dispatcher;
    }

    /**
     * Get the subscription unique id
     * @return the id
     */
    @NonNull
    public String getSID() {
        return this.sid;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessage(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 millisecond.");
        }
        return nextMessageInternal(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 " + unit + ".");
        }
        return nextMessageInternal(timeout, unit);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageNoWait() throws InterruptedException {
        return nextMessageInternal(null, TimeUnit.MILLISECONDS);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageWaitForever() throws InterruptedException {
        return nextMessageInternal(0L, TimeUnit.MILLISECONDS);
    }

    // Raw primitive: null = poll once (no wait), <= 0 = wait forever, > 0 = wait that long. No arg validation.
    // Public nextMessage* methods validate and delegate here; JetStream subclasses use it for unmanaged reads.
    @Nullable Message nextMessageInternal(@Nullable Long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        if (this.dispatcher != null) {
            throw new IllegalStateException("Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.");
        }
        else if (this.incoming == null) {
            throw new IllegalStateException("This subscription is inactive.");
        }

        NatsMessage msg = incoming.pop(timeout, timeoutUnit);

        if (this.incoming == null || !this.incoming.isRunning()) { // We were unsubscribed while waiting
            throw new IllegalStateException("This subscription became inactive.");
        }

        if (msg != null) {
            this.incrementDeliveredCount();
        }

        if (this.reachedUnsubLimit()) {
            this.connection.invalidate(this);
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
        this.connection.sendUnsub(this, -1);
    }

    /** {@inheritDoc} */
    @Override
    void cleanUpAfterDrain() {
        this.connection.invalidate(this);
    }
}
