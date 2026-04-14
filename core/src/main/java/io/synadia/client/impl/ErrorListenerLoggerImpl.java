package io.synadia.client.impl;

import io.synadia.client.Consumer;
import io.synadia.client.ErrorListener;
import io.synadia.client.Message;
import io.synadia.client.js.JetStreamSubscription;
import io.synadia.client.support.Status;

import java.util.logging.Logger;

public class ErrorListenerLoggerImpl implements ErrorListener {

    private final static Logger LOGGER = Logger.getLogger(ErrorListenerLoggerImpl.class.getName());

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final NatsConnection conn, final String error) {
        LOGGER.severe(() -> supplyMessage("errorOccurred", conn, null, null, "Error: ", error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionOccurred(final NatsConnection conn, final Exception exp) {
        LOGGER.severe(() -> supplyMessage("exceptionOccurred", conn, null, null, "Exception: ", exp));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(final NatsConnection conn, final Consumer consumer) {
        LOGGER.warning(() -> supplyMessage("slowConsumerDetected", conn, consumer, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final NatsConnection conn, final Message msg) {
        LOGGER.info(() -> supplyMessage("messageDiscarded", conn, null, null, "Message: ", msg));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final NatsConnection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        LOGGER.severe(() -> supplyMessage("heartbeatAlarm", conn, null, sub, "lastStreamSequence: ", lastStreamSequence, "lastConsumerSequence: ", lastConsumerSequence));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final NatsConnection conn, final JetStreamSubscription sub, final Status status) {
        LOGGER.warning(() -> supplyMessage("unhandledStatus", conn, null, sub, "Status:", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(NatsConnection conn, JetStreamSubscription sub, Status status) {
        LOGGER.warning(() -> supplyMessage("pullStatusWarning", conn, null, sub, "Status:", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(NatsConnection conn, JetStreamSubscription sub, Status status) {
        LOGGER.severe(() -> supplyMessage("pullStatusError", conn, null, sub, "Status:", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(NatsConnection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        LOGGER.info(() -> supplyMessage("flowControlProcessed", conn, null, sub, "FlowControlSource:", source));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(NatsConnection conn) {
        LOGGER.severe(() -> supplyMessage("socketWriteTimeout", conn, null, null));
    }
}
