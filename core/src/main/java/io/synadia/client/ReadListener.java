package io.synadia.client;

/**
 * A listener that allows the user to track messages as soon as they come in off the wire.
 * Be aware that implementations tracking messages can slow down handing off of those messages to the subscriptions waiting for them
 * if they take a significant time to process them. This class is really intended for debugging purposes.
 * <p>Unlike {@link ErrorListener ErrorListener} and {@link ConnectionListener ConnectionListener}, there can be only one... read listener.
 * This is intentional, not an oversight: this listener sits on the per-message read path where a fan-out would cost every message, and one debugging hook is
 * the expected case. Set it with {@link OptionsBuilder#readListener(ReadListener) readListener()} or replace it at
 * runtime with {@link io.synadia.client.impl.NatsConnection#setReadListener(ReadListener) setReadListener()}. For the
 * same reason there is no id method here - the error and connection listeners have one only because they are keyed
 * in a map for add/remove.
 */
public interface ReadListener {
    /**
     * Called when the message is specifically a protocol message
     * @param op the protocol operation
     * @param text the text associated with the protocol if there is any. May be null
     */
    default void protocol(String op, String text) {};

    /**
     * Called when the message is any non-protocol message
     * @param op the message operation
     * @param message the actual message
     */
    default void message(String op, Message message) {};
}
