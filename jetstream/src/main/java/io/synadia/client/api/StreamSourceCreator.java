package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.impl.JetStreamApiUtils.replaceAll;
import static io.synadia.client.impl.JetStreamOptions.convertDomainToPrefix;
import static io.synadia.client.testutils.ApiConstants.*;
import static io.synadia.client.testutils.JsValidator.validateStreamName;

/**
 * Base class for MirrorCreator and SourceCreator.
 */
@SuppressWarnings("unchecked")
@NullMarked
abstract class StreamSourceCreator<T extends StreamSourceCreator<T>> implements JsonSerializable {
    private final String streamName;
    private long startSequence;
    private @Nullable ZonedDateTime startTime;
    private @Nullable String filterSubject;
    private @Nullable ExternalCreator externalCreator;
    private final List<SubjectTransformCreator> subjectTransformCreators = new ArrayList<>();

    /**
     * Construct a StreamSourceCreator
     * @param streamName the stream name
     */
    public StreamSourceCreator(String streamName) {
        this.streamName = validateStreamName(streamName, true);
    }

    /**
     * Construct a StreamSourceCreator by copying another
     * @param newName the new name to replace the basis name
     * @param basis the source base the copy on for all the other fields
     */
    public StreamSourceCreator(String newName, StreamSourceCreator<?> basis) {
        this.streamName = newName;
        this.startSequence = basis.startSequence;
        this.startTime = basis.startTime;
        this.filterSubject = basis.filterSubject;
        this.externalCreator = basis.externalCreator;
        this.subjectTransformCreators.addAll(basis.subjectTransformCreators);
    }

    /**
     * Construct a StreamSourceCreator from a StreamSource (server response)
     * @param ss the stream source to copy from
     */
    StreamSourceCreator(StreamSource ss) {
        this.streamName = ss.getStreamName();
        this.startSequence = ss.getStartSequence();
        this.startTime = ss.getStartTime();
        this.filterSubject = ss.getFilterSubject();
        External ext = ss.getExternal();
        this.externalCreator = ext == null ? null : new ExternalCreator(ext);
        for (SubjectTransform st : ss.getSubjectTransforms()) {
            this.subjectTransformCreators.add(new SubjectTransformCreator(st));
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
    public T startTime(@Nullable ZonedDateTime startTime) {
        this.startTime = startTime;
        return (T) this;
    }

    /**
     * Set the filter subject
     * @param filterSubject the filter subject
     * @return this instance for chaining
     */
    public T filterSubject(@Nullable String filterSubject) {
        this.filterSubject = filterSubject;
        return (T) this;
    }

    /**
     * Set the external reference
     * @param external the external
     * @return this instance for chaining
     */
    public T externalCreator(@Nullable ExternalCreator external) {
        this.externalCreator = external;
        return (T) this;
    }

    /**
     * Set the domain
     * @param domain the domain
     * @return this instance for chaining
     */
    public T domain(String domain) {
        String prefix = convertDomainToPrefix(domain);
        externalCreator = prefix == null ? null : new ExternalCreator().api(prefix);
        return (T) this;
    }

    /**
     * Set subjectTransformCreators
     * @param subjectTransformCreators the array of SubjectTransformCreator
     * @return this instance for chaining
     */
    public T subjectTransforms(SubjectTransformCreator... subjectTransformCreators) {
        replaceAll(this.subjectTransformCreators, subjectTransformCreators);
        return (T) this;
    }

    /**
     * Set subjectTransformCreators
     * @param subjectTransformCreators the list of SubjectTransformCreator
     * @return this instance for chaining
     */
    public T subjectTransforms(List<SubjectTransformCreator> subjectTransformCreators) {
        replaceAll(this.subjectTransformCreators, subjectTransformCreators);
        return (T) this;
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /** @return the name */
    public String getStreamName() { return streamName; }

    /** @return the start sequence */
    public long getStartSequence() { return startSequence; }

    /** @return the start time */
    @Nullable public ZonedDateTime getStartTime() { return startTime; }

    /** @return the filter subject */
    @Nullable public String getFilterSubject() { return filterSubject; }

    /** @return the external reference */
    @Nullable public ExternalCreator getExternalCreator() { return externalCreator; }

    /** @return the subject transforms */
    public List<SubjectTransformCreator> getSubjectTransformCreators() { return subjectTransformCreators; }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, NAME, streamName);
        addFieldWhenGtZero(sb, OPT_START_SEQ, startSequence);
        addField(sb, OPT_START_TIME, startTime);
        addField(sb, FILTER_SUBJECT, filterSubject);
        addField(sb, EXTERNAL, externalCreator);
        addJsons(sb, SUBJECT_TRANSFORMS, subjectTransformCreators);
        return endJson(sb).toString();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof StreamSourceCreator<?> that)) return false;

        return startSequence == that.startSequence
            && Objects.equals(streamName, that.streamName)
            && Objects.equals(startTime, that.startTime)
            && Objects.equals(filterSubject, that.filterSubject)
            && Objects.equals(externalCreator, that.externalCreator)
            && subjectTransformCreators.equals(that.subjectTransformCreators);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(streamName);
        result = 31 * result + Long.hashCode(startSequence);
        result = 31 * result + Objects.hashCode(startTime);
        result = 31 * result + Objects.hashCode(filterSubject);
        result = 31 * result + Objects.hashCode(externalCreator);
        result = 31 * result + subjectTransformCreators.hashCode();
        return result;
    }
}
