package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readArrayOrEmpty;

abstract class AbstractListReader {

    private final String objectName;
    private final String filterFieldName;
    protected ListRequestEngine engine;

    void process(Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        processItems(readArrayOrEmpty(engine.getSourceLazyJsonValue(), objectName));
    }

    abstract void processItems(List<LazyJsonValue> items);

    AbstractListReader(@NonNull String objectName) {
        this(objectName, null);
    }

    AbstractListReader(@NonNull String objectName, @Nullable String filterFieldName) {
        this.objectName = objectName;
        this.filterFieldName = filterFieldName;
        engine = new ListRequestEngine();
    }

    byte[] nextJson() {
        return engine.internalNextJson();
    }

    byte[] nextJson(@Nullable String filter) {
        if (filterFieldName == null) {
            throw new IllegalArgumentException("Filter not supported.");
        }
        return engine.internalNextJson(filterFieldName, filter);
    }

    boolean hasMore() {
        return engine.hasMore();
    }
}
