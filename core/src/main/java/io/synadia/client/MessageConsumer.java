package io.synadia.client;

import io.synadia.client.jsapi.ConsumerInfo;

import java.io.IOException;

/**
 * The MessageConsumer interface is the core interface replacing
 * a subscription for a simplified consumer.
 */
public interface MessageConsumer extends AutoCloseable {
    /**
     * Gets the consumer name associated with the subscription.
     * For simplified consumers, this value can be null unless
     * the consumer info was manually read via {@link #getConsumerInfo()}.
     * @return the consumer name
     */
    String getConsumerName();

    /**
     * Gets information about the consumer behind this subscription.
     * @return consumer information
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    ConsumerInfo getConsumerInfo() throws IOException, JetStreamApiException;

    /**
     * Gets information about the consumer behind this subscription.
     * This returns the last read version of Consumer Info,
     * which could be null or out of date.
     * @return consumer information
     */
    ConsumerInfo getCachedConsumerInfo();

    /**
     * Use {@link #close()} to unsubscribe. Stop will not unsubcribe or clean up resources.
     * The consumer will finish all pull requests already in progress, but will not start any new ones.
     */
    void stop();

    /**
     * Unsubscribe the underlying subject. Close will be lenient. In flight and buffered messages may still be delivered.
     */
    @Override
	void close() throws Exception;

    /**
     * Stopped indicates whether consuming has been stopped. Can be stopped without being finished.
     * @return the stopped flag
     */
    boolean isStopped();

    /**
     * Finish indicates all messages have been received from the server. Can be finished without being stopped.
     * @return the finished flag
     */
    boolean isFinished();
}
