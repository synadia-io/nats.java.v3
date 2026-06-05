package io.synadia.client.utils;

import io.synadia.client.ReconnectDelayHandler;

import java.time.Duration;

/**
 * Concrete ReconnectDelayHandler used to test setting it via PROP_RECONNECT_DELAY_HANDLER_CLASS.
 * Requires a public no-arg constructor for reflective instantiation.
 */
public class CoverageReconnectDelayHandler implements ReconnectDelayHandler {
    @Override
    public Duration getWaitTime(long totalTries) {
        return Duration.ofMillis(totalTries);
    }
}
