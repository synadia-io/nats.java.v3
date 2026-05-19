package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.nats.json.LazyJsonValueUtils.readStringListOrNull;
import static io.synadia.client.utils.ApiConstants.CLUSTER;
import static io.synadia.client.utils.ApiConstants.TAGS;

/**
 * Placement directives returned from the server.
 */
@NullMarked
public class Placement extends LazyApiObject {

    @Nullable
    static Placement optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new Placement(v);
    }

    Placement(LazyJsonValue v) {
        super(v);
    }

    /**
     * Whether the Placement has either a cluster or tags
     * @return true if the Placement has data
     */
    public boolean hasData() {
        return getCluster() != null || getTags() != null;
    }

    /**
     * The desired cluster name to place the stream.
     * @return The cluster name
     */
    @Nullable
    public String getCluster() {
        String c = readString(ljv, CLUSTER);
        return c == null || c.isEmpty() ? null : c;
    }

    /**
     * Tags required on servers hosting this stream
     * @return the list of tags
     */
    @Nullable
    public List<String> getTags() {
        return readStringListOrNull(ljv, TAGS);
    }

    @Override
    public String toString() {
        return "Placement " + ljv.toJson();
    }
}
