package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.synadia.client.testutils.Validator;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.DEST;
import static io.synadia.client.testutils.ApiConstants.SRC;

/**
 * SubjectTransformCreator is used to create a SubjectTransform for use in a StreamCreator.
 */
@NullMarked
public class SubjectTransformCreator implements JsonSerializable {
    private final String source;
    private final String destination;

    /**
     * Construct a SubjectTransformCreator with source and destination
     * @param source the subject matching filter
     * @param destination the SubjectTransform Subject template
     */
    public SubjectTransformCreator(String source, String destination) {
        this.source = Validator.required(source, "Source");
        this.destination = Validator.required(destination, "Destination");
    }

    /**
     * Construct a SubjectTransformCreator from a SubjectTransform (server response)
     * @param st the subject transform to copy from
     */
    SubjectTransformCreator(SubjectTransform st) {
        this(st.getSource(), st.getDestination());
    }

    /**
     * Get source, the subject matching filter
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * Get destination, the SubjectTransform Subject template
     * @return the destination
     */
    public String getDestination() {
        return destination;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SRC, source);
        addField(sb, DEST, destination);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return "SubjectTransformCreator " + toJson();
    }
}
