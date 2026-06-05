package io.synadia.client.impl;

import io.synadia.client.api.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

/**
 * Implementation of Stream Context
 */
@NullMarked
class NatsStreamContext implements StreamContext {
    final String streamName;
    final JetStream js;
    final JetStreamManagement jsm;

    // for when this is constructed from the JetStream itself
    NatsStreamContext(String streamName, @Nullable JetStream js, NatsConnection connection, @Nullable JetStreamOptions jsOptions) throws IOException, JetStreamApiException {
        this.streamName = streamName;
        this.js = js == null ? new JetStream(connection, jsOptions) : js;
        jsm = new JetStreamManagement(connection, jsOptions);
        jsm.getStreamInfo(streamName); // this is just verifying that the stream exists
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStreamName() {
        return streamName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamInfo getStreamInfo() throws IOException, JetStreamApiException {
        return jsm.getStreamInfo(streamName, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamInfo getStreamInfo(@Nullable StreamInfoOptions options) throws IOException, JetStreamApiException {
        return jsm.getStreamInfo(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PurgeResponse purge() throws IOException, JetStreamApiException {
        return jsm.purgeStream(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PurgeResponse purge(PurgeOptions options) throws IOException, JetStreamApiException {
        return jsm.purgeStream(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext getConsumerContext(String consumerName) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, jsm.getConsumerInfo(streamName, consumerName), null);
    }

    @Override
    public ConsumerContext getConsumerContext(ConsumerInfo ci) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, ci, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createConsumer(String subject) throws IOException, JetStreamApiException {
        return js.createConsumer(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createConsumer(PullConsumerCreator creator) throws IOException, JetStreamApiException {
        return js.createConsumer(creator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createOrUpdateConsumer(PullConsumerCreator creator) throws IOException, JetStreamApiException {
        return new NatsConsumerContext(this, jsm.addOrUpdateConsumer(creator), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrderedConsumerContext createOrderedConsumer(PullOrderedConsumerCreator config) throws IOException, JetStreamApiException {
        return new NatsOrderedConsumerContext(this, config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteConsumer(String consumerName) throws IOException, JetStreamApiException {
        return jsm.deleteConsumer(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInfo getConsumerInfo(String consumerName) throws IOException, JetStreamApiException {
        return jsm.getConsumerInfo(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getConsumerNames() throws IOException, JetStreamApiException {
        return jsm.getConsumerNames(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConsumerInfo> getConsumers() throws IOException, JetStreamApiException {
        return jsm.getConsumers(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getMessage(long seq) throws IOException, JetStreamApiException {
        return jsm.getMessage(streamName, seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getLastMessage(String subject) throws IOException, JetStreamApiException {
        return jsm.getLastMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getFirstMessage(String subject) throws IOException, JetStreamApiException {
        return jsm.getFirstMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getNextMessage(long seq, String subject) throws IOException, JetStreamApiException {
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
