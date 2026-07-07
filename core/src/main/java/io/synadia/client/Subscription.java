package io.synadia.client;

import io.synadia.client.impl.NatsDispatcher;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A Subscription encapsulates an incoming queue of messages associated with a single
 * subject and optional queue name. Subscriptions can be in one of two modes. Either the
 * subscription can be used for synchronous reading of messages with {@code Subscription.nextMessage}
 * or the subscription can be owned by a Dispatcher. When a subscription is owned by a dispatcher
 * it cannot be used to get messages or unsubscribe, those operations must be performed on the dispatcher.
 * 
 * <p>Subscriptions support the concept of auto-unsubscribe. This concept is a bit tricky, as it involves
 * the client library, the server and the Subscriptions history. If told to unsubscribe after 5 messages, a subscription
 * will stop receiving messages when one of the following occurs:
 * <ul>
 * <li>The subscription delivers 5 messages with next messages, <em>including any previous messages</em>.
 * <li>The server sends 5 messages to the subscription.
 * <li>The subscription already received 5 or more messages.
 * </ul>
 * <p>In the case of a reconnect, the remaining message count, as maintained by the subscription, will be sent
 * to the server. So if you unsubscribe with a max of 5, then disconnect after 2, the new server will be told to
 * unsubscribe after 3.
 * <p>The other, possibly confusing case, is that unsubscribe is based on total messages. So if you make a subscription and
 * receive 5 messages on it, then say unsubscribe with a maximum of 5, the subscription will immediately stop handling messages.
 */
public interface Subscription {

    /**
     * the subject associated with this subscription, will be non-null
     * @return the subject
     */
    @NonNull String getSubject();

    /**
     * the queue associated with this subscription, may be null.
     * @return the queue name
     */
    @Nullable String getQueueName();

    /**
     * the Dispatcher that owns this subscription, or null
     * @return the dispatcher instance
     */
    @Nullable NatsDispatcher getDispatcher();

    /**
     * Get the subscription unique id
     * @return the id
     */
    @NonNull String getSID();

    /**
     * Whether this subscription is still processing messages; false after unsubscribe.
     * @return the active state
     */
    boolean isActive();

    /**
     * Drain the subscription: process in-flight/cached messages, stop receiving new ones,
     * then effectively unsubscribe. The returned future completes when the drain finishes.
     * @param timeoutMillis time to wait for the drain, in milliseconds; 0 or less waits forever
     * @return a future that completes true when the drain succeeded
     * @throws InterruptedException if the thread is interrupted
     */
    CompletableFuture<Boolean> drain(long timeoutMillis) throws InterruptedException;

    /**
     * Read the next message for a subscription, waiting up to {@code timeoutMillis}.
     * While useful in some situations, i.e. tests and simple examples, using a
     * Dispatcher is generally easier and likely preferred for application code.
     *
     * @param timeoutMillis the maximum time to wait, in milliseconds; must be at least 1
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeoutMillis} is less than 1
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs while waiting for the message
     */
    @Nullable Message nextMessage(long timeoutMillis) throws InterruptedException;

    /**
     * Read the next message for a subscription, waiting up to {@code timeout} of the given unit.
     * Useful for readable waits such as {@code nextMessage(10, TimeUnit.SECONDS)}.
     *
     * @param timeout the maximum time to wait, in {@code unit}s; must be at least 1 (so the smallest possible wait is 1 nanosecond)
     * @param unit the time unit of {@code timeout}
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeout} is less than 1
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs while waiting for the message
     */
    @Nullable Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Poll once for an already-buffered message and return immediately, without waiting.
     *
     * @return the next buffered message, or null if none is currently available
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs
     */
    @Nullable Message nextMessageNoWait() throws InterruptedException;

    /**
     * Read the next message, blocking indefinitely until one is available. The wait can
     * still be interrupted if the subscription is unsubscribed or the connection is closed.
     *
     * @return the next message, or null if the subscription became inactive
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs while waiting for the message
     */
    @Nullable Message nextMessageWaitForever() throws InterruptedException;

    /**
     * Unsubscribe this subscription and stop listening for messages.
     * 
     * <p>Stops messages to the subscription locally and notifies the server.
     * 
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    void unsubscribe();

    /**
     * Unsubscribe this subscription and stop listening for messages, after the
     * specified number of messages.
     * 
     * <p>If the subscription has already received that many messages, it will not receive
     * more. This limit is a lifetime total for the subscription; if it has already received
     * more than the limit when unsubscribe is called, the client will not travel back in time
     * to stop them.
     * 
     * <p>Supports chaining so that you can do things like:
     * <pre>
     * nc = Nats.connect()
     * m = nc.subscribe("hello").unsubscribe(1).nextMessageWaitForever();
     * </pre>
     * 
     * @param after the number of messages to accept before unsubscribing
     * @return the subscription so that calls can be chained
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Subscription unsubscribe(int after);
}
