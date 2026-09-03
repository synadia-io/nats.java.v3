package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Stream persist modes
 */
@NullMarked
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
    public String getMode() {
        return mode;
    }

    @Override
    public String toString() {
        return mode;
    }

    /**
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is null or not matched
     * @return the matching PersistMode or the supplied default
     */
    public static PersistMode get(@Nullable String value, PersistMode dflt) {
        if (value != null) {
            if (Default.mode.equals(value)) { return Default; }
            if (Async.mode.equals(value)) { return Async; }
        }
        return dflt;
    }
}
