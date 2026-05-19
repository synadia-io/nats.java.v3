package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.synadia.client.impl.JetStreamApiUtils.mapToList;

/**
 * Replica (Peer Info)
 */
@NullMarked
public class Replica extends PeerInfo {
    static List<Replica> listOf(@Nullable LazyJsonValue v) {
        return mapToList(v, Replica::new);
    }

    Replica(LazyJsonValue ljv) {
        super(ljv);
    }
}
