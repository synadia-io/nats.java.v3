package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;

import static io.nats.json.LazyJsonValueUtils.readMapObjectOrEmpty;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.*;

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
     * <p>The server value is an unsigned 64-bit number.
     * @return the memory storage in bytes
     */
    public long getMemoryBytes() {
        return readUnsignedLongOrZero(ljv, MEMORY);
    }

    /**
     * Memory Storage being used for Stream Message storage in this tier, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getMemoryBytes()}.
     * @return the memory storage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getMemoryBytesAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, MEMORY);
    }

    /**
     * File Storage being used for Stream Message storage in this tier.
     * <p>The server value is an unsigned 64-bit number.
     * @return the storage in bytes
     */
    public long getStorageBytes() {
        return readUnsignedLongOrZero(ljv, STORAGE);
    }

    /**
     * File Storage being used for Stream Message storage in this tier, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getStorageBytes()}.
     * @return the storage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getStorageBytesAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, STORAGE);
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server.
     * <p>The server value is an unsigned 64-bit number.
     * @return the memory usage in bytes
     */
    public long getReservedMemoryBytes() {
        return readUnsignedLongOrZero(ljv, RESERVED_MEMORY);
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getReservedMemoryBytes()}.
     * @return the memory usage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getReservedMemoryBytesAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, RESERVED_MEMORY);
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server.
     * <p>The server value is an unsigned 64-bit number.
     * @return the disk usage in bytes
     */
    public long getReservedStorageBytes() {
        return readUnsignedLongOrZero(ljv, RESERVED_STORAGE);
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getReservedStorageBytes()}.
     * @return the disk usage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getReservedStorageBytesAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, RESERVED_STORAGE);
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
