package io.nats.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream retention policies.
 */
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

    private static final Map<String, RetentionPolicy> strEnumHash = new HashMap<>();

    static {
        for (RetentionPolicy env : RetentionPolicy.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static RetentionPolicy get(String value) {
        return strEnumHash.get(value);
    }
}
