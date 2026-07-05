package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.utils.MessageSupplier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

class NatsIterableMessageConsumer extends NatsMessageConsumer implements IterableMessageConsumer {

    NatsIterableMessageConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, ConsumeOptions opts) throws IOException, JetStreamApiException {
        super(subscriptionMaker, cachedConsumerInfo, opts, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(long timeoutMillis) throws InterruptedException, JetStreamStatusCheckedException {
        return process(() -> sub.nextMessage(timeoutMillis));
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException, JetStreamStatusCheckedException {
        return process(() -> sub.nextMessage(timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessageNoWait() throws InterruptedException, JetStreamStatusCheckedException {
        return process(sub::nextMessageNoWait);
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessageWaitForever() throws InterruptedException, JetStreamStatusCheckedException {
        return process(sub::nextMessageWaitForever);
    }

    // Shared status/processed handling for all four nextMessage variants.
    private Message process(MessageSupplier supplier) throws InterruptedException, JetStreamStatusCheckedException {
        try {
            Message msg = supplier.get();
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
