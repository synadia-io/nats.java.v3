package io.synadia.service;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonValue;
import io.synadia.client.utils.Validator;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static io.nats.json.JsonValueUtils.readString;
import static io.nats.json.JsonValueUtils.readStringMapOrNull;
import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.Validator.validateIsRestrictedTerm;
import static io.synadia.service.ServiceConstants.*;

/**
 * Endpoint encapsulates the name, subject and metadata for a {@link ServiceEndpoint}.
 * <p>Endpoints can be used directly or as part of a group. {@link ServiceEndpoint} and {@link Group}</p>
 * <p>Endpoint names and subjects are considered 'Restricted Terms' and must only contain A-Z, a-z, 0-9, '-' or '_'</p>
 * <p>To create an Endpoint, either use a direct constructor or use the Endpoint builder
 * via the static method {@code builder()} or {@code new Endpoint.Builder()} to get an instance.
 * </p>
 * <p>Constructors and the builder validate their arguments and throw {@link IllegalArgumentException} for invalid values.</p>
 */
public class Endpoint implements JsonSerializable {
    /**
     * The name of the default queue group
     */
    public static final String DEFAULT_QGROUP = "q";

    private final String name;
    private final String subject;
    private final String queueGroup;
    private final Map<String, String> metadata;

    /**
     * Directly construct an Endpoint with a name, which becomes the subject
     * @param name the name
     * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term
     */
    public Endpoint(String name) {
        this(name, null, DEFAULT_QGROUP, null, true);
    }

    /**
     * Directly construct an Endpoint with a name, which becomes the subject, and metadata
     * @param name the name
     * @param metadata the metadata
     * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term
     */
    public Endpoint(String name, Map<String, String> metadata) {
        this(name, null, DEFAULT_QGROUP, metadata, true);
    }

    /**
     * Directly construct an Endpoint with a name and a subject
     * @param name the name
     * @param subject the subject
     * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term; or the subject is invalid
     */
    public Endpoint(String name, String subject) {
        this(name, subject, DEFAULT_QGROUP, null, true);
    }

    /**
     * Directly construct an Endpoint with a name, the subject, and metadata
     * @param name the name
     * @param subject the subject
     * @param metadata the metadata
     * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term; or the subject is invalid
     */
    public Endpoint(String name, String subject, Map<String, String> metadata) {
        this(name, subject, DEFAULT_QGROUP, metadata, true);
    }

    /**
     * Directly construct an Endpoint with a name, the subject, queueGroup and metadata
     * @param name the name
     * @param subject the subject
     * @param queueGroup the queueGroup
     * @param metadata the metadata
     * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term; the subject is invalid; or the queue group is invalid
     */
    public Endpoint(String name, String subject, String queueGroup, Map<String, String> metadata) {
        this(name, subject, queueGroup, metadata, true);
    }

    // internal use constructors
    Endpoint(String name, String subject, String queueGroup, Map<String, String> metadata, boolean validate) {
        if (validate) {
            this.name = validateIsRestrictedTerm(name, "Endpoint Name", true);
            if (subject == null) {
                this.subject = this.name;
            }
            else {
                this.subject = Validator.validateSubjectTermStrict(subject, "Endpoint Subject", false);
            }
            this.queueGroup = queueGroup == null ? null : Validator.validateSubjectTermStrict(queueGroup, "Endpoint Queue Group", true);
        }
        else {
            this.name = name;
            this.subject = subject;
            this.queueGroup = queueGroup;
        }
        this.metadata = metadata == null || metadata.isEmpty() ? null : metadata;
    }

    Endpoint(JsonValue vEndpoint) {
        name = readString(vEndpoint, NAME);
        subject = readString(vEndpoint, SUBJECT);
        queueGroup = readString(vEndpoint, QUEUE_GROUP);
        metadata = readStringMapOrNull(vEndpoint, METADATA);
    }

    Endpoint(Builder b) {
        this(b.name, b.subject, b.queueGroup, b.metadata, true);
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, NAME, name);
        addField(sb, SUBJECT, subject);
        addField(sb, QUEUE_GROUP, queueGroup);
        addField(sb, METADATA, metadata);
        return endJson(sb).toString();
    }

    @Override
    public String toString() {
        return toKey(getClass()) + toJson();
    }

    /**
     * Get the name of the Endpoint
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Get the subject of the Endpoint
     * @return the subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Get the queueGroup for the Endpoint
     * @return the queueGroup
     */
    public String getQueueGroup() {
        return queueGroup;
    }

    /**
     * Get a copy of the metadata of the Endpoint
     * @return the copy of metadata
     */
    public Map<String, String> getMetadata() {
        return metadata == null ? null : new HashMap<>(metadata);
    }

    /**
     * Get an instance of an Endpoint builder.
     * @return the instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Build an Endpoint using a fluent builder.
     */
    public static class Builder {
        private String name;
        private String subject;
        private String queueGroup = DEFAULT_QGROUP;
        private Map<String, String> metadata;

        /**
         * Construct the builder
         */
        public Builder() {}

        /**
         * Copy the Endpoint, replacing all existing endpoint information.
         * @param endpoint the endpoint to copy
         * @return the Endpoint.Builder
         */
        public Builder endpoint(Endpoint endpoint) {
            name(endpoint.getName())
                .subject(endpoint.getSubject())
                .metadata(endpoint.getMetadata());

            if (endpoint.queueGroup == null) {
                return noQueueGroup();
            }

            return queueGroup(endpoint.getQueueGroup());
        }

        /**
         * Set the name for the Endpoint, replacing any name already set.
         * @param name the endpoint name
         * @return the Endpoint.Builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the queueGroup for the Endpoint, overriding the system default queue group
         * Setting to null sets the group to the default qgroup, {@value #DEFAULT_QGROUP}. If you
         * do not want a queue group, use {@link #noQueueGroup() noQueueGroup()}
         * @param queueGroup the queueGroup
         * @return the Endpoint.Builder
         */
        public Builder queueGroup(String queueGroup) {
            this.queueGroup = queueGroup == null ? DEFAULT_QGROUP : queueGroup;
            return this;
        }

        /**
         * Set to not use a queueGroup for this endpoint
         * @return the Endpoint.Builder
         */
        public Builder noQueueGroup() {
            this.queueGroup = null;
            return this;
        }

        /**
         * Set the subject for the Endpoint, replacing any subject already set.
         * @param subject the subject
         * @return the Endpoint.Builder
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Set the metadata for the Endpoint, replacing any metadata already set.
         * @param metadata the metadata
         * @return the Endpoint.Builder
         */
        public Builder metadata(Map<String, String> metadata) {
            if (metadata == null || metadata.isEmpty()) {
                this.metadata = null;
            }
            else {
                this.metadata = new HashMap<>(metadata);
            }
            return this;
        }

        /**
         * Build the Endpoint instance.
         * @return the Endpoint instance
         * @throws IllegalArgumentException if the name is null, blank, or not a valid restricted term; the subject is invalid; or the queue group is invalid
         */
        public Endpoint build() {
            return new Endpoint(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Endpoint endpoint = (Endpoint) o;

        if (!Objects.equals(name, endpoint.name)) return false;
        if (!Objects.equals(subject, endpoint.subject)) return false;
        if (!Objects.equals(queueGroup, endpoint.queueGroup)) return false;
        return Objects.equals(metadata, endpoint.metadata);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (subject != null ? subject.hashCode() : 0);
        result = 31 * result + (queueGroup != null ? queueGroup.hashCode() : 0);
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        return result;
    }
}
