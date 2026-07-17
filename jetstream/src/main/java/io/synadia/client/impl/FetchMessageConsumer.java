package io.synadia.client.impl;

import io.synadia.client.Message;

/**
 * A fetch consumer gets messages by calling nextMessage.
 * nextMessage returns null when there are no more messages
 */
public interface FetchMessageConsumer extends MessageConsumer {
    /**
     * Read the next message. Return null if the fetch has been fulfilled either
     * because max messages or bytes max bytes have been reached,
     * or because the fetch was not fulfilled in the timeout set by the fetch options.
     * @return the next message for this subscriber or null if there is a timeout
     * @throws InterruptedException if one is thrown, in order to propagate it up
     * @throws JetStreamStatusException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     */
    Message nextMessage() throws InterruptedException, JetStreamStatusException;
}
