package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface SimplifiedSubscriptionMaker {
    JetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                        @Nullable NatsDispatcher optionalDispatcher,
                                        @Nullable PullMessageManager optionalPmm,
                                        @Nullable Long optionalInactiveThreshold)
        throws IOException, JetStreamApiException;
}
