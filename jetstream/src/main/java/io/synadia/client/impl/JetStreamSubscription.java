package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * This is a JetStream specific subscription.
 */
public abstract class JetStreamSubscription extends NatsSubscription implements Subscription, JetStreamConstants {

    /**
     * Millis subtracted from the caller's max wait when setting the pull request expiration,
     * {@value}, so the server expires the request before the client gives up waiting for it.
     */
    public static final long EXPIRE_ADJUSTMENT = 10;

    /**
     * Shortest max wait, in millis, that still gets {@link #EXPIRE_ADJUSTMENT} applied, {@value}.
     * Below this the wait is sent to the server as is.
     */
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

    void setConsumerName(String consumerName) {
        this.consumerName = consumerName;
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
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ConsumerInfo getConsumerInfo() throws JetStreamException, InterruptedException {
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
    public @Nullable Message nextMessage(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 millisecond.");
        }
        return _nextUnmanaged(timeoutMillis, TimeUnit.MILLISECONDS, null);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessage(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 1) {
            throw new IllegalArgumentException("Timeout must be at least 1 " + unit + ".");
        }
        return _nextUnmanaged(timeout, unit, null);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageNoWait() throws InterruptedException {
        return _nextUnmanagedNoWait(null);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable Message nextMessageWaitForever() throws InterruptedException {
        return _nextUnmanagedWaitForever();
    }

    // The reason this does not have an expectedPullSubject parameter is because it happens
    // that it is only called internally from place(s) that do not have an expected subject
    protected Message _nextUnmanagedWaitForever() throws InterruptedException {
        while (true) {
            Message msg = nextMessageInternal(0L, TimeUnit.MILLISECONDS); // 0 = wait forever, unit is irrelevant
            if (msg != null) { // null shouldn't happen, so just a code guard b/c nextMessageInternal can return null
                switch (manager.manage(msg)) {
                    case MESSAGE:
                        return msg;
                    case STATUS_ERROR:
                        throw new JetStreamStatusInternalException("Error during next message / wait forever", msg.getStatus(), this);
                }
                // Check again since waiting forever for any other state
            }
        }
    }

    protected Message _nextUnmanagedNoWait(String expectedPullSubject) throws InterruptedException {
        while (true) {
            Message msg = nextMessageInternal(null, TimeUnit.MILLISECONDS); // null = try once, no wait, unit is irrelevant
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
                        throw new JetStreamStatusInternalException("Pull Subject Mismatch", msg.getStatus(), this);
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
            Message msg = nextMessageInternal(timeLeftNanos, TimeUnit.NANOSECONDS);
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
                        throw new JetStreamStatusInternalException("Pull Subject Mismatch", msg.getStatus(), this);
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
