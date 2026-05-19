package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * PullConsumerCreator helps you create a pull Consumer with durable support.
 */
@NullMarked
public class PullConsumerCreator extends AbstractEphemeralConsumerCreator<PullConsumerCreator> {

    /**
     * Construct the creator
     * @param stream the stream name
     */
    public PullConsumerCreator(String stream) {
        super(stream, false);
    }

    public PullConsumerCreator(PullConsumerCreator creator) {
        super(creator);
    }

    // ----------------------------------------------------------------------------------------------------
    // Durable
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the name of the durable consumer.
     * Null or empty clears the field.
     * @param durable name of the durable consumer.
     * @return this instance for chaining.
     */
    public PullConsumerCreator durable(@Nullable String durable) {
        _durable(durable);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Pull-specific
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the max amount of expire time for the server to allow on pull requests.
     * @param maxExpires the max expire duration
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxExpires(@Nullable Duration maxExpires) {
        _maxExpires(maxExpires);
        return this;
    }

    /**
     * Sets the max amount of expire time for the server to allow on pull requests.
     * @param maxExpires the max expire duration in milliseconds
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxExpires(long maxExpires) {
        _maxExpires(maxExpires);
        return this;
    }

    /**
     * Sets the max pull waiting, the number of pulls that can be outstanding on a pull consumer, pulls received after this is reached are ignored.
     * Use null to unset / clear.
     * @param maxPullWaiting the max pull waiting
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxPullWaiting(@Nullable Long maxPullWaiting) {
        _maxPullWaiting(maxPullWaiting);
        return this;
    }

    /**
     * Sets the max pull waiting, the number of pulls that can be outstanding on a pull consumer, pulls received after this is reached are ignored.
     * @param maxPullWaiting the max pull waiting
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxPullWaiting(long maxPullWaiting) {
        _maxPullWaiting(maxPullWaiting);
        return this;
    }

    /**
     * Sets the max batch size for the server to allow on pull requests.
     * @param maxBatch the max batch size
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBatch(@Nullable Long maxBatch) {
        _maxBatch(maxBatch);
        return this;
    }

    /**
     * Sets the max batch size for the server to allow on pull requests.
     * @param maxBatch the max batch size
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBatch(long maxBatch) {
        _maxBatch(maxBatch);
        return this;
    }

    /**
     * Sets the max bytes size for the server to allow on pull requests.
     * @param maxBytes the max bytes size
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBytes(@Nullable Long maxBytes) {
        _maxBytes(maxBytes);
        return this;
    }

    /**
     * Sets the max bytes size for the server to allow on pull requests.
     * @param maxBytes the max bytes size
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBytes(long maxBytes) {
        _maxBytes(maxBytes);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Priority (pull-only)
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the priority groups.
     * Replaces any other priority groups set in the builder
     * @param priorityGroups one or more priority groups
     * @return this instance for chaining.
     */
    public PullConsumerCreator priorityGroups(String... priorityGroups) {
        _priorityGroups(priorityGroups);
        return this;
    }

    /**
     * Sets the priority groups.
     * Replaces any other priority groups set in the builder
     * @param priorityGroups the list of priority groups
     * @return this instance for chaining.
     */
    public PullConsumerCreator priorityGroups(@Nullable List<String> priorityGroups) {
        _priorityGroups(priorityGroups);
        return this;
    }

    /**
     * Sets the priority policy
     * @param policy the priority policy.
     * @return this instance for chaining.
     */
    public PullConsumerCreator priorityPolicy(@Nullable PriorityPolicy policy) {
        _priorityPolicy(policy);
        return this;
    }

    /**
     * Sets the priority policy timeout
     * @param priorityTimeout the timeout
     * @return this instance for chaining.
     */
    public PullConsumerCreator priorityTimeout(@Nullable Duration priorityTimeout) {
        _priorityTimeout(priorityTimeout);
        return this;
    }

    /**
     * Sets the priority policy timeout
     * @param priorityTimeoutMillis the timeout in milliseconds
     * @return this instance for chaining.
     */
    public PullConsumerCreator priorityTimeout(long priorityTimeoutMillis) {
        _priorityTimeout(priorityTimeoutMillis);
        return this;
    }

    @Override
    public String toString() {
        return "PullConsumerCreator " + toJson();
    }
}
