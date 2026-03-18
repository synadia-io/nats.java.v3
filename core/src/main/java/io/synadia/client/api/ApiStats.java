package io.synadia.client.api;

import io.synadia.client.support.JsonValue;
import io.synadia.client.support.JsonValueUtils;

import static io.synadia.client.support.ApiConstants.*;

/**
 * Represents the JetStream Account Api Stats
 */
public class ApiStats {

    private final int level;
    private final long total;
    private final long errors;
    private final long inFlight;

    ApiStats(JsonValue vApiStats) {
        this.level = JsonValueUtils.readInteger(vApiStats, LEVEL, 0);
        this.total = JsonValueUtils.readLong(vApiStats, TOTAL, 0);
        this.errors = JsonValueUtils.readLong(vApiStats, ERRORS, 0);
        this.inFlight = JsonValueUtils.readLong(vApiStats, INFLIGHT, 0);
    }

    /**
     * The JetStream API Level
     * @return the level
     */
    public int getLevel() {
        return level;
    }

    /**
     * Total number of API requests received for this account
     * @return the total requests
     */
    public long getTotalApiRequests() {
        return total;
    }

    /**
     * API requests that resulted in an error response
     * @return the error count
     */
    public long getErrorCount() {
        return errors;
    }

    /**
     * The number of inflight API requests waiting to be processed
     * @return inflight API requests
     */
    public long getInFlight() {
        return inFlight;
    }
}
