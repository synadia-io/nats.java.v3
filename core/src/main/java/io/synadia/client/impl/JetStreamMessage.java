package io.synadia.client.impl;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.impl.AckType.*;
import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;

/**
 * An incoming message that arrived from a JetStream consumer, so it carries a reply subject the
 * ack methods can publish to and metadata that can be parsed out of that reply subject.
 */
public class JetStreamMessage extends IncomingMessage {

    private JetStreamMetaData jsMetaData = null;

    /**
     * Create a JetStream message from the payload read off the wire.
     * @param data the message payload
     */
    public JetStreamMessage(byte[] data) {
        super(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ack() {
        ackReply(AckAck, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ackSync(long timeoutMillis) throws InterruptedException, TimeoutException {
        if (ackHasntBeenTermed()) {
            NatsConnection nc = getJetStreamValidatedConnection();
            if (nc.request(replyTo, AckAck.bytes, timeoutMillis) == null) {
                throw new TimeoutException("Ack response timed out.");
            }
            lastAck = AckAck;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nak() {
        ackReply(AckNak, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nakWithDelay(long nakDelayMillis) {
        ackReply(AckNak, nakDelayMillis * NANOS_PER_MILLI);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nakWithDelay(Duration nakDelay) {
        ackReply(AckNak, nakDelay.toNanos());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void inProgress() {
        ackReply(AckProgress, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void term() {
        ackReply(AckTerm, -1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JetStreamMetaData metaData() {
        if (this.jsMetaData == null) {
            this.jsMetaData = new JetStreamMetaData(this);
        }
        return this.jsMetaData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isJetStream() {
        return true; // JetStreamMessage will never be created unless it's actually a JetStream Message
    }

    private void ackReply(AckType ackType, long delayNanos) {
        if (ackHasntBeenTermed()) {
            NatsConnection nc = getJetStreamValidatedConnection();
            nc.publish(replyTo, ackType.bodyBytes(delayNanos));
            lastAck = ackType;
        }
    }

    private boolean ackHasntBeenTermed() {
        return lastAck == null || !lastAck.terminal;
    }

    NatsConnection getJetStreamValidatedConnection() {
        if (getSubscription() == null) {
            throw new IllegalStateException("Message is not bound to a subscription.");
        }

        NatsConnection c = getConnection();
        if (c == null) {
            throw new IllegalStateException("Message is not bound to a connection");
        }
        return c;
    }
}
