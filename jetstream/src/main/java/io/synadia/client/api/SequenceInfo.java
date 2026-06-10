package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.ZonedDateTime;

import static io.nats.json.LazyJsonValueUtils.readDate;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readUnsignedBigIntegerOrZero;
import static io.synadia.client.utils.ApiUtils.readUnsignedLongOrZero;

/**
 * This class holds the sequence numbers for a consumer and
 * stream, plus the last active time.
 */
@NullMarked
public class SequenceInfo extends LazyApiObject {

    static final SequenceInfo EMPTY;
    static {
        EMPTY = new SequenceInfo(LazyJsonValue.EMPTY_MAP);
    }

    SequenceInfo(LazyJsonValue v) {
        super(v);
    }

    /**
     * Gets the consumer sequence number.
     * <p>The server value is an unsigned 64-bit number.
     * @return sequence number
     */
    public long getConsumerSequence() {
        return readUnsignedLongOrZero(ljv, CONSUMER_SEQ);
    }

    /**
     * Gets the consumer sequence number as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getConsumerSequence()}.
     * @return sequence number, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getConsumerSequenceAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, CONSUMER_SEQ);
    }

    /**
     * Gets the stream sequence number.
     * <p>The server value is an unsigned 64-bit number.
     * @return sequence number
     */
    public long getStreamSequence() {
        return readUnsignedLongOrZero(ljv, STREAM_SEQ);
    }

    /**
     * Gets the stream sequence number as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getStreamSequence()}.
     * @return sequence number, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getStreamSequenceAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, STREAM_SEQ);
    }

    /**
     * The last time a message was delivered or acknowledged (for ack_floor)
     * @return the last active time
     */
    @Nullable
    public ZonedDateTime getLastActive() {
        return readDate(ljv, LAST_ACTIVE);
    }

    @Override
    public String toString() {
        return "SequenceInfo " + ljv.toJson();
    }
}
