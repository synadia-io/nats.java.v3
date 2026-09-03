package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stream discard policies
 */
@NullMarked
public enum DiscardPolicy {
    /** discard new messages */
    New("new"),
    /** discard old messages */
    Old("old");

    private final String policy;

    DiscardPolicy(String p) {
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
     * @return the matching DiscardPolicy or the supplied default
     */
    public static DiscardPolicy get(@Nullable String value, DiscardPolicy dflt) {
        if (value != null) {
            if (New.policy.equals(value)) { return New; }
            if (Old.policy.equals(value)) { return Old; }
        }
        return dflt;
    }
}
