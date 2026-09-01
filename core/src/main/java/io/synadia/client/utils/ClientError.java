package io.synadia.client.utils;

/**
 * A client-side error: a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link #instance()} to build the exception to throw.
 *
 * <p><b>Intended for internal library use. Its API is not guaranteed and may change without notice.</b>
 * Applications should catch the {@link IllegalArgumentException} or {@link IllegalStateException} that
 * {@link #instance()} produces, not construct or subclass this type. The constructor is public only so the
 * per-domain catalogs that declare the constants can live in other modules; declaring your own errors here
 * is not supported.
 */
public class ClientError {
    /** Kind indicating {@link #instance()} builds an IllegalArgumentException. {@value} */
    public static final int KIND_ILLEGAL_ARGUMENT = 0;
    /** Kind indicating {@link #instance()} builds an IllegalStateException. {@value} */
    public static final int KIND_ILLEGAL_STATE = 1;

    private final String id;
    private final String message;
    private final int kind;

    /**
     * Construct an error, choosing the kind of exception it raises.
     * Public for the per-domain catalogs in other modules; not part of the supported API.
     * @param group the group code, which prefixes the id
     * @param code the numeric error code
     * @param description the human-readable description
     * @param kind {@code KIND_ILLEGAL_ARGUMENT} or {@code KIND_ILLEGAL_STATE}
     */
    public ClientError(String group, int code, String description, int kind) {
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

    /**
     * Whether an exception is the one this error raises, by both kind and message.
     * @param e the exception to test
     * @return true if it matches
     */
    public boolean matches(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return kind == KIND_ILLEGAL_ARGUMENT && e.getMessage().equals(message);
        }
        if (e instanceof IllegalStateException) {
            return kind == KIND_ILLEGAL_STATE && e.getMessage().equals(message);
        }
        return false;
    }
}
