package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.testutils.ApiConstants.API;
import static io.synadia.client.testutils.ApiConstants.DELIVER;

/**
 * External configuration referencing a stream source in another account.
 * Returned from the server.
 */
@NullMarked
public class External extends LazyApiObject {

    @Nullable
    static External optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new External(v);
    }

    External(LazyJsonValue v) {
        super(v);
    }

    /**
     * The subject prefix that imports the other account <code>$JS.API.CONSUMER.&gt; subjects</code>
     * @return the api prefix
     */
    @Nullable
    public String getApi() {
        return readString(ljv, API);
    }

    /**
     * The delivery subject to use for the push consumer.
     * @return delivery subject
     */
    @Nullable
    public String getDeliver() {
        return readString(ljv, DELIVER);
    }

    @Override
    public String toString() {
        return "External " + ljv.toJson();
    }
}
