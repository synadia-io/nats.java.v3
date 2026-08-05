package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.api.ConsumerInfo;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.utils.ApiConstants.CONSUMERS;

/**
 * Accumulates the ConsumerInfo objects from the pages of a consumer list request for one stream.
 */
public class ConsumerListReader extends AbstractListReader {

    List<ConsumerInfo> consumers;

    /**
     * Construct a reader with an empty result list.
     */
    public ConsumerListReader() {
        super(CONSUMERS);
        consumers = new ArrayList<>();
    }

    @Override
    public void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            consumers.add(new ConsumerInfo(v));
        }
    }

    /**
     * The consumers gathered so far. Complete once hasMore returns false.
     * @return the list, empty if nothing has been read
     */
    public List<ConsumerInfo> getConsumers() {
        return consumers;
    }
}
