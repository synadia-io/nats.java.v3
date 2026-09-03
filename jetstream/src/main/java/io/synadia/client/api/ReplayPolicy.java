package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the replay policy of a consumer.
 */
@NullMarked
public enum ReplayPolicy {
    /** instant policy */
    Instant("instant"),
    /** original policy */
    Original("original");

    private final String policy;

    ReplayPolicy(String p) {
        this.policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    /**
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is null or not matched
     * @return the matching ReplayPolicy or the supplied default
     */
    public static ReplayPolicy get(@Nullable String value, ReplayPolicy dflt) {
        if (value != null) {
            if (Instant.policy.equals(value)) { return Instant; }
            if (Original.policy.equals(value)) { return Original; }
        }
        return dflt;
    }
}
