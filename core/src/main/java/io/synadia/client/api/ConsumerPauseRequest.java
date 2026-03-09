package io.synadia.client.api;

import io.synadia.client.support.JsonSerializable;
import org.jspecify.annotations.NonNull;

import java.time.ZonedDateTime;

import static io.synadia.client.support.ApiConstants.PAUSE_UNTIL;
import static io.synadia.client.support.JsonUtils.*;

/**
 * Object used to make a request to pause a consumer. Used Internally
 */
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
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();

        addField(sb, PAUSE_UNTIL, pauseUntil);

        return endJson(sb).toString();
    }
}
