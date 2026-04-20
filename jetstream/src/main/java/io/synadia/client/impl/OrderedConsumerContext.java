package io.synadia.client.impl;

import org.jspecify.annotations.Nullable;

/**
 * The Ordered Consumer and it's context provide a simplification interface to the ordered consumer behavior.
 */
public interface OrderedConsumerContext extends BaseConsumerContext {
    /**
     * Gets the consumer name created for the underlying Ordered Consumer
     * This will return null until the first consume (next, iterate, fetch, consume)
     * is executed because the JetStream consumer, which carries the name,
     * has not been created yet.
     * <p>
     * The consumer name is subject to change for 2 reasons.
     * 1. Any time next(...) is called
     * 2. Anytime a message is received out of order for instance because of a disconnection
     * </p>
     * <p>If your PullOrderedConsumerCreator has a consumerNamePrefix,
     * the consumer name will always start with the prefix
     * </p>
     * @return the consumer name or null
     */
    @Override
    @Nullable
    String getConsumerName();
}
