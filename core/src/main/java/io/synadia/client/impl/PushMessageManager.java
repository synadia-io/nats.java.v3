package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.SubscribeOptions;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.support.Status;

import static io.synadia.client.ErrorListener.FlowControlSource;
import static io.synadia.client.ErrorListener.FlowControlSource.FLOW_CONTROL;
import static io.synadia.client.ErrorListener.FlowControlSource.HEARTBEAT;
import static io.synadia.client.impl.MessageManager.ManageResult.*;
import static io.synadia.client.support.NatsJetStreamConstants.CONSUMER_STALLED_HDR;

class PushMessageManager extends MessageManager {

    protected final NatsJetStream js;
    protected final String stream;
    protected final ConsumerConfiguration initialCc;

    protected final boolean queueMode;
    protected final boolean fc;
    protected String lastFcSubject;

    protected PushMessageManager(NatsConnection conn,
                       NatsJetStream js,
                       String stream,
                       SubscribeOptions so,
                       ConsumerConfiguration initialCc,
                       boolean queueMode,
                       boolean syncMode)
    {
        super(conn, so, syncMode);
        this.js = js;
        this.stream = stream;
        this.initialCc = initialCc;
        this.queueMode = queueMode;

        if (queueMode) {
            fc = false;
        }
        else {
            configureIdleHeartbeat(initialCc.getIdleHeartbeat(), so.getMessageAlarmTime());
            fc = hb.get() && initialCc.isFlowControl(); // can't have fc w/o heartbeat
        }
    }

    protected boolean isQueueMode()     { return queueMode; }
    protected boolean isFc()            { return fc; }
    protected String getLastFcSubject() { return lastFcSubject; }

    @Override
    protected void startup(NatsJetStreamSubscription sub) {
        super.startup(sub);
        sub.setBeforeQueueProcessor(this::beforeQueueProcessorImpl);
        if (hb.get()) {
            initOrResetHeartbeatTimer();
        }
    }

    @Override
    protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
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
    protected ManageResult manage(Message msg) {
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
            conn.publishInternal(fcSubject, null, null, null, false);
            lastFcSubject = fcSubject; // set after publish in case the pub fails
            conn.notifyErrorListener((c, el) -> el.flowControlProcessed(c, sub, fcSubject, source));
        }
    }
}
