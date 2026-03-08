package io.nats.client.impl;

import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.api.ApiResponse;

import java.nio.charset.StandardCharsets;

import static io.nats.client.support.ApiConstants.*;
import static io.nats.client.support.JsonValueUtils.readInteger;

class ListRequestEngine extends ApiResponse<ListRequestEngine> {

    private static final String OFFSET_JSON_START = "{\"offset\":";

    protected int total = Integer.MAX_VALUE; // so always has the first "at least one more"
    protected int limit = 0;
    protected int lastOffset = 0;

    ListRequestEngine() {
        super();
    }

    ListRequestEngine(Message msg) throws JetStreamApiException {
        super(msg);
        io.nats.client.api.Error apiError = super.getErrorObject();
        if (apiError != null) {
            throw new JetStreamApiException(apiError);
        }
        total = readInteger(jv, TOTAL, -1);
        limit = readInteger(jv, LIMIT, 0);
        lastOffset = readInteger(jv, OFFSET, 0);
    }

    boolean hasMore() {
        return total > nextOffset();
    }

    private byte[] noFilterJson() {
        return (OFFSET_JSON_START + nextOffset() + "}").getBytes(StandardCharsets.UTF_8);
    }

    byte[] internalNextJson() {
        return hasMore() ? noFilterJson() : null;
    }

    byte[] internalNextJson(String fieldName, String filter) {
        if (hasMore()) {
            if (filter == null) {
                return noFilterJson();
            }
            return (OFFSET_JSON_START + nextOffset()
                    + ",\"" + fieldName + "\":\"" + filter + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    int nextOffset() {
        return lastOffset + limit;
    }
}
