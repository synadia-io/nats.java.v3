package io.synadia.client.impl;

import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.support.JsonValue;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.support.ApiConstants.CONSUMERS;

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
