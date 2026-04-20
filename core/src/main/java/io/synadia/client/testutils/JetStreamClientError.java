package io.synadia.client.testutils;

public class JetStreamClientError {
    public static final int KIND_ILLEGAL_ARGUMENT = 0;
    public static final int KIND_ILLEGAL_STATE = 1;
    private static final String SUB = "SUB";
    private static final String SO = "SO";
    private static final String OS = "OS";
    private static final String CON = "CON";

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
