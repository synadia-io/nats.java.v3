package io.synadia.client.impl;

import io.synadia.client.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Implementation of Stream Context
 */
class NatsStreamContext implements StreamContext {
    final String streamName;
    final JetStream js;
    final JetStreamManagement jsm;

    // for when this is constructed from the JetStream itself
    NatsStreamContext(@NonNull String streamName, @Nullable JetStream js, @NonNull NatsConnection connection, @Nullable JetStreamOptions jsOptions) throws IOException, JetStreamApiException {
        this.streamName = streamName;
        this.js = js == null ? new JetStream(connection, jsOptions) : js;
        jsm = new JetStreamManagement(connection, jsOptions);
        jsm.getStreamInfo(streamName); // this is just verifying that the stream exists
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String getStreamName() {
        return streamName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public StreamInfo getStreamInfo() throws IOException, JetStreamApiException {
        return jsm.getStreamInfo(streamName, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public StreamInfo getStreamInfo(@Nullable StreamInfoOptions options) throws IOException, JetStreamApiException {
        return jsm.getStreamInfo(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public PurgeResponse purge() throws IOException, JetStreamApiException {
        return jsm.purgeStream(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public PurgeResponse purge(@NonNull PurgeOptions options) throws IOException, JetStreamApiException {
        return jsm.purgeStream(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ConsumerContext getConsumerContext(@NonNull String consumerName) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, jsm.getConsumerInfo(streamName, consumerName), null);
    }

    @Override
    public ConsumerContext getConsumerContext(@NonNull ConsumerInfo ci) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, ci, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ConsumerContext createOrUpdateConsumer(@NonNull PullConsumerCreator creator) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, jsm.addOrUpdateConsumer(creator), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public OrderedConsumerContext createOrderedConsumer(@NonNull PullOrderedConsumerCreator config) throws IOException, JetStreamApiException {
        return new NatsOrderedConsumerContext(this, config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteConsumer(@NonNull String consumerName) throws IOException, JetStreamApiException {
        return jsm.deleteConsumer(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ConsumerInfo getConsumerInfo(@NonNull String consumerName) throws IOException, JetStreamApiException {
        return jsm.getConsumerInfo(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<String> getConsumerNames() throws IOException, JetStreamApiException {
        return jsm.getConsumerNames(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public List<ConsumerInfo> getConsumers() throws IOException, JetStreamApiException {
        return jsm.getConsumers(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageInfo getMessage(long seq) throws IOException, JetStreamApiException {
        return jsm.getMessage(streamName, seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageInfo getLastMessage(@NonNull String subject) throws IOException, JetStreamApiException {
        return jsm.getLastMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageInfo getFirstMessage(@NonNull String subject) throws IOException, JetStreamApiException {
        return jsm.getFirstMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public MessageInfo getNextMessage(long seq, @NonNull String subject) throws IOException, JetStreamApiException {
        return jsm.getNextMessage(streamName, seq, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteMessage(long seq) throws IOException, JetStreamApiException {
        return jsm.deleteMessage(streamName, seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteMessage(long seq, boolean erase) throws IOException, JetStreamApiException {
        return jsm.deleteMessage(streamName, seq, erase);
    }
}
