package io.synadia.client.js;

import io.nats.json.LazyJsonValue;
import io.synadia.client.jsapi.ConsumerInfo;

import java.util.ArrayList;
import java.util.List;

import static io.synadia.client.support.ApiConstants.CONSUMERS;

public class ConsumerListReader extends AbstractListReader {

    List<ConsumerInfo> consumers;

    public ConsumerListReader() {
        super(CONSUMERS);
        consumers = new ArrayList<>();
    }

    @Override
    void processItems(List<LazyJsonValue> items) {
        for (LazyJsonValue v : items) {
            consumers.add(new ConsumerInfo(v));
        }
    }

    public List<ConsumerInfo> getConsumers() {
        return consumers;
    }
}
