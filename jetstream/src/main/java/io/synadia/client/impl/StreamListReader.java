package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.api.StreamInfo;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.testutils.ApiConstants.STREAMS;
import static io.synadia.client.testutils.ApiConstants.SUBJECT;

class StreamListReader extends AbstractListReader {

    List<StreamInfo> streams;

    StreamListReader() {
        super(STREAMS, SUBJECT);
        streams = new ArrayList<>();
    }

    @Override
    void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            streams.add(new StreamInfo(v));
        }
    }

    List<StreamInfo> getStreams() {
        return streams;
    }
}
