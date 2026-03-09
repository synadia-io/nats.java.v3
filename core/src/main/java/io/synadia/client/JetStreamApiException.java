package io.synadia.client;

import io.synadia.client.api.ApiResponse;
import io.synadia.client.api.Error;
import org.jspecify.annotations.NonNull;

/**
 * JetStreamApiException is used to indicate that the server returned an error while make a request
 * related to JetStream.
 */
public class JetStreamApiException extends Exception {
    /**
     * The error that this exception represents if there is one
     */
    private final Error error;

    /**
     * @deprecated Prefer to construct with JetStreamApiException(@NonNull Error)
     * Construct an exception with the response from the server.
     * @param apiResponse the response from the server.
     */
    @Deprecated
    public JetStreamApiException(ApiResponse<?> apiResponse) {
        // deprecated because of getErrorObject() is marked as @Nullable
        //noinspection DataFlowIssue
        this(apiResponse.getErrorObject());
    }

    /**
     * Construct an exception with an Error
     * @param error the error
     */
    public JetStreamApiException(@NonNull Error error) {
        super(error.toString());
        this.error = error;
    }

    /**
     * Get the error code from the response
     * @return the code
     */
    public int getErrorCode() {
        return error.getCode();
    }

    /**
     * Get the error code from the response
     * @return the code
     */
    public int getApiErrorCode() {
        return error.getApiErrorCode();
    }

    /**
     * Get the description from the response
     * @return the description
     */
    public String getErrorDescription() {
        return error.getDescription();
    }
}
