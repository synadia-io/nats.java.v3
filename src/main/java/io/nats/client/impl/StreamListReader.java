package io.nats.client.impl;

import io.nats.client.api.StreamInfo;
import io.nats.client.support.JsonValue;

import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.ApiConstants.STREAMS;
import static io.nats.client.support.ApiConstants.SUBJECT;

class StreamListReader extends AbstractListReader {

    List<StreamInfo> streams;

    StreamListReader() {
        super(STREAMS, SUBJECT);
        streams = new ArrayList<>();
    }

    @Override
    void processItems(List<JsonValue> items) {
        for (JsonValue v : items) {
            streams.add(new StreamInfo(v));
        }
    }

    List<StreamInfo> getStreams() {
        return streams;
    }
}
