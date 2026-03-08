package io.nats.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the replay policy of a consumer.
 */
public enum ReplayPolicy {
    /** instant policy */
    Instant("instant"),
    /** original policy */
    Original("original");

    private String policy;

    ReplayPolicy(String p) {
        this.policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    private static final Map<String, ReplayPolicy> strEnumHash = new HashMap<>();

    static {
        for (ReplayPolicy env : ReplayPolicy.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static ReplayPolicy get(String value) {
        return strEnumHash.get(value);
    }
}
