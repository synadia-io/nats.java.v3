package io.synadia.client.impl;

import io.synadia.client.api.ConsumerInfo;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class NatsMessageConsumerBase implements MessageConsumer, PullManagerObserver {
    protected JetStreamPullSubscription sub;
    protected PullMessageManager pmm;
    protected final AtomicBoolean stopped;
    protected final AtomicBoolean finished;
    protected ConsumerInfo cachedConsumerInfo;
    protected String consumerName;

    NatsMessageConsumerBase(ConsumerInfo cachedConsumerInfo) {
        this.cachedConsumerInfo = cachedConsumerInfo;
        if (cachedConsumerInfo != null) {
            this.consumerName = cachedConsumerInfo.getName();
        }
        this.stopped = new AtomicBoolean(false);
        this.finished = new AtomicBoolean(false);
    }

    void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
    }

    void initSub(JetStreamPullSubscription sub, boolean clearCachedConsumerInfo) {
        this.sub = sub;
        this.consumerName = sub.getConsumerName();
        if (clearCachedConsumerInfo) {
            cachedConsumerInfo = null;
        }
        pmm = (PullMessageManager)sub.manager;
    }

    protected void rePull() {
        // may or may not be implemented
        // fetch does not implement
    }

    /**
     * {@inheritDoc}
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isFinished() {
        return finished.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getConsumerName() {
        if (consumerName == null && cachedConsumerInfo != null) {
            consumerName = cachedConsumerInfo.getName();
        }
        return consumerName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInfo getConsumerInfo() throws IOException, JetStreamApiException {
        if (cachedConsumerInfo == null) {
            cachedConsumerInfo = sub.getConsumerInfo();
            consumerName = cachedConsumerInfo.getName();
        }
        return cachedConsumerInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsumerInfo getCachedConsumerInfo() {
        return cachedConsumerInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        stopped.set(true);
    }

    @Override
    public void close() throws Exception {
        stopped.set(true);
        shutdownSub();
    }

    protected void fullClose() {
        stopped.set(true);
        finished.set(true);
        shutdownSub();
    }

    protected void shutdownSub() {
        try {
            if (sub.isActive()) {
                if (sub.getDispatcher() != null) {
                    sub.getDispatcher().unsubscribe(sub);
                }
                else {
                    sub.unsubscribe();
                }
            }
        }
        catch (Throwable ignore) {
            // nothing to do
        }
        if (pmm != null) {
            try {
                pmm.shutdownHeartbeatTimer();
            }
            catch (Throwable ignore) {
                // nothing to do
            }
        }
    }

    static class PinnablePullRequestOptions extends PullRequestOptions {
        final String pinId;

        public PinnablePullRequestOptions(String pinId, Builder b) {
            super(b);
            this.pinId = pinId;
        }

        @Override
        protected String getPinId() {
            return pinId;
        }
    }
}
