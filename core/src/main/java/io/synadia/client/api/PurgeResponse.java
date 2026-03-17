package io.synadia.client.api;

import io.synadia.client.Message;

import static io.synadia.client.support.ApiConstants.PURGED;
import static io.synadia.client.support.ApiConstants.SUCCESS;
import static io.synadia.client.support.JsonValueUtils.readBoolean;
import static io.synadia.client.support.JsonValueUtils.readLong;

/**
 * The response to a request to Purge a stream
 */
public class PurgeResponse extends ApiResponse<PurgeResponse> {

    private final boolean success;
    private final long purged;

    /**
     * Construct an instance of the PurgeResponse with the message
     * @param msg the message
     */
    public PurgeResponse(Message msg) {
        super(msg);
        success = readBoolean(jv, SUCCESS);
        purged = readLong(jv, PURGED, 0);
    }

    /**
     * Returns true if the server was able to purge the stream
     * @return the result flag
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Returns the number of items purged from the stream
     * @return the count
     */
    public long getPurged() {
        return purged;
    }
}
