package io.synadia.client;

/**
 * Enum representing the status of a connection
 */
public enum ConnectionStatus {
    /**
     * The {@code NatsConnection} is not connected.
     */
    DISCONNECTED,
    /**
     * The {@code NatsConnection} is currently connected.
     */
    CONNECTED,
    /**
     * The {@code NatsConnection} is currently closed.
     */
    CLOSED,
    /**
     * The {@code NatsConnection} is currently attempting to reconnect to a server from its server list.
     */
    RECONNECTING,
    /**
     * The {@code NatsConnection} is currently connecting to a server for the first
     * time.
     */
    CONNECTING;
}
