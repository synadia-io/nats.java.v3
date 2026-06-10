package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.utils.ApiConstants.BYTES;
import static io.synadia.client.utils.ApiConstants.MSGS;

/**
 * Information about lost stream data
 */
@NullMarked
public class LostStreamData extends LazyApiObject {

    @Nullable
    static LostStreamData optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new LostStreamData(v);
    }

    LostStreamData(LazyJsonValue v) {
        super(v);
    }

    /**
     * Get the lost message ids. May be empty
     * @return the list of message ids
     */
    public List<Long> getMessages() {
        return readLongListOrEmpty(ljv, MSGS);
    }

    /**
     * Get the number of bytes that were lost.
     * <p>The server value is an unsigned 64-bit number.
     * @return the number of lost bytes, or {@code null} if absent
     */
    @Nullable
    public Long getBytes() {
        return readUnsignedLong(ljv, BYTES);
    }

    /**
     * Get the number of bytes that were lost as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getBytes()}.
     * @return the number of lost bytes, or {@code null} if absent
     */
    @Nullable
    public BigInteger getBytesAsBigInteger() {
        return readUnsignedBigInteger(ljv, BYTES);
    }

    @Override
    public String toString() {
        return "LostStreamData " + ljv.toJson();
    }
}
