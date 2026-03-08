package io.nats.client.api;

import io.nats.client.support.JsonSerializable;
import io.nats.client.support.JsonValue;
import io.nats.client.support.JsonValueUtils;
import io.nats.client.support.Status;
import org.jspecify.annotations.NonNull;

import static io.nats.client.support.ApiConstants.*;

/**
 * Error returned from an api request.
 */
public class Error implements JsonSerializable {

    /**
     * represents an error code that was not set / provided
     */
    public static final int NOT_SET = -1;

    private final JsonValue jv;

    static Error optionalInstance(JsonValue vError) {
        return vError == null ? null : new Error(vError);
    }

    Error(JsonValue jv) {
        this.jv = jv;
    }

    Error(int code, String desc) {
        this(code, NOT_SET, desc);
    }

    Error(int code, int apiErrorCode, String desc) {
        jv = JsonValueUtils.mapBuilder()
            .put(CODE, code)
            .put(ERR_CODE, apiErrorCode)
            .put(DESCRIPTION, desc)
            .toJsonValue();
    }

    @Override
    @NonNull
    public String toJson() {
        return jv.toJson();
    }

    @Override
    @NonNull
    public JsonValue toJsonValue() {
        return jv;
    }

    /**
     * The request error code from the server
     * @return the code
     */
    public int getCode() {
        return JsonValueUtils.readInteger(jv, CODE, NOT_SET);
    }

    /**
     * The api error code from the server
     * @return the code
     */
    public int getApiErrorCode() {
        return JsonValueUtils.readInteger(jv, ERR_CODE, NOT_SET);
    }

    /**
     * Get the error description
     * @return the description
     */
    @NonNull
    public String getDescription() {
        return JsonValueUtils.readString(jv, DESCRIPTION, "Unknown JetStream Error");
    }

    @Override
    public String toString() {
        int apiErrorCode = getApiErrorCode();
        int code = getCode();
        if (apiErrorCode == NOT_SET) {
            if (code == NOT_SET) {
                return getDescription();
            }
            return getDescription() + " (" + code + ")";
        }
        if (code == NOT_SET) {
            return getDescription();
        }
        return getDescription() + " [" + apiErrorCode + "]";
    }

    /**
     * Convert a status to an Error object. Only some status are supported, otherwise a generic error is returned
     * @param status the status
     * @return the error
     */
    @NonNull
    public static Error convert(Status status) {
        switch (status.getCode()) {
            case 404:
                return JsNoMessageFoundErr;
            case 408:
                return JsBadRequestErr;
        }
        return new Error(status.getCode(), NOT_SET, status.getMessage());
    }

    /**
     * Error representing 400 / 10003 / "bad request"
     */
    @NonNull
    public static final Error JsBadRequestErr = new Error(400, 10003, "bad request");

    /**
     * Error representing 404 / 10037 / "no message found"
     */
    @NonNull
    public static final Error JsNoMessageFoundErr = new Error(404, 10037, "no message found");
}
