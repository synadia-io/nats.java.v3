package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static io.synadia.client.impl.ConsumerCreateRequest.Action.Create;
import static io.synadia.client.impl.MessageManager.ManageResult;
import static io.synadia.client.utils.JetStreamApiUtils.UNSET;
import static io.synadia.client.utils.JetStreamClientError.JsSubNoMatchingStreamForSubject;
import static io.synadia.client.utils.JsValidator.validateStreamName;
import static io.synadia.client.utils.NatsRequestCompletableFuture.CancelAction;
import static io.synadia.client.utils.Validator.*;

/**
 * Publish, subscribe, and context methods validate their arguments and throw {@link IllegalArgumentException} for an invalid subject, stream name, or a null message.
 */
@NullMarked
public class JetStream extends JetStreamImpl {
    @Nullable
    private JetStreamManagement jsm; // this is lazy init'ed

    /**
     * Create a JetStream context for the connection.
     * @param connection the connection to run JetStream requests over
     * @return the new JetStream context
     */
    public static JetStream instance(NatsConnection connection) {
        return new JetStream(connection);
    }

    /**
     * Create a JetStream context for the connection.
     * @param connection the connection to run JetStream requests over
     * @param jsOptions JetStream options such as the domain or request timeout, or null for defaults
     * @return the new JetStream context
     */
    public static JetStream instance(NatsConnection connection, JetStreamOptions jsOptions) {
        return new JetStream(connection, jsOptions);
    }

    /**
     * Create a JetStream context for the connection, with default options.
     * @param connection the connection to run JetStream requests over
     */
    public JetStream(NatsConnection connection) {
        super(connection, null);
    }

    /**
     * Create a JetStream context for the connection with the given options.
     * @param connection the connection to run JetStream requests over
     * @param jsOptions JetStream options such as the domain or request timeout, or null for defaults
     */
    public JetStream(NatsConnection connection, @Nullable JetStreamOptions jsOptions) {
        super(connection, jsOptions);
    }

    JetStream(JetStreamImpl impl) {
        super(impl);
    }

    /**
     * A management context sharing this context's connection and options, for creating and inspecting streams and consumers.
     * @return the management context
     */
    public JetStreamManagement jetStreamManagement() {
        if (jsm == null) {
            jsm = new JetStreamManagement(this);
        }
        return jsm;
    }

    /**
     * Convenience method to create a dispatcher with no default handler, which
     * is the type that JetStream subscriptions expect.
     *
     * @return a new Dispatcher
     */
    public NatsDispatcher createDispatcher() {
        return conn.createDispatcher(null);
    }

