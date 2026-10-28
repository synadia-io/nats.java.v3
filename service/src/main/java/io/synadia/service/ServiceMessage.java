package io.synadia.service;

import io.nats.json.JsonSerializable;
import io.synadia.client.Message;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.NatsMessage;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

/**
 * Service Message is service specific object that exposes the service relevant parts of a NATS Message.
 */
public class ServiceMessage {

    /**
     * Standard header name used to report the text of an error
     */
    public static final String NATS_SERVICE_ERROR = "Nats-Service-Error";

    /**
     * Standard header name used to report the code of an error
     */
    public static final String NATS_SERVICE_ERROR_CODE = "Nats-Service-Error-Code";

    private final Message message;
    private byte[] responseData;
    private Headers responseHeaders;
    private String responseErrorDescription;
    private Integer responseErrorCode;

    ServiceMessage(@NonNull Message message) {
        this.message = message;
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a byte array
     */
    public void respond(@NonNull NatsConnection conn, byte @Nullable [] response) {
        _respond(conn, response, null, null, null);
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a string
     */
    public void respond(@NonNull NatsConnection conn, @Nullable String response) {
        _respond(conn, toBytes(response), null, null, null);
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a {@link JsonSerializable} object
     */
    public void respond(@NonNull NatsConnection conn, @Nullable JsonSerializable response) {
        _respond(conn, toBytes(response), null, null, null);
    }

    /**
     * Respond to a service request message with a response and custom headers.
     * @param conn the NATS connection
     * @param response the response payload in the form of a byte array
     * @param headers the custom headers
     */
    public void respond(@NonNull NatsConnection conn, byte @Nullable [] response, @Nullable Headers headers) {
        _respond(conn, response, headers, null, null);
    }

    /**
     * Respond to a service request message with a response and custom headers.
     * @param conn the NATS connection
     * @param response the response payload in the form of a string
     * @param headers the custom headers
     */
    public void respond(@NonNull NatsConnection conn, @Nullable String response, @Nullable Headers headers) {
        _respond(conn, toBytes(response), headers, null, null);
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a {@link JsonSerializable} object
     * @param headers the custom headers
     */
    public void respond(@NonNull NatsConnection conn, @Nullable JsonSerializable response, @Nullable Headers headers) {
        _respond(conn, toBytes(response), headers, null, null);
    }

    /**
     * Respond to a service request message with an error.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description) {
        _respond(conn, null, null, description, code);
    }

    /**
     * Respond to a service request message with an error and custom headers.
     * The error headers are added to a copy of the custom headers.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param headers the custom headers
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, @Nullable Headers headers) {
        _respond(conn, null, headers, description, code);
    }

    /**
     * Respond to a service request message with an error and a response.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a byte array
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, byte @Nullable [] response) {
        _respond(conn, response, null, description, code);
    }

    /**
     * Respond to a service request message with an error and a response.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a string
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, @Nullable String response) {
        _respond(conn, toBytes(response), null, description, code);
    }

    /**
     * Respond to a service request message with an error and a response.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a {@link JsonSerializable} object
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, @Nullable JsonSerializable response) {
        _respond(conn, toBytes(response), null, description, code);
    }

    /**
     * Respond to a service request message with an error, a response and custom headers.
     * The error headers are added to a copy of the custom headers.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a byte array
     * @param headers the custom headers
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, byte @Nullable [] response, @Nullable Headers headers) {
        _respond(conn, response, headers, description, code);
    }

    /**
     * Respond to a service request message with an error, a response and custom headers.
     * The error headers are added to a copy of the custom headers.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a string
     * @param headers the custom headers
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, @Nullable String response, @Nullable Headers headers) {
        _respond(conn, toBytes(response), headers, description, code);
    }

    /**
     * Respond to a service request message with an error, a response and custom headers.
     * The error headers are added to a copy of the custom headers.
     * If this is sent before the handler returns, it is counted in the endpoint's
     * num_errors and the description becomes the endpoint's last_error.
     * @param conn the NATS connection
     * @param code the error code, or null to send no {@value #NATS_SERVICE_ERROR_CODE} header
     * @param description the error description
     * @param response the response payload in the form of a {@link JsonSerializable} object
     * @param headers the custom headers
     */
    public void respondError(@NonNull NatsConnection conn, @Nullable Integer code, @NonNull String description, @Nullable JsonSerializable response, @Nullable Headers headers) {
        _respond(conn, toBytes(response), headers, description, code);
    }

    private static byte @Nullable [] toBytes(@Nullable String response) {
        return response == null ? null : response.getBytes(StandardCharsets.UTF_8);
    }

    private static byte @Nullable [] toBytes(@Nullable JsonSerializable response) {
        return response == null ? null : response.serialize();
    }

    // a response with an error description or code is an error; an error header is sent only for a value given
    private void _respond(@NonNull NatsConnection conn, byte @Nullable [] data, @Nullable Headers headers,
                          @Nullable String errorDescription, @Nullable Integer errorCode) {
        Headers h = headers;
        if (errorDescription != null || errorCode != null) {
            h = new Headers(headers);
            if (errorDescription != null) {
                h.put(NATS_SERVICE_ERROR, errorDescription);
            }
            if (errorCode != null) {
                h.put(NATS_SERVICE_ERROR_CODE, errorCode.toString());
            }
        }
        conn.publish(NatsMessage.builder().subject(message.getReplyTo()).data(data).headers(h).build());
        responseData = data;
        responseHeaders = h;
        responseErrorDescription = errorDescription;
        responseErrorCode = errorCode;
    }

    /**
     * The data of the last response sent to this message
     * @return the data or null if no response was sent or it had no data
     */
    byte @Nullable [] getResponseData() {
        return responseData;
    }

    /**
     * The headers of the last response sent to this message, including any error headers
     * @return the headers or null if no response was sent or it had no headers
     */
    @Nullable
    Headers getResponseHeaders() {
        return responseHeaders;
    }

    /**
     * The error description of the last response sent to this message
     * @return the error description or null if no response was sent or it had no error description
     */
    @Nullable
    String getResponseErrorDescription() {
        return responseErrorDescription;
    }

    /**
     * The error code of the last response sent to this message
     * @return the error code or null if no response was sent or it had no error code
     */
    @Nullable
    Integer getResponseErrorCode() {
        return responseErrorCode;
    }

    /**
     * the subject that this message was sent to
     * @return the subject that this message was sent to
     */
    @NonNull
    public String getSubject() {
        return message.getSubject();
    }

    /**
     * the subject the application is expected to send a reply message on
     * @return the subject the application is expected to send a reply message on
     */
    @Nullable
    public String getReplyTo() {
        return message.getReplyTo();
    }

    /**
     * true if there are headers
     * @return true if there are headers
     */
    public boolean hasHeaders() {
        return message.hasHeaders();
    }

    /**
     * the headers object for the message
     * @return the headers object for the message
     */
    @Nullable
    public Headers getHeaders() {
        return message.getHeaders();
    }

    /**
     * the data from the message
     * @return the data from the message
     */
    public byte @NonNull [] getData() {
        return message.getData();
    }
}
