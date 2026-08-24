package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.client.impl.BaseConsumeOptions.DEFAULT_EXPIRES_IN_MILLIS;
import static io.synadia.client.impl.BaseConsumeOptions.MIN_EXPIRES_MILLS;
import static io.synadia.client.impl.ConsumeOptions.DEFAULT_CONSUME_OPTIONS;
import static io.synadia.client.utils.Validator.required;

/**
 * Implementation of Consumer Context
 */
public class NatsConsumerContext implements ConsumerContext, SimplifiedSubscriptionMaker {
    private final ReentrantLock stateLock;
    private final NatsStreamContext streamCtx;
    private final boolean isOrdered;
    private final ConsumerInfo unorderedConsumerInfo;
    private final PullOrderedConsumerCreator initialPocc;

    private final AtomicReference<ConsumerInfo> cachedConsumerInfo;
    private final AtomicReference<String> consumerName;
    private final AtomicLong highestSeq;
    private final AtomicReference<NatsDispatcher> defaultDispatcher;
    private final AtomicReference<NatsMessageConsumerBase> lastConsumer;

    NatsConsumerContext(@NonNull NatsStreamContext streamCtx, @Nullable ConsumerInfo unorderedConsumerInfo, @Nullable PullOrderedConsumerCreator pocc) {
        stateLock = new ReentrantLock();
        this.streamCtx = streamCtx;
        this.unorderedConsumerInfo = unorderedConsumerInfo;
        cachedConsumerInfo = new AtomicReference<>();
        consumerName = new AtomicReference<>();
        highestSeq = new AtomicLong();
        defaultDispatcher = new AtomicReference<>();
        lastConsumer = new AtomicReference<>();

        if (unorderedConsumerInfo != null) {
            isOrdered = false;
            initialPocc = null;
            cachedConsumerInfo.set(unorderedConsumerInfo);
            consumerName.set(unorderedConsumerInfo.getConsumerConfiguration().getName());
        }
        else if (pocc != null) {
            isOrdered = true;
            initialPocc = pocc;
        }
        else {
            throw new IllegalArgumentException("Internal Error, must be ordered or unordered.");
        }
    }

    @Override
    public JetStreamPullSubscription subscribe(@Nullable MessageHandler messageHandler,
                                               @Nullable NatsDispatcher userDispatcher,
                                               @Nullable PullMessageManager optionalPmm,
                                               @Nullable Long optionalInactiveThreshold)
        throws JetStreamException, InterruptedException
    {
        if (isOrdered) {
            NatsMessageConsumerBase lastCon = lastConsumer.get();
            long lastStreamSeq = 0;
            if (lastCon != null) {
                lastStreamSeq = Math.max(highestSeq.get(), lastCon.pmm.getLastStreamSequence());
                highestSeq.set(lastStreamSeq);
            }
            ConsumerCreator<?> creator = new PullOrderedConsumerCreator(initialPocc, lastStreamSeq, optionalInactiveThreshold);
            ConsumerInfo ci = streamCtx.js._createConsumer(streamCtx.streamName, creator, ConsumerCreateRequest.Action.Create);
            cachedConsumerInfo.set(ci);
            consumerName.set(ci.getName());

            // Deliberately outside the try: this can only throw when the connection or the dispatcher is
            // closing, which is exactly when the delete could not be sent either, so covering it would buy
            // a doomed round trip on a consumer the server reaps on its own.
            SubscribeBehavior behavior = subscribeBehavior(messageHandler, userDispatcher);

            // This call made the consumer just above, so a failed subscribe has to delete it again. The
            // _createConsumer used the Create action, so its success proves the consumer did not exist beforehand
            // and deleting restores the state the caller started in.
            try {
                return (JetStreamPullSubscription) streamCtx.js._createJsSubscription(ci, behavior, initialPocc, optionalPmm);
            }
            catch (RuntimeException e) {
                // Best effort: everything after the consumer create throws IllegalStateException when the
                // connection or the dispatcher is closing or draining, and when the cause is the connection
                // the delete cannot be sent either, so the orphan survives. The cleanup never replaces the
                // original failure - that is the mistake jnats V2 issue #1596 was reported for.
                try {
                    streamCtx.js._deleteConsumer(streamCtx.streamName, ci.getName());
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    e.addSuppressed(ie);
                }
                catch (Exception de) {
                    e.addSuppressed(de);
                }
                throw e;
            }
        }

        // Bound to a consumer this call did not create, so a failed subscribe must leave it alone.
        return (JetStreamPullSubscription) streamCtx.js._createJsSubscription(unorderedConsumerInfo, subscribeBehavior(messageHandler, userDispatcher), initialPocc, optionalPmm);
    }

    // The handler decides everything here: no handler means a sync subscription with nothing to set.
    // The dispatcher is the caller's if they supplied one, otherwise this context's own, made once.
    private SubscribeBehavior subscribeBehavior(@Nullable MessageHandler messageHandler, @Nullable NatsDispatcher userDispatcher) {
        SubscribeBehavior subscribeBehavior = new SubscribeBehavior();
        if (messageHandler != null) {
            subscribeBehavior.handler(messageHandler);
            NatsDispatcher d = userDispatcher;
            if (d == null) {
                d = defaultDispatcher.get();
                if (d == null) {
                    d = streamCtx.js.conn.createDispatcher();
                    defaultDispatcher.set(d);
                }
            }
            subscribeBehavior.dispatcher(d);
        }
        return subscribeBehavior;
    }

