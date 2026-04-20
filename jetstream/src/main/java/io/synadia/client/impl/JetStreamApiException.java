package io.synadia.client.impl;

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
