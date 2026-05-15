package io.synadia.client.impl;

import io.synadia.client.Consumer;
import io.synadia.client.ErrorListener;
import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.testutils.Status;

public class ErrorListenerConsoleImpl implements ErrorListener {

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(NatsConnection conn, String error) {
        System.out.println(supplyMessage("[SEVERE] errorOccurred", conn, null, null, "Error: ", error));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void exceptionOccurred(NatsConnection conn, Exception exp) {
        System.out.println(supplyMessage("[SEVERE] exceptionOccurred", conn, null, null, "Exception: ", exp));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(NatsConnection conn, Consumer consumer) {
        System.out.println(supplyMessage("[WARN] slowConsumerDetected", conn, consumer, null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(NatsConnection conn, Message msg) {
        System.out.println(supplyMessage("[INFO] messageDiscarded", conn, null, null, "Message: ", msg));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(NatsConnection conn, Subscription sub,
                               long lastStreamSequence, long lastConsumerSequence) {
        System.out.println(supplyMessage("[SEVERE] heartbeatAlarm", conn, null, sub, "lastStreamSequence: ", lastStreamSequence, "lastConsumerSequence: ", lastConsumerSequence));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(NatsConnection conn, Subscription sub, Status status) {
        System.out.println(supplyMessage("[WARN] unhandledStatus", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(NatsConnection conn, Subscription sub, Status status) {
        System.out.println(supplyMessage("[WARN] pullStatusWarning", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(NatsConnection conn, Subscription sub, Status status) {
        System.out.println(supplyMessage("[SEVERE] pullStatusError", conn, null, sub, "Status: ", status));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(NatsConnection conn, Subscription sub, String id, FlowControlSource source) {
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
