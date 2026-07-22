package io.synadia.client.impl;

import io.synadia.client.api.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
    NatsStreamContext(String streamName, @Nullable JetStream js, NatsConnection connection, @Nullable JetStreamOptions jsOptions) throws JetStreamException, InterruptedException {
        this.streamName = streamName;
        this.js = js == null ? new JetStream(connection, jsOptions) : js;
        jsm = this.js.jetStreamManagement();
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
    public StreamInfo getStreamInfo() throws JetStreamException, InterruptedException {
        return jsm.getStreamInfo(streamName, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamInfo getStreamInfo(@Nullable StreamInfoOptions options) throws JetStreamException, InterruptedException {
        return jsm.getStreamInfo(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PurgeResponse purge() throws JetStreamException, InterruptedException {
        return jsm.purgeStream(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PurgeResponse purge(PurgeOptions options) throws JetStreamException, InterruptedException {
        return jsm.purgeStream(streamName, options);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext getConsumerContext(String consumerName) throws JetStreamException, InterruptedException {
        return new NatsConsumerContext(this, jsm.getConsumerInfo(streamName, consumerName), null);
    }

    @Override
    public ConsumerContext getConsumerContext(ConsumerInfo ci) throws JetStreamException {
        return new NatsConsumerContext(this, ci, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createConsumer(String subject) throws JetStreamException, InterruptedException {
        return js.createConsumer(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createConsumer(PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        return new NatsConsumerContext(this, jsm.createConsumer(streamName, creator), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext updateConsumer(PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        return new NatsConsumerContext(this, jsm.updateConsumer(streamName, creator), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerContext createOrUpdateConsumer(PullConsumerCreator creator) throws JetStreamException, InterruptedException {
        return new NatsConsumerContext(this, jsm.createOrUpdateConsumer(streamName, creator), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OrderedConsumerContext createOrderedConsumer(PullOrderedConsumerCreator creator) throws JetStreamException {
        return new NatsOrderedConsumerContext(this, creator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteConsumer(String consumerName) throws JetStreamException, InterruptedException {
        return jsm.deleteConsumer(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInfo getConsumerInfo(String consumerName) throws JetStreamException, InterruptedException {
        return jsm.getConsumerInfo(streamName, consumerName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getConsumerNames() throws JetStreamException, InterruptedException {
        return jsm.getConsumerNames(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ConsumerInfo> getConsumers() throws JetStreamException, InterruptedException {
        return jsm.getConsumers(streamName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getMessage(long seq) throws JetStreamException, InterruptedException {
        return jsm.getMessage(streamName, seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getLastMessage(String subject) throws JetStreamException, InterruptedException {
        return jsm.getLastMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getFirstMessage(String subject) throws JetStreamException, InterruptedException {
        return jsm.getFirstMessage(streamName, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageInfo getNextMessage(long seq, String subject) throws JetStreamException, InterruptedException {
        return jsm.getNextMessage(streamName, seq, subject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteMessage(long seq) throws JetStreamException, InterruptedException {
        return jsm.deleteMessage(streamName, seq);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteMessage(long seq, boolean erase) throws JetStreamException, InterruptedException {
        return jsm.deleteMessage(streamName, seq, erase);
    }
}
