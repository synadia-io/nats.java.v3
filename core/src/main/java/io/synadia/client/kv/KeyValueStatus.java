package io.synadia.client.kv;

import io.nats.json.MapBuilder;
import io.synadia.client.jsapi.Placement;
import io.synadia.client.jsapi.Republish;
import io.synadia.client.jsapi.StorageType;
import io.synadia.client.jsapi.StreamInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * The KeyValueStatus class contains information about a Key Value Bucket.
 */
@NullMarked
public class KeyValueStatus {

    private final StreamInfo streamInfo;
    private final KeyValueConfiguration config;

    /**
     * Construct an instance from the underlying stream info
     * @param si the stream info
     */
    public KeyValueStatus(StreamInfo si) {
        streamInfo = si;
        config = new KeyValueConfiguration(streamInfo.getConfiguration());
    }

    /**
     * Get the name of the bucket
     * @return the name
     */
    public String getBucketName() {
        return config.getBucketName();
    }

    /**
     * Gets the description of this bucket.
     * @return the description of the bucket.
     */
    @Nullable
    public String getDescription() {
        return config.getDescription();
    }

    /**
     * Gets the info for the stream which backs the bucket. Valid for BackingStore "JetStream"
     * @return the stream info
     */
    public StreamInfo getBackingStreamInfo() {
        return streamInfo;
    }

    /**
     * Gets the configuration object directly
     * @return the configuration.
     */
    public KeyValueConfiguration getConfiguration() {
        return config;
    }

    /**
     * Get the number of total entries in the bucket, including historical entries
     * @return the count of entries
     */
    public long getEntryCount() {
        return streamInfo.getStreamState().getMessageCount();
    }

    /**
     * Get the size of the bucket in bytes
     * @return the number of bytes
     */
    public long getByteCount() {
        return streamInfo.getStreamState().getByteCount();
    }

    /**
     * Gets the maximum number of history for any one key. Includes the current value.
     * @return the maximum number of values for any one key.
     */
    public long getMaxHistoryPerKey() {
        return config.getMaxHistoryPerKey();
    }

    /**
     * Gets the maximum number of bytes for this bucket.
     * @return the maximum number of bytes for this bucket.
     */
    public long getMaxBucketSize() {
        return config.getMaxBucketSize();
    }

    /**
     * Gets the maximum size for an individual value in the bucket.
     * @return the maximum size a value.
     */
    public int getMaxValueSize() {
        return config.getMaxValueSize();
    }

    /**
     * Gets the maximum age for a value in this bucket.
     * @return the maximum age.
     */
    @Nullable
    public Duration getTtl() {
        return config.getTtl();
    }

    /**
     * Gets the storage type for this bucket.
     * @return the storage type for this stream.
     */
    public StorageType getStorageType() {
        return config.getStorageType();
    }

    /**
     * Gets the number of replicas for this bucket.
     * @return the number of replicas
     */
    public int getReplicas() {
        return config.getReplicas();
    }

    /**
     * Gets the placement directive for the store.
     * @return the placement
     */
    @Nullable
    public Placement getPlacement() {
        return config.getPlacement();
    }

    /**
     * Gets the republish configuration
     * @return the republish object
     */
    @Nullable
    public Republish getRepublish() {
        return config.getRepublish();
    }

    /**
     * Gets the state of compression
     * @return true if compression is used
     */
    public boolean isCompressed() {
        return config.isCompressed();
    }

    /**
     * Get the metadata for the store
     * @return the metadata map. Might be null.
     */
    @Nullable
    public Map<String, String> getMetadata() {
        return config.getMetadata();
    }

    /**
     * Get the Limit Marker TTL duration or null if configured.
     * @return the duration.
     */
    @Nullable
    public Duration getLimitMarkerTtl() {
        return streamInfo.getConfiguration().getSubjectDeleteMarkerTtl();
    }

    /**
     * Gets the name of the type of backing store, currently only "JetStream"
     * @return the name of the store, currently only "JetStream"
     */
    public String getBackingStore() {
        return "JetStream";
    }

    @Override
    public String toString() {
        return "KeyValueStatus " + new MapBuilder()
            .put("bucketName", getBucketName())
            .put("description", getDescription())
            .put("entryCount", getEntryCount())
            .put("byteCount", getByteCount())
            .put("maxHistoryPerKey", getMaxHistoryPerKey())
            .put("maxBucketSize", getMaxBucketSize())
            .put("maxValueSize", getMaxValueSize())
            .put("ttl", getTtl())
            .put("storageType", getStorageType())
            .put("replicas", getReplicas())
            .put("isCompressed", isCompressed())
            .put("backingStore", getBackingStore())
            .toJson();
    }
}
