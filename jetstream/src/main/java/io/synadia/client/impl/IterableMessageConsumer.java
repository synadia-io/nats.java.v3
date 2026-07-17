package io.synadia.client.impl;

import io.synadia.client.Message;

import java.util.concurrent.TimeUnit;

/**
 * A simplified consumer that does endless consume on the style of an iterator
 */
public interface IterableMessageConsumer extends MessageConsumer {
    /**
     * Read the next message, waiting up to {@code timeoutMillis}.
     * @param timeoutMillis the maximum time to wait, in milliseconds; must be at least 1
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeoutMillis} is less than 1
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage(long timeoutMillis) throws InterruptedException, JetStreamStatusException;

    /**
     * Read the next message, waiting up to {@code timeout} of the given unit.
     * @param timeout the maximum time to wait, in {@code unit}s; must be at least 1
     * @param unit the time unit of {@code timeout}
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeout} is less than 1
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException, JetStreamStatusException;

    /**
     * Poll once for an already-buffered message and return immediately, without waiting.
     * @return the next buffered message, or null if none is currently available
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessageNoWait() throws InterruptedException, JetStreamStatusException;

    /**
     * Read the next message, blocking indefinitely until one is available (interruptible by unsubscribe/close).
     * @return the next message, or null if the subscription became inactive
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessageWaitForever() throws InterruptedException, JetStreamStatusException;
}
