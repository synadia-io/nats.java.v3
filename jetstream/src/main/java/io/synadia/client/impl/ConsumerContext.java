package io.synadia.client.impl;

import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerInfo retrieveConsumerInfo() throws JetStreamException, InterruptedException;

    /**
     * Gets information about the consumer behind this subscription.
     * This returns the last read version of Consumer Info, which could technically be out of date.
     * Some implementations do not guarantee this being set
     * @return consumer information
     */
    @Nullable
    ConsumerInfo getCachedConsumerInfo();
}
