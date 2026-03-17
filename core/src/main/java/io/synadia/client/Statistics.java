package io.synadia.client;

/**
 * Connections can provide an instance of Statistics, {@link NatsConnection#getStatistics() getStatistics()}. The statistics
 * object provides information about key metrics related to the connection over its entire lifecycle.
 *
 * <p>The Statistics toString() provides a summary of the statistics.
 */
public interface Statistics {

    /**
     * the total number of pings that have been sent from this connection.
     * @return the total number of pings that have been sent from this connection.
     */
    long getPings();

    /**
     * the total number of times this connection has tried to reconnect.
     * @return the total number of times this connection has tried to reconnect.
     */
    long getReconnects();

    /**
     * the total number of messages dropped by this connection across all slow consumers.
     * @return the total number of messages dropped by this connection across all slow consumers.
     */
    long getDroppedCount();

    /**
     * the total number of op +OKs received by this connection.
     * @return the total number of op +OKs received by this connection.
     */
    long getOKs();

    /**
     * the total number of op -ERRs received by this connection.
     * @return the total number of op -ERRs received by this connection.
     */
    long getErrs();

    /**
     * the total number of exceptions seen by this connection.
     * @return the total number of exceptions seen by this connection.
     */
    long getExceptions();

    /**
     * the total number of requests sent by this connection.
     * @return the total number of requests sent by this connection.
     */
    long getRequestsSent();

    /**
     * the total number of replies received by this connection.
     * @return the total number of replies received by this connection.
     */
    long getRepliesReceived();

    /**
     * the total number of duplicate replies received by this connection.
     * @return the total number of duplicate replies received by this connection.
     *
     * NOTE: This is only counted if advanced stats are enabled.
     */
    long getDuplicateRepliesReceived();

    /**
     * the total number of orphan replies received by this connection.
     * @return the total number of orphan replies received by this connection.
     *
     * NOTE: This is only counted if advanced stats are enabled.
     */
    long getOrphanRepliesReceived();

    /**
     * the total number of messages that have come in to this connection.
     * @return the total number of messages that have come in to this connection.
     */
    long getInMsgs();

    /**
     * the total number of messages that have gone out of this connection.
     * @return the total number of messages that have gone out of this connection.
     */
    long getOutMsgs();

    /**
     * the total number of message bytes that have come in to this connection.
     * @return the total number of message bytes that have come in to this connection.
     */
    long getInBytes();

    /**
     * the total number of message bytes that have gone out of this connection.
     * @return the total number of message bytes that have gone out of this connection.
     */
    long getOutBytes();

    /**
     * the total number of outgoing message flushes by this connection.
     * @return the total number of outgoing message flushes by this connection.
     */
    long getFlushCounter();

    /**
     * the count of outstanding of requests from this connection.
     * @return the count of outstanding of requests from this connection.
     */
    long getOutstandingRequests();
}
