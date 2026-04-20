package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * Stream storage types.
 */
@NullMarked
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
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is null or not matched
     * @return the matching StorageType or the supplied default
     */
    public static StorageType get(String value, StorageType dflt) {
        if (value != null) {
            if (File.policy.equalsIgnoreCase(value)) { return File; }
            if (Memory.policy.equalsIgnoreCase(value)) { return Memory; }
        }
        return dflt;
    }
}
