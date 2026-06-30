package io.synadia.client.impl;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NonNull;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;
import static io.synadia.client.utils.Validator.validateGtZero;

/**
 * The PullRequestOptions class specifies the options for pull requests
 */
public class PullRequestOptions implements JsonSerializable {

    private final int batchSize;
    private final long maxBytes;
    private final boolean noWait;
    private final long expiresIn;
    private final long idleHeartbeat;
    private final String group;
    private final int priority;
    private final long minPending;
    private final long minAckPending;

    /**
     * Construct PullRequestOptions from the builder
     * @param b the builder
     */
    public PullRequestOptions(Builder b) {
        this.batchSize = b.batchSize;
        this.maxBytes = b.maxBytes;
        this.noWait = b.noWait;
        this.expiresIn = b.expiresIn;
        this.idleHeartbeat = b.idleHeartbeat;
        this.group = b.group;
        this.priority = b.priority;
        this.minPending = b.minPending < 0 ? -1 : b.minPending;
        this.minAckPending = b.minAckPending < 0 ? -1 : b.minAckPending;
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, BATCH, batchSize);
        addField(sb, MAX_BYTES, maxBytes);
        addField(sb, NO_WAIT, noWait);
        addFieldWhenGtZero(sb, EXPIRES, expiresIn * NANOS_PER_MILLI);
        addFieldWhenGtZero(sb, IDLE_HEARTBEAT, idleHeartbeat * NANOS_PER_MILLI);
        addField(sb, GROUP, group);
        addFieldWhenGtZero(sb, PRIORITY, priority);
        addField(sb, ID, getPinId());
        addField(sb, MIN_PENDING, minPending);
        addField(sb, MIN_ACK_PENDING, minAckPending);
        return endJson(sb).toString();
    }

    protected String getPinId() {
        return null;
    }

    /**
     * Get the batch size option value
     * @return the batch size
     */
    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Get the max bytes size option value
     * @return the max bytes size
     */
    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Get the no wait flag value
     * @return the flag
     */
    public boolean isNoWait() {
        return noWait;
    }

    /**
     * Get the expires in option value in milliseconds
     * @return the expires in milliseconds, 0 if not set
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Get the idle heartbeat option value in milliseconds
     * @return the idle heartbeat milliseconds, 0 if not set
     */
    public long getIdleHeartbeat() {
        return idleHeartbeat;
    }

    /**
     * Get the group option
     * @return the group
     */
    public String getGroup() {
        return group;
    }

    /**
     * Get the priority
     * @return the priority
     */
    public int getPriority() { return priority; }

    /**
     * Get the min pending setting
     * @return the min pending
     */
    public long getMinPending() {
        return minPending;
    }

    /**
     * Get the min ack pending setting
     * @return the min ack setting
     */
    public long getMinAckPending() {
        return minAckPending;
    }

    /**
     * Creates a builder for the pull options, with batch size since it's always required
     * @param batchSize the size of the batch. Must be greater than 0
     * @return a pull options builder
     */
    public static Builder builder(int batchSize) {
        return new Builder().batchSize(batchSize);
    }

    /**
     * Creates a builder for the pull options, setting no wait to true and accepting batch size
     * @param batchSize the size of the batch. Must be greater than 0
     * @return a pull options builder
     */
    public static Builder noWait(int batchSize) {
        return new Builder().batchSize(batchSize).noWait();
    }

    /**
     * The builder for PullRequestOptions
     */
    public static class Builder {
        private int batchSize;
        private long maxBytes;
        private boolean noWait;
        private long expiresIn;
        private long idleHeartbeat;
        private String group;
        private int priority;
        private long minPending = -1;
        private long minAckPending = -1;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the batch size for the pull
         * @param batchSize the size of the batch. Must be greater than 0
         * @return the builder
         */
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        /**
         * The maximum bytes for the pull
         * @param maxBytes the maximum bytes
         * @return the builder
         */
        public Builder maxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }

        /**
         * Set no wait to true
         * @return the builder
         */
        public Builder noWait() {
            this.noWait = true;
            return this;
        }

        /**
         * Set the no wait flag
         * @param noWait the flag
         * @return the builder
         */
        public Builder noWait(boolean noWait) {
            this.noWait = noWait;
            return this;
        }

        /**
         * Set the expires time in millis
         * @param millis the millis
         * @return the builder
         */
        public Builder expiresIn(long millis) {
            this.expiresIn = millis <= 0 ? 0 : millis;
            return this;
        }

        /**
         * Set the idle heartbeat time in millis
         * @param millis the millis
         * @return the builder
         */
        public Builder idleHeartbeat(long millis) {
            this.idleHeartbeat = millis <= 0 ? 0 : millis;
            return this;
        }

        /**
         * Sets the group
         * Replaces any other groups set in the builder
         * @param group the priority group for this pull
         * @return Builder
         */
        public Builder group(String group) {
            this.group = group;
            return this;
        }

        /**
         * Sets the priority within the group. Priority must be between 0 and 9 inclusive.
         * @param priority the priority
         * @return Builder
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * When specified, the pull request will only receive messages when the consumer has at least this many pending messages.
         * @param minPending the min pending
         * @return the builder
         */
        public Builder minPending(long minPending) {
            this.minPending = minPending < 1 ? -1 : minPending;
            return this;
        }

        /**
         * When specified, this Pull request will only receive messages when the consumer has at least this many ack pending messages.
         * @param minAckPending the min ack pending
         * @return the builder
         */
        public Builder minAckPending(long minAckPending) {
            this.minAckPending = minAckPending < 1 ? -1 : minAckPending;
            return this;
        }

        /**
         * Build the PullRequestOptions.
         * <p>Validates that the batch size is greater than 0</p>
         * <p>If supplied, validates that the idle heartbeat is valid for the expiration</p>
         * @return the built PullRequestOptions
         */
        public PullRequestOptions build() {
            validateGtZero(batchSize, "Pull batch size");
            if (priority < 0 || priority > 9) {
                throw new IllegalArgumentException("Priority must be between 0 and 9 inclusive.");
            }
            if (idleHeartbeat > 0) {
                if (expiresIn <= 0) {
                    throw new IllegalArgumentException("Idle Heartbeat not allowed without expiration.");
                }
                if (idleHeartbeat * 2 > expiresIn) {
                    throw new IllegalArgumentException("Idle Heartbeat cannot be more than half the expiration.");
                }
            }
            return new PullRequestOptions(this);
        }
    }
}
