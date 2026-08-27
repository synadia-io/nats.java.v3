package io.synadia.client.api;

import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsConstants;
import io.synadia.client.impl.NatsDispatcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The SubscribeBehavior configures how the subscription behaves
 */
@NullMarked
public class SubscribeBehavior {

    protected @Nullable NatsDispatcher dispatcher;
    protected @Nullable MessageHandler handler;
    protected long messageAlarmTime;

    // Configures a synchronous (non-dispatched) push subscription's own queue.
    // Async delivery is bounded by the dispatcher's own limits; pull by its batch size.
    private long pendingMessageLimit = OptionsConstants.DEFAULT_MAX_MESSAGES;
    private long pendingByteLimit = OptionsConstants.DEFAULT_MAX_BYTES;

    /**
     * Construct a SubscribeBehavior with default settings.
     */
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
     * Gets the pending message limit, the cap on a synchronous push subscription's own queue.
     *
     * @return the message limit
     */
    public long getPendingMessageLimit() {
        return pendingMessageLimit;
    }

    /**
     * Gets the pending byte limit, the cap on a synchronous push subscription's own queue.
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
     * Set the maximum number of messages the internal (pending) queue may hold before messages
     * are dropped; 0 or negative means no limit. Defaults to {@code OptionsConstants.DEFAULT_MAX_MESSAGES}.
     * <p>Applies to a synchronous (non-dispatched) push subscription, which owns its own queue.
     * Asynchronous delivery is bounded by the dispatcher's own limits (set them on the dispatcher) —
     * that queue can still back up when a message handler is slow to return. Pull subscriptions
     * don't use this; each pull requests a bounded batch.
     * @param pendingMessageLimit the number of messages.
     * @return this instance for chaining.
     */
    public SubscribeBehavior pendingMessageLimit(long pendingMessageLimit) {
        this.pendingMessageLimit = pendingMessageLimit < 1 ? 0 : pendingMessageLimit;
        return this;
    }

    /**
     * Set the maximum number of bytes the internal (pending) queue may hold before messages
     * are dropped; 0 or negative means no limit. Defaults to {@code OptionsConstants.DEFAULT_MAX_BYTES}.
     * <p>Applies to a synchronous (non-dispatched) push subscription, which owns its own queue.
     * Asynchronous delivery is bounded by the dispatcher's own limits (set them on the dispatcher) —
     * that queue can still back up when a message handler is slow to return. Pull subscriptions
     * don't use this; each pull requests a bounded batch.
     * @param pendingByteLimit the number of bytes.
     * @return this instance for chaining.
     */
    public SubscribeBehavior pendingByteLimit(long pendingByteLimit) {
        this.pendingByteLimit = pendingByteLimit < 1 ? 0 : pendingByteLimit;
        return this;
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof SubscribeBehavior that)) return false;
        return messageAlarmTime == that.messageAlarmTime
            && pendingMessageLimit == that.pendingMessageLimit
            && pendingByteLimit == that.pendingByteLimit
            && Objects.equals(dispatcher, that.dispatcher)
            && Objects.equals(handler, that.handler);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(dispatcher);
        result = 31 * result + Objects.hashCode(handler);
        result = 31 * result + Long.hashCode(messageAlarmTime);
        result = 31 * result + Long.hashCode(pendingMessageLimit);
        result = 31 * result + Long.hashCode(pendingByteLimit);
        return result;
    }
}
