package io.synadia.client.api;

import io.nats.json.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.JsonValueUtils.listOfOrNull;
import static io.nats.json.JsonValueUtils.readString;
import static io.synadia.client.support.ApiConstants.*;

/**
 * The Stream Alternate
 */
public class StreamAlternate {
    private final String name;
    private final String domain;
    private final String cluster;

    static List<StreamAlternate> optionalListOf(JsonValue vSourceInfos) {
        return listOfOrNull(vSourceInfos, StreamAlternate::new);
    }

    StreamAlternate(JsonValue vLost) {
        name = readString(vLost, NAME);
        domain = readString(vLost, DOMAIN);
        cluster = readString(vLost, CLUSTER);
    }

    /**
     * The mirror stream name
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * The domain
     * @return the domain
     */
    @Nullable
    public String getDomain() {
        return domain;
    }

    /**
     * The name of the cluster holding the stream
     * @return the cluster
     */
    @NonNull
    public String getCluster() {
        return cluster;
    }
}
