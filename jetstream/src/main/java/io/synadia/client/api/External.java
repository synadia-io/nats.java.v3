package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.testutils.ApiConstants.API;
import static io.synadia.client.testutils.ApiConstants.DELIVER;

/**
 * External configuration referencing a stream source in another account.
 * Returned from the server.
 */
@NullMarked
public class External implements JsonSerializable {
    private final LazyJsonValue ljv;

    @Nullable
    static External optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new External(v);
    }

    External(LazyJsonValue v) {
        this.ljv = v;
    }

    /**
     * The subject prefix that imports the other account <code>$JS.API.CONSUMER.&gt; subjects</code>
     * @return the api prefix
     */
    @Nullable
    public String getApi() {
        return readString(ljv, API);
    }

    /**
     * The delivery subject to use for the push consumer.
     * @return delivery subject
     */
    @Nullable
    public String getDeliver() {
        return readString(ljv, DELIVER);
    }

    @Override
    public String toJson() {
        return ljv.toJson();
    }

    @Override
    public String toString() {
        return "External " + ljv.toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        External external = (External) o;

        if (!Objects.equals(getApi(), external.getApi())) return false;
        return Objects.equals(getDeliver(), external.getDeliver());
    }

    @Override
    public int hashCode() {
        int result = getApi() != null ? getApi().hashCode() : 0;
        result = 31 * result + (getDeliver() != null ? getDeliver().hashCode() : 0);
        return result;
    }
}
