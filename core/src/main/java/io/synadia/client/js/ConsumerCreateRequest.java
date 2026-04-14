package io.synadia.client.js;

import io.nats.json.JsonSerializable;
import io.synadia.client.jsapi.ConsumerCreator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Object used to make a request to create a consumer. Used Internally
 */
@NullMarked
public class ConsumerCreateRequest implements JsonSerializable {
    public enum Action {
        Create("create"),
        Update("update"),
        CreateOrUpdate(null);

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
