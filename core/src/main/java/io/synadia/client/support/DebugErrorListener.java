package io.synadia.client.support;

import io.synadia.client.*;

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

    public String string(Connection conn) {
        return "Connection(" + conn.hashCode() + ") " + conn.getStatus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void errorOccurred(final Connection conn, final String error) {
        Debug.info(label, "errorOccurred", string(conn), "Error: " + error);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("CallToPrintStackTrace")
    @Override
    public void exceptionOccurred(final Connection conn, final Exception exp) {
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
    public void slowConsumerDetected(final Connection conn, final Consumer consumer) {
        Debug.info(label, "slowConsumerDetected", string(conn), consumer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void messageDiscarded(final Connection conn, final Message msg) {
        Debug.info(label, "messageDiscarded", string(conn), "Message: " + msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void heartbeatAlarm(final Connection conn, final JetStreamSubscription sub,
                               final long lastStreamSequence, final long lastConsumerSequence) {
        Debug.info(label, "heartbeatAlarm", string(conn), sub.hashCode(), "lastStreamSequence: " + lastStreamSequence, "lastConsumerSequence: " + lastConsumerSequence);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unhandledStatus(final Connection conn, final JetStreamSubscription sub, final Status status) {
        Debug.info(label, "unhandledStatus", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusWarning(Connection conn, JetStreamSubscription sub, Status status) {
        Debug.info(label, "pullStatusWarning", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void pullStatusError(Connection conn, JetStreamSubscription sub, Status status) {
        Debug.info(label, "pullStatusError", string(conn), sub, "Status: " + status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flowControlProcessed(Connection conn, JetStreamSubscription sub, String id, FlowControlSource source) {
        Debug.info(label, "flowControlProcessed", string(conn), sub, "FlowControlSource: " + source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void socketWriteTimeout(Connection conn) {
        Debug.info(label, "socketWriteTimeout", string(conn));
    }
}
