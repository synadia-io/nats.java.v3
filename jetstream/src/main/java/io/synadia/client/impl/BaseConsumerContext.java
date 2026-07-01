package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * The Consumer Context provides a convenient interface around a defined JetStream Consumer
 * <p> Note: ConsumerContext requires a <b>pull consumer</b>.
 * <p>Methods validate their arguments and throw {@link IllegalArgumentException} for invalid values.
 */
public interface BaseConsumerContext {
    /**
     * Gets the consumer name that was used to create the context.
     * Some implementations do not guarantee that the name is available.
     * @return the consumer name
     */
    @Nullable
    String getConsumerName();

    /**
     * Read the next message with max wait set to {@value BaseConsumeOptions#DEFAULT_EXPIRES_IN_MILLIS} ms
     * @return the next message or null if the max wait expires
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws InterruptedException if one is thrown, to propagate it up
     * @throws JetStreamStatusCheckedException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     * @throws JetStreamApiException the request had an error related to the data
     */
    @Nullable
    Message next() throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException;

    /**
     * Read the next message with provided max wait
     * @param maxWait the max wait value in milliseconds. Cannot be less than {@value BaseConsumeOptions#MIN_EXPIRES_MILLS} milliseconds.
     * @return the next message or null if the max wait expires
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws InterruptedException if one is thrown, to propagate it up
     * @throws JetStreamStatusCheckedException an exception representing a status that requires attention,
     *         such as the consumer was deleted on the server in the middle of use.
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if maxWait is positive and less than {@value BaseConsumeOptions#MIN_EXPIRES_MILLS}
     */
    @Nullable
    Message next(long maxWait) throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException;

    /**
     * Start a one use Fetch Consumer using all defaults other than the number of messages. See {@link FetchMessageConsumer}
     * @param maxMessages the maximum number of messages to consume
     * @return the FetchMessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    @NonNull
    FetchMessageConsumer fetchMessages(int maxMessages) throws IOException, JetStreamApiException;

    /**
     * Start a one use Fetch Consumer using all defaults other than the number of bytes. See {@link FetchMessageConsumer}
     * @param maxBytes the maximum number of bytes to consume
     * @return the FetchMessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    @NonNull
    FetchMessageConsumer fetchBytes(int maxBytes) throws IOException, JetStreamApiException;

    /**
     * Start a one-use Fetch Consumer with custom FetchConsumeOptions. See {@link FetchConsumeOptions}
     * @param fetchConsumeOptions the custom fetch consume options.
     * @return the FetchMessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the fetch consume options are null
     */
    @NonNull
    FetchMessageConsumer fetch(@NonNull FetchConsumeOptions fetchConsumeOptions) throws IOException, JetStreamApiException;

    /**
     * Start a long-running IterableMessageConsumer with default ConsumeOptions. See {@link IterableMessageConsumer} and {@link ConsumeOptions}
     * IterableMessageConsumer require the developer calls nextMessage.
     * @return the IterableMessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    @NonNull
    IterableMessageConsumer iterate() throws IOException, JetStreamApiException;

    /**
     * Start a long-running IterableMessageConsumer with custom ConsumeOptions. See {@link IterableMessageConsumer} and {@link ConsumeOptions}
     * IterableMessageConsumer requires the developer calls nextMessage.
     * @param consumeOptions the custom consume options
     * @return the IterableMessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the consume options are null
     */
    @NonNull
    IterableMessageConsumer iterate(@NonNull ConsumeOptions consumeOptions) throws IOException, JetStreamApiException;

    /**
     * Start a long-running MessageConsumer with default ConsumeOptions. See {@link MessageConsumer} and  {@link ConsumeOptions}
     * and the default dispatcher for this consumer context.
     * @param handler the MessageHandler used for receiving messages.
     * @return the MessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the handler is null
     */
    @NonNull
    MessageConsumer consume(@NonNull MessageHandler handler) throws IOException, JetStreamApiException;

    /**
     * Start a long-running MessageConsumer with default ConsumeOptions. See {@link MessageConsumer} and  {@link ConsumeOptions}
     * @param dispatcher The dispatcher to handle this subscription. If null, the default dispatcher will be used.
     * @param handler the MessageHandler used for receiving messages.
     * @return the MessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the handler is null
     */
    @NonNull
    MessageConsumer consume(@Nullable NatsDispatcher dispatcher, @NonNull MessageHandler handler) throws IOException, JetStreamApiException;

    /**
     * Start a long-running MessageConsumer with custom ConsumeOptions. See {@link MessageConsumer} and  {@link ConsumeOptions}
     * and the default dispatcher for this consumer context.
     * @param consumeOptions the custom consume options
     * @param handler the MessageHandler used for receiving messages.
     * @return the MessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the consume options or the handler is null
     */
    @NonNull
    MessageConsumer consume(@NonNull ConsumeOptions consumeOptions, @NonNull MessageHandler handler) throws IOException, JetStreamApiException;

    /**
     * Start a long-running MessageConsumer with custom ConsumeOptions. See {@link MessageConsumer} and  {@link ConsumeOptions}
     * @param consumeOptions the custom consume options
     * @param dispatcher The dispatcher to handle this subscription. If null, the default dispatcher will be used.
     * @param handler the MessageHandler used for receiving messages.
     * @return the MessageConsumer instance
     * @throws IOException covers various communication issues with the NATS
     *         server, such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException if the consume options or the handler is null
     */
    @NonNull
    MessageConsumer consume(@NonNull ConsumeOptions consumeOptions, @Nullable NatsDispatcher dispatcher, @NonNull MessageHandler handler) throws IOException, JetStreamApiException;

    /**
     * Unpins this consumer
     * @param group the group name of the consumer's group
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return true if the delete succeeded
     */
    boolean unpin(String group) throws IOException, JetStreamApiException;
}
