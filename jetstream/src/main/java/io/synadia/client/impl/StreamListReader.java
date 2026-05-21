package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.api.StreamInfo;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.utils.ApiConstants.STREAMS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

public class StreamListReader extends AbstractListReader {

    List<StreamInfo> streams;

    public StreamListReader() {
        super(STREAMS, SUBJECT);
        streams = new ArrayList<>();
    }

    @Override
    public void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            streams.add(new StreamInfo(v));
        }
    }

    public List<StreamInfo> getStreams() {
        return streams;
    }
}
