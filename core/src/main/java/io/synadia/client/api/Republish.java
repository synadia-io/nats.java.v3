package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import io.synadia.client.support.Validator;
import org.jspecify.annotations.NonNull;

import static io.nats.json.JsonValueUtils.readBoolean;
import static io.nats.json.JsonValueUtils.readString;
import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Republish Configuration
 */
public class Republish implements JsonSerializable {
    private final String source;
    private final String destination;
    private final boolean headersOnly;

    static Republish optionalInstance(JsonValue vRepublish) {
        return vRepublish == null ? null : new Republish(vRepublish);
    }

    Republish(JsonValue vRepublish) {
        source = readString(vRepublish, SRC);
        destination = readString(vRepublish, DEST);
        headersOnly = readBoolean(vRepublish, HEADERS_ONLY, false);
    }

    /**
     * Construct a 'republish' object
     * @param source the Published subject matching filter
     * @param destination the RePublish Subject template
     * @param headersOnly Whether to RePublish only headers (no body)
     */
    public Republish(String source, String destination, boolean headersOnly) {
        Validator.required(source, "Source");
        Validator.required(destination, "Destination");
        this.source = source;
        this.destination = destination;
        this.headersOnly = headersOnly;
    }

    /**
     * Get source, the Published subject matching filter
     * @return the source
     */
    @NonNull
    public String getSource() {
        return source;
    }

    /**
     * Get destination, the RePublish Subject template
     * @return the destination
     */
    @NonNull
    public String getDestination() {
        return destination;
    }

    /**
     * Get headersOnly, Whether to RePublish only headers (no body)
     * @return headersOnly
     */
    public boolean isHeadersOnly() {
        return headersOnly;
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SRC, source);
        addField(sb, DEST, destination);
        addField(sb, HEADERS_ONLY, headersOnly);
        return endJson(sb).toString();
    }

    /**
     * Creates a builder for a placements object.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Placement can be created using a Builder.
     */
    public static class Builder {
        private String source;
        private String destination;
        private boolean headersOnly;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the Published Subject-matching filter
         * @param source the source
         * @return the builder
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        /**
         * Set the RePublish Subject template
         * @param destination the destination
         * @return the builder
         */
        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }

        /**
         * set Whether to RePublish only headers (no body)
         * @param headersOnly the flag
         * @return Builder
         */
        public Builder headersOnly(Boolean headersOnly) {
            this.headersOnly = headersOnly;
            return this;
        }

        /**
         * Build a Placement object
         * @return the Placement
         */
        public Republish build() {
            return new Republish(source, destination, headersOnly);
        }
    }
}
