package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The delivery policy for this consumer, the point in the stream from which to receive messages
 */
@NullMarked
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

    /**
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is null or not matched
     * @return the matching DeliverPolicy or the supplied default
     */
    public static DeliverPolicy get(@Nullable String value, DeliverPolicy dflt) {
        if (value != null) {
            if (All.policy.equalsIgnoreCase(value)) { return All; }
            if (Last.policy.equalsIgnoreCase(value)) { return Last; }
            if (New.policy.equalsIgnoreCase(value)) { return New; }
            if (ByStartSequence.policy.equalsIgnoreCase(value)) { return ByStartSequence; }
            if (ByStartTime.policy.equalsIgnoreCase(value)) { return ByStartTime; }
            if (LastPerSubject.policy.equalsIgnoreCase(value)) { return LastPerSubject; }
        }
        return dflt;
    }
}
