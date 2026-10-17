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
 * The JetStream pull subscribe methods. Use them through an instance made from a {@link JetStream},
 * or as static methods that take the {@link JetStream} as the first parameter:
 * <pre>
 * JetStreamPullSubscription sub = new JetStreamPullSubscribe(js).pullSubscribe(stream, consumerName);
 * JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(js, stream, consumerName);
 * </pre>
 * See {@link JetStreamPushSubscribe} for push subscriptions.
 */
@NullMarked
public class JetStreamPullSubscribe {
    private final JetStream js;

    /**
     * Make the pull subscribe methods for a JetStream context.
     * @param js the JetStream context
     */
    public JetStreamPullSubscribe(JetStream js) {
        this.js = Validator.required(js, "JetStream");
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param consumerInfo describes the consumer to bind to
     * @return the pull subscription
     */
    public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo) {
        return pullSubscribe(js, consumerInfo);
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
        return pullSubscribe(js, consumerInfo, messageHandler);
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
        return pullSubscribe(js, consumerInfo, subscribeBehavior);
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
        return pullSubscribe(js, stream, consumerName);
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
        return pullSubscribe(js, stream, consumerName, messageHandler);
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
        return pullSubscribe(js, stream, consumerName, subscribeBehavior);
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
        return pullSubscribe(js, subject);
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
        return pullSubscribe(js, subject, messageHandler);
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
        return pullSubscribe(js, subject, subscribeBehavior);
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
        return pullSubscribe(js, stream, creator);
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
        return pullSubscribe(js, stream, creator, messageHandler);
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
        return pullSubscribe(js, stream, creator, subscribeBehavior);
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
        return pullSubscribe(js, stream, creator);
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
        return pullSubscribe(js, stream, creator, messageHandler);
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
        return pullSubscribe(js, stream, creator, subscribeBehavior);
    }
    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @return the pull subscription
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, ConsumerInfo consumerInfo) {
        return (JetStreamPullSubscription) js._createJsSubscription(consumerInfo, null, null, null);
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. Messages are
     * delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, ConsumerInfo consumerInfo, MessageHandler messageHandler) {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) js._createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the consumer described by {@code consumerInfo}. The subscription
     * is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param consumerInfo describes the consumer to bind to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) js._createJsSubscription(consumerInfo, subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, String consumerName) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), null, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. Messages are delivered asynchronously to {@code messageHandler} on a
     * newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code messageHandler} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe, binding to the existing consumer {@code consumerName} on {@code stream}, which
     * is looked up on the server. The subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param consumerName the name of the existing consumer
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if {@code subscribeBehavior} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) js._createJsSubscription(js.strictGetConsumerInfo(stream, consumerName), subscribeBehavior, null, null);
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are consumed synchronously from the
     * returned subscription.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String subject) throws JetStreamException, InterruptedException {
        return pullSubscribe(js, subject, new SubscribeBehavior());
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. Messages are delivered asynchronously to
     * {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code messageHandler} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return pullSubscribe(js, subject, subscribeBehavior);
    }

    /**
     * Pull subscribe to {@code subject}. The single stream carrying the subject is looked up and a new
     * ephemeral, unnamed consumer is created on it. The subscription is set up according to
     * {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param subject the subject to subscribe to
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the subject is invalid or {@code subscribeBehavior} is null
     * @throws IllegalStateException if no single stream carries the subject
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        subject = validateSubject(subject, true);
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        String stream = js.lookupStreamBySubject(subject);
        if (stream == null) {
            throw JsSubNoMatchingStreamForSubject.instance();
        }
        PullConsumerCreator creator = new PullConsumerCreator().filterSubject(subject);
        return pullSubscribe(js, stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, null);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. Messages
     * are delivered asynchronously to {@code messageHandler} on a newly created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe, creating the consumer described by {@code creator} on {@code stream}. The
     * subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are consumed synchronously from the returned subscription.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, null);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. Messages are delivered asynchronously to {@code messageHandler} on a newly
     * created {@link Dispatcher}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param messageHandler the handler used to consume messages asynchronously; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code messageHandler} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
        Validator.required(messageHandler, "MessageHandler");
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior().handler(messageHandler);
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }

    /**
     * Pull subscribe with an ordered consumer, creating the consumer described by {@code creator} on
     * {@code stream}. The subscription is set up according to {@code subscribeBehavior}.
     * @param js the JetStream context
     * @param stream the stream name
     * @param creator describes the ordered consumer to create
     * @param subscribeBehavior the behavior controlling the subscription; required
     * @return the pull subscription
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     * @throws IllegalArgumentException if the stream name is null, empty, or not a valid stream name, or if {@code subscribeBehavior} is null
     */
    public static JetStreamPullSubscription pullSubscribe(JetStream js, String stream, PullOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
        Validator.required(subscribeBehavior, "SubscribeBehavior");
        return (JetStreamPullSubscription) js._createConsumerAndSubscription(stream, creator, subscribeBehavior);
    }
}
