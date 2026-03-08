package io.nats.client.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Stream persist modes
 */
public enum PersistMode {
    /** default mode */
    Default("default"),
    /** async mode */
    Async("async");

    private final String mode;

    PersistMode(String mode) {
        this.mode = mode;
    }

    /**
     * get the mode JSON value string
     * @return the mode
     */
    @NonNull
    public String getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return mode;
    }

    private static final Map<String, PersistMode> strEnumHash = new HashMap<>();

    static {
        for (PersistMode env : PersistMode.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static PersistMode get(String value) {
        return strEnumHash.get(value);
    }
}
