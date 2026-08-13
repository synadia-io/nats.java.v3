package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;


class NatsMessageConsumer extends NatsMessageConsumerBase implements PullManagerObserver {
    protected final ConsumeOptions consumeOpts;
    protected final SimplifiedSubscriptionMaker subscriptionMaker;
    protected final NatsDispatcher userDispatcher;
    protected final MessageHandler userMessageHandler;

    protected final int thresholdMessages;
    protected final long thresholdBytes;
    protected final boolean isTrackingBytes;

    protected int pendingReceivedMessages;
    protected long pendingReceivedBytes;
    protected boolean noReceivedArePending;
    protected boolean forcePull;
    protected int pendingProcessedMessages;
    protected long pendingProcessedBytes;
    protected boolean processedHasCrossedThreshold;

    NatsMessageConsumer(SimplifiedSubscriptionMaker subscriptionMaker,
                        ConsumerInfo cachedConsumerInfo,
                        ConsumeOptions consumeOpts,
                        NatsDispatcher userDispatcher,
                        final MessageHandler userMessageHandler) throws JetStreamException, InterruptedException
    {
        super(cachedConsumerInfo);

        this.subscriptionMaker = subscriptionMaker;
        this.consumeOpts = consumeOpts;
        this.userDispatcher = userDispatcher;
        this.userMessageHandler = userMessageHandler;

        int bm = consumeOpts.getBatchSize();
        long bb = consumeOpts.getBatchBytes();
        int rePullMessages = Math.max(1, bm * consumeOpts.getThresholdPercent() / 100);
        long rePullBytes = bb == 0 ? 0 : Math.max(1, bb * consumeOpts.getThresholdPercent() / 100);
        thresholdMessages = bm - rePullMessages;
        thresholdBytes = bb == 0 ? Integer.MIN_VALUE : bb - rePullBytes;
        isTrackingBytes = rePullBytes > 0;
        doSub(true);
    }

    protected void fullResetPending() {
        pendingReceivedMessages = 0;
        pendingReceivedBytes = 0;
        noReceivedArePending = true;
        forcePull = true;
        pendingProcessedMessages = 0;
        pendingProcessedBytes = 0;
        processedHasCrossedThreshold = true;
    }

    protected void statusAdjustPending(int messages, long bytes) {
        pendingReceivedMessages = Math.max(0, pendingReceivedMessages - messages);
        pendingReceivedBytes = Math.max(0, pendingReceivedBytes - bytes);
        noReceivedArePending = pendingReceivedMessages == 0 || (isTrackingBytes && pendingReceivedBytes == 0);
        forcePull = true;
        pendingProcessedMessages = Math.max(0, pendingProcessedMessages - messages);
        pendingProcessedBytes = Math.max(0, pendingProcessedBytes - bytes);
        processedHasCrossedThreshold = pendingProcessedMessages < thresholdMessages || (isTrackingBytes && pendingProcessedBytes < thresholdBytes);
    }

    protected void aboutToPull(int messages, long bytes) {
        pendingReceivedMessages += messages;
        pendingReceivedBytes += bytes;
        noReceivedArePending = false;
        forcePull = false;
        pendingProcessedMessages += messages;
        pendingProcessedBytes += bytes;
        processedHasCrossedThreshold = false;
    }

    protected void updateProcessed(Message msg) {
        pendingProcessedMessages = Math.max(0, pendingProcessedMessages - 1);
        if (pendingProcessedMessages < thresholdMessages) {
            processedHasCrossedThreshold = true;
        }
        if (isTrackingBytes) {
            pendingProcessedBytes = Math.max(0, pendingProcessedBytes - msg.consumeByteCount());
            processedHasCrossedThreshold |= pendingProcessedBytes < thresholdBytes;
        }
        afterPendingUpdated();
    }

    @Override
    public void messageReceived(Message msg) {
        pendingReceivedMessages = Math.max(0, pendingReceivedMessages - 1);
        if (pendingReceivedMessages == 0) {
            noReceivedArePending = true;
        }
        if (isTrackingBytes) {
            pendingReceivedBytes = Math.max(0, pendingReceivedBytes - msg.consumeByteCount());
            noReceivedArePending |= pendingReceivedBytes == 0;
        }
        afterPendingUpdated();
    }

