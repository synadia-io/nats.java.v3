package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readArrayOrEmpty;

public abstract class AbstractListReader {

    private final String objectName;
    private final String filterFieldName;
    protected ListRequestEngine engine;

    public void process(Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        processItems(readArrayOrEmpty(engine.getSourceLazyJsonValue(), objectName));
    }

    public abstract void processItems(List<LazyJsonValue> items);

    protected AbstractListReader(@NonNull String objectName) {
        this(objectName, null);
    }

    protected AbstractListReader(@NonNull String objectName, @Nullable String filterFieldName) {
        this.objectName = objectName;
        this.filterFieldName = filterFieldName;
        engine = new ListRequestEngine();
    }

    protected byte[] nextJson() {
        return engine.internalNextJson();
    }

    protected byte[] nextJson(@Nullable String filter) {
        if (filterFieldName == null) {
            throw new IllegalArgumentException("Filter not supported.");
        }
        return engine.internalNextJson(filterFieldName, filter);
    }

    public boolean hasMore() {
        return engine.hasMore();
    }
}
