package io.synadia.client.impl;

/**
 * The BaseMessageConsumer interface is the core interface replacing
 * a subscription for a simplified consumer.
 */
public interface BaseMessageConsumer extends AutoCloseable {
    /**
     * Stop the MessageConsumer from asking for any more messages from the server.
     * The consumer will finish all pull request already in progress, but will not start any new ones.
     */
    void stop();
}
