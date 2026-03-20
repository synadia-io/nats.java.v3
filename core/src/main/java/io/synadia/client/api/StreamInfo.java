package io.synadia.client.api;

import io.nats.json.JsonValue;
import io.synadia.client.Message;
import io.synadia.client.support.DateTimeUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.JsonParser.parseUnchecked;
import static io.nats.json.JsonValueUtils.readDate;
import static io.nats.json.JsonValueUtils.readValue;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.NatsConstants.UNDEFINED;

/**
 * The StreamInfo class contains information about a JetStream stream.
 */
public class StreamInfo extends ApiResponse<StreamInfo> {

    private final ZonedDateTime createTime;
    private final StreamConfiguration config;
    private final StreamState streamState;
    private final ClusterInfo clusterInfo;
    private final MirrorInfo mirrorInfo;
    private final List<SourceInfo> sourceInfos;
    private final List<StreamAlternate> alternates;
    private final ZonedDateTime timestamp;

    /**
     * Construct a StreamInfo instance from a message
     * @param msg the message
     */
    public StreamInfo(@NonNull Message msg) {
        this(parseUnchecked(msg.getData()));
    }

    /**
     * Construct a StreamInfo instance from a JsonValue
     * @param vStreamInfo the JsonValue
     */
    public StreamInfo(@NonNull JsonValue vStreamInfo) {
        super(vStreamInfo);
        if (hasError()) {
            createTime = DateTimeUtils.DEFAULT_TIME;
            config = StreamConfiguration.builder().name(UNDEFINED).build();
            streamState = new StreamState(JsonValue.EMPTY_MAP);
            clusterInfo = null;
            mirrorInfo = null;
            sourceInfos = null;
            alternates = null;
            timestamp = null;
        }
        else {
            JsonValue jvConfig = nullValueIsError(jv, CONFIG, JsonValue.NULL);
            config = (jvConfig == JsonValue.NULL)
                ? StreamConfiguration.builder().name(UNDEFINED).build()
                : StreamConfiguration.instance(jvConfig);

            createTime = nullDateIsError(jv, CREATED);

            streamState = new StreamState(readValue(jv, STATE));
            clusterInfo = ClusterInfo.optionalInstance(readValue(jv, CLUSTER));
            mirrorInfo = MirrorInfo.optionalInstance(readValue(jv, MIRROR));
            sourceInfos = SourceInfo.optionalListOf(readValue(jv, SOURCES));
            alternates = StreamAlternate.optionalListOf(readValue(jv, ALTERNATES));
            timestamp = readDate(jv, TIMESTAMP);
        }
    }

    /**
     * Gets the stream configuration. Same as getConfig
     * @return the stream configuration.
     */
    @NonNull
    public StreamConfiguration getConfiguration() {
        return config;
    }

    /**
     * Gets the stream configuration. Same as getConfiguration
     * @return the stream configuration.
     */
    @NonNull
    public StreamConfiguration getConfig() {
        return config;
    }

    /**
     * Gets the stream state.
     * @return the stream state
     */
    @NonNull
    public StreamState getStreamState() {
        return streamState;
    }

    /**
     * Gets the creation time of the stream.
     * @return the creation date and time.
     */
    @NonNull
    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    /**
     * Gets the mirror info
     * @return the mirror info
     */
    @Nullable
    public MirrorInfo getMirrorInfo() {
        return mirrorInfo;
    }

    /**
     * Gets the source info
     * @return the source info
     */
    @Nullable
    public List<SourceInfo> getSourceInfos() {
        return sourceInfos;
    }

    /**
     * Gets the cluster info
     * @return the cluster info
     */
    @Nullable
    public ClusterInfo getClusterInfo() {
        return clusterInfo;
    }

    /**
     * Gets the stream alternates
     * @return the stream alternates
     */
    @Nullable
    public List<StreamAlternate> getAlternates() {
        return alternates;
    }

    /**
     * Gets the server time the info was gathered
     * @return the server gathered timed
     */
    @Nullable // doesn't exist in some versions of the server
    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "StreamInfo " + jv;
    }
}
