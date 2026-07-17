package io.synadia.client.api;

/**
 * Raised when a JetStream response is malformed and cannot be parsed as the expected reply.
 */
public class JetStreamProtocolException extends JetStreamException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a JetStreamProtocolException with a message.
     * @param message the error message
     */
    public JetStreamProtocolException(String message) {
        super(message);
    }
}
