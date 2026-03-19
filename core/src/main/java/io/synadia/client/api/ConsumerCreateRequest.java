package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NonNull;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Object used to make a request to create a consumer. Used Internally
 */
public class ConsumerCreateRequest implements JsonSerializable {
    public enum Action {
        Create("create"),
        Update("update"),
        CreateOrUpdate(null);

        public final String actionText;

        Action(String actionText) {
            this.actionText = actionText;
        }
    }

    private final String streamName;
    private final ConsumerConfiguration config;
    private final Action action;

    public ConsumerCreateRequest(String streamName, ConsumerConfiguration config) {
        this.streamName = streamName;
        this.config = config;
        this.action = Action.CreateOrUpdate;
    }

    public ConsumerCreateRequest(String streamName, ConsumerConfiguration config, Action action) {
        this.streamName = streamName;
        this.config = config;
        this.action = action;
    }

    @NonNull
    public String getStreamName() {
        return streamName;
    }

    @NonNull
    public ConsumerConfiguration getConfig() {
        return config;
    }

    @NonNull
    public Action getAction() {
        return action;
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();

        addField(sb, STREAM_NAME, streamName);
        addField(sb, ACTION, action.actionText);
        addField(sb, CONFIG, config);

        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ConsumerCreateRequest{" +
                "streamName='" + streamName + '\'' +
                ", " + config +
                '}';
    }
}
