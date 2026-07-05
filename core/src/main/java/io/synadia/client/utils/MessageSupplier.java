package io.synadia.client.utils;

import io.synadia.client.Message;

/**
 * Supplies a {@link Message}, typically by a blocking read such as a {@code nextMessage} call.
 * Like {@link java.util.function.Supplier} but its {@link #get()} may throw {@link InterruptedException},
 * so it can wrap a call that blocks while waiting for a message.
 */
@FunctionalInterface
public interface MessageSupplier {
    /**
     * Get the next message.
     * @return the message, or null if none was produced
     * @throws InterruptedException if interrupted while waiting for the message
     */
    Message get() throws InterruptedException;
}
