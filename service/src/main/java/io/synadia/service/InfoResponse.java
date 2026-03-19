package io.synadia.service;

import io.nats.json.JsonValue;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nats.json.JsonValueUtils.*;
import static io.nats.json.JsonWriteUtils.addField;
import static io.nats.json.JsonWriteUtils.addJsons;
import static io.synadia.client.support.ApiConstants.DESCRIPTION;
import static io.synadia.client.support.ApiConstants.ENDPOINTS;

/**
 * Info response class forms the info json payload, for example:
 * <code>{"id":"JlkwZvmHAXCQGwwxiPwaBJ","name":"MyService","version":"0.0.1","endpoints":[{"name":"MyEndpoint","subject":"myend"}],"type":"io.nats.micro.v1.info_response"}</code>
 */
public class InfoResponse extends ServiceResponse {
    /**
     * The API response type for InfoResponse
     */
    public static final String TYPE = "io.nats.micro.v1.info_response";

    private final String description;
    private final List<Endpoint> endpoints;

    InfoResponse(String id, String name, String version, Map<String, String> metadata, String description) {
        super(TYPE, id, name, version, metadata);
        this.description = description;
        this.endpoints = new ArrayList<>();
    }

    void addServiceEndpoint(@NonNull ServiceEndpoint se) {
        endpoints.add(new Endpoint(
            se.getName(),
            se.getSubject(),
            se.getQueueGroup(),
            se.getMetadata()
        ));
        serialized.set(null);
    }

    InfoResponse(byte[] jsonBytes) {
        this(parseMessage(jsonBytes));
    }

    private InfoResponse(JsonValue jv) {
        super(TYPE, jv);
        description = readString(jv, DESCRIPTION);
        endpoints = listOfOrEmpty(readValue(jv, ENDPOINTS), Endpoint::new);

    }

    @Override
    protected void subToJson(StringBuilder sb) {
        addField(sb, DESCRIPTION, description);
        addJsons(sb, ENDPOINTS, endpoints);
    }

    /**
     * Description for the service
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * List of endpoints
     * @return the endpoints
     */
    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        InfoResponse that = (InfoResponse) o;

        if (!Objects.equals(description, that.description)) return false;
        return Objects.equals(endpoints, that.endpoints);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (endpoints != null ? endpoints.hashCode() : 0);
        return result;
    }
}
