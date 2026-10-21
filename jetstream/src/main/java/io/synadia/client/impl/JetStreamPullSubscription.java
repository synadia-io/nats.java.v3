package io.synadia.client.impl;

import io.synadia.client.Subscription;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The pull methods validate their arguments and throw {@link IllegalArgumentException} for invalid values.
 */
public class JetStreamPullSubscription extends JetStreamSubscription implements Subscription {

    private final AtomicLong pullSubjectIdHolder;

    JetStreamPullSubscription(String sid, String subject,
                              NatsConnection connection, NatsDispatcher dispatcher,
                              JetStream js,
                              JetStreamSubscribeConfig subConf,
                              MessageManager manager) {
        super(sid, subject, null, connection, dispatcher, js, subConf, manager);
        pullSubjectIdHolder = new AtomicLong();
    }

    /**
     * Initiate pull with the specified batch size.
     * ! Primitive API for ADVANCED use only. Prefer {@link ConsumerContext}.
     *
     * @param batchSize the size of the batch
     */
    public void pull(int batchSize) {
        _pull(PullRequestOptions.builder(batchSize).build(), true, null);
    }

    /**
     * Initiate pull with the specified request options
     * ! Primitive API for ADVANCED use only. Prefer {@link ConsumerContext}.
     *
     * @param pullRequestOptions the options object
     */
    public void pull(PullRequestOptions pullRequestOptions) {
        _pull(pullRequestOptions, true, null);
    }

    protected String _pull(PullRequestOptions pullRequestOptions, boolean raiseStatusWarnings, PullManagerObserver pullManagerObserver) {
        String publishSubject = js.prependPrefix(String.format(JSAPI_CONSUMER_MSG_NEXT, stream, consumerName));
        String pullSubject = getSubject().replace("*", Long.toString(this.pullSubjectIdHolder.incrementAndGet()));
        manager.startPullRequest(pullSubject, pullRequestOptions, raiseStatusWarnings, pullManagerObserver);
        connection.publish(publishSubject, pullSubject, null, pullRequestOptions.serialize(), connection.isForceFlushOnRequest());
        return pullSubject;
    }

    /**
     * Initiate pull in noWait mode with the specified batch size.
     * The fetch will return immediately with as many messages as are available. Between zero and the maximum configured.
     * ! Primitive API for ADVANCED use only. Prefer {@link ConsumerContext}.
     *
     * @param batchSize the size of the batch
     */
    public void pullNoWait(int batchSize) {
        _pull(PullRequestOptions.noWait(batchSize).build(), true, null);
    }

    /**
     * Initiate pull in noWait mode with the specified batch size.
     * The fetch will return immediately with as many messages as are available, but at least one message. Between one and the maximum configured.
     * When no message is available it will wait for new messages to arrive till it expires.
     * ! Primitive API for ADVANCED use only. Prefer {@link ConsumerContext}.
     *
     * @param batchSize the size of the batch
     * @param expiresInMillis how long from now this request should be expired from the server wait list, in milliseconds
     * @throws IllegalArgumentException if expiresInMillis is not greater than zero
     */
    public void pullNoWait(int batchSize, long expiresInMillis) {
        durationGtZeroRequired(expiresInMillis, "NoWait Expires In");
        _pull(PullRequestOptions.noWait(batchSize).expiresIn(expiresInMillis).build(), true, null);
    }

    /**
     * Initiate pull for all messages available before expiration.
     * This can only be used when the subscription is pull based.
     * <p>
     * {@code sub.nextMessage(timeoutMillis)} can return a:
     * <ul>
     * <li>regular JetStream message
     * <li>null
     * </ul>
     * <p>
     * ! Primitive API for ADVANCED use only. Prefer {@link ConsumerContext}.
     *
     * @param batchSize the size of the batch
     * @param expiresInMillis how long from now this request should be expired from the server wait list, in milliseconds
     * @throws IllegalArgumentException if expiresInMillis is not greater than zero
     */
    public void pullExpiresIn(int batchSize, long expiresInMillis) {
        durationGtZeroRequired(expiresInMillis, "Expires In");
        _pull(PullRequestOptions.builder(batchSize).expiresIn(expiresInMillis).build(), true, null);
    }

    private void durationGtZeroRequired(long millis, String label) {
        if (millis <= 0) {
            throw new IllegalArgumentException(label + " wait duration must be supplied and greater than 0.");
        }
    }
}
