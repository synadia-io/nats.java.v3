package io.synadia.client.impl;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class InternalPublishableMessage extends NatsMessage {
    final boolean hasHeaders;

    InternalPublishableMessage() {
        super(null, null, null, null, false);
        hasHeaders = false;
    }

    InternalPublishableMessage(byte @Nullable[] data, @NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, boolean flushImmediatelyAfterPublish) {
        // headers handled by finishInit, not sent to super avoiding wasted assignment
        super(data, subject, replyTo, null, flushImmediatelyAfterPublish);
        hasHeaders = finishInit(headers);
    }

    InternalPublishableMessage(@NonNull NatsMessage nm, boolean flushImmediatelyAfterPublish) {
        // headers handled by finishInit, not sent to super avoiding wasted assignment
        super(nm.data, nm.subject, nm.replyTo, null, flushImmediatelyAfterPublish);
        hasHeaders = finishInit(nm.headers);
    }

    private boolean finishInit(@Nullable Headers headers) {
        final boolean hasHeaders;
        if (headers == null || headers.isEmpty()) {
            hasHeaders = false;
        }
        else {
            hasHeaders = true;
            this.headers = headers.isReadOnly() ? headers : new Headers(headers, true, null);
        }
        super.calculate();
        return hasHeaders;
    }

    @Override
    protected void calculate() {
        // it's already done in the constructor/finishInit
    }
}
