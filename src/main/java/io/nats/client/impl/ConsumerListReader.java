package io.nats.client.impl;

import io.nats.client.api.ConsumerInfo;
import io.nats.client.support.JsonValue;

import java.util.ArrayList;
import java.util.List;

import static io.nats.client.support.ApiConstants.CONSUMERS;

class ConsumerListReader extends AbstractListReader {

    List<ConsumerInfo> consumers;

    ConsumerListReader() {
        super(CONSUMERS);
        consumers = new ArrayList<>();
    }

    @Override
    protected void processItems(List<JsonValue> items) {
        for (JsonValue v : items) {
            consumers.add(new ConsumerInfo(v));
        }
    }

    public List<ConsumerInfo> getConsumers() {
        return consumers;
    }
}
