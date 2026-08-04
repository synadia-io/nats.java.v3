package io.synadia.client.utils;

/**
 * A client-side JetStream error: a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link #instance()} to build the exception to throw.
 */
public class JetStreamClientError {
    /** Kind indicating {@link #instance()} builds an IllegalArgumentException. {@value} */
    public static final int KIND_ILLEGAL_ARGUMENT = 0;
    /** Kind indicating {@link #instance()} builds an IllegalStateException. {@value} */
    public static final int KIND_ILLEGAL_STATE = 1;
    private static final String SUB = "SUB";
    private static final String SO = "SO";
    private static final String OS = "OS";
    private static final String CON = "CON";

    /** No matching streams for subject. */
    public static final JetStreamClientError JsSubNoMatchingStreamForSubject = new JetStreamClientError(SUB, 90007, "No matching streams for subject.", KIND_ILLEGAL_STATE);

    /** The object was not found. */
    public static final JetStreamClientError OsObjectNotFound = new JetStreamClientError(OS, 90201, "The object was not found.");
    /** The object is deleted. */
    public static final JetStreamClientError OsObjectIsDeleted = new JetStreamClientError(OS, 90202, "The object is deleted.");
    /** An object with that name already exists. */
    public static final JetStreamClientError OsObjectAlreadyExists = new JetStreamClientError(OS, 90203, "An object with that name already exists.");
    /** A link cannot link to another link. */
    public static final JetStreamClientError OsCantLinkToLink = new JetStreamClientError(OS, 90204, "A link cannot link to another link.");
    /** Digest does not match meta data. */
    public static final JetStreamClientError OsGetDigestMismatch = new JetStreamClientError(OS, 90205, "Digest does not match meta data.");
    /** Number of chunks does not match meta data. */
    public static final JetStreamClientError OsGetChunksMismatch = new JetStreamClientError(OS, 90206, "Number of chunks does not match meta data.");
    /** Total size does not match meta data. */
    public static final JetStreamClientError OsGetSizeMismatch = new JetStreamClientError(OS, 90207, "Total size does not match meta data.");
    /** Cannot get object, it is a link to a bucket. */
    public static final JetStreamClientError OsGetLinkToBucket = new JetStreamClientError(OS, 90208, "Cannot get object, it is a link to a bucket.");
    /** Link not allowed in metadata when putting an object. */
    public static final JetStreamClientError OsLinkNotAllowOnPut = new JetStreamClientError(OS, 90209, "Link not allowed in metadata when putting an object.");

    /** Name field not valid when v2.9.0 consumer create api is not available. */
    public static final JetStreamClientError JsConsumerCreate290NotAvailable = new JetStreamClientError(CON, 90301, "Name field not valid when v2.9.0 consumer create api is not available.");
    /** Name must match durable if both are supplied. */
    public static final JetStreamClientError JsConsumerNameDurableMismatch = new JetStreamClientError(CON, 90302, "Name must match durable if both are supplied.");
    /** Multiple filter subjects not available until server version 2.10.0. */
    public static final JetStreamClientError JsMultipleFilterSubjects210NotAvailable = new JetStreamClientError(CON, 90303, "Multiple filter subjects not available until server version 2.10.0.");

    private final String id;
    private final String message;
    private final int kind;

    /**
     * Construct an error that raises an IllegalArgumentException.
     * @param group the group code, which prefixes the id
     * @param code the numeric error code
     * @param description the human-readable description
     */
    public JetStreamClientError(String group, int code, String description) {
        this(group, code, description, KIND_ILLEGAL_ARGUMENT);
    }

    /**
     * Construct an error, choosing the kind of exception it raises.
     * @param group the group code, which prefixes the id
     * @param code the numeric error code
     * @param description the human readable description
     * @param kind {@code KIND_ILLEGAL_ARGUMENT} or {@code KIND_ILLEGAL_STATE}
     */
    public JetStreamClientError(String group, int code, String description, int kind) {
        id = String.format("%s-%d", group, code);
        message = String.format("[%s] %s", id, description);
        this.kind = kind;
    }

    /**
     * Build the exception for this error, ready to throw.
     * @return the exception, of the kind this error was constructed with
     */
    public RuntimeException instance() {
        return _instance(message);
    }

    /**
     * Build the exception for this error with extra context appended to the message.
     * @param extraMessage text appended after the standard message
     * @return the exception, of the kind this error was constructed with
     */
    public RuntimeException instance(String extraMessage) {
        return _instance(message + " " + extraMessage);
    }

    private RuntimeException _instance(String msg) {
        if (kind == KIND_ILLEGAL_ARGUMENT) {
            return new IllegalArgumentException(msg);
        }
        return new IllegalStateException(msg);
    }

    /**
     * The stable id of this error, formatted as {@code GROUP-CODE}.
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * The full message, which begins with the bracketed {@link #id()}.
     * @return the message
     */
    public String message() {
        return message;
    }

    /**
     * Which unchecked exception {@link #instance()} builds.
     * @return the kind
     */
    public int getKind() {
        return kind;
    }
}
