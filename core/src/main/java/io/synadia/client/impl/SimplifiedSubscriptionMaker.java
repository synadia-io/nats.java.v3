package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.JetStreamApiException;
import io.synadia.client.MessageHandler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

interface SimplifiedSubscriptionMaker {
    NatsJetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                            @Nullable Dispatcher optionalDispatcher,
                                            @Nullable PullMessageManager optionalPmm,
                                            @Nullable Long optionalInactiveThreshold)
        throws IOException, JetStreamApiException;
}
