package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.api.StreamCreator.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * The StreamConfiguration is returned from the server on stream info calls.
 */
@NullMarked
public class StreamConfiguration extends LazyApiObject {

    static final StreamConfiguration EMPTY;
    static {
        EMPTY = new StreamConfiguration(LazyJsonValue.EMPTY_MAP);
    }

    /**
     * Construct a StreamConfiguration from a LazyJsonValue (server response).
     * @param v the LazyJsonValue
     */
    StreamConfiguration(LazyJsonValue v) {
        super(v);
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Gets the name of this stream configuration.
     * @return the name of the stream.
     */
    public String getName() {
        String name = readString(ljv, NAME);
        if (name == null) {
            throw new IllegalStateException("Stream Configuration does not have required name.");
        }
        return name;
    }

    /**
     * Gets the description of this stream configuration.
     * @return the description of the stream.
     */
    @Nullable
    public String getDescription() {
        return readString(ljv, DESCRIPTION);
    }

    /**
     * Gets the subjects for this stream configuration.
     * @return the subjects of the stream.
     */
    public List<String> getSubjects() {
        return readStringListOrEmpty(ljv, SUBJECTS);
    }

    /**
     * Gets the retention policy for this stream configuration.
     * @return the retention policy for this stream.
     */
    public RetentionPolicy getRetentionPolicy() {
        return RetentionPolicy.get(readString(ljv, RETENTION), DEFAULT_RETENTION_POLICY);
    }

    /**
     * Gets the compression option for this stream configuration.
     * @return the compression option for this stream.
     */
    public CompressionOption getCompressionOption() {
        return CompressionOption.get(readString(ljv, COMPRESSION), DEFAULT_COMPRESSION_OPTION);
    }

    /**
     * Gets the maximum number of consumers for this stream configuration.
     * @return the maximum number of consumers for this stream.
     */
    public long getMaxConsumers() {
        return readLong(ljv, MAX_CONSUMERS, -1);
    }

    /**
     * Gets the maximum messages for this stream configuration.
     * @return the maximum number of messages for this stream.
     */
    public long getMaxMessages() {
        return readLong(ljv, MAX_MSGS, -1);
    }

    /**
     * Gets the maximum messages per subject for this stream configuration.
     * @return the maximum number of messages per subject for this stream.
     */
    public long getMaxMessagesPerSubject() {
        return readLong(ljv, MAX_MSGS_PER_SUB, -1);
    }

    /**
     * Gets the maximum number of bytes for this stream configuration.
     * @return the maximum number of bytes for this stream.
     */
    public long getMaxBytes() {
        return readLong(ljv, MAX_BYTES, -1);
    }

    /**
     * Gets the maximum message age for this stream configuration.
     * @return the maximum message age for this stream.
     */
    public Duration getMaxAge() {
        Duration d = readNanosAsDuration(ljv, MAX_AGE);
        return d == null ? Duration.ZERO : d;
    }

    /**
     * Gets the maximum message size for this stream configuration.
     * @return the maximum message size for this stream.
     */
    public int getMaxMessageSize() {
        return readInteger(ljv, MAX_MSG_SIZE, -1);
    }

    /**
     * Gets the storage type for this stream configuration.
     * @return the storage type for this stream.
     */
    public StorageType getStorageType() {
        return StorageType.get(readString(ljv, STORAGE), DEFAULT_STORAGE_TYPE);
    }

    /**
     * Gets the number of replicas for this stream configuration.
     * @return the number of replicas
     */
    public int getReplicas() {
        return readInteger(ljv, NUM_REPLICAS, 1);
    }

    /**
     * Gets whether acknowledgements are required in this stream configuration.
     * @return true if acknowledgements are not required.
     */
    public boolean getNoAck() {
        return readBoolean(ljv, NO_ACK, false);
    }

    /**
     * Gets the template JSON for this stream configuration.
     * @return the template for this stream.
     */
    @Nullable
    public String getTemplateOwner() {
        return readString(ljv, TEMPLATE_OWNER);
    }

    /**
     * Gets the discard policy for this stream configuration.
     * @return the discard policy of the stream.
     */
    public DiscardPolicy getDiscardPolicy() {
        return DiscardPolicy.get(readString(ljv, DISCARD), DEFAULT_DISCARD_POLICY);
    }

    /**
     * Gets the duplicate checking window stream configuration.  Duration.ZERO
     * means duplicate checking is not enabled.
     * @return the duration of the window.
     */
    public Duration getDuplicateWindow() {
        Duration d = readNanosAsDuration(ljv, DUPLICATE_WINDOW);
        return d == null ? Duration.ZERO : d;
    }

    /**
     * Get the placement directives to consider when placing replicas of this stream,
     * random placement when unset. May be null.
     * @return the placement object
     */
    @Nullable
    public Placement getPlacement() {
        return Placement.optionalInstance(readValue(ljv, PLACEMENT));
    }

    /**
     * Get the republish configuration. May be null.
     * @return the republish object
     */
    @Nullable
    public Republish getRepublish() {
        return Republish.optionalInstance(readValue(ljv, REPUBLISH));
    }

    /**
     * Get the subjectTransform configuration. May be null.
     * @return the subjectTransform object
     */
    @Nullable
    public SubjectTransform getSubjectTransform() {
        return SubjectTransform.optionalInstance(readValue(ljv, SUBJECT_TRANSFORM));
    }

    /**
     * Get the consumerLimits configuration. May be null.
     * @return the consumerLimits object
     */
    @Nullable
    public ConsumerLimits getConsumerLimits() {
        return ConsumerLimits.optionalInstance(readValue(ljv, CONSUMER_LIMITS));
    }

    /**
     * The mirror definition for this stream
     * @return the mirror
     */
    @Nullable
    public Mirror getMirror() {
        return Mirror.optionalInstance(readValue(ljv, MIRROR));
    }

    /**
     * The sources for this stream
     * @return the sources
     */
    public List<Source> getSources() {
        return Source.listOf(readValue(ljv, SOURCES));
    }

    /**
     * Get the flag indicating if the stream is sealed.
     * @return the sealed flag
     */
    public boolean getSealed() {
        return readBoolean(ljv, SEALED, false);
    }

    /**
     * Get the flag indicating if the stream allows rollup.
     * @return the allows rollup flag
     */
    public boolean getAllowRollup() {
        return readBoolean(ljv, ALLOW_ROLLUP_HDRS, false);
    }

    /**
     * Get the flag indicating if the stream allows direct message access.
     * @return the allows direct flag
     */
    public boolean getAllowDirect() {
        return readBoolean(ljv, ALLOW_DIRECT, false);
    }

    /**
     * Get the flag indicating if the stream allows
     * higher performance and unified direct access for mirrors as well.
     * @return the allows direct flag
     */
    public boolean getMirrorDirect() {
        return readBoolean(ljv, MIRROR_DIRECT, false);
    }

    /**
     * Get the flag indicating if deny delete is set for the stream
     * @return the deny delete flag
     */
    public boolean getDenyDelete() {
        return readBoolean(ljv, DENY_DELETE, false);
    }

    /**
     * Get the flag indicating if deny purge is set for the stream
     * @return the deny purge flag
     */
    public boolean getDenyPurge() {
        return readBoolean(ljv, DENY_PURGE, false);
    }

    /**
     * Whether discard policy with max message per subject is applied per subject.
     * @return the discard new per subject flag
     */
    public boolean isDiscardNewPerSubject() {
        return readBoolean(ljv, DISCARD_NEW_PER_SUBJECT, false);
    }

    /**
     * Metadata for the stream; may be empty, will never be null.
     * @return the metadata map.
     */
    public Map<String, String> getMetadata() {
        Map<String, String> m = readStringMapOrNull(ljv, METADATA);
        return m == null ? Collections.emptyMap() : Collections.unmodifiableMap(m);
    }

    /**
     * The first sequence used in the stream.
     * @return the first sequence
     */
    public long getFirstSequence() {
        return readLong(ljv, FIRST_SEQ, 1);
    }

    /**
     * Whether Allow Message TTL is set
     * @return the flag
     */
    public boolean getAllowMessageTtl() {
        return readBoolean(ljv, ALLOW_MSG_TTL, false);
    }

    /**
     * Whether Allow Message Schedules is set
     * @return the flag
     */
    public boolean getAllowMessageSchedules() {
        return readBoolean(ljv, ALLOW_MSG_SCHEDULES, false);
    }

    /**
     * Whether Allow Message Counter is set
     * @return the flag
     */
    public boolean getAllowMessageCounter() {
        return readBoolean(ljv, ALLOW_MSG_COUNTER, false);
    }

    /**
     * Whether Allow Atomic Publish is set
     * @return the flag
     */
    public boolean getAllowAtomicPublish() {
        return readBoolean(ljv, ALLOW_ATOMIC, false);
    }

    /**
     * Whether Allow Batched is set
     * @return the flag
     */
    public boolean getAllowBatched() {
        return readBoolean(ljv, ALLOW_BATCHED, false);
    }

    /**
     * Get the Subject Delete Marker TTL duration. May be null.
     * @return The duration
     */
    @Nullable
    public Duration getSubjectDeleteMarkerTtl() {
        return readNanosAsDuration(ljv, SUBJECT_DELETE_MARKER_TTL);
    }

    /**
     * Gets the persist mode or null if it was not explicitly set when creating or the server did not send it with stream info
     * @return the persist mode
     */
    public PersistMode getPersistMode() {
        return PersistMode.get(readString(ljv, PERSIST_MODE), DEFAULT_PERSIST_MODE);
    }

    // ----------------------------------------------------------------------------------------------------
    // JSON
    // ----------------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return "StreamConfiguration " + ljv.toJson();
    }
}
