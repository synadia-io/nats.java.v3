package io.synadia.client.js;

import io.synadia.client.JetStreamApiException;
import io.synadia.client.Message;
import io.synadia.client.PullRequestOptions;
import io.synadia.client.jsapi.ConsumerInfo;

import java.io.IOException;

import static io.synadia.client.js.JetStreamSubscription.EXPIRE_ADJUSTMENT;

class NatsNextConsumer extends NatsMessageConsumerBase {
    final long maxWaitMillis;

    NatsNextConsumer(SimplifiedSubscriptionMaker subscriptionMaker, ConsumerInfo cachedConsumerInfo, long maxWaitMillis) throws IOException, JetStreamApiException {
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
