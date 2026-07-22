package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.JetStreamException;
import org.jspecify.annotations.Nullable;


public interface SimplifiedSubscriptionMaker {
    JetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                        @Nullable NatsDispatcher optionalDispatcher,
                                        @Nullable PullMessageManager optionalPmm,
                                        @Nullable Long optionalInactiveThreshold)
        throws JetStreamException, InterruptedException;
}
