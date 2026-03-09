package io.synadia.client;

import io.synadia.client.api.ConsumerInfo;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * The Consumer Context provides a convenient interface around a defined JetStream Consumer
 * <p> Note: ConsumerContext requires a <b>pull consumer</b>.
 * <p> For basic usage examples see {@link JetStream JetStream}
 */
public interface ConsumerContext extends BaseConsumerContext {
    /**
     * Gets the current information about the consumer behind this subscription
     * by making a call to the server.
     * @return consumer information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    @NonNull
    ConsumerInfo getConsumerInfo() throws IOException, JetStreamApiException;

    /**
     * Gets information about the consumer behind this subscription.
     * This returns the last read version of Consumer Info, which could technically be out of date.
     * Some implementations do not guarantee this being set
     * @return consumer information
     */
    @Nullable
    ConsumerInfo getCachedConsumerInfo();
}
