package io.nats.client;

/**
 * A listener that allows the user to track messages as soon as they come in off the wire.
 * Be aware that implementations tracking messages can slow down handing off of those messages to the subscriptions waiting for them
 * if they take a significant time to process them. This class is really intended for debugging purposes.
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
