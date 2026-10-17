// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import org.jspecify.annotations.NullMarked;

/**
 * The two ways to call the push and pull subscribe methods. A test parameterized on this makes the same
 * calls through the {@link JetStreamPushSubscribe} / {@link JetStreamPullSubscribe} instance methods and through their
 * static methods.
 */
@NullMarked
public enum JsSubscribeForm {
    INSTANCE,
    STATIC;

    public JetStreamPushSubscribe push(JetStream js) {
        return this == INSTANCE ? new JetStreamPushSubscribe(js) : new StaticPush(js);
    }

    public JetStreamPullSubscribe pull(JetStream js) {
        return this == INSTANCE ? new JetStreamPullSubscribe(js) : new StaticPull(js);
    }

    // Each method calls the static method with the same parameters
    private static class StaticPush extends JetStreamPushSubscribe {
        private final JetStream js;

        StaticPush(JetStream js) {
            super(js);
            this.js = js;
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo) {
            return JetStreamPushSubscribe.pushSubscribe(js, consumerInfo);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler) {
            return JetStreamPushSubscribe.pushSubscribe(js, consumerInfo, messageHandler);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
            return JetStreamPushSubscribe.pushSubscribe(js, consumerInfo, subscribeBehavior);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, consumerName);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, consumerName, messageHandler);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, consumerName, subscribeBehavior);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String subject) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, subject);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, subject, messageHandler);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, subject, subscribeBehavior);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator, messageHandler);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator, subscribeBehavior);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator, messageHandler);
        }

        @Override
        public JetStreamPushSubscription pushSubscribe(String stream, PushOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPushSubscribe.pushSubscribe(js, stream, creator, subscribeBehavior);
        }
    }

    // Each method calls the static method with the same parameters
    private static class StaticPull extends JetStreamPullSubscribe {
        private final JetStream js;

        StaticPull(JetStream js) {
            super(js);
            this.js = js;
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo) {
            return JetStreamPullSubscribe.pullSubscribe(js, consumerInfo);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler) {
            return JetStreamPullSubscribe.pullSubscribe(js, consumerInfo, messageHandler);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior) {
            return JetStreamPullSubscribe.pullSubscribe(js, consumerInfo, subscribeBehavior);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, consumerName);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, consumerName, messageHandler);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, consumerName, subscribeBehavior);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String subject) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, subject);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, subject, messageHandler);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, subject, subscribeBehavior);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator, messageHandler);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator, subscribeBehavior);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator, messageHandler);
        }

        @Override
        public JetStreamPullSubscription pullSubscribe(String stream, PullOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException {
            return JetStreamPullSubscribe.pullSubscribe(js, stream, creator, subscribeBehavior);
        }
    }
}
