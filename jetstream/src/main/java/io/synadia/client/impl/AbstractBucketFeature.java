package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.PushConsumerCreator;
import io.synadia.client.api.PushOrderedConsumerCreator;
import io.synadia.client.utils.JsValidator;

import java.util.Collections;
import java.util.List;

import static io.synadia.client.impl.JetStreamConstants.JS_NO_MESSAGE_FOUND_ERR;

/**
 * Base class for the features that are built on top of a stream and present it as a named bucket,
 * such as key value and object store. Holds the connection and JetStream contexts they all need
 * and works out the stream name that backs the bucket.
 */
public abstract class AbstractBucketFeature {

    /** The connection the feature works over. */
    public final NatsConnection nc;

    /** The options the feature was created with, null if it was created with defaults. */
    public final FeatureOptions fo;

    /** JetStream context used for the feature's message traffic. */
    public final JetStream js;

    /** JetStream management context used for the feature's stream and consumer administration. */
    public final JetStreamManagement jsm;

    /** The bucket name, already validated. */
    public final String bucketName;

    /** The name of the stream backing the bucket, derived from the bucket name. */
    public final String streamName;

    protected AbstractBucketFeature(String bucketName, NatsConnection connection, FeatureOptions fo) {
        this.nc = connection;
        this.fo = fo;
        if (fo == null) {
            this.js = new JetStream(connection, null);
            this.jsm = new JetStreamManagement(connection, null);
        }
        else {
            this.js = new JetStream(connection, fo.getJetStreamOptions());
            this.jsm = new JetStreamManagement(connection, fo.getJetStreamOptions());
        }
        this.bucketName = JsValidator.validateBucketName(bucketName, true);
        this.streamName = toStreamName(bucketName);
    }

    protected AbstractBucketFeature(String bucketName, AbstractBucketFeature existing) {
        this.nc = existing.nc;
        this.fo = existing.fo;
        this.js = existing.js;
        this.jsm = existing.jsm;
        this.bucketName = JsValidator.validateBucketName(bucketName, true);
        this.streamName = toStreamName(bucketName);
    }

    abstract protected String toStreamName(String bucketName);

    /**
     * The name of the bucket, as supplied when the feature was created.
     * @return the bucket name
     */
    public String getBucketName() {
        return bucketName;
    }

    /**
     * The name of the stream that holds the bucket's data. The feature derives it from the bucket
     * name, so it is not the same string.
     * @return the stream name
     */
    public String getStreamName() {
        return streamName;
    }

    protected MessageInfo _getLast(String subject) throws JetStreamException, InterruptedException {
        try {
            return jsm.getLastMessage(streamName, subject);
        }
        catch (JetStreamApiException jsae) {
            if (jsae.getApiErrorCode() == JS_NO_MESSAGE_FOUND_ERR) {
                return null;
            }
            throw jsae;
        }
    }

    protected MessageInfo _getBySeq(long seq) throws JetStreamException, InterruptedException {
        try {
            return jsm.getMessage(streamName, seq);
        }
        catch (JetStreamApiException jsae) {
            if (jsae.getApiErrorCode() == JS_NO_MESSAGE_FOUND_ERR) {
                return null;
            }
            throw jsae;
        }
    }

    protected void visitSubject(String subject, DeliverPolicy deliverPolicy, boolean headersOnly, boolean ordered, MessageHandler handler) throws JetStreamException, InterruptedException {
        visitSubject(Collections.singletonList(subject), deliverPolicy, headersOnly, ordered, handler);
    }

    protected void visitSubject(List<String> subjects, DeliverPolicy deliverPolicy, boolean headersOnly, boolean ordered, MessageHandler handler) throws JetStreamException, InterruptedException {
        JetStreamPushSubscription sub;
        if (ordered) {
            PushOrderedConsumerCreator creator = new PushOrderedConsumerCreator()
                .deliverPolicy(deliverPolicy)
                .headersOnly(headersOnly)
                .subjects(subjects);
            sub = js.pushSubscribe(streamName, creator);
        }
        else {
            PushConsumerCreator creator = new PushConsumerCreator()
                .deliverPolicy(deliverPolicy)
                .headersOnly(headersOnly)
                .subjects(subjects);
            sub = js.pushSubscribe(streamName, creator);
        }

        long timeoutMillis = js.getTimeout();
        try {
            long pending = sub.getConsumerInfo().getCalculatedPending();
            while (pending > 0) { // no need to loop if nothing pending
                Message m = sub.nextMessage(timeoutMillis);
                if (m == null) {
                    return; // if there are no messages by the timeout, we are done.
                }
                handler.onMessage(m);
                if (--pending == 0) {
                    return;
                }
            }
        }
        finally {
            sub.unsubscribe();
        }
    }
}
