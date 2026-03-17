package io.synadia.service;

import io.synadia.client.Message;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.NatsMessage;
import io.synadia.client.support.JsonSerializable;

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

    ServiceMessage(Message message) {
        this.message = message;
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a byte array 
     */
    public void respond(NatsConnection conn, byte[] response) {
        conn.publish(message.getReplyTo(), response);
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a string
     */
    public void respond(NatsConnection conn, String response) {
        conn.publish(message.getReplyTo(), response.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a {@link JsonSerializable} object
     */
    public void respond(NatsConnection conn, JsonSerializable response) {
        conn.publish(message.getReplyTo(), response.serialize());
    }

    /**
     * Respond to a service request message with a response and custom headers.
     * @param conn the NATS connection
     * @param response the response payload in the form of a byte array
     * @param headers the custom headers                 
     */
    public void respond(NatsConnection conn, byte[] response, Headers headers) {
        conn.publish(NatsMessage.builder().subject(message.getReplyTo()).data(response).headers(headers).build());
    }

    /**
     * Respond to a service request message with a response and custom headers.
     * @param conn the NATS connection
     * @param response the response payload in the form of a string
     * @param headers the custom headers                 
     */
    public void respond(NatsConnection conn, String response, Headers headers) {
        conn.publish(NatsMessage.builder().subject(message.getReplyTo()).data(response).headers(headers).build());
    }

    /**
     * Respond to a service request message.
     * @param conn the NATS connection
     * @param response the response payload in the form of a {@link JsonSerializable} object
     * @param headers the custom headers                 
     */
    public void respond(NatsConnection conn, JsonSerializable response, Headers headers) {
        conn.publish(NatsMessage.builder().subject(message.getReplyTo()).data(response.serialize()).headers(headers).build());
    }

    /**
     * Respond to a service request message with a standard error.
     * @param conn the NATS connection
     * @param errorText the error message text
     * @param errorCode the error message code
     */
    public void respondStandardError(NatsConnection conn, String errorText, int errorCode) {
        conn.publish(NatsMessage.builder()
            .subject(message.getReplyTo())
            .headers(new Headers()
                .put(NATS_SERVICE_ERROR, errorText)
                .put(NATS_SERVICE_ERROR_CODE, "" + errorCode))
            .build());
    }

    /**
     * the subject that this message was sent to
     * @return the subject that this message was sent to
     */
    public String getSubject() {
        return message.getSubject();
    }

    /**
     * the subject the application is expected to send a reply message on
     * @return the subject the application is expected to send a reply message on
     */
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
    public Headers getHeaders() {
        return message.getHeaders();
    }

    /**
     * the data from the message
     * @return the data from the message
     */
    public byte[] getData() {
        return message.getData();
    }
}
