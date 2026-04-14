package io.synadia.client.impl;

import io.synadia.client.FeatureOptions;
import io.synadia.client.JetStreamApiException;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.js.JetStream;
import io.synadia.client.js.JetStreamManagement;
import io.synadia.client.js.JetStreamPushSubscription;
import io.synadia.client.jsapi.DeliverPolicy;
import io.synadia.client.jsapi.MessageInfo;
import io.synadia.client.jsapi.PushConsumerCreator;
import io.synadia.client.jsapi.PushOrderedConsumerCreator;
import io.synadia.client.support.Validator;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static io.synadia.client.support.JetStreamConstants.JS_NO_MESSAGE_FOUND_ERR;

public abstract class AbstractBucketFeature {

    public final NatsConnection nc;
    public final FeatureOptions fo;
    public final JetStream js;
    public final JetStreamManagement jsm;
    public final String bucketName;
    public final String streamName;

    protected AbstractBucketFeature(String bucketName, NatsConnection connection, FeatureOptions fo) throws IOException {
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
        this.bucketName = Validator.validateBucketName(bucketName, true);
        this.streamName = toStreamName(bucketName);
    }

    protected AbstractBucketFeature(String bucketName, AbstractBucketFeature existing) throws IOException {
        this.nc = existing.nc;
        this.fo = existing.fo;
        this.js = existing.js;
        this.jsm = existing.jsm;
        this.bucketName = Validator.validateBucketName(bucketName, true);
        this.streamName = toStreamName(bucketName);
    }

    abstract protected String toStreamName(String bucketName);

    public String getBucketName() {
        return bucketName;
    }

    protected String getStreamName() {
        return streamName;
    }

    protected MessageInfo _getLast(String subject) throws IOException, JetStreamApiException {
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

    protected MessageInfo _getBySeq(long seq) throws IOException, JetStreamApiException {
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

    protected void visitSubject(String subject, DeliverPolicy deliverPolicy, boolean headersOnly, boolean ordered, MessageHandler handler) throws IOException, JetStreamApiException, InterruptedException {
        visitSubject(Collections.singletonList(subject), deliverPolicy, headersOnly, ordered, handler);
    }

    protected void visitSubject(List<String> subjects, DeliverPolicy deliverPolicy, boolean headersOnly, boolean ordered, MessageHandler handler) throws IOException, JetStreamApiException, InterruptedException {
        JetStreamPushSubscription sub;
        if (ordered) {
            PushOrderedConsumerCreator creator = new PushOrderedConsumerCreator(streamName)
                .deliverPolicy(deliverPolicy)
                .headersOnly(headersOnly)
                .filterSubjects(subjects);
            sub = js.pushSubscribe(creator);
        }
        else {
            PushConsumerCreator creator = new PushConsumerCreator(streamName)
                .deliverPolicy(deliverPolicy)
                .headersOnly(headersOnly)
                .filterSubjects(subjects);
            sub = js.pushSubscribe(creator);
        }

        Duration timeout = js.getTimeout();
        try {
            long pending = sub.getConsumerInfo().getCalculatedPending();
            while (pending > 0) { // no need to loop if nothing pending
                Message m = sub.nextMessage(timeout);
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
