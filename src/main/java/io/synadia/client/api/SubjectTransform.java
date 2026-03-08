package io.synadia.client.api;

import io.synadia.client.support.JsonSerializable;
import io.synadia.client.support.JsonValue;
import io.synadia.client.support.JsonValueUtils;
import io.synadia.client.support.Validator;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

import static io.synadia.client.support.ApiConstants.DEST;
import static io.synadia.client.support.ApiConstants.SRC;
import static io.synadia.client.support.JsonUtils.*;
import static io.synadia.client.support.JsonValueUtils.readString;

/**
 * SubjectTransform
 */
public class SubjectTransform implements JsonSerializable {
    private final String source;
    private final String destination;

    static SubjectTransform optionalInstance(JsonValue vSubjectTransform) {
        return vSubjectTransform == null ? null : new SubjectTransform(vSubjectTransform);
    }

    static List<SubjectTransform> optionalListOf(JsonValue vSubjectTransforms) {
        return JsonValueUtils.optionalListOf(vSubjectTransforms, SubjectTransform::new);
    }

    SubjectTransform(JsonValue vSubjectTransform) {
        source = readString(vSubjectTransform, SRC);
        destination = readString(vSubjectTransform, DEST);
    }

    /**
     * Construct a 'SubjectTransform' object
     * @param source the subject matching filter
     * @param destination the SubjectTransform Subject template
     */
    public SubjectTransform(@NonNull String source, @NonNull String destination) {
        this.source = Validator.required(source, "Source");
        this.destination = Validator.required(destination, "Destination");
    }

    /**
     * Get source, the subject matching filter
     * @return the source
     */
    @NonNull
    public String getSource() {
        return source;
    }

    /**
     * Get destination, the SubjectTransform Subject template
     * @return the destination
     */
    @NonNull
    public String getDestination() {
        return destination;
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SRC, source);
        addField(sb, DEST, destination);
        return endJson(sb).toString();
    }

    /**
     * Creates a builder for a placements object.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Placement can be created using a Builder.
     */
    public static class Builder {
        private String source;
        private String destination;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the Published Subject-matching filter
         * @param source the source
         * @return the builder
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        /**
         * Set the SubjectTransform Subject template
         * @param destination the destination
         * @return the builder
         */
        public Builder destination(String destination) {
            this.destination = destination;
            return this;
        }

        /**
         * Build a Placement object
         * @return the Placement
         */
        public SubjectTransform build() {
            return new SubjectTransform(source, destination);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SubjectTransform that = (SubjectTransform) o;

        if (!Objects.equals(source, that.source)) return false;
        return Objects.equals(destination, that.destination);
    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + (destination != null ? destination.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SubjectTransform{" +
            "source='" + source + '\'' +
            ", destination='" + destination + '\'' +
            '}';
    }
}
