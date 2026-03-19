package io.synadia.client.api;

import io.nats.json.JsonValue;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

import static io.nats.json.JsonValueUtils.readDate;
import static io.synadia.client.support.ApiConstants.LAST_ACTIVE;

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
