package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.global.NatsSystemClock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.impl.MessageManager.ManageResult.MESSAGE;

public class JetStreamPullSubscription extends JetStreamSubscription implements Subscription {

    private final AtomicLong pullSubjectIdHolder;

    JetStreamPullSubscription(String sid, String subject,
                              NatsConnection connection, NatsDispatcher dispatcher,
                              JetStream js,
                              JetStreamSubscribeConfig subConf,
                              MessageManager manager) {
        super(sid, subject, null, connection, dispatcher, js, subConf, manager);
        pullSubjectIdHolder = new AtomicLong();
    }

    /**
     * Initiate pull with the specified batch size.
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     */
    public void pull(int batchSize) {
        _pull(PullRequestOptions.builder(batchSize).build(), true, null);
    }

    /**
     * Initiate pull with the specified request options
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param pullRequestOptions the options object
     */
    public void pull(PullRequestOptions pullRequestOptions) {
        _pull(pullRequestOptions, true, null);
    }

    protected String _pull(PullRequestOptions pullRequestOptions, boolean raiseStatusWarnings, PullManagerObserver pullManagerObserver) {
        String publishSubject = js.prependPrefix(String.format(JSAPI_CONSUMER_MSG_NEXT, stream, consumerName));
        String pullSubject = getSubject().replace("*", Long.toString(this.pullSubjectIdHolder.incrementAndGet()));
        manager.startPullRequest(pullSubject, pullRequestOptions, raiseStatusWarnings, pullManagerObserver);
        connection.publish(publishSubject, pullSubject, null, pullRequestOptions.serialize(), connection.isForceFlushOnRequest());
        return pullSubject;
    }

    /**
     * Initiate pull in noWait mode with the specified batch size.
     * The fetch will return immediately with as many messages as are available. Between zero and the maximum configured.
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     */
    public void pullNoWait(int batchSize) {
        _pull(PullRequestOptions.noWait(batchSize).build(), true, null);
    }

    /**
     * Initiate pull in noWait mode with the specified batch size.
     * The fetch will return immediately with as many messages as are available, but at least one message. Between one and the maximum configured.
     * When no message is available it will wait for new messages to arrive till it expires.
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     * @param expiresIn how long from now this request should be expired from the server wait list
     */
    public void pullNoWait(int batchSize, Duration expiresIn) {
        durationGtZeroRequired(expiresIn, "NoWait Expires In");
        _pull(PullRequestOptions.noWait(batchSize).expiresIn(expiresIn).build(), true, null);
    }

    /**
     * Initiate pull in noWait mode with the specified batch size.
     * The fetch will return immediately with as many messages as are available, but at least one message. Between one and the maximum configured.
     * When no message is available it will wait for new messages to arrive till it expires.
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     * @param expiresInMillis how long from now this request should be expired from the server wait list, in milliseconds
     */
    public void pullNoWait(int batchSize, long expiresInMillis) {
        durationGtZeroRequired(expiresInMillis, "NoWait Expires In");
        _pull(PullRequestOptions.noWait(batchSize).expiresIn(expiresInMillis).build(), true, null);
    }

    /**
     * Initiate pull for all messages available before expiration.
     * <p>
     * <code>sub.nextMessage(timeout)</code> can return a:
     * <ul>
     * <li>regular JetStream message
     * <li>null
     * </ul>
     * <p>
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     * @param expiresIn how long from now this request should be expired from the server wait list
     */
    public void pullExpiresIn(int batchSize, Duration expiresIn) {
        durationGtZeroRequired(expiresIn, "Expires In");
        _pull(PullRequestOptions.builder(batchSize).expiresIn(expiresIn).build(), true, null);
    }

