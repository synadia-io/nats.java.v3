package io.synadia.client.jsapi;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readDate;
import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.JetStreamApiUtils.mapToList;

/**
 * Status of a specific consumer priority group
 */
@NullMarked
public class PriorityGroupState {
    private final LazyJsonValue ljv;
    static List<PriorityGroupState> listOf(LazyJsonValue v) {
        return mapToList(v, PriorityGroupState::new);
    }

    PriorityGroupState(LazyJsonValue v) {
        this.ljv = v;
    }

    /**
     * The group this status is for
     * @return the group
     */
    public String getGroup() {
        //noinspection DataFlowIssue
        return readString(ljv, GROUP);
    }

    /**
     * The generated ID of the pinned client
     * @return the id
     */
    @Nullable
    public String getPinnedClientId() {
        return readString(ljv, PINNED_CLIENT_ID);
    }

    /**
     * The timestamp when the client was pinned
     * @return the timestamp
     */
    @Nullable
    public ZonedDateTime getPinnedTime() {
        return readDate(ljv, PINNED_TS);
    }

    @Override
    public String toString() {
        return "PriorityGroupState " + ljv.toJson();
    }
}
