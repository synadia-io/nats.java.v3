package io.synadia.client.impl;

import io.synadia.client.api.StreamInfo;
import io.synadia.client.support.JsonValue;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.support.ApiConstants.STREAMS;
import static io.synadia.client.support.ApiConstants.SUBJECT;

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
