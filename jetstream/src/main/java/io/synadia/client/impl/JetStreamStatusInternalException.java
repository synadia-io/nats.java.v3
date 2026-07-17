package io.synadia.client.impl;

import io.synadia.client.api.Status;

/**
 * JetStreamStatusInternalException is used to indicate an unknown status message was received.
 */
public class JetStreamStatusInternalException extends StatusException {

    /**
     * The subscription that this exception occurred on
     */
    private final JetStreamSubscription sub;

    /**
     * Construct JetStreamStatusInternalException for a subscription and a status
     *
     * @param status the status
     * @param sub    the subscription
     */
    public JetStreamStatusInternalException(Status status, JetStreamSubscription sub) {
        super(status);
        this.sub = sub;
    }

    /**
     * Construct JetStreamStatusInternalException for a status
     * @param status the status
     */
    public JetStreamStatusInternalException(Status status) {
        this(status, null);
    }

    /**
     * Get the subscription this issue occurred on
     * @return the subscription
     */
    public JetStreamSubscription getSubscription() {
        return sub;
    }
}
