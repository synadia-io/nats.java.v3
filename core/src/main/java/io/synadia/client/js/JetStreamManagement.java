package io.synadia.client.js;

import io.synadia.client.JetStreamApiException;
import io.synadia.client.JetStreamOptions;
import io.synadia.client.Message;
import io.synadia.client.PurgeOptions;
import io.synadia.client.api.Error;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.jsapi.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import static io.synadia.client.support.Validator.validateNotNull;

@NullMarked
public class JetStreamManagement extends JetStreamImpl {
    @Nullable
    private JetStream js; // this is lazy init'ed

    public JetStreamManagement(NatsConnection connection) throws IOException {
        super(connection, null);
    }

    public JetStreamManagement(NatsConnection connection, @Nullable JetStreamOptions jsOptions) throws IOException {
        super(connection, jsOptions);
    }

    /**
     * Gets the account statistics for the logged in account.
     * @return account statistics
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public AccountStatistics getAccountStatistics() throws IOException, JetStreamApiException {
        Message resp = makeRequestResponseRequired(JSAPI_ACCOUNT_INFO, null, getTimeout());
        return new AccountStatistics(resp).throwOnHasError();
    }

    /**
     * Loads or creates a stream.
     * @param creator the stream creator
     * @return stream information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the configuration is missing or invalid
     */
    public StreamInfo addStream(StreamCreator creator) throws IOException, JetStreamApiException {
        return addOrUpdateStream(creator, JSAPI_STREAM_CREATE);
    }

    /**
     * Updates an existing stream.
     * @param creator the stream creator
     * @return stream information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @throws IllegalArgumentException the configuration is missing or invalid
     */
    public StreamInfo updateStream(StreamCreator creator) throws IOException, JetStreamApiException {
        return addOrUpdateStream(creator, JSAPI_STREAM_UPDATE);
    }

    private StreamInfo addOrUpdateStream(StreamCreator creator, String template) throws IOException, JetStreamApiException {
        validateNotNull(creator, "Creator");
        String streamName = creator.getName();
        String subj = String.format(template, streamName);
        Message resp = makeRequestResponseRequired(subj, creator.toJson().getBytes(StandardCharsets.UTF_8), getTimeout());
        return createAndCacheStreamInfoThrowOnError(streamName, resp);
    }

    /**
     * Deletes an existing stream.
     * @param streamName the stream name to use.
     * @return true if the delete succeeded. Usually throws a JetStreamApiException otherwise
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public boolean deleteStream(String streamName) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_STREAM_DELETE, streamName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout());
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Gets the info for an existing stream.
     * Does not retrieve any optional data.
     * See the overloaded version that accepts StreamInfoOptions
     * @param streamName the stream name to use.
     * @return stream information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public StreamInfo getStreamInfo(String streamName) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        return _getStreamInfo(streamName, null);
    }

    /**
     * Gets the info for an existing stream, and include subject or deleted details
     * as defined by StreamInfoOptions.
     * @param streamName the stream name to use.
     * @param options the stream info options. If null, request will not return any optional data.
     * @return stream information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public StreamInfo getStreamInfo(String streamName, @Nullable StreamInfoOptions options) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        return _getStreamInfo(streamName, options);
    }

    /**
     * Purge stream messages
     * @param streamName the stream name to use.
     * @return PurgeResponse the purge response
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PurgeResponse purgeStream(String streamName) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_STREAM_PURGE, streamName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout());
        return new PurgeResponse(resp).throwOnHasError();
    }

    /**
     * Purge messages for a specific subject
     * @param streamName the stream name to use.
     * @param options the purge options
     * @return PurgeResponse the purge response
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PurgeResponse purgeStream(String streamName, PurgeOptions options) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(options, "Purge Options");
        String subj = String.format(JSAPI_STREAM_PURGE, streamName);
        byte[] body = options.toJson().getBytes(StandardCharsets.UTF_8);
        Message resp = makeRequestResponseRequired(subj, body, getTimeout());
        return new PurgeResponse(resp).throwOnHasError();
    }

    /**
     * Loads or creates a consumer.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ConsumerInfo addOrUpdateConsumer(ConsumerCreator<?> creator) throws IOException, JetStreamApiException {
        return _createConsumer(creator, ConsumerCreateRequest.Action.CreateOrUpdate);
    }

    /**
     * Creates a consumer. Must not already exist.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data such as the consumer already exists
     */
    public ConsumerInfo createConsumer(ConsumerCreator<?> creator) throws IOException, JetStreamApiException {
        return _createConsumer(creator, ConsumerCreateRequest.Action.Create);
    }

    /**
     * Updates an existing consumer. Must already exist.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data such as the consumer does not already exist
     */
    public ConsumerInfo updateConsumer(ConsumerCreator<?> creator) throws IOException, JetStreamApiException {
        return _createConsumer(creator, ConsumerCreateRequest.Action.Update);
    }