    // ----------------------------------------------------------------------------------------------------
    // Publish
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param data the message payload, may be null for an empty message
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, byte @Nullable[] data) throws JetStreamException, InterruptedException {
        return _publishSync(subject, null, data, null, null);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param data the message payload, may be null for an empty message
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, @Nullable String data) throws JetStreamException, InterruptedException {
        return _publishSync(subject, null, null, data, null);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, Headers headers) throws JetStreamException, InterruptedException {
        return _publishSync(subject, headers, null, null, null);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param data the message payload, may be null for an empty message
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, Headers headers, byte @Nullable[] data) throws JetStreamException, InterruptedException {
        return _publishSync(subject, headers, data, null, null);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param data the message payload, may be null for an empty message
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, Headers headers, @Nullable String data) throws JetStreamException, InterruptedException {
        return _publishSync(subject, headers, null, data, null);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param data the message payload, may be null for an empty message
     * @param options publish options such as expected stream, sequence or message id
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, byte @Nullable [] data, PublishOptions options) throws JetStreamException, InterruptedException {
        return _publishSync(subject, null, data, null, options);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param data the message payload, may be null for an empty message
     * @param options publish options such as expected stream, sequence or message id
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, @Nullable String data, PublishOptions options) throws JetStreamException, InterruptedException {
        return _publishSync(subject, null, null, data, options);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param data the message payload, may be null for an empty message
     * @param options publish options such as expected stream, sequence or message id
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, Headers headers, byte[] data, PublishOptions options) throws JetStreamException, InterruptedException {
        return _publishSync(subject, headers, data, null, options);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param data the message payload, may be null for an empty message
     * @param options publish options such as expected stream, sequence or message id
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(String subject, Headers headers, String data, PublishOptions options) throws JetStreamException, InterruptedException {
        return _publishSync(subject, headers, null, data, options);
    }

    /**
     * Sends a message to the specified subject and wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the message is null
     * @param message the message to send, carrying its own subject, headers and payload
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(Message message) throws JetStreamException, InterruptedException {
        validateNotNull(message, "Message");
        return _publishSync(message.getSubject(), message.getHeaders(), message.getData(), null, null);
    }

    /**
     * Sends a messageand wait for the server to acknowledge it. The subject must be covered by a stream or the publish fails.
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the message is null
     * @param message the message to send, carrying its own subject, headers and payload
     * @param options publish options such as expected stream, sequence or message id
     * @return an acknowledgement carrying the stream name and the sequence the message was stored at
     * @throws JetStreamStatusException if the server replies with a status message instead of an ack
     */
    public PublishAck publish(Message message, PublishOptions options) throws JetStreamException, InterruptedException {
        validateNotNull(message, "Message");
        return _publishSync(message.getSubject(), message.getHeaders(), message.getData(), null, options);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream. 
     * The default publish options will be used.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param body the message body
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, byte[] body) {
        return _publishAsync(subject, null, body, null, null, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream. 
     * The default publish options will be used.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param body the message payload
     * @return a future that completes with the acknowledgement The future completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, @Nullable String body) {
        return _publishAsync(subject, null, null, body, null, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream. 
     * The default publish options will be used.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, byte[] body) {
        return _publishAsync(subject, headers, body, null, null, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream. 
     * The default publish options will be used.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param body the message payload
     * @return a future that completes with the acknowledgement The future completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, @Nullable String body) {
        return _publishAsync(subject, headers, null, body, null, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param body the message body
     * @param options publisher options
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, byte @Nullable [] body, PublishOptions options) {
        return _publishAsync(subject, null, body, null, options, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param body the message payload
     * @param options publish options such as expected stream, sequence or message id
     * @return a future that completes with the acknowledgement The future completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, @Nullable String body, PublishOptions options) {
        return _publishAsync(subject, null, null, body, options, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param body the message body
     * @param options publisher options
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, byte @Nullable [] body, PublishOptions options) {
        return _publishAsync(subject, headers, body, null, options, true);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param subject the subject to send the message to
     * @param headers headers to send with the message
     * @param body the message payload
     * @param options publish options such as expected stream, sequence or message id
     * @return a future that completes with the acknowledgement The future completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     */
    public CompletableFuture<PublishAck> publishAsync(String subject, Headers headers, @Nullable String body, PublishOptions options) {
        return _publishAsync(subject, headers, null, body, options, true);
    }

    /**
     * Sends a messagebut does not wait for a response from JetStream.
     * The default publish options will be used.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param message the message to send
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     * @throws IllegalArgumentException if the message is null
     */
    public CompletableFuture<PublishAck> publishAsync(Message message) {
        validateNotNull(message, "Message");
        return _publishAsync(message.getSubject(), message.getHeaders(), message.getData(), null, null, false);
    }

    /**
     * Sends a message to the specified subject but does not wait for a response from JetStream.
     * The future may be completed exceptionally with a JetStreamException covering
     * communication and server-side JetStream errors.
     * @param message the message to publish
     * @param options publisher options
     * @return The future, which completes exceptionally with a JetStreamStatusException if the server replies with a status message instead of an ack.
     * @throws IllegalArgumentException if the message is null
     */
    public CompletableFuture<PublishAck> publishAsync(Message message, PublishOptions options) {
        validateNotNull(message, "Message");
        return _publishAsync(message.getSubject(), message.getHeaders(), message.getData(), null, options, false);
    }

    private PublishAck _publishSync(String subject, @Nullable Headers headers, byte @Nullable[] data, @Nullable String sData, @Nullable PublishOptions options) throws JetStreamException, InterruptedException {
        Headers merged = mergePublishOptions(headers, options);

        if (data == null && sData != null) {
            data = sData.getBytes(jso.getDefaultCharset());
        }

        long timeout = options == null || options.getPublishTimeout() == UNSET ? getTimeout() : options.getPublishTimeout();
        Message resp = makeInternalRequestResponseRequired(subject, merged, data, timeout);
        return processPublishResponse(resp);
    }

    private CompletableFuture<PublishAck> _publishAsync(String subject, @Nullable Headers headers, byte @Nullable [] data, @Nullable String sData, @Nullable PublishOptions options, boolean validateSubjectAndReplyTo) {
        Headers merged = mergePublishOptions(headers, options);

        if (data == null && sData != null) {
            data = sData.getBytes(jso.getDefaultCharset());
        }

        CompletableFuture<Message> future = conn.requestAsync(subject, merged, data, -1, CancelAction.COMPLETE, conn.isForceFlushOnRequest());

        return future.thenCompose(resp -> {
            try {
                responseRequired(resp, subject);
                return CompletableFuture.completedFuture(processPublishResponse(resp));
            } catch (JetStreamException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private PublishAck processPublishResponse(Message resp) throws JetStreamException {
        if (resp.isStatusMessage()) {
            throw new JetStreamStatusException("Error Publishing", resp.getStatus(), null);
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

    private @Nullable Headers mergeNum(@Nullable Headers h, String key, long value) {
        return value > -1 ? _merge(h, key, Long.toString(value)): h;
    }

    private @Nullable Headers mergeString(@Nullable Headers h, String key, String value) {
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


    NatsSubscription _createConsumerAndSubscription(String stream, ConsumerCreator<?> creator, @Nullable SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        ConsumerInfo ci = _createConsumer(stream, creator, Create);
        try {
            if (creator instanceof AbstractOrderedConsumerCreator<?> orderedCreator) {
                return _createJsSubscription(ci, subscribeBehavior, orderedCreator, null);
            }
            return _createJsSubscription(ci, subscribeBehavior, null, null);
        }
        catch (RuntimeException e) {
            try {
                _deleteConsumer(stream, ci.getName());
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                e.addSuppressed(ie);
            }
            catch (Exception de) {
                e.addSuppressed(de);
            }
            throw e;
        }
    }

    NatsSubscription _createJsSubscription(ConsumerInfo consumerInfo,
                                           @Nullable SubscribeBehavior subscribeBehavior,
                                           @Nullable AbstractOrderedConsumerCreator<?> orderedCreator,
                                           @Nullable PullMessageManager pmmInstance)
    {
        // The config makes the dispatcher when the subscribe is async and the caller did not supply one,
        // so from here on a failure has to close it - the subscription that would have owned it never exists.
        JetStreamSubscribeConfig jssc = new JetStreamSubscribeConfig(consumerInfo, subscribeBehavior, orderedCreator, conn::createDispatcher);
        try {
            ConsumerConfiguration cc = jssc.consumerInfo.getConsumerConfiguration();
            MessageHandler handler = jssc.getHandler();

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
                    // Pending limits only apply to a synchronous push subscription (it owns its own queue).
                    // For async, set limits on the dispatcher directly; pull is bounded by its batch size.
                    if (lDispatcher == null) {
                        sub.setPendingLimits(jssc.getPendingMessageLimit(), jssc.getPendingByteLimit());
                    }
                    return sub;
                };
            }

            if (handler == null) {
                return conn._createSubscriptionByFactory(inbox, cc.getDeliverGroup(), null, subFactory);
            }

            AsyncMessageHandler amh = new AsyncMessageHandler(mm, handler);
            //noinspection DataFlowIssue DISPATCHER WILL NEVER BE NULL WHEN THERE IS A HANDLER!
            return jssc.getDispatcher()._subscribeByFactory(inbox, cc.getDeliverGroup(), amh, subFactory);
        }
        catch (RuntimeException e) {
            if (jssc.internalDispatcher) {
                try {
                    //noinspection DataFlowIssue DISPATCHER WILL NEVER BE NULL WHEN IT IS INTERNAL!
                    conn.closeDispatcher(jssc.getDispatcher());
                }
                catch (Exception ce) {
                    e.addSuppressed(ce);
                }
            }
            throw e;
        }
    }

    static class AsyncMessageHandler implements MessageHandler {
        MessageManager manager;
        MessageHandler userHandler;

        public AsyncMessageHandler(MessageManager manager, MessageHandler userHandler) {
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
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param consumerInfo describes the consumer to bind to
     * @return the push subscription
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo) {
        return (JetStreamPushSubscription) _createJsSubscription(consumerInfo, null, null, null);
    }

    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param consumerInfo describes the consumer to bind to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler) {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) _createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. The subscription
     * is set up according to {@code subscribeBehavior}.
     * @param consumerInfo describes the consumer to bind to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) _createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public JetStreamPushSubscription pushSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException {
        return (JetStreamPushSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are delivered asynchronously to {@code messageHandler} on a
     * newly created {@link Dispatcher}.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. The subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are consumed synchronously from the
     * returned subscription.
     * @param subject the subject to subscribe to
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPushSubscription pushSubscribe(String subject) throws JetStreamException, InterruptedException {
        return pushSubscribe(subject, new SubscribeBehavior());
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are delivered asynchronously to
     * {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param subject the subject to subscribe to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code messageHandler} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPushSubscription pushSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pushSubscribe(subject, subscribeBehavior);
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. The subscription is set up according to
     * {@code subscribeBehavior}.
     * @param subject the subject to subscribe to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code subscribeBehavior} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPushSubscription pushSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        subject = validateSubject(subject, true);
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        String stream = lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PushConsumerCreator creator = new PushConsumerCreator().filterSubject(subject);
        return pushSubscribe(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator) throws JetStreamException, InterruptedException {
        return pushSubscribe(stream, creator, new SubscribeBehavior());
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. The
     * subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        return pushSubscribe(stream, creator, new SubscribeBehavior());
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are delivered asynchronously to {@code messageHandler} on a newly
     * created {@link Dispatcher}.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pushSubscribe(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. The subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param consumerInfo describes the consumer to bind to
     * @return the pull subscription
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo) {
        return (JetStreamPullSubscription) _createJsSubscription(consumerInfo, null, null, null);
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param consumerInfo describes the consumer to bind to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler) {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) _createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. The subscription
     * is set up according to {@code subscribeBehavior}.
     * @param consumerInfo describes the consumer to bind to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) _createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public JetStreamPullSubscription pullSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are delivered asynchronously to {@code messageHandler} on a
     * newly created {@link Dispatcher}.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. The subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) _createJsSubscription(strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are consumed synchronously from the
     * returned subscription.
     * @param subject the subject to subscribe to
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPullSubscription pullSubscribe(String subject) throws JetStreamException, InterruptedException {
        return pullSubscribe(subject, new SubscribeBehavior());
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are delivered asynchronously to
     * {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param subject the subject to subscribe to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code messageHandler} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPullSubscription pullSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pullSubscribe(subject, subscribeBehavior);
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. The subscription is set up according to
     * {@code subscribeBehavior}.
     * @param subject the subject to subscribe to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code subscribeBehavior} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public JetStreamPullSubscription pullSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        subject = validateSubject(subject, true);
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        String stream = lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PullConsumerCreator creator = new PullConsumerCreator().filterSubject(subject);
        return pullSubscribe(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, null);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. The
     * subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are consumed synchronously from the returned subscription.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, null);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are delivered asynchronously to {@code messageHandler} on a newly
     * created {@link Dispatcher}.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. The subscription is set up according to {@code subscribeBehavior}.
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) _createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Get a stream context for a specific named stream. Verifies that the stream exists.
     * @param streamName the name of the stream
     * @return a StreamContext object
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public StreamContext getStreamContext(String streamName) throws JetStreamException, InterruptedException {
        validateStreamName(streamName, true);
        return getNatsStreamContext(streamName);
    }

    /**
     * Create a consumer.
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     * @param stream the stream name
     * @param subject the subject to send the message to
     * @return a context for consuming from the new consumer
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerContext createConsumer(String stream, String subject) throws JetStreamException, InterruptedException {
        return createConsumer(stream, new PullConsumerCreator().filterSubject(subject));
    }

    /**
     * Create a consumer.
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     * @param stream the stream name
     * @param creator the consumer configuration to create from
     * @return a context for consuming from the new consumer
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerContext createConsumer(String stream, PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        ConsumerInfo ci = _createConsumer(stream, creator, Create);
        return getNatsStreamContext(stream).getConsumerContext(ci.getName());
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is invalid, or the consumer name is null or empty
     */
    public ConsumerContext getConsumerContext(String streamName, String consumerName) throws JetStreamException, InterruptedException {
        validateStreamName(streamName, true);
        required(consumerName, "Consumer Name");
        return getNatsStreamContext(streamName).getConsumerContext(consumerName);
    }

    /**
     * Get a context for consuming from an existing consumer.
     * @param consumerInfo info identifying the consumer and its stream
     * @return a context for consuming from the consumer
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerContext getConsumerContext(ConsumerInfo consumerInfo) throws JetStreamException, InterruptedException {
        return getNatsStreamContext(consumerInfo.getStreamName()).getConsumerContext(consumerInfo);
    }

    private NatsStreamContext getNatsStreamContext(String streamName) throws JetStreamException, InterruptedException {
        return new NatsStreamContext(streamName, this, conn, jso);
    }
}
