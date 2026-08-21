package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.*;
import io.synadia.client.api.Error;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;

import static io.synadia.client.utils.Validator.validateNotNull;

/**
 * The JetStream management context. Every call here is a request to the JetStream API for
 * administering streams, consumers and the messages stored in them.
 */
@NullMarked
public class JetStreamManagement extends JetStreamImpl {
    @Nullable
    private JetStream js; // this is lazy init'ed

    /**
     * Create a management context with default JetStream options.
     * @param connection the connection to run the API requests over
     * @return the management context
     */
    public static JetStreamManagement instance(NatsConnection connection) {
        return new JetStreamManagement(connection);
    }

    /**
     * Create a management context with the given JetStream options.
     * @param connection the connection to run the API requests over
     * @param jsOptions the JetStream options, supplying the API prefix and the request timeout
     * @return the management context
     */
    public static JetStreamManagement instance(NatsConnection connection, JetStreamOptions jsOptions) {
        return new JetStreamManagement(connection, jsOptions);
    }

    /**
     * Create a management context with default JetStream options.
     * @param connection the connection to run the API requests over
     */
    public JetStreamManagement(NatsConnection connection) {
        super(connection, null);
    }

    /**
     * Create a management context with the given JetStream options.
     * @param connection the connection to run the API requests over
     * @param jsOptions the JetStream options, or null to use the defaults
     */
    public JetStreamManagement(NatsConnection connection, @Nullable JetStreamOptions jsOptions) {
        super(connection, jsOptions);
    }

    JetStreamManagement(JetStreamImpl impl) {
        super(impl);
    }

    /**
     * Gets a JetStream context using the same connection and JetStreamOptions as the management.
     * @return a JetStream instance.
     */
    public JetStream jetStream() {
        if (js == null) {
            js = new JetStream(this);
        }
        return js;
    }

    /**
     * Gets the account statistics for the logged in account.
     * @return account statistics
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the server is not JetStream enabled
     */
    public AccountStatistics getAccountStatistics() throws JetStreamException, InterruptedException {
        Message resp = makeRequestResponseRequired(JSAPI_ACCOUNT_INFO, null, getTimeout(), "getAccountStatistics");
        return new AccountStatistics(resp).throwOnHasError();
    }

    /**
     * Loads or creates a stream.
     * @param creator the stream creator
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the configuration is missing or invalid
     */
    public StreamInfo addStream(StreamCreator creator) throws JetStreamException, InterruptedException {
        return addOrUpdateStream(creator, JSAPI_STREAM_CREATE);
    }

    /**
     * Updates an existing stream.
     * @param creator the stream creator
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException the configuration is missing or invalid
     */
    public StreamInfo updateStream(StreamCreator creator) throws JetStreamException, InterruptedException {
        return addOrUpdateStream(creator, JSAPI_STREAM_UPDATE);
    }

    private StreamInfo addOrUpdateStream(StreamCreator creator, String template) throws JetStreamException, InterruptedException {
        validateNotNull(creator, "Creator");
        String streamName = creator.getName();
        String subj = String.format(template, streamName);
        Message resp = makeRequestResponseRequired(subj, creator.toJson().getBytes(StandardCharsets.UTF_8), getTimeout(), template.equals(JSAPI_STREAM_CREATE) ? "addStream" : "updateStream");
        return createAndCacheStreamInfoThrowOnError(streamName, resp);
    }

