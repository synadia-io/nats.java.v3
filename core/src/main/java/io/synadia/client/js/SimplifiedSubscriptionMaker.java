package io.synadia.client.js;

import io.synadia.client.JetStreamApiException;
import io.synadia.client.MessageHandler;
import io.synadia.client.impl.NatsDispatcher;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface SimplifiedSubscriptionMaker {
    JetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                        @Nullable NatsDispatcher optionalDispatcher,
                                        @Nullable PullMessageManager optionalPmm,
                                        @Nullable Long optionalInactiveThreshold)
        throws IOException, JetStreamApiException;
}
