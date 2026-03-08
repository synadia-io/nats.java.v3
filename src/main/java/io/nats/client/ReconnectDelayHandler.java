package io.nats.client;

import java.time.Duration;

/**
 * Allows the developer to provide the duration of time to before reconnecting a second or more
 * time through the servers list
 */
public interface ReconnectDelayHandler {

    /**
     * Get the duration of time to wait before trying to reconnect against the server list
     *
     * @param totalTries the total number of individual tries to connect to a server
     *
     * @return The wait time
     */
    Duration getWaitTime(long totalTries);
}
