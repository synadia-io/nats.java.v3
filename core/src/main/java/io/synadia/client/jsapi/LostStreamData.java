package io.synadia.client.jsapi;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.nats.json.LazyJsonValueUtils.readLongListOrEmpty;
import static io.synadia.client.support.ApiConstants.BYTES;
import static io.synadia.client.support.ApiConstants.MSGS;

/**
 * Information about lost stream data
 */
@NullMarked
public class LostStreamData {
    private final LazyJsonValue ljv;

    @Nullable
    static LostStreamData optionalInstance(@Nullable LazyJsonValue v) {
        return v == null ? null : new LostStreamData(v);
    }

    LostStreamData(LazyJsonValue v) {
        this.ljv = v;
    }

    /**
     * Get the lost message ids. May be empty
     * @return the list of message ids
     */
    public List<Long> getMessages() {
        return readLongListOrEmpty(ljv, MSGS);
    }

    /**
     * Get the number of bytes that were lost
     * @return the number of lost bytes
     */
    @Nullable
    public Long getBytes() {
        return readLong(ljv, BYTES);
    }

    @Override
    public String toString() {
        return "LostStreamData " + ljv.toJson();
    }
}
