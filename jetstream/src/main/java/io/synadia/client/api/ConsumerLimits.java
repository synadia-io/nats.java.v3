package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.nats.json.LazyJsonValueUtils.readNanosAsDuration;
import static io.synadia.client.utils.ApiConstants.INACTIVE_THRESHOLD;
import static io.synadia.client.utils.ApiConstants.MAX_ACK_PENDING;
import static io.synadia.client.utils.JetStreamApiUtils.UNSET;

/**
 * ConsumerLimits returned from the server.
 */
@NullMarked
public class ConsumerLimits extends LazyApiObject {

    @Nullable
    static ConsumerLimits optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new ConsumerLimits(v);
    }

    ConsumerLimits(LazyJsonValue v) {
        super(v);
    }

    /**
     * Maximum value for inactive_threshold for consumers of this stream.
     * Acts as a default when consumers do not set this value.
     * @return the inactive threshold limit
     */
    @Nullable
    public Duration getInactiveThreshold() {
        return readNanosAsDuration(ljv, INACTIVE_THRESHOLD);
    }

    /**
     * Maximum value for max_ack_pending for consumers of this stream.
     * Acts as a default when consumers do not set this value.
     * @return maximum ack pending limit
     */
    public long getMaxAckPending() {
        return readLong(ljv, MAX_ACK_PENDING, UNSET);
    }

    @Override
    public String toString() {
        return "ConsumerLimits " + ljv.toJson();
    }
}
