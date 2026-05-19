package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * Republish Configuration returned from the server.
 */
@NullMarked
public class Republish extends LazyApiObject {

    @Nullable
    static Republish optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new Republish(v);
    }

    Republish(LazyJsonValue v) {
        super(v);
    }

    /**
     * Get source, the Published subject matching filter
     * @return the source
     */
    public String getSource() {
        //noinspection DataFlowIssue
        return readString(ljv, SRC);
    }

    /**
     * Get destination, the RePublish Subject template
     * @return the destination
     */
    public String getDestination() {
        //noinspection DataFlowIssue
        return readString(ljv, DEST);
    }

    /**
     * Get headersOnly, Whether to RePublish only headers (no body)
     * @return headersOnly
     */
    public boolean isHeadersOnly() {
        return readBoolean(ljv, HEADERS_ONLY, false);
    }

    @Override
    public String toString() {
        return "Republish " + ljv.toJson();
    }
}
