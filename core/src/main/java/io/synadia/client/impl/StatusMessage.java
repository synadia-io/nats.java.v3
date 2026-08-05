package io.synadia.client.impl;

import io.synadia.client.api.Status;

/**
 * A message that carries a protocol status instead of user data, for instance a 404 No Messages
 * or a 100 Idle Heartbeat from the server. It has no subject, headers or payload.
 */
public class StatusMessage extends IncomingMessage {
    private final Status status;

    StatusMessage(Status status) {
        super(null);
        this.status = status;
    }

    @Override
    public boolean isStatusMessage() {
        return true;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "StatusMessage{" +
            "code=" + status.getCode() +
            ", message='" + status.getMessage() + '\'' +
            '}';
    }
}
