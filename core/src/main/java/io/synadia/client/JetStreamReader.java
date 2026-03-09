package io.synadia.client;

import java.time.Duration;

/**
 * This interface provides a simple iterative access to a pull consumer.
 * <p>Note: This interface is superseded by  {@link ConsumerContext ConsumerContext}. For examples for <b>recommended usage</b> see {@link JetStream JetStream}.
 */
public interface JetStreamReader {
    /**
     * Read the next message. Return null if the calls times out.
     * Use a timeout of 0 to wait indefinitely. This could still be interrupted if
     * the subscription is unsubscribed or the client connection is closed.
     * @param timeout the maximum time to wait
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessage(Duration timeout) throws InterruptedException;

    /**
     * Read the next message. Return null if the calls times out.
     * Use a timeout of 0 to wait indefinitely. This could still be interrupted if
     * the subscription is unsubscribed or the client connection is closed.
     * @param timeoutMillis the maximum time to wait
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessage(long timeoutMillis) throws InterruptedException;

    /**
     * Stop getting more messages from the server. Some messages may have already arrived and
     * are in the buffer, so continue to call nextMessage until a null is returned.
     */
    void stop();
}
