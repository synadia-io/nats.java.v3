package io.synadia.client.kv;

import io.synadia.client.api.*;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static io.synadia.client.kv.KeyValueUtils.toStreamName;
import static io.synadia.client.utils.JsValidator.*;

/**
 * KeyValueCreator is used to create a Key Value bucket.
 */
public class KeyValueCreator {

    private final String bucketName;
    private final StreamCreator streamCreator;

    /**
     * Constructor accepting the key value bucket name.
     * @param bucketName name of the key value bucket.
     */
    public KeyValueCreator(String bucketName) {
        this.bucketName = validateBucketName(bucketName, true);
        streamCreator = new StreamCreator(toStreamName(this.bucketName));
        maxHistoryPerKey(1);
        replicas(1);
    }

    // ----------------------------------------------------------------------------------------------------
    // Bucket setters
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the description of the bucket.
     * @param description description of the bucket.
     * @return this instance for chaining
     */
    public KeyValueCreator description(String description) {
        streamCreator.description(description);
        return this;
    }

    /**
     * Sets the maximum number of bytes in the configuration.
     * @param maxBucketSize the maximum number of bytes
     * @return this instance for chaining
     */
    public KeyValueCreator maxBucketSize(long maxBucketSize) {
        streamCreator.maxBytes(validateMaxBucketBytes(maxBucketSize));
        return this;
    }

    /**
     * Sets the maximum age for a value in this configuration.
     * @param ttl the maximum age
     * @return this instance for chaining
     */
    public KeyValueCreator ttl(Duration ttl) {
        streamCreator.maxAge(ttl == null ? Duration.ZERO : ttl);
        return this;
    }

    /**
     * Sets the maximum age for a value in this configuration.
     * @param ttlMillis the maximum age
     * @return this instance for chaining
     */
    public KeyValueCreator ttl(Long ttlMillis) {
        streamCreator.maxAge(ttlMillis == null || ttlMillis < 0 ? Duration.ZERO : Duration.ofMillis(ttlMillis));
        return this;
    }

    /**
     * Sets the storage type in the configuration.
     * @param storageType the storage type
     * @return this instance for chaining
     */
    public KeyValueCreator storageType(StorageType storageType) {
        streamCreator.storageType(storageType);
        return this;
    }

    /**
     * Sets the number of replicas.
     * @param replicas the number of replicas
     * @return this instance for chaining
     */
    public KeyValueCreator replicas(int replicas) {
        streamCreator.replicas(replicas);
        return this;
    }

    /**
     * Sets the placement directive object
     * @param placement the placement directive object
     * @return this instance for chaining
     */
    public KeyValueCreator placement(PlacementCreator placement) {
        streamCreator.placementCreator(placement);
        return this;
    }