    /**
     * Initiate pull for all messages available before expiration.
     * This can only be used when the subscription is pull based.
     * <p>
     * <code>sub.nextMessage(timeout)</code> can return a:
     * <ul>
     * <li>regular JetStream message
     * <li>null
     * </ul>
     * <p>
     * ! Primitive API for ADVANCED use only, officially not supported. Prefer fetch, iterate or reader.
     *
     * @param batchSize the size of the batch
     * @param expiresInMillis how long from now this request should be expired from the server wait list, in milliseconds
     */
    public void pullExpiresIn(int batchSize, long expiresInMillis) {
        durationGtZeroRequired(expiresInMillis, "Expires In");
        _pull(PullRequestOptions.builder(batchSize).expiresIn(expiresInMillis).build(), true, null);
    }

    /**
     * Fetch a list of messages up to the batch size, waiting no longer than maxWait.
     * This uses <code>pullExpiresIn</code> under the covers, and manages all responses
     * from <code>sub.nextMessage(...)</code> to only return regular JetStream messages.
     * This can only be used when the subscription is pull based.
     *
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait to collect messages for the batch, in milliseconds.
     *
     * @return the list of messages
     */
    public List<Message> fetch(int batchSize, long maxWaitMillis) {
        durationGtZeroRequired(maxWaitMillis, "Fetch");
        return _fetch(batchSize, maxWaitMillis);
    }

    /**
     * Fetch a list of messages up to the batch size, waiting no longer than maxWait.
     * This uses <code>pullExpiresIn</code> under the covers, and manages all responses
     * from <code>sub.nextMessage(...)</code> to only return regular JetStream messages.
     * This can only be used when the subscription is pull based.
     *
     * @param batchSize the size of the batch
     * @param maxWait the maximum time to wait to collect messages for the batch.
     *
     * @return the list of messages
     */
    public List<Message> fetch(int batchSize, Duration maxWait) {
        durationGtZeroRequired(maxWait, "Fetch");
        return _fetch(batchSize, maxWait.toMillis());
    }

