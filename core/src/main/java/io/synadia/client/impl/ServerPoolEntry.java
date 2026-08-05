package io.synadia.client.impl;

import io.synadia.client.utils.NatsUri;

/**
 * One server in the connection's server pool, along with the connect history the pool
 * uses to decide ordering and back off.
 */
public class ServerPoolEntry {
    /** The server address. */
    public final NatsUri nuri;

    /** True when the server was learned from an INFO message rather than configured by the user. */
    public final boolean isGossiped;

    /** Consecutive failed connect attempts to this server, reset on a successful connect. */
    public int failedAttempts;

    /** Wall clock time in millis of the last connect attempt, 0 if never attempted. */
    public long lastAttempt;

    /**
     * Construct an entry with no attempt history.
     * @param nuri the server address
     * @param isGossiped true if the server came from an INFO message
     */
    public ServerPoolEntry(NatsUri nuri, boolean isGossiped) {
        this.nuri = nuri;
        this.isGossiped = isGossiped;
    }

    @Override
    public String toString() {
        return nuri + " " + isGossiped + "/" + failedAttempts;
    }
}
