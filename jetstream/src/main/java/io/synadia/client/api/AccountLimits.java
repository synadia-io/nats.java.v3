package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Represents the JetStream Account Limits
 */
@NullMarked
public class AccountLimits extends LazyApiObject {

    AccountLimits(LazyJsonValue v) {
        super(v);
    }

    /**
     * The maximum amount of Memory storage Stream Messages may consume.
     * @return bytes
     */
    public long getMaxMemory() {
        return readLong(ljv, MAX_MEMORY, 0);
    }

    /**
     * The maximum amount of File storage Stream Messages may consume.
     * @return bytes
     */
    public long getMaxStorage() {
        return readLong(ljv, MAX_STORAGE, 0);
    }

    /**
     * The maximum number of Streams an account can create.
     * @return stream maximum count
     */
    public long getMaxStreams() {
        return readLong(ljv, MAX_STREAMS, 0);
    }

    /**
     * The maximum number of Consumers an account can create.
     * @return consumer maximum count
     */
    public long getMaxConsumers() {
        return readLong(ljv, MAX_CONSUMERS, 0);
    }

    /**
     * The maximum number of outstanding ACKs any consumer may configure.
     * @return the configuration count
     */
    public long getMaxAckPending() {
        return readLong(ljv, MAX_ACK_PENDING, 0);
    }

    /**
     * The maximum size any single memory stream may be.
     * @return bytes
     */
    public long getMemoryMaxStreamBytes() {
        return readLong(ljv, MEMORY_MAX_STREAM_BYTES, 0);
    }

    /**
     * The maximum size any single storage based stream may be.
     * @return bytes
     */
    public long getStorageMaxStreamBytes() {
        return readLong(ljv, STORAGE_MAX_STREAM_BYTES, 0);
    }

    /**
     * Indicates if streams created in this account requires the max_bytes property set.
     * @return the flag
     */
    public boolean isMaxBytesRequired() {
        return readBoolean(ljv, MAX_BYTES_REQUIRED, false);
    }

    @Override
    public String toString() {
        return "AccountLimits " + ljv.toJson();
    }
}
