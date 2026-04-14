package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.Subscription;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class NatsSubscription extends NatsConsumer implements Subscription {

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

    public void reSubscribe(String newDeliverSubject) {
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

    public void setBeforeQueueProcessorFunction(@Nullable Function<NatsMessage, Boolean> beforeQueueProcessor) {
        this.beforeQueueProcessor = beforeQueueProcessor == null ? m -> true : beforeQueueProcessor;
    }

    public Function<NatsMessage, Boolean> getBeforeQueueProcessor() {
        return beforeQueueProcessor;
    }

    protected void invalidate() {
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

    public NatsDispatcher getNatsDispatcher() {
        return this.dispatcher;
    }

    @Override
    ConsumerMessageQueue getMessageQueue() {
        return this.incoming;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dispatcher getDispatcher() {
        return this.dispatcher;
    }

    /**
     * Get the subscription unique id
     * @return the id
     */
    public String getSID() {
        return this.sid;
    }

    /** {@inheritDoc} */
    @Override
    public String getSubject() {
        return this.subject;
    }

    /** {@inheritDoc} */
    @Override
    public String getQueueName() {
        return this.queueName;
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(long timeoutMillis) throws InterruptedException, IllegalStateException {
        return nextMessageInternal(Duration.ofMillis(timeoutMillis));
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(Duration timeout) throws InterruptedException, IllegalStateException {
        return nextMessageInternal(timeout);
    }

    protected NatsMessage nextMessageInternal(Duration timeout) throws InterruptedException {
        if (this.dispatcher != null) {
            throw new IllegalStateException(
                    "Subscriptions that belong to a dispatcher cannot respond to nextMessage directly.");
        } else if (this.incoming == null) {
            throw new IllegalStateException("This subscription is inactive.");
        }

        NatsMessage msg = incoming.pop(timeout);

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
        if (this.dispatcher != null) {
            throw new IllegalStateException(
                    "Subscriptions that belong to a dispatcher cannot respond to unsubscribe directly.");
        } else if (this.incoming == null) {
            throw new IllegalStateException("This subscription is inactive.");
        }

        if (isDraining()) { // No op while draining
            return;
        }

        this.connection.unsubscribe(this, -1);
    }

    /** {@inheritDoc} */
    @Override
    public Subscription unsubscribe(int after) {
        if (this.dispatcher != null) {
            throw new IllegalStateException(
                    "Subscriptions that belong to a dispatcher cannot respond to unsubscribe directly.");
        } else if (this.incoming == null) {
            throw new IllegalStateException("This subscription is inactive.");
        }

        if (isDraining()) { // No op while draining
            return this;
        }

        this.connection.unsubscribe(this, after);
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
