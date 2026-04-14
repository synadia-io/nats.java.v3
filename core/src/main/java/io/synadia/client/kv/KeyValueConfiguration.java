package io.synadia.client.kv;

import io.synadia.client.jsapi.*;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.synadia.client.kv.KeyValueUtils.extractBucketName;

/**
 * The KeyValueConfiguration class contains the configuration for a Key Value bucket.
 * Returned from the server.
 */
@NullMarked
public class KeyValueConfiguration {
    private final StreamConfiguration sc;
    private final String bucketName;

    KeyValueConfiguration(StreamConfiguration sc) {
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

    /** @return the maximum number of history for any one key */
    public long getMaxHistoryPerKey() { return sc.getMaxMessagesPerSubject(); }

    /** @return the maximum size for an individual value */
    public int getMaxValueSize() { return sc.getMaxMessageSize(); }

    /** @return the republish configuration */
    public @Nullable Republish getRepublish() { return sc.getRepublish(); }

    /** @return the mirror */
    public @Nullable Mirror getMirror() { return sc.getMirror(); }

    /** @return the sources */
    public List<Source> getSources() { return sc.getSources(); }

    /** @return the limit marker ttl */
    public @Nullable Duration getLimitMarkerTtl() { return sc.getSubjectDeleteMarkerTtl(); }

    /** @return the backing StreamConfiguration */
    public StreamConfiguration getBackingConfig() { return sc; }

    @Override
    public String toString() {
        return "KeyValueConfiguration " + sc.toJson();
    }
}
