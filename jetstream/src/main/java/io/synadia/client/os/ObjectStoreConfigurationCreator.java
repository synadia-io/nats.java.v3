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
 * ObjectStoreConfigurationCreator is used to create an Object Store bucket.
 */
public class ObjectStoreConfigurationCreator {

    private final String bucketName;
    private final StreamCreator streamCreator;
    private Duration ttl = Duration.ZERO;

    /**
     * Constructor accepting the object store bucket name.
     * @param bucketName name of the store.
     */
    public ObjectStoreConfigurationCreator(String bucketName) {
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
    public ObjectStoreConfigurationCreator description(String description) {
        streamCreator.description(description);
        return this;
    }

    /**
     * Sets the maximum number of bytes in the configuration.
     * @param maxBucketSize the maximum number of bytes
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator maxBucketSize(long maxBucketSize) {
        streamCreator.maxBytes(validateMaxBucketBytes(maxBucketSize));
        return this;
    }

    /**
     * Sets the maximum age for a value in this configuration.
     * @param ttl the maximum age
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator ttl(Duration ttl) {
        this.ttl = ttl == null ? Duration.ZERO : ttl;
        streamCreator.maxAge(this.ttl);
        return this;
    }

    /**
     * Sets the storage type in the configuration.
     * @param storageType the storage type
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator storageType(StorageType storageType) {
        streamCreator.storageType(storageType);
        return this;
    }

    /**
     * Sets the number of replicas.
     * @param replicas the number of replicas
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator replicas(int replicas) {
        streamCreator.replicas(replicas);
        return this;
    }

    /**
     * Sets the placement directive object
     * @param placement the placement directive object
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator placement(PlacementCreator placement) {
        streamCreator.placementCreator(placement);
        return this;
    }

    /**
     * Sets whether to use compression.
     * @param compression whether to use compression
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator compression(boolean compression) {
        streamCreator.compressionOption(compression ? CompressionOption.S2 : CompressionOption.None);
        return this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining
     */
    public ObjectStoreConfigurationCreator metadata(Map<String, String> metadata) {
        streamCreator.metadata(metadata);
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

    /** @return the backing StreamCreator */
    public StreamCreator getStreamCreatorCopy() { return new StreamCreator(streamCreator); }

    @Override
    public String toString() {
        return "ObjectStoreConfigurationCreator " + streamCreator.toJson();
    }
}
