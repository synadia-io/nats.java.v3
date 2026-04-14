package io.synadia.client.jsapi;

import io.nats.json.JsonSerializable;
import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.support.ApiConstants.*;

/**
 * Represents a stream source or mirror returned from the server.
 * Used in StreamConfiguration for both mirror and sources.
 */
@NullMarked
abstract class StreamSource implements JsonSerializable {
    private final LazyJsonValue ljv;
    private final String type;

    protected StreamSource(String type, LazyJsonValue v) {
        this.type = type;
        this.ljv = v;
    }

    /**
     * Get the name of the source.
     * @return the source name
     */
    public String getName() {
        String name = readString(ljv, NAME);
        if (name == null) {
            throw new IllegalStateException(type + " does not have required name.");
        }
        return name;
    }

    /**
     * Get the configured start sequence
     * @return the start sequence
     */
    public long getStartSequence() {
        return readLong(ljv, OPT_START_SEQ, 0);
    }

    /**
     * Get the configured start time
     * @return the start time
     */
    @Nullable
    public ZonedDateTime getStartTime() {
        return readDate(ljv, OPT_START_TIME);
    }

    /**
     * Get the configured filter subject
     * @return the filter subject
     */
    @Nullable
    public String getFilterSubject() {
        return readString(ljv, FILTER_SUBJECT);
    }

    /**
     * Get the External reference
     * @return the External
     */
    @Nullable
    public External getExternal() {
        return External.optionalInstance(readValue(ljv, EXTERNAL));
    }

    /**
     * Get the subject transforms
     * @return the list of subject transforms
     */
    public List<SubjectTransform> getSubjectTransforms() {
        return SubjectTransform.listOf(readValue(ljv, SUBJECT_TRANSFORMS));
    }

    @Override
    public String toJson() {
        return ljv.toJson();
    }

    @Override
    public String toString() {
        return type + " " + ljv.toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StreamSource that = (StreamSource) o;

        if (getStartSequence() != that.getStartSequence()) return false;
        if (!Objects.equals(getName(), that.getName())) return false;
        if (!Objects.equals(getStartTime(), that.getStartTime())) return false;
        if (!Objects.equals(getFilterSubject(), that.getFilterSubject())) return false;
        if (!Objects.equals(getExternal(), that.getExternal())) return false;
        return Objects.equals(getSubjectTransforms(), that.getSubjectTransforms());
    }

    @Override
    public int hashCode() {
        int result = getName().hashCode();
        result = 31 * result + Long.hashCode(getStartSequence());
        result = 31 * result + (getStartTime() != null ? getStartTime().hashCode() : 0);
        result = 31 * result + (getFilterSubject() != null ? getFilterSubject().hashCode() : 0);
        result = 31 * result + (getExternal() != null ? getExternal().hashCode() : 0);
        result = 31 * result + getSubjectTransforms().hashCode();
        return result;
    }
}
