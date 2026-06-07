package io.synadia.client.utils;

import io.synadia.client.Options;
import io.synadia.client.ReconnectDelayHandler;

/**
 * Concrete ReconnectDelayHandler used to test setting it via PROP_RECONNECT_DELAY_HANDLER_CLASS.
 * Requires a public no-arg constructor for reflective instantiation.
 */
public class CoverageReconnectDelayHandler implements ReconnectDelayHandler {
    @Override
    public long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered) {
        return round;
    }
}
