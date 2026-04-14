package io.synadia.client.jsapi;

import io.nats.json.JsonSerializable;
import io.nats.json.JsonWriteUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.Validator.*;

/**
 * StreamCreator is used to create or update a stream on the server.
 * The constructor requires a stream name.
 */
@NullMarked
public class StreamCreator implements JsonSerializable {

    public static final RetentionPolicy DEFAULT_RETENTION_POLICY = RetentionPolicy.Limits;
    public static final CompressionOption DEFAULT_COMPRESSION_OPTION = CompressionOption.None;
    public static final StorageType DEFAULT_STORAGE_TYPE = StorageType.File;
    public static final DiscardPolicy DEFAULT_DISCARD_POLICY = DiscardPolicy.Old;
    public static final PersistMode DEFAULT_PERSIST_MODE = PersistMode.Default;

    private final String name;
    private @Nullable String description;
    private final List<String> subjects;
    private RetentionPolicy retentionPolicy;
    private CompressionOption compressionOption;
    private long maxConsumers;
    private long maxMsgs;
    private long maxMsgsPerSubject;
    private long maxBytes;
    private Duration maxAge;
    private int maxMessageSize;
    private StorageType storageType;
    private int replicas;
    private boolean noAck;
    private @Nullable String templateOwner;
    private DiscardPolicy discardPolicy;
    private Duration duplicateWindow;
    private @Nullable PlacementCreator placementCreator;
    private @Nullable RepublishCreator republishCreator;
    private @Nullable SubjectTransformCreator subjectTransformCreator;
    private @Nullable ConsumerLimitsCreator consumerLimitsCreator;
    private @Nullable MirrorCreator mirrorCreator;
    private final List<SourceCreator> sourceCreators;
    private boolean sealed;
    private boolean allowRollup;
    private boolean allowDirect;
    private boolean mirrorDirect;
    private boolean denyDelete;
    private boolean denyPurge;
    private boolean discardNewPerSubject;
    private final Map<String, String> metadata;
    private long firstSequence;
    private @Nullable Duration subjectDeleteMarkerTtl;
    private boolean allowMessageTtl;
    private boolean allowMsgSchedules;
    private boolean allowMessageCounter;
    private boolean allowAtomicPublish;
    private @Nullable PersistMode persistMode;

    /**
     * Construct a StreamCreator with the required stream name.
     * @param name the stream name
     */
    public StreamCreator(String name) {
        this.name = validateStreamName(name, true);
        retentionPolicy = DEFAULT_RETENTION_POLICY;
        compressionOption = DEFAULT_COMPRESSION_OPTION;
        maxConsumers = -1;
        maxMsgs = -1;
        maxMsgsPerSubject = -1;
        maxBytes = -1;
        maxAge = Duration.ZERO;
        maxMessageSize = -1;
        storageType = DEFAULT_STORAGE_TYPE;
        replicas = 1;
        discardPolicy = DiscardPolicy.Old;
        duplicateWindow = Duration.ZERO;
        firstSequence = 1;

        subjects = new ArrayList<>();
        sourceCreators = new ArrayList<>();
        metadata = new HashMap<>();
    }

