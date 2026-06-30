package io.synadia.client.kv;

import io.synadia.client.api.*;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;

import static io.synadia.client.kv.KeyValueUtils.toStreamName;
import static io.synadia.client.utils.JsValidator.*;

/**
 * KeyValueConfigurationCreator is used to create a Key Value bucket.
 */
public class KeyValueConfigurationCreator {

    private final String bucketName;
    private final StreamCreator streamCreator;
    private Duration ttl = Duration.ZERO;
    private @Nullable MirrorCreator mirror;
    private @Nullable Duration limitMarkerTtl;
    private final List<SourceCreator> sourceCreators = new ArrayList<>();

    /**
     * Constructor accepting the key value bucket name.
     * @param bucketName name of the key value bucket.
     */
    public KeyValueConfigurationCreator(String bucketName) {
        this.bucketName = validateBucketName(bucketName, true);
        streamCreator = new StreamCreator(toStreamName(this.bucketName));
    }

    // ----------------------------------------------------------------------------------------------------
    // Bucket setters
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the description of the store.
     * @param description description of the store.
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator description(String description) {
        streamCreator.description(description);
        return this;
    }

    /**
     * Sets the maximum number of bytes in the configuration.
     * @param maxBucketSize the maximum number of bytes
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator maxBucketSize(long maxBucketSize) {
        streamCreator.maxBytes(validateMaxBucketBytes(maxBucketSize));
        return this;
    }

    /**
     * Sets the maximum age for a value in this configuration.
     * @param ttl the maximum age
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator ttl(Duration ttl) {
        this.ttl = ttl == null ? Duration.ZERO : ttl;
        streamCreator.maxAge(this.ttl);
        return this;
    }

    /**
     * Sets the maximum age for a value in this configuration.
     * @param ttlMillis the maximum age
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator ttl(Long ttlMillis) {
        this.ttl = ttlMillis == null || ttlMillis < 0 ? Duration.ZERO : Duration.ofMillis(ttlMillis);
        streamCreator.maxAge(this.ttl);
        return this;
    }

    /**
     * Sets the storage type in the configuration.
     * @param storageType the storage type
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator storageType(StorageType storageType) {
        streamCreator.storageType(storageType);
        return this;
    }

    /**
     * Sets the number of replicas.
     * @param replicas the number of replicas
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator replicas(int replicas) {
        streamCreator.replicas(replicas);
        return this;
    }

    /**
     * Sets the placement directive object
     * @param placement the placement directive object
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator placement(PlacementCreator placement) {
        streamCreator.placementCreator(placement);
        return this;
    }

    /**
     * Sets whether to use compression.
     * @param compression whether to use compression
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator compression(boolean compression) {
        streamCreator.compressionOption(compression ? CompressionOption.S2 : CompressionOption.None);
        return this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator metadata(Map<String, String> metadata) {
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
    public KeyValueConfigurationCreator maxHistoryPerKey(int maxHistoryPerKey) {
        streamCreator.maxMessagesPerSubject(validateMaxHistory(maxHistoryPerKey));
        return this;
    }

    /**
     * Sets the maximum size for an individual value in the configuration.
     * @param maxValueSize the maximum size for a value
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator maximumValueSize(int maxValueSize) {
        streamCreator.maxMessageSize(validateMaxValueSize(maxValueSize));
        return this;
    }

    /**
     * Sets the Republish options
     * @param republish the Republish object
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator republish(RepublishCreator republish) {
        streamCreator.republishCreator(republish);
        return this;
    }

    /**
     * Sets the mirror in the configuration.
     * @param mirror the mirror
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator mirror(MirrorCreator mirror) {
        this.mirror = mirror;
        return this;
    }

    /**
     * Sets the sources in the configuration.
     * @param sourceCreators the sources
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator sourceCreators(SourceCreator... sourceCreators) {
        return sourceCreators(Arrays.asList(sourceCreators));
    }

    /**
     * Sets the sources in the configuration
     * @param sourceCreators the sources
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator sourceCreators(Collection<SourceCreator> sourceCreators) {
        this.sourceCreators.clear();
        for (SourceCreator c : sourceCreators) {
            if (!this.sourceCreators.contains(c)) {
                this.sourceCreators.add(c);
            }
        }
        return this;
    }

    /**
     * The limit marker TTL duration. Server accepts 1 second or more.
     * @param limitMarkerTtl the TTL duration
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator limitMarkerTtl(Duration limitMarkerTtl) {
        this.limitMarkerTtl = validateDurationNotRequiredGtOrEqSeconds(1, limitMarkerTtl, null, "Limit Marker Ttl");
        return this;
    }

    /**
     * The limit marker TTL duration in milliseconds. Server accepts 1 second or more.
     * @param limitMarkerTtlMillis the TTL duration
     * @return this instance for chaining
     */
    public KeyValueConfigurationCreator limitMarkerTtl(long limitMarkerTtlMillis) {
        if (limitMarkerTtlMillis <= 0) {
            this.limitMarkerTtl = null;
        }
        else {
            this.limitMarkerTtl = validateDurationGtOrEqSeconds(1, limitMarkerTtlMillis, "Limit Marker Ttl");
        }
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------------------------------------------

    /** @return the bucket name */
    public @Nullable String getBucketName() { return bucketName; }

    /** @return the description */
    public @Nullable String getDescription() { return streamCreator.getDescription(); }

    /** @return the maximum number of bytes */
    public long getMaxBucketSize() { return streamCreator.getMaxBytes(); }

    /** @return the maximum age */
    public Duration getTtl() { return ttl; }

    /** @return the storage type */
    public StorageType getStorageType() { return streamCreator.getStorageType(); }

    /** @return the number of replicas */
    public int getReplicas() { return streamCreator.getReplicas(); }

    /** @return the placement directive */
    public @Nullable PlacementCreator getPlacement() { return streamCreator.getPlacementCreator(); }

    /** @return true if compression is enabled */
    public boolean isCompressed() { return streamCreator.getCompressionOption() == CompressionOption.S2; }

    /** @return the metadata map */
    public Map<String, String> getMetadata() { return streamCreator.getMetadata(); }

    /** @return the maximum number of history for any one key */
    public long getMaxHistoryPerKey() { return streamCreator.getMaxMessagesPerSubject(); }

    /** @return the maximum size for an individual value */
    public int getMaxValueSize() { return streamCreator.getMaxMessageSize(); }

    /** @return the republish configuration */
    public @Nullable RepublishCreator getRepublish() { return streamCreator.getRepublishCreator(); }

    /** @return the mirror */
    public @Nullable MirrorCreator getMirror() { return mirror; }

    /** @return the sources */
    public List<SourceCreator> getSourceCreators() { return sourceCreators; }

    /** @return the limit marker ttl */
    public @Nullable Duration getLimitMarkerTtl() { return limitMarkerTtl; }

    /** @return the backing StreamCreator */
    public StreamCreator getStreamCreatorCopy() { return new StreamCreator(streamCreator); }

    @Override
    public String toString() {
        return "KeyValueConfigurationCreator " + streamCreator.toJson();
    }
}
