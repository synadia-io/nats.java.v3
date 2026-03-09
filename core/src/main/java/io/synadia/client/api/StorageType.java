package io.synadia.client.api;

import org.jspecify.annotations.Nullable;

/**
 * Stream storage types.
 */
public enum StorageType {
    /** File Storage */
    File("file"),
    /** Memory Storage */
    Memory("memory");

    private final String policy;

    StorageType(String p) {
        policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    /**
     * Get an instance from the JSON value
     * @param value the value
     * @return the instance or null if the string is not matched
     */
    @Nullable
    public static StorageType get(String value) {
        if (File.policy.equalsIgnoreCase(value)) { return File; }
        if (Memory.policy.equalsIgnoreCase(value)) { return Memory; }
        return null;
    }
}
