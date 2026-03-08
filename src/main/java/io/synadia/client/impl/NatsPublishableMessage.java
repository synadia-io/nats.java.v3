package io.synadia.client.impl;

class NatsPublishableMessage extends NatsMessage {
    final boolean hasHeaders;

    public NatsPublishableMessage(boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
        flushImmediatelyAfterPublish = false;
    }

    public NatsPublishableMessage(String subject, String replyTo, Headers headers, byte[] data, boolean flushImmediatelyAfterPublish) {
        super(data);
        this.flushImmediatelyAfterPublish = flushImmediatelyAfterPublish;
        this.subject = subject;
        this.replyTo = replyTo;
        if (headers == null || headers.isEmpty()) {
            hasHeaders = false;
        }
        else {
            hasHeaders = true;
            this.headers = headers.isReadOnly() ? headers : new Headers(headers, true, null);
        }
        super.calculate();
    }

    @Override
    protected void calculate() {
        // it's already done in the constructor
    }
}
