package io.synadia.client.impl;

import io.synadia.client.ErrorListener;
import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.api.Status;

/**
 * {@link ErrorListener} that prints every event to {@code System.out}, prefixed with a severity
 * and the name of the callback. Handy for development; production code will normally want a
 * listener that goes to a real log.
 */
public class ErrorListenerConsoleImpl implements ErrorListener {

    /** Construct a listener. It holds no state, so one instance can serve any number of connections. */
    public ErrorListenerConsoleImpl() {}

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
    public void slowConsumerDetected(NatsConnection conn, Subscription subscription) {
        System.out.println(supplyMessage("[WARN] slowConsumerDetected", conn, subscription));
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
        System.out.println(supplyMessage("[SEVERE] socketWriteTimeout", conn, null));
    }
}
