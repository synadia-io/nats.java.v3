package io.nats.client.impl;

import io.nats.client.support.Status;

public class StatusMessage extends IncomingMessage {
    private final Status status;

    StatusMessage(Status status) {
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
