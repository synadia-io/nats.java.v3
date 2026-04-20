package io.synadia.client.impl;

/**
 *  A checked version of a JetStreamStatusException
 */
public class JetStreamStatusCheckedException extends Exception {

    /**
     * construct a JetStreamStatusCheckedException from a JetStreamStatusException
     * @param cause the JetStreamStatusException cause
     */
    public JetStreamStatusCheckedException(JetStreamStatusException cause) {
        super(cause);
    }
}
