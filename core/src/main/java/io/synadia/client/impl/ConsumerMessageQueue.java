package io.synadia.client.impl;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * The queue holding messages delivered to a single subscription until the consumer pops them.
 */
@NullMarked
public class ConsumerMessageQueue extends MessageQueueBase {

    ConsumerMessageQueue() {
        super();
    }

    void push(NatsMessage msg) {
        if (queue.offer(msg)) {
            length.incrementAndGet();
            sizeInBytes.addAndGet(msg.getSizeInBytes());
        }
    }

    /**
     * timeoutMillis follows the shared reader-chain convention:
     *   null           -> poll once and return immediately (whatever is buffered, or null) -- no waiting
     *   <= 0 (e.g. 0)  -> wait forever (until a message arrives, including a POISON_PILL)
     *   > 0            -> wait up to that many time units
     * @param timeout the timeout amount
     * @param timeoutUnit the time unit of the timeout
     * @return a message or null
     * @throws InterruptedException if the polling was interrupted
     */
    @Nullable NatsMessage pop(@Nullable Long timeout, TimeUnit timeoutUnit) throws InterruptedException {
        if (!isRunning()) {
            return null;
        }

        NatsMessage msg = _poll(timeout, timeoutUnit);
        if (msg == null) {
            return null;
        }

        length.decrementAndGet();
        sizeInBytes.addAndGet(-msg.getSizeInBytes());
        return msg;
    }
}
