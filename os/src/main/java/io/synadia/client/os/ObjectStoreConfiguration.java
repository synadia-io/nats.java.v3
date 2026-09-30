package io.synadia.client.os;

import io.synadia.client.api.CompressionOption;
import io.synadia.client.api.Placement;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamConfiguration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

import static io.synadia.client.os.ObjectStoreUtil.extractBucketName;

/**
 * The ObjectStoreConfiguration class contains information about an object store.
 * Returned from the server.
 */
@NullMarked
public class ObjectStoreConfiguration {
    private final StreamConfiguration sc;
    private final String bucketName;

    ObjectStoreConfiguration(StreamConfiguration sc) {
        this.sc = sc;
        this.bucketName = extractBucketName(sc.getName());
    }

    // ----------------------------------------------------------------------------------------------------
    // Getters
    // ----------------------------------------------------------------------------------------------------

    /**
     * The bucket name. The backing stream is this name with an {@code OBJ_} prefix.
     * @return the bucket name
     */
    public String getBucketName() { return bucketName; }

    /**
     * Free-form description carried on the backing stream.
     * @return the description
     */
    public @Nullable String getDescription() { return sc.getDescription(); }

    /**
     * Maximum total size of the bucket in bytes, held as the stream's max bytes.
     * @return the maximum number of bytes
     */
    public long getMaxBucketSize() { return sc.getMaxBytes(); }

    /**
     * How long an object is kept before the server removes it, held as the stream's max age.
     * @return the maximum age
     */
    public Duration getTtl() { return sc.getMaxAge(); }

    /**
     * Whether the bucket is stored on file or in memory.
     * @return the storage type
     */
    public StorageType getStorageType() { return sc.getStorageType(); }

    /**
     * How many replicas of the backing stream the cluster keeps.
     * @return the number of replicas
     */
    public int getReplicas() { return sc.getReplicas(); }

    /**
     * Cluster placement constraints for the backing stream.
     * @return the placement directive
     */
    public @Nullable Placement getPlacement() { return sc.getPlacement(); }

    /**
     * Whether the backing stream uses S2 compression.
     * @return true if compression is enabled
     */
    public boolean isCompressed() { return sc.getCompressionOption() == CompressionOption.S2; }

    /**
     * User metadata carried on the backing stream.
     * @return the metadata map
     */
    public Map<String, String> getMetadata() { return sc.getMetadata(); }

    /**
     * Whether the bucket is sealed, meaning it is permanently read-only.
     * @return true if the store is sealed
     */
    public boolean isSealed() { return sc.getSealed(); }

    /**
     * The stream configuration this bucket is built on, for settings the Object Store API does not surface directly.
     * @return the backing StreamConfiguration
     */
    public StreamConfiguration getBackingConfig() { return sc; }

    @Override
    public String toString() {
        return "ObjectStoreConfiguration " + sc.toJson();
    }
}