    /**
     * Deletes an existing stream.
     * @param streamName the stream name to use.
     * @return true if the delete succeeded. Usually throws a JetStreamApiException otherwise
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public boolean deleteStream(String streamName) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_STREAM_DELETE, streamName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout(), "deleteStream");
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Gets the info for an existing stream.
     * Does not retrieve any optional data.
     * See the overloaded version that accepts StreamInfoOptions
     * @param streamName the stream name to use.
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public StreamInfo getStreamInfo(String streamName) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        return _getStreamInfo(streamName, null);
    }

    /**
     * Gets the info for an existing stream, and include subject or deleted details
     * as defined by StreamInfoOptions.
     * @param streamName the stream name to use.
     * @param options the stream info options. If null, request will not return any optional data.
     * @return stream information
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public StreamInfo getStreamInfo(String streamName, @Nullable StreamInfoOptions options) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        return _getStreamInfo(streamName, options);
    }

    /**
     * Purge stream messages
     * @param streamName the stream name to use.
     * @return PurgeResponse the purge response
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public PurgeResponse purgeStream(String streamName) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_STREAM_PURGE, streamName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout(), "purgeStream");
        return new PurgeResponse(resp).throwOnHasError();
    }

    /**
     * Purge messages for a specific subject
     * @param streamName the stream name to use.
     * @param options the purge options
     * @return PurgeResponse the purge response
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public PurgeResponse purgeStream(String streamName, PurgeOptions options) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(options, "Purge Options");
        String subj = String.format(JSAPI_STREAM_PURGE, streamName);
        byte[] body = options.toJson().getBytes(StandardCharsets.UTF_8);
        Message resp = makeRequestResponseRequired(subj, body, getTimeout(), "purgeStream");
        return new PurgeResponse(resp).throwOnHasError();
    }

    /**
     * Creates a consumer. Must not already exist.
     * @param stream the name of the stream the consumer is created against.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerInfo createConsumer(String stream, ConsumerCreator<?> creator) throws JetStreamException, InterruptedException {
        return _createConsumer(stream, creator, ConsumerCreateRequest.Action.Create);
    }

    /**
     * Updates an existing consumer. Must already exist.
     * @param stream the name of the stream the consumer belongs to.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerInfo updateConsumer(String stream, ConsumerCreator<?> creator) throws JetStreamException, InterruptedException {
        return _createConsumer(stream, creator, ConsumerCreateRequest.Action.Update);
    }

    /**
     * Loads or creates a consumer.
     * @param stream the name of the stream the consumer belongs to.
     * @param creator the consumer creator to use.
     * @return consumer information.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerInfo createOrUpdateConsumer(String stream, ConsumerCreator<?> creator) throws JetStreamException, InterruptedException {
        return _createConsumer(stream, creator, ConsumerCreateRequest.Action.CreateOrUpdate);
    }

    /**
     * Deletes a consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @return true if the delete succeeded
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public boolean deleteConsumer(String streamName, String consumerName) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        return _deleteConsumer(streamName, consumerName);
    }

    /**
     * Pauses a consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @param pauseUntil consumer is paused until this time.
     * @return ConsumerPauseResponse the pause response
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerPauseResponse pauseConsumer(String streamName, String consumerName, ZonedDateTime pauseUntil) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        validateNotNull(pauseUntil, "Pause Until");
        String subj = String.format(JSAPI_CONSUMER_PAUSE, streamName, consumerName);
        ConsumerPauseRequest pauseRequest = new ConsumerPauseRequest(pauseUntil);
        Message resp = makeRequestResponseRequired(subj, pauseRequest.serialize(), getTimeout(), "pauseConsumer");
        return new ConsumerPauseResponse(resp).throwOnHasError();
    }

    /**
     * Resumes a paused consumer.
     * @param streamName name of the stream
     * @param consumerName the name of the consumer.
     * @return true if the call succeeded
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public boolean resumeConsumer(String streamName, String consumerName) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_PAUSE, streamName, consumerName);
        Message resp = makeRequestResponseRequired(subj, null, getTimeout(), "resumeConsumer");
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerInfo getConsumerInfo(String streamName, String consumerName) throws JetStreamException, InterruptedException {
        return super.strictGetConsumerInfo(streamName, consumerName);
    }

    /**
     * Return a list of consumers by name
     * @param streamName the name of the stream.
     * @return The list of names
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> getConsumerNames(String streamName) throws JetStreamException, InterruptedException {
        String subj = String.format(JSAPI_CONSUMER_NAMES, streamName);
        ConsumerNamesReader cnr = new ConsumerNamesReader();
        while (cnr.hasMore()) {
            Message resp = makeRequestResponseRequired(subj, cnr.nextJson(null), getTimeout(), "getConsumerNames");
            cnr.process(resp);
        }
        return cnr.getStrings();
    }

    /**
     * Return a list of ConsumerInfo objects.
     * @param streamName the name of the stream.
     * @return The list of ConsumerInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<ConsumerInfo> getConsumers(String streamName) throws JetStreamException, InterruptedException {
        String subj = String.format(JSAPI_CONSUMER_LIST, streamName);
        ConsumerListReader clg = new ConsumerListReader();
        while (clg.hasMore()) {
            Message resp = makeRequestResponseRequired(subj, clg.nextJson(), getTimeout(), "getConsumers");
            clg.process(resp);
        }
        return clg.getConsumers();
    }

    /**
     * Get the names of all streams.
     * @return The list of names
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> getStreamNames() throws JetStreamException, InterruptedException {
        return _getStreamNames(null);
    }

    /**
     * Get a list of stream names that have subjects matching the subject filter.
     *
     * @param subjectFilter the subject. Wildcards are allowed.
     * @return The list of stream names matching the subject filter. May be empty, will not be null.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<String> getStreamNames(String subjectFilter) throws JetStreamException, InterruptedException {
        return _getStreamNames(subjectFilter);
    }

    /**
     * Return a list of StreamInfo objects.
     * @return The list of StreamInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<StreamInfo> getStreams() throws JetStreamException, InterruptedException {
        return _getStreams(null);
    }

    /**
     * Return a list of StreamInfo objects that have subjects matching the filter.
     * @param subjectFilter the filter to limit the streams by subjects. Wildcards allowed.
     * @return The list of StreamInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public List<StreamInfo> getStreams(String subjectFilter) throws JetStreamException, InterruptedException {
        return _getStreams(subjectFilter);
    }

    private List<StreamInfo> _getStreams(@Nullable String subjectFilter) throws JetStreamException, InterruptedException {
        StreamListReader slr = new StreamListReader();
        while (slr.hasMore()) {
            Message resp = makeRequestResponseRequired(JSAPI_STREAM_LIST, slr.nextJson(subjectFilter), getTimeout(), "getStreams");
            slr.process(resp);
        }
        return cacheStreamInfo(slr.getStreams());
    }

    /**
     * Get MessageInfo for the message with the exact sequence in the stream.
     * @param streamName the name of the stream.
     * @param seq the sequence number of the message
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getMessage(String streamName, long seq) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, MessageGetRequest.forSequence(seq));
    }

    /**
     * Get MessageInfo for the message matching the {@link MessageGetRequest}.
     * @param streamName the name of the stream.
     * @param messageGetRequest the {@link MessageGetRequest} to get a message
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getMessage(String streamName, MessageGetRequest messageGetRequest) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, messageGetRequest);
    }

    /**
     * Get MessageInfo for the last message of the subject.
     * @param streamName the name of the stream.
     * @param subject the subject to get the last message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getLastMessage(String streamName, String subject) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, MessageGetRequest.lastForSubject(subject));
    }

    /**
     * Get MessageInfo for the first message of the subject.
     * @param streamName the name of the stream.
     * @param subject the subject to get the first message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getFirstMessage(String streamName, String subject) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, MessageGetRequest.firstForSubject(subject));
    }

    /**
     * Get MessageInfo for the first message created at or after the start time.
     * <p>
     * This API works on Server 2.11 or later
     * @param streamName the name of the stream.
     * @param startTime the start time to get the first message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getFirstMessage(String streamName, ZonedDateTime startTime) throws JetStreamException, InterruptedException {
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getFirstMessage(String streamName, ZonedDateTime startTime, String subject) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, MessageGetRequest.firstForStartTimeAndSubject(startTime, subject));
    }

    /**
     * Get MessageInfo for the message of the message sequence
     * is equal to or greater the requested sequence for the subject.
     * @param streamName the name of the stream.
     * @param seq the first possible sequence number of the message
     * @param subject the subject to get the next message for.
     * @return The MessageInfo
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public MessageInfo getNextMessage(String streamName, long seq, String subject) throws JetStreamException, InterruptedException {
        return _getMessage(streamName, MessageGetRequest.nextForSubject(seq, subject));
    }

    private MessageInfo _getMessage(String streamName, MessageGetRequest messageGetRequest) throws JetStreamException, InterruptedException {
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
            Message resp = makeRequestResponseRequired(subject, payload, getTimeout(), "getMessage");
            if (resp.isStatusMessage()) {
                throw new JetStreamApiException(Error.convert(resp.getStatus()));
            }
            return new MessageInfo(resp, streamName, true);
        }
        else {
            String getSubject = String.format(JSAPI_MSG_GET, streamName);
            Message resp = makeRequestResponseRequired(getSubject, messageGetRequest.serialize(), getTimeout(), "getMessage");
            return new MessageInfo(resp, streamName, false).throwOnHasError();
        }
    }

    /**
     * Deletes a message, overwriting the message data with garbage
     * This can be considered an expensive (time-consuming) operation, but is more secure.
     * @param streamName name of the stream
     * @param seq the sequence number of the message
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return true if the delete succeeded
     */
    public boolean deleteMessage(String streamName, long seq) throws JetStreamException, InterruptedException {
        return deleteMessage(streamName, seq, true);
    }

