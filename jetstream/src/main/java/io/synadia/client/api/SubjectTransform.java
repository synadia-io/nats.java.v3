package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.impl.JetStreamApiUtils.mapToList;
import static io.synadia.client.testutils.ApiConstants.DEST;
import static io.synadia.client.testutils.ApiConstants.SRC;

/**
 * SubjectTransform returned from the server.
 */
@NullMarked
public class SubjectTransform implements JsonSerializable {
    private final LazyJsonValue ljv;

    @Nullable
    static SubjectTransform optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new SubjectTransform(v);
    }

    static List<SubjectTransform> listOf(@Nullable LazyJsonValue v) {
        return mapToList(v, SubjectTransform::new);
    }

    SubjectTransform(LazyJsonValue v) {
        this.ljv = v;
    }

    /**
     * Get source, the subject matching filter
     * @return the source
     */
    public String getSource() {
        //noinspection DataFlowIssue we know this will not be null
        return readString(ljv, SRC);
    }

    /**
     * Get destination, the SubjectTransform Subject template
     * @return the destination
     */
    public String getDestination() {
        //noinspection DataFlowIssue we know this will not be null
        return readString(ljv, DEST);
    }

    @Override
    public String toJson() {
        return ljv.toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubjectTransform that = (SubjectTransform) o;

        if (!Objects.equals(getSource(), that.getSource())) return false;
        return Objects.equals(getDestination(), that.getDestination());
    }

    @Override
    public int hashCode() {
        getSource();
        int result = getSource().hashCode();
        getDestination();
        result = 31 * result + getDestination().hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "SubjectTransform " + toJson();
    }
}
