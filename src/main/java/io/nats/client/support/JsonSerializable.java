package io.nats.client.support;

import org.jspecify.annotations.NonNull;

import java.nio.charset.StandardCharsets;

/**
 * A general interface for an object to be able to converted to JSON
 */
public interface JsonSerializable {
    /**
     * Get the JSON string
     * @return the string
     */
    @NonNull
    String toJson();

    /**
     * Get the JSON string as a byte array
     * @return the byte array
     */
    default byte @NonNull [] serialize() {
        return toJson().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Get the JsonValue for the object
     * @return the value
     */
    @NonNull
    default JsonValue toJsonValue() {
        return JsonParser.parseUnchecked(toJson());
    }
}