    private void checkState() {
        NatsMessageConsumerBase lastCon = lastConsumer.get();
        if (lastCon != null && isOrdered && !lastCon.finished.get()) {
            throw new IllegalStateException("The ordered consumer is already receiving messages. Ordered Consumer does not allow multiple instances at time.");
        }
    }

    private void checkNotPinned(String label) {
        ConsumerInfo ci = cachedConsumerInfo.get();
        if (ci != null && ci.getConsumerConfiguration().getPriorityPolicy() == PriorityPolicy.PinnedClient) {
            throw new IllegalStateException("Pinned not allowed with " + label);
        }
    }

    private NatsMessageConsumerBase trackConsume(NatsMessageConsumerBase con) {
        lastConsumer.set(con);
        return con;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConsumerName() {
        return consumerName.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ConsumerInfo retrieveConsumerInfo() throws JetStreamException, InterruptedException {
        ConsumerInfo ci = streamCtx.jsm.getConsumerInfo(streamCtx.streamName, consumerName.get());
        cachedConsumerInfo.set(ci);
        consumerName.set(ci.getName());
        return ci;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public ConsumerInfo getCachedConsumerInfo() {
        return cachedConsumerInfo.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next() throws JetStreamException, InterruptedException {
        return next(DEFAULT_EXPIRES_IN_MILLIS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next(long maxWait) throws JetStreamException, InterruptedException {
        if (maxWait < MIN_EXPIRES_MILLS) {
            throw new IllegalArgumentException("Max wait must be at least " + MIN_EXPIRES_MILLS + " milliseconds.");
        }

        NatsNextConsumer nnc = null;
        try {
            stateLock.lock();
            checkState();
            checkNotPinned("Next");

            try {
                nnc = new NatsNextConsumer(this, isOrdered ? null : cachedConsumerInfo.get(), maxWait);
                trackConsume(nnc); // this has to be done after the nnc is fully set up
            }
            catch (Exception e) {
                if (nnc != null) {
                    nnc.fullClose();
                }
                return null;
            }
        }
        finally {
            stateLock.unlock();
        }

        // intentionally outside the lock
        return nnc.getMessage();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetchMessages(int maxMessages) throws JetStreamException, InterruptedException {
        return fetch(FetchConsumeOptions.builder().maxMessages(maxMessages).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetchBytes(int maxBytes) throws JetStreamException, InterruptedException {
        return fetch(FetchConsumeOptions.builder().maxBytes(maxBytes).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetch(@NonNull FetchConsumeOptions fetchConsumeOptions) throws JetStreamException, InterruptedException {
        required(fetchConsumeOptions, "Fetch Consume Options");
        try {
            stateLock.lock();
            checkState();
            checkNotPinned("Fetch");
            return (FetchMessageConsumer)trackConsume(new NatsFetchMessageConsumer(this, cachedConsumerInfo.get(), fetchConsumeOptions));
        }
        finally {
            stateLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public IterableMessageConsumer iterate() throws JetStreamException, InterruptedException {
        return iterate(DEFAULT_CONSUME_OPTIONS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public IterableMessageConsumer iterate(@NonNull ConsumeOptions consumeOptions) throws JetStreamException, InterruptedException {
        required(consumeOptions, "Consume Options");
        try {
            stateLock.lock();
            checkState();
            return (IterableMessageConsumer) trackConsume(new NatsIterableMessageConsumer(this, cachedConsumerInfo.get(), consumeOptions));
        }
        finally {
            stateLock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull MessageHandler handler) throws JetStreamException, InterruptedException {
        return consume(DEFAULT_CONSUME_OPTIONS, null, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@Nullable NatsDispatcher dispatcher,
                                   @NonNull MessageHandler handler) throws JetStreamException, InterruptedException {
        return consume(DEFAULT_CONSUME_OPTIONS, dispatcher, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull ConsumeOptions consumeOptions,
                                   @NonNull MessageHandler handler) throws JetStreamException, InterruptedException {
        return consume(consumeOptions, null, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull ConsumeOptions consumeOptions,
                                   @Nullable NatsDispatcher userDispatcher,
                                   @NonNull MessageHandler handler)
        throws JetStreamException, InterruptedException
    {
        required(consumeOptions, "Consume Options");
        required(handler, "Message Handler");
        try {
            stateLock.lock();
            checkState();
            return trackConsume(new NatsMessageConsumer(this, cachedConsumerInfo.get(), consumeOptions, userDispatcher, handler));
        }
        finally {
            stateLock.unlock();
        }
    }

    @Override
    public boolean unpin(String group) throws JetStreamException, InterruptedException {
        String name = consumerName.get();
        if (name == null) {
            ConsumerInfo ci = cachedConsumerInfo.get();
            if (ci == null) {
                ci = retrieveConsumerInfo();
            }
            name = ci.getName();
        }
        return streamCtx.jsm.unpinConsumer(streamCtx.streamName, name, group);
    }
}
