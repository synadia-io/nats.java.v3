package io.synadia.client.impl;

import io.synadia.client.api.JetStreamException;

/**
 *  A checked version of a JetStreamStatusInternalException
 */
public class JetStreamStatusException extends JetStreamException {

    /**
     * construct a JetStreamStatusException from a JetStreamStatusInternalException
     * @param cause the JetStreamStatusInternalException cause
     */
    public JetStreamStatusException(JetStreamStatusInternalException cause) {
        super(cause);
    }
}
