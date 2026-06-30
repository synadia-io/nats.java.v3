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

public abstract class MessageManager {
    public enum ManageResult {MESSAGE, STATUS_HANDLED, STATUS_TERMINUS, STATUS_ERROR}

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

    public JetStreamSubscribeConfig getSubConf() { return subConf; }
    public boolean isSyncMode()              { return syncMode; }
    public long getLastStreamSequence()      { return lastStreamSeq; }
    public long getLastConsumerSequence()    { return lastConsumerSeq; }
    public long getLastMsgReceivedNanoTime() { return lastMsgReceivedNanoTime.get(); }
    public boolean isHb()                    { return hb.get(); }
    public long getIdleHeartbeatSetting()    { return idleHeartbeatSettingMillis.get(); }
    public long getAlarmPeriodSettingNanos() { return alarmPeriodSettingNanos.get(); }

    public String createInbox() {
        return conn.createInbox();
    }

    public void startup(JetStreamSubscription sub) {
        this.sub = sub;
    }

    public void shutdown() {
        shutdownHeartbeatTimer();
    }

    public void startPullRequest(String pullSubject, PullRequestOptions pullRequestOptions, boolean raiseStatusWarnings, PullManagerObserver pullManagerObserver) {
        // does nothing - only implemented for pulls, but in base class since instance is referenced as MessageManager, not subclass
    }

    protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
        return true;
    }

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

    public void updateLastMessageReceived() {
        lastMsgReceivedNanoTime.set(NatsSystemClock.nanoTime());
    }

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
