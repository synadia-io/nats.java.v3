package io.synadia.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The delivery policy for this consumer, the point in the stream from which to receive messages
 */
public enum DeliverPolicy {
    /** all messages */
    All("all"),

    /** start at the last message */
    Last("last"),

    /** start at any new messages */
    New("new"),

    /** by start sequence */
    ByStartSequence("by_start_sequence"),

    /** by start time */
    ByStartTime("by_start_time"),

    /** last per subject */
    LastPerSubject("last_per_subject");

    private final String policy;

    DeliverPolicy(String p) {
        policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    private static final Map<String, DeliverPolicy> strEnumHash = new HashMap<>();

    static {
        for (DeliverPolicy env : DeliverPolicy.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static DeliverPolicy get(String value) {
        return strEnumHash.get(value);
    }
}
