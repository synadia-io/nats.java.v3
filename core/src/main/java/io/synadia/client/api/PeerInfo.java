package io.synadia.client.api;

import io.nats.json.JsonValue;
import org.jspecify.annotations.NonNull;

import java.time.Duration;

import static io.nats.json.JsonValueUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Server peer information
 */
public class PeerInfo {

    private final String name;
    private final boolean current;
    private final boolean offline;
    private final Duration active;
    private final long lag;

    PeerInfo(JsonValue vPeerInfo) {
        name = readString(vPeerInfo, NAME);
        current = readBoolean(vPeerInfo, CURRENT, false);
        offline = readBoolean(vPeerInfo, OFFLINE, false);
        active = readNanosAsDuration(vPeerInfo, ACTIVE, Duration.ZERO);
        lag = readLong(vPeerInfo, LAG, 0);
    }

    /**
     * The server name of the peer
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Indicates if the server is up-to-date and synchronised
     * @return if is current
     */
    public boolean isCurrent() {
        return current;
    }

    /**
     * Indicates the node is considered offline by the group
     * @return if is offline
     */
    public boolean isOffline() {
        return offline;
    }

    /**
     * Time since this peer was last seen
     * @return the active time
     */
    @NonNull
    public Duration getActive() {
        return active;
    }

    /**
     * How many uncommitted operations this peer is behind the leader
     * @return the lag
     */
    public long getLag() {
        return lag;
    }
}
