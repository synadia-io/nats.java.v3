package io.synadia.client;

import io.synadia.client.support.Status;

/**
 * JetStreamStatusException is used to indicate an unknown status message was received.
 */
public class JetStreamStatusException extends IllegalStateException {
    /**
     * A description for an exception that does not provide one
     */
    public static final String DEFAULT_DESCRIPTION = "Unknown or unprocessed status message";

    /**
     * The subscription that this exception occured on
     */
    private final JetStreamSubscription sub;

    /**
     * The status object
     */
    private final Status status;

    /**
     * Construct JetStreamStatusException for a subscription and a status
     *
     * @param status the status
     * @param sub    the subscription
     */
    public JetStreamStatusException(Status status, JetStreamSubscription sub) {
        super(status == null ? DEFAULT_DESCRIPTION : status.getMessageWithCode());
        this.sub = sub;
        this.status = status;
    }

    /**
     * Construct JetStreamStatusException for a status
     * @param status the status
     */
    public JetStreamStatusException(Status status) {
        this(status, null);
    }

    /**
     * Get the subscription this issue occurred on
     * @return the subscription
     */
    public JetStreamSubscription getSubscription() {
        return sub;
    }

    /**
     * Get the full status object
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
    }
}
