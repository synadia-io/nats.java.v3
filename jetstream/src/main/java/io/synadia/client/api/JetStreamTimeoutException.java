package io.synadia.client.api;

import java.io.Serial;

/**
 * Raised when a JetStream request gets no response within the request timeout.
 */
public class JetStreamTimeoutException extends JetStreamException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Create a JetStreamTimeoutException with a message.
     * @param message the error message
     */
    public JetStreamTimeoutException(String message) {
        super(message);
    }
}
