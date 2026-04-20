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

    /** @return the bucket name */
    public String getBucketName() { return bucketName; }

    /** @return the description */
    public @Nullable String getDescription() { return sc.getDescription(); }

    /** @return the maximum number of bytes */
    public long getMaxBucketSize() { return sc.getMaxBytes(); }

    /** @return the maximum age */
    public Duration getTtl() { return sc.getMaxAge(); }

    /** @return the storage type */
    public StorageType getStorageType() { return sc.getStorageType(); }

    /** @return the number of replicas */
    public int getReplicas() { return sc.getReplicas(); }

    /** @return the placement directive */
    public @Nullable Placement getPlacement() { return sc.getPlacement(); }

    /** @return true if compression is enabled */
    public boolean isCompressed() { return sc.getCompressionOption() == CompressionOption.S2; }

    /** @return the metadata map */
    public Map<String, String> getMetadata() { return sc.getMetadata(); }

    /** @return true if the store is sealed */
    public boolean isSealed() { return sc.getSealed(); }

    /** @return the backing StreamConfiguration */
    public StreamConfiguration getBackingConfig() { return sc; }

    @Override
    public String toString() {
        return "ObjectStoreConfiguration " + sc.toJson();
    }
}
