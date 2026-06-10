package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.impl.JetStreamApiUtils.mapToList;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;

/**
 * The Stream Alternate
 */
@NullMarked
public class StreamAlternate extends LazyApiObject {

    static List<StreamAlternate> listOf(@Nullable LazyJsonValue v) {
        return mapToList(v, StreamAlternate::new);
    }

    StreamAlternate(LazyJsonValue v) {
        super(v);
    }

    /**
     * The mirror stream name
     * @return the name
     */
    public String getName() {
        return readStringOrEmpty(ljv, NAME);
    }

    /**
     * The domain
     * @return the domain
     */
    @Nullable
    public String getDomain() {
        return readString(ljv, DOMAIN);
    }

    /**
     * The name of the cluster holding the stream
     * @return the cluster
     */
    public String getCluster() {
        return readStringOrEmpty(ljv, CLUSTER);
    }

    @Override
    public String toString() {
        return "StreamAlternate " + ljv.toJson();
    }
}