    @Override
    public void pullCompletedWithStatus(int messages, long bytes) {
        if (messages == -1) {
            // status without Nats-Pending-* headers
            fullResetPending();
        }
        else {
            statusAdjustPending(messages, bytes);
        }
        afterPendingUpdated();
    }

    @Override
    public void pullTerminatedByError() {
        if (stopped.get()) {
            fullClose();
        }
        else {
            try {
                shutdownSub();
                doSub(false);
            }
            catch (JetStreamException e) {
                resetOnException();
            }
            catch (InterruptedException e) {
                // Runs on a NATS-owned thread (heartbeat timer pool or message delivery) that
                // can't propagate the checked interrupt. Realistically only our own close
                // (shutdownNow) interrupts here, and the stopped/fullClose path handles that
                // teardown; a stray interrupt is transient, so recovering is correct. Restore
                // the flag as standard hygiene (a no-op on a pooled worker), then recover.
                Thread.currentThread().interrupt();
                resetOnException();
            }
        }
    }

    void doSub(boolean first) throws JetStreamException, InterruptedException {
        MessageHandler mh = userMessageHandler == null ? null : msg -> {
            try {
                userMessageHandler.onMessage(msg);
            }
            finally {
                updateProcessed(msg);
            }
        };

        // Whether the subscribe below succeeded, which is what decides if this attempt owns a
        // subscription that has to be cleaned up when something after it fails. It cannot be inferred
        // from the "sub" field: on the reset path that still refers to the previous, already shut down
        // subscription, and shutdownSub() dereferences it without a null check.
        boolean subInitialized = false;
        try {
            stopped.set(false);
            finished.set(false);
            super.initSub(subscriptionMaker.subscribe(mh, userDispatcher, pmm, null), !first);
            subInitialized = true;
            fullResetPending();
            rePull();
        }
        catch (JetStreamException | RuntimeException e) {
            if (subInitialized) {
                // The "subscribe" succeeded and something after it did not, so this attempt is being
                // abandoned while holding a live subscription. Nothing else will clean it up - on the
                // first == true path the consumer never reaches the caller, so both the subscription
                // and its heartbeat timer would be unreachable and leak.
                shutdownSub();
            }
            // The unchecked case matters as much as the checked one. A connection or dispatcher that is
            // closing or draining throws IllegalStateException out of the subscribe, and on the reset
            // path (first == false) shutdownSub() has already run. Letting that escape leaves the
            // consumer with no subscription and no heartbeat timer while stopped and finished both
            // still report false - it silently stops delivering, permanently. Recovering is correct:
            // resetOnException re-arms the heartbeat, which alarms again and retries the reset.
            if (first) {
                throw e;
            }
            resetOnException();
        }
    }

    private void resetOnException() {
        fullResetPending();
        pmm.updateLastMessageReceived();
        pmm.initOrResetHeartbeatTimer();
    }

    protected void afterPendingUpdated() {
        if (stopped.get()) {
            pmm.shutdownHeartbeatTimer();
            if (noReceivedArePending) {
                fullClose();
            }
        }
        else if (forcePull || processedHasCrossedThreshold) {
            rePull();
        }
    }

    protected void rePull() {
        int rePullMessages = Math.max(1, consumeOpts.getBatchSize() - pendingProcessedMessages);
        long rePullBytes = consumeOpts.getBatchBytes() == 0 ? 0 : consumeOpts.getBatchBytes() - pendingProcessedBytes;
        PinnablePullRequestOptions pro = new PinnablePullRequestOptions(pmm.currentPinId,
            PullRequestOptions.builder(rePullMessages)
                .maxBytes(rePullBytes)
                .expiresIn(consumeOpts.getExpiresInMillis())
                .idleHeartbeat(consumeOpts.getIdleHeartbeat())
                .group(consumeOpts.getGroup())
                .priority(consumeOpts.getPriority())
                .minPending(consumeOpts.getMinPending())
                .minAckPending(consumeOpts.getMinAckPending()));
        aboutToPull(rePullMessages, rePullBytes);
        sub._pull(pro, consumeOpts.raiseStatusWarnings(), this);
    }
}
