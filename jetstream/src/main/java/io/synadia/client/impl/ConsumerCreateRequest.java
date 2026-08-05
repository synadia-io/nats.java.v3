package io.synadia.client.impl;

import io.nats.json.JsonSerializable;
import io.synadia.client.api.ConsumerCreator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * Object used to make a request to create a consumer. Used Internally
 */
@NullMarked
public class ConsumerCreateRequest implements JsonSerializable {
    /**
     * Tells the server whether the consumer is expected to already exist.
     */
    public enum Action {
        /** Create the consumer, failing if one with that name already exists. */
        Create("create"),

        /** Update an existing consumer, failing if there is none with that name. */
        Update("update"),

        /** Create the consumer or update it if it already exists; sends no action to the server. */
        CreateOrUpdate(null);

        /** The value sent in the request's action field, null for the server default. */
        public final @Nullable String actionText;

        Action(@Nullable String actionText) {
            this.actionText = actionText;
        }
    }

    final String streamName;
    final ConsumerCreator<?> creator;
    final Action action;

    ConsumerCreateRequest(String streamName, ConsumerCreator<?> creator, Action action) {
        this.streamName = streamName;
        this.creator = creator;
        this.action = action;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, STREAM_NAME, streamName);
        addField(sb, ACTION, action.actionText);
        addField(sb, CONFIG, creator);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ConsumerCreateRequest " + toJson();
    }
}
