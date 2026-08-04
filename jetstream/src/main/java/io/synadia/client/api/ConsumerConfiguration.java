package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import io.nats.json.LazyMapBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.api.ConsumerCreator.*;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.ApiUtils.readUnsignedBigIntegerOrZero;
import static io.synadia.client.utils.ApiUtils.readUnsignedLongOrZero;
import static io.synadia.client.utils.JetStreamApiUtils.UNSET;
import static io.synadia.client.utils.NatsConstants.UNDEFINED;

/**
 * The ConsumerConfiguration is returned from the server on consumer info calls.
 */
@NullMarked
public class ConsumerConfiguration extends LazyApiObject {
    private static @Nullable ConsumerConfiguration DEFAULT_INSTANCE;

    /**
     * A configuration with every setting left at its default.
     * @return the default instance
     */
    public static ConsumerConfiguration getDefaultInstance() {
        if (DEFAULT_INSTANCE == null) {
            DEFAULT_INSTANCE = new ConsumerConfiguration(
                new LazyMapBuilder().put(NAME, UNDEFINED).build());
        }
        return DEFAULT_INSTANCE;
    }

    /**
     * Construct a ConsumerConfiguration from a LazyJsonValue (server response).
     * @param v the LazyJsonValue
     */
    ConsumerConfiguration(LazyJsonValue v) {
        super(v);
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /** @return the description. */
    @Nullable
    public String getDescription() {
        return readString(ljv, DESCRIPTION);
    }

    /** @return name of the durable. */
    @Nullable
    public String getDurable() {
        return readString(ljv, DURABLE_NAME);
    }

    /** @return name of the consumer. */
    public String getName() {
        //noinspection DataFlowIssue we know this will not be null from the server
        return readString(ljv, NAME);
    }

    /** @return the deliver subject. */
    @Nullable
    public String getDeliverSubject() {
        return readString(ljv, DELIVER_SUBJECT);
    }

    /** @return the deliver group. */
    @Nullable
    public String getDeliverGroup() {
        return readString(ljv, DELIVER_GROUP);
    }

    /** @return the deliver policy. */
    public DeliverPolicy getDeliverPolicy() {
        return DeliverPolicy.get(readString(ljv, DELIVER_POLICY), DEFAULT_DELIVER_POLICY);
    }

    /** @return the start sequence. The server value is an unsigned 64-bit number. */
    public long getStartSequence() {
        return readUnsignedLongOrZero(ljv, OPT_START_SEQ);
    }

    /** @return the start sequence as a non-negative unsigned {@link BigInteger}; companion to {@link #getStartSequence()}. */
    public BigInteger getStartSequenceAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, OPT_START_SEQ);
    }

    /** @return the start time. */
    @Nullable
    public ZonedDateTime getStartTime() {
        return readDate(ljv, OPT_START_TIME);
    }

    /** @return the acknowledgment policy. */
    public AckPolicy getAckPolicy() {
        return AckPolicy.get(readString(ljv, ACK_POLICY), DEFAULT_ACK_POLICY);
    }

    /** @return the acknowledgment wait duration. */
    public Duration getAckWait() {
        Duration d = readNanosAsDuration(ljv, ACK_WAIT);
        return d == null ? Duration.ZERO : d;
    }

    /** @return the max delivery amount. */
    public long getMaxDeliver() {
        return readLong(ljv, MAX_DELIVER, UNSET);
    }

    /**
     * Gets the filter subject.
     * Returns null if there is not exactly one filter subject.
     * @return the first filter subject.
     */
    @Nullable
    public String getFilterSubject() {
        return readString(ljv, FILTER_SUBJECT);
    }

    /** @return the filter subjects list */
    public List<String> getFilterSubjects() {
        // Server may send filter_subject (singular) or filter_subjects (plural)
        String single = getFilterSubject();
        if (single != null) {
            return List.of(single);
        }
        return readStringListOrEmpty(ljv, FILTER_SUBJECTS);
    }

    /** @return the priority groups list */
    @Nullable
    public List<String> getPriorityGroups() {
        return readStringListOrEmpty(ljv, PRIORITY_GROUPS);
    }

    /** @return true if there are multiple filter subjects */
    public boolean hasMultipleFilterSubjects() {
        return getFilterSubjects().size() > 1;
    }

    /** @return the replay policy. */
    public ReplayPolicy getReplayPolicy() {
        return ReplayPolicy.get(readString(ljv, REPLAY_POLICY), DEFAULT_REPLAY_POLICY);
    }

    /** @return the rate limit in bits per second. The server value is an unsigned 64-bit number. */
    public long getRateLimit() {
        return readUnsignedLongOrZero(ljv, RATE_LIMIT_BPS);
    }

    /** @return the rate limit as a non-negative unsigned {@link BigInteger}; companion to {@link #getRateLimit()}. */
    public BigInteger getRateLimitAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, RATE_LIMIT_BPS);
    }

    /** @return maximum ack pending. */
    public long getMaxAckPending() {
        return readLong(ljv, MAX_ACK_PENDING, UNSET);
    }

    /** @return the sample frequency. */
    @Nullable
    public String getSampleFrequency() {
        return readString(ljv, SAMPLE_FREQ);
    }

    /** @return the idle heart beat wait duration. */
    @Nullable
    public Duration getIdleHeartbeat() {
        return readNanosAsDuration(ljv, IDLE_HEARTBEAT);
    }

    /** @return the flow control flag */
    public boolean isFlowControl() {
        return readBoolean(ljv, FLOW_CONTROL, false);
    }

    /** @return the max pull waiting */
    public long getMaxPullWaiting() {
        return readLong(ljv, MAX_WAITING, UNSET);
    }

    /** @return the headers only flag */
    public boolean isHeadersOnly() {
        return readBoolean(ljv, HEADERS_ONLY, false);
    }

    /** @return the mem storage flag */
    public boolean isMemStorage() {
        return readBoolean(ljv, MEM_STORAGE, false);
    }

    /** @return the max batch size */
    public long getMaxBatch() {
        return readLong(ljv, MAX_BATCH, UNSET);
    }

    /** @return the max byte size */
    public long getMaxBytes() {
        return readLong(ljv, MAX_BYTES, UNSET);
    }

    /** @return the max expire */
    @Nullable
    public Duration getMaxExpires() {
        return readNanosAsDuration(ljv, MAX_EXPIRES);
    }

    /** @return the inactive threshold */
    @Nullable
    public Duration getInactiveThreshold() {
        return readNanosAsDuration(ljv, INACTIVE_THRESHOLD);
    }

    /** @return the backoff list */
    public List<Duration> getBackoff() {
        return readNanosAsDurationListOrEmpty(ljv, BACKOFF);
    }

    /** @return the metadata map */
    public Map<String, String> getMetadata() {
        return readStringMapOrEmpty(ljv, METADATA);
    }

    /** @return the replicas count */
    public long getNumReplicas() {
        return readLong(ljv, NUM_REPLICAS, 0);
    }

    /** @return paused until time */
    @Nullable
    public ZonedDateTime getPauseUntil() {
        return readDate(ljv, PAUSE_UNTIL);
    }

    /** @return the priority policy. */
    public PriorityPolicy getPriorityPolicy() {
        return PriorityPolicy.get(readString(ljv, PRIORITY_POLICY), DEFAULT_PRIORITY_POLICY);
    }

    /** @return the priority timeout duration */
    @Nullable
    public Duration getPriorityTimeout() {
        return readNanosAsDuration(ljv, PRIORITY_TIMEOUT);
    }

    // ----------------------------------------------------------------------------------------------------
    // JSON
    // ----------------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return "ConsumerConfiguration " + ljv.toJson();
    }
}
