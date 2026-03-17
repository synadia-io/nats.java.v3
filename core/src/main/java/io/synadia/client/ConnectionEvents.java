package io.synadia.client;

/**
 * Enum for connection events
 */
public enum ConnectionEvents {
    /** The connection has successfully completed the handshake with the nats-server. */
    CONNECTED(true, "opened"),
    /** The connection is permanently closed, either by manual action or failed reconnects. */
    CLOSED(true, "closed"),
    /** The connection lost its connection, but may try to reconnect if configured to. */
    DISCONNECTED(true, "disconnected"),
    /** The connection was connected, lost its connection and successfully reconnected. */
    RECONNECTED(true, "reconnected"),
    /** The connection was reconnected and the server has been notified of all subscriptions. */
    RESUBSCRIBED(false, "subscriptions re-established"),
    /** The connection was made aware of new servers from the current server connection. */
    DISCOVERED_SERVERS(false, "discovered servers"),
    /** Server Sent a lame duck mode. */
    LAME_DUCK(false, "lame duck mode");

    private final boolean connectionEvent;
    private final String event;
    private final String natsEvent;

    /**
     * Construct an events enum
     * @param connectionEvent whether this is a connection event
     * @param event the simple event text
     */
    ConnectionEvents(boolean connectionEvent, String event) {
        this.connectionEvent = connectionEvent;
        this.event = event;
        if (connectionEvent) {
            this.natsEvent = "nats: connection " + event;
        }
        else {
            this.natsEvent = "nats: " + event;
        }
    }

    /**
     * Whether this event is a connection event.
     * @return the flag
     */
    public boolean isConnectionEvent() {
        return connectionEvent;
    }

    /**
     * Get the simple event text
     * @return the text
     */
    public String getEvent() {
        return event;
    }

    /**
     * Get the event text calculated with if it's a connection event and prefixed with "nats:"
     * @return the text
     */
    public String getNatsEvent() {
        return natsEvent;
    }

    /**
     * @return the string value for this event
     */
    public String toString() {
        return this.natsEvent;
    }
}
