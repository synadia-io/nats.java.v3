package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.JsonParseException;
import io.nats.json.JsonValue;
import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import io.synadia.client.impl.JetStreamApiException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.function.Function;

import static io.nats.json.JsonWriteUtils.toKey;
import static io.nats.json.LazyJsonParser.parse;
import static io.nats.json.LazyJsonParser.parseUnchecked;
import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.ERROR;
import static io.synadia.client.testutils.ApiConstants.TYPE;

/**
 * ApiResponse is the base class for all api responses from the server
 * @param <T> the success response class
 */
@NullMarked
public abstract class ApiResponse<T> {

    /**
     * A constant for a response without a type
     */
    public static final String NO_TYPE = "io.nats.jetstream.api.v1.no_type";

    /**
     * a constant for a response that errors while parsing
     */
    public static final String PARSE_ERROR_ERROR_JSON = "{\"error\":{\"code\":500,\"err_code\":-1,\"description\":\"Error parsing Message\"},\"type\":\"io.nats.client.api.parse_error\"}";

    /**
     * The JSON value made from creating the object from a message or that was used to directly construct the response
     */
    protected final LazyJsonValue ljv;

    private final String type;
    private @Nullable Error error;

    /**
     * construct an ApiResponse from a message
     *
     * @param msg the message
     */
    protected ApiResponse(@Nullable Message msg) {
        this(msg == null ? null : parseMessage(msg));
    }

    /**
     * parse the response message
     *
     * @param msg the message
     * @return the LazyJsonValue of the parsed JSON
     */
    protected static LazyJsonValue parseMessage(Message msg) {
        try {
            return parse(msg.getData());
        }
        catch (JsonParseException e) {
            return parseUnchecked(PARSE_ERROR_ERROR_JSON);
        }
    }

    /**
     * Called when the JSON is invalid. Sets the error if it is not already set.
     */
    protected void invalidJson() {
        if (error == null) {
            error = new Error(500, "Invalid JSON for " + getClass().getSimpleName());
        }
    }

    /**
     * set an error if the value in the key is null/not found
     *
     * @param key the key
     * @return the value of the key or empty string if the value is null/not found
     */
    protected String stringRequired(String key) {
        if (hasError()) {
            return "";
        }
        String s = readString(ljv, key);
        if (s == null) {
            invalidJson();
            return "";
        }
        return s;
    }

    /**
     * set an error if the value in the key is null
     *
     * @param key the key
     * @return the value of the key
     */
    protected ZonedDateTime dateRequired(String key) {
        if (hasError()) {
            return DateTimeUtils.DEFAULT_TIME;
        }
        ZonedDateTime zdt = readDate(ljv, key);
        if (zdt == null) {
            invalidJson();
            return DateTimeUtils.DEFAULT_TIME;
        }
        return zdt;
    }
    protected <R> R valueRequired(String key, Function<LazyJsonValue, R> maker, R errVal) {
        if (hasError()) {
            return errVal;
        }
        LazyJsonValue v = readValue(ljv, key);
        if (v == null) {
            invalidJson();
            return errVal;
        }
        return maker.apply(v);
    }

    /**
     * Construct an ApiResponse from a LazyJsonValue
     * @param lazyJsonValue the value
     */
    protected ApiResponse(@Nullable LazyJsonValue lazyJsonValue) {
        if (lazyJsonValue == null) {
            ljv = LazyJsonValue.EMPTY_MAP;
            error = null;
            type = NO_TYPE;
        }
        else {
            ljv = lazyJsonValue;
            error = Error.optionalInstance(readValue(ljv, ERROR));
            String temp = readString(ljv, TYPE);
            type = temp == null ? NO_TYPE : temp;
        }
    }

    /**
     * Construct an empty ApiResponse
     */
    protected ApiResponse() {
        ljv = LazyJsonValue.EMPTY_MAP;
        error = null;
        type = NO_TYPE;
    }

    /**
     * Construct an ApiResponse from an error object
     * @param error the error object
     */
    protected ApiResponse(Error error) {
        ljv = LazyJsonValue.EMPTY_MAP;
        this.error = error;
        type = NO_TYPE;
    }

    /**
     * throw an Exception if the response had an error
     * @return the ApiResponse if not an error
     * @throws JetStreamApiException if the response had an error
     */
    @SuppressWarnings("unchecked")
    public T throwOnHasError() throws JetStreamApiException {
        if (error != null) {
            throw new JetStreamApiException(error);
        }
        return (T)this;
    }

    /**
     * Get the LazyJsonValue used to make this object
     * @return the value
     */
    @Nullable
    public LazyJsonValue getOriginalJsonValue() {
        return ljv;
    }

    /**
     * Does the response have an error
     * @return true if the response has an error
     */
    public boolean hasError() {
        return error != null;
    }

    /**
     * The type of the response object
     * @return the type
     */
    @Nullable
    public String getType() {
        return type;
    }

    /**
     * The request error code from the server
     * @return the code
     */
    public int getErrorCode() {
        return error == null ? Error.NOT_SET : error.getCode();
    }

    /**
     * The api error code from the server
     * @return the code
     */
    public int getApiErrorCode() {
        return error == null ? Error.NOT_SET : error.getApiErrorCode();
    }

    /**
     * Get the error description
     * @return the description if the response is an error
     */
    @Nullable
    public String getDescription() {
        return error == null ? null : error.getDescription();
    }

    /**
     * Get the error object string
     * @return the error object string if the response is an error
     */
    @Nullable
    public String getError() {
        return error == null ? null : error.toString();
    }

    /**
     * Get the error object
     * @return the error object if the response is an error
     */
    @Nullable
    public Error getErrorObject() {
        return error;
    }

    @Override
    public String toString() {
        if (ljv == LazyJsonValue.EMPTY_MAP) {
            return toKey(getClass()) + "\":null";
        }

        JsonValue jv = this.ljv.toJsonValue();
        if (jv.map != null) {
            jv.map.remove(TYPE); // just so it's not in the toString, it's very long and the object name will be there
        }

        return toKey(getClass()) + jv.toJson();
    }
}
