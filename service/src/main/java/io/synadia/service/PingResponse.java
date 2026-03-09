package io.synadia.service;

import java.util.Map;

/**
 * Ping response class forms the ping json payload, for example:
 * <code>{"id":"JlkwZvmHAXCQGwwxiPwaBJ","name":"MyService","version":"0.0.1","type":"io.nats.micro.v1.ping_response"}</code>
 */
public class PingResponse extends ServiceResponse {
    /**
     * The API response type for PingResponse
     */
    public static final String TYPE = "io.nats.micro.v1.ping_response";

    PingResponse(String id, String name, String version, Map<String, String> metadata) {
        super(TYPE, id, name, version, metadata);
    }

    PingResponse(byte[] jsonBytes) {
        super(TYPE, parseMessage(jsonBytes));
    }
}
