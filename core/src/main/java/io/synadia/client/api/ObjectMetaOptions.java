package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.nats.json.JsonValueUtils.readInteger;
import static io.nats.json.JsonValueUtils.readValue;
import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.LINK;
import static io.synadia.client.support.ApiConstants.MAX_CHUNK_SIZE;

/**
 * The ObjectMetaOptions are additional options describing the object
 */
public class ObjectMetaOptions implements JsonSerializable {

    private final ObjectLink link;
    private final int chunkSize;

    private ObjectMetaOptions(Builder b) {
        link = b.link;
        chunkSize = b.chunkSize;
    }

    ObjectMetaOptions(JsonValue vOptions) {
        link = ObjectLink.optionalInstance(readValue(vOptions, LINK));
        chunkSize = readInteger(vOptions, MAX_CHUNK_SIZE, -1);
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, LINK, link);
        addField(sb, MAX_CHUNK_SIZE, chunkSize);
        return endJson(sb).toString();
    }

    /**
     * Whether the object is a link or has its own data
     * @return true if it has data
     */
    boolean hasData() {
        return link != null || chunkSize > 0;
    }

    /**
     * Get the link this object refers to
     * @return the link or null if this is not a link object
     */
    @Nullable
    public ObjectLink getLink() {
        return link;
    }

    /**
     * Get the chunk size
     * @return the chunk size in bytes
     */
    public int getChunkSize() {
        return chunkSize;
    }

    static Builder builder() {
        return new Builder();
    }

    static Builder builder(ObjectMetaOptions om) {
        return new Builder(om);
    }

    /**
     * The builder for ObjectMetaOptions
     */
    public static class Builder {
        ObjectLink link;
        int chunkSize;

        /**
         * Construct an ObjectMetaOptions.Builder
         */
        public Builder() {}

        /**
         * Construct an ObjectMetaOptions.Builder as a copy of existing options
         * @param om the existing options
         */
        public Builder(ObjectMetaOptions om) {
            link = om.link;
            chunkSize = om.chunkSize;
        }

        /**
         * Set the link
         * @param link the link
         * @return the builder
         */
        public Builder link(ObjectLink link) {
            this.link = link;
            return this;
        }

        /**
         * Set the chunk size
         * @param chunkSize the size in bytes
         * @return the builder
         */
        public Builder chunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
            return this;
        }

        /**
         * Build the ObjectMetaOptions
         * @return the ObjectMetaOptions instance
         */
        public ObjectMetaOptions build() {
            return new ObjectMetaOptions(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ObjectMetaOptions options = (ObjectMetaOptions) o;

        if (chunkSize != options.chunkSize) return false;
        return link != null ? link.equals(options.link) : options.link == null;
    }

    @Override
    public int hashCode() {
        int result = link != null ? link.hashCode() : 0;
        result = 31 * result + chunkSize;
        return result;
    }

    @Override
    public String toString() {
        return "ObjectMetaOptions{" +
            "link=" + link +
            ", chunkSize=" + chunkSize +
            '}';
    }
}
