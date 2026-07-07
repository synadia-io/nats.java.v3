package io.synadia.client;

import io.synadia.client.impl.NatsSubscription;

import java.util.concurrent.CompletableFuture;

/**
 * This library uses the concept of a Dispatcher to organize message callbacks in a way that the
 * application can control. Each dispatcher has a single {@link MessageHandler MessageHandler} that
 * will be notified of incoming messages. The dispatcher also has 0 or more subscriptions associated with it.
 * This means that a group of subscriptions, or subjects, can be combined into a single callback thread. But,
 * multiple dispatchers can be created to handle different groups of subscriptions/subjects.
 *
 * <p>All messages to this dispatcher are delivered via a single thread. If the message handler is slow to handle
 * any one message it will delay deliver of subsequent messages. Use separate dispatchers to handle the scenario of
 * a set of messages that require a lot of work and a set of fast moving messages, or create other threads as necessary.
 * The Dispatcher will only use one.
 *
 * <p>Dispatchers are created from the connection using {@link io.synadia.client.impl.NatsConnection#createDispatcher(MessageHandler) createDispatcher()}
 * and can be closed using {@link io.synadia.client.impl.NatsConnection#closeDispatcher(Dispatcher) closeDispatcher()}. Closing a dispatcher will
 * clean up the thread it is using for message deliver.
 *
 * <p><em>A dispatcher buffers incoming messages in a single queue; if the handler is slow that queue can back up.</em>
 *
 * <p>Subscribe and unsubscribe methods validate their arguments and throw {@link IllegalArgumentException} for invalid values.
 */
public interface Dispatcher {

    /**
     * Start the dispatcher with a given id.
     * Use post-construction to start to the dispatcher,
     * which should not be started on construction.
     * @param id the assigned id of the dispatcher
     */
    void start(String id);

    /**
     * Whether this dispatcher is still processing messages; false after it is stopped.
     * @return the active state
     */
    boolean isActive();

    /**
     * Drain the dispatcher: process in-flight/cached messages, stop receiving new ones,
     * then effectively unsubscribe all of its subscriptions. The returned future completes
     * when the drain finishes.
     * @param timeoutMillis time to wait for the drain, in milliseconds; 0 or less waits forever
     * @return a future that completes true when the drain succeeded
     * @throws InterruptedException if the thread is interrupted
     */
    CompletableFuture<Boolean> drain(long timeoutMillis) throws InterruptedException;

    /**
     * Set the maximum number of messages and/or bytes this dispatcher's delivery queue holds
     * before it starts dropping newly arriving messages (a &quot;slow consumer&quot;).
     *
     * <p>All of a dispatcher's subscriptions share this one queue, so the limit is per-dispatcher,
     * not per-subscription. It bounds the async slow-consumer case: the server controls the flow of
     * messages, so if the {@link MessageHandler} is slow the queue backs up, and once a limit is
     * reached newly arriving messages are dropped (and {@link ErrorListener#slowConsumerDetected}
     * fires). This is the handler-based (dispatched) equivalent of a synchronous consumer falling
     * behind on {@code nextMessage}; for a synchronous subscription the limit is set on the
     * subscription instead.
     *
     * <p>Any value less than or equal to zero means unlimited.
     * @param maxMessages the maximum message count to hold
     * @param maxBytes the maximum bytes to hold
     */
    void setPendingLimits(long maxMessages, long maxBytes);

    /**
     * @return the pending message limit set by {@link #setPendingLimits(long, long) setPendingLimits}; -1 when unlimited
     */
    long getPendingMessageLimit();

    /**
     * @return the pending byte limit set by {@link #setPendingLimits(long, long) setPendingLimits}; -1 when unlimited
     */
    long getPendingByteLimit();

    /**
     * @return the number of messages currently waiting in this dispatcher's delivery queue
     */
    long getPendingMessageCount();

    /**
     * @return the cumulative size in bytes of the messages currently waiting in this dispatcher's delivery queue
     */
    long getPendingByteCount();

    /**
     * @return the total number of messages this dispatcher has delivered to its handler(s), for all time
     */
    long getDeliveredCount();

    /**
     * @return the number of messages dropped since the last {@link #clearDroppedCount() clearDroppedCount},
     *         because a pending limit was reached
     */
    long getDroppedCount();

    /**
     * Reset this dispatcher's dropped-message count to 0.
     */
    void clearDroppedCount();

