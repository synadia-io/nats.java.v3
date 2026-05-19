package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.impl.JetStreamApiUtils.mapToList;
import static io.synadia.client.utils.ApiConstants.*;

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
        String name = readString(ljv, NAME);
        if (name == null) {
            throw new IllegalStateException("StreamAlternate does not have required name.");
        }
        return name;
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
        String cluster = readString(ljv, CLUSTER);
        if (cluster == null) {
            throw new IllegalStateException("StreamAlternate does not have required cluster name.");
        }
        return cluster;
    }

    @Override
    public String toString() {
        return "StreamAlternate " + ljv.toJson();
    }
}
