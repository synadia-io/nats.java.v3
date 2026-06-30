package io.synadia.client.impl;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.impl.MarkerMessage.POISON_PILL;

abstract class MessageQueueBase {
    protected static final int PAUSED = 0;
    protected static final int RUNNING = 1;
    protected static final int DRAINING = 2;

    protected final int queueCapacity;
    protected final LinkedBlockingQueue<NatsMessage> queue;
    protected final AtomicLong length;
    protected final AtomicLong sizeInBytes;
    protected final AtomicInteger running;

    MessageQueueBase() {
        this(Integer.MAX_VALUE);
    }

    MessageQueueBase(int queueCapacity) {
        this.queueCapacity = queueCapacity > 0 ? queueCapacity : Integer.MAX_VALUE;
        queue = new LinkedBlockingQueue<>(this.queueCapacity);
        length = new AtomicLong(0);
        sizeInBytes = new AtomicLong(0);
        running = new AtomicInteger(RUNNING);
    }

    boolean isRunning() {
        return running.get() != PAUSED;
    }

    boolean isPaused() {
        return running.get() == PAUSED;
    }

    boolean isDraining() {
        return running.get() == DRAINING;
    }

    boolean isDrained() {
        return running.get() == DRAINING && length.get() == 0;
    }

    void pause() {
        if (running.compareAndSet(RUNNING, PAUSED)) {
            queue.offer(POISON_PILL);
        }
    }

    void drain() {
        if (running.compareAndSet(RUNNING, DRAINING)) {
            queue.offer(POISON_PILL);
        }
    }

    void resume() {
        running.set(RUNNING);
    }

    long queueSize() {
        return queue.size();
    }

    long length() {
        return length.get();
    }

    long sizeInBytes() {
        return sizeInBytes.get();
    }

    // Poll a message off the queue, honoring the timeout convention shared by the whole incoming-message
    // reader chain (Subscription.nextMessage -> nextMessageInternal -> pop -> _poll). The timeout is a
    // @Nullable Long number of milliseconds/nanoseconds
    //   null           -> poll once and return immediately (whatever is buffered, or null) -- no waiting
    //   <= 0 (e.g. 0)  -> wait forever (until a message arrives, or pause() enqueues a POISON_PILL)
    //   > 0            -> wait up to that many milliseconds/nanoseconds
    // This method owns the actual wait, so it hands the unit straight to queue.poll. If the polled message
    // was a POISON_PILL, return null.
    @Nullable NatsMessage _poll(@Nullable Long timeoutMillis, TimeUnit timeoutUnit) throws InterruptedException {
        NatsMessage msg = null;
        if (timeoutMillis == null || this.isDraining()) { // try immediately (poll once)
            msg = queue.poll(); // may get null
        }
        else if (timeoutMillis <= 0) {
            // poll forever until a message
            // Calling pause will put a POISON_PILL so will break this loop
            while (isRunning()) {
                msg = queue.poll(3650, TimeUnit.DAYS);
                if (msg != null) {
                    break;
                }
            }
        }
        else {
            msg = queue.poll(timeoutMillis, timeoutUnit); // may get null
        }

        return msg == null || msg == POISON_PILL ? null : msg;
    }
}
