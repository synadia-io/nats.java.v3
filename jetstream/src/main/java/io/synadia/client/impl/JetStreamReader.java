package io.synadia.client.impl;

import io.synadia.client.Message;

import java.util.concurrent.TimeUnit;

/**
 * This interface provides simple iterative access to a pull consumer.
 * <p>Note: This interface is superseded by  {@link ConsumerContext ConsumerContext}. For examples for <b>recommended usage</b> see {@link JetStream JetStream}.
 */
public interface JetStreamReader {
    /**
     * Read the next message, waiting up to {@code timeoutMillis}.
     * @param timeoutMillis the maximum time to wait, in milliseconds; must be at least 1
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeoutMillis} is less than 1
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessage(long timeoutMillis) throws InterruptedException;

    /**
     * Read the next message, waiting up to {@code timeout} of the given unit.
     * @param timeout the maximum time to wait, in {@code unit}s; must be at least 1
     * @param unit the time unit of {@code timeout}
     * @return the next message, or null if the wait elapsed with no message
     * @throws IllegalArgumentException if {@code timeout} is less than 1
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Poll once for an already-buffered message and return immediately, without waiting.
     * @return the next buffered message, or null if none is currently available
     * @throws InterruptedException if one is thrown
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessageNoWait() throws InterruptedException;

    /**
     * Read the next message, blocking indefinitely until one is available (interruptible by unsubscribe/close).
     * @return the next message, or null if the subscription became inactive
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessageWaitForever() throws InterruptedException;

    /**
     * Stop getting more messages from the server. Some messages may have already arrived and
     * are in the buffer, so continue to call nextMessage until a null is returned.
     */
    void stop();
}
