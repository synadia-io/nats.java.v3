package io.synadia.client;

/**
 * Enum representing the status of a connection
 */
public enum ConnectionStatus {
    /**
     * The {@code Connection} is not connected.
     */
    DISCONNECTED,
    /**
     * The {@code Connection} is currently connected.
     */
    CONNECTED,
    /**
     * The {@code Connection} is currently closed.
     */
    CLOSED,
    /**
     * The {@code Connection} is currently attempting to reconnect to a server from its server list.
     */
    RECONNECTING,
    /**
     * The {@code Connection} is currently connecting to a server for the first
     * time.
     */
    CONNECTING;
}
