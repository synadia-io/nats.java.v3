package io.synadia.client;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * A Subscription encapsulates an incoming queue of messages associated with a single
 * subject and optional queue name. Subscriptions can be in one of two modes. Either the
 * subscription can be used for synchronous reading of messages with {@link #nextMessage(Long) nextMessage()}
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
public interface Subscription extends Consumer {

    /**
     * the subject associated with this subscription, will be non-null
     * @return the subject
     */
    String getSubject();

    /**
     * the queue associated with this subscription, may be null.
     * @return the queue name
     */
    String getQueueName();

    /**
     * the Dispatcher that owns this subscription, or null
     * @return the dispatcher instance
     */
    Dispatcher getDispatcher();

    /**
     * Get the subscription unique id
     * @return the id
     */
    String getSID();

    /**
     * Gets the consumer name associated with the subscription.
     * Not all subscriptions have consumer names
     * @return the consumer name
     */
    default @Nullable String getConsumerName() {
        return null;
    }

    /**
     * Read the next message for a subscription, or block until one is available.
     * While useful in some situations, i.e. tests and simple examples, using a
     * Dispatcher is generally easier and likely preferred for application code.
     *
     * <p>Will return null if the call times out (or, for an immediate poll, if nothing is buffered).
     *
     * <p>The {@code timeoutMillis} value selects the behavior:
     * <ul>
     *   <li>{@code null} &mdash; poll once and return immediately with whatever message is already buffered (or {@code null} if none); no waiting.</li>
     *   <li>{@code <= 0} (or any value {@code <= 0}) &mdash; wait indefinitely. This can still be interrupted if the subscription is unsubscribed or the connection is closed.</li>
     *   <li>{@code > 0} &mdash; wait up to that many milliseconds.</li>
     * </ul>
     *
     * @param timeoutMillis the wait in milliseconds: {@code null} = return immediately, {@code <= 0} = wait forever, {@code > 0} = wait up to that long
     * @return the next message for this subscriber, or null on timeout / immediate-empty
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs while waiting for the message
     */
    @Nullable Message nextMessage(@Nullable Long timeoutMillis) throws InterruptedException;

    /**
     * Read the next message for a subscription, or block until one is available.
     * While useful in some situations, i.e. tests and simple examples, using a
     * Dispatcher is generally easier and likely preferred for application code.
     *
     * <p>Will return null if the call times out (or, for an immediate poll, if nothing is buffered).
     *
     * <p>The {@code timeout} value selects the behavior:
     * <ul>
     *   <li>{@code null} &mdash; poll once and return immediately with whatever message is already buffered (or {@code null} if none); no waiting.</li>
     *   <li>{@code <= 0} (or any value {@code <= 0}) &mdash; wait indefinitely. This can still be interrupted if the subscription is unsubscribed or the connection is closed.</li>
     *   <li>{@code > 0} &mdash; wait up to that many time units.</li>
     * </ul>
     *
     * @param timeout the wait amount: {@code null} = return immediately, {@code <= 0} = wait forever, {@code > 0} = wait up to that long
     * @param timeoutUnit the time unit of the timeout
     * @return the next message for this subscriber, or null on timeout / immediate-empty
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     * @throws InterruptedException if one occurs while waiting for the message
     */
    @Nullable Message nextMessage(@Nullable Long timeout, TimeUnit timeoutUnit) throws InterruptedException;

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
     * <p>If the subscription has already received <code>after</code> messages, it will not receive
     * more. The provided limit is a lifetime total for the subscription, with the caveat
     * that if the subscription already received more than <code>after</code> when unsubscribe is called
     * the client will not travel back in time to stop them.
     * 
     * <p>Supports chaining so that you can do things like:
     * <pre>
     * nc = Nats.connect()
     * m = nc.subscribe("hello").unsubscribe(1).nextMessage(0L);
     * </pre>
     * 
     * @param after the number of messages to accept before unsubscribing
     * @return the subscription so that calls can be chained
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Subscription unsubscribe(int after);
}