    /**
     * Deletes a consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @return true if the delete succeeded
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data, for instance the consumer does not exist.
     */
    public boolean deleteConsumer(String streamName, String consumerName) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_DELETE, streamName, consumerName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout());
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Pauses a consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @param pauseUntil consumer is paused until this time.
     * @return ConsumerPauseResponse the pause response
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data, for instance the consumer does not exist.
     */
    public ConsumerPauseResponse pauseConsumer(String streamName, String consumerName, ZonedDateTime pauseUntil) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_PAUSE, streamName, consumerName);
        ConsumerPauseRequest pauseRequest = new ConsumerPauseRequest(pauseUntil);
        Message resp = makeRequestResponseRequired(subj, pauseRequest.serialize(), getTimeout());
        return new ConsumerPauseResponse(resp).throwOnHasError();
    }

    /**
     * Resumes a paused consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @return true if the resume succeeded
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data, for instance the consumer does not exist.
     */
    public boolean resumeConsumer(String streamName, String consumerName) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_PAUSE, streamName, consumerName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout());
        ConsumerPauseResponse response = new ConsumerPauseResponse(resp).throwOnHasError();
        return !response.isPaused();
    }

    /**
     * Gets the info for an existing consumer. When possible, use metadata
     * from the message since it often already contains the needed information
     * and does not require a server call.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @return consumer information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ConsumerInfo getConsumerInfo(String streamName, String consumerName) throws IOException, JetStreamApiException {
        return super.strictGetConsumerInfo(streamName, consumerName);
    }

    /**
     * Return a list of consumers by name
     * @param streamName the name of the stream.
     * @return The list of names
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<String> getConsumerNames(String streamName) throws IOException, JetStreamApiException {
        String subj = String.format(JSAPI_CONSUMER_NAMES, streamName);
        ConsumerNamesReader cnr = new ConsumerNamesReader();
        while (cnr.hasMore()) {
            Message resp = makeRequestResponseRequired(subj, cnr.nextJson(null), getTimeout());
            cnr.process(resp);
        }
        return cnr.getStrings();
    }

    /**
     * Return a list of ConsumerInfo objects.
     * @param streamName the name of the stream.
     * @return The list of ConsumerInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<ConsumerInfo> getConsumers(String streamName) throws IOException, JetStreamApiException {
        String subj = String.format(JSAPI_CONSUMER_LIST, streamName);
        ConsumerListReader clg = new ConsumerListReader();
        while (clg.hasMore()) {
            Message resp = makeRequestResponseRequired(subj, clg.nextJson(), getTimeout());
            clg.process(resp);
        }
        return clg.getConsumers();
    }

    /**
     * Get the names of all streams.
     * @return The list of names
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<String> getStreamNames() throws IOException, JetStreamApiException {
        return _getStreamNames(null);
    }

    /**
     * Get a list of stream names that have subjects matching the subject filter.
     *
     * @param subjectFilter the subject. Wildcards are allowed.
     * @return The list of stream names matching the subject filter. May be empty, will not be null.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<String> getStreamNames(String subjectFilter) throws IOException, JetStreamApiException {
        return _getStreamNames(subjectFilter);
    }

    /**
     * Return a list of StreamInfo objects.
     * @return The list of StreamInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<StreamInfo> getStreams() throws IOException, JetStreamApiException {
        return _getStreams(null);
    }

    /**
     * Return a list of StreamInfo objects that have subjects matching the filter.
     * @param subjectFilter the filter to limit the streams by subjects. Wildcards allowed.
     * @return The list of StreamInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public List<StreamInfo> getStreams(String subjectFilter) throws IOException, JetStreamApiException {
        return _getStreams(subjectFilter);
    }

    private List<StreamInfo> _getStreams(@Nullable String subjectFilter) throws IOException, JetStreamApiException {
        StreamListReader slr = new StreamListReader();
        while (slr.hasMore()) {
            Message resp = makeRequestResponseRequired(JSAPI_STREAM_LIST, slr.nextJson(subjectFilter), getTimeout());
            slr.process(resp);
        }
        return cacheStreamInfo(slr.getStreams());
    }

    /**
     * Get MessageInfo for the message with the exact sequence in the stream.
     * @param streamName the name of the stream.
     * @param seq the sequence number of the message
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getMessage(String streamName, long seq) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.forSequence(seq));
    }

    /**
     * Get MessageInfo for the message matching the {@link MessageGetRequest}.
     * @param streamName the name of the stream.
     * @param messageGetRequest the {@link MessageGetRequest} to get a message
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getMessage(String streamName, MessageGetRequest messageGetRequest) throws IOException, JetStreamApiException {
        return _getMessage(streamName, messageGetRequest);
    }

    /**
     * Get MessageInfo for the last message of the subject.
     * @param streamName the name of the stream.
     * @param subject the subject to get the last message for.
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getLastMessage(String streamName, String subject) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.lastForSubject(subject));
    }

    /**
     * Get MessageInfo for the first message of the subject.
     * @param streamName the name of the stream.
     * @param subject the subject to get the first message for.
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getFirstMessage(String streamName, String subject) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.firstForSubject(subject));
    }

    /**
     * Get MessageInfo for the first message created at or after the start time.
     * <p>
     * This API works on Server 2.11 or later
     * @param streamName the name of the stream.
     * @param startTime the start time to get the first message for.
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getFirstMessage(String streamName, ZonedDateTime startTime) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.firstForStartTime(startTime));
    }

    /**
     * Get MessageInfo for the first message created at or after the start time matching the subject.
     * <p>
     * This API works on Server 2.11 or later
     * @param streamName the name of the stream.
     * @param startTime the start time to get the first message for.
     * @param subject the subject to get the first message for.
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getFirstMessage(String streamName, ZonedDateTime startTime, String subject) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.firstForStartTimeAndSubject(startTime, subject));
    }

    /**
     * Get MessageInfo for the message of the message sequence
     * is equal to or greater the requested sequence for the subject.
     * @param streamName the name of the stream.
     * @param seq the first possible sequence number of the message
     * @param subject the subject to get the next message for.
     * @return The MessageInfo
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public MessageInfo getNextMessage(String streamName, long seq, String subject) throws IOException, JetStreamApiException {
        return _getMessage(streamName, MessageGetRequest.nextForSubject(seq, subject));
    }

    private MessageInfo _getMessage(String streamName, MessageGetRequest messageGetRequest) throws IOException, JetStreamApiException {
        validateNotNull(messageGetRequest, "Message Get Request");
        CachedStreamInfo csi = getCachedStreamInfo(streamName);
        if (csi.allowDirect) {
            String subject;
            byte[] payload;
            if (messageGetRequest.isLastBySubject()) {
                subject = String.format(JSAPI_DIRECT_GET_LAST, streamName, messageGetRequest.getLastBySubject());
                payload = null;
            }
            else{
                subject = String.format(JSAPI_DIRECT_GET, streamName);
                payload = messageGetRequest.serialize();
            }
            Message resp = makeRequestResponseRequired(subject, payload, getTimeout());
            if (resp.isStatusMessage()) {
                throw new JetStreamApiException(Error.convert(resp.getStatus()));
            }
            return new MessageInfo(resp, streamName, true);
        }
        else {
            String getSubject = String.format(JSAPI_MSG_GET, streamName);
            Message resp = makeRequestResponseRequired(getSubject, messageGetRequest.serialize(), getTimeout());
            return new MessageInfo(resp, streamName, false).throwOnHasError();
        }
    }

    /**
     * Deletes a message, overwriting the message data with garbage
     * This can be considered an expensive (time-consuming) operation, but is more secure.
     * @param streamName name of the stream
     * @param seq the sequence number of the message
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return true if the delete succeeded
     */
    public boolean deleteMessage(String streamName, long seq) throws IOException, JetStreamApiException {
        return deleteMessage(streamName, seq, true);
    }

    /**
     * Deletes a message, optionally erasing the content of the message.
     * @param streamName name of the stream
     * @param seq the sequence number of the message
     * @param erase whether to erase the message (overwriting with garbage) or only mark it as erased.
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return true if the delete succeeded
     */
    public boolean deleteMessage(String streamName, long seq, boolean erase) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_MSG_DELETE, streamName);
        MessageDeleteRequest mdr = new MessageDeleteRequest(seq, erase);
        Message resp = makeRequestResponseRequired(subj, mdr.serialize(), getTimeout());
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Unpins a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @param consumerGroup name of the consumer's group
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     * @return true if the delete succeeded
     */
    public boolean unpinConsumer(String streamName, String consumerName, String consumerGroup) throws IOException, JetStreamApiException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        validateNotNull(consumerGroup, "Consumer Group");
        String subj = String.format(JSAPI_CONSUMER_UNPIN, streamName, consumerName);
        byte[] payload = String.format("{\"group\": \"%s\"}", consumerGroup).getBytes();
        Message resp = makeRequestResponseRequired(subj, payload, getTimeout());
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Gets a context for publishing and subscribing to subjects backed by Jetstream streams
     * and consumers, using the same connection and JetStreamOptions as the management.
     * @return a JetStream instance.
     */
    public JetStream jetStream() {
        if (js == null) {
            js = new JetStream(this);
        }
        return js;
    }
}
