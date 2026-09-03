package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stream retention policies.
 */
@NullMarked
public enum RetentionPolicy {
    /** Limits */
    Limits("limits"),
    /** Interest */
    Interest("interest"),
    /** Workqueue */
    WorkQueue("workqueue");

    private final String policy;

    RetentionPolicy(String p) {
        policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    /**
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is not null or not matched
     * @return the matching RetentionPolicy or the supplied default
     */
    public static RetentionPolicy get(@Nullable String value, RetentionPolicy dflt) {
        if (value != null) {
            if (Limits.policy.equals(value)) {
                return Limits;
            }
            if (Interest.policy.equals(value)) {
                return Interest;
            }
            if (WorkQueue.policy.equals(value)) {
                return WorkQueue;
            }
        }
        return dflt;
    }
}
