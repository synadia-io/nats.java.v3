package io.synadia.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream compression policies.
 */
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

    private static final Map<String, CompressionOption> strEnumHash = new HashMap<>();

    static {
        for (CompressionOption env : CompressionOption.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance of the CompressionOption or null if the string does not match the JSON value text
     * @param value the value to look up
     * @return the CompressionOption or null if not found
     */
    @Nullable
    public static CompressionOption get(String value) {
        return strEnumHash.get(value);
    }
}
