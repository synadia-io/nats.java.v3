package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import io.synadia.client.utils.ApiConstants;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.JetStreamApiUtils.*;
import static io.synadia.client.utils.JsValidator.*;

/**
 * Base class for consumer creators, providing setters common to all consumer types
 * (full, ephemeral, and ordered).
 * Setter methods validate their arguments and throw {@link IllegalArgumentException} for invalid values; see individual methods for specifics.
 * @param <T> the concrete creator type, returned by the fluent setters for chaining
 */
@NullMarked
public abstract class ConsumerCreator<T extends ConsumerCreator<T>> implements JsonSerializable {

    /**
     * The default deliver policy for consumers
     */
    public static final DeliverPolicy DEFAULT_DELIVER_POLICY = DeliverPolicy.All;

    /**
     * The default ack policy for consumers
     */
    public static final AckPolicy DEFAULT_ACK_POLICY = AckPolicy.Explicit;

    /**
     * The default replay policy for consumers
     */
    public static final ReplayPolicy DEFAULT_REPLAY_POLICY = ReplayPolicy.Instant;

    /**
     * The default priority policy for consumers
     */
    public static final PriorityPolicy DEFAULT_PRIORITY_POLICY = PriorityPolicy.None;

    /**
     * The minimum allowed idle heartbeat setting
     */
    public static final Duration MIN_IDLE_HEARTBEAT = Duration.ofMillis(100);

    /**
     * Constant representing the minimum idle heartbeat in nanos
     */
    public static final long MIN_IDLE_HEARTBEAT_NANOS = MIN_IDLE_HEARTBEAT.toNanos();

    /**
     * Constant representing the minimum idle heartbeat in milliseconds
     */
    public static final long MIN_IDLE_HEARTBEAT_MILLIS = MIN_IDLE_HEARTBEAT.toMillis();

    protected final boolean isPush;

    protected DeliverPolicy deliverPolicy;
    protected AckPolicy ackPolicy;
    protected ReplayPolicy replayPolicy;

    protected @Nullable String description;
    protected @Nullable String durable;
    protected @Nullable String name;
    protected @Nullable String deliverSubject;
    protected @Nullable String deliverGroup;
    protected @Nullable String sampleFrequency;

    protected @Nullable ZonedDateTime startTime;
    protected @Nullable Duration ackWait;
    protected @Nullable Duration idleHeartbeat;
    protected @Nullable Duration maxExpires;
    protected @Nullable Duration inactiveThreshold;

    protected long startSequence;
    protected long rateLimit;

    protected long maxDeliver;
    protected long maxAckPending;
    protected long maxPullWaiting;
    protected long maxBatch;
    protected long maxBytes;
    protected long numReplicas;

    protected @Nullable ZonedDateTime pauseUntil;

    protected boolean flowControl;
    protected boolean headersOnly;
    protected boolean memStorage;

    protected final List<String> filterSubjects;
    protected final List<Duration> backoff;
    protected final Map<String, String> metadata;

    protected final List<String> priorityGroups;
    protected PriorityPolicy priorityPolicy;
    protected @Nullable Duration priorityTimeout;

    // ----------------------------------------------------------------------------------------------------
    // CONSTRUCTORS
    // ----------------------------------------------------------------------------------------------------

    protected ConsumerCreator(boolean isPush) {
        this.isPush = isPush;

        deliverPolicy = DEFAULT_DELIVER_POLICY;
        ackPolicy = DEFAULT_ACK_POLICY;
        replayPolicy = DEFAULT_REPLAY_POLICY;
        priorityPolicy = DEFAULT_PRIORITY_POLICY;

        startSequence = ULONG_UNSET;
        rateLimit = ULONG_UNSET;

        maxDeliver = UNSET;
        maxAckPending = UNSET;
        maxPullWaiting = UNSET;
        maxBatch = UNSET;
        maxBytes = UNSET;
        numReplicas = UNSET;

        filterSubjects = new ArrayList<>();
        backoff = new ArrayList<>();
        metadata = new HashMap<>();
        priorityGroups = new ArrayList<>();
    }

