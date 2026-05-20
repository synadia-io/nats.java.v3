package io.synadia.client.impl;

import io.synadia.client.api.Status;

/**
 * StatusException is used to carry a status message response that was received.
 */
public class StatusException extends IllegalStateException {
    /**
     * A description for an exception that does not provide one
     */
    public static final String DEFAULT_DESCRIPTION = "Unknown or unprocessed status message";

    /**
     * The status object
     */
    protected final Status status;

    /**
     * Construct StatusException for a status
     * @param status the status
     */
    public StatusException(Status status) {
        super(status == null ? DEFAULT_DESCRIPTION : status.getMessageWithCode());
        this.status = status;
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
