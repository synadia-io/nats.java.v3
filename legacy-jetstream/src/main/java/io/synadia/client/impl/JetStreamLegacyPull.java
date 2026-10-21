// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.synadia.client.impl.JetStreamSubscription.EXPIRE_ADJUSTMENT;
import static io.synadia.client.impl.MessageManager.ManageResult.MESSAGE;
import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;

/**
 * The legacy pull behaviors fetch, iterate and reader, built on a {@link JetStreamPullSubscription}.
 * They exist only to support legacy behavior; the preferred way to consume is {@link ConsumerContext}.
 * Use them through an instance made from the subscription, or as static methods that take the
 * subscription as the first parameter:
 * <pre>
 * List&lt;Message&gt; messages = new JetStreamLegacyPull(sub).fetch(10, 1000);
 * List&lt;Message&gt; messages = JetStreamLegacyPull.fetch(sub, 10, 1000);
 * </pre>
 * The methods validate their arguments and throw {@link IllegalArgumentException} for invalid values.
 */
@NullMarked
public class JetStreamLegacyPull {

    /**
     * Shortest max wait, in millis, that still gets {@link JetStreamSubscription#EXPIRE_ADJUSTMENT} applied, {@value}.
     * Below this the wait is sent to the server as is.
     */
    public static final long MIN_EXPIRE_MILLIS = 20;

    private final JetStreamPullSubscription sub;

    /**
     * Make the legacy pull behaviors for a pull subscription.
     * @param sub the pull subscription
     */
    public JetStreamLegacyPull(JetStreamPullSubscription sub) {
        this.sub = Validator.required(sub, "Subscription");
    }

    /**
     * Fetch a list of messages up to the batch size, waiting no longer than maxWait.
     * See {@link #fetch(JetStreamPullSubscription, int, long)}.
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait to collect messages for the batch, in milliseconds.
     * @return the list of messages
     * @throws IllegalArgumentException if maxWaitMillis is not greater than zero
     * @throws JetStreamStatusException if the server sends an error status for the fetch
     */
    public List<Message> fetch(int batchSize, long maxWaitMillis) {
        return fetch(sub, batchSize, maxWaitMillis);
    }

    /**
     * Prepares an iterator. See {@link #iterate(JetStreamPullSubscription, int, long)}.
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait for the first message, in milliseconds.
     * @return the message iterator
     * @throws IllegalArgumentException if maxWaitMillis is not greater than zero
     * @throws JetStreamStatusException from the returned iterator, if the server sends an error status for the read
     */
    public Iterator<Message> iterate(int batchSize, long maxWaitMillis) {
        return iterate(sub, batchSize, maxWaitMillis);
    }

    /**
     * Prepares a reader. See {@link #reader(JetStreamPullSubscription, int, int)}.
     * @param batchSize the size of the batch
     * @param repullAt the point in the current batch to tell the server to start the next batch
     * @return the reader
     */
    public JetStreamReader reader(int batchSize, int repullAt) {
        return reader(sub, batchSize, repullAt);
    }

    /**
     * Fetch a list of messages up to the batch size, waiting no longer than maxWait.
     * This uses {@code pullExpiresIn} under the covers, and manages all responses
     * from {@code sub.nextMessage(...)} to only return regular JetStream messages.
     *
     * @param sub the pull subscription
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait to collect messages for the batch, in milliseconds.
     *
     * @return the list of messages
     * @throws IllegalArgumentException if maxWaitMillis is not greater than zero
     * @throws JetStreamStatusException if the server sends an error status for the fetch
     */
    public static List<Message> fetch(JetStreamPullSubscription sub, int batchSize, long maxWaitMillis) {
        durationGtZeroRequired(maxWaitMillis, "Fetch");
        List<Message> messages = drainAlreadyBuffered(sub, batchSize);

        int batchLeft = batchSize - messages.size();
        if (batchLeft == 0) {
            return messages;
        }

        try {
            long start = NatsSystemClock.nanoTime();

            long expiresMillis = maxWaitMillis > MIN_EXPIRE_MILLIS ? maxWaitMillis - EXPIRE_ADJUSTMENT : maxWaitMillis;
            String pullSubject = sub._pull(PullRequestOptions.builder(batchLeft).expiresIn(expiresMillis).build(), false, null);

            // timeout > 0 process as many messages we can in that time period
            // If we get a message that either manager handles, we try again, but
            // with a shorter timeout based on what we already used up
            long maxWaitNanos = maxWaitMillis * NANOS_PER_MILLI;
            long timeLeftNanos = maxWaitNanos;
            while (batchLeft > 0 && timeLeftNanos > 0) {
                Message msg = sub._nextMessage(timeLeftNanos, TimeUnit.NANOSECONDS);
                if (msg == null) {
                    return messages; // normal timeout
                }
                switch (sub.manager.manage(msg)) {
                    case MESSAGE:
                        messages.add(msg);
                        batchLeft--;
                        break;
                    case MessageManager.ManageResult.STATUS_TERMINUS:
                        // if there is a match, the status applies otherwise it's ignored
                        if (pullSubject.equals(msg.getSubject())) {
                            return messages;
                        }
                        break;
                    case MessageManager.ManageResult.STATUS_ERROR:
                        // if there is a match, the status applies otherwise it's ignored
                        if (pullSubject.equals(msg.getSubject())) {
                            throw new JetStreamStatusException(msg.getStatus(), sub);
                        }
                        break;
                }
                // anything else, try again while we have time
                timeLeftNanos = maxWaitNanos - (NatsSystemClock.nanoTime() - start);
            }
        }
        catch (InterruptedException e) {
            // _nextMessage failed. By not throwing
            // this gives them the messages already added to the list
            Thread.currentThread().interrupt();
        }
        return messages;
    }

