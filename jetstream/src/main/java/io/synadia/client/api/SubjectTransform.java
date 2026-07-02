package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.synadia.client.utils.ApiConstants.DEST;
import static io.synadia.client.utils.ApiConstants.SRC;
import static io.synadia.client.utils.ApiUtils.mapToList;
import static io.synadia.client.utils.ApiUtils.readStringOrEmpty;

/**
 * SubjectTransform returned from the server.
 */
@NullMarked
public class SubjectTransform extends LazyApiObject {

    @Nullable
    static SubjectTransform optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new SubjectTransform(v);
    }

    static List<SubjectTransform> listOf(@Nullable LazyJsonValue v) {
        return mapToList(v, SubjectTransform::new);
    }

    SubjectTransform(LazyJsonValue v) {
        super(v);
    }

    /**
     * Get source, the subject matching filter
     * @return the source
     */
    public String getSource() {
        return readStringOrEmpty(ljv, SRC);
    }

    /**
     * Get destination, the SubjectTransform Subject template
     * @return the destination
     */
    public String getDestination() {
        return readStringOrEmpty(ljv, DEST);
    }

    @Override
    public String toString() {
        return "SubjectTransform " + toJson();
    }
}
