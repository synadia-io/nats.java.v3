package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.api.StreamInfo;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.utils.ApiConstants.STREAMS;
import static io.synadia.client.utils.ApiConstants.SUBJECT;

/**
 * Accumulates the StreamInfo objects from the pages of a stream list request,
 * optionally filtered on the server by subject.
 */
public class StreamListReader extends AbstractListReader {

    List<StreamInfo> streams;

    /**
     * Construct a reader with an empty result list.
     */
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

    /**
     * The streams gathered so far. Complete once hasMore returns false.
     * @return the list, empty if nothing has been read
     */
    public List<StreamInfo> getStreams() {
        return streams;
    }
}
