package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.DELETED_DETAILS;
import static io.synadia.client.testutils.ApiConstants.SUBJECTS_FILTER;
import static io.synadia.client.testutils.NatsConstants.GREATER_THAN;
import static io.synadia.client.testutils.Validator.emptyAsNull;

/**
 * Object used to make a request for special stream info requests
 */
@NullMarked
public class StreamInfoOptions implements JsonSerializable {
    private final @Nullable String subjectsFilter;
    private final boolean deletedDetails;

    private StreamInfoOptions(@Nullable String subjectsFilter, boolean deletedDetails) {
        this.subjectsFilter = subjectsFilter;
        this.deletedDetails = deletedDetails;
    }

    /**
     * Get the configured subject filter
     * @return the subject filter
     */
    @Nullable
    public String getSubjectsFilter() {
        return subjectsFilter;
    }

    /**
     * Get the configured flag requesting deleted details
     * @return true if configured for deleted details
     */
    public boolean isDeletedDetails() {
        return deletedDetails;
    }

    /**
     * Create options that get subject information, filtering for subjects. Wildcards are allowed.
     * @param subjectsFilter the subject filter. &gt; is equivalent to all
     * @return the StreamInfoOptions object
     */
    public static StreamInfoOptions filterSubjects(String subjectsFilter) {
        return new Builder().filterSubjects(subjectsFilter).build();
    }

    /**
     * Create options that get subject information, filtering for all subjects.
     * @return the StreamInfoOptions object
     */
    public static StreamInfoOptions allSubjects() {
        return new Builder().allSubjects().build();
    }

    /**
     * Create options that get deleted details.
     * @return the StreamInfoOptions object
     */
    public static StreamInfoOptions deletedDetails() {
        return new Builder().deletedDetails().build();
    }

    /**
     * Get an instance of the builder
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SUBJECTS_FILTER, subjectsFilter);
        addField(sb, DELETED_DETAILS, deletedDetails);
        return endJson(sb).toString();
    }

    /**
     * StreamInfoOptions is created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are calls.
     *
     * <p>{@code new StreamInfoOptions.Builder().build()} will create a new StreamInfoOptions.
     *
     */
    public static class Builder {
        private @Nullable String subjectsFilter;
        private boolean deletedDetails;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the subjects filter, which turns on getting subject info.
         * Setting the filter to &gt; is the same as all subjects
         * Setting the filter to null clears the filter and turns off getting subject info
         * @param subjectsFilter the
         * @return the builder
         */
        public Builder filterSubjects(@Nullable String subjectsFilter) {
            this.subjectsFilter = emptyAsNull(subjectsFilter);
            return this;
        }

        /**
         * Set the subjects filter to &gt;, which turns on getting subject info.
         * @return the builder
         */
        public Builder allSubjects() {
            this.subjectsFilter = GREATER_THAN;
            return this;
        }

        /**
         * Turns on getting deleted details
         * @return the builder
         */
        public Builder deletedDetails() {
            this.deletedDetails = true;
            return this;
        }

        /**
         * Build the options
         * @return the StreamInfoOptions object
         */
        public StreamInfoOptions build() {
            return new StreamInfoOptions(subjectsFilter, deletedDetails);
        }
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof StreamInfoOptions that)) return false;
        return deletedDetails == that.deletedDetails
            && Objects.equals(subjectsFilter, that.subjectsFilter);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(subjectsFilter);
        result = 31 * result + Boolean.hashCode(deletedDetails);
        return result;
    }
}
