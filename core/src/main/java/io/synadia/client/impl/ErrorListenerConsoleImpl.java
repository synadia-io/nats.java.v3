package io.synadia.client.impl;

import io.synadia.client.Consumer;
import io.synadia.client.ErrorListener;
import io.synadia.client.Message;
import io.synadia.client.js.JetStreamSubscription;
import io.synadia.client.support.Status;

public class ErrorListenerConsoleImpl implements ErrorListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final NatsConnection conn, final String error) {
        System.out.println(supplyMessage("[SEVERE] errorOccurred", conn, null, null, "Error: ", error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionOccurred(final NatsConnection conn, final Exception exp) {
        System.out.println(supplyMessage("[SEVERE] exceptionOccurred", conn, null, null, "Exception: ", exp));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(final NatsConnection conn, final Consumer consumer) {
        System.out.println(supplyMessage("[WARN] slowConsumerDetected", conn, consumer, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final NatsConnection conn, final Message msg) {
        System.out.println(supplyMessage("[INFO] messageDiscarded", conn, null, null, "Message: ", msg));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final NatsConnection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        System.out.println(supplyMessage("[SEVERE] heartbeatAlarm", conn, null, sub, "lastStreamSequence: ", lastStreamSequence, "lastConsumerSequence: ", lastConsumerSequence));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final NatsConnection conn, final JetStreamSubscription sub, final Status status) {
        System.out.println(supplyMessage("[WARN] unhandledStatus", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(NatsConnection conn, JetStreamSubscription sub, Status status) {
        System.out.println(supplyMessage("[WARN] pullStatusWarning", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(NatsConnection conn, JetStreamSubscription sub, Status status) {
        System.out.println(supplyMessage("[SEVERE] pullStatusError", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(NatsConnection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        System.out.println(supplyMessage("[INFO] flowControlProcessed", conn, null, sub, "FlowControlSource: ", source));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(NatsConnection conn) {
        System.out.println(supplyMessage("[SEVERE] socketWriteTimeout", conn, null, null));
    }
}
