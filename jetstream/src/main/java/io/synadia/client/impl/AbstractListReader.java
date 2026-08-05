package io.synadia.client.impl;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readArrayOrEmpty;

/**
 * Base for the readers that walk a paged JetStream list API. The caller alternates
 * {@link #nextJson()} to build the next request and {@link #process(Message)} to absorb the
 * response, until {@link #hasMore()} reports false.
 */
public abstract class AbstractListReader {

    private final String objectName;
    private final String filterFieldName;

    /** Tracks the paging offset and total across requests. */
    protected ListRequestEngine engine;

    /**
     * Absorb one page of the response, updating the paging state and handing the page's
     * items to {@link #processItems(List)}.
     * @param msg the server reply to the last request
     * @throws JetStreamApiException if the reply carries a JetStream API error
     */
    public void process(Message msg) throws JetStreamApiException {
        engine = new ListRequestEngine(msg);
        processItems(readArrayOrEmpty(engine.getSourceLazyJsonValue(), objectName));
    }

    /**
     * Accumulate one page of items. Called once per response, with an empty list when the
     * page held none.
     * @param items the raw JSON items from this page
     */
    public abstract void processItems(List<LazyJsonValue> items);

    /**
     * Construct a reader that does not support filtering.
     * @param objectName the JSON field holding the array of items in the response
     */
    protected AbstractListReader(@NonNull String objectName) {
        this(objectName, null);
    }

    /**
     * Construct a reader, optionally supporting a filter.
     * @param objectName the JSON field holding the array of items in the response
     * @param filterFieldName the JSON field carrying the filter in the request, null if the
     *                        API does not support filtering
     */
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

    /**
     * Whether the server reported more items beyond those already read. True before the
     * first request, since nothing has been read yet.
     * @return true if another request should be made
     */
    public boolean hasMore() {
        return engine.hasMore();
    }
}
