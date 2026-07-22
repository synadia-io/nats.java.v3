package io.synadia.client.kv;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Options used when purging keys
 */
@NullMarked
public class KeyValuePurgeOptions {

    /**
     * The default time in millis that is used for as the threshold to keep markers.
     */
    public static final long DEFAULT_THRESHOLD_MILLIS = Duration.ofMinutes(30).toMillis();

    private final long millis;

    private KeyValuePurgeOptions(Builder b) {
        this.millis = b.millis;
    }

    /**
     * The value of the delete marker threshold, in milliseconds.
     * @return the threshold
     */
    public long getDeleteMarkersThresholdMillis() {
        return millis;
    }

    /**
     * Creates a builder for the Key Value Purge Options.
     * @return a key value purge options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * KeyValuePurgeOptions is created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are calls.
     * <p>{@code new KeyValuePurgeOptions.Builder().build()} will create a new KeyValuePurgeOptions.</p>
     */
    public static class Builder {
        private long millis = DEFAULT_THRESHOLD_MILLIS;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the delete marker threshold.
         * null will assume the default threshold {@link #DEFAULT_THRESHOLD_MILLIS}
         * {@code <= 0} will assume no threshold and will not keep any markers, same as calling {@link #deleteMarkersNoThreshold()}
         * @param millis the threshold millis
         * @return The builder
         */
        public Builder deleteMarkersThreshold(@Nullable Long millis) {
            if (millis == null) {
                this.millis = DEFAULT_THRESHOLD_MILLIS;
            }
            else if (millis <= 0) {
                this.millis = -1;
            }
            else {
                this.millis = millis;
            }
            return this;
        }

        /**
         * Set the delete marker threshold to -1 to not keep any markers
         * @return The builder
         */
        public Builder deleteMarkersNoThreshold() {
            this.millis = -1;
            return this;
        }

        /**
         * Build the Key Value Purge Options
         * @return the options
         */
        public KeyValuePurgeOptions build() {
            return new KeyValuePurgeOptions(this);
        }
    }
}