    protected ConsumerCreator(ConsumerCreator<?> creator) {
        this.isPush = creator.isPush;
        this.deliverPolicy = creator.deliverPolicy;
        this.ackPolicy = creator.ackPolicy;
        this.replayPolicy = creator.replayPolicy;
        this.description = creator.description;
        this.durable = creator.durable;
        this.name = creator.name;
        this.deliverSubject = creator.deliverSubject;
        this.deliverGroup = creator.deliverGroup;
        this.sampleFrequency = creator.sampleFrequency;
        this.startTime = creator.startTime;
        this.ackWait = creator.ackWait;
        this.idleHeartbeat = creator.idleHeartbeat;
        this.maxExpires = creator.maxExpires;
        this.inactiveThreshold = creator.inactiveThreshold;
        this.startSequence = creator.startSequence;
        this.maxDeliver = creator.maxDeliver;
        this.rateLimit = creator.rateLimit;
        this.maxAckPending = creator.maxAckPending;
        this.maxPullWaiting = creator.maxPullWaiting;
        this.maxBatch = creator.maxBatch;
        this.maxBytes = creator.maxBytes;
        this.numReplicas = creator.numReplicas;
        this.pauseUntil = creator.pauseUntil;
        this.flowControl = creator.flowControl;
        this.headersOnly = creator.headersOnly;
        this.memStorage = creator.memStorage;
        this.priorityPolicy = creator.priorityPolicy;
        this.priorityTimeout = creator.priorityTimeout;

        this.filterSubjects = new ArrayList<>(creator.filterSubjects);
        this.backoff = new ArrayList<>(creator.backoff);
        this.metadata = new HashMap<>(creator.metadata);
        this.priorityGroups = new ArrayList<>(creator.priorityGroups);
    }

