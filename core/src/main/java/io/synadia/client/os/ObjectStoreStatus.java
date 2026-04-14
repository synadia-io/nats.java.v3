package io.synadia.client.os;

import io.nats.json.MapBuilder;
import io.synadia.client.jsapi.Placement;
import io.synadia.client.jsapi.StorageType;
import io.synadia.client.jsapi.StreamInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

/**
 * The ObjectStoreStatus class contains information about an object store.
 */
@NullMarked
public class ObjectStoreStatus {

    private final StreamInfo streamInfo;
    private final ObjectStoreConfiguration config;

    /**
     * Create an object store from the StreamInfo for the underlying stream
     * @param si the stream info
     */
    public ObjectStoreStatus(StreamInfo si) {
        streamInfo = si;
        config = new ObjectStoreConfiguration(streamInfo.getConfiguration());
    }

    /**
     * Get the name of the object store
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
    public ObjectStoreConfiguration getConfiguration() {
        return config;
    }

    /**
     * Get the combined size of all data in the bucket including metadata, in bytes
     * @return the size
     */
    public long getSize() {
        return streamInfo.getStreamState().getByteCount();
    }

    /**
     * Gets the maximum number of bytes for this bucket.
     * @return the maximum number of bytes for this bucket.
     */
    public long getMaxBucketSize() {
        return config.getMaxBucketSize();
    }

    /**
     * If true, indicates the store is sealed and cannot be modified in any way
     * @return the sealed setting
     */
    public boolean isSealed() {
        return config.isSealed();
    }

    /**
     * Gets the maximum age for a value in this store.
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
     * Gets the number of replicas for this store.
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
     * Gets the name of the type of backing store, currently only "JetStream"
     * @return the name of the store, currently only "JetStream"
     */
    public String getBackingStore() {
        return "JetStream";
    }

    @Override
    public String toString() {
        return "ObjectStoreStatus " + new MapBuilder()
            .put("bucketName", getBucketName())
            .put("description", getDescription())
            .put("size", getSize())
            .put("maxBucketSize", getMaxBucketSize())
            .put("ttl", getTtl())
            .put("storageType", getStorageType())
            .put("replicas", getReplicas())
            .put("isSealed", isSealed())
            .put("isCompressed", isCompressed())
            .put("backingStore", getBackingStore())
            .toJson();
    }
}
