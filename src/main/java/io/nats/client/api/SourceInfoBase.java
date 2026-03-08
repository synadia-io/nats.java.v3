package io.nats.client.api;

import io.nats.client.support.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

import static io.nats.client.support.ApiConstants.*;
import static io.nats.client.support.JsonValueUtils.*;

abstract class SourceInfoBase {
    protected final JsonValue jv;
    protected final String name;
    protected final long lag;
    protected final Duration active;
    protected final External external;
    protected final List<SubjectTransform> subjectTransforms;
    protected final Error error;

    SourceInfoBase(JsonValue vSourceInfo) {
        jv = vSourceInfo;
        name = readString(vSourceInfo, NAME);
        lag = readLong(vSourceInfo, LAG, 0);
        Long l = readLong(vSourceInfo, ACTIVE);
        active = l == null || l < 0 ? null : Duration.ofNanos(l);
        external = External.optionalInstance(readValue(vSourceInfo, EXTERNAL));
        subjectTransforms = SubjectTransform.optionalListOf(readValue(vSourceInfo, SUBJECT_TRANSFORMS));
        error = Error.optionalInstance(readValue(vSourceInfo, ERROR));
    }

    /**
     * The name of the Stream being replicated
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * How many uncommitted operations this peer is behind the leader
     * @return the lag
     */
    public long getLag() {
        return lag;
    }

    /**
     * Time since this peer was last seen, or null if there is no information
     * @return the time
     */
    @Nullable
    public Duration getActive() {
        return active;
    }

    /**
     * Configuration referencing a stream source in another account or JetStream domain
     * @return the external
     */
    @Nullable
    public External getExternal() {
        return external;
    }

    /**
     * The list of subject transforms, if any
     * @return the list of subject transforms
     */
    @Nullable
    public List<SubjectTransform> getSubjectTransforms() {
        return subjectTransforms;
    }

    /**
     * The last error
     * @return the error
     */
    @Nullable
    public Error getError() {
        return error;
    }
}
