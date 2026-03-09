package io.synadia.client.impl;

import java.time.Duration;

class ConsumerMessageQueue extends MessageQueueBase {

    ConsumerMessageQueue() {
        super();
    }

    void push(NatsMessage msg) {
        if (queue.offer(msg)) {
            length.incrementAndGet();
            sizeInBytes.addAndGet(msg.getSizeInBytes());
        }
    }

    NatsMessage pop(Duration timeout) throws InterruptedException {
        if (!isRunning()) {
            return null;
        }

        NatsMessage msg = _poll(timeout);

        if (msg == null) {
            return null;
        }

        length.decrementAndGet();
        sizeInBytes.addAndGet(-msg.getSizeInBytes());
        return msg;
    }
}