    /**
     * Deletes a message, optionally erasing the content of the message.
     * @param streamName name of the stream
     * @param seq the sequence number of the message
     * @param erase whether to erase the message (overwriting with garbage) or only mark it as erased.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return true if the delete succeeded
     */
    public boolean deleteMessage(String streamName, long seq, boolean erase) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        String subj = String.format(JSAPI_MSG_DELETE, streamName);
        MessageDeleteRequest mdr = new MessageDeleteRequest(seq, erase);
        Message resp = makeRequestResponseRequired(subj, mdr.serialize(), getTimeout(), "deleteMessage");
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Unpins a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @param consumerGroup name of the consumer's group
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return true if the delete succeeded
     */
    public boolean unpinConsumer(String streamName, String consumerName, String consumerGroup) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        validateNotNull(consumerGroup, "Consumer Group");
        String subj = String.format(JSAPI_CONSUMER_UNPIN, streamName, consumerName);
        byte[] payload = String.format("{\"group\": \"%s\"}", consumerGroup).getBytes();
        Message resp = makeRequestResponseRequired(subj, payload, getTimeout(), "unpinConsumer");
        return new SuccessApiResponse(resp).throwOnHasError().getSuccess();
    }

    /**
     * Reset a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return the current consumer after the reset
     */
    public ConsumerInfo resetConsumer(String streamName, String consumerName) throws JetStreamException, InterruptedException {
        return resetConsumer(streamName, consumerName, -1);
    }

    /**
     * Reset a consumer
     * @param streamName name of the stream
     * @param consumerName name of consumer
     * @param sequence ack floor stream sequence
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @return the current consumer after the reset
     */
    public ConsumerInfo resetConsumer(String streamName, String consumerName, long sequence) throws JetStreamException, InterruptedException {
        validateNotNull(streamName, "Stream Name");
        validateNotNull(consumerName, "Consumer Name");
        String subj = String.format(JSAPI_CONSUMER_RESET, streamName, consumerName);
        byte[] payload = (sequence < 1 ? "{}" : String.format("{\"seq\":%d}", sequence)).getBytes(StandardCharsets.ISO_8859_1);
        Message resp = makeRequestResponseRequired(subj, payload, getTimeout(), "resetConsumer");
        return new ConsumerInfo(resp).throwOnHasError();
    }
}
