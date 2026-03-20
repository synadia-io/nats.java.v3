package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

import static io.nats.json.JsonValueUtils.readInteger;
import static io.nats.json.JsonValueUtils.readNanosAsDuration;
import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.api.ConsumerConfiguration.*;
import static io.synadia.client.support.ApiConstants.INACTIVE_THRESHOLD;
import static io.synadia.client.support.ApiConstants.MAX_ACK_PENDING;

/**
 * ConsumerLimits
 */
public class ConsumerLimits implements JsonSerializable {
    private final Duration inactiveThreshold;
    private final Integer maxAckPending;

    static ConsumerLimits optionalInstance(JsonValue vConsumerLimits) {
        return vConsumerLimits == null ? null : new ConsumerLimits(vConsumerLimits);
    }

    ConsumerLimits(JsonValue vConsumerLimits) {
        inactiveThreshold = readNanosAsDuration(vConsumerLimits, INACTIVE_THRESHOLD);
        maxAckPending = readInteger(vConsumerLimits, MAX_ACK_PENDING);
    }

    ConsumerLimits(ConsumerLimits.Builder b) {
        this.inactiveThreshold = b.inactiveThreshold;
        this.maxAckPending = b.maxAckPending;
    }

    /**
     * Maximum value for inactive_threshold for consumers of this stream. Acts as a default when consumers do not set this value.
     * @return the inactive threshold limit
     */
    @Nullable
    public Duration getInactiveThreshold() {
        return inactiveThreshold;
    }

    /**
     * Maximum value for max_ack_pending for consumers of this stream. Acts as a default when consumers do not set this value.
     * @return maximum ack pending limit
     */
    public long getMaxAckPending() {
        return getOrUnset(maxAckPending);
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addFieldAsNanos(sb, INACTIVE_THRESHOLD, inactiveThreshold);
        addField(sb, MAX_ACK_PENDING, maxAckPending);
        return endJson(sb).toString();
    }

    /**
     * Creates a builder for a consumer limits object.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ConsumerLimits can be created using a Builder.
     */
    public static class Builder {
        private Duration inactiveThreshold;
        private Integer maxAckPending;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * sets the amount of time before the consumer is deemed inactive.
         * @param inactiveThreshold the threshold duration
         * @return Builder
         */
        public Builder inactiveThreshold(Duration inactiveThreshold) {
            this.inactiveThreshold = normalize(inactiveThreshold);
            return this;
        }

        /**
         * sets the amount of time before the consumer is deemed inactive.
         * @param inactiveThreshold the threshold duration in milliseconds
         * @return Builder
         */
        public Builder inactiveThreshold(long inactiveThreshold) {
            this.inactiveThreshold = normalizeDuration(inactiveThreshold);
            return this;
        }

        /**
         * Sets the maximum ack pending or null to unset / clear.
         * @param maxAckPending maximum pending acknowledgements.
         * @return Builder
         */
        public Builder maxAckPending(Long maxAckPending) {
            this.maxAckPending = normalize(maxAckPending, STANDARD_MIN);
            return this;
        }

        /**
         * Sets the maximum ack pending.
         * @param maxAckPending maximum pending acknowledgements.
         * @return Builder
         */
        public Builder maxAckPending(long maxAckPending) {
            this.maxAckPending = normalize(maxAckPending, STANDARD_MIN);
            return this;
        }

        /**
         * Build a ConsumerLimits object
         * @return the ConsumerLimits
         */
        public ConsumerLimits build() {
            return new ConsumerLimits(this);
        }
    }
}