    /**
     * Create a subscription to the specified subject under the control of this
     * dispatcher.
     *
     * <p>
     * This call is a no-op if the dispatcher already has a subscription to the
     * specified subject.
     *
     *
     * @param subject The subject to subscribe to.
     * @return The NatsSubscription.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject is invalid
     */
    NatsSubscription subscribe(String subject);

    /**
     * Create a subscription to the specified subject and queue under the control of
     * this dispatcher.
     *
     * <p>
     * This call is a no-op if the dispatcher already has a subscription to the
     * specified subject (regardless of the queue name).
     *
     *
     * @param subject The subject to subscribe to.
     * @param queue The queue group to join.
     * @return The NatsSubscription.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject or the queue is invalid
     */
    NatsSubscription subscribe(String subject, String queue);

    /**
     * Create a subscription to the specified subject under the control of this
     * dispatcher. Since a MessageHandler is also required, the Dispatcher will
     * not prevent duplicate subscriptions from being made.
     *
     * <p>
     * Every call creates a new subscription, unlike the
     * {@link Dispatcher#subscribe(String)} method that does not take a
     * MessageHandler.
     *
     *
     * @param subject The subject to subscribe to.
     * @param handler The target for the messages
     * @return The NatsSubscription, so subscriptions may be later unsubscribed manually.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject is invalid, or the handler is null
     */
    NatsSubscription subscribe(String subject, MessageHandler handler);

    /**
     * Create a subscription to the specified subject under the control of this
     * dispatcher. Since a MessageHandler is also required, the Dispatcher will
     * not prevent duplicate subscriptions from being made.
     *
     * <p>
     * Every call creates a new subscription, unlike the
     * {@link Dispatcher#subscribe(String, String)} method that does not take a
     * MessageHandler.
     *
     *
     * @param subject The subject to subscribe to.
     * @param queue The queue group to join.
     * @param handler The target for the messages
     * @return The NatsSubscription, so subscriptions may be later unsubscribed manually.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject or the queue is invalid, or the handler is null
     */
    NatsSubscription subscribe(String subject, String queue, MessageHandler handler);

    /**
     * Unsubscribe from the specified subject, the queue is implicit.
     *
     * <p>Stops messages to the subscription locally and notifies the server.
     *
     * @param subject The subject to unsubscribe from.
     * @return The Dispatcher, so calls can be chained.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject is null or empty
     */
    Dispatcher unsubscribe(String subject);

    /**
     * Unsubscribe from the specified Subscription.
     *
     * <p>Stops messages to the subscription locally and notifies the server.
     * This method is to be used to unsubscribe from subscriptions created by
     * the Dispatcher using {@link Dispatcher#subscribe(String, MessageHandler)}.
     *
     * @param subscription The Subscription to unsubscribe from.
     * @return The Dispatcher, so calls can be chained.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subscription was not created by this dispatcher
     */
    Dispatcher unsubscribe(Subscription subscription);

    /**
     * Unsubscribe from the specified subject, the queue is implicit, after the
     * specified number of messages.
     *
     * <p>If the subscription has already received that many messages, it will not receive
     * more. This limit is a lifetime total for the subscription; if it has already received
     * more than the limit when unsubscribe is called, the client will not travel back in time
     * to stop them.
     *
     * <p>For example, to get a single asynchronous message, you might do:
     * <blockquote><pre>
     * nc = Nats.connect()
     * d = nc.createDispatcher(myHandler);
     * d.subscribe("hello");
     * d.unsubscribe("hello", 1);
     * </pre></blockquote>
     *
     * @param subject The subject to unsubscribe from.
     * @param after The number of messages to accept before unsubscribing
     * @return The Dispatcher, so calls can be chained.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subject is null or empty
     */
    Dispatcher unsubscribe(String subject, int after);

    /**
     * Unsubscribe from the specified subject, the queue is implicit, after the
     * specified number of messages.
     *
     * <p>If the subscription has already received that many messages, it will not receive
     * more. This limit is a lifetime total for the subscription; if it has already received
     * more than the limit when unsubscribe is called, the client will not travel back in time
     * to stop them.
     *
     * <p>Stops messages to the subscription locally and notifies the server.
     * This method is to be used to unsubscribe from subscriptions created by
     * the Dispatcher using {@link Dispatcher#subscribe(String, MessageHandler)}.
     *
     * @param subscription The Subscription to unsubscribe from.
     * @param after The number of messages to accept before unsubscribing
     * @return The Dispatcher, so calls can be chained.
     * @throws IllegalStateException if the dispatcher was previously closed
     * @throws IllegalArgumentException if the subscription is not managed by this dispatcher
     */
    Dispatcher unsubscribe(Subscription subscription, int after);
}
