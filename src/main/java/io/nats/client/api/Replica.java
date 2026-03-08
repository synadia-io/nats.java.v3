package io.nats.client.api;

import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;

import java.util.List;

/**
 * Replica Peer Info
 */
public class Replica extends PeerInfo {

    static List<Replica> optionalListOf(JsonValue vReplicas) {
        return JsonValueUtils.optionalListOf(vReplicas, Replica::new);
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
