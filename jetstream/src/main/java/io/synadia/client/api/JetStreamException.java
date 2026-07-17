package io.synadia.client.api;

/**
 * Base type for the checked exceptions raised by the JetStream API. Catch this to handle any
 * JetStream failure; switch on the subtype (with a {@code default} branch) when you need to
 * distinguish the cause. Not sealed: new subtypes may be added, so a {@code default} is required.
 */
public class JetStreamException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Create a JetStreamException with a message.
     * @param message the error message
     */
    public JetStreamException(String message) {
        super(message);
    }

    /**
     * Create a JetStreamException with a message and cause.
     * @param message the error message
     * @param cause the underlying cause
     */
    public JetStreamException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a JetStreamException with a cause.
     * @param cause the underlying cause
     */
    public JetStreamException(Throwable cause) {
        super(cause);
    }
}
