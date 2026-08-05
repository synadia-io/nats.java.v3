package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.NatsConstants;
import io.synadia.client.utils.ScheduledTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class for the per-subscription logic that inspects every incoming message before the
 * application sees it, so that JetStream protocol traffic (status messages, heartbeats, flow
 * control) is handled by the client instead of being delivered as a regular message.
 * Also owns the idle heartbeat alarm timer for the subscription.
 */
public abstract class MessageManager {
    /**
     * What the subscription should do with a message after the manager has looked at it.
     */
    public enum ManageResult {
        /** A regular JetStream message; deliver it to the application. */
        MESSAGE,
        /** A status message that the manager fully dealt with; skip it and keep waiting. */
        STATUS_HANDLED,
        /** A status that ends the current pull request, such as 404, 408 or "Batch Completed"; the caller gets no message. */
        STATUS_TERMINUS,
        /** A status the client cannot handle; the caller gets an exception. */
        STATUS_ERROR
    }

    protected static final int THRESHOLD = 3;

    protected final ReentrantLock stateChangeLock;
    protected final NatsConnection conn;
    protected final JetStreamSubscribeConfig subConf;
    protected final boolean syncMode;

    protected JetStreamSubscription sub; // not final it is not set until after construction

    protected long lastStreamSeq;
    protected long lastConsumerSeq;
    protected final AtomicLong lastMsgReceivedNanoTime;

    // heartbeat stuff
    protected final AtomicBoolean hb;
    protected final AtomicLong idleHeartbeatSettingMillis;
    protected final AtomicLong alarmPeriodSettingNanos;
    protected final AtomicReference<ScheduledTask> heartbeatTaskRef;

    protected MessageManager(NatsConnection conn, JetStreamSubscribeConfig subConf) {
        this.stateChangeLock = new ReentrantLock();
        this.conn = conn;
        this.subConf = subConf;
        this.syncMode = subConf.getHandler() == null;

        lastStreamSeq = 0;
        lastConsumerSeq = 0;

        hb = new AtomicBoolean(false);
        idleHeartbeatSettingMillis = new AtomicLong();
        alarmPeriodSettingNanos = new AtomicLong();
        lastMsgReceivedNanoTime = new AtomicLong(NatsSystemClock.nanoTime());
        heartbeatTaskRef = new AtomicReference<>();
    }

    /**
     * The configuration the subscription was created with.
     * @return the subscribe config
     */
    public JetStreamSubscribeConfig getSubConf() { return subConf; }

    /**
     * Sync mode means the subscription has no message handler, so the application pulls
     * messages with nextMessage instead of having them dispatched.
     * @return true if the subscription was created without a handler
     */
    public boolean isSyncMode()              { return syncMode; }

    /**
     * Stream sequence of the last JetStream message that reached the application.
     * @return the stream sequence, 0 if none yet
     */
    public long getLastStreamSequence()      { return lastStreamSeq; }

    /**
     * Count of JetStream messages tracked so far, used as the expected consumer sequence.
     * @return the consumer sequence, 0 if none yet
     */
    public long getLastConsumerSequence()    { return lastConsumerSeq; }

    /**
     * Time any message, including a heartbeat, last arrived. The heartbeat alarm compares
     * against this to decide whether the server has gone quiet.
     * @return the time in nanoseconds, on the {@code System.nanoTime()} scale
     */
    public long getLastMsgReceivedNanoTime() { return lastMsgReceivedNanoTime.get(); }

    /**
     * Whether idle heartbeat monitoring is active, which requires a positive idle heartbeat setting.
     * @return true if the heartbeat alarm is in play
     */
    public boolean isHb()                    { return hb.get(); }

    /**
     * The consumer's idle heartbeat interval, the rate at which the server sends a heartbeat when
     * there are no messages.
     * @return the interval in milliseconds, 0 if heartbeats are off
     */
    public long getIdleHeartbeatSetting()    { return idleHeartbeatSettingMillis.get(); }

    /**
     * How long messages may stop arriving before the error listener is told of a heartbeat alarm.
     * Defaults to three times the idle heartbeat unless a longer message alarm time was configured.
     * @return the period in nanoseconds, 0 if heartbeats are off
     */
    public long getAlarmPeriodSettingNanos() { return alarmPeriodSettingNanos.get(); }

    /**
     * Make an inbox subject using the connection's inbox prefix.
     * @return the new inbox subject
     */
    public String createInbox() {
        return conn.createInbox();
    }

