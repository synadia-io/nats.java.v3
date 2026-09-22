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
     * Construct StatusException for a status, with a message the subtype composed.
     * @param message the exception message
     * @param status the status
     */
    protected StatusException(String message, Status status) {
        super(message);
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

    /**
     * Whether the status behind the exception is a bad request response
     * @return true if this is bad request response
     */
    public boolean isBadRequest() {
        return status.isBadRequest();
    }

    /**
     * Whether the status behind the exception is a flow control status, which a push consumer answers to keep the server sending.
     * @return true if this is flow control
     */
    public boolean isFlowControl() {
        return status.isFlowControl();
    }

    /**
     * Whether the status behind the exception is an idle heartbeat status, sent when a consumer has had no traffic.
     * @return true if this is an idle heartbeat
     */
    public boolean isHeartbeat() {
        return status.isHeartbeat();
    }

    /**
     * Whether the status behind the exception is a no-responders status, meaning nothing was listening on the request subject.
     * @return true if this is no responders
     */
    public boolean isNoResponders() {
        return status.isNoResponders();
    }

    /**
     * Whether the status behind the exception is an end-of-batch status.
     * @return true if this is end of batch
     */
    public boolean isEob() {
        return status.isEob();
    }
}
