package io.synadia.client.api;

import io.synadia.client.MessageHandler;
import io.synadia.client.impl.NatsDispatcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The SubscribeBehavior configures how the subscription behaves
 */
@NullMarked
public class SubscribeBehavior {
    public final static SubscribeBehavior DEFAULT_SUBSCRIBE_BEHAVIOR = new SubscribeBehavior();

    private @Nullable NatsDispatcher dispatcher;
    private @Nullable MessageHandler handler;
    private long messageAlarmTime;
    private long pendingMessageLimit; // Only applicable for non-dispatched (sync) push consumers.
    private long pendingByteLimit;    // Only applicable for non-dispatched (sync) push consumers.

    public SubscribeBehavior() {}

    /**
     * The user's dispatcher
     * @return the dispatcher
     */
    @Nullable
    public NatsDispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * The user's handler
     * @return the handler
     */
    @Nullable
    public MessageHandler getHandler() {
        return handler;
    }

    /**
     * Get the time amount of time allowed to elapse without a heartbeat.
     * If not set will default to 3 times the idle heartbeat setting
     * @return the message alarm time
     */
    public long getMessageAlarmTime() {
        return messageAlarmTime;
    }

    /**
     * Gets the pending message limit. Only applicable for non-dispatched (sync) push consumers.
     *
     * @return the message limit
     */
    public long getPendingMessageLimit() {
        return pendingMessageLimit;
    }

    /**
     * Gets the pending byte limit. Only applicable for non-dispatched (sync) push consumers.
     *
     * @return the byte limit
     */
    public long getPendingByteLimit() {
        return pendingByteLimit;
    }

    /**
     * Copy all behaviors from an existing SubscribeBehavior
     * @param subscribeBehavior the object to copy
     * @return this instance for chaining.
     */
    public SubscribeBehavior subscribeBehavior(SubscribeBehavior subscribeBehavior) {
        dispatcher = subscribeBehavior.dispatcher;
        handler = subscribeBehavior.handler;
        messageAlarmTime = subscribeBehavior.messageAlarmTime;
        pendingMessageLimit = subscribeBehavior.pendingMessageLimit;
        pendingByteLimit = subscribeBehavior.pendingByteLimit;
        return this;
    }

    /**
     * Set the dispatcher to use for handlers
     * @param dispatcher the dispatcher instance
     * @return this instance for chaining.
     */
    public SubscribeBehavior dispatcher(NatsDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        return this;
    }

    /**
     * Set the handler for messages to go to
     * @param handler The target for the messages
     * @return this instance for chaining.
     */
    public SubscribeBehavior handler(MessageHandler handler) {
        this.handler = handler;
        return this;
    }

    /**
     * Set the total amount of time to not receive any messages or heartbeats
     * before calling the ErrorListener heartbeatAlarm
     * @param messageAlarmTime the time
     * @return this instance for chaining.
     */
    public SubscribeBehavior messageAlarmTime(long messageAlarmTime) {
        this.messageAlarmTime = messageAlarmTime;
        return this;
    }

    /**
     * Set the maximum number of messages that non-dispatched push subscriptions can hold
     * in the internal (pending) message queue. Defaults to 512 * 1024  (Consumer.DEFAULT_MAX_MESSAGES)
     * ONLY APPLIES TO PUSH CONSUMER
     * @param pendingMessageLimit the number of messages.
     * @return the builder
     */
    public SubscribeBehavior pendingMessageLimit(long pendingMessageLimit) {
        this.pendingMessageLimit = pendingMessageLimit;
        return this;
    }

    /**
     * Set the maximum number of bytes that non-dispatched push subscriptions can hold
     * in the internal (pending) message queue. Defaults to 64 * 1024 * 1024 (Consumer.DEFAULT_MAX_BYTES)
     * ONLY APPLIES TO PUSH CONSUMER
     * @param pendingByteLimit the number of bytes.
     * @return the builder
     */
    public SubscribeBehavior pendingByteLimit(long pendingByteLimit) {
        this.pendingByteLimit = pendingByteLimit;
        return this;
    }
}
