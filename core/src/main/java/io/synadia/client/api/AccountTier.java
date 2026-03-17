package io.synadia.client.api;

import io.synadia.client.support.JsonValue;
import org.jspecify.annotations.NonNull;

import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.JsonValueUtils.*;

/**
 * Represents the JetStream Account Tier
 */
public class AccountTier {

    private final long memory;
    private final long storage;
    private final long reservedMemory;
    private final long reservedStorage;
    private final int streams;
    private final int consumers;
    private final AccountLimits limits;

    AccountTier(JsonValue vAccountTier) {
        memory = readInteger(vAccountTier, MEMORY, 0);
        storage = readInteger(vAccountTier, STORAGE, 0);
        reservedMemory = readLong(vAccountTier, RESERVED_MEMORY, 0);
        reservedStorage = readLong(vAccountTier, RESERVED_STORAGE, 0);
        streams = readInteger(vAccountTier, STREAMS, 0);
        consumers = readInteger(vAccountTier, CONSUMERS, 0);
        limits = new AccountLimits(readObject(vAccountTier, LIMITS));
    }

    /**
     * Memory Storage being used for Stream Message storage in this tier.
     * @return the memory storage in bytes
     */
    public long getMemoryBytes() {
        return memory;
    }

    /**
     * File Storage being used for Stream Message storage in this tier.
     * @return the storage in bytes
     */
    public long getStorageBytes() {
        return storage;
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server
     * @return the memory usage in bytes
     */
    public long getReservedMemory() {
        return (int)reservedMemory;
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server
     * @return the disk usage in bytes
     */
    public long getReservedStorage() {
        return reservedStorage;
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server
     * @return the memory usage in bytes
     */
    public long getReservedMemoryBytes() {
        return reservedMemory;
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server
     * @return the disk usage in bytes
     */
    public long getReservedStorageBytes() {
        return reservedStorage;
    }

    /**
     * Number of active streams in this tier.
     * @return the number of streams
     */
    public int getStreams() {
        return streams;
    }

    /**
     * Number of active consumers in this tier.
     * @return the number of consumers
     */
    public int getConsumers() {
        return consumers;
    }

    /**
     * The limits of this tier.
     * @return the limits object
     */
    @NonNull
    public AccountLimits getLimits() {
        return limits;
    }
}
