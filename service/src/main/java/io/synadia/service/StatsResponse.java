package io.synadia.service;

import io.nats.json.JsonValue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import static io.nats.json.JsonValueUtils.readDate;
import static io.nats.json.JsonValueUtils.readValue;
import static io.nats.json.JsonWriteUtils.addField;
import static io.nats.json.JsonWriteUtils.addJsons;
import static io.synadia.service.ServiceConstants.ENDPOINTS;
import static io.synadia.service.ServiceConstants.STARTED;

/**
 * Stats response class forms the stats json payload, for example:
 * <code>
 * {
 * "id": "ZP1oVevzLGu4CBORMXKKke",
 * "name": "Service1",
 * "version": "0.0.1",
 * "endpoints": [{
 *     "name": "SortEndpointAscending",
 *     "subject": "sort.ascending",
 *     "num_requests": 1,
 *     "processing_time": 538900,
 *     "average_processing_time": 538900,
 *     "started": "2023-08-15T13:51:41.318000000Z"
 * }, {
 *     "name": "SortEndpointDescending",
 *     "subject": "sort.descending",
 *     "num_requests": 1,
 *     "processing_time": 88400,
 *     "average_processing_time": 88400,
 *     "started": "2023-08-15T13:51:41.318000000Z"
 * }, {
 *     "name": "EchoEndpoint",
 *     "subject": "echo",
 *     "num_requests": 5,
 *     "processing_time": 1931600,
 *     "average_processing_time": 386320,
 *     "data": {
 *          "idata": 2,
 *          "sdata": "s-996409223"
 *     },
 *     "started": "2023-08-15T13:51:41.318000000Z"
 * }],
 * "started": "2023-08-15T13:51:41.319000000Z",
 * "type": "io.nats.micro.v1.stats_response"
 * }
 * </code>
 */
public class StatsResponse extends ServiceResponse {
    /**
     * The API response type for StatsResponse
     */
    public static final String TYPE = "io.nats.micro.v1.stats_response";

    private final ZonedDateTime started;
    private final List<EndpointStats> endpointStatsList;

    StatsResponse(ServiceResponse template, ZonedDateTime started, List<EndpointStats> endpointStatsList) {
        super(TYPE, template);
        this.started = started;
        this.endpointStatsList = endpointStatsList;
    }

    StatsResponse(byte[] jsonBytes) {
        this(parseMessage(jsonBytes));
    }

    private StatsResponse(JsonValue jv) {
        super(TYPE, jv);
        endpointStatsList = EndpointStats.listOf(readValue(jv, ENDPOINTS));
        started = readDate(jv, STARTED);
    }

    @Override
    protected void subToJson(StringBuilder sb) {
        addJsons(sb, ENDPOINTS, endpointStatsList);
        addField(sb, STARTED, started);
    }

    /**
     * Get the time the service was started
     * @return the start time
     */
    public ZonedDateTime getStarted() {
        return started;
    }

    /**
     * Get the list of {@link EndpointStats}
     * @return the list of endpoint stats
     */
    public List<EndpointStats> getEndpointStatsList() {
        return endpointStatsList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        StatsResponse that = (StatsResponse) o;

        if (!Objects.equals(started, that.started)) return false;
        return Objects.equals(endpointStatsList, that.endpointStatsList);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (started != null ? started.hashCode() : 0);
        result = 31 * result + (endpointStatsList != null ? endpointStatsList.hashCode() : 0);
        return result;
    }
}
