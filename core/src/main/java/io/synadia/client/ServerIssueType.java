// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client;

/**
 * What happened to a server, as reported by {@link ErrorListener#serverIssue ErrorListener.serverIssue}.
 */
public enum ServerIssueType {
    /**
     * An entry in an INFO message's {@code connect_urls} is not {@code host:port}. The entry is skipped and not
     * given to the server pool. nats-server only sends {@code host:port}, so this points to a problem in the system,
     * such as a misconfigured {@code advertise} value. Reported each time an INFO carries such an entry.
     */
    InvalidDiscoveredServer,

    /**
     * A connect or reconnect attempt to the server failed, for example refused, timed out, a TLS failure,
     * a server version that is too old, or an error from the server such as an authentication error.
     * The cause is the exception that ended the attempt, when there is one. An attempt ended because the
     * application closed the connection is not reported.
     */
    ConnectAttemptFailed,

    /**
     * The server pool no longer supplies the server, because it failed more times than the reconnect limit allows.
     */
    CountedOut,

    /**
     * The server rejected the credentials with the same authentication error twice in a row,
     * so the reconnect stops and the connection closes.
     */
    DoubleAuthError
}
