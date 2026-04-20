package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.testutils.Validator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static io.synadia.client.impl.ConsumerCreateRequest.Action.Create;
import static io.synadia.client.impl.MessageManager.ManageResult;
import static io.synadia.client.testutils.JsValidator.validateStreamName;
import static io.synadia.client.testutils.NatsRequestCompletableFuture.CancelAction;
import static io.synadia.client.testutils.Validator.required;
import static io.synadia.client.testutils.Validator.validateNotNull;

public class JetStream extends JetStreamImpl {

    public JetStream(NatsConnection connection) throws IOException {
        super(connection, null);
    }

    public JetStream(NatsConnection connection, JetStreamOptions jsOptions) throws IOException {
        super(connection, jsOptions);
    }

    public JetStream(JetStreamImpl impl) {
        super(impl);
    }
    // ----------------------------------------------------------------------------------------------------
    // Publish
    // ----------------------------------------------------------------------------------------------------

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * js.publish("destination", "message".getBytes("UTF-8"))
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     *
     * @param subject the subject to send the message to
     * @param body the message body
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(String subject, byte[] body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, body, null);
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * Headers h = new Headers().put("foo", "bar");
     * js.publish("destination", h, "message".getBytes("UTF-8"))
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(String subject, Headers headers, byte[] body) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, body, null);
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * js.publish("destination", "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param subject the subject to send the message to
     * @param body the message body
     * @param options publisher options
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(String subject, byte[] body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, null, body, options);
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * Headers h = new Headers().put("foo", "bar");
     * js.publish("destination", h, "message".getBytes("UTF-8"), publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @param options publisher options
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(String subject, Headers headers, byte[] body, PublishOptions options) throws IOException, JetStreamApiException {
        return publishSyncInternal(subject, headers, body, options);
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The default publish options will be used.
     * The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * js.publish(message)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     *
     * <p>The Message object allows you to set a replyTo, but in publish requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the PublishAck.</p>
     *
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param message the message to send
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(Message message) throws IOException, JetStreamApiException {
        validateNotNull(message, "Message");
        return publishSyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null);
    }

    /**
     * Send a message to the specified subject and waits for a response from
     * Jetstream. The expected usage with string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * JetStream js = new JetStream(nc)
     * js.publish(message, publishOptions)
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     *
     * <p>The Message object allows you to set a replyTo, but in publish requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the PublishAck.</p>
     *
     * See {@link #publish(String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param message the message to send
     * @param options publisher options
     * @return The acknowledgement of the publish
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public PublishAck publish(Message message, PublishOptions options) throws IOException, JetStreamApiException {
        validateNotNull(message, "Message");
        return publishSyncInternal(message.getSubject(), message.getHeaders(), message.getData(), options);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The default publish options will be used.
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
        return publishAsyncInternal(subject, null, body, null, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The default publish options will be used.
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
        return publishAsyncInternal(subject, headers, body, null, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The expected usage with string content is something like:
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
        return publishAsyncInternal(subject, null, body, options, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The expected usage with string content is something like:
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
        return publishAsyncInternal(subject, headers, body, options, true);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The default publish options will be used.
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
        return publishAsyncInternal(message.getSubject(), message.getHeaders(), message.getData(), null, false);
    }

    /**
     * Send a message to the specified subject but does not wait for a response from
     * Jetstream. The expected usage with string content is something like:
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
        return publishAsyncInternal(message.getSubject(), message.getHeaders(), message.getData(), options, false);
    }

    private PublishAck publishSyncInternal(String subject, Headers headers, byte[] data, PublishOptions options) throws IOException, JetStreamApiException {
        Headers merged = mergePublishOptions(headers, options);

        if (jso.isPublishNoAck()) {
            conn.publish(subject, null, merged, data, false);
            return null;
        }

        Message resp = makeInternalRequestResponseRequired(subject, merged, data, getTimeout(), CancelAction.COMPLETE);
        return processPublishResponse(resp, options);
    }

    private CompletableFuture<PublishAck> publishAsyncInternal(String subject, Headers headers, byte[] data, PublishOptions options, boolean validateSubjectAndReplyTo) {
        Headers merged = mergePublishOptions(headers, options);

        if (jso.isPublishNoAck()) {
            conn.publish(subject, null, merged, data, false);
            return null;
        }

        CompletableFuture<Message> future = conn.requestAsync(subject, merged, data, null, CancelAction.COMPLETE);

        return future.thenCompose(resp -> {
            try {
                responseRequired(resp);
                return CompletableFuture.completedFuture(processPublishResponse(resp, options));
            } catch (IOException | JetStreamApiException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private PublishAck processPublishResponse(Message resp, PublishOptions options) throws IOException, JetStreamApiException {
        if (resp.isStatusMessage()) {
            throw new IOException("Error Publishing: " + resp.getStatus().getMessageWithCode());
        }
        return new PublishAck(resp);
    }

    private Headers mergePublishOptions(Headers headers, PublishOptions opts) {
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

    private Headers mergeNum(Headers h, String key, long value) {
        return value > -1 ? _merge(h, key, Long.toString(value)): h;
    }

    private Headers mergeString(Headers h, String key, String value) {
        return Validator.nullOrEmpty(value) ? h : _merge(h, key, value);
    }

    private Headers _merge(Headers h, String key, String value) {
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


    NatsSubscription createSubscription(@NonNull ConsumerInfo consumerInfo,
                                        @Nullable SubscribeBehavior subscribeBehavior,
                                        @Nullable AbstractOrderedConsumerCreator<?> orderedCreator,
                                        @Nullable PullMessageManager pmmInstance)
    {
        JetStreamSubscribeConfig subConf = new JetStreamSubscribeConfig(consumerInfo, subscribeBehavior, orderedCreator);
        ConsumerConfiguration cc = subConf.consumerInfo.getConsumerConfiguration();
        MessageHandler handler = subConf.getHandler();
        NatsDispatcher dispatcher = subConf.getDispatcher();
        if (handler != null && dispatcher == null) {
            subConf.dispatcher(dispatcher = conn.createDispatcher());
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
                MessageManagerFactory mmFactory = subConf.isOrdered ? _pullOrderedMessageManagerFactory : _pullMessageManagerFactory;
                mm = mmFactory.createMessageManager(conn, this, subConf);
            }
            else {
                mm = pmmInstance;
            }
            subFactory = (sid, lSubject, lQgroup, lConn, lDispatcher)
                -> new JetStreamPullSubscription(sid, lSubject, lConn, lDispatcher, this, subConf, mm);
        }
        else {
            MessageManagerFactory mmFactory = subConf.isOrdered ? _pushOrderedMessageManagerFactory : _pushMessageManagerFactory;
            mm = mmFactory.createMessageManager(conn, this, subConf);
            subFactory = (sid, lSubject, lQgroup, lConn, lDispatcher) -> {
                JetStreamPushSubscription sub =
                    new JetStreamPushSubscription(sid, lSubject, lQgroup, lConn, lDispatcher, this, subConf, mm);
                if (lDispatcher == null) {
                    sub.setPendingLimits(subConf.getPendingMessageLimit(), subConf.getPendingByteLimit());
                }
                return sub;
            };
        }

        if (dispatcher == null) {
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
    public JetStreamPushSubscription pushSubscribe(@NonNull ConsumerInfo consumerInfo) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(consumerInfo, null, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull ConsumerInfo consumerInfo, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull String stream, @NonNull String consumerName) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull String stream, @NonNull String consumerName, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPushSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull PushConsumerCreator creator) throws IOException, JetStreamApiException {
        return pushSubscribe(creator, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull PushConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        ConsumerInfo ci = _createConsumer(creator, Create);
        return (JetStreamPushSubscription) createSubscription(ci, subscribeBehavior, null, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull PushOrderedConsumerCreator creator) throws IOException, JetStreamApiException {
        return pushSubscribe(creator, null);
    }

    /**
     * pushSubscribe
     */
    public JetStreamPushSubscription pushSubscribe(@NonNull PushOrderedConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        ConsumerInfo ci = _createConsumer(creator, Create);
        return (JetStreamPushSubscription) createSubscription(ci, subscribeBehavior, creator, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull ConsumerInfo consumerInfo) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(consumerInfo, null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull ConsumerInfo consumerInfo, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull String stream, @NonNull String consumerName) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull String stream, @NonNull String consumerName, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull PullConsumerCreator creator) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), null, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull PullConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), subscribeBehavior, null, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull PullOrderedConsumerCreator creator) throws IOException, JetStreamApiException {
        return (JetStreamPullSubscription) createSubscription(_createConsumer(creator, Create), null, creator, null);
    }

    /**
     * pullSubscribe
     */
    public JetStreamPullSubscription pullSubscribe(@NonNull PullOrderedConsumerCreator creator, @Nullable SubscribeBehavior subscribeBehavior) throws IOException, JetStreamApiException {
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

    private NatsStreamContext getNatsStreamContext(String streamName) throws IOException, JetStreamApiException {
        return new NatsStreamContext(streamName, this, conn, jso);
    }
}
