package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This is a JetStream specific subscription.
 */
public abstract class JetStreamSubscription extends NatsSubscription implements Subscription, JetStreamConstants {

    public static final long EXPIRE_ADJUSTMENT = 10;
    public static final long MIN_EXPIRE_MILLIS = 20;

    protected final JetStream js;

    protected String stream;
    protected String consumerName;

    protected MessageManager manager;

    JetStreamSubscription(String sid, String subject, String queueName,
                          NatsConnection connection, NatsDispatcher dispatcher,
                          JetStream js,
                          JetStreamSubscribeConfig subConf,
                          MessageManager manager)
    {
        super(sid, subject, queueName, connection, dispatcher);

        this.js = js;
        this.stream = subConf.consumerInfo.getStreamName();
        this.consumerName = subConf.consumerInfo.getName(); // should not be null since this comes from actual consumer info

        this.manager = manager;
        manager.startup(this);
    }

    /**
     * Gets the consumer name associated with the subscription.
     * @return the consumer name
     */
    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Gets the stream name associated with the subscription.
     * @return the stream name
     */
    public String getStreamName() {
        return stream;
    }

    /**
     * Gets information about the consumer behind this subscription.
     * @return consumer information
     * @throws IOException covers various communication issues with the NATS
     *         server such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the data
     */
    public ConsumerInfo getConsumerInfo() throws IOException, JetStreamApiException {
        return js.lenientGetConsumerInfo(stream, consumerName);
    }

    boolean isPullMode() {
        return false;
    }

    MessageManager getManager() { return manager; } // internal, for testing

    @Override
    protected void invalidate() {
        manager.shutdown();
        super.invalidate();
    }

    /** {@inheritDoc} */
    @Override
    public Message nextMessage(@Nullable Long timeoutMillis) throws InterruptedException, IllegalStateException {
        if (timeoutMillis == null) {
            return _nextUnmanagedNoWait(null);       // poll once, return immediately
        }
        if (timeoutMillis <= 0) {
            return _nextUnmanagedWaitForever();      // wait forever
        }
        return _nextUnmanaged(timeoutMillis, TimeUnit.MILLISECONDS, null);  // wait up to timeoutMillis
    }

    protected Message _nextUnmanagedWaitForever() throws InterruptedException {
        while (true) {
            Message msg = nextMessage(0L, TimeUnit.MILLISECONDS); // 0 = wait forever, unit is irrelevant
            if (msg != null) { // null shouldn't happen, so just a code guard b/c nextMessageInternal can return null
                switch (manager.manage(msg)) {
                    case MESSAGE:
                        return msg;
                    case STATUS_ERROR:
                        throw new JetStreamStatusException(msg.getStatus(), this);
                }
                // Check again since waiting forever for any other state
            }
        }
    }

    protected Message _nextUnmanagedNoWait(String expectedPullSubject) throws InterruptedException {
        while (true) {
            Message msg = nextMessage(null, TimeUnit.MILLISECONDS); // null = try once, no wait, unit is irrelevant
            if (msg == null) {
                return null;
            }
            switch (manager.manage(msg)) {
                case MESSAGE:
                    return msg;
                case STATUS_TERMINUS:
                    // if the status applies, return null, otherwise it's ignored, fall through
                    if (expectedPullSubject == null || expectedPullSubject.equals(msg.getSubject())) {
                        return null;
                    }
                    break;
                case STATUS_ERROR:
                    // if the status applies, throw exception, otherwise it's ignored, fall through
                    if (expectedPullSubject == null || expectedPullSubject.equals(msg.getSubject())) {
                        throw new JetStreamStatusException(msg.getStatus(), this);
                    }
                    break;
            }
            // These statuses don't apply to the message that came in,
            // so we just loop and move on to the next message.
            // 1. Any STATUS_HANDLED
            // 2. STATUS_TERMINUS or STATUS_ERRORS that aren't for expected pullSubject
        }
    }

    protected Message _nextUnmanaged(long timeout, TimeUnit timeoutUnit, String expectedPullSubject) throws InterruptedException {
        long timeoutNanos = timeoutUnit.toNanos(timeout);
        long timeLeftNanos = timeoutNanos;
        long start = NatsSystemClock.nanoTime();
        while (timeLeftNanos > 0) {
            Message msg = nextMessage(timeLeftNanos, TimeUnit.NANOSECONDS);
            if (msg == null) {
                return null; // normal timeout
            }
            switch (manager.manage(msg)) {
                case MESSAGE:
                    return msg;
                case STATUS_TERMINUS:
                    // if the status applies return null, otherwise it's ignored, fall through
                    if (expectedPullSubject == null || expectedPullSubject.equals(msg.getSubject())) {
                        return null;
                    }
                    break;
                case STATUS_ERROR:
                    // if the status applies throw exception, otherwise it's ignored, fall through
                    if (expectedPullSubject == null || expectedPullSubject.equals(msg.getSubject())) {
                        throw new JetStreamStatusException(msg.getStatus(), this);
                    }
                    break;
            }
            // anything else, try again while we have time
            timeLeftNanos = timeoutNanos - (NatsSystemClock.nanoTime() - start);
        }
        return null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " {" +
                "consumer='" + consumerName + '\'' +
                ", stream='" + stream + '\'' +
                ", deliver='" + getSubject() + '\'' +
                ", isPullMode=" + isPullMode() +
                '}';
    }
}
