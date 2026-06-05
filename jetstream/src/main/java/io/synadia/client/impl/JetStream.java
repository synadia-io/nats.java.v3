package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static io.synadia.client.impl.ConsumerCreateRequest.Action.Create;
import static io.synadia.client.impl.MessageManager.ManageResult;
import static io.synadia.client.utils.JetStreamClientError.JsSubNoMatchingStreamForSubject;
import static io.synadia.client.utils.JsValidator.validateStreamName;
import static io.synadia.client.utils.NatsRequestCompletableFuture.CancelAction;
import static io.synadia.client.utils.Validator.*;

@NullMarked
public class JetStream extends JetStreamImpl {

    public static JetStream instance(NatsConnection connection) throws IOException {
        return new JetStream(connection);
    }

    public static JetStream instance(NatsConnection connection, JetStreamOptions jsOptions) throws IOException {
        return new JetStream(connection, jsOptions);
    }

    public JetStream(NatsConnection connection) throws IOException {
        super(connection, null);
    }

    public JetStream(NatsConnection connection, @Nullable JetStreamOptions jsOptions) throws IOException {
        super(connection, jsOptions);
    }

    public JetStream(JetStreamImpl impl) {
        super(impl);
    }

    // ----------------------------------------------------------------------------------------------------
    // Publish
    // ----------------------------------------------------------------------------------------------------

