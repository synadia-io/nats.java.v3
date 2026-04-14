package io.synadia.client.jsapi;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static io.synadia.client.support.JetStreamApiUtils.mapToList;

/**
 * Replica (Peer Info)
 */
@NullMarked
public class Replica extends PeerInfo {
    static List<Replica> listOf(LazyJsonValue v) {
        return mapToList(v, Replica::new);
    }

    Replica(LazyJsonValue ljv) {
        super("Replica", ljv);
    }
}
