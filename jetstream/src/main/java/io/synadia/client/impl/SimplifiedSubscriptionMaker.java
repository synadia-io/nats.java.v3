package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.JetStreamException;
import org.jspecify.annotations.Nullable;


/**
 * Makes the underlying pull subscription that backs a simplified consumer. The simplified
 * consumer creates a new subscription for each fetch, next or consume operation.
 */
public interface SimplifiedSubscriptionMaker {
    /**
     * Make a pull subscription.
     * @param optionalMessageHandler the handler, null for a synchronous subscription
     * @param optionalDispatcher the dispatcher to use, null to let the connection supply one
     * @param optionalPmm the message manager to use, null for the default
     * @param optionalInactiveThreshold the consumer inactive threshold in milliseconds, null for the default
     * @return the subscription
     * @throws JetStreamException if the consumer or the subscription cannot be created
     * @throws InterruptedException if the underlying server round trip is interrupted
     */
    JetStreamPullSubscription subscribe(@Nullable MessageHandler optionalMessageHandler,
                                        @Nullable NatsDispatcher optionalDispatcher,
                                        @Nullable PullMessageManager optionalPmm,
                                        @Nullable Long optionalInactiveThreshold)
        throws JetStreamException, InterruptedException;
}
