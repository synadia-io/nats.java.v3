package io.synadia.client.support;

public class JetStreamClientError {
    public static final int KIND_ILLEGAL_ARGUMENT = 0;
    public static final int KIND_ILLEGAL_STATE = 1;
    private static final String SUB = "SUB";
    private static final String SO = "SO";
    private static final String OS = "OS";
    private static final String CON = "CON";

    public static final JetStreamClientError JsSubPullCantHaveDeliverGroup = new JetStreamClientError(SUB, 90001, "Pull subscriptions can't have a deliver group.");
    public static final JetStreamClientError JsSubPullCantHaveDeliverSubject = new JetStreamClientError(SUB, 90002, "Pull subscriptions can't have a deliver subject.");
    public static final JetStreamClientError JsSubPushCantHaveMaxPullWaiting = new JetStreamClientError(SUB, 90003, "Push subscriptions cannot supply max pull waiting.");
    public static final JetStreamClientError JsSubQueueDeliverGroupMismatch = new JetStreamClientError(SUB, 90004, "Queue / deliver group mismatch.");
    public static final JetStreamClientError JsSubFcHbNotValidPull = new JetStreamClientError(SUB, 90005, "Flow Control and/or heartbeat is not valid with a pull subscription.");
    public static final JetStreamClientError JsSubFcHbNotValidQueue = new JetStreamClientError(SUB, 90006, "Flow Control and/or heartbeat is not valid in queue mode.");
    public static final JetStreamClientError JsSubNoMatchingStreamForSubject = new JetStreamClientError(SUB, 90007, "No matching streams for subject.", KIND_ILLEGAL_STATE);
    public static final JetStreamClientError JsSubConsumerAlreadyConfiguredAsPush = new JetStreamClientError(SUB, 90008, "Consumer is already configured as a push consumer.");
    public static final JetStreamClientError JsSubConsumerAlreadyConfiguredAsPull = new JetStreamClientError(SUB, 90009, "Consumer is already configured as a pull consumer.");
    public static final JetStreamClientError JsSubSubjectDoesNotMatchFilter = new JetStreamClientError(SUB, 90011, "Subject does not match consumer configuration filter.");
    public static final JetStreamClientError JsSubConsumerAlreadyBound = new JetStreamClientError(SUB, 90012, "Consumer is already bound to a subscription.");
    public static final JetStreamClientError JsSubExistingConsumerNotQueue = new JetStreamClientError(SUB, 90013, "Existing consumer is not configured as a queue / deliver group.");
    public static final JetStreamClientError JsSubExistingConsumerIsQueue = new JetStreamClientError(SUB, 90014, "Existing consumer is configured as a queue / deliver group.");
    public static final JetStreamClientError JsSubExistingQueueDoesNotMatchRequestedQueue = new JetStreamClientError(SUB, 90015, "Existing consumer deliver group does not match requested queue / deliver group.");
    public static final JetStreamClientError JsSubExistingConsumerCannotBeModified = new JetStreamClientError(SUB, 90016, "Existing consumer cannot be modified.");
    public static final JetStreamClientError JsSubConsumerNotFoundRequiredInBind = new JetStreamClientError(SUB, 90017, "Consumer not found, required in bind mode.");
    public static final JetStreamClientError JsSubOrderedNotAllowOnQueues = new JetStreamClientError(SUB, 90018, "Ordered consumer not allowed on queues.");
    public static final JetStreamClientError JsSubPushCantHaveMaxBatch = new JetStreamClientError(SUB, 90019, "Push subscriptions cannot supply max batch.");
    public static final JetStreamClientError JsSubPushCantHaveMaxBytes = new JetStreamClientError(SUB, 90020, "Push subscriptions cannot supply max bytes.");
    public static final JetStreamClientError JsSubPushAsyncCantSetPending = new JetStreamClientError(SUB, 90021, "Pending limits must be set directly on the dispatcher.");
    public static final JetStreamClientError JsSubSubjectNeededToLookupStream = new JetStreamClientError(SUB, 90022, "Subject needed to lookup stream. Provide either a subscribe subject or a ConsumerConfiguration filter subject.");

