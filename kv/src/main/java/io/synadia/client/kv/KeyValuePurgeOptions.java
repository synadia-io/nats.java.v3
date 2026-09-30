package io.synadia.client.kv;

import org.jspecify.annotations.NullMarked;

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

    /**
     * The value in millis that is used for as the threshold to NOT keep any markers.
     */
    public static final long NO_THRESHOLD_MILLIS = -1;

    private final long deleteMarkersThresholdMillis;

    private KeyValuePurgeOptions(Builder b) {
        this.deleteMarkersThresholdMillis = b.deleteMarkersThresholdMillis;
    }

    /**
     * The value of the delete marker threshold, in milliseconds.
     * @return the threshold
     */
    public long getDeleteMarkersThresholdMillis() {
        return deleteMarkersThresholdMillis;
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
        private long deleteMarkersThresholdMillis = DEFAULT_THRESHOLD_MILLIS;

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Set the delete marker threshold.
         * {@code <= 0} sets the delete marker threshold to not keep any markers, the same as calling {@link #deleteMarkersNoThreshold()}
         * @param millis the threshold millis
         * @return The builder
         */
        public Builder deleteMarkersThreshold(long millis) {
            if (millis <= 0) {
                this.deleteMarkersThresholdMillis = NO_THRESHOLD_MILLIS;
            }
            else {
                this.deleteMarkersThresholdMillis = millis;
            }
            return this;
        }

        /**
         * Set the delete marker threshold to not keep any markers.
         * @return The builder
         */
        public Builder deleteMarkersNoThreshold() {
            this.deleteMarkersThresholdMillis = NO_THRESHOLD_MILLIS;
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
