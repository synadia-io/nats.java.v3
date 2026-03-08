package io.nats.client.impl;

import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.support.JsonValue;

import java.util.List;

import static io.nats.client.support.JsonValueUtils.readArray;

abstract class AbstractListReader {

    private final String objectName;
    private final String filterFieldName;
    protected ListRequestEngine engine;

    void process(Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        processItems(readArray(engine.getJv(), objectName));
    }

    abstract void processItems(List<JsonValue> items);

    AbstractListReader(String objectName) {
        this(objectName, null);
    }

    AbstractListReader(String objectName, String filterFieldName) {
        this.objectName = objectName;
        this.filterFieldName = filterFieldName;
        engine = new ListRequestEngine();
    }

    byte[] nextJson() {
        return engine.internalNextJson();
    }

    byte[] nextJson(String filter) {
        if (filterFieldName == null) {
            throw new IllegalArgumentException("Filter not supported.");
        }
        return engine.internalNextJson(filterFieldName, filter);
    }

    boolean hasMore() {
        return engine.hasMore();
    }
}
