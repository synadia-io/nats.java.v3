package io.synadia.client.kv;

import io.synadia.client.api.*;
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

    /**
     * The bucket name. The backing stream is this name with a {@code KV_} prefix.
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
     * How long an entry is kept before the server removes it, held as the stream's max age.
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
     * How many revisions of a single key are retained, held as the stream's max messages per subject. A value of 1 keeps only the current value.
     * @return the maximum number of history for any one key
     */
    public long getMaxHistoryPerKey() { return sc.getMaxMessagesPerSubject(); }

    /**
     * Largest value a single key may hold, in bytes, held as the stream's max message size.
     * @return the maximum size for an individual value
     */
    public int getMaxValueSize() { return sc.getMaxMessageSize(); }

    /**
     * Republish settings, which echo writes onto a second subject.
     * @return the republish configuration
     */
    public @Nullable Republish getRepublish() { return sc.getRepublish(); }

    /**
     * The bucket this one mirrors, or null when it is not a mirror.
     * @return the mirror
     */
    public @Nullable Mirror getMirror() { return sc.getMirror(); }

    /**
     * The buckets this one sources entries from.
     * @return the sources
     */
    public List<Source> getSources() { return sc.getSources(); }

    /**
     * How long a delete marker is kept after a limit removes an entry, or null when markers are not used.
     * @return the limit marker ttl
     */
    public @Nullable Duration getLimitMarkerTtl() { return sc.getSubjectDeleteMarkerTtl(); }

    /**
     * The stream configuration this bucket is built on, for settings the KV API does not surface directly.
     * @return the backing StreamConfiguration
     */
    public StreamConfiguration getBackingConfig() { return sc; }

    @Override
    public String toString() {
        return "KeyValueConfiguration " + sc.toJson();
    }
}
