package io.synadia.client.impl;

import io.synadia.client.Dispatcher;

import java.util.Map;

public class NatsPackageScopeWorkarounds {

    public static Map<String, Dispatcher> getDispatchers(NatsConnection connection) {
        return ((NatsConnection)connection).getDispatchers();
    }
}
