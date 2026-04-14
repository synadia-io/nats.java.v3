package io.synadia.client.jsapi;

import org.jspecify.annotations.NullMarked;

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
    public static DiscardPolicy get(String value, DiscardPolicy dflt) {
        if (value != null) {
            if (New.policy.equalsIgnoreCase(value)) { return New; }
            if (Old.policy.equalsIgnoreCase(value)) { return Old; }
        }
        return dflt;
    }
}
