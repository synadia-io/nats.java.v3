package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.nats.json.LazyJsonValueUtils.readMapObjectOrEmpty;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Represents the JetStream Account Tier
 */
@NullMarked
public class AccountTier {
    private final LazyJsonValue ljv;
    private AccountLimits _limits;

    AccountTier(LazyJsonValue v) {
        this.ljv = v;
    }

    /**
     * Memory Storage being used for Stream Message storage in this tier.
     * @return the memory storage in bytes
     */
    public long getMemoryBytes() {
        return readLong(ljv, MEMORY, 0);
    }

    /**
     * File Storage being used for Stream Message storage in this tier.
     * @return the storage in bytes
     */
    public long getStorageBytes() {
        return readLong(ljv, STORAGE, 0);
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
        return readLong(ljv, STREAMS, 0);
    }

    /**
     * Number of active consumers in this tier.
     * @return the number of consumers
     */
    public long getConsumers() {
        return readLong(ljv, CONSUMERS, 0);
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
