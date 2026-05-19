package io.synadia.client.api;

import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.synadia.client.utils.ApiConstants.SUCCESS;

/**
 * A response indicating a successful api call
 */
@NullMarked
public class SuccessApiResponse extends ApiResponse<SuccessApiResponse> {
    boolean success;

    /**
     * Construct a SuccessApiResponse from a message
     * @param msg the message
     */
    public SuccessApiResponse(Message msg) {
        super(msg);
        // not all success responses work the same
        // some just error, some actually return the flag.
        Boolean b = readBoolean(ljv, SUCCESS);
        success = b == null ? !hasError() : b;
    }

    /**
     * Get the success state
     * @return true if the call was successful
     */
    public boolean getSuccess() {
        return success;
    }
}
