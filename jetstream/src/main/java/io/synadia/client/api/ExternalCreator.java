package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.API;
import static io.synadia.client.utils.ApiConstants.DELIVER;

/**
 * ExternalCreator is used to create an External configuration referencing a stream source in another account.
 */
@NullMarked
public class ExternalCreator implements JsonSerializable {
    private String api;
    private @Nullable String deliver;

    /**
     * Construct an empty ExternalCreator
     */
    public ExternalCreator() {}

    /**
     * Construct an ExternalCreator with api and deliver
     * @param api the api prefix
     * @param deliver the delivery subject
     */
    public ExternalCreator(String api, @Nullable String deliver) {
        this.api = Validator.required(api, "api");
        this.deliver = deliver;
    }

    /**
     * Construct an ExternalCreator from an External (server response)
     * @param ext the external to copy from
     */
    ExternalCreator(External ext) {
        this(ext.getApi(), ext.getDeliver());
    }

    /**
     * Set the api string.
     * @param api the api
     * @return this instance for chaining
     */
    public ExternalCreator api(String api) {
        this.api = Validator.required(api, "api");
        return this;
    }

    /**
     * Set the deliver string.
     * @param deliver the deliver
     * @return this instance for chaining
     */
    public ExternalCreator deliver(@Nullable String deliver) {
        this.deliver = deliver;
        return this;
    }

    /**
     * The subject prefix that imports the other account <code>$JS.API.CONSUMER.&gt; subjects</code>
     * @return the api prefix
     */
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
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, API, api);
        addField(sb, DELIVER, deliver);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "ExternalCreator" + toJson();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof ExternalCreator that)) return false;
        return Objects.equals(api, that.api)
            && Objects.equals(deliver, that.deliver);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(api);
        result = 31 * result + Objects.hashCode(deliver);
        return result;
    }
}
