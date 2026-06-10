package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.nats.json.LazyJsonValueUtils.readBoolean;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.*;

/**
 * The ConsumerInfo class returns information about a JetStream consumer.
 */
@NullMarked
public class ConsumerInfo extends ApiResponse<ConsumerInfo> {

    private @Nullable ConsumerConfiguration _configuration;
    private @Nullable SequenceInfo _delivered;
    private @Nullable SequenceInfo _ackFloor;
    private @Nullable List<PriorityGroupState> _priorityGroupStates;

    /**
     * Construct a ConsumerInfo instance from a message
     * @param msg the message
     */
    public ConsumerInfo(Message msg) {
        super(msg);
    }

    /**
     * Construct a ConsumerInfo instance from a LazyJsonValue
     * @param ljv the JsonValue
     */
    public ConsumerInfo(LazyJsonValue ljv) {
        super(ljv);
    }

    /**
     * The consumer configuration representing this consumer.
     * @return the config
     */
    public ConsumerConfiguration getConsumerConfiguration() {
        if (_configuration == null) {
            if (hasError()) {
                return ConsumerConfiguration.getDefaultInstance();
            }
            LazyJsonValue v = readValue(ljv, CONFIG);
            if (v == null) {
                invalidJson();
                return ConsumerConfiguration.getDefaultInstance();
            }
            _configuration = new ConsumerConfiguration(v);
        }
        return _configuration;
    }

    /**
     * A unique name for the consumer, either machine generated or the durable name
     * @return the name
     */
    public String getName() {
        return stringRequired(NAME);
    }

    /**
     * The Stream the consumer belongs to
     * @return the stream name
     */
    public String getStreamName() {
        return stringRequired(STREAM_NAME);
    }

    /**
     * Gets the creation time of the consumer.
     * @return the creation date and time.
     */
    public ZonedDateTime getCreationTime() {
        return dateRequired(CREATED);
    }

    /**
     * The last message delivered from this Consumer
     * @return the last delivered sequence info
     */
    public SequenceInfo getDelivered() {
        if (_delivered == null) {
            _delivered = hasError() ? SequenceInfo.EMPTY : new SequenceInfo(readMapObjectOrEmpty(ljv, DELIVERED));
        }
        return _delivered;
    }

    /**
     * The highest contiguous acknowledged message
     * @return the sequence info
     */
    public SequenceInfo getAckFloor() {
        if (_ackFloor == null) {
            _ackFloor = hasError() ? SequenceInfo.EMPTY : new SequenceInfo(readMapObjectOrEmpty(ljv, ACK_FLOOR));
        }
        return _ackFloor;
    }

    /**
     * The number of messages left unconsumed in this Consumer.
     * <p>The server value is an unsigned 64-bit number.
     * @return the number of pending messages
     */
    public long getNumPending() {
        return readUnsignedLongOrZero(ljv, NUM_PENDING);
    }

    /**
     * The number of messages left unconsumed in this Consumer as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getNumPending()}.
     * @return the number of pending messages, or {@link BigInteger#ZERO} if absent
     */
    public BigInteger getNumPendingAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, NUM_PENDING);
    }

    /**
     * The number of pull consumers waiting for messages
     * @return the number of waiting messages
     */
    public long getNumWaiting() {
        return readLongOrMinusOne(ljv, NUM_WAITING);
    }

    /**
     * The number of messages pending acknowledgement
     * @return the number of messages
     */
    public long getNumAckPending() {
        return readLongOrMinusOne(ljv, NUM_ACK_PENDING);
    }

    /**
     * The number of redeliveries that have been performed
     * @return the number of redeliveries
     */
    public long getRedelivered() {
        return readLongOrMinusOne(ljv, NUM_REDELIVERED);
    }

    /**
     * Indicates if the consumer is currently in a paused state
     * @return true if paused
     */
    public boolean getPaused() {
        return readBoolean(ljv, PAUSED, false);
    }

    /**
     * When paused the time remaining until unpause
     * @return the time remaining
     */
    @Nullable
    public Duration getPauseRemaining() {
        return readNanosAsDuration(ljv, PAUSE_REMAINING);
    }

    /**
     * Information about the cluster for clustered environments
     * @return the cluster info object
     */
    @Nullable
    public ClusterInfo getClusterInfo() {
        return ClusterInfo.optionalInstance(readValue(ljv, CLUSTER));
    }

    /**
     * Indicates if any client is connected and receiving messages from a push consumer
     * @return the flag
     */
    public boolean isPushBound() {
        return readBoolean(ljv, PUSH_BOUND, false);
    }

    /**
     * Gets the server time the info was gathered
     * @return the server gathered timed
     */
    public ZonedDateTime getTimestamp() {
        return dateRequired(TIMESTAMP);
    }

    /**
     * The state of Priority Groups
     * @return the list of Priority Groups
     */
    public List<PriorityGroupState> getPriorityGroupStates() {
        if (_priorityGroupStates == null) {
            _priorityGroupStates = PriorityGroupState.listOf(readValue(ljv, PRIORITY_GROUPS));
        }
        return _priorityGroupStates;
    }

    /**
     * A way to more accurately calculate pending during the initial state
     * of the consumer when messages may be unaccounted for in flight
     * @return the calculated amount
     */
    public long getCalculatedPending() {
        return getNumPending() + getDelivered().getConsumerSequence();
    }
}
