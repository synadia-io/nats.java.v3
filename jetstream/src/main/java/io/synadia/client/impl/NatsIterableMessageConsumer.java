package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.utils.MessageSupplier;

import java.util.concurrent.TimeUnit;

class NatsIterableMessageConsumer extends NatsMessageConsumer implements IterableMessageConsumer {

    NatsIterableMessageConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, ConsumeOptions opts) throws JetStreamException, InterruptedException {
        super(subscriptionMaker, cachedConsumerInfo, opts, null, null);
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(long timeoutMillis) throws InterruptedException, JetStreamStatusException {
        return process(() -> sub.nextMessage(timeoutMillis));
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException, JetStreamStatusException {
        return process(() -> sub.nextMessage(timeout, unit));
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessageNoWait() throws InterruptedException, JetStreamStatusException {
        return process(sub::nextMessageNoWait);
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessageWaitForever() throws InterruptedException, JetStreamStatusException {
        return process(sub::nextMessageWaitForever);
    }

    // Shared status/processed handling for all four nextMessage variants.
    private Message process(MessageSupplier supplier) throws InterruptedException, JetStreamStatusException {
        try {
            Message msg = supplier.get();
            if (msg != null) {
                updateProcessed(msg);
            }
            return msg;
        }
        catch (StatusException e) {
            // a status is the caller's to see, and it is an IllegalStateException, so it has to be
            // taken before the catch below, which is only about a consumer that was stopped
            throw e;
        }
        catch (IllegalStateException i) {
            // this happens if the consumer is stopped, since it is
            // drained/unsubscribed, so don't pass it on if it's expected
            return null;
        }
    }
}
