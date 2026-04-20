package io.synadia.client;

/**
 * A collector for connection metrics.
 * <p>
 * Information about key metrics is incremented on this collector by the connection.
 * <p>
 * See {@link Statistics} for accessing the collected metrics.
 */
public interface StatisticsCollector extends Statistics {
    /**
     * Sets whether advanced stats are/should be tracked.
     * @param trackAdvanced the advanced tracking flag. set to true to turn on advanced tracking
     */
    default void setAdvancedTracking(boolean trackAdvanced) {}

    /**
     * Increments the total number of pings that have been sent from this connection.
     */
    default void incrementPingCount() {}

    /**
     * Increments the total number of times this connection has tried to reconnect.
     */
    default void incrementReconnects() {}

    /**
     * Increments the total number of messages dropped by this connection across all slow consumers.
     */
    default void incrementDroppedCount() {}

    /**
     * Increments the total number of op +OKs received by this connection.
     */
    default void incrementOkCount() {}

    /**
     * Increments the total number of op -ERRs received by this connection.
     */
    default void incrementErrCount() {}

    /**
     * Increments the total number of exceptions seen by this connection.
     */
    default void incrementExceptionCount() {}

    /**
     * Increments the total number of requests sent by this connection.
     */
    default void incrementRequestsSent() {}

    /**
     * Increments the total number of replies received by this connection.
     */
    default void incrementRepliesReceived() {}

    /**
     * Increments the total number of duplicate replies received by this connection.
     * <p>
     * NOTE: This is only counted if advanced stats are enabled.
     */
    default void incrementDuplicateRepliesReceived() {}

    /**
     * Increments the total number of orphan replies received by this connection.
     * <p>
     * NOTE: This is only counted if advanced stats are enabled.
     */
    default void incrementOrphanRepliesReceived() {}

    /**
     * Increments the total number of messages that have come in to this connection
     * by 1 AND the number of bytes in the same call.
     * @param bytes the number of bytes
     */
    default void incrementIn(long bytes) {}

    /**
     * Increments the total number of messages that have gone out of this connection.
     * by 1 AND the number of bytes in the same call.
     * @param bytes the number of bytes
     */
    default void incrementOut(long bytes) {}

    /**
     * Increment the total number of outgoing message flushes by this connection.
     */
    default void incrementFlushCounter() {}

    /**
     * Increments the count of outstanding of requests from this connection.
     */
    default void incrementOutstandingRequests() {}

    /**
     * Decrements the count of outstanding of requests from this connection.
     */
    default void decrementOutstandingRequests() {}

    /**
     * Registers a Socket read by this connection.
     * <p>NOTE: Implementations should only count this if advanced stats are enabled.</p>
     * @param bytes the number of bytes being read
     */
    default void registerRead(long bytes) {}

    /**
     * Registers a Socket write by this connection.
     * <p>NOTE: Implementations should only count this if advanced stats are enabled.</p>
     * @param bytes the number of bytes being written
     */
    default void registerWrite(long bytes) {}

    // ----------------------------------------------------------------------------------------------------
    // Statistics defaults
    // ----------------------------------------------------------------------------------------------------

    /** {@inheritDoc} */
    default long getPings() { return 0; }

    /** {@inheritDoc} */
    default long getReconnects() { return 0; }

    /** {@inheritDoc} */
    default long getDroppedCount() { return 0; }

    /** {@inheritDoc} */
    default long getOKs() { return 0; }

    /** {@inheritDoc} */
    default long getErrs() { return 0; }

    /** {@inheritDoc} */
    default long getExceptions() { return 0; }

    /** {@inheritDoc} */
    default long getRequestsSent() { return 0; }

    /** {@inheritDoc} */
    default long getRepliesReceived() { return 0; }

    /** {@inheritDoc} */
    default long getDuplicateRepliesReceived() { return 0; }

    /** {@inheritDoc} */
    default long getOrphanRepliesReceived() { return 0; }

    /** {@inheritDoc} */
    default long getInMsgs() { return 0; }

    /** {@inheritDoc} */
    default long getOutMsgs() { return 0; }

    /** {@inheritDoc} */
    default long getInBytes() { return 0; }

    /** {@inheritDoc} */
    default long getOutBytes() { return 0; }

    /** {@inheritDoc} */
    default long getFlushCounter() { return 0; }

    /** {@inheritDoc} */
    default long getOutstandingRequests() { return 0; }
}
