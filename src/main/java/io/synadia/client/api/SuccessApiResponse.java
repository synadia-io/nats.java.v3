package io.synadia.client.api;

import io.synadia.client.Message;

import static io.synadia.client.support.ApiConstants.SUCCESS;
import static io.synadia.client.support.JsonValueUtils.readBoolean;

/**
 * A response indicating a successful api call
 */
public class SuccessApiResponse extends ApiResponse<SuccessApiResponse> {
    boolean success;

    /**
     * Construct a SuccessApiResponse from a message
     * @param msg the message
     */
    public SuccessApiResponse(Message msg) {
        super(msg);
        Boolean b = readBoolean(jv, SUCCESS, null);
        if (b == null) {
            success = !hasError();
        }
        else {
            success = b;
        }
    }

    /**
     * Get the success state
     * @return true if the call was successful
     */
    public boolean getSuccess() {
        return success;
    }
}
