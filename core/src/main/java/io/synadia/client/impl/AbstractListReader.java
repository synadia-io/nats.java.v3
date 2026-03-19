package io.synadia.client.impl;

import io.nats.json.JsonValue;
import io.synadia.client.JetStreamApiException;
import io.synadia.client.Message;

import java.util.List;

import static io.nats.json.JsonValueUtils.readArrayOrEmpty;

abstract class AbstractListReader {

    private final String objectName;
    private final String filterFieldName;
    protected ListRequestEngine engine;

    void process(Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        processItems(readArrayOrEmpty(engine.getJv(), objectName));
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
