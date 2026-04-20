package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Information about a stream being sourced
 */
@NullMarked
abstract class StreamSourceInfo {
    private final String type;
    private final LazyJsonValue ljv;

    protected StreamSourceInfo(String type, LazyJsonValue v) {
        this.type = type;
        this.ljv = v;
    }

    /**
     * The name of the Stream being replicated
     * @return the name
     */
    public String getName() {
        String name = readString(ljv, NAME);
        if (name == null) {
            throw new IllegalStateException(type + " does not have required name.");
        }
        return name;
    }

    /**
     * The subject filter to apply to the messages
     * @return the subject filter
     */
    @Nullable
    public String getFilterSubject() {
        return readString(ljv, FILTER_SUBJECT);
    }

    /**
     * How many uncommitted operations this peer is behind the leader
     * @return the lag
     */
    public long getLag() {
        return readLong(ljv, LAG, 0);
    }

    /**
     * Time since this peer was last seen, or null if there is no information
     * @return the time
     */
    @Nullable
    public Duration getActive() {
        Long l = readLong(ljv, ACTIVE);
        return l == null || l < 0 ? null : Duration.ofNanos(l);
    }

    /**
     * Configuration referencing a stream source in another account or JetStream domain
     * @return the external
     */
    @Nullable
    public External getExternal() {
        return External.optionalInstance(readValue(ljv, EXTERNAL));
    }

    /**
     * The list of subject transforms, if any
     * @return the list of subject transforms
     */
    public List<SubjectTransform> getSubjectTransforms() {
        return SubjectTransform.listOf(readValue(ljv, SUBJECT_TRANSFORMS));
    }

    /**
     * The last error
     * @return the error
     */
    @Nullable
    public Error getError() {
        return Error.optionalInstance(readValue(ljv, ERROR));
    }

    @Override
    public String toString() {
        return type + " " + ljv.toJson();
    }
}
