package io.synadia.client.jsapi;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.JetStreamOptions.convertDomainToPrefix;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.Validator.nullOrEmpty;

/**
 * Base class for MirrorCreator and SourceCreator.
 */
@SuppressWarnings("unchecked")
@NullMarked
abstract class StreamSourceCreator<T extends StreamSourceCreator<T>> implements JsonSerializable {
    private final String name;
    private long startSequence;
    private @Nullable ZonedDateTime startTime;
    private @Nullable String filterSubject;
    private @Nullable ExternalCreator external;
    private final List<SubjectTransformCreator> subjectTransforms = new ArrayList<>();

    /**
     * Construct a StreamSourceCreator
     * @param name the stream name
     */
    public StreamSourceCreator(String name) {
        this.name = name;
    }

    /**
     * Construct a StreamSourceCreator by copying another
     * @param newName the new name to replace the basis name
     * @param basis the source base the copy on for all the other fields
     */
    public StreamSourceCreator(String newName, StreamSourceCreator<?> basis) {
        this.name = newName;
        this.startSequence = basis.startSequence;
        this.startTime = basis.startTime;
        this.filterSubject = basis.filterSubject;
        this.external = basis.external;
        this.subjectTransforms.addAll(basis.subjectTransforms);
    }

    /**
     * Construct a StreamSourceCreator from a StreamSource (server response)
     * @param ss the stream source to copy from
     */
    StreamSourceCreator(StreamSource ss) {
        this.name = ss.getName();
        this.startSequence = ss.getStartSequence();
        this.startTime = ss.getStartTime();
        this.filterSubject = ss.getFilterSubject();
        External ext = ss.getExternal();
        this.external = ext == null ? null : new ExternalCreator(ext);
        for (SubjectTransform st : ss.getSubjectTransforms()) {
            this.subjectTransforms.add(new SubjectTransformCreator(st));
        }
    }

    /**
     * Set the start sequence
     * @param startSequence the sequence
     * @return this instance for chaining
     */
    public T startSequence(long startSequence) {
        this.startSequence = startSequence;
        return (T) this;
    }

    /**
     * Set the start time
     * @param startTime the start time
     * @return this instance for chaining
     */
    public T startTime(ZonedDateTime startTime) {
        this.startTime = startTime;
        return (T) this;
    }

    /**
     * Set the filter subject
     * @param filterSubject the filter subject
     * @return this instance for chaining
     */
    public T filterSubject(String filterSubject) {
        this.filterSubject = filterSubject;
        return (T) this;
    }

    /**
     * Set the external reference
     * @param external the external
     * @return this instance for chaining
     */
    public T external(ExternalCreator external) {
        this.external = external;
        return (T) this;
    }

    /**
     * Set the domain
     * @param domain the domain
     * @return this instance for chaining
     */
    public T domain(String domain) {
        String prefix = convertDomainToPrefix(domain);
        external = prefix == null ? null : new ExternalCreator().api(prefix);
        return (T) this;
    }

    /**
     * Set subjectTransformCreators
     * @param subjectTransformCreators the array of SubjectTransformCreator
     * @return this instance for chaining
     */
    public T subjectTransforms(SubjectTransformCreator... subjectTransformCreators) {
        return subjectTransforms(Arrays.asList(subjectTransformCreators));
    }

    /**
     * Set subjectTransformCreators
     * @param subjectTransformCreators the list of SubjectTransformCreator
     * @return this instance for chaining
     */
    public T subjectTransforms(List<SubjectTransformCreator> subjectTransformCreators) {
        this.subjectTransforms.clear();
        if (!nullOrEmpty(subjectTransformCreators)) {
            this.subjectTransforms.addAll(subjectTransformCreators);
        }
        return (T) this;
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /** @return the name */
    public String getName() { return name; }

    /** @return the start sequence */
    public long getStartSequence() { return startSequence; }

    /** @return the start time */
    @Nullable public ZonedDateTime getStartTime() { return startTime; }

    /** @return the filter subject */
    @Nullable public String getFilterSubject() { return filterSubject; }

    /** @return the external reference */
    @Nullable public ExternalCreator getExternal() { return external; }

    /** @return the subject transforms */
    public List<SubjectTransformCreator> getSubjectTransforms() { return subjectTransforms; }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, NAME, name);
        addFieldWhenGtZero(sb, OPT_START_SEQ, startSequence);
        addField(sb, OPT_START_TIME, startTime);
        addField(sb, FILTER_SUBJECT, filterSubject);
        addField(sb, EXTERNAL, external);
        addJsons(sb, SUBJECT_TRANSFORMS, subjectTransforms);
        return endJson(sb).toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof StreamSourceCreator<?> that)) return false;

        return startSequence == that.startSequence
            && Objects.equals(name, that.name)
            && Objects.equals(startTime, that.startTime)
            && Objects.equals(filterSubject, that.filterSubject)
            && Objects.equals(external, that.external)
            && subjectTransforms.equals(that.subjectTransforms);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(name);
        result = 31 * result + Long.hashCode(startSequence);
        result = 31 * result + Objects.hashCode(startTime);
        result = 31 * result + Objects.hashCode(filterSubject);
        result = 31 * result + Objects.hashCode(external);
        result = 31 * result + subjectTransforms.hashCode();
        return result;
    }
}
