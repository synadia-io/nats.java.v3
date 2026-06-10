package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.nats.json.LazyJsonValueUtils.readMapObjectOrEmpty;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readLongOrMinusOne;

/**
 * Represents the JetStream Account Tier
 */
@NullMarked
public class AccountTier extends LazyApiObject {
    private @Nullable AccountLimits _limits;

    AccountTier(LazyJsonValue v) {
        super(v);
    }

    /**
     * Memory Storage being used for Stream Message storage in this tier.
     * @return the memory storage in bytes
     */
    public long getMemoryBytes() {
        return readLongOrMinusOne(ljv, MEMORY);
    }

    /**
     * File Storage being used for Stream Message storage in this tier.
     * @return the storage in bytes
     */
    public long getStorageBytes() {
        return readLongOrMinusOne(ljv, STORAGE);
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server
     * @return the memory usage in bytes
     */
    public long getReservedMemoryBytes() {
        return readLong(ljv, RESERVED_MEMORY, 0);
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server
     * @return the disk usage in bytes
     */
    public long getReservedStorageBytes() {
        return readLong(ljv, RESERVED_STORAGE, 0);
    }

    /**
     * Number of active streams in this tier.
     * @return the number of streams
     */
    public long getStreams() {
        return readLongOrMinusOne(ljv, STREAMS);
    }

    /**
     * Number of active consumers in this tier.
     * @return the number of consumers
     */
    public long getConsumers() {
        return readLongOrMinusOne(ljv, CONSUMERS);
    }

    /**
     * The limits of this tier.
     * @return the limits object
     */
    public AccountLimits getLimits() {
        if (_limits == null) {
            _limits = new AccountLimits(readMapObjectOrEmpty(ljv, LIMITS));
        }
        return _limits;
    }

    @Override
    public String toString() {
        return "AccountTier " + ljv.toJson();
    }
}
