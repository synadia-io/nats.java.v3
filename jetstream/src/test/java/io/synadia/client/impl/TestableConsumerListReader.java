package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TestableConsumerListReader extends ConsumerListReader {
    public TestableConsumerListReader() {
        super();
    }

    @Override
    public void processItems(List<LazyJsonValue> items) {
        super.processItems(items);
    }

    @Override
    public List<ConsumerInfo> getConsumers() {
        return super.getConsumers();
    }

    @Override
    public void process(Message msg) throws JetStreamApiException {
        super.process(msg);
    }

    @Override
    public byte[] nextJson() {
        return super.nextJson();
    }

    @Override
    public byte[] nextJson(@Nullable String filter) {
        return super.nextJson(filter);
    }

    @Override
    public boolean hasMore() {
        return super.hasMore();
    }
}
