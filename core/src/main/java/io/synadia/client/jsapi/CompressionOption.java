package io.synadia.client.jsapi;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stream compression policies.
 */
@NullMarked
public enum CompressionOption {
    /**
     * No compress
     */
    None("none"),

    /**
     * S2 compression
     */
    S2("s2");

    private final String policy;

    CompressionOption(String p) {
        policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    /**
     * Get an instance of the CompressionOption from the string value
     * @param value the value to look up
     * @param dflt the result if value is not null or not matched
     * @return the matching CompressionOption or the supplied default
     */
    public static CompressionOption get(@Nullable String value, CompressionOption dflt) {
        if (value != null) {
            if (None.policy.equalsIgnoreCase(value)) {
                return None;
            }
            if (S2.policy.equalsIgnoreCase(value)) {
                return S2;
            }
        }
        return dflt;
    }
}
