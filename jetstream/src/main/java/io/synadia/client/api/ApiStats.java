package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.LazyJsonValueUtils.readInteger;
import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.synadia.client.testutils.ApiConstants.*;

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
     * Total number of API requests received for this account
     * @return the total requests
     */
    public long getTotal() {
        return readLong(ljv, TOTAL, 0);
    }

    /**
     * API requests that resulted in an error response
     * @return the error count
     */
    public long getErrors() {
        return readLong(ljv, ERRORS, 0);
    }

    /**
     * The number of inflight API requests waiting to be processed
     * @return inflight API requests
     */
    public long getInFlight() {
        return readLong(ljv, INFLIGHT, 0);
    }

    @Override
    public String toString() {
        return "ApiStats " + ljv.toJson();
    }
}