    /**
     * Give the manager the subscription it manages. Called once, right after the subscription
     * is created, since the subscription cannot exist at construction time.
     * @param sub the subscription
     */
    public void startup(JetStreamSubscription sub) {
        this.sub = sub;
    }

    /**
     * Release the manager's resources, in particular the heartbeat timer. Called when the
     * subscription is unsubscribed or drained.
     */
    public void shutdown() {
        shutdownHeartbeatTimer();
    }

    /**
     * Tell the manager a pull request was just sent, so it can track the request's expectations.
     * Does nothing in the base class; only the pull managers implement it.
     * @param pullSubject the unique subject the pull request replies come in on
     * @param pullRequestOptions the options the pull request was made with
     * @param raiseStatusWarnings whether to notify the error listener of non-terminal statuses
     * @param pullManagerObserver the observer notified when the pull completes, may be null
     */
    public void startPullRequest(String pullSubject, PullRequestOptions pullRequestOptions, boolean raiseStatusWarnings, PullManagerObserver pullManagerObserver) {
        // does nothing - only implemented for pulls, but in base class since instance is referenced as MessageManager, not subclass
    }

    protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
        return true;
    }

    /**
     * Inspect an incoming message, handling it if it is protocol traffic, and tell the
     * subscription what to do with it.
     * @param msg the message that just arrived
     * @return the disposition of the message
     */
    abstract public ManageResult manage(Message msg);

    protected void trackJsMessage(Message msg) {
        stateChangeLock.lock();
        try {
            JetStreamMetaData meta = msg.metaData();
            lastStreamSeq = meta.streamSequence();
            lastConsumerSeq++;
        }
        finally {
            stateChangeLock.unlock();
        }
    }

    protected void handleHeartbeatError() {
        conn.notifyErrorListener((c, el) -> el.heartbeatAlarm(c, sub, lastStreamSeq, lastConsumerSeq));
    }

    protected void configureIdleHeartbeat(long configIdleHeartbeatMillis, long configMessageAlarmTime) {
        stateChangeLock.lock();
        try {
            idleHeartbeatSettingMillis.set(configIdleHeartbeatMillis);
            if (configIdleHeartbeatMillis <= 0) {
                alarmPeriodSettingNanos.set(0);
                hb.set(false);
            }
            else {
                long alarmPeriodSettingMillis;
                if (configMessageAlarmTime < configIdleHeartbeatMillis) {
                    alarmPeriodSettingMillis = configIdleHeartbeatMillis * THRESHOLD;
                }
                else {
                    alarmPeriodSettingMillis = configMessageAlarmTime;
                }
                alarmPeriodSettingNanos.set(alarmPeriodSettingMillis * NatsConstants.NANOS_PER_MILLI);
                hb.set(true);
            }
        }
        finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Mark now as the time a message last arrived, restarting the countdown to the heartbeat alarm.
     */
    public void updateLastMessageReceived() {
        lastMsgReceivedNanoTime.set(NatsSystemClock.nanoTime());
    }

    /**
     * Start the heartbeat alarm timer, replacing any existing one so the initial delay starts over.
     * The timer notifies the error listener when nothing has arrived for the alarm period.
     */
    public void initOrResetHeartbeatTimer() {
        stateChangeLock.lock();
        try {
            ScheduledTask hbTask = heartbeatTaskRef.get();
            if (hbTask != null) {
                // we always want a fresh schedule because it will have the initial delay
                hbTask.shutdown();
            }

            // set the ref with a new ScheduledTask
            // reminder that ScheduledTask schedules itself, which is why we pass the executor
            heartbeatTaskRef.set(
                new ScheduledTask(conn.getScheduledExecutor(), alarmPeriodSettingNanos.get(), TimeUnit.NANOSECONDS,
                    () -> {
                        long sinceLast = NatsSystemClock.nanoTime() - lastMsgReceivedNanoTime.get();
                        if (sinceLast > alarmPeriodSettingNanos.get()) {
                            handleHeartbeatError();
                        }
                    })
            );

            // since we just scheduled, reset this otherwise it may alarm too soon
            updateLastMessageReceived();
        }
        finally {
            stateChangeLock.unlock();
        }
    }

    /**
     * Stop the heartbeat alarm timer if one is running. Safe to call when there is none.
     */
    public void shutdownHeartbeatTimer() {
        stateChangeLock.lock();
        try {
            ScheduledTask hbTask = heartbeatTaskRef.get();
            if (hbTask != null) {
                hbTask.shutdown();
                heartbeatTaskRef.set(null);
            }
        }
        finally {
            stateChangeLock.unlock();
        }
    }
}
