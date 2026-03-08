package io.nats.client.api;

import java.time.Duration;

/**
 * Options used when purging keys
 */
public class KeyValuePurgeOptions {

    /**
     * The default time in millis that is used for as the threshold to keep markers.
     */
    public static final long DEFAULT_THRESHOLD_MILLIS = Duration.ofMinutes(30).toMillis();

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
         * Null or duration of 0 will assume the default threshold {@link #DEFAULT_THRESHOLD_MILLIS}
         * Duration less than zero will assume no threshold and will not keep any markers.
         * @param deleteMarkersThreshold the threshold duration or null
         * @return The builder
         */
        public Builder deleteMarkersThreshold(Duration deleteMarkersThreshold) {
            this.deleteMarkersThresholdMillis = deleteMarkersThreshold == null
                ? DEFAULT_THRESHOLD_MILLIS : deleteMarkersThreshold.toMillis();
            return this;
        }

        /**
         * Set the delete marker threshold.
         * 0 will assume the default threshold {@link #DEFAULT_THRESHOLD_MILLIS}
         * Less than zero will assume no threshold and will not keep any markers.
         * @param deleteMarkersThresholdMillis the threshold millis
         * @return The builder
         */
        public Builder deleteMarkersThreshold(long deleteMarkersThresholdMillis) {
            this.deleteMarkersThresholdMillis = deleteMarkersThresholdMillis;
            return this;
        }

        /**
         * Set the delete marker threshold to -1 so as to not keep any markers
         * @return The builder
         */
        public Builder deleteMarkersNoThreshold() {
            this.deleteMarkersThresholdMillis = -1;
            return this;
        }

        /**
         * Build the Key Value Purge Options
         * @return the options
         */
        public KeyValuePurgeOptions build() {
            if (deleteMarkersThresholdMillis < 0) {
                deleteMarkersThresholdMillis = -1;
            }
            else if (deleteMarkersThresholdMillis == 0) {
                deleteMarkersThresholdMillis = DEFAULT_THRESHOLD_MILLIS;
            }
            return new KeyValuePurgeOptions(this);
        }
    }
}
