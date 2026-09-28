package io.synadia.client.impl;

import io.synadia.client.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Stream Context provide a set of operations for managing the stream
 * and its contents and for managing consumers.
 * <p> For basic usage examples see {@link JetStream JetStream}
 */
public interface StreamContext {
    /**
     * Gets the stream name that was used to create the context.
     * @return the stream name
     */
    @NonNull
    String getStreamName();

    /**
     * Gets information about the stream for this context.
     * Does not retrieve any optional data.
     * See the overloaded version that accepts StreamInfoOptions
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    StreamInfo getStreamInfo() throws JetStreamException, InterruptedException;

    /**
     * Gets information about the stream for this context.
     * @param options the stream info options. If null, request will not return any optional data.
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    StreamInfo getStreamInfo(@Nullable StreamInfoOptions options) throws JetStreamException, InterruptedException;

    /**
     * Purge stream messages
     * @return PurgeResponse the purge response
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    PurgeResponse purge() throws JetStreamException, InterruptedException;

    /**
     * Purge messages for a specific subject
     * @param options the purge options
     * @return PurgeResponse the purge response
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    PurgeResponse purge(PurgeOptions options) throws JetStreamException, InterruptedException;

    /**
     * Create an ephemeral consumer on this stream filtered to a subject, taking the defaults
     * for everything else. The shorthand for the common case where no configuration is needed.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param subject the subject filter for the consumer
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerContext createConsumer(@NonNull String subject) throws JetStreamException, InterruptedException;

    /**
     * Get a consumer context for the context's stream and specific named consumer.
     * Verifies that the consumer exists.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param consumerName the name of the consumer
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerContext getConsumerContext(@NonNull String consumerName) throws JetStreamException, InterruptedException;

    /**
     * Get a consumer context from consumer info already in hand. Skips the round trip that
     * {@link #getConsumerContext(String)} makes, so the consumer is not re-verified.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param ci the info for an existing consumer on this stream
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    ConsumerContext getConsumerContext(@NonNull ConsumerInfo ci) throws JetStreamException, InterruptedException;

    /**
     * Management function to create a consumer on this stream. Fails if a consumer with the
     * same name already exists.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param creator the consumer configuration to use.
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerContext createConsumer(@NonNull PullConsumerCreator creator) throws JetStreamException, InterruptedException;

    /**
     * Management function to update an existing consumer on this stream. Fails if the consumer
     * does not exist or if the change is not one the server allows on a live consumer.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param creator the consumer configuration to use.
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerContext updateConsumer(@NonNull PullConsumerCreator creator) throws JetStreamException, InterruptedException;

    /**
     * Management function to create or update a consumer on this stream.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * @param creator the consumer configuration to use.
     * @return a ConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerContext createOrUpdateConsumer(@NonNull PullConsumerCreator creator) throws JetStreamException, InterruptedException;

    /**
     * Create an ordered consumer context for the context's stream.
     * @param creator the creator for the ordered consumer
     * @return an OrderedConsumerContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     */
    @NonNull
    OrderedConsumerContext createOrderedConsumer(@NonNull PullOrderedConsumerCreator creator) throws JetStreamException;

    /**
     * Management function to deletes a consumer.
     * @param consumerName the name of the consumer.
     * @return true if the delete succeeded
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    boolean deleteConsumer(@NonNull String consumerName) throws JetStreamException, InterruptedException;

    /**
     * Gets the info for an existing consumer.
     * @param consumerName the name of the consumer.
     * @return consumer information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    ConsumerInfo getConsumerInfo(@NonNull String consumerName) throws JetStreamException, InterruptedException;

    /**
     * Return a list of consumers by name
     * @return The list of names
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    List<String> getConsumerNames() throws JetStreamException, InterruptedException;

    /**
     * Return a list of ConsumerInfo objects.
     * @return The list of ConsumerInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    List<ConsumerInfo> getConsumers() throws JetStreamException, InterruptedException;

    /**
     * Get MessageInfo for the message with the exact sequence in the stream.
     * @param seq the sequence number of the message
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    MessageInfo getMessage(long seq) throws JetStreamException, InterruptedException;

    /**
     * Get MessageInfo for the last message of the subject.
     * @param subject the subject to get the last message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    MessageInfo getLastMessage(@NonNull String subject) throws JetStreamException, InterruptedException;

    /**
     * Get MessageInfo for the first message of the subject.
     * @param subject the subject to get the first message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    MessageInfo getFirstMessage(@NonNull String subject) throws JetStreamException, InterruptedException;

    /**
     * Get MessageInfo for the message of the message sequence
     * is equal to or greater the requested sequence for the subject.
     * @param seq the first possible sequence number of the message
     * @param subject the subject to get the next message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    @NonNull
    MessageInfo getNextMessage(long seq, @NonNull String subject) throws JetStreamException, InterruptedException;

    /**
     * Deletes a message, overwriting the message data with garbage
     * This can be considered an expensive (time-consuming) operation, but is more secure.
     * @param seq the sequence number of the message
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return true if the delete succeeded
     */
    boolean deleteMessage(long seq) throws JetStreamException, InterruptedException;

    /**
     * Deletes a message, optionally erasing the content of the message.
     * @param seq the sequence number of the message
     * @param erase whether to erase the message (overwriting with garbage) or only mark it as erased.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return true if the delete succeeded
     */
    boolean deleteMessage(long seq, boolean erase) throws JetStreamException, InterruptedException;
}
