package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.Status;

import static io.synadia.client.ErrorListener.FlowControlSource;
import static io.synadia.client.ErrorListener.FlowControlSource.FLOW_CONTROL;
import static io.synadia.client.ErrorListener.FlowControlSource.HEARTBEAT;
import static io.synadia.client.impl.JetStreamConstants.CONSUMER_STALLED_HDR;
import static io.synadia.client.impl.MessageManager.ManageResult.*;

/**
 * The message manager for push subscriptions. It handles the flow control and idle heartbeat
 * statuses that the server interleaves with the JetStream messages.
 */
public class PushMessageManager extends MessageManager {

    protected final JetStream js;
    protected final String stream;

    protected final boolean queueMode;
    protected final boolean fc;
    protected String lastFcSubject;

    /**
     * Construct a push message manager. Queue subscriptions never get flow control or heartbeats;
     * otherwise both are enabled from the consumer configuration, and flow control requires heartbeats.
     * @param conn the connection
     * @param js the JetStream context
     * @param subConf the subscription configuration, which carries the consumer info
     */
    public PushMessageManager(
        NatsConnection conn,
        JetStream js,
        JetStreamSubscribeConfig subConf)
    {
        super(conn, subConf);
        ConsumerConfiguration cc = subConf.consumerInfo.getConsumerConfiguration();
        this.js = js;
        this.stream = subConf.consumerInfo.getStreamName();
        this.queueMode = cc.getDeliverGroup() != null;

        if (queueMode) {
            fc = false;
        }
        else {
            configureIdleHeartbeat(cc.getIdleHeartbeat() == null ? 0 : cc.getIdleHeartbeat().toMillis(), subConf.getMessageAlarmTime());
            fc = hb.get() && cc.isFlowControl(); // can't have fc w/o heartbeat
        }
    }

    /**
     * Whether the subscription is part of a deliver group, which disables flow control and heartbeats.
     * @return true if in queue mode
     */
    public boolean isQueueMode()     { return queueMode; }

    /**
     * Whether flow control is active. Requires both the consumer flow control setting and an idle heartbeat.
     * @return true if flow control is active
     */
    public boolean isFc()            { return fc; }

    /**
     * The subject of the most recent flow control response published, kept so the same
     * flow control request is not answered twice.
     * @return the subject, null if no flow control has been processed
     */
    public String getLastFcSubject() { return lastFcSubject; }

    @Override
    public void startup(JetStreamSubscription sub) {
        super.startup(sub);
        sub.setBeforeQueueProcessorFunction(this::beforeQueueProcessorImpl);
        if (hb.get()) {
            initOrResetHeartbeatTimer();
        }
    }

    @Override
    public Boolean beforeQueueProcessorImpl(NatsMessage msg) {
        if (hb.get()) {
            updateLastMessageReceived(); // only need to track when heartbeats are expected
            Status status = msg.getStatus();
            if (status != null) {
                // only fc heartbeats get queued
                if (status.isHeartbeat()) {
                    return hasFcSubject(msg); // true if a fc hb
                }
            }
        }
        return true;
    }

    protected boolean hasFcSubject(Message msg) {
        return msg.getHeaders() != null && msg.getHeaders().containsKey(CONSUMER_STALLED_HDR);
    }

    protected String extractFcSubject(Message msg) {
        return msg.getHeaders() == null ? null : msg.getHeaders().getFirst(CONSUMER_STALLED_HDR);
    }

    @Override
    public ManageResult manage(Message msg) {
        if (msg.isJetStream()) {
            trackJsMessage(msg);
            return MESSAGE;
        }
        return manageStatus(msg);
    }

    protected ManageResult manageStatus(Message msg) {
        // this checks fc, hb and unknown
        // only process fc and hb if those flags are set
        // otherwise they are simply known statuses
        Status status = msg.getStatus();
        if (fc) {
            boolean isFcNotHb = status.isFlowControl();
            String fcSubject = isFcNotHb ? msg.getReplyTo() : extractFcSubject(msg);
            if (fcSubject != null) {
                processFlowControl(fcSubject, isFcNotHb ? FLOW_CONTROL : HEARTBEAT);
                return STATUS_HANDLED;
            }
        }

        conn.notifyErrorListener((c, el) -> el.unhandledStatus(c, sub, status));
        return STATUS_ERROR;
    }

    private void processFlowControl(String fcSubject, FlowControlSource source) {
        // we may get multiple fc/hb messages with the same reply
        // only need to post to that subject once
        if (fcSubject != null && !fcSubject.equals(lastFcSubject)) {
            conn.publish(fcSubject, null, null, null, false);
            lastFcSubject = fcSubject; // set after publish in case the pub fails
            conn.notifyErrorListener((c, el) -> el.flowControlProcessed(c, sub, fcSubject, source));
        }
    }
}