    /**
     * Construct a StreamCreator by copying an existing StreamCreator.
     * @param sc the stream creator to copy
     */
    public StreamCreator(StreamCreator sc) {
        this.name = sc.name;
        this.description = sc.description;
        this.retentionPolicy = sc.retentionPolicy;
        this.compressionOption = sc.compressionOption;
        this.maxConsumers = sc.maxConsumers;
        this.maxMsgs = sc.maxMsgs;
        this.maxMsgsPerSubject = sc.maxMsgsPerSubject;
        this.maxBytes = sc.maxBytes;
        this.maxAge = sc.maxAge;
        this.maxMessageSize = sc.maxMessageSize;
        this.storageType = sc.storageType;
        this.replicas = sc.replicas;
        this.noAck = sc.noAck;
        this.templateOwner = sc.templateOwner;
        this.discardPolicy = sc.discardPolicy;
        this.duplicateWindow = sc.duplicateWindow;
        this.placementCreator = sc.placementCreator;
        this.republishCreator = sc.republishCreator;
        this.subjectTransformCreator = sc.subjectTransformCreator;
        this.consumerLimitsCreator = sc.consumerLimitsCreator;
        this.mirrorCreator = sc.mirrorCreator;
        this.sealed = sc.sealed;
        this.allowRollup = sc.allowRollup;
        this.allowDirect = sc.allowDirect;
        this.mirrorDirect = sc.mirrorDirect;
        this.denyDelete = sc.denyDelete;
        this.denyPurge = sc.denyPurge;
        this.discardNewPerSubject = sc.discardNewPerSubject;
        this.firstSequence = sc.firstSequence;
        this.subjectDeleteMarkerTtl = sc.subjectDeleteMarkerTtl;
        this.allowMessageTtl = sc.allowMessageTtl;
        this.allowMsgSchedules = sc.allowMsgSchedules;
        this.allowMessageCounter = sc.allowMessageCounter;
        this.allowAtomicPublish = sc.allowAtomicPublish;
        this.persistMode = sc.persistMode;
        this.subjects = new ArrayList<>(sc.subjects);
        this.sourceCreators = new ArrayList<>(sc.sourceCreators);
        this.metadata = new HashMap<>(sc.metadata);
    }


