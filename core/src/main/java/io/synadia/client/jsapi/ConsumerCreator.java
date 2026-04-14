package io.synadia.client.jsapi;

import io.nats.json.JsonSerializable;
import io.synadia.client.support.ApiConstants;
import io.synadia.client.support.JetStreamApiUtils;
import io.synadia.client.support.Validator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.JetStreamApiUtils.*;
import static io.synadia.client.support.JetStreamClientError.JsConsumerNameDurableMismatch;
import static io.synadia.client.support.Validator.*;

/**
 * Base class for consumer creators, providing setters common to all consumer types
 * (full, ephemeral, and ordered).
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
     * Constant representing the minimum max deliver
     */
    public static final int MAX_DELIVER_MIN = 1;

    /**
     * Constant representing the minimum idle heartbeat in nanos
     */
    public static final long MIN_IDLE_HEARTBEAT_NANOS = MIN_IDLE_HEARTBEAT.toNanos();

    /**
     * Constant representing the minimum idle heartbeat in milliseconds
     */
    public static final long MIN_IDLE_HEARTBEAT_MILLIS = MIN_IDLE_HEARTBEAT.toMillis();

    protected final String stream;
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

    protected ConsumerCreator(String stream, boolean isPush) {
        this.stream = Validator.validateStreamName(stream, true);
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
        numReplicas = 0;

        filterSubjects = new ArrayList<>();
        backoff = new ArrayList<>();
        metadata = new HashMap<>();
        priorityGroups = new ArrayList<>();
    }

    protected ConsumerCreator(ConsumerCreator<?> cc) {
        this.stream = cc.stream;
        this.isPush = cc.isPush;
        this.deliverPolicy = cc.deliverPolicy;
        this.ackPolicy = cc.ackPolicy;
        this.replayPolicy = cc.replayPolicy;
        this.description = cc.description;
        this.durable = cc.durable;
        this.name = cc.name;
        this.deliverSubject = cc.deliverSubject;
        this.deliverGroup = cc.deliverGroup;
        this.sampleFrequency = cc.sampleFrequency;
        this.startTime = cc.startTime;
        this.ackWait = cc.ackWait;
        this.idleHeartbeat = cc.idleHeartbeat;
        this.maxExpires = cc.maxExpires;
        this.inactiveThreshold = cc.inactiveThreshold;
        this.startSequence = cc.startSequence;
        this.maxDeliver = cc.maxDeliver;
        this.rateLimit = cc.rateLimit;
        this.maxAckPending = cc.maxAckPending;
        this.maxPullWaiting = cc.maxPullWaiting;
        this.maxBatch = cc.maxBatch;
        this.maxBytes = cc.maxBytes;
        this.numReplicas = cc.numReplicas;
        this.pauseUntil = cc.pauseUntil;
        this.flowControl = cc.flowControl;
        this.headersOnly = cc.headersOnly;
        this.memStorage = cc.memStorage;
        this.priorityPolicy = cc.priorityPolicy;
        this.priorityTimeout = cc.priorityTimeout;

        this.filterSubjects = new ArrayList<>(cc.filterSubjects);
        this.backoff = new ArrayList<>(cc.backoff);
        this.metadata = new HashMap<>(cc.metadata);
        this.priorityGroups = new ArrayList<>(cc.priorityGroups);
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
        addEnumWhenNot(sb, DELIVER_POLICY, deliverPolicy, DEFAULT_DELIVER_POLICY);
        addFieldWhenGtZero(sb, OPT_START_SEQ, startSequence);
        addField(sb, OPT_START_TIME, startTime);
        addEnumWhenNot(sb, ACK_POLICY, ackPolicy, DEFAULT_ACK_POLICY);
        addFieldAsNanos(sb, ACK_WAIT, ackWait);
        addFieldWhenGtZero(sb, MAX_DELIVER, maxDeliver);
        addField(sb, MAX_ACK_PENDING, maxAckPending);
        addEnumWhenNot(sb, REPLAY_POLICY, replayPolicy, DEFAULT_REPLAY_POLICY);
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
     * The stream the consumer will operate on.
     * @return the stream
     */
    public String getStream() {
        return stream;
    }

    /**
     * Whether this creator is for a push consumer
     * @return
     */
    public boolean isPush() {
        return isPush;
    }

    /** @return the description. */
    @Nullable public String getDescription() { return description; }

    /** @return name of the durable. */
    @Nullable public String getDurable() { return durable; }

    /** @return name of the consumer. */
    @Nullable public String getName() { return name; }

    /** @return the deliver subject. */
    @Nullable public String getDeliverSubject() { return deliverSubject; }

    /** @return the deliver group. */
    @Nullable public String getDeliverGroup() { return deliverGroup; }

    /** @return the deliver policy. */
    @Nullable public DeliverPolicy getDeliverPolicy() { return deliverPolicy; }

    /** @return the start sequence. */
    public long getStartSequence() { return startSequence; }

    /** @return the start time. */
    @Nullable public ZonedDateTime getStartTime() { return startTime; }

    /** @return the acknowledgment policy. */
    @Nullable public AckPolicy getAckPolicy() { return ackPolicy; }

    /** @return the acknowledgment wait duration. */
    @Nullable public Duration getAckWait() { return ackWait; }

    /** @return the max delivery amount. */
    public long getMaxDeliver() { return maxDeliver; }

    /**
     * Gets the filter subject.
     * Returns null if there is not exactly one filter subject.
     * @return the first filter subject.
     */
    @Nullable public String getFilterSubject() {
        return filterSubjects.size() != 1 ? null : filterSubjects.get(0);
    }

    /** @return the filter subjects list */
    public List<String> getFilterSubjects() { return Collections.unmodifiableList(filterSubjects); }

    /** @return the priority groups list */
    public List<String> getPriorityGroups() { return Collections.unmodifiableList(priorityGroups); }

    /** @return true if there are multiple filter subjects */
    public boolean hasMultipleFilterSubjects() { return filterSubjects.size() > 1; }

    /** @return the replay policy. */
    @Nullable public ReplayPolicy getReplayPolicy() { return replayPolicy; }

    /** @return the rate limit in bits per second */
    public long getRateLimit() { return rateLimit; }

    /** @return maximum ack pending. */
    public long getMaxAckPending() { return maxAckPending; }

    /** @return the sample frequency. */
    @Nullable public String getSampleFrequency() { return sampleFrequency; }

    /** @return the idle heart beat wait duration. */
    @Nullable public Duration getIdleHeartbeat() { return idleHeartbeat; }

    /** @return the flow control flag */
    public boolean isFlowControl() { return flowControl; }

    /** @return the max pull waiting */
    public long getMaxPullWaiting() { return maxPullWaiting; }

    /** @return the headers only flag */
    public boolean isHeadersOnly() { return headersOnly; }

    /** @return the mem storage flag */
    public boolean isMemStorage() { return memStorage; }

    /** @return the max batch size */
    public long getMaxBatch() { return maxBatch; }

    /** @return the max byte size */
    public long getMaxBytes() { return maxBytes; }

    /** @return the max expire */
    @Nullable public Duration getMaxExpires() { return maxExpires; }

    /** @return the inactive threshold */
    @Nullable public Duration getInactiveThreshold() { return inactiveThreshold; }

    /** @return the backoff list */
    public List<Duration> getBackoff() { return Collections.unmodifiableList(backoff); }

    /** @return the metadata map */
    public Map<String, String> getMetadata() { return Collections.unmodifiableMap(metadata); }

    /** @return the replicas count */
    public long getNumReplicas() { return numReplicas; }

    /** @return paused until time */
    @Nullable public ZonedDateTime getPauseUntil() { return pauseUntil; }

    /** @return the priority policy. */
    @Nullable public PriorityPolicy getPriorityPolicy() { return priorityPolicy; }

    /** @return the priority timeout duration */
    @Nullable public Duration getPriorityTimeout() { return priorityTimeout; }

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
     * Sets the start sequence or null to unset / clear.
     * @param sequence the start sequence
     * @return this instance for chaining.
     */
    public T startSequence(Long sequence) {
        this.startSequence = normalizeUlong(sequence);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the start sequence
     * @param sequence the start sequence
     * @return this instance for chaining.
     */
    public T startSequence(long sequence) {
        this.startSequence = normalizeUlong(sequence);
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
     * Sets the filter subject.
     * Replaces any other filter subjects.
     * @param filterSubject the filter subject
     * @return this instance for chaining.
     */
    public T filterSubject(String filterSubject) {
        this.filterSubjects.clear();
        if (!nullOrEmpty(filterSubject)) {
            this.filterSubjects.add(filterSubject);
        }
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the filter subjects.
     * Replaces any other filter subjects.
     * @param filterSubjects one or more filter subjects
     * @return this instance for chaining.
     */
    public T filterSubjects(String... filterSubjects) {
        this.filterSubjects.clear();
        if (!nullOrEmpty(filterSubjects)) {
            for (String fs : filterSubjects) {
                if (!nullOrEmpty(fs)) {
                    this.filterSubjects.add(fs);
                }
            }
        }
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the filter subjects.
     * Replaces any other filter subjects.
     * @param filterSubjects the list of filter subjects
     * @return this instance for chaining.
     */
    public T filterSubjects(List<String> filterSubjects) {
        this.filterSubjects.clear();
        if (!nullOrEmpty(filterSubjects)) {
            for (String fs : filterSubjects) {
                if (!nullOrEmpty(fs)) {
                    this.filterSubjects.add(fs);
                }
            }
        }
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
     * Set the rate limit or null to unset / clear.
     * @param bitsPerSecond bits per second to deliver
     * @return this instance for chaining.
     */
    public T rateLimit(Long bitsPerSecond) {
        this.rateLimit = normalizeUlong(bitsPerSecond);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the rate limit
     * @param bitsPerSecond bits per second to deliver
     * @return this instance for chaining.
     */
    public T rateLimit(long bitsPerSecond) {
        this.rateLimit = normalizeUlong(bitsPerSecond);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the idle heart beat wait time
     * @param idleHeartbeat the idle heart beat duration
     * @return this instance for chaining.
     */
    public T idleHeartbeat(Duration idleHeartbeat) {
        _idleHeartbeat(idleHeartbeat);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the idle heart beat wait time
     * @param idleHeartbeatMillis the idle heart beat duration in milliseconds
     * @return this instance for chaining.
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
    public T inactiveThreshold(Duration inactiveThreshold) {
        this.inactiveThreshold = JetStreamApiUtils.normalizeDuration(inactiveThreshold);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Sets the amount of time before the consumer is deemed inactive.
     * @param inactiveThreshold the threshold duration in milliseconds
     * @return this instance for chaining.
     */
    public T inactiveThreshold(long inactiveThreshold) {
        this.inactiveThreshold = normalizeDuration(inactiveThreshold);
        //noinspection unchecked
        return (T)this;
    }

    /**
     * Set the headers only flag
     * @param headersOnly the flag
     * @return this instance for chaining.
     */
    public T headersOnly(Boolean headersOnly) {
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

    protected void _durable(String durable) {
        this.durable = validateDurable(emptyAsNull(durable), false);
        validateMustMatchIfBothSupplied(name, durable, JsConsumerNameDurableMismatch);
    }

    protected void _name(String name) {
        this.name = validateConsumerName(emptyAsNull(name), false);
        validateMustMatchIfBothSupplied(name, durable, JsConsumerNameDurableMismatch);
    }

    protected void _deliverSubject(String subject) {
        this.deliverSubject = emptyAsNull(subject);
    }

    protected void _deliverGroup(String group) {
        this.deliverGroup = emptyAsNull(group);
    }

    protected void _ackPolicy(@Nullable AckPolicy policy) {
        this.ackPolicy = policy == null ? DEFAULT_ACK_POLICY : policy;
    }

    protected void _ackWait(Duration timeout) {
        this.ackWait = JetStreamApiUtils.normalizeDuration(timeout);
    }

    protected void _ackWait(long timeoutMillis) {
        this.ackWait = normalizeDuration(timeoutMillis);
    }

    protected void _maxDeliver(long maxDeliver) {
        this.maxDeliver = normalizeLong(maxDeliver, MAX_DELIVER_MIN);
    }

    protected void _maxAckPending(Long maxAckPending) {
        this.maxAckPending = normalizeLong(maxAckPending, STANDARD_MIN);
    }

    protected void _maxAckPending(long maxAckPending) {
        this.maxAckPending = normalizeLong(maxAckPending, STANDARD_MIN);
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
                throw new IllegalArgumentException("Duration must be greater than or equal to " + MIN_IDLE_HEARTBEAT_NANOS + " nanos.");
            }
            else {
                this.idleHeartbeat = idleHeartbeat;
            }
        }
    }

    protected void _idleHeartbeat(long idleHeartbeatMillis) {
        if (idleHeartbeatMillis <= 0) {
            this.idleHeartbeat = Duration.ZERO;
        }
        else if (idleHeartbeatMillis < MIN_IDLE_HEARTBEAT_MILLIS) {
            throw new IllegalArgumentException("Duration must be greater than or equal to " + MIN_IDLE_HEARTBEAT_MILLIS + " milliseconds.");
        }
        else {
            this.idleHeartbeat = Duration.ofMillis(idleHeartbeatMillis);
        }
    }

    protected void _flowControl(Duration idleHeartbeat) {
        this.flowControl = true;
        _idleHeartbeat(idleHeartbeat);
    }

    protected void _flowControl(long idleHeartbeatMillis) {
        this.flowControl = true;
        _idleHeartbeat(idleHeartbeatMillis);
    }

    protected void _maxExpires(Duration maxExpires) {
        this.maxExpires = JetStreamApiUtils.normalizeDuration(maxExpires);
    }

    protected void _maxExpires(long maxExpires) {
        this.maxExpires = normalizeDuration(maxExpires);
    }

    protected void _maxPullWaiting(Long maxPullWaiting) {
        this.maxPullWaiting = normalizeLong(maxPullWaiting, STANDARD_MIN);
    }

    protected void _maxPullWaiting(long maxPullWaiting) {
        this.maxPullWaiting = normalizeLong(maxPullWaiting, STANDARD_MIN);
    }

    protected void _maxBatch(Long maxBatch) {
        this.maxBatch = normalizeLong(maxBatch, STANDARD_MIN);
    }

    protected void _maxBatch(long maxBatch) {
        this.maxBatch = normalizeLong(maxBatch, STANDARD_MIN);
    }

    protected void _maxBytes(Long maxBytes) {
        this.maxBytes = normalizeLong(maxBytes);
    }

    protected void _maxBytes(long maxBytes) {
        this.maxBytes = normalizeLong(maxBytes);
    }

    protected void _numReplicas(int numReplicas) {
        this.numReplicas = numReplicas <= 0 ? 0 : validateNumberOfReplicas(numReplicas);
    }

    protected void _pauseUntil(ZonedDateTime pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    protected void _memStorage(Boolean memStorage) {
        this.memStorage = memStorage;
    }

    protected void _backoff(Duration @Nullable ... backoffs) {
        backoff.clear();
        if (backoffs != null) {
            for (Duration d : backoffs) {
                if (d.toNanos() < 0) {
                    throw new IllegalArgumentException("Backoff must be 0 or greater.");
                }
                this.backoff.add(d);
            }
        }
    }

    protected void _backoff(long @Nullable ... backoffMillis) {
        backoff.clear();
        if (backoffMillis != null) {
            for (long l : backoffMillis) {
                if (l < 0) {
                    throw new IllegalArgumentException("Backoff must be 0 or greater.");
                }
                this.backoff.add(Duration.ofMillis(l));
            }
        }
    }

    protected void _priorityGroups(String... priorityGroups) {
        this.priorityGroups.clear();
        if (!nullOrEmpty(priorityGroups)) {
            addPriorityGroupsInternal(Arrays.asList(priorityGroups));
        }
    }

    protected void _priorityGroups(List<String> priorityGroups) {
        this.priorityGroups.clear();
        if (!nullOrEmpty(priorityGroups)) {
            addPriorityGroupsInternal(priorityGroups);
        }
    }

    private void addPriorityGroupsInternal(List<String> priorityGroups) {
        for (String pg : priorityGroups) {
            if (!nullOrEmpty(pg)) {
                this.priorityGroups.add(pg);
            }
        }
    }

    protected void _priorityPolicy(@Nullable PriorityPolicy policy) {
        this.priorityPolicy = policy == null ? DEFAULT_PRIORITY_POLICY : policy;
    }

    protected void _priorityTimeout(Duration priorityTimeout) {
        this.priorityTimeout = JetStreamApiUtils.normalizeDuration(priorityTimeout);
    }

    protected void _priorityTimeout(long priorityTimeoutMillis) {
        this.priorityTimeout = normalizeDuration(priorityTimeoutMillis);
    }
}
