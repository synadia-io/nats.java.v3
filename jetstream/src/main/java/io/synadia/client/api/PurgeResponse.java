package io.synadia.client.api;

import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.nats.json.LazyJsonValueUtils.readLong;
import static io.synadia.client.testutils.ApiConstants.PURGED;
import static io.synadia.client.testutils.ApiConstants.SUCCESS;

/**
 * The response to a request to Purge a stream
 */
@NullMarked
public class PurgeResponse extends ApiResponse<PurgeResponse> {

    private final boolean success;
    private final long purged;

    /**
     * Construct an instance of the PurgeResponse with the message
     * @param msg the message
     */
    public PurgeResponse(Message msg) {
        super(msg);
        success = readBoolean(ljv, SUCCESS, false);
        purged = readLong(ljv, PURGED, 0);
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
