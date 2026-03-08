package io.nats.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream discard policies
 */
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

    private static final Map<String, DiscardPolicy> strEnumHash = new HashMap<>();

    static {
        for (DiscardPolicy env : DiscardPolicy.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static DiscardPolicy get(String value) {
        return strEnumHash.get(value);
    }
}
