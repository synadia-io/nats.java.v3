package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

import static io.nats.json.LazyJsonValueUtils.readDate;
import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.synadia.client.utils.ApiConstants.*;

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
     * @return sequence number.
     */
    public long getConsumerSequence() {
        return readLong(ljv, CONSUMER_SEQ, 0);
    }

    /**
     * Gets the stream sequence number.
     * @return sequence number.
     */
    public long getStreamSequence() {
        return readLong(ljv, STREAM_SEQ, 0);
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
