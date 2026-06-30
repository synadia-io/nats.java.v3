package io.synadia.client;

/**
 * The PublishOptions class specifies the options for publishing with JetStream enabled servers.
 * Options are created using a {@link ForceReconnectOptions.Builder Builder}.
 */
public class ForceReconnectOptions {

    /**
     * A default instance of ForceReconnectOptions
     */
    public static final ForceReconnectOptions DEFAULT_INSTANCE = ForceReconnectOptions.builder().build();

    /**
     * An instance representing the force close option
     */
    public static final ForceReconnectOptions FORCE_CLOSE_INSTANCE = ForceReconnectOptions.builder().forceClose().build();

    private final boolean forceClose;
    private final long flushWait;

    private ForceReconnectOptions(Builder b) {
        this.forceClose = b.forceClose;
        this.flushWait = b.flushWait;
    }

    /**
     * True if these options represent force close
     * @return the flag
     */
    public boolean isForceClose() {
        return forceClose;
    }

    /**
     * True if these options represent to flush
     * @return the flag
     */
    public boolean isFlush() {
        return flushWait > 0;
    }

    /**
     * Get the flush wait setting, in milliseconds
     * @return the flush wait in milliseconds, or 0 if there is no flush wait
     */
    public long getFlushWait() {
        return flushWait;
    }

    /**
     * Creates a builder for the options.
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * ForceReconnectOptions are created using a Builder.
     */
    public static class Builder {
        boolean forceClose = false;
        long flushWait;

        /**
         * Constructs a new Builder with the default values.
         */
        public Builder() {}

        /**
         * set the force close flag to true
         * @return the builder
         */
        public Builder forceClose() {
            this.forceClose = true;
            return this;
        }

        /**
         * if supplied and at least 1 millisecond, the forceReconnect will try to
         * flush before closing for the specified wait time. Flush happens before close
         * so not affected by forceClose option
         * @param millis the flush wait millis; less than 1 means no flush
         * @return the builder
         */
        public Builder flush(long millis) {
            this.flushWait = millis > 0 ? millis : 0;
            return this;
        }

        /**
         * Builds the ForceReconnectOptions.
         * @return ForceReconnectOptions
         */
        public ForceReconnectOptions build() {
            return new ForceReconnectOptions(this);
        }
    }
}
