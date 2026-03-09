package io.synadia.client.impl;

import java.time.Duration;
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

    // this is just a helper method to poll a message from
    // the queue handling various forms of timeouts
    // if the polled message was a POISON_PILL, return null
    NatsMessage _poll(Duration timeout) throws InterruptedException {
        NatsMessage msg = null;

        if (timeout == null || this.isDraining()) { // try immediately
            msg = queue.poll(); // may get null
        }
        else {
            long nanos = timeout.toNanos();
            if (nanos < 1) {
                // A value < 1 means poll forever until a message
                // Calling pause will put a POISON_PILL so will break this loop
                while (isRunning()) {
                    msg = queue.poll(3650, TimeUnit.DAYS);
                    if (msg != null) {
                        break;
                    }
                }
            }
            else {
                msg = queue.poll(nanos, TimeUnit.NANOSECONDS); // may get null
            }
        }

        return msg == null || msg == POISON_PILL ? null : msg;
    }
}
