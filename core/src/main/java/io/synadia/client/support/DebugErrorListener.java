package io.synadia.client.support;

import io.synadia.client.Consumer;
import io.synadia.client.ErrorListener;
import io.synadia.client.JetStreamSubscription;
import io.synadia.client.Message;
import io.synadia.client.impl.NatsConnection;

public class DebugErrorListener implements ErrorListener {
    String label;
    boolean printStackTrace;

    public DebugErrorListener() {
        this(null, false);
    }

    public DebugErrorListener(String label) {
        this(label, false);
    }

    public DebugErrorListener(boolean printStackTrace) {
        this(null, printStackTrace);
    }

    public DebugErrorListener(String label, boolean printStackTrace) {
        label(label);
        this.printStackTrace = printStackTrace;
    }

    public void label(String elLabel) {
        this.label = elLabel == null ? "EL" : elLabel;
    }

    public void printStackTrace(boolean printStackTrace) {
        this.printStackTrace = printStackTrace;
    }

    public String string(NatsConnection conn) {
        return "Connection(" + conn.hashCode() + ") " + conn.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final NatsConnection conn, final String error) {
        Debug.info(label, "errorOccurred", string(conn), "Error: " + error);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void exceptionOccurred(final NatsConnection conn, final Exception exp) {
        if (printStackTrace) {
            Debug.stackTrace(label, exp, string(conn));
        }
        else {
            Debug.info(label, "exceptionOccurred:", string(conn), exp);
            Throwable cause = exp.getCause();
            while (cause != null) {
                Debug.info(label, "            cause:", cause.getClass().getName());
                cause = cause.getCause();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void slowConsumerDetected(final NatsConnection conn, final Consumer consumer) {
        Debug.info(label, "slowConsumerDetected", string(conn), consumer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final NatsConnection conn, final Message msg) {
        Debug.info(label, "messageDiscarded", string(conn), "Message: " + msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final NatsConnection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        Debug.info(label, "heartbeatAlarm", string(conn), sub.hashCode(), "lastStreamSequence: " + lastStreamSequence, "lastConsumerSequence: " + lastConsumerSequence);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final NatsConnection conn, final JetStreamSubscription sub, final Status status) {
        Debug.info(label, "unhandledStatus", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(NatsConnection conn, JetStreamSubscription sub, Status status) {
        Debug.info(label, "pullStatusWarning", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(NatsConnection conn, JetStreamSubscription sub, Status status) {
        Debug.info(label, "pullStatusError", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(NatsConnection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        Debug.info(label, "flowControlProcessed", string(conn), sub, "FlowControlSource: " + source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(NatsConnection conn) {
        Debug.info(label, "socketWriteTimeout", string(conn));
    }
}
