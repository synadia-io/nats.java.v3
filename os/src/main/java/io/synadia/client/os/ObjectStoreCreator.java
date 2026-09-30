package io.synadia.client.os;

import io.synadia.client.api.CompressionOption;
import io.synadia.client.api.PlacementCreator;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamCreator;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

import static io.synadia.client.os.ObjectStoreUtil.toStreamName;
import static io.synadia.client.utils.JsValidator.validateBucketName;
import static io.synadia.client.utils.JsValidator.validateMaxBucketBytes;

/**
 * ObjectStoreCreator is used to create an Object Store bucket.
 */
public class ObjectStoreCreator {

    private final String bucketName;
    private final StreamCreator streamCreator;
    private Duration ttl = Duration.ZERO;

    /**
     * Constructor accepting the object store bucket name.
     * @param bucketName name of the store.
     */
    public ObjectStoreCreator(String bucketName) {
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
    public ObjectStoreCreator description(String description) {
        streamCreator.description(description);
        return this;
    }

    /**
     * Sets the maximum number of bytes in the configuration.
     * @param maxBucketSize the maximum number of bytes
     * @return this instance for chaining
     */
    public ObjectStoreCreator maxBucketSize(long maxBucketSize) {
        streamCreator.maxBytes(validateMaxBucketBytes(maxBucketSize));
        return this;
    }

    /**
     * Sets the maximum age for an object in this configuration.
     * @param ttl the maximum age
     * @return this instance for chaining
     */
    public ObjectStoreCreator ttl(Duration ttl) {
        this.ttl = ttl == null ? Duration.ZERO : ttl;
        streamCreator.maxAge(this.ttl);
        return this;
    }

    /**
     * Sets the maximum age for an object in this configuration.
     * @param ttlMillis the maximum age in milliseconds
     * @return this instance for chaining
     */
    public ObjectStoreCreator ttl(Long ttlMillis) {
        this.ttl = ttlMillis == null || ttlMillis < 0 ? Duration.ZERO : Duration.ofMillis(ttlMillis);
        streamCreator.maxAge(this.ttl);
        return this;
    }

    /**
     * Sets the storage type in the configuration.
     * @param storageType the storage type
     * @return this instance for chaining
     */
    public ObjectStoreCreator storageType(StorageType storageType) {
        streamCreator.storageType(storageType);
        return this;
    }

    /**
     * Sets the number of replicas.
     * @param replicas the number of replicas
     * @return this instance for chaining
     */
    public ObjectStoreCreator replicas(int replicas) {
        streamCreator.replicas(replicas);
        return this;
    }

    /**
     * Sets the placement directive object
     * @param placement the placement directive object
     * @return this instance for chaining
     */
    public ObjectStoreCreator placement(PlacementCreator placement) {
        streamCreator.placementCreator(placement);
        return this;
    }

    /**
     * Sets whether to use compression.
     * @param compression whether to use compression
     * @return this instance for chaining
     */
    public ObjectStoreCreator compression(boolean compression) {
        streamCreator.compressionOption(compression ? CompressionOption.S2 : CompressionOption.None);
        return this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining
     */
    public ObjectStoreCreator metadata(Map<String, String> metadata) {
        streamCreator.metadata(metadata);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------------------------------------------

    /**
     * The bucket name. The backing stream will be this name with an {@code OBJ_} prefix.
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
     * How long an object is kept before the server removes it, applied as the stream's max age.
     * @return the maximum age
     */
    public Duration getTtl() { return ttl; }

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
     * A copy of the stream creator being built up, so callers cannot mutate this creator's state through it.
     * @return the backing StreamCreator
     */
    public StreamCreator getStreamCreatorCopy() { return new StreamCreator(streamCreator); }

    @Override
    public String toString() {
        return "ObjectStoreCreator " + streamCreator.toJson();
    }
}
