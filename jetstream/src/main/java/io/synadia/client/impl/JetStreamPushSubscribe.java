// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NullMarked;

import static io.synadia.client.utils.JetStreamClientError.JsSubNoMatchingStreamForSubject;
import static io.synadia.client.utils.Validator.validateSubject;

/**
 * The JetStream push subscribe methods. Use them through an instance made from a {@link JetStream},
 * or as static methods that take the {@link JetStream} as the first parameter:
 * <pre>
 * JetStreamPushSubscription sub = new JetStreamPushSubscribe(js).pushSubscribe(stream, consumerName);
 * JetStreamPushSubscription sub = JetStreamPushSubscribe.pushSubscribe(js, stream, consumerName);
 * </pre>
 * See {@link JetStreamPullSubscribe} for pull subscriptions.
 */
@NullMarked
public class JetStreamPushSubscribe {
    private final JetStream js;

    /**
     * Make the push subscribe methods for a JetStream context.
     * @param js the JetStream context
     */
    public JetStreamPushSubscribe(JetStream js) {
        this.js = Validator.required(js, "JetStream");
    }

    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param consumerInfo describes the consumer to bind to
     * @return the push subscription
     */
    public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo) {
        return pushSubscribe(js, consumerInfo);
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
        return pushSubscribe(js, consumerInfo, messageHandler);
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
        return pushSubscribe(js, consumerInfo, subscribeBehavior);
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
        return pushSubscribe(js, stream, consumerName);
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
        return pushSubscribe(js, stream, consumerName, messageHandler);
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
        return pushSubscribe(js, stream, consumerName, subscribeBehavior);
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
        return pushSubscribe(js, subject);
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
        return pushSubscribe(js, subject, messageHandler);
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
        return pushSubscribe(js, subject, subscribeBehavior);
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
        return pushSubscribe(js, stream, creator);
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
        return pushSubscribe(js, stream, creator, messageHandler);
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
        return pushSubscribe(js, stream, creator, subscribeBehavior);
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
        return pushSubscribe(js, stream, creator);
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
        return pushSubscribe(js, stream, creator, messageHandler);
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
        return pushSubscribe(js, stream, creator, subscribeBehavior);
    }
    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @return the push subscription
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, ConsumerInfo consumerInfo) {
        return (JetStreamPushSubscription) js._createJsSubscription(consumerInfo, null, null, null);
    }

    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, ConsumerInfo consumerInfo, MessageHandler messageHandler) {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) js._createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the consumer described by {@code consumerInfo}. The subscription
     * is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) js._createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, String consumerName) throws JetStreamException, InterruptedException {
        return (JetStreamPushSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are delivered asynchronously to {@code messageHandler} on a
     * newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Push subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. The subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are consumed synchronously from the
     * returned subscription.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String subject) throws JetStreamException, InterruptedException {
        return pushSubscribe(js, subject, new SubscribeBehavior());
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are delivered asynchronously to
     * {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code messageHandler} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pushSubscribe(js, subject, subscribeBehavior);
    }

    /**
     * Push subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. The subscription is set up according to
     * {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code subscribeBehavior} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        subject = validateSubject(subject, true);
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        String stream = js.lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PushConsumerCreator creator = new PushConsumerCreator().filterSubject(subject);
        return pushSubscribe(js, stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushConsumerCreator creator) throws JetStreamException, InterruptedException {
        return pushSubscribe(js, stream, creator, new SubscribeBehavior());
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPushSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe, creating the consumer described by {@code creator} on {@code stream}. The
     * subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        return pushSubscribe(js, stream, creator, new SubscribeBehavior());
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are delivered asynchronously to {@code messageHandler} on a newly
     * created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pushSubscribe(js, stream, creator, subscribeBehavior);
    }

    /**
     * Push subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. The subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the push subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public static JetStreamPushSubscription pushSubscribe(JetStream js, String stream, PushOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPushSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }
}
