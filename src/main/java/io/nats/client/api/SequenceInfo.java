package io.nats.client.api;

import io.nats.client.support.JsonValue;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

import static io.nats.client.support.ApiConstants.LAST_ACTIVE;
import static io.nats.client.support.JsonValueUtils.readDate;

/**
 * This class holds the sequence numbers for a consumer and
 * stream and .
 */
public class SequenceInfo extends SequencePair {

    private final ZonedDateTime lastActive;

    SequenceInfo(JsonValue vSequenceInfo) {
        super(vSequenceInfo);
        lastActive = readDate(vSequenceInfo, LAST_ACTIVE);
    }

    /**
     * The last time a message was delivered or acknowledged (for ack_floor)
     * @return the last active time
     */
    @Nullable
    public ZonedDateTime getLastActive() {
        return lastActive;
    }

    @Override
    public String toString() {
        return "SequenceInfo{" +
            "consumerSeq=" + consumerSeq +
            ", streamSeq=" + streamSeq +
            ", lastActive=" + lastActive +
            '}';
    }
}