    private List<Message> _fetch(int batchSize, long maxWaitMillis) {
        List<Message> messages = drainAlreadyBuffered(batchSize);

        int batchLeft = batchSize - messages.size();
        if (batchLeft == 0) {
            return messages;
        }

        try {
            long start = NatsSystemClock.nanoTime();

            Duration expires = Duration.ofMillis(
                maxWaitMillis > MIN_EXPIRE_MILLIS ? maxWaitMillis - EXPIRE_ADJUSTMENT : maxWaitMillis);
            String pullSubject = _pull(PullRequestOptions.builder(batchLeft).expiresIn(expires).build(), false, null);

            // timeout > 0 process as many messages we can in that time period
            // If we get a message that either manager handles, we try again, but
            // with a shorter timeout based on what we already used up
            long maxWaitNanos = maxWaitMillis * 1_000_000;
            long timeLeftNanos = maxWaitNanos;
            while (batchLeft > 0 && timeLeftNanos > 0) {
                Message msg = nextMessageInternal( Duration.ofNanos(timeLeftNanos) );
                if (msg == null) {
                    return messages; // normal timeout
                }
                switch (manager.manage(msg)) {
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
                            throw new JetStreamStatusException(msg.getStatus(), this);
                        }
                        break;
                }
                // anything else, try again while we have time
                timeLeftNanos = maxWaitNanos - (NatsSystemClock.nanoTime() - start);
            }
        }
        catch (InterruptedException e) {
            // nextMessageInternal failed. By not throwing
            // this gives them the messages already added to the list
            Thread.currentThread().interrupt();
        }
        return messages;
    }

    private List<Message> drainAlreadyBuffered(int batchSize) {
        List<Message> messages = new ArrayList<>(batchSize);
        try {
            while (true) {
                Message msg = nextMessageInternal(null);
                if (msg == null) {
                    return messages; // no more message currently queued
                }
                if (manager.manage(msg) == MESSAGE) {
                    messages.add(msg);
                    if (messages.size() == batchSize) {
                        return messages;
                    }
                }
                // since this is buffered, no non-message applies, try again
            }
        }
        catch (InterruptedException ignore) {
            // nextMessageInternal failed. By not throwing
            // this gives them the messages already added to the list
            Thread.currentThread().interrupt();
        }
        return messages;
    }

    private void durationGtZeroRequired(Duration duration, String label) {
        if (duration == null || duration.toMillis() <= 0) {
            throw new IllegalArgumentException(label + " wait duration must be supplied and greater than 0.");
        }
    }

    private void durationGtZeroRequired(long millis, String label) {
        if (millis <= 0) {
            throw new IllegalArgumentException(label + " wait duration must be supplied and greater than 0.");
        }
    }

    /**
     * Prepares an iterator. This uses <code>pullExpiresIn</code> under the covers,
     * and manages all responses. The iterator will have no messages if it does not
     * receive the first message within the max wait period. It will stop if the batch is
     * fulfilled or if there are fewer than batch size messages. 408 Status messages
     * are ignored and will not count toward the fulfilled batch size.
     *
     * @param batchSize the size of the batch
     * @param maxWait the maximum time to wait for the first message.
     *
     * @return the message iterator
     */
    public Iterator<Message> iterate(int batchSize, Duration maxWait) {
        durationGtZeroRequired(maxWait, "Iterate");
        return _iterate(batchSize, maxWait.toNanos());
    }

    /**
     * Prepares an iterator. This uses <code>pullExpiresIn</code> under the covers,
     * and manages all responses. The iterator will have no messages if it does not
     * receive the first message within the max wait period. It will stop if the batch is
     * fulfilled or if there are fewer than batch size messages. 408 Status messages
     * are ignored and will not count toward the fulfilled batch size.
     *
     * @param batchSize the size of the batch
     * @param maxWaitMillis the maximum time to wait for the first message, in milliseconds.
     *
     * @return the message iterator
     */
    public Iterator<Message> iterate(final int batchSize, long maxWaitMillis) {
        durationGtZeroRequired(maxWaitMillis, "Iterate");
        return _iterate(batchSize, maxWaitMillis * NANOS_PER_MILLI);
    }

    private Iterator<Message> _iterate(final int batchSize, long maxWaitNanos) {
        final List<Message> buffered = drainAlreadyBuffered(batchSize);

        // if there was a full batch buffered, no need to pull, just iterate over the list you already have
        int batchLeft = batchSize - buffered.size();
        if (batchLeft == 0) {
            return new Iterator<Message>() {
            
                public boolean hasNext() {
                    return buffered.size() > 0;
                }
            
                public Message next() {
                    return buffered.remove(0);
                }
            };
        }

        // if there were some messages buffered, reduce the raw pull batch size
        String pullSubject = _pull(PullRequestOptions.builder(batchLeft).expiresIn(Duration.ofNanos(maxWaitNanos)).build(), false, null);

        // the iterator is also more complicated
        return new Iterator<>() {
            int received = 0;
            boolean done = false;
            Message msg = null;
        
            public boolean hasNext() {
                try {
                    if (msg != null) {
                        return true;
                    }

                    if (done) {
                        return false;
                    }

                    if (buffered.isEmpty()) {
                        msg = _nextUnmanaged(maxWaitNanos, pullSubject);
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
        
            public Message next() {
                Message next = msg;
                msg = null;
                return next;
            }
        };
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
            this.repullAt = Math.max(1, Math.min(batchSize, repullAt));
            currentBatchRed = 0;
            sub.pull(batchSize);
        }
    
        public Message nextMessage(Duration timeout) throws InterruptedException {
            return track(sub.nextMessage(timeout));
        }
    
        public Message nextMessage(long timeoutMillis) throws InterruptedException {
            return track(sub.nextMessage(timeoutMillis));
        }

        private Message track(Message msg) {
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

    /**
     * Prepares a reader. A reader looks like a push sync subscription,
     * meaning it is just an endless stream of messages to ask for by nextMessage,
     * but uses pull under the covers.
     *
     * @param batchSize the size of the batch
     * @param repullAt the point in the current batch to tell the server to start the next batch
     *
     * @return the message iterator
     */
    public JetStreamReader reader(final int batchSize, final int repullAt) {
        return new JetStreamReaderImpl(this, batchSize, repullAt);
    }
}
