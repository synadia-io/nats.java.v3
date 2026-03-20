package io.synadia.client.api;

import io.nats.json.JsonValue;

import static io.nats.json.JsonValueUtils.readLong;
import static io.synadia.client.support.ApiConstants.CONSUMER_SEQ;
import static io.synadia.client.support.ApiConstants.STREAM_SEQ;

/**
 * This class holds the sequence numbers for a consumer and
 * stream.
 */
public class SequencePair {
    protected final long consumerSeq;
    protected final long streamSeq;

    SequencePair(JsonValue vSequencePair) {
        consumerSeq = readLong(vSequencePair, CONSUMER_SEQ, 0);
        streamSeq = readLong(vSequencePair, STREAM_SEQ, 0);
    }

    /**
     * Gets the consumer sequence number.
     * @return sequence number.
     */
    public long getConsumerSequence() {
        return consumerSeq;
    }

    /**
     * Gets the stream sequence number.
     * @return sequence number.
     */
    public long getStreamSequence() {
        return streamSeq;
    }
}
