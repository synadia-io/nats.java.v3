package io.synadia.client.impl;

import io.synadia.client.Message;
import org.jspecify.annotations.Nullable;

/**
 * This interface provides simple iterative access to a pull consumer.
 * <p>Note: This interface is superseded by  {@link ConsumerContext ConsumerContext}. For examples for <b>recommended usage</b> see {@link JetStream JetStream}.
 */
public interface JetStreamReader {
    /**
     * Read the next message. Return null on timeout (or, for an immediate poll, if nothing is buffered).
     * @param timeoutMillis the wait in milliseconds: {@code null} = poll once / return immediately,
     *                       {@code <= 0} = wait forever (can still be interrupted by unsubscribe/close), {@code > 0} = wait up to that long
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws IllegalStateException if the subscription belongs to a dispatcher, or is not active
     */
    Message nextMessage(@Nullable Long timeoutMillis) throws InterruptedException;

    /**
     * Stop getting more messages from the server. Some messages may have already arrived and
     * are in the buffer, so continue to call nextMessage until a null is returned.
     */
    void stop();
}
