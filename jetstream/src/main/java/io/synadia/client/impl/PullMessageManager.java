package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.Status;

import static io.synadia.client.api.Status.*;
import static io.synadia.client.impl.JetStreamConstants.*;
import static io.synadia.client.impl.MessageManager.ManageResult.*;

/**
 * The message manager for pull subscriptions. It filters the status messages the server sends in
 * the pull's reply stream out of the user's message flow and reports pull progress to the
 * {@link PullManagerObserver}.
 */
public class PullMessageManager extends MessageManager {

    protected boolean raiseStatusWarnings;
    protected PullManagerObserver pullManagerObserver;
    protected String currentPinId;

    protected PullMessageManager(NatsConnection conn, JetStreamSubscribeConfig subConf) {
        super(conn, subConf);
    }

    @Override
    public void startup(JetStreamSubscription sub) {
        super.startup(sub);
        sub.setBeforeQueueProcessorFunction(this::beforeQueueProcessorImpl);
    }

    @Override
    public void startPullRequest(String pullSubject, PullRequestOptions pro, boolean raiseStatusWarnings, PullManagerObserver pullManagerObserver) {
        stateChangeLock.lock();
        try {
            this.raiseStatusWarnings = raiseStatusWarnings;
            this.pullManagerObserver = pullManagerObserver;
            configureIdleHeartbeat(pro.getIdleHeartbeat(), -1);
            if (hb.get()) {
                initOrResetHeartbeatTimer();
            }
            else {
                shutdownHeartbeatTimer(); // just in case the pull was changed from hb to non-hb
            }
        }
        finally {
            stateChangeLock.unlock();
        }
    }

    @Override
    protected void handleHeartbeatError() {
        super.handleHeartbeatError();
        if (pullManagerObserver != null) {
            pullManagerObserver.pullTerminatedByError();
        }
    }

    @Override
    protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
        updateLastMessageReceived();

        Status status = msg.getStatus();

        // normal js message
        if (status == null) {
            if (pullManagerObserver != null) {
                pullManagerObserver.messageReceived(msg);
            }
            return true;
        }

        // heartbeat just needed to updateLastMessageReceived
        if (status.isHeartbeat()) {
            return false;
        }

        // all other status messages return true, but some have work to do.

        int m = Integer.MIN_VALUE;
        long b = 0;

        // pin error or status with pending headers
        // pin is checked first, since there may be a version
        // of the error where headers are set.
        // Always clear currentPinId anyway
        if (status.getCode() == PIN_ERROR_CODE) {
            currentPinId = null;
            m = -1;
            b = -1;
        }
        Headers h = msg.getHeaders();
        if (h != null) {
            try {
                //noinspection DataFlowIssue WE ALREADY CATCH THE EXCEPTION
                m = Integer.parseInt(h.getFirst(NATS_PENDING_MESSAGES));
                //noinspection DataFlowIssue WE ALREADY CATCH THE EXCEPTION
                b = Long.parseLong(h.getFirst(NATS_PENDING_BYTES));
            }
            catch (NumberFormatException ignore) {
                m = Integer.MIN_VALUE;
            }
        }

        if (m != Integer.MIN_VALUE && pullManagerObserver != null) {
            pullManagerObserver.pullCompletedWithStatus(m, b);
        }

        return true;
    }

    @Override
    public ManageResult manage(Message msg) {
        if (msg.isJetStream()) {
            trackJsMessage(msg);
            checkForPin(msg);
            return MESSAGE;
        }
        return manageStatus(msg);
    }

    protected void checkForPin(Message msg) {
        if (msg.hasHeaders()) {
            String pinId = msg.getHeaders().getFirst(NATS_PIN_ID_HDR);
            if (pinId != null) {
                currentPinId = pinId;
            }
        }
    }

    protected ManageResult manageStatus(Message msg) {
        Status status = msg.getStatus();
        switch (status.getCode()) {
            case PIN_ERROR_CODE:
            case NOT_FOUND_CODE:
            case BAD_JS_REQUEST_CODE:
            case NO_RESPONDERS_CODE:
                if (raiseStatusWarnings) {
                    conn.notifyErrorListener((c, el) -> el.pullStatusWarning(c, sub, status));
                }
                return STATUS_TERMINUS;

            case CONFLICT_CODE:
                // sometimes just a warning
                String statMsg = status.getMessage();
                if (statMsg.startsWith(EXCEEDED_MAX_PREFIX) || statMsg.equals(SERVER_SHUTDOWN))
                {
                    if (raiseStatusWarnings) {
                        conn.notifyErrorListener((c, el) -> el.pullStatusWarning(c, sub, status));
                    }
                    return STATUS_HANDLED;
                }

                if (statMsg.equals(BATCH_COMPLETED)
                    || statMsg.equals(LEADERSHIP_CHANGE)
                    || statMsg.equals(MESSAGE_SIZE_EXCEEDS_MAX_BYTES))
                {
                    if (raiseStatusWarnings) {
                        conn.notifyErrorListener((c, el) -> el.pullStatusWarning(c, sub, status));
                    }
                    return STATUS_TERMINUS;
                }
                break;
        }

        // All unknown 409s are errors, since that basically means the client is not aware of them.
        // These known ones are also errors: "Consumer Deleted" and "Consumer is push based"
        conn.notifyErrorListener((c, el) -> el.pullStatusError(c, sub, status));
        return STATUS_ERROR;
    }
}
