package io.synadia.client.impl;

import io.synadia.client.js.JetStreamMessage;
import io.synadia.client.support.IncomingHeadersProcessor;
import io.synadia.client.support.Status;

import static io.synadia.client.support.JetStreamConstants.JS_ACK_SUBJECT_PREFIX;

// ----------------------------------------------------------------------------------------------------
// Incoming Message Factory - internal use only
// ----------------------------------------------------------------------------------------------------
class IncomingMessageFactory {
    private final String sid;
    private final String subject;
    private final String replyTo;
    private final int protocolLineLength;

    private byte[] data;
    private Headers headers;
    private Status status;
    private int headerLen;

    // Create an incoming message for a subscriber
    // Doesn't check control line size, since the server sent us the message
    IncomingMessageFactory(String sid, String subject, String replyTo, int protocolLength) {
        this.sid = sid;
        this.subject = subject;
        this.replyTo = replyTo;
        this.protocolLineLength = protocolLength;
    }

    void setHeaders(IncomingHeadersProcessor ihp) {
        headers = ihp.getHeaders();
        status = ihp.getStatus();
        headerLen = ihp.getSerializedLength();
    }

    void setData(byte[] data) {
        this.data = data;
    }

    NatsMessage getMessage() {
        NatsMessage message;
        if (status != null) {
            message = new StatusMessage(status);
        }
        else if (replyTo != null && replyTo.startsWith(JS_ACK_SUBJECT_PREFIX)) {
            message = new JetStreamMessage(data);
        }
        else {
            message = new IncomingMessage(data);
        }
        message.sid = sid;
        message.subject = subject;
        message.replyTo = replyTo;
        message.headers = headers;
        message.headerLen = headerLen;
        message.sizeInBytes = protocolLineLength + headerLen + message.dataLen + 4; // Two CRLFs
        return message;
    }
}
