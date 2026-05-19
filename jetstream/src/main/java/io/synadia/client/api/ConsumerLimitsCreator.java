package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.impl.JetStreamApiUtils.*;
import static io.synadia.client.utils.ApiConstants.INACTIVE_THRESHOLD;
import static io.synadia.client.utils.ApiConstants.MAX_ACK_PENDING;

/**
 * ConsumerLimitsCreator is used to create a ConsumerLimits configuration for use in a StreamCreator.
 */
@NullMarked
public class ConsumerLimitsCreator implements JsonSerializable {
    private @Nullable Duration inactiveThreshold;
    private long maxAckPending = UNSET;

    /**
     * Construct an empty ConsumerLimitsCreator
     */
    public ConsumerLimitsCreator() {}

    /**
     * Construct a ConsumerLimitsCreator from a ConsumerLimits (server response)
     * @param cl the consumer limits to copy from
     */
    ConsumerLimitsCreator(ConsumerLimits cl) {
        this.inactiveThreshold = cl.getInactiveThreshold();
        this.maxAckPending = cl.getMaxAckPending();
    }

    /**
     * Sets the amount of time before the consumer is deemed inactive.
     * @param inactiveThreshold the threshold duration
     * @return this instance for chaining
     */
    public ConsumerLimitsCreator inactiveThreshold(@Nullable Duration inactiveThreshold) {
        this.inactiveThreshold = normalizeDuration(inactiveThreshold, null);
        return this;
    }

    /**
     * Sets the amount of time before the consumer is deemed inactive.
     * @param inactiveThresholdMillis the threshold duration in milliseconds
     * @return this instance for chaining
     */
    public ConsumerLimitsCreator inactiveThreshold(long inactiveThresholdMillis) {
        this.inactiveThreshold = normalizeDuration(inactiveThresholdMillis, null);
        return this;
    }

    /**
     * Sets the maximum ack pending.
     * @param maxAckPending maximum pending acknowledgements.
     * @return this instance for chaining
     */
    public ConsumerLimitsCreator maxAckPending(long maxAckPending) {
        this.maxAckPending = normalizeLong(maxAckPending, STANDARD_MIN);
        return this;
    }

    /**
     * Maximum value for inactive_threshold for consumers of this stream.
     * @return the inactive threshold limit
     */
    @Nullable
    public Duration getInactiveThreshold() {
        return inactiveThreshold;
    }

    /**
     * Maximum value for max_ack_pending for consumers of this stream.
     * @return maximum ack pending limit
     */
    public long getMaxAckPending() {
        return maxAckPending;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addFieldAsNanos(sb, INACTIVE_THRESHOLD, inactiveThreshold);
        addField(sb, MAX_ACK_PENDING, maxAckPending);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ConsumerLimitsCreator " + toJson();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof ConsumerLimitsCreator that)) return false;
        return maxAckPending == that.maxAckPending
            && Objects.equals(inactiveThreshold, that.inactiveThreshold);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(inactiveThreshold);
        result = 31 * result + Long.hashCode(maxAckPending);
        return result;
    }
}