    /**
     * Construct a StreamCreator from an existing StreamConfiguration (server response).
     * Copies all fields via getters, converting lazy read-only types to their Creator equivalents.
     * @param sc the stream configuration to copy from
     */
    public StreamCreator(StreamConfiguration sc) {
        this.name = sc.getName();
        this.description = sc.getDescription();
        this.retentionPolicy = sc.getRetentionPolicy();
        this.compressionOption = sc.getCompressionOption();
        this.maxConsumers = sc.getMaxConsumers();
        this.maxMsgs = sc.getMaxMessages();
        this.maxMsgsPerSubject = sc.getMaxMessagesPerSubject();
        this.maxBytes = sc.getMaxBytes();
        this.maxAge = sc.getMaxAge();
        this.maxMessageSize = sc.getMaxMessageSize();
        this.storageType = sc.getStorageType();
        this.replicas = sc.getReplicas();
        this.noAck = sc.getNoAck();
        this.templateOwner = sc.getTemplateOwner();
        this.discardPolicy = sc.getDiscardPolicy();
        this.duplicateWindow = sc.getDuplicateWindow();
        this.sealed = sc.getSealed();
        this.allowRollup = sc.getAllowRollup();
        this.allowDirect = sc.getAllowDirect();
        this.mirrorDirect = sc.getMirrorDirect();
        this.denyDelete = sc.getDenyDelete();
        this.denyPurge = sc.getDenyPurge();
        this.discardNewPerSubject = sc.isDiscardNewPerSubject();
        this.firstSequence = sc.getFirstSequence();
        this.subjectDeleteMarkerTtl = sc.getSubjectDeleteMarkerTtl();
        this.allowMessageTtl = sc.getAllowMessageTtl();
        this.allowMsgSchedules = sc.getAllowMessageSchedules();
        this.allowMessageCounter = sc.getAllowMessageCounter();
        this.allowAtomicPublish = sc.getAllowAtomicPublish();
        this.persistMode = sc.getPersistMode();
        this.subjects = new ArrayList<>(sc.getSubjects());
        this.metadata = new HashMap<>(sc.getMetadata());

        Placement p = sc.getPlacement();
        this.placementCreator = p == null ? null : new PlacementCreator(p);

        Republish r = sc.getRepublish();
        this.republishCreator = r == null ? null : new RepublishCreator(r);

        SubjectTransform st = sc.getSubjectTransform();
        this.subjectTransformCreator = st == null ? null : new SubjectTransformCreator(st);

        ConsumerLimits cl = sc.getConsumerLimits();
        this.consumerLimitsCreator = cl == null ? null : new ConsumerLimitsCreator(cl);

        Mirror m = sc.getMirror();
        this.mirrorCreator = m == null ? null : new MirrorCreator(m);

        this.sourceCreators = new ArrayList<>();
        for (Source s : sc.getSources()) {
            this.sourceCreators.add(new SourceCreator(s));
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // JSON
    // ----------------------------------------------------------------------------------------------------

    /**
     * Returns a JSON representation of this stream configuration.
     * @return JSON stream configuration JSON string
     */
    @Override
    public String toJson() {
        StringBuilder sb = beginJson();

        addField(sb, NAME, name);
        addField(sb, DESCRIPTION, description);
        addStrings(sb, SUBJECTS, subjects);
        addEnumWhenNot(sb, RETENTION, retentionPolicy, DEFAULT_RETENTION_POLICY);
        addEnumWhenNot(sb, COMPRESSION, compressionOption, DEFAULT_COMPRESSION_OPTION);
        addField(sb, MAX_CONSUMERS, maxConsumers);
        addField(sb, MAX_MSGS, maxMsgs);
        addField(sb, MAX_MSGS_PER_SUB, maxMsgsPerSubject);
        addField(sb, MAX_BYTES, maxBytes);
        addFieldAsNanos(sb, MAX_AGE, maxAge);
        addField(sb, MAX_MSG_SIZE, maxMessageSize);
        addEnumWhenNot(sb, STORAGE, storageType, DEFAULT_STORAGE_TYPE);
        addField(sb, NUM_REPLICAS, replicas);
        addField(sb, NO_ACK, noAck);
        addField(sb, TEMPLATE_OWNER, templateOwner);
        addEnumWhenNot(sb, DISCARD, discardPolicy, DEFAULT_DISCARD_POLICY);
        addFieldAsNanos(sb, DUPLICATE_WINDOW, duplicateWindow);
        addField(sb, PLACEMENT, placementCreator);
        addField(sb, REPUBLISH, republishCreator);
        addField(sb, SUBJECT_TRANSFORM, subjectTransformCreator);
        addField(sb, CONSUMER_LIMITS, consumerLimitsCreator);
        JsonWriteUtils.addField(sb, MIRROR, mirrorCreator);
        addJsons(sb, SOURCES, sourceCreators);
        addField(sb, SEALED, sealed);
        addField(sb, ALLOW_ROLLUP_HDRS, allowRollup);
        addField(sb, ALLOW_DIRECT, allowDirect);
        addField(sb, MIRROR_DIRECT, mirrorDirect);
        addField(sb, DENY_DELETE, denyDelete);
        addField(sb, DENY_PURGE, denyPurge);
        addField(sb, DISCARD_NEW_PER_SUBJECT, discardNewPerSubject);
        addField(sb, METADATA, metadata);
        addFieldWhenGreaterThan(sb, FIRST_SEQ, firstSequence, 1);
        addFieldAsNanos(sb, SUBJECT_DELETE_MARKER_TTL, subjectDeleteMarkerTtl);
        addField(sb, ALLOW_MSG_TTL, allowMessageTtl);
        addField(sb, ALLOW_MSG_SCHEDULES, allowMsgSchedules);
        addField(sb, ALLOW_MSG_COUNTER, allowMessageCounter);
        addField(sb, ALLOW_ATOMIC, allowAtomicPublish);
        addEnumWhenNot(sb, PERSIST_MODE, persistMode, DEFAULT_PERSIST_MODE);
        return endJson(sb).toString();
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Gets the name of this stream configuration.
     * @return the name of the stream.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the description of this stream configuration.
     * @return the description of the stream.
     */
    @Nullable
    public String getDescription() {
        return description;
    }

    /**
     * Gets the subjects for this stream configuration.
     * @return the subjects of the stream.
     */
    public List<String> getSubjects() {
        return Collections.unmodifiableList(subjects);
    }

    /**
     * Gets the retention policy for this stream configuration.
     * @return the retention policy for this stream.
     */
    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    /**
     * Gets the compression option for this stream configuration.
     * @return the compression option for this stream.
     */
    public CompressionOption getCompressionOption() {
        return compressionOption;
    }

    /**
     * Gets the maximum number of consumers for this stream configuration.
     * @return the maximum number of consumers for this stream.
     */
    public long getMaxConsumers() {
        return maxConsumers;
    }

    /**
     * Gets the maximum messages for this stream configuration.
     * @return the maximum number of messages for this stream.
     */
    public long getMaxMessages() {
        return maxMsgs;
    }

    /**
     * Gets the maximum messages per subject for this stream configuration.
     * @return the maximum number of messages per subject for this stream.
     */
    public long getMaxMessagesPerSubject() {
        return maxMsgsPerSubject;
    }

    /**
     * Gets the maximum number of bytes for this stream configuration.
     * @return the maximum number of bytes for this stream.
     */
    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Gets the maximum message age for this stream configuration.
     * @return the maximum message age for this stream.
     */
    public Duration getMaxAge() {
        return maxAge;
    }

    /**
     * Gets the maximum message size for this stream configuration.
     * @return the maximum message size for this stream.
     */
    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    /**
     * Gets the storage type for this stream configuration.
     * @return the storage type for this stream.
     */
    public StorageType getStorageType() {
        return storageType;
    }

    /**
     * Gets the number of replicas for this stream configuration.
     * @return the number of replicas
     */
    public int getReplicas() {
        return replicas;
    }

    /**
     * Gets whether acknowledgements are required in this stream configuration.
     * @return true if acknowledgements are not required.
     */
    public boolean getNoAck() {
        return noAck;
    }

    /**
     * Gets the template JSON for this stream configuration.
     * @return the template for this stream.
     */
    @Nullable
    public String getTemplateOwner() {
        return templateOwner;
    }

    /**
     * Gets the discard policy for this stream configuration.
     * @return the discard policy of the stream.
     */
    public DiscardPolicy getDiscardPolicy() {
        return discardPolicy;
    }

    /**
     * Gets the duplicate checking window stream configuration.
     * Duration.ZERO means duplicate checking is not enabled.
     * @return the duration of the window.
     */
    public Duration getDuplicateWindow() {
        return duplicateWindow;
    }

    /**
     * Get the placement directives to consider when placing replicas of this stream,
     * random placement when unset. May be null.
     * @return the placement object
     */
    @Nullable
    public PlacementCreator getPlacementCreator() {
        return placementCreator;
    }

    /**
     * Get the republish configuration. May be null.
     * @return the republish object
     */
    @Nullable
    public RepublishCreator getRepublishCreator() {
        return republishCreator;
    }

    /**
     * Get the subjectTransform configuration. May be null.
     * @return the subjectTransform object
     */
    @Nullable
    public SubjectTransformCreator getSubjectTransformCreator() {
        return subjectTransformCreator;
    }

    /**
     * Get the consumerLimits configuration. May be null.
     * @return the consumerLimits object
     */
    @Nullable
    public ConsumerLimitsCreator getConsumerLimitsCreator() {
        return consumerLimitsCreator;
    }

    /**
     * The mirror definition for this stream
     * @return the mirror
     */
    @Nullable
    public MirrorCreator getMirrorCreator() {
        return mirrorCreator;
    }

    /**
     * The sources for this stream
     * @return the sources
     */
    public List<SourceCreator> getSourceCreators() {
        return sourceCreators;
    }

    /**
     * Get the flag indicating if the stream is sealed.
     * @return the sealed flag
     */
    public boolean getSealed() {
        return sealed;
    }

    /**
     * Get the flag indicating if the stream allows rollup.
     * @return the allows rollup flag
     */
    public boolean getAllowRollup() {
        return allowRollup;
    }

    /**
     * Get the flag indicating if the stream allows direct message access.
     * @return the allows direct flag
     */
    public boolean getAllowDirect() {
        return allowDirect;
    }

    /**
     * Get the flag indicating if the stream allows
     * higher performance and unified direct access for mirrors as well.
     * @return the allows direct flag
     */
    public boolean getMirrorDirect() {
        return mirrorDirect;
    }

    /**
     * Get the flag indicating if deny delete is set for the stream
     * @return the deny delete flag
     */
    public boolean getDenyDelete() {
        return denyDelete;
    }

    /**
     * Get the flag indicating if deny purge is set for the stream
     * @return the deny purge flag
     */
    public boolean getDenyPurge() {
        return denyPurge;
    }

    /**
     * Whether discard policy with max message per subject is applied per subject.
     * @return the discard new per subject flag
     */
    public boolean isDiscardNewPerSubject() {
        return discardNewPerSubject;
    }

    /**
     * Metadata for the stream; may be empty, will never be null.
     * @return the metadata map.
     */
    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * The first sequence used in the stream.
     * @return the first sequence
     */
    public long getFirstSequence() {
        return firstSequence;
    }

    /**
     * Whether Allow Message TTL is set
     * @return the flag
     */
    public boolean getAllowMessageTtl() {
        return allowMessageTtl;
    }

    /**
     * Whether Allow Message Schedules is set
     * @return the flag
     */
    public boolean getAllowMessageSchedules() {
        return allowMsgSchedules;
    }

    /**
     * Whether Allow Message Counter is set
     * @return the flag
     */
    public boolean getAllowMessageCounter() {
        return allowMessageCounter;
    }

    /**
     * Whether Allow Atomic Publish is set
     * @return the flag
     */
    public boolean getAllowAtomicPublish() {
        return allowAtomicPublish;
    }

    /**
     * Get the Subject Delete Marker TTL duration. May be null.
     * @return The duration
     */
    @Nullable
    public Duration getSubjectDeleteMarkerTtl() {
        return subjectDeleteMarkerTtl;
    }

    /**
     * Gets the persist mode or null if it was not explicitly set when creating or the server did not send it with stream info
     * @return the persist mode
     */
    @Nullable
    public PersistMode getPersistMode() {
        return persistMode;
    }

    // ----------------------------------------------------------------------------------------------------
    // SETTERS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the description
     * @param description the description
     * @return this instance for chaining
     */
    public StreamCreator description(@Nullable String description) {
        this.description = emptyAsNull(description);
        return this;
    }

    /**
     * Sets the subjects in the StreamCreator.
     * @param subjects the stream's subjects
     * @return this instance for chaining
     */
    public StreamCreator subjects(String... subjects) {
        return subjects(Arrays.asList(subjects));
    }

    /**
     * Sets the subjects in the StreamCreator.
     * @param subjects the stream's subjects
     * @return this instance for chaining
     */
    public StreamCreator subjects(Collection<String> subjects) {
        this.subjects.clear();
        for (String sub : subjects) {
            if (!nullOrEmpty(sub) && !this.subjects.contains(sub)) {
                this.subjects.add(sub);
            }
        }
        return this;
    }

    /**
     * Sets the retention policy in the StreamCreator.
     * @param policy the retention policy of the StreamCreator
     * @return this instance for chaining
     */
    public StreamCreator retentionPolicy(@Nullable RetentionPolicy policy) {
        this.retentionPolicy = policy == null ? RetentionPolicy.Limits : policy;
        return this;
    }

    /**
     * Sets the compression option in the StreamCreator.
     * @param compressionOption the compression option of the StreamCreator
     * @return this instance for chaining
     */
    public StreamCreator compressionOption(@Nullable CompressionOption compressionOption) {
        this.compressionOption = compressionOption == null ? CompressionOption.None : compressionOption;
        return this;
    }

    /**
     * Sets the maximum number of consumers in the StreamCreator.
     * @param maxConsumers the maximum number of consumers
     * @return this instance for chaining
     */
    public StreamCreator maxConsumers(long maxConsumers) {
        this.maxConsumers = validateMaxConsumers(maxConsumers);
        return this;
    }

    /**
     * Sets the maximum number of messages in the StreamCreator.
     * @param maxMsgs the maximum number of messages
     * @return this instance for chaining
     */
    public StreamCreator maxMessages(long maxMsgs) {
        this.maxMsgs = validateMaxMessages(maxMsgs);
        return this;
    }

    /**
     * Sets the maximum number of message per subject in the StreamCreator.
     * @param maxMsgsPerSubject the maximum number of messages
     * @return this instance for chaining
     */
    public StreamCreator maxMessagesPerSubject(long maxMsgsPerSubject) {
        this.maxMsgsPerSubject = validateMaxMessagesPerSubject(maxMsgsPerSubject);
        return this;
    }

    /**
     * Sets the maximum number of bytes in the StreamCreator.
     * @param maxBytes the maximum number of bytes
     * @return this instance for chaining
     */
    public StreamCreator maxBytes(long maxBytes) {
        this.maxBytes = validateMaxBytes(maxBytes);
        return this;
    }

    /**
     * Sets the maximum age in the StreamCreator.
     * @param maxAge the maximum message age
     * @return this instance for chaining
     */
    public StreamCreator maxAge(@Nullable Duration maxAge) {
        this.maxAge = validateDurationNotRequiredGtOrEqZero(maxAge, Duration.ZERO);
        return this;
    }

    /**
     * Sets the maximum age in the StreamCreator.
     * @param maxAgeMillis the maximum message age in milliseconds
     * @return this instance for chaining
     */
    public StreamCreator maxAge(long maxAgeMillis) {
        this.maxAge = validateDurationNotRequiredGtOrEqZero(maxAgeMillis);
        return this;
    }

    /**
     * Sets the maximum message size in the StreamCreator.
     * @param maxMessageSize the maximum message size
     * @return this instance for chaining
     */
    public StreamCreator maxMessageSize(int maxMessageSize) {
        this.maxMessageSize = validateMaxMessageSize(maxMessageSize);
        return this;
    }

    /**
     * Sets the storage type in the StreamCreator.
     * @param storageType the storage type
     * @return this instance for chaining
     */
    public StreamCreator storageType(@Nullable StorageType storageType) {
        this.storageType = storageType == null ? StorageType.File : storageType;
        return this;
    }

    /**
     * Sets the number of replicas a message must be stored on in the StreamCreator.
     * Must be 1 to 5 inclusive
     * @param replicas the number of replicas to store this message on
     * @return this instance for chaining
     */
    public StreamCreator replicas(int replicas) {
        this.replicas = validateNumberOfReplicas(replicas);
        return this;
    }

    /**
     * Sets the acknowledgement mode of the StreamCreator.  if no acknowledgements are
     * set, then acknowledgements are not sent back to the client.  The default is false.
     * @param noAck true to disable acknowledgements.
     * @return this instance for chaining
     */
    public StreamCreator noAck(boolean noAck) {
        this.noAck = noAck;
        return this;
    }

    /**
     * Sets the template a stream in the form of raw JSON.
     * @param templateOwner the stream template of the stream.
     * @return this instance for chaining
     */
    public StreamCreator templateOwner(@Nullable String templateOwner) {
        this.templateOwner = emptyAsNull(templateOwner);
        return this;
    }

    /**
     * Sets the discard policy in the StreamCreator.
     * @param policy the discard policy of the StreamCreator
     * @return this instance for chaining
     */
    public StreamCreator discardPolicy(@Nullable DiscardPolicy policy) {
        this.discardPolicy = policy == null ? DiscardPolicy.Old : policy;
        return this;
    }

    /**
     * Sets the duplicate checking window in the StreamCreator.  A Duration.Zero
     * disables duplicate checking.  Duplicate checking is disabled by default.
     * @param window duration to hold message ids for duplicate checking.
     * @return this instance for chaining
     */
    public StreamCreator duplicateWindow(@Nullable Duration window) {
        this.duplicateWindow = validateDurationNotRequiredGtOrEqZero(window, Duration.ZERO);
        return this;
    }

    /**
     * Sets the duplicate checking window in the StreamCreator.  A Duration.Zero
     * disables duplicate checking.  Duplicate checking is disabled by default.
     * @param windowMillis duration to hold message ids for duplicate checking.
     * @return this instance for chaining
     */
    public StreamCreator duplicateWindow(long windowMillis) {
        this.duplicateWindow = validateDurationNotRequiredGtOrEqZero(windowMillis);
        return this;
    }

    /**
     * Sets the placement directive object
     * @param placementCreator the placement directive object
     * @return this instance for chaining
     */
    public StreamCreator placementCreator(PlacementCreator placementCreator) {
        if (placementCreator.hasData()) {
            this.placementCreator = placementCreator;
        }
        else {
            this.placementCreator = null;
        }
        return this;
    }

    /**
     * Sets the republish config object
     * @param republishCreator the republish config object
     * @return this instance for chaining
     */
    public StreamCreator republishCreator(RepublishCreator republishCreator) {
        this.republishCreator = republishCreator;
        return this;
    }

    /**
     * Sets the subjectTransform config object
     * @param subjectTransformCreator the subjectTransform config object
     * @return this instance for chaining
     */
    public StreamCreator subjectTransformCreator(SubjectTransformCreator subjectTransformCreator) {
        this.subjectTransformCreator = subjectTransformCreator;
        return this;
    }

    /**
     * Sets the consumerLimits config object
     * @param consumerLimits the consumerLimits config object
     * @return this instance for chaining
     */
    public StreamCreator consumerLimits(ConsumerLimitsCreator consumerLimits) {
        this.consumerLimitsCreator = consumerLimits;
        return this;
    }

    /**
     * Sets the mirror object
     * @param mirrorCreator the mirror object
     * @return this instance for chaining
     */
    public StreamCreator mirrorCreator(MirrorCreator mirrorCreator) {
        this.mirrorCreator = mirrorCreator;
        return this;
    }

    /**
     * Sets the sources in the StreamCreator.
     * @param sourceCreators the stream's sources
     * @return this instance for chaining
     */
    public StreamCreator sourceCreators(SourceCreator... sourceCreators) {
        return sourceCreators(Arrays.asList(sourceCreators));
    }

    /**
     * Sets the sources in the StreamCreator.
     * @param sourceCreators the stream's sources
     * @return this instance for chaining
     */
    public StreamCreator sourceCreators(Collection<SourceCreator> sourceCreators) {
        this.sourceCreators.clear();
        for (SourceCreator sc : sourceCreators) {
            if (!this.sourceCreators.contains(sc)) {
                this.sourceCreators.add(sc);
            }
        }
        return this;
    }

    /**
     * Set whether to seal the stream.
     * @param sealed the sealed setting
     * @return this instance for chaining
     */
    public StreamCreator sealed(boolean sealed) {
        this.sealed = sealed;
        return this;
    }

    /**
     * Set this stream to be sealed. This is irreversible.
     * @return this instance for chaining
     */
    public StreamCreator seal() {
        this.sealed = true;
        return this;
    }

    /**
     * Set whether to allow the rollup feature for a stream
     * @param allowRollup the allow rollup setting
     * @return this instance for chaining
     */
    public StreamCreator allowRollup(boolean allowRollup) {
        this.allowRollup = allowRollup;
        return this;
    }

    /**
     * Set whether to allow direct message access for a stream
     * @param allowDirect the allow direct setting
     * @return this instance for chaining
     */
    public StreamCreator allowDirect(boolean allowDirect) {
        this.allowDirect = allowDirect;
        return this;
    }

    /**
     * Set whether to allow unified direct access for mirrors
     * @param mirrorDirect the allow direct setting
     * @return this instance for chaining
     */
    public StreamCreator mirrorDirect(boolean mirrorDirect) {
        this.mirrorDirect = mirrorDirect;
        return this;
    }

    /**
     * Set whether to deny deleting messages from the stream
     * @param denyDelete the deny delete setting
     * @return this instance for chaining
     */
    public StreamCreator denyDelete(boolean denyDelete) {
        this.denyDelete = denyDelete;
        return this;
    }

    /**
     * Set whether to deny purging messages from the stream
     * @param denyPurge the deny purge setting
     * @return this instance for chaining
     */
    public StreamCreator denyPurge(boolean denyPurge) {
        this.denyPurge = denyPurge;
        return this;
    }

    /**
     * Set whether discard policy new with max message per subject applies to existing subjects, not just new subjects.
     * @param discardNewPerSubject the setting
     * @return this instance for chaining
     */
    public StreamCreator discardNewPerSubject(boolean discardNewPerSubject) {
        this.discardNewPerSubject = discardNewPerSubject;
        return this;
    }

    /**
     * Sets the metadata for the configuration
     * @param metadata the metadata map
     * @return this instance for chaining
     */
    public StreamCreator metadata(Map<String, String> metadata) {
        this.metadata.clear();
        if (!metadata.isEmpty()) {
            this.metadata.putAll(metadata);
        }
        return this;
    }

    /**
     * Sets the first sequence to be used. 1 is the default. All values less than 2 are treated as 1.
     * @param firstSeq specify the first_seq in the stream config when creating the stream.
     * @return this instance for chaining
     */
    public StreamCreator firstSequence(long firstSeq) {
        this.firstSequence = firstSeq > 1 ? firstSeq : 1;
        return this;
    }

    /**
     * Set the subject delete marker TTL duration. Server accepts 1 second or more.
     * null has the effect of clearing the subject delete marker TTL
     * @param subjectDeleteMarkerTtl the TTL duration
     * @return this instance for chaining
     */
    public StreamCreator subjectDeleteMarkerTtl(@Nullable Duration subjectDeleteMarkerTtl) {
        this.subjectDeleteMarkerTtl = validateDurationNotRequiredGtOrEqSeconds(1, subjectDeleteMarkerTtl, null, "Subject Delete Marker Ttl");
        return this;
    }

    /**
     * Set the subject delete marker TTL duration in milliseconds. Server accepts 1 second or more.
     * 0 or less has the effect of clearing the subject delete marker TTL
     * @param subjectDeleteMarkerTtlMillis the TTL duration in milliseconds
     * @return this instance for chaining
     */
    public StreamCreator subjectDeleteMarkerTtl(long subjectDeleteMarkerTtlMillis) {
        this.subjectDeleteMarkerTtl = subjectDeleteMarkerTtlMillis <= 0 ? null
            : validateDurationGtOrEqSeconds(1, subjectDeleteMarkerTtlMillis, "Subject Delete Marker Ttl");
        return this;
    }

    /**
     * Set allow per message TTL to true
     * @return this instance for chaining
     */
    public StreamCreator allowMessageTtl() {
        this.allowMessageTtl = true;
        return this;
    }

    /**
     * Set the allow per message TTL flag
     * @param allowMessageTtl the flag
     * @return this instance for chaining
     */
    public StreamCreator allowMessageTtl(boolean allowMessageTtl) {
        this.allowMessageTtl = allowMessageTtl;
        return this;
    }

    /**
     * Set to allow message Schedules to true
     * @return this instance for chaining
     */
    public StreamCreator allowMessageSchedules() {
        this.allowMsgSchedules = true;
        return this;
    }

    /**
     * Set allow message Schedules flag
     * @param allowMessageSchedules the flag
     * @return this instance for chaining
     */
    public StreamCreator allowMessageSchedules(boolean allowMessageSchedules) {
        this.allowMsgSchedules = allowMessageSchedules;
        return this;
    }

    /**
     * Set allow message counter to true
     * @return this instance for chaining
     */
    public StreamCreator allowMessageCounter() {
        this.allowMessageCounter = true;
        return this;
    }

    /**
     * Set the allow message counter flag
     * @param allowMessageCounter the flag
     * @return this instance for chaining
     */
    public StreamCreator allowMessageCounter(boolean allowMessageCounter) {
        this.allowMessageCounter = allowMessageCounter;
        return this;
    }

    /**
     * Set allow atomic publish to true
     * @return this instance for chaining
     */
    public StreamCreator allowAtomicPublish() {
        this.allowAtomicPublish = true;
        return this;
    }

    /**
     * Set allow atomic publish flag
     * @param allowAtomicPublish the flag
     * @return this instance for chaining
     */
    public StreamCreator allowAtomicPublish(boolean allowAtomicPublish) {
        this.allowAtomicPublish = allowAtomicPublish;
        return this;
    }

    /**
     * Set the persist mode. Setting null leaves it up to the server
     * @param persistMode the persist mode
     * @return this instance for chaining
     */
    public StreamCreator persistMode(PersistMode persistMode) {
        this.persistMode = persistMode;
        return this;
    }

    @Override
    public String toString() {
        return "StreamCreator " + toJson();
    }
}
