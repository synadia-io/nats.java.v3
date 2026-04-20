package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;

import java.time.ZonedDateTime;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.PAUSE_UNTIL;

/**
 * Object used to make a request to pause a consumer. Used Internally
 */
@NullMarked
public class ConsumerPauseRequest implements JsonSerializable {
    private final ZonedDateTime pauseUntil;

    /**
     * Construct a consumer pause request with the time requested to pause
     * @param pauseUntil the time
     */
    public ConsumerPauseRequest(ZonedDateTime pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();

        addField(sb, PAUSE_UNTIL, pauseUntil);

        return endJson(sb).toString();
    }
}
