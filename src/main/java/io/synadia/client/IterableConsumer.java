package io.synadia.client;

import java.time.Duration;

/**
 * A simplified consumer that does endless consume on the style of an iterator
 */
public interface IterableConsumer extends MessageConsumer {
    /**
     * Read the next message. Return null if the calls times out.
     * Use a timeout of 0 to wait indefinitely. This could still be interrupted if
     * the subscription is unsubscribed or the client connection is closed.
     * @param timeout the maximum time to wait
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusCheckedException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage(Duration timeout) throws InterruptedException, JetStreamStatusCheckedException;

    /**
     * Read the next message. Return null if the calls times out.
     * Use a timeout of 0 to wait indefinitely. This could still be interrupted if
     * the subscription is unsubscribed or the client connection is closed.
     * @param timeoutMillis the maximum time to wait
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusCheckedException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage(long timeoutMillis) throws InterruptedException, JetStreamStatusCheckedException;
}
