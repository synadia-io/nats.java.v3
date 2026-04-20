package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * The StreamInfo class contains information about a JetStream stream.
 */
@NullMarked
public class StreamInfo extends ApiResponse<StreamInfo> {

    private @Nullable StreamConfiguration _config;
    private @Nullable StreamState _streamState;
    private @Nullable List<SourceInfo> _sources;
    private @Nullable List<StreamAlternate> _alternates;

    /**
     * Construct a StreamInfo instance from a message
     * @param msg the message
     */
    public StreamInfo(Message msg) {
        super(msg);
    }

    /**
     * Construct a StreamInfo instance from a LazyJsonValue
     * @param ljv the JsonValue
     */
    public StreamInfo(LazyJsonValue ljv) {
        super(ljv);
    }

    /**
     * Gets the stream configuration. Same as getConfig
     * @return the stream configuration.
     */
    public StreamConfiguration getConfiguration() {
        if (_config == null) {
            _config = valueRequired(CONFIG, StreamConfiguration::new, StreamConfiguration.EMPTY);
        }
        return _config;
    }

    /**
     * Gets the stream state.
     * @return the stream state
     */
    public StreamState getStreamState() {
        if (_streamState == null) {
            _streamState = valueRequired(STATE, StreamState::new, StreamState.EMPTY);
        }
        return _streamState;
    }

    /**
     * Gets the creation time of the stream.
     * @return the creation date and time.
     */
    public ZonedDateTime getCreateTime() {
        return dateRequired(CREATED);
    }

    /**
     * Gets the mirror info
     * @return the mirror info
     */
    @Nullable
    public MirrorInfo getMirrorInfo() {
        return MirrorInfo.optionalInstance(readValue(ljv, MIRROR));
    }

    /**
     * Gets the source info
     * @return the source info
     */
    public List<SourceInfo> getSources() {
        if (_sources == null) {
            _sources = SourceInfo.listOf(readValue(ljv, SOURCES));
        }
        return _sources;
    }

    /**
     * Gets the cluster info
     * @return the cluster info
     */
    @Nullable
    public ClusterInfo getClusterInfo() {
        return ClusterInfo.optionalInstance(readValue(ljv, CLUSTER));
    }

    /**
     * Gets the stream alternates
     * @return the stream alternates
     */
    public List<StreamAlternate> getAlternates() {
        if (_alternates == null) {
            _alternates = StreamAlternate.listOf(readValue(ljv, ALTERNATES));
        }
        return _alternates;
    }

    /**
     * Gets the server time the info was gathered
     * @return the server gathered timed
     */
    @Nullable
    public ZonedDateTime getTimestamp() {
        return readDate(ljv, TIMESTAMP);
    }

    /**
     * Indicates if this response was made from a stream creation call
     * @return true if did create
     */
    public boolean didCreate() {
        return readBoolean(ljv, DID_CREATE, false);
    }
}
