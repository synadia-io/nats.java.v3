package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;

import java.io.IOException;
import java.time.Duration;

class NatsIterableConsumer extends NatsMessageConsumer implements IterableConsumer {

    NatsIterableConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, ConsumeOptions opts) throws IOException, JetStreamApiException {
        super(subscriptionMaker, cachedConsumerInfo, opts, null, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message nextMessage(Duration timeout) throws InterruptedException, JetStreamStatusCheckedException {
        try {
            Message msg = sub.nextMessage(timeout);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Message nextMessage(long timeoutMillis) throws InterruptedException, JetStreamStatusCheckedException {
        return nextMessage(Duration.ofMillis(timeoutMillis));
    }
}
