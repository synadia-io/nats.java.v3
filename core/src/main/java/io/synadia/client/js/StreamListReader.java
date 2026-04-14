package io.synadia.client.js;

import io.nats.json.LazyJsonValue;
import io.synadia.client.jsapi.StreamInfo;

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
    void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            streams.add(new StreamInfo(v));
        }
    }

    List<StreamInfo> getStreams() {
        return streams;
    }
}