    /**
     * Sets whether to use compression.
     * @param compression whether to use compression
     * @return this instance for chaining
     */
    public KeyValueCreator compression(boolean compression) {
        streamCreator.compressionOption(compression ? CompressionOption.S2 : CompressionOption.None);
        return this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining
     */
    public KeyValueCreator metadata(Map<String, String> metadata) {
        streamCreator.metadata(metadata);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // KV-specific setters
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the maximum number of history for any one key. Includes the current value.
     * @param maxHistoryPerKey the maximum history
     * @return this instance for chaining
     */
    public KeyValueCreator maxHistoryPerKey(int maxHistoryPerKey) {
        streamCreator.maxMessagesPerSubject(validateMaxHistory(maxHistoryPerKey));
        return this;
    }

    /**
     * Sets the maximum size for an individual value in the configuration.
     * @param maxValueSize the maximum size for a value
     * @return this instance for chaining
     */
    public KeyValueCreator maximumValueSize(int maxValueSize) {
        streamCreator.maxMessageSize(validateMaxValueSize(maxValueSize));
        return this;
    }

    /**
     * Sets the Republish options
     * @param republish the Republish object
     * @return this instance for chaining
     */
    public KeyValueCreator republish(RepublishCreator republish) {
        streamCreator.republishCreator(republish);
        return this;
    }

    /**
     * Sets the mirror in the configuration.
     * @param mirrorCreator the mirror cretor
     * @return this instance for chaining
     */
    public KeyValueCreator mirror(MirrorCreator mirrorCreator) {
        streamCreator.mirrorCreator(mirrorCreator);
        return this;
    }

    /**
     * Sets the sources in the configuration.
     * @param sourceCreators the sources
     * @return this instance for chaining
     */
    public KeyValueCreator sourceCreators(SourceCreator... sourceCreators) {
        streamCreator.sourceCreators(sourceCreators);
        return this;
    }

    /**
     * Sets the sources in the configuration
     * @param sourceCreators the sources
     * @return this instance for chaining
     */
    public KeyValueCreator sourceCreators(Collection<SourceCreator> sourceCreators) {
        streamCreator.sourceCreators(sourceCreators);
        return this;
    }

    /**
     * The limit marker TTL duration. Server accepts 1 second or more.
     * Null or {@code <= 0 } resets the limit marker ttl to none
     * {@code > 0 but < 1 second} is invalid
     * @param limitMarkerTtl the TTL duration
     * @return this instance for chaining
     */
    public KeyValueCreator limitMarkerTtl(Duration limitMarkerTtl) {
        return limitMarkerTtl(limitMarkerTtl == null ? 0 : limitMarkerTtl.toMillis());
    }

    /**
     * The limit marker TTL duration in milliseconds. Server accepts 1 second or more.
     * {@code <= 0 } resets the limit marker ttl to none
     * {@code > 0 but < 1 second} is invalid
     * @param limitMarkerTtlMillis the TTL duration
     * @return this instance for chaining
     */
    public KeyValueCreator limitMarkerTtl(long limitMarkerTtlMillis) {
        if (limitMarkerTtlMillis <= 0) {
            streamCreator.subjectDeleteMarkerTtl(null);
            streamCreator.allowMessageTtl(false);
            return this;
        }
        validateMillisGtOrEqSeconds(limitMarkerTtlMillis, 1, "Limit Marker Ttl");
        streamCreator.subjectDeleteMarkerTtl(limitMarkerTtlMillis).allowMessageTtl();
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------------------------------------------

    /**
     * The bucket name. The backing stream will be this name with a {@code KV_} prefix.
     * @return the bucket name
     */
    public @Nullable String getBucketName() { return bucketName; }

    /**
     * Free-form description to carry on the backing stream.
     * @return the description
     */
    public @Nullable String getDescription() { return streamCreator.getDescription(); }

    /**
     * Maximum total size of the bucket in bytes, applied as the stream's max bytes.
     * @return the maximum number of bytes
     */
    public long getMaxBucketSize() { return streamCreator.getMaxBytes(); }

    /**
     * How long an entry is kept before the server removes it, applied as the stream's max age.
     * @return the maximum age
     */
    public Duration getTtl() { return streamCreator.getMaxAge(); }

    /**
     * Whether the bucket is stored on file or in memory.
     * @return the storage type
     */
    public StorageType getStorageType() { return streamCreator.getStorageType(); }

    /**
     * How many replicas of the backing stream the cluster keeps.
     * @return the number of replicas
     */
    public int getReplicas() { return streamCreator.getReplicas(); }

    /**
     * Cluster placement constraints for the backing stream.
     * @return the placement directive
     */
    public @Nullable PlacementCreator getPlacement() { return streamCreator.getPlacementCreator(); }

    /**
     * Whether the backing stream uses S2 compression.
     * @return true if compression is enabled
     */
    public boolean isCompressed() { return streamCreator.getCompressionOption() == CompressionOption.S2; }

    /**
     * User metadata to carry on the backing stream.
     * @return the metadata map
     */
    public Map<String, String> getMetadata() { return streamCreator.getMetadata(); }

    /**
     * How many revisions of a single key are retained, applied as the stream's max messages per subject. A value of 1 keeps only the current value.
     * @return the maximum number of history for any one key
     */
    public long getMaxHistoryPerKey() { return streamCreator.getMaxMessagesPerSubject(); }

    /**
     * Largest value a single key may hold, in bytes, applied as the stream's max message size.
     * @return the maximum size for an individual value
     */
    public int getMaxValueSize() { return streamCreator.getMaxMessageSize(); }

    /**
     * Republish settings, which echo writes onto a second subject.
     * @return the republish configuration
     */
    public @Nullable RepublishCreator getRepublish() { return streamCreator.getRepublishCreator(); }

    /**
     * The bucket to mirror, or null when this is not a mirror.
     * @return the mirror
     */
    public @Nullable MirrorCreator getMirrorCreator() { return streamCreator.getMirrorCreator(); }

    /**
     * The buckets to source entries from.
     * @return the sources
     */
    public List<SourceCreator> getSourceCreators() { return streamCreator.getSourceCreators(); }

    /**
     * How long a delete marker is kept after a limit removes an entry, or null when markers are not used.
     * @return the limit marker ttl
     */
    public @Nullable Duration getLimitMarkerTtl() { return streamCreator.getSubjectDeleteMarkerTtl(); }

    /**
     * A copy of the stream creator being built up, so callers cannot mutate this creator's state through it.
     * @return the backing StreamCreator
     */
    public StreamCreator getStreamCreatorCopy() { return new StreamCreator(streamCreator); }

    @Override
    public String toString() {
        return "KeyValueCreator " + streamCreator.toJson();
    }
}
