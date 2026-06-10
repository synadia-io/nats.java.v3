package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.math.BigInteger;
import java.time.Duration;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.*;

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
     * How many uncommitted operations this peer is behind the leader.
     * <p>The server value is an unsigned 64-bit number.
     * @return the lag
     */
    public long getLag() {
        return readUnsignedLongOrZero(ljv, LAG);
    }

    /**
     * How many uncommitted operations this peer is behind the leader, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getLag()}.
     * @return the lag, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getLagAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, LAG);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + ljv.toJson();
    }
}
