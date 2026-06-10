package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * The JetStream Account Statistics
 */
@NullMarked
public class AccountStatistics extends ApiResponse<AccountStatistics> {

    /**
     * Construct an AccountStatistics
     * @param msg the api response message
     */
    public AccountStatistics(Message msg) {
        super(msg);
    }

    private @Nullable AccountTier _rollupTier;
    private @Nullable ApiStats _apiStats;
    private @Nullable Map<String, AccountTier> _tiers;

    private AccountTier getRollupTier() {
        if (_rollupTier == null) {
            _rollupTier = new AccountTier(ljv);
        }
        return _rollupTier;
    }

    /**
     * Gets the amount of memory storage used by the JetStream deployment.
     * If the account has tiers, this will represent a rollup.
     * <p>The server value is an unsigned 64-bit number.
     * @return bytes
     */
    public long getMemory() {
        return getRollupTier().getMemoryBytes();
    }

    /**
     * Gets the amount of memory storage used by the JetStream deployment, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getMemory()}.
     * @return bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getMemoryAsBigInteger() {
        return getRollupTier().getMemoryBytesAsBigInteger();
    }

    /**
     * Gets the amount of file storage used by the JetStream deployment.
     * If the account has tiers, this will represent a rollup.
     * <p>The server value is an unsigned 64-bit number.
     * @return bytes
     */
    public long getStorage() {
        return getRollupTier().getStorageBytes();
    }

    /**
     * Gets the amount of file storage used by the JetStream deployment, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getStorage()}.
     * @return bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getStorageAsBigInteger() {
        return getRollupTier().getStorageBytesAsBigInteger();
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server.
     * <p>The server value is an unsigned 64-bit number.
     * @return the memory usage in bytes
     */
    public long getReservedMemory() {
        return getRollupTier().getReservedMemoryBytes();
    }

    /**
     * Bytes that is reserved for memory usage by this account on the server, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getReservedMemory()}.
     * @return the memory usage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getReservedMemoryAsBigInteger() {
        return getRollupTier().getReservedMemoryBytesAsBigInteger();
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server.
     * <p>The server value is an unsigned 64-bit number.
     * @return the disk usage in bytes
     */
    public long getReservedStorage() {
        return getRollupTier().getReservedStorageBytes();
    }

    /**
     * Bytes that is reserved for disk usage by this account on the server, as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getReservedStorage()}.
     * @return the disk usage in bytes, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getReservedStorageAsBigInteger() {
        return getRollupTier().getReservedStorageBytesAsBigInteger();
    }

    /**
     * Gets the number of streams used by the JetStream deployment.
     * If the account has tiers, this will represent a rollup.
     * @return stream maximum count
     */
    public long getStreams() {
        return getRollupTier().getStreams();
    }

    /**
     * Gets the number of consumers used by the JetStream deployment.
     * If the account has tiers, this will represent a rollup.
     * @return consumer maximum count
     */
    public long getConsumers() {
        return getRollupTier().getConsumers();
    }

    /**
     * Gets the Account Limits object. If the account has tiers,
     * the object will be present but all values will be zero.
     * See the Account Limits for the specific tier.
     * @return the AccountLimits object
     */
    public AccountLimits getLimits() {
        return getRollupTier().getLimits();
    }

    /**
     * Gets the account domain. May be null
     * @return the domain
     */
    @Nullable
    public String getDomain() {
        return readString(ljv, DOMAIN);
    }

    /**
     * Gets the account api stats
     * @return the ApiStats object
     */
    public ApiStats getApiStats() {
        if (_apiStats == null) {
            _apiStats = new ApiStats(readMapObjectOrEmpty(ljv, API));
        }
        return _apiStats;
    }

    /**
     * Gets the map of the Account Tiers by tier name. May be empty, but never null.
     * @return the map
     */
    public Map<String, AccountTier> getTiers() {
        if (_tiers == null) {
            _tiers = new HashMap<>();
            LazyJsonValue vTiers = readValue(ljv, TIERS);
            if (vTiers != null && vTiers.getMap() != null) {
                for (Map.Entry<String, LazyJsonValue> entry : vTiers.getMap().entrySet()) {
                    _tiers.put(entry.getKey(), new AccountTier(entry.getValue()));
                }
            }
        }
        return _tiers;
    }
}
