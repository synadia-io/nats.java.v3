package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Server peer information
 */
@NullMarked
abstract class PeerInfo extends LazyApiObject {

    protected PeerInfo(LazyJsonValue v) {
        super(v);
    }

    /**
     * The server name of the peer
     * @return the name
     */
    public String getName() {
        //noinspection DataFlowIssue
        return readString(ljv, NAME);
    }

    /**
     * Indicates if the server is up-to-date and synchronized
     * @return if is current
     */
    public boolean isCurrent() {
        return readBoolean(ljv, CURRENT, false);
    }

    /**
     * Indicates the node is considered offline by the group
     * @return if is offline
     */
    public boolean isOffline() {
        return readBoolean(ljv, OFFLINE, false);
    }

    /**
     * Time since this peer was last seen
     * @return the active time
     */
    public Duration getActive() {
        Duration d = readNanosAsDuration(ljv, ACTIVE);
        return d == null ? Duration.ZERO : d;
    }

    /**
     * How many uncommitted operations this peer is behind the leader
     * @return the lag
     */
    public long getLag() {
        return readLong(ljv, LAG, 0);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + ljv.toJson();
    }
}
