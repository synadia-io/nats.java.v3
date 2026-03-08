package io.nats.client.api;

import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.client.support.ApiConstants.*;
import static io.nats.client.support.JsonValueUtils.readString;

/**
 * The Stream Alternate
 */
public class StreamAlternate {
    private final String name;
    private final String domain;
    private final String cluster;

    static List<StreamAlternate> optionalListOf(JsonValue vSourceInfos) {
        return JsonValueUtils.optionalListOf(vSourceInfos, StreamAlternate::new);
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
