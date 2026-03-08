package io.nats.client.api;

import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.client.support.ApiConstants.*;
import static io.nats.client.support.JsonValueUtils.readDate;

/**
 * Status of a specific consumer priority group
 */
public class PriorityGroupState {
    private final String group;
    private final String pinnedClientId;
    private final ZonedDateTime pinnedTime;

    static List<PriorityGroupState> optionalListOf(JsonValue vpgStates) {
        return JsonValueUtils.optionalListOf(vpgStates, PriorityGroupState::new);
    }

    PriorityGroupState(JsonValue vpgState) {
        group = JsonValueUtils.readString(vpgState, GROUP);
        pinnedClientId = JsonValueUtils.readString(vpgState, PINNED_CLIENT_ID);
        pinnedTime = readDate(vpgState, PINNED_TS);
    }

    /**
     * The group this status is for
     * @return the group
     */
    @NonNull
    public String getGroup() {
        return group;
    }

    /**
     * The generated ID of the pinned client
     * @return the id
     */
    @Nullable
    public String getPinnedClientId() {
        return pinnedClientId;
    }

    /**
     * The timestamp when the client was pinned
     * @return the timestamp
     */
    @Nullable
    public ZonedDateTime getPinnedTime() {
        return pinnedTime;
    }

    @Override
    public String toString() {
        return "PriorityGroupState{" +
            "group='" + group + '\'' +
            ", pinnedClientId='" + pinnedClientId + '\'' +
            ", pinnedTime=" + pinnedTime +
            '}';
    }
}
