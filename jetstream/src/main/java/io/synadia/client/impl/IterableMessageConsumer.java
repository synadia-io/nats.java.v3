package io.synadia.client.impl;

import io.synadia.client.Message;
import org.jspecify.annotations.Nullable;

/**
 * A simplified consumer that does endless consume on the style of an iterator
 */
public interface IterableMessageConsumer extends MessageConsumer {
    /**
     * Read the next message. Return null on timeout (or, for an immediate poll, if nothing is buffered).
     * @param timeoutMillis the wait in milliseconds: {@code null} = poll once / return immediately,
     *                       {@code <= 0} = wait forever (can still be interrupted by unsubscribe/close), {@code > 0} = wait up to that long
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusCheckedException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage(@Nullable Long timeoutMillis) throws InterruptedException, JetStreamStatusCheckedException;
}