    public PublishAck publish(String subject) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, null, null, null);
    }

    public PublishAck publish(String subject, byte @Nullable[] body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, body, null, null);
    }

    public PublishAck publish(String subject, @Nullable String body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, null, body, null);
    }

    public PublishAck publish(String subject, Headers headers) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, null, null, null);
    }

    public PublishAck publish(String subject, Headers headers, byte @Nullable[] body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, body, null, null);
    }

    public PublishAck publish(String subject, Headers headers, @Nullable String body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, null, body, null);
    }

    public PublishAck publish(String subject, byte @Nullable [] body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, body, null, options);
    }

    public PublishAck publish(String subject, @Nullable String body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, null, body, options);
    }

    public PublishAck publish(String subject, Headers headers, byte[] body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, body, null, options);
    }

    public PublishAck publish(String subject, Headers headers, String body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, null, body, options);
    }

    public PublishAck publish(Message message) throws IOException, JetStreamApiException {
        validateNotNull(message, "Message");
        return publishSyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null, null);
    }

    public PublishAck publish(Message message, PublishOptions options) throws IOException, JetStreamApiException {
        validateNotNull(message, "Message");
        return publishSyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null, options);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * CompletableFuture&lt;PublishAck&gt; future =
     *     js.publishAsync("destination", "message".getBytes("UTF-8"),)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * @param subject the subject to send the message to
     * @param body the message body
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, byte[] body) {
        return publishAsyncInternal(subject, null, body, null, null, true);
    }

    public CompletableFuture<PublishAck> publishAsync(String subject, String body) {
        return publishAsyncInternal(subject, null, null, body, null, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * Headers h = new Headers().put("foo", "bar");
     * CompletableFuture&lt;PublishAck&gt; future =
     *     js.publishAsync("destination", h, "message".getBytes("UTF-8"),)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, byte[] body) {
        return publishAsyncInternal(subject, headers, body, null, null, true);
    }

    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, String body) {
        return publishAsyncInternal(subject, headers, null, body, null, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * CompletableFuture&lt;PublishAck&gt; future =
     *     js.publishAsync("destination", "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * @param subject the subject to send the message to
     * @param body the message body
     * @param options publisher options
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, byte[] body, PublishOptions options) {
        return publishAsyncInternal(subject, null, body, null, options, true);
    }

    public CompletableFuture<PublishAck> publishAsync(String subject, String body, PublishOptions options) {
        return publishAsyncInternal(subject, null, null, body, options, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * Headers h = new Headers().put("foo", "bar");
     * CompletableFuture&lt;PublishAck&gt; future =
     *     js.publishAsync("destination", h, "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @param options publisher options
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, byte[] body, PublishOptions options) {
        return publishAsyncInternal(subject, headers, body, null, options, true);
    }

    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, String body, PublishOptions options) {
        return publishAsyncInternal(subject, headers, null, body, options, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * CompletableFuture&lt;PublishAck&gt; future = js.publishAsync(message)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * <p>The Message object allows you to set a replyTo, but in publish requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the PublishAck.</p>
     *
     * @param message the message to send
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(Message message) {
        validateNotNull(message, "Message");
        return publishAsyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null, null, false);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * JetStream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * CompletableFuture&lt;PublishAck&gt; future = js.publishAsync(message, publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     * The future may be completed with an exception, either
     * an IOException covers various communication issues with the NATS server such as timeout or interruption
     * - or - a JetStreamApiException the request had an error related to the data
     *
     * <p>The Message object allows you to set a replyTo, but in publish requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the PublishAck.</p>
     *
     * @param message the message to publish
     * @param options publisher options
     * @return The future
     */
    public CompletableFuture<PublishAck> publishAsync(Message message, PublishOptions options) {
        validateNotNull(message, "Message");
        return publishAsyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null, options, false);
    }

    private PublishAck publishSyncInternal(String subject, @Nullable Headers headers, byte @Nullable[] data, @Nullable String sData, @Nullable PublishOptions options) throws IOException, JetStreamApiException {
        Headers merged = mergePublishOptions(headers, options);

        if (data == null && sData != null) {
            data = sData.getBytes(jso.getDefaultCharset());
        }

        Message resp = makeInternalRequestResponseRequired(subject, merged, data, getTimeout(), CancelAction.COMPLETE);
        return processPublishResponse(resp);
    }

    private CompletableFuture<PublishAck> publishAsyncInternal(String subject, @Nullable Headers headers, byte @Nullable [] data, @Nullable String sData, @Nullable PublishOptions options, boolean validateSubjectAndReplyTo) {
        Headers merged = mergePublishOptions(headers, options);

        if (data == null && sData != null) {
            data = sData.getBytes(jso.getDefaultCharset());
        }

        CompletableFuture<Message> future = conn.requestAsync(subject, merged, data, null, CancelAction.COMPLETE);

        return future.thenCompose(resp -> {
            try {
                responseRequired(resp);
                return CompletableFuture.completedFuture(processPublishResponse(resp));
            } catch (IOException | JetStreamApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private PublishAck processPublishResponse(Message resp) throws IOException, JetStreamApiException {
        if (resp.isStatusMessage()) {
            throw new IOException("Error Publishing: " + resp.getStatus().getMessageWithCode());
        }
        return new PublishAck(resp);
    }

    private @Nullable Headers mergePublishOptions(@Nullable Headers headers, @Nullable PublishOptions opts) {
        if (opts == null) {
            return headers;
        }

        // never touch the user's original headers
        Headers merged = headers == null ? null : new Headers(headers);

        merged = mergeNum(merged, EXPECTED_LAST_SEQ_HDR, opts.getExpectedLastSequence());
        merged = mergeNum(merged, EXPECTED_LAST_SUB_SEQ_HDR, opts.getExpectedLastSubjectSequence());
        merged = mergeString(merged, EXPECTED_LAST_SUB_SEQ_SUB_HDR, opts.getExpectedLastSubjectSequenceSubject());
        merged = mergeString(merged, EXPECTED_LAST_MSG_ID_HDR, opts.getExpectedLastMsgId());
        merged = mergeString(merged, EXPECTED_STREAM_HDR, opts.getExpectedStream());
        merged = mergeString(merged, MSG_ID_HDR, opts.getMessageId());
        return mergeString(merged, MSG_TTL_HDR, opts.getMessageTtl());
    }

    private Headers mergeNum(@Nullable Headers h, String key, long value) {
        return value > -1 ? _merge(h, key, Long.toString(value)): h;
    }

    private Headers mergeString(@Nullable Headers h, String key, String value) {
        return Validator.nullOrEmpty(value) ? h : _merge(h, key, value);
    }

    private Headers _merge(@Nullable Headers h, String key, String value) {
        if (h == null) {
            h = new Headers();
        }
        // this is always an internal header with one value per key
        return h.put(key, Collections.singletonList(value));
    }

    // ----------------------------------------------------------------------------------------------------
    // Subscribe
    // ----------------------------------------------------------------------------------------------------
    interface MessageManagerFactory {
        MessageManager createMessageManager(NatsConnection conn, JetStream js, JetStreamSubscribeConfig so);
    }

    MessageManagerFactory _pushMessageManagerFactory = PushMessageManager::new;
    MessageManagerFactory _pushOrderedMessageManagerFactory = PushOrderedMessageManager::new;
    MessageManagerFactory _pullMessageManagerFactory = (mmConn, mmJs, mmSo) -> new PullMessageManager(mmConn, mmSo);
    MessageManagerFactory _pullOrderedMessageManagerFactory = PullOrderedMessageManager::new;


    NatsSubscription createSubscription(ConsumerInfo consumerInfo,
                                        @Nullable SubscribeBehavior subscribeBehavior,
                                        @Nullable AbstractOrderedConsumerCreator<?> orderedCreator,
                                        @Nullable PullMessageManager pmmInstance)
    {
        JetStreamSubscribeConfig jssc = new JetStreamSubscribeConfig(consumerInfo, subscribeBehavior, orderedCreator);
        ConsumerConfiguration cc = jssc.consumerInfo.getConsumerConfiguration();
        MessageHandler handler = jssc.getHandler();
        NatsDispatcher dispatcher = jssc.getDispatcher();
        if (handler == null) {
            if (dispatcher != null) {
                throw new IllegalArgumentException("Dispatcher without a handler cannot receive messages");
            }
        }
        else if (dispatcher == null) {
            jssc.dispatcher(dispatcher = conn.createDispatcher());
        }

        String inbox = cc.getDeliverSubject();
        boolean isPull = inbox == null;
        if (isPull) {
            inbox = conn.createInbox() + ".*";
        }

        MessageManager mm;
        NatsSubscriptionFactory subFactory;
        if (isPull) {
            if (pmmInstance == null) {
                MessageManagerFactory mmFactory = jssc.isOrdered ? _pullOrderedMessageManagerFactory : _pullMessageManagerFactory;
                mm = mmFactory.createMessageManager(conn, this, jssc);
            }
            else {
                mm = pmmInstance;
            }
            subFactory = (sid, lSubject, lQgroup, lConn, lDispatcher)
                -> new JetStreamPullSubscription(sid, lSubject, lConn, lDispatcher, this, jssc, mm);
        }
        else {
            MessageManagerFactory mmFactory = jssc.isOrdered ? _pushOrderedMessageManagerFactory : _pushMessageManagerFactory;
            mm = mmFactory.createMessageManager(conn, this, jssc);
            subFactory = (sid, lSubject, lQgroup, lConn, lDispatcher) -> {
                JetStreamPushSubscription sub =
                    new JetStreamPushSubscription(sid, lSubject, lQgroup, lConn, lDispatcher, this, jssc, mm);
                if (lDispatcher == null) {
                    sub.setPendingLimits(jssc.getPendingMessageLimit(), jssc.getPendingByteLimit());
                }
                return sub;
            };
        }

        if (handler == null) {
            return conn.createSubscription(inbox, cc.getDeliverGroup(), null, subFactory);
        }

        AsyncMessageHandler amh = new AsyncMessageHandler(mm, handler, cc);
        return dispatcher.subscribeImplJetStream(inbox, cc.getDeliverGroup(), amh, subFactory);
    }

    static class AsyncMessageHandler implements MessageHandler {
        MessageManager manager;
        MessageHandler userHandler;

        public AsyncMessageHandler(MessageManager manager, MessageHandler userHandler, ConsumerConfiguration settledServerCC) {
            this.manager = manager;
            this.userHandler = userHandler;
        }

        @Override
        public void onMessage(Message msg) throws InterruptedException {
            if (manager.manage(msg) == ManageResult.MESSAGE) {
                userHandler.onMessage(msg);
            }
        }
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(consumerInfo, null, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(String stream, String consumerName) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(String stream, String consumerName, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(String subject) throws IOException, JetStreamApiException {
        return pushSubscribe(subject, (SubscribeBehavior)null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(String subject, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        subject = validateSubject(subject, true);
        String stream = lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PushConsumerCreator creator = new PushConsumerCreator(stream).filterSubject(subject);
        return pushSubscribe(creator, subscribeBehavior);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(PushConsumerCreator creator) throws IOException, JetStreamApiException {
        return pushSubscribe(creator, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(PushConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        ConsumerInfo ci = _createConsumer(creator, Create);
        return (JetStreamPushSubscription) createSubscription(ci, subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(PushOrderedConsumerCreator creator) throws IOException, JetStreamApiException {
        return pushSubscribe(creator, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(PushOrderedConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        ConsumerInfo ci = _createConsumer(creator, Create);
        return (JetStreamPushSubscription) createSubscription(ci, subscribeBehavior, creator, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(consumerInfo, null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(String stream, String consumerName) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(String stream, String consumerName, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(String subject) throws IOException, JetStreamApiException {
        return pullSubscribe(subject, (SubscribeBehavior)null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(String subject, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        subject = validateSubject(subject, true);
        String stream = lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PullConsumerCreator creator = new PullConsumerCreator(stream).filterSubject(subject);
        return pullSubscribe(creator, subscribeBehavior);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(PullConsumerCreator creator) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(PullConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(PullOrderedConsumerCreator creator) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), null, creator, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(PullOrderedConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), subscribeBehavior, creator, null);
    }

    /**
     * Get a stream context for a specific named stream. Verifies that the stream exists.
     * @param streamName the name of the stream
     * @return a StreamContext object
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public StreamContext getStreamContext(String streamName) throws IOException, JetStreamApiException {
        validateStreamName(streamName, true);
        return getNatsStreamContext(streamName);
    }

    public ConsumerContext createConsumer(String streamName, String subject) throws IOException, JetStreamApiException {
        return createConsumer(new PullConsumerCreator(streamName).filterSubject(subject));
    }

    public ConsumerContext createConsumer(PullConsumerCreator creator) throws IOException, JetStreamApiException {
        ConsumerInfo ci = _createConsumer(creator, Create);
        return getNatsStreamContext(creator.getStream()).getConsumerContext(ci.getName());
    }

    /**
     * Get a consumer context for a specific named stream and specific named consumer.
     * <p> Note that ConsumerContext expects a <b>pull consumer</b>.
     * <p><b>Recommended usage:</b> See {@link #getStreamContext(String) getStreamContext(String)}
     *
     * Verifies that the stream and consumer exist.
     * @param streamName the name of the stream
     * @param consumerName the name of the consumer
     * @return a ConsumerContext object
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data.
     */
    public ConsumerContext getConsumerContext(String streamName, String consumerName) throws IOException, JetStreamApiException {
        validateStreamName(streamName, true);
        required(consumerName, "Consumer Name");
        return getNatsStreamContext(streamName).getConsumerContext(consumerName);
    }

    public ConsumerContext getConsumerContext(ConsumerInfo consumerInfo) throws IOException, JetStreamApiException {
        return getNatsStreamContext(consumerInfo.getStreamName()).getConsumerContext(consumerInfo);
    }

    private NatsStreamContext getNatsStreamContext(String streamName) throws IOException, JetStreamApiException {
        return new NatsStreamContext(streamName, this, conn, jso);
    }
}
