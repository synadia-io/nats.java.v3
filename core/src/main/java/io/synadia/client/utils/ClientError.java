package io.synadia.client.utils;

/**
 * A client-side error: a stable id, a message, and the kind of unchecked exception it raises.
 * Each constant is a distinct error condition the client detects itself, before or instead of a server round
 * trip. Call {@link #instance(Object...)} to build the exception to throw; a description containing {@code %s}
 * takes that many labels from the thrower.
 *
 * <p><b>Intended for internal library use. Its API is not guaranteed and may change without notice.</b>
 * Applications should catch the {@link IllegalArgumentException} or {@link IllegalStateException} that
 * {@link #instance(Object...)} produces, not construct or subclass this type. The constructor is public only so the
 * per-domain catalogs that declare the constants can live in other modules; declaring your own errors here
 * is not supported.
 */
public class ClientError {
    /** What a label reads when the description has more placeholders than labels supplied. {@value} */
    public static final String MISSING_LABEL = "???";

    /** Kind indicating {@link #instance(Object...)} builds an IllegalArgumentException. {@value} */
    public static final int KIND_ILLEGAL_ARGUMENT = 0;
    /** Kind indicating {@link #instance(Object...)} builds an IllegalStateException. {@value} */
    public static final int KIND_ILLEGAL_STATE = 1;

    private final String id;
    private final String idPrefix;
    private final String message;
    private final int kind;
    private final int labelCount;

    /**
     * Construct an error, choosing the kind of exception it raises.
     * Public for the per-domain catalogs in other modules; not part of the supported API.
     * @param group the group code, which prefixes the id
     * @param code the numeric error code
     * @param description the human-readable description. Each {@code %s} in it is a label the thrower
     *                    supplies to {@link #instance(Object...)}; no other format specifier is supported.
     * @param kind {@code KIND_ILLEGAL_ARGUMENT} or {@code KIND_ILLEGAL_STATE}
     */
    public ClientError(String group, int code, String description, int kind) {
        id = String.format("%s-%d", group, code);
        idPrefix = "[" + id + "] ";
        message = idPrefix + description;
        this.kind = kind;
        labelCount = countLabels(description);
    }

    private static int countLabels(String description) {
        int count = 0;
        int at = description.indexOf("%s");
        while (at != -1) {
            count++;
            at = description.indexOf("%s", at + 2);
        }
        return count;
    }

    /**
     * Build the exception for this error, ready to throw. A label count that does not match the description
     * is a library mistake, not a caller's, so it never fails: extra labels are combined into the last
     * placeholder, comma delimited, and missing ones read {@value #MISSING_LABEL}.
     * @param labels one value per {@code %s} in the description, in order. None for a fixed message.
     * @return the exception, of the kind this error was constructed with
     */
    public RuntimeException instance(Object... labels) {
        return _instance(labelCount == 0 ? message : String.format(message, fitLabels(labels)));
    }

    private Object[] fitLabels(Object[] labels) {
        if (labels.length == labelCount) {
            return labels;
        }

        Object[] fitted = new Object[labelCount];
        if (labels.length < labelCount) {
            System.arraycopy(labels, 0, fitted, 0, labels.length);
            for (int x = labels.length; x < labelCount; x++) {
                fitted[x] = MISSING_LABEL;
            }
            return fitted;
        }

        // too many, so everything from the last placeholder on becomes one comma delimited label
        int last = labelCount - 1;
        System.arraycopy(labels, 0, fitted, 0, last);
        StringBuilder sb = new StringBuilder();
        for (int x = last; x < labels.length; x++) {
            if (x > last) {
                sb.append(", ");
            }
            sb.append(labels[x]);
        }
        fitted[last] = sb.toString();
        return fitted;
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
     * The full message, which begins with the bracketed {@link #id()}. For a label-parameterized
     * error this is the template, with its {@code %s} placeholders unfilled.
     * @return the message
     */
    public String message() {
        return message;
    }

    /**
     * How many labels {@link #instance(Object...)} requires, which is the number of {@code %s} in the description.
     * @return the label count
     */
    public int labelCount() {
        return labelCount;
    }

    /**
     * Which unchecked exception {@link #instance(Object...)} builds.
     * @return the kind
     */
    public int getKind() {
        return kind;
    }

    /**
     * Whether an exception is the one this error raises, by kind and by id. The id is matched rather than
     * the whole message so a label-parameterized error still matches once its labels are filled in.
     * @param e the exception to test
     * @return true if it matches
     */
    public boolean matches(Exception e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        if (e instanceof IllegalArgumentException) {
            return kind == KIND_ILLEGAL_ARGUMENT && e.getMessage().startsWith(idPrefix);
        }
        if (e instanceof IllegalStateException) {
            return kind == KIND_ILLEGAL_STATE && e.getMessage().startsWith(idPrefix);
        }
        return false;
    }
}
