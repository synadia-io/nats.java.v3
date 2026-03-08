package io.nats.client.impl;

import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.MessageHandler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

interface SimplifiedSubscriptionMaker {
    NatsJetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                            @Nullable Dispatcher optionalDispatcher,
                                            @Nullable PullMessageManager optionalPmm,
                                            @Nullable Long optionalInactiveThreshold)
        throws IOException, JetStreamApiException;
}
