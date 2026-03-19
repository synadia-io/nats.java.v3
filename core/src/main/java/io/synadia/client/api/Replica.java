package io.synadia.client.api;

import io.nats.json.JsonValue;

import java.util.List;

import static io.nats.json.JsonValueUtils.listOfOrNull;

/**
 * Replica Peer Info
 */
public class Replica extends PeerInfo {

    static List<Replica> optionalListOf(JsonValue vReplicas) {
        return listOfOrNull(vReplicas, Replica::new);
    }

    Replica(JsonValue vReplica) {
        super(vReplica);
    }

    @Override
    public String toString() {
        return "Replica{" +
            "name='" + getName() + '\'' +
            ", current=" + isCurrent() +
            ", offline=" + isOffline() +
            ", active=" + getActive() +
            ", lag=" + getLag() +
            '}';
    }
}