    public static final JetStreamClientError JsSoDurableMismatch = new JetStreamClientError(SO, 90101, "Builder durable must match the consumer configuration durable if both are provided.");
    public static final JetStreamClientError JsSoDeliverGroupMismatch = new JetStreamClientError(SO, 90102, "Builder deliver group must match the consumer configuration deliver group if both are provided.");
    public static final JetStreamClientError JsSoDeliverSubjectMismatch = new JetStreamClientError(SO, 90103, "Builder deliver subject must match the consumer configuration deliver subject if both are provided.");
    public static final JetStreamClientError JsSoOrderedNotAllowedWithBind = new JetStreamClientError(SO, 90104, "Bind is not allowed with an ordered consumer.");
    public static final JetStreamClientError JsSoOrderedNotAllowedWithDurable = new JetStreamClientError(SO, 90106, "Durable is not allowed with an ordered consumer.");
    public static final JetStreamClientError JsSoOrderedRequiresAckPolicyNone = new JetStreamClientError(SO, 90108, "Ordered consumer requires Ack Policy None.");
    public static final JetStreamClientError JsSoOrderedRequiresMaxDeliverOfOne = new JetStreamClientError(SO, 90109, "Max Deliver is limited to 1 with an ordered consumer.");
    public static final JetStreamClientError JsSoNameMismatch = new JetStreamClientError(SO, 90110, "Builder name must match the consumer configuration name if both are provided.");
    public static final JetStreamClientError JsSoOrderedMemStorageNotSuppliedOrTrue = new JetStreamClientError(SO, 90111, "Mem Storage must be true if supplied.");
    public static final JetStreamClientError JsSoOrderedReplicasNotSuppliedOrOne = new JetStreamClientError(SO, 90112, "Replicas must be 1 if supplied.");
    public static final JetStreamClientError JsSoNameOrDurableRequiredForBind = new JetStreamClientError(SO, 90113, "Name or Durable required for Bind.");

    public static final JetStreamClientError OsObjectNotFound = new JetStreamClientError(OS, 90201, "The object was not found.");
    public static final JetStreamClientError OsObjectIsDeleted = new JetStreamClientError(OS, 90202, "The object is deleted.");
    public static final JetStreamClientError OsObjectAlreadyExists = new JetStreamClientError(OS, 90203, "An object with that name already exists.");
    public static final JetStreamClientError OsCantLinkToLink = new JetStreamClientError(OS, 90204, "A link cannot link to another link.");
    public static final JetStreamClientError OsGetDigestMismatch = new JetStreamClientError(OS, 90205, "Digest does not match meta data.");
    public static final JetStreamClientError OsGetChunksMismatch = new JetStreamClientError(OS, 90206, "Number of chunks does not match meta data.");
    public static final JetStreamClientError OsGetSizeMismatch = new JetStreamClientError(OS, 90207, "Total size does not match meta data.");
    public static final JetStreamClientError OsGetLinkToBucket = new JetStreamClientError(OS, 90208, "Cannot get object, it is a link to a bucket.");
    public static final JetStreamClientError OsLinkNotAllowOnPut = new JetStreamClientError(OS, 90209, "Link not allowed in metadata when putting an object.");

    public static final JetStreamClientError JsConsumerCreate290NotAvailable = new JetStreamClientError(CON, 90301, "Name field not valid when v2.9.0 consumer create api is not available.");
    public static final JetStreamClientError JsConsumerNameDurableMismatch = new JetStreamClientError(CON, 90302, "Name must match durable if both are supplied.");
    public static final JetStreamClientError JsMultipleFilterSubjects210NotAvailable = new JetStreamClientError(CON, 90303, "Multiple filter subjects not available until server version 2.10.0.");

    private final String id;
    private final String message;
    private final int kind;

    public JetStreamClientError(String group, int code, String description) {
        this(group, code, description, KIND_ILLEGAL_ARGUMENT);
    }

    public JetStreamClientError(String group, int code, String description, int kind) {
        id = String.format("%s-%d", group, code);
        message = String.format("[%s] %s", id, description);
        this.kind = kind;
    }

    public RuntimeException instance() {
        return _instance(message);
    }

    public RuntimeException instance(String extraMessage) {
        return _instance(message + " " + extraMessage);
    }

    private RuntimeException _instance(String msg) {
        if (kind == KIND_ILLEGAL_ARGUMENT) {
            return new IllegalArgumentException(msg);
        }
        return new IllegalStateException(msg);
    }

    public String id() {
        return id;
    }

    public String message() {
        return message;
    }

    public int getKind() {
        return kind;
    }
}
