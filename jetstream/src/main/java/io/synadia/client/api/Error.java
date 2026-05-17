package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import io.synadia.client.testutils.Status;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.nats.json.LazyJsonValueUtils.readInteger;
import static io.nats.json.LazyJsonValueUtils.readString;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Error returned from an api request.
 */
@NullMarked
public class Error {

    /**
     * represents an error code that was not set / provided
     */
    public static final int NOT_SET = -1;

    private final int code;
    private final int apiErrorCode;
    private final String description;

    @Nullable
    public static Error optionalInstance(@Nullable LazyJsonValue vError) {
        return vError == null ? null : new Error(vError);
    }

    Error(LazyJsonValue ljv) {
        this.code = readInteger(ljv, CODE, NOT_SET);
        this.apiErrorCode = readInteger(ljv, ERR_CODE, NOT_SET);
        String d = readString(ljv, DESCRIPTION);
        this.description = d == null ? "Unknown JetStream Error" : d;
    }

    Error(int code, String description) {
        this(code, NOT_SET, description);
    }

    Error(int code, int apiErrorCode, String description) {
        this.code = code;
        this.apiErrorCode = apiErrorCode;
        this.description = description;
    }

    /**
     * The request error code from the server
     * @return the code
     */
    public int getCode() {
        return code;
    }

    /**
     * The api error code from the server
     * @return the code
     */
    public int getApiErrorCode() {
        return apiErrorCode;
    }

    /**
     * Get the error description
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        if (apiErrorCode == NOT_SET) {
            if (code == NOT_SET) {
                return description;
            }
            return description + " (" + code + ")";
        }
        if (code == NOT_SET) {
            return description;
        }
        return description + " [" + apiErrorCode + "]";
    }

    /**
     * Convert a status to an Error object. Only some status are supported, otherwise a generic error is returned
     * @param status the status
     * @return the error
     */
    public static Error convert(Status status) {
        return switch (status.getCode()) {
            case 404 -> JsNoMessageFoundErr;
            case 408 -> JsBadRequestErr;
            default -> new Error(status.getCode(), NOT_SET, status.getMessage());
        };
    }

    /**
     * Error representing 400 / 10003 / "bad request"
     */
    public static final Error JsBadRequestErr = new Error(400, 10003, "bad request");

    /**
     * Error representing 404 / 10037 / "no message found"
     */
    public static final Error JsNoMessageFoundErr = new Error(404, 10037, "no message found");

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof Error)) return false;
        Error that = (Error) o;
        return code == that.code
            && apiErrorCode == that.apiErrorCode
            && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        int result = code;
        result = 31 * result + apiErrorCode;
        result = 31 * result + description.hashCode();
        return result;
    }
}