    // ----------------------------------------------------------------------------------------------------
    // JSON
    // ----------------------------------------------------------------------------------------------------

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, DESCRIPTION, description);
        addField(sb, DURABLE_NAME, durable);
        addField(sb, NAME, name);
        addField(sb, DELIVER_SUBJECT, deliverSubject);
        addField(sb, DELIVER_GROUP, deliverGroup);
        addEnum(sb, DELIVER_POLICY, deliverPolicy);
        addFieldWhenGtZero(sb, OPT_START_SEQ, startSequence);
        addField(sb, OPT_START_TIME, startTime);
        addEnum(sb, ACK_POLICY, ackPolicy);
        addFieldAsNanos(sb, ACK_WAIT, ackWait);
        addFieldWhenGtZero(sb, MAX_DELIVER, maxDeliver);
        addField(sb, MAX_ACK_PENDING, maxAckPending);
        addEnum(sb, REPLAY_POLICY, replayPolicy);
        addField(sb, SAMPLE_FREQ, sampleFrequency);
        addFieldWhenGtZero(sb, RATE_LIMIT_BPS, rateLimit);
        addFieldAsNanos(sb, IDLE_HEARTBEAT, idleHeartbeat);
        addField(sb, FLOW_CONTROL, flowControl);
        addField(sb, ApiConstants.MAX_WAITING, maxPullWaiting);
        addField(sb, HEADERS_ONLY, headersOnly);
        addField(sb, MAX_BATCH, maxBatch);
        addField(sb, MAX_BYTES, maxBytes);
        addFieldAsNanos(sb, MAX_EXPIRES, maxExpires);
        addFieldAsNanos(sb, INACTIVE_THRESHOLD, inactiveThreshold);
        addDurations(sb, BACKOFF, backoff);
        addField(sb, NUM_REPLICAS, numReplicas);
        addField(sb, PAUSE_UNTIL, pauseUntil);
        addField(sb, MEM_STORAGE, memStorage);
        addField(sb, METADATA, metadata);
        if (filterSubjects.size() == 1) {
            addField(sb, FILTER_SUBJECT, filterSubjects.get(0));
        }
        else {
            addStrings(sb, FILTER_SUBJECTS, filterSubjects);
        }
        addStrings(sb, PRIORITY_GROUPS, priorityGroups);
        addEnumWhenNot(sb, PRIORITY_POLICY, priorityPolicy, DEFAULT_PRIORITY_POLICY);
        addFieldAsNanos(sb, PRIORITY_TIMEOUT, priorityTimeout);

        return endJson(sb).toString();
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Whether this creator is for a push consumer
     * @return true if this is a creator for a push consumer
     */
    public boolean isPush() {
        return isPush;
    }

    /**
     * Free-form description of the consumer.
     * @return the description.
     */
    public @Nullable String getDescription() { return description; }

    /**
     * Durable name, which makes the consumer survive client restarts. Null for an ephemeral consumer.
     * @return name of the durable.
     */
    public @Nullable String getDurable() { return durable; }

    /**
     * The consumer's name, which the server uses to address it.
     * @return name of the consumer.
     */
    public @Nullable String getName() { return name; }

    /**
     * Subject the server pushes messages to. Set only for push consumers.
     * @return the deliver subject.
     */
    public @Nullable String getDeliverSubject() { return deliverSubject; }

    /**
     * Queue group sharing the deliver subject, so its members split the messages between them.
     * @return the deliver group.
     */
    public @Nullable String getDeliverGroup() { return deliverGroup; }

    /**
     * Where in the stream the consumer begins reading.
     * @return the deliver policy.
     */
    public DeliverPolicy getDeliverPolicy() { return deliverPolicy; }

    /**
     * Stream sequence to begin at, used when the deliver policy starts by sequence.
     * @return the start sequence.
     */
    public long getStartSequence() { return startSequence; }

    /**
     * Point in time to begin at, used when the deliver policy starts by time.
     * @return the start time.
     */
    public @Nullable ZonedDateTime getStartTime() { return startTime; }

    /**
     * Whether messages must be acknowledged, and whether an ack covers earlier ones.
     * @return the acknowledgment policy.
     */
    public AckPolicy getAckPolicy() { return ackPolicy; }

    /**
     * How long the server waits for an ack before redelivering the message.
     * @return the acknowledgment wait duration.
     */
    public @Nullable Duration getAckWait() { return ackWait; }

    /**
     * How many times a message may be delivered before the server stops trying.
     * @return the max delivery amount.
     */
    public long getMaxDeliver() { return maxDeliver; }

    /**
     * Gets the filter subject.
     * Returns null if there is not exactly one filter subject.
     * @return the first filter subject.
     */
    public @Nullable String getFilterSubject() {
        return filterSubjects.size() != 1 ? null : filterSubjects.get(0);
    }

    /**
     * Only messages on these subjects are delivered. Empty means the whole stream.
     * @return the filter subjects list
     */
    public List<String> getFilterSubjects() { return Collections.unmodifiableList(filterSubjects); }

    /**
     * Named priority groups this consumer serves.
     * @return the priority groups list
     */
    public List<String> getPriorityGroups() { return Collections.unmodifiableList(priorityGroups); }

    /**
     * Whether more than one filter subject is set, which older servers do not support.
     * @return true if there are multiple filter subjects
     */
    public boolean hasMultipleFilterSubjects() { return filterSubjects.size() > 1; }

    /**
     * Whether messages replay as fast as possible or at their original recorded pace.
     * @return the replay policy.
     */
    public ReplayPolicy getReplayPolicy() { return replayPolicy; }

    /**
     * Ceiling on delivery throughput, in bits per second.
     * @return the rate limit in bits per second
     */
    public long getRateLimit() { return rateLimit; }

    /**
     * How many unacknowledged messages may be outstanding before the server pauses delivery.
     * @return maximum ack pending.
     */
    public long getMaxAckPending() { return maxAckPending; }

    /**
     * Percentage of acknowledgements the server samples for monitoring.
     * @return the sample frequency.
     */
    public @Nullable String getSampleFrequency() { return sampleFrequency; }

    /**
     * How often the server sends a heartbeat while idle, so a stalled consumer can be detected.
     * @return the idle heart beat wait duration.
     */
    public @Nullable Duration getIdleHeartbeat() { return idleHeartbeat; }

    /**
     * Whether the server paces delivery using flow control messages.
     * @return the flow control flag
     */
    public boolean isFlowControl() { return flowControl; }

    /**
     * How many pull requests may be parked waiting for messages to arrive.
     * @return the max pull waiting
     */
    public long getMaxPullWaiting() { return maxPullWaiting; }

    /**
     * Whether only headers are delivered, omitting message bodies.
     * @return the headers only flag
     */
    public boolean isHeadersOnly() { return headersOnly; }

    /**
     * Whether the consumer's state is kept in memory rather than on file.
     * @return the mem storage flag
     */
    public boolean isMemStorage() { return memStorage; }

    /**
     * Largest batch a single pull request may ask for.
     * @return the max batch size
     */
    public long getMaxBatch() { return maxBatch; }

    /**
     * Largest total size a single pull request may ask for.
     * @return the max byte size
     */
    public long getMaxBytes() { return maxBytes; }

    /**
     * Longest expiry a single pull request may ask for.
     * @return the max expire
     */
    public @Nullable Duration getMaxExpires() { return maxExpires; }

    /**
     * How long the consumer may go unused before the server removes it.
     * @return the inactive threshold
     */
    public @Nullable Duration getInactiveThreshold() { return inactiveThreshold; }

    /**
     * Escalating redelivery delays, applied in order on successive redeliveries.
     * @return the backoff list
     */
    public List<Duration> getBackoff() { return Collections.unmodifiableList(backoff); }

    /**
     * User metadata carried on the consumer.
     * @return the metadata map
     */
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

    /**
     * How many replicas of the consumer state the cluster keeps.
     * @return the replicas count
     */
    public long getNumReplicas() { return numReplicas; }

    /**
     * The consumer is paused until this time, after which delivery resumes.
     * @return paused until time
     */
    public @Nullable ZonedDateTime getPauseUntil() { return pauseUntil; }

    /**
     * How the server chooses among members of a priority group.
     * @return the priority policy.
     */
    public PriorityPolicy getPriorityPolicy() { return priorityPolicy; }

    /**
     * How long a priority group member holds its claim before another may take over.
     * @return the priority timeout duration
     */
    public @Nullable Duration getPriorityTimeout() { return priorityTimeout; }

    // ----------------------------------------------------------------------------------------------------
    // PUBLIC SETTERS (common to all consumer types)
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the description
     * @param description the description
     * @return this instance for chaining.
     */
    public T description(String description) {
        this.description = emptyAsNull(description);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the delivery policy
     * @param policy the delivery policy.
     * @return this instance for chaining.
     */
    public T deliverPolicy(@Nullable DeliverPolicy policy) {
        this.deliverPolicy = policy == null ? DEFAULT_DELIVER_POLICY : policy;
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the start sequence to -1 clear.
     * @param sequence the start sequence
     * @return this instance for chaining.
     */
    public T startSequence(long sequence) {
        this.startSequence = normalizeULong(sequence);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the start time
     * @param startTime the start time
     * @return this instance for chaining.
     */
    public T startTime(@Nullable ZonedDateTime startTime) {
        this.startTime = startTime;
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the filter subjects.
     * Replaces any other filter subjects.
     * @param subjects one or more filter subjects
     * @return this instance for chaining.
     * @throws IllegalArgumentException if any filter subject is not a valid subject
     */
    public T subjects(String... subjects) {
        replaceAllStrings(this.filterSubjects, subjects, s -> validateSubjectTermStrict(s, "Subject"));
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the filter subjects.
     * Replaces any other filter subjects.
     * @param subjects the list of filter subjects
     * @return this instance for chaining.
     * @throws IllegalArgumentException if any filter subject is not a valid subject
     */
    public T subjects(@Nullable List<String> subjects) {
        replaceAllStrings(this.filterSubjects, subjects, s -> validateSubjectTermStrict(s, "Subject"));
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the replay policy
     * @param policy the replay policy.
     * @return this instance for chaining.
     */
    public T replayPolicy(@Nullable ReplayPolicy policy) {
        this.replayPolicy = policy == null ? DEFAULT_REPLAY_POLICY : policy;
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the sample frequency
     * @param frequency the frequency
     * @return this instance for chaining.
     */
    public T sampleFrequency(String frequency) {
        this.sampleFrequency = emptyAsNull(frequency);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the rate limit to -1 unset / clear.
     * @param bitsPerSecond bits per second to deliver
     * @return this instance for chaining.
     */
    public T rateLimit(long bitsPerSecond) {
        this.rateLimit = normalizeULong(bitsPerSecond);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the idle heart beat wait time
     * @param idleHeartbeat the idle heart beat duration
     * @return this instance for chaining.
     * @throws IllegalArgumentException if the idle heartbeat is greater than zero but below the 100ms minimum
     */
    public T idleHeartbeat(@Nullable Duration idleHeartbeat) {
        _idleHeartbeat(idleHeartbeat);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the idle heart beat wait time
     * @param idleHeartbeatMillis the idle heart beat duration in milliseconds
     * @return this instance for chaining.
     * @throws IllegalArgumentException if the idle heartbeat is greater than zero but below the 100ms minimum
     */
    public T idleHeartbeat(long idleHeartbeatMillis) {
        _idleHeartbeat(idleHeartbeatMillis);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the amount of time before the consumer is deemed inactive.
     * @param inactiveThreshold the threshold duration
     * @return this instance for chaining.
     */
    public T inactiveThreshold(@Nullable Duration inactiveThreshold) {
        this.inactiveThreshold = normalizeDuration(inactiveThreshold, null);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the amount of time before the consumer is deemed inactive.
     * A value less than 1 nullifies the threshold (server default applies).
     * @param inactiveThresholdMillis the threshold duration in milliseconds
     * @return this instance for chaining.
     */
    public T inactiveThreshold(long inactiveThresholdMillis) {
        this.inactiveThreshold = normalizeDuration(inactiveThresholdMillis, null);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the headers only flag
     * @param headersOnly the flag
     * @return this instance for chaining.
     */
    public T headersOnly(boolean headersOnly) {
        this.headersOnly = headersOnly;
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining.
     */
    public T metadata(@Nullable Map<String, String> metadata) {
        this.metadata.clear();
        if (metadata != null && !metadata.isEmpty()) {
            this.metadata.putAll(metadata);
        }
        //noinspection unchecked
        return (T)this;
    }

    // ----------------------------------------------------------------------------------------------------
    // PROTECTED DELEGATE SETTERS (for subclasses that selectively expose)
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the durable name.
     * @param durable the durable name
     * @throws IllegalArgumentException if the durable is not printable or contains '*', '.', '&gt;', '\' or '/'
     * @throws IllegalStateException if a name was already set and does not match
     */
    protected void _durable(@Nullable String durable) {
        this.durable = validateDurable(durable, false);
        validateMustMatchIfBothSupplied(name, durable, "Name", "Durable");
    }

    /**
     * Sets the consumer name.
     * @param name the consumer name
     * @throws IllegalArgumentException if the name is not printable or contains '*', '.', '&gt;', '\' or '/'
     * @throws IllegalStateException if a durable was already set and does not match
     */
    protected void _name(@Nullable String name) {
        this.name = validateConsumerName(name, false);
        validateMustMatchIfBothSupplied(name, durable, "Name", "Durable");
    }

    protected void _deliverSubject(@Nullable String subject) {
        this.deliverSubject = emptyAsNull(subject);
    }

    protected void _deliverGroup(@Nullable String group) {
        this.deliverGroup = validateSubjectTermStrict(group, "DeliverGroup", false);
    }

    protected void _ackPolicy(@Nullable AckPolicy policy) {
        this.ackPolicy = policy == null ? DEFAULT_ACK_POLICY : policy;
    }

    protected void _ackWait(@Nullable Duration timeout) {
        this.ackWait = normalizeDuration(timeout, null);
    }

    protected void _ackWait(long timeoutMillis) {
        this.ackWait = normalizeDuration(timeoutMillis, null);
    }

    protected void _maxDeliver(long maxDeliver) {
        this.maxDeliver = normalizeLong(maxDeliver, 1);
    }

    protected void _maxAckPending(long maxAckPending) {
        this.maxAckPending = normalizeLong(maxAckPending, 1);
    }

    protected void _idleHeartbeat(@Nullable Duration idleHeartbeat) {
        if (idleHeartbeat == null) {
            this.idleHeartbeat = null;
        }
        else {
            long nanos = idleHeartbeat.toNanos();
            if (nanos <= 0) {
                this.idleHeartbeat = DURATION_UNSET;
            }
            else if (nanos < MIN_IDLE_HEARTBEAT_NANOS) {
                throw new IllegalArgumentException("Idle Heartbeat must be greater than or equal to " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
            }
            else {
                this.idleHeartbeat = idleHeartbeat;
            }
        }
    }

    protected void _idleHeartbeat(long idleHeartbeatMillis) {
        if (idleHeartbeatMillis <= 0) {
            this.idleHeartbeat = DURATION_UNSET;
        }
        else if (idleHeartbeatMillis < MIN_IDLE_HEARTBEAT_MILLIS) {
            throw new IllegalArgumentException("Idle Heartbeat must be greater than or equal to " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
        }
        else {
            this.idleHeartbeat = Duration.ofMillis(idleHeartbeatMillis);
        }
    }

    /**
     * Sets flow control and the idle heartbeat.
     * @param idleHeartbeat the idle heartbeat duration
     * @throws IllegalArgumentException if the idle heartbeat is not set, or is below the 100ms minimum
     */
    protected void _flowControl(@Nullable Duration idleHeartbeat) {
        _idleHeartbeat(idleHeartbeat);
        // the field, not the parameter, which _idleHeartbeat clears for a non-positive value
        if (this.idleHeartbeat == null) {
            throw new IllegalArgumentException("Idle Heartbeat must set with flow control and must be at least " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
        }
        this.flowControl = true;
    }

    /**
     * Sets flow control and the idle heartbeat.
     * @param idleHeartbeatMillis the idle heartbeat duration in milliseconds
     * @throws IllegalArgumentException if the idle heartbeat is not set, or is below the 100ms minimum
     */
    protected void _flowControl(long idleHeartbeatMillis) {
        _idleHeartbeat(idleHeartbeatMillis);
        if (idleHeartbeat == null) {
            throw new IllegalArgumentException("Idle Heartbeat must set with flow control and must be at least " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
        }
        this.flowControl = true;
    }

    protected void _maxExpires(@Nullable Duration maxExpires) {
        this.maxExpires = normalizeDuration(maxExpires, null);
    }

    protected void _maxExpires(long maxExpiresMillis) {
        this.maxExpires = normalizeDuration(maxExpiresMillis, null);
    }

    protected void _maxPullWaiting(long maxPullWaiting) {
        this.maxPullWaiting = normalizeLong(maxPullWaiting, 1);
    }

    protected void _maxBatch(long maxBatch) {
        this.maxBatch = normalizeLong(maxBatch, 1);
    }

    protected void _maxBytes(@Nullable Long maxBytes) {
        this.maxBytes = normalizeLong(maxBytes, 1);
    }

    protected void _maxBytes(long maxBytes) {
        this.maxBytes = normalizeLong(maxBytes, 1);
    }

    /**
     * Sets the number of replicas.
     * @param numReplicas the number of replicas
     * @throws IllegalArgumentException if the number of replicas is set but not between 1 and 5
     */
    protected void _numReplicas(int numReplicas) {
        this.numReplicas = numReplicas < 1 ? UNSET : validateNumberOfReplicas(numReplicas);
    }

    protected void _pauseUntil(@Nullable ZonedDateTime pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    protected void _memStorage(boolean memStorage) {
        this.memStorage = memStorage;
    }

    /**
     * Sets the backoff durations.
     * @param backoffs one or more backoff durations
     * @throws IllegalArgumentException if any backoff value is negative
     */
    protected void _backoff(Duration... backoffs) {
        backoff.clear();
        for (Duration d : backoffs) {
            if (d.toNanos() < 0) {
                throw new IllegalArgumentException("Backoff must be 0 or greater.");
            }
            this.backoff.add(d);
        }
    }

    /**
     * Sets the backoff durations.
     * @param backoffMillis one or more backoff durations in milliseconds
     * @throws IllegalArgumentException if any backoff value is negative
     */
    protected void _backoff(long... backoffMillis) {
        backoff.clear();
        for (long l : backoffMillis) {
            if (l < 0) {
                throw new IllegalArgumentException("Backoff must be 0 or greater.");
            }
            this.backoff.add(Duration.ofMillis(l));
        }
    }

    protected void _priorityGroups(String... priorityGroups) {
        replaceAllStrings(this.priorityGroups, priorityGroups);
    }

    protected void _priorityGroups(@Nullable List<String> priorityGroups) {
        replaceAllStrings(this.priorityGroups, priorityGroups);
    }

    protected void _priorityPolicy(@Nullable PriorityPolicy policy) {
        this.priorityPolicy = policy == null ? DEFAULT_PRIORITY_POLICY : policy;
    }

    protected void _priorityTimeout(@Nullable Duration priorityTimeout) {
        this.priorityTimeout = normalizeDuration(priorityTimeout, null);
    }

    protected void _priorityTimeout(long priorityTimeoutMillis) {
        this.priorityTimeout = normalizeDuration(priorityTimeoutMillis, null);
    }

    // ----------------------------------------------------------------------------------------------------
    // EQUALS / HASHCODE
    // ----------------------------------------------------------------------------------------------------

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumerCreator<?> that = (ConsumerCreator<?>) o;
        return isPush == that.isPush
            && startSequence == that.startSequence
            && rateLimit == that.rateLimit
            && maxDeliver == that.maxDeliver
            && maxAckPending == that.maxAckPending
            && maxPullWaiting == that.maxPullWaiting
            && maxBatch == that.maxBatch
            && maxBytes == that.maxBytes
            && numReplicas == that.numReplicas
            && flowControl == that.flowControl
            && headersOnly == that.headersOnly
            && memStorage == that.memStorage
            && deliverPolicy.equals(that.deliverPolicy)
            && ackPolicy.equals(that.ackPolicy)
            && replayPolicy.equals(that.replayPolicy)
            && priorityPolicy.equals(that.priorityPolicy)
            && Objects.equals(description, that.description)
            && Objects.equals(durable, that.durable)
            && Objects.equals(name, that.name)
            && Objects.equals(deliverSubject, that.deliverSubject)
            && Objects.equals(deliverGroup, that.deliverGroup)
            && Objects.equals(sampleFrequency, that.sampleFrequency)
            && Objects.equals(startTime, that.startTime)
            && Objects.equals(ackWait, that.ackWait)
            && Objects.equals(idleHeartbeat, that.idleHeartbeat)
            && Objects.equals(maxExpires, that.maxExpires)
            && Objects.equals(inactiveThreshold, that.inactiveThreshold)
            && Objects.equals(pauseUntil, that.pauseUntil)
            && Objects.equals(priorityTimeout, that.priorityTimeout)
            && filterSubjects.equals(that.filterSubjects)
            && backoff.equals(that.backoff)
            && metadata.equals(that.metadata)
            && priorityGroups.equals(that.priorityGroups);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(isPush);
        result = 31 * result + deliverPolicy.hashCode();
        result = 31 * result + ackPolicy.hashCode();
        result = 31 * result + replayPolicy.hashCode();
        result = 31 * result + priorityPolicy.hashCode();
        result = 31 * result + Objects.hashCode(description);
        result = 31 * result + Objects.hashCode(durable);
        result = 31 * result + Objects.hashCode(name);
        result = 31 * result + Objects.hashCode(deliverSubject);
        result = 31 * result + Objects.hashCode(deliverGroup);
        result = 31 * result + Objects.hashCode(sampleFrequency);
        result = 31 * result + Objects.hashCode(startTime);
        result = 31 * result + Objects.hashCode(ackWait);
        result = 31 * result + Objects.hashCode(idleHeartbeat);
        result = 31 * result + Objects.hashCode(maxExpires);
        result = 31 * result + Objects.hashCode(inactiveThreshold);
        result = 31 * result + Long.hashCode(startSequence);
        result = 31 * result + Long.hashCode(rateLimit);
        result = 31 * result + Long.hashCode(maxDeliver);
        result = 31 * result + Long.hashCode(maxAckPending);
        result = 31 * result + Long.hashCode(maxPullWaiting);
        result = 31 * result + Long.hashCode(maxBatch);
        result = 31 * result + Long.hashCode(maxBytes);
        result = 31 * result + Long.hashCode(numReplicas);
        result = 31 * result + Objects.hashCode(pauseUntil);
        result = 31 * result + Boolean.hashCode(flowControl);
        result = 31 * result + Boolean.hashCode(headersOnly);
        result = 31 * result + Boolean.hashCode(memStorage);
        result = 31 * result + filterSubjects.hashCode();
        result = 31 * result + backoff.hashCode();
        result = 31 * result + metadata.hashCode();
        result = 31 * result + priorityGroups.hashCode();
        result = 31 * result + Objects.hashCode(priorityTimeout);
        return result;
    }
}
