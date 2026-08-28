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

    /**
     * Free-form description of the consumer.
     * @return the description.
     */
    @Nullable
    public String getDescription() {
        return readString(ljv, DESCRIPTION);
    }

    /**
     * Durable name, which makes the consumer survive client restarts. Null for an ephemeral consumer.
     * @return name of the durable.
     */
    @Nullable
    public String getDurable() {
        return readString(ljv, DURABLE_NAME);
    }

    /**
     * The consumer's name, which the server uses to address it.
     * @return name of the consumer.
     */
    public String getName() {
        //noinspection DataFlowIssue we know this will not be null from the server
        return readString(ljv, NAME);
    }

    /**
     * Subject the server pushes messages to. Set only for push consumers.
     * @return the deliver subject.
     */
    @Nullable
    public String getDeliverSubject() {
        return readString(ljv, DELIVER_SUBJECT);
    }

    /**
     * Indicates if the client is configured as a push consumer
     * @return the flag
     */
    public boolean isPushConsumer() {
        return getDeliverSubject() != null;
    }

    /**
     * Indicates if the client is configured as a push consumer
     * @return the flag
     */
    public boolean isPullConsumer() {
        return !isPushConsumer();
    }

    /**
     * Queue group sharing the deliver subject, so its members split the messages between them.
     * @return the deliver group.
     */
    @Nullable
    public String getDeliverGroup() {
        return readString(ljv, DELIVER_GROUP);
    }

    /**
     * Where in the stream the consumer begins reading.
     * @return the deliver policy.
     */
    public DeliverPolicy getDeliverPolicy() {
        return DeliverPolicy.get(readString(ljv, DELIVER_POLICY), DEFAULT_DELIVER_POLICY);
    }

    /**
     * Stream sequence to begin at, used when the deliver policy starts by sequence.
     * @return the start sequence. The server value is an unsigned 64-bit number.
     */
    public long getStartSequence() {
        return readUnsignedLongOrZero(ljv, OPT_START_SEQ);
    }

    /**
     * Stream sequence to begin at, used when the deliver policy starts by sequence.
     * @return the start sequence as a non-negative unsigned {@link BigInteger}; companion to {@link #getStartSequence()}.
     */
    public BigInteger getStartSequenceAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, OPT_START_SEQ);
    }

    /**
     * Point in time to begin at, used when the deliver policy starts by time.
     * @return the start time.
     */
    @Nullable
    public ZonedDateTime getStartTime() {
        return readDate(ljv, OPT_START_TIME);
    }

    /**
     * Whether messages must be acknowledged, and whether an ack covers earlier ones.
     * @return the acknowledgment policy.
     */
    public AckPolicy getAckPolicy() {
        return AckPolicy.get(readString(ljv, ACK_POLICY), DEFAULT_ACK_POLICY);
    }

    /**
     * How long the server waits for an ack before redelivering the message.
     * @return the acknowledgment wait duration.
     */
    public Duration getAckWait() {
        Duration d = readNanosAsDuration(ljv, ACK_WAIT);
        return d == null ? Duration.ZERO : d;
    }

    /**
     * How many times a message may be delivered before the server stops trying.
     * @return the max delivery amount.
     */
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

    /**
     * Only messages on these subjects are delivered. Empty means the whole stream.
     * @return the filter subjects list
     */
    public List<String> getFilterSubjects() {
        // Server may send filter_subject (singular) or filter_subjects (plural)
        String single = getFilterSubject();
        if (single != null) {
            return List.of(single);
        }
        return readStringListOrEmpty(ljv, FILTER_SUBJECTS);
    }

    /**
     * Named priority groups this consumer serves.
     * @return the priority groups list
     */
    @Nullable
    public List<String> getPriorityGroups() {
        return readStringListOrEmpty(ljv, PRIORITY_GROUPS);
    }

    /**
     * Whether more than one filter subject is set, which older servers do not support.
     * @return true if there are multiple filter subjects
     */
    public boolean hasMultipleFilterSubjects() {
        return getFilterSubjects().size() > 1;
    }

    /**
     * Whether messages replay as fast as possible or at their original recorded pace.
     * @return the replay policy.
     */
    public ReplayPolicy getReplayPolicy() {
        return ReplayPolicy.get(readString(ljv, REPLAY_POLICY), DEFAULT_REPLAY_POLICY);
    }

    /**
     * Ceiling on delivery throughput, in bits per second.
     * @return the rate limit in bits per second. The server value is an unsigned 64-bit number.
     */
    public long getRateLimit() {
        return readUnsignedLongOrZero(ljv, RATE_LIMIT_BPS);
    }

    /**
     * Unsigned form of the rate limit, for values above the signed long range.
     * @return the rate limit as a non-negative unsigned {@link BigInteger}; companion to {@link #getRateLimit()}.
     */
    public BigInteger getRateLimitAsBigInteger() {
        return readUnsignedBigIntegerOrZero(ljv, RATE_LIMIT_BPS);
    }

    /**
     * How many unacknowledged messages may be outstanding before the server pauses delivery.
     * @return maximum ack pending.
     */
    public long getMaxAckPending() {
        return readLong(ljv, MAX_ACK_PENDING, UNSET);
    }

    /**
     * Percentage of acknowledgements the server samples for monitoring.
     * @return the sample frequency.
     */
    @Nullable
    public String getSampleFrequency() {
        return readString(ljv, SAMPLE_FREQ);
    }

    /**
     * How often the server sends a heartbeat while idle, so a stalled consumer can be detected.
     * @return the idle heart beat wait duration.
     */
    @Nullable
    public Duration getIdleHeartbeat() {
        return readNanosAsDuration(ljv, IDLE_HEARTBEAT);
    }

    /**
     * Whether the server paces delivery using flow control messages.
     * @return the flow control flag
     */
    public boolean isFlowControl() {
        return readBoolean(ljv, FLOW_CONTROL, false);
    }

    /**
     * How many pull requests may be parked waiting for messages to arrive.
     * @return the max pull waiting
     */
    public long getMaxPullWaiting() {
        return readLong(ljv, MAX_WAITING, UNSET);
    }

    /**
     * Whether only headers are delivered, omitting message bodies.
     * @return the headers only flag
     */
    public boolean isHeadersOnly() {
        return readBoolean(ljv, HEADERS_ONLY, false);
    }

    /**
     * Whether the consumer's state is kept in memory rather than on file.
     * @return the mem storage flag
     */
    public boolean isMemStorage() {
        return readBoolean(ljv, MEM_STORAGE, false);
    }

    /**
     * Largest batch a single pull request may ask for.
     * @return the max batch size
     */
    public long getMaxBatch() {
        return readLong(ljv, MAX_BATCH, UNSET);
    }

    /**
     * Largest total size a single pull request may ask for.
     * @return the max byte size
     */
    public long getMaxBytes() {
        return readLong(ljv, MAX_BYTES, UNSET);
    }

    /**
     * Longest expiry a single pull request may ask for.
     * @return the max expire
     */
    @Nullable
    public Duration getMaxExpires() {
        return readNanosAsDuration(ljv, MAX_EXPIRES);
    }

    /**
     * How long the consumer may go unused before the server removes it.
     * @return the inactive threshold
     */
    @Nullable
    public Duration getInactiveThreshold() {
        return readNanosAsDuration(ljv, INACTIVE_THRESHOLD);
    }

    /**
     * Escalating redelivery delays, applied in order on successive redeliveries.
     * @return the backoff list
     */
    public List<Duration> getBackoff() {
        return readNanosAsDurationListOrEmpty(ljv, BACKOFF);
    }

    /**
     * User metadata carried on the consumer.
     * @return the metadata map
     */
    public Map<String, String> getMetadata() {
        return readStringMapOrEmpty(ljv, METADATA);
    }

    /**
     * How many replicas of the consumer state the cluster keeps.
     * @return the replicas count
     */
    public long getNumReplicas() {
        return readLong(ljv, NUM_REPLICAS, 0);
    }

    /**
     * The consumer is paused until this time, after which delivery resumes.
     * @return paused until time
     */
    @Nullable
    public ZonedDateTime getPauseUntil() {
        return readDate(ljv, PAUSE_UNTIL);
    }

    /**
     * How the server chooses among members of a priority group.
     * @return the priority policy.
     */
    public PriorityPolicy getPriorityPolicy() {
        return PriorityPolicy.get(readString(ljv, PRIORITY_POLICY), DEFAULT_PRIORITY_POLICY);
    }

    /**
     * How long a priority group member holds its claim before another may take over.
     * @return the priority timeout duration
     */
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
