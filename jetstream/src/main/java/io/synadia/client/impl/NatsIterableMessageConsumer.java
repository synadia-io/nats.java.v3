package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

class NatsIterableMessageConsumer extends NatsMessageConsumer implements IterableMessageConsumer {

    NatsIterableMessageConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, ConsumeOptions opts) throws IOException, JetStreamApiException {
        super(subscriptionMaker, cachedConsumerInfo, opts, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message nextMessage(@Nullable Long timeoutMillis) throws InterruptedException, JetStreamStatusCheckedException {
        try {
            Message msg = sub.nextMessage(timeoutMillis);
            if (msg != null) {
                updateProcessed(msg);
            }
            return msg;
        }
        catch (JetStreamStatusException e) {
            throw new JetStreamStatusCheckedException(e);
        }
        catch (IllegalStateException i) {
            // this happens if the consumer is stopped, since it is
            // drained/unsubscribed, so don't pass it on if it's expected
            return null;
        }
    }
}
