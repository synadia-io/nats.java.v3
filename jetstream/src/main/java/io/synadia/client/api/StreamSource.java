package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * Represents a stream source or mirror returned from the server.
 * Used in StreamConfiguration for both mirror and sources.
 */
@NullMarked
abstract class StreamSource extends LazyApiObject {

    protected StreamSource(LazyJsonValue v) {
        super(v);
    }

    /**
     * Get the name of the source.
     * @return the source name
     */
    public String getStreamName() {
        String name = readString(ljv, NAME);
        if (name == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " does not have required name.");
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
    public String toString() {
        return getClass().getSimpleName() + " " + ljv.toJson();
    }
}
