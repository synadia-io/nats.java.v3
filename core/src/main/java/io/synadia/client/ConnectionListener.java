package io.synadia.client;

import io.synadia.client.impl.NatsConnection;

/**
 * Applications can use a ConnectionListener to track the status of a {@link NatsConnection NatsConnection}. The
 * listener is configured in the {@link Options Options} at creation time.
 */
public interface ConnectionListener {

    /**
     * @deprecated use new api that gives additional details
     * Connection related events that occur asynchronously in the client code are
     * sent to a ConnectionListener via a single method. The ConnectionListener can
     * use the event type to decide what to do about the problem.
     *
     * @param conn the connection associated with the error
     * @param type the type of event that has occurred
     */
    @Deprecated
    void connectionEvent(NatsConnection conn, ConnectionEvents type);

    /**
     * Connection related events that occur asynchronously in the client code are
     * sent to a ConnectionListener via a single method. The ConnectionListener can
     * use the event type to decide what to do about the problem.
     * @param conn the connection associated with the error
     * @param type the type of event that has occurred
     * @param time the time of the event, milliseconds since 1/1/1970
     * @param uriDetails extra details about the uri related to this connection event
     */
    default void connectionEvent(NatsConnection conn, ConnectionEvents type, Long time, String uriDetails) {
        connectionEvent(conn, type);
    }
}
