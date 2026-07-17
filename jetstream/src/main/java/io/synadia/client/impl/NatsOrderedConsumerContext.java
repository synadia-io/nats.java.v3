package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.PullOrderedConsumerCreator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Implementation of Ordered Consumer Context
 */
public class NatsOrderedConsumerContext implements OrderedConsumerContext {
    private final NatsConsumerContext impl;

    NatsOrderedConsumerContext(@NonNull NatsStreamContext streamContext,
                               @NonNull PullOrderedConsumerCreator config) {
        impl = new NatsConsumerContext(streamContext, null, config);
    }

    /**
     * Gets the consumer name created for the underlying Ordered Consumer
     * This will return null until the first consume (next, iterate, fetch, consume)
     * is executed because the JetStream consumer, which carries the name,
     * has not been created yet.
     * <p>
     * The consumer name is subject to change for 2 reasons.
     * 1. Any time next(...) is called
     * 2. Anytime a message is received out of order for instance because of a disconnection
     * </p>
     * <p>If your PullOrderedConsumerCreator has a consumerNamePrefix,
     * the consumer name will always start with the prefix
     * </p>
     * @return the consumer name or null
     */
    @Override
    public String getConsumerName() {
        return impl.getConsumerName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next() throws IOException, InterruptedException, JetStreamStatusException, JetStreamApiException {
        return impl.next();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Message next(long maxWait) throws IOException, InterruptedException, JetStreamStatusException, JetStreamApiException {
        return impl.next(maxWait);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetchMessages(int maxMessages) throws IOException, JetStreamApiException {
        return impl.fetchMessages(maxMessages);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetchBytes(int maxBytes) throws IOException, JetStreamApiException {
        return impl.fetchBytes(maxBytes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public FetchMessageConsumer fetch(@NonNull FetchConsumeOptions fetchConsumeOptions) throws IOException, JetStreamApiException {
        return impl.fetch(fetchConsumeOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public IterableMessageConsumer iterate() throws IOException, JetStreamApiException {
        return impl.iterate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public IterableMessageConsumer iterate(@NonNull ConsumeOptions consumeOptions) throws IOException, JetStreamApiException {
        return impl.iterate(consumeOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return impl.consume(handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@Nullable NatsDispatcher dispatcher, @NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return impl.consume(dispatcher, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull ConsumeOptions consumeOptions, @NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return impl.consume(consumeOptions, handler);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageConsumer consume(@NonNull ConsumeOptions consumeOptions, @Nullable NatsDispatcher dispatcher, @NonNull MessageHandler handler) throws IOException, JetStreamApiException {
        return impl.consume(consumeOptions, dispatcher, handler);
    }

    @Override
    public boolean unpin(String group) throws IOException, JetStreamApiException {
        return impl.unpin(group);
    }
}