    /**
     * Prepares an iterator. This uses {@code pullExpiresIn} under the covers,
     * and manages all responses. The iterator will have no messages if it does not
     * receive the first message within the max wait period. It will stop if the batch is
     * fulfilled or if there are fewer than batch size messages. 408 Status messages
     * are ignored and will not count toward the fulfilled batch size.
     *
     * @param sub the pull subscription
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait for the first message, in milliseconds.
     *
     * @return the message iterator
     * @throws IllegalArgumentException if maxWaitMillis is not greater than zero
     * @throws JetStreamStatusException from the returned iterator, if the server sends an error status for the read
     */
    public static Iterator<Message> iterate(JetStreamPullSubscription sub, int batchSize, long maxWaitMillis) {
        durationGtZeroRequired(maxWaitMillis, "Iterate");
        final List<Message> buffered = drainAlreadyBuffered(sub, batchSize);

        // if there was a full batch buffered, no need to pull, just iterate over the list you already have
        int batchLeft = batchSize - buffered.size();
        if (batchLeft == 0) {
            return new Iterator<>() {
                public boolean hasNext() {
                    return buffered.size() > 0;
                }
                public Message next() {
                    return buffered.remove(0);
                }
            };
        }

        // if there were some messages buffered, reduce the raw pull batch size
        String pullSubject = sub._pull(PullRequestOptions.builder(batchLeft).expiresIn(maxWaitMillis).build(), false, null);

        // the iterator is also more complicated
        return new Iterator<>() {
            int received = 0;
            boolean done = false;
            @Nullable Message msg = null;

            public boolean hasNext() {
                try {
                    if (msg != null) {
                        return true;
                    }

                    if (done) {
                        return false;
                    }

                    if (buffered.isEmpty()) {
                        msg = sub._nextUnmanaged(maxWaitMillis, TimeUnit.MILLISECONDS, pullSubject);
                        if (msg == null) {
                            done = true;
                            return false;
                        }
                    }
                    else {
                        msg = buffered.remove(0);
                    }

                    done = ++received == batchSize;
                    return true;
                }
                catch (InterruptedException e) {
                    msg = null;
                    done = true;
                    // _nextUnmanaged failed
                    // there still could be messages in the buffer
                    // no good choice here
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            public @Nullable Message next() {
                @Nullable Message next = msg;
                msg = null;
                return next;
            }
        };
    }

    /**
     * Prepares a reader. A reader looks like a push sync subscription,
     * meaning it is just an endless stream of messages to ask for by nextMessage,
     * but uses pull under the covers.
     *
     * @param sub the pull subscription
     * @param batchSize the size of the batch
     * @param repullAt the point in the current batch to tell the server to start the next batch
     *
     * @return the reader
     */
    public static JetStreamReader reader(JetStreamPullSubscription sub, int batchSize, int repullAt) {
        return new JetStreamReaderImpl(sub, batchSize, repullAt);
    }

    private static List<Message> drainAlreadyBuffered(JetStreamPullSubscription sub, int batchSize) {
        List<Message> messages = new ArrayList<>(batchSize);
        try {
            while (true) {
                Message msg = sub._nextMessage(null, TimeUnit.MILLISECONDS); // raw poll once; drain manages messages itself
                if (msg == null) {
                    return messages; // no more message currently queued
                }
                if (sub.manager.manage(msg) == MESSAGE) {
                    messages.add(msg);
                    if (messages.size() == batchSize) {
                        return messages;
                    }
                }
                // since this is buffered, no non-message applies, try again
            }
        }
        catch (InterruptedException ignore) {
            // _nextMessage failed. By not throwing
            // this gives them the messages already added to the list
            Thread.currentThread().interrupt();
        }
        return messages;
    }

    private static void durationGtZeroRequired(long millis, String label) {
        if (millis <= 0) {
            throw new IllegalArgumentException(label + " wait duration must be supplied and greater than 0.");
        }
    }

    static class JetStreamReaderImpl implements JetStreamReader {
        private final JetStreamPullSubscription sub;
        private final int batchSize;
        private final int repullAt;
        private int currentBatchRed;
        private boolean keepGoing = true;
        public JetStreamReaderImpl(final JetStreamPullSubscription sub, final int batchSize, final int repullAt) {
            this.sub = sub;
            this.batchSize = batchSize;
            this.repullAt = Math.clamp(batchSize, 1, repullAt);
            currentBatchRed = 0;
            sub.pull(batchSize);
        }

        public @Nullable Message nextMessage(long timeoutMillis) throws InterruptedException {
            return track(sub.nextMessage(timeoutMillis));
        }

        public @Nullable Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException {
            return track(sub.nextMessage(timeout, unit));
        }

        public @Nullable Message nextMessageNoWait() throws InterruptedException {
            return track(sub.nextMessageNoWait());
        }

        public @Nullable Message nextMessageWaitForever() throws InterruptedException {
            return track(sub.nextMessageWaitForever());
        }

        private @Nullable Message track(@Nullable Message msg) {
            if (msg != null) {
                if (++currentBatchRed == repullAt) {
                    if (keepGoing) {
                        sub.pull(batchSize);
                    }
                }
                if (currentBatchRed == batchSize) {
                    currentBatchRed = 0;
                }
            }
            return msg;
        }

        public void stop() {
            keepGoing = false;
        }
    }
}
