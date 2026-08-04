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
     */
    public PullConsumerCreator() {
        super(false);
    }

    /**
     * Copy constructor.
     * @param creator the creator to copy
     */
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
     * @param maxExpiresMillis the max expire duration in milliseconds
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxExpires(long maxExpiresMillis) {
        _maxExpires(maxExpiresMillis);
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
     * Sets the max batch size, the largest batch a single pull request may specify on this consumer.
     * <p>
     * Server semantics: {@code 0} means no per-consumer cap, so a pull request may ask for any batch
     * size. When this is {@code 0} and account or stream JetStream limits define a {@code MaxRequestBatch},
     * the server inherits that value for the consumer. Negative values are rejected by the server and
     * are normalized to unset by this client.
     * @param maxBatch the max batch size; {@code 0} disables the per-consumer cap
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBatch(long maxBatch) {
        _maxBatch(maxBatch);
        return this;
    }

    /**
     * Sets the max bytes size, the largest byte count a single pull request may specify on this consumer.
     * <p>
     * Server semantics: {@code 0} means no per-consumer cap, so a pull request may ask for any number
     * of bytes. Negative values are rejected by the server and are normalized to unset by this client.
     * {@code null} is treated as unset.
     * @param maxBytes the max bytes size; {@code 0} or {@code null} disables the per-consumer cap
     * @return this instance for chaining.
     */
    public PullConsumerCreator maxBytes(@Nullable Long maxBytes) {
        _maxBytes(maxBytes);
        return this;
    }

    /**
     * Sets the max bytes size, the largest byte count a single pull request may specify on this consumer.
     * <p>
     * Server semantics: {@code 0} means no per-consumer cap, so a pull request may ask for any number
     * of bytes. Negative values are rejected by the server and are normalized to unset by this client.
     * @param maxBytes the max bytes size; {@code 0} disables the per-consumer cap
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
