package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * The PullOrderedConsumerCreator class specifies the configuration for creating an ordered JetStream consumer
 */
@NullMarked
public class PullOrderedConsumerCreator extends AbstractOrderedConsumerCreator<PullOrderedConsumerCreator> {

    /**
     * PullOrderedConsumerCreator works like a builder.
     * It supports chaining and will create a default set of options if
     * no methods are calls, including setting the filter subject to &gt;
     */
    public PullOrderedConsumerCreator() {
        super(false);
    }

    public PullOrderedConsumerCreator(PullOrderedConsumerCreator creator, long lastStreamSeq, @Nullable Long inactiveThreshold) {
        super(creator, lastStreamSeq, inactiveThreshold);
    }

    // ----------------------------------------------------------------------------------------------------
    // Pull Specific ConsumerFields
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the max amount of expire time for the server to allow on pull requests.
     * @param maxExpires the max expire duration
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxExpires(@Nullable Duration maxExpires) {
        _maxExpires(maxExpires);
        return this;
    }

    /**
     * Sets the max amount of expire time for the server to allow on pull requests.
     * @param maxExpires the max expire duration in milliseconds
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxExpires(long maxExpires) {
        _maxExpires(maxExpires);
        return this;
    }

    /**
     * Sets the max pull waiting, the number of pulls that can be outstanding on a pull consumer, pulls received after this is reached are ignored.
     * @param maxPullWaiting the max pull waiting
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxPullWaiting(long maxPullWaiting) {
        _maxPullWaiting(maxPullWaiting);
        return this;
    }

    /**
     * Sets the max batch size for the server to allow on pull requests.
     * @param maxBatch the max batch size
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxBatch(long maxBatch) {
        _maxBatch(maxBatch);
        return this;
    }

    /**
     * Sets the max bytes size for the server to allow on pull requests.
     * @param maxBytes the max bytes size
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxBytes(@Nullable Long maxBytes) {
        _maxBytes(maxBytes);
        return this;
    }

    /**
     * Sets the max bytes size for the server to allow on pull requests.
     * @param maxBytes the max bytes size
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator maxBytes(long maxBytes) {
        _maxBytes(maxBytes);
        return this;
    }

    /**
     * Sets the priority groups.
     * Replaces any other priority groups set in the builder
     * @param priorityGroups one or more priority groups
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator priorityGroups(String... priorityGroups) {
        _priorityGroups(priorityGroups);
        return this;
    }

    /**
     * Sets the priority groups.
     * Replaces any other priority groups set in the builder
     * @param priorityGroups the list of priority groups
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator priorityGroups(@Nullable List<String> priorityGroups) {
        _priorityGroups(priorityGroups);
        return this;
    }

    /**
     * Sets the priority policy
     * @param policy the priority policy.
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator priorityPolicy(@Nullable PriorityPolicy policy) {
        _priorityPolicy(policy);
        return this;
    }

    /**
     * Sets the priority policy timeout
     * @param priorityTimeout the timeout
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator priorityTimeout(@Nullable Duration priorityTimeout) {
        _priorityTimeout(priorityTimeout);
        return this;
    }

    /**
     * Sets the priority policy timeout
     * @param priorityTimeoutMillis the timeout in milliseconds
     * @return this instance for chaining.
     */
    public PullOrderedConsumerCreator priorityTimeout(long priorityTimeoutMillis) {
        _priorityTimeout(priorityTimeoutMillis);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return "PullOrderedConsumerCreator " + toJson();
    }
}
