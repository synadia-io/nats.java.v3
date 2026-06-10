package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.time.Duration;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readDurationOrZero;
import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;

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
        return readStringOrEmpty(ljv, NAME);
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
        return readDurationOrZero(ljv, ACTIVE);
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
