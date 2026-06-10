package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import java.math.BigInteger;

import static io.nats.json.LazyJsonValueUtils.readInteger;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readUnsignedBigIntegerOrZero;
import static io.synadia.client.utils.ApiUtils.readUnsignedLongOrZero;

/**
 * Represents the JetStream Account Api Stats
 */
@NullMarked
public class ApiStats extends LazyApiObject {

    ApiStats(LazyJsonValue v) {
        super(v);
    }

    /**
     * The JetStream API Level
     * @return the level
     */
    public int getLevel() {
        return readInteger(ljv, LEVEL, 0);
    }

    /**
     * Total number of API requests received for this account.
     * <p>The server value is an unsigned 64-bit number.
     * @return the total requests
     */
    public long getTotal() {
        return readUnsignedLongOrZero(ljv, TOTAL);
    }

    /**
     * Total number of API requests received for this account as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getTotal()}.
     * @return the total requests, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getTotalAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, TOTAL);
    }

    /**
     * API requests that resulted in an error response.
     * <p>The server value is an unsigned 64-bit number.
     * @return the error count
     */
    public long getErrors() {
        return readUnsignedLongOrZero(ljv, ERRORS);
    }

    /**
     * API requests that resulted in an error response as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getErrors()}.
     * @return the error count, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getErrorsAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, ERRORS);
    }

    /**
     * The number of inflight API requests waiting to be processed.
     * <p>The server value is an unsigned 64-bit number.
     * @return inflight API requests
     */
    public long getInFlight() {
        return readUnsignedLongOrZero(ljv, INFLIGHT);
    }

    /**
     * The number of inflight API requests waiting to be processed as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getInFlight()}.
     * @return inflight API requests, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getInFlightAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, INFLIGHT);
    }

    @Override
    public String toString() {
        return "ApiStats " + ljv.toJson();
    }
}
