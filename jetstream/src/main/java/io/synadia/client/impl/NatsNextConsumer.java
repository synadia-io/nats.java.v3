package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;

import static io.synadia.client.impl.JetStreamSubscription.EXPIRE_ADJUSTMENT;

class NatsNextConsumer extends NatsMessageConsumerBase {
    final long maxWaitMillis;

    NatsNextConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, long maxWaitMillis) throws JetStreamException, InterruptedException {
        super(cachedConsumerInfo);
        this.maxWaitMillis = maxWaitMillis;
        long inactiveThreshold = maxWaitMillis * 110 / 100; // 10% longer than the wait

        initSub(subscriptionMaker.subscribe(null, null, null, inactiveThreshold), false);
        setConsumerName(consumerName); // the call to subscribe sets this
        sub._pull(PullRequestOptions.builder(1)
            .expiresIn(maxWaitMillis - EXPIRE_ADJUSTMENT)
            .build(), false, null);
    }

    @Override
    public void messageReceived(Message msg) {}

    @Override
    public void pullCompletedWithStatus(int messages, long bytes) {
        stop();
    }

    @Override
    public void pullTerminatedByError() {
        fullClose();
    }

    Message getMessage() throws InterruptedException, IllegalStateException {
        try {
            // it is stopped if it got a status error or was
            // terminated by error, so there is no message
            if (stopped.get()) {
                return null;
            }
            return sub.nextMessage(maxWaitMillis);
        }
        finally {
            fullClose();
        }
    }
}
