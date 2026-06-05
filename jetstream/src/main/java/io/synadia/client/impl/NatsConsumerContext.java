package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.time.Duration;
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
        throws IOException, JetStreamApiException
    {
        ConsumerInfo ci;
        if (isOrdered) {
            NatsMessageConsumerBase lastCon = lastConsumer.get();
            if (lastCon != null) {
                highestSeq.set(Math.max(highestSeq.get(), lastCon.pmm.getLastStreamSequence()));
            }
            ConsumerCreator<?> creator = new PullOrderedConsumerCreator(initialPocc, highestSeq.get());
            if (optionalInactiveThreshold != null) {
                creator.inactiveThreshold(optionalInactiveThreshold);
            }

            ci = streamCtx.js._createConsumer(creator, ConsumerCreateRequest.Action.Create);
            cachedConsumerInfo.set(ci);
            consumerName.set(ci.getName());
        }
        else {
            ci = unorderedConsumerInfo;
        }

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
        return (JetStreamPullSubscription) streamCtx.js.createSubscription(ci, subscribeBehavior, initialPocc, optionalPmm);
    }

    private void checkState() throws IOException {
        NatsMessageConsumerBase lastCon = lastConsumer.get();
        if (lastCon != null && isOrdered && !lastCon.finished.get()) {
            throw new IOException("The ordered consumer is already receiving messages. Ordered Consumer does not allow multiple instances at time.");
        }
    }

    private void checkNotPinned(String label) throws IOException {
        ConsumerInfo ci = cachedConsumerInfo.get();
        if (ci != null && ci.getConsumerConfiguration().getPriorityPolicy() == PriorityPolicy.PinnedClient) {
            throw new IOException("Pinned not allowed with " + label);
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
    public ConsumerInfo retrieveConsumerInfo() throws IOException, JetStreamApiException {
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
    public Message next() throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException {
        return next(DEFAULT_EXPIRES_IN_MILLIS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next(@Nullable Duration maxWait) throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException {
        return maxWait == null || maxWait.isZero() || maxWait.isNegative()
            ? next(DEFAULT_EXPIRES_IN_MILLIS)
            : next(maxWait.toMillis());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next(long maxWaitMillis) throws IOException, InterruptedException, JetStreamStatusCheckedException, JetStreamApiException {
        if (maxWaitMillis < MIN_EXPIRES_MILLS) {
            throw new IllegalArgumentException("Max wait must be at least " + MIN_EXPIRES_MILLS + " milliseconds.");
        }

        NatsNextConsumer nnc = null;
        try {
            stateLock.lock();
            checkState();
            checkNotPinned("Next");

            try {
                nnc = new NatsNextConsumer(this, cachedConsumerInfo.get(), maxWaitMillis);
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
    public FetchMessageConsumer fetchMessages(int maxMessages) throws IOException, JetStreamApiException {
        return fetch(FetchConsumeOptions.builder().maxMessages(maxMessages).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetchBytes(int maxBytes) throws IOException, JetStreamApiException {
        return fetch(FetchConsumeOptions.builder().maxBytes(maxBytes).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetch(@NonNull FetchConsumeOptions fetchConsumeOptions) throws IOException, JetStreamApiException {
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
    public IterableMessageConsumer iterate() throws IOException, JetStreamApiException {
        return iterate(DEFAULT_CONSUME_OPTIONS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public IterableMessageConsumer iterate(@NonNull ConsumeOptions consumeOptions) throws IOException, JetStreamApiException {
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
    public MessageConsumer consume(@NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return consume(DEFAULT_CONSUME_OPTIONS, null, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@Nullable NatsDispatcher dispatcher,
                                   @NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return consume(DEFAULT_CONSUME_OPTIONS, dispatcher, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull ConsumeOptions consumeOptions,
                                   @NonNull MessageHandler handler) throws IOException, JetStreamApiException {
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
        throws IOException, JetStreamApiException
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
    public boolean unpin(String group) throws IOException, JetStreamApiException {
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
