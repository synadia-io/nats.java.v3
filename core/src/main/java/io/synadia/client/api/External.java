package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import io.nats.json.JsonValueUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.API;
import static io.synadia.client.support.ApiConstants.DELIVER;

/**
 * External configuration referencing a stream source in another account
 */
public class External implements JsonSerializable {
    private final String api;
    private final String deliver;

    static External optionalInstance(JsonValue vExternal) {
        return vExternal == null ? null : new External(vExternal);
    }

    External(JsonValue vExternal) {
        api = JsonValueUtils.readString(vExternal, API);
        deliver = JsonValueUtils.readString(vExternal, DELIVER);
    }

    /**
     * Construct the External configuration
     * @param api the api prefix
     * @param deliver the delivery subject
     */
    public External(String api, String deliver) {
        this.api = api;
        this.deliver = deliver;
    }

    /**
     * Construct an External configuration copying the information from an External configuration
     * @param external the source configuration
     */
    public External(External external) {
        this.api = external.api;
        this.deliver = external.deliver;
    }
    /**
     * Returns a JSON representation of this mirror
     *
     * @return json mirror json string
     */
    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, API, api);
        addField(sb, DELIVER, deliver);
        return endJson(sb).toString();
    }

    /**
     * The subject prefix that imports the other account <code>$JS.API.CONSUMER.&gt; subjects</code>
     * @return the api prefix
     */
    @Nullable
    public String getApi() {
        return api;
    }

    /**
     * The delivery subject to use for the push consumer.
     * @return delivery subject
     */
    @Nullable
    public String getDeliver() {
        return deliver;
    }

    @Override
    public String toString() {
        return "External{" +
                "api='" + api + '\'' +
                ", deliver='" + deliver + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        External external = (External) o;

        if (api != null ? !api.equals(external.api) : external.api != null) return false;
        return deliver != null ? deliver.equals(external.deliver) : external.deliver == null;
    }

    @Override
    public int hashCode() {
        int result = api != null ? api.hashCode() : 0;
        result = 31 * result + (deliver != null ? deliver.hashCode() : 0);
        return result;
    }

    /**
     * Creates a builder for an External object.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * External can be created using a Builder.
     */
    public static class Builder {
        private String api;
        private String deliver;

        /**
         * Construct a builder for an External object
         */
        public Builder() {}

        /**
         * Set the api string.
         * @param api the api
         * @return the builder
         */
        public Builder api(String api) {
            this.api = api;
            return this;
        }

        /**
         * Set the deliver string.
         * @param deliver the deliver
         * @return the builder
         */
        public Builder deliver(String deliver) {
            this.deliver = deliver;
            return this;
        }

        /**
         * Build an External object
         * @return the External object
         */
        public External build() {
            return new External(api, deliver);
        }
    }
}
