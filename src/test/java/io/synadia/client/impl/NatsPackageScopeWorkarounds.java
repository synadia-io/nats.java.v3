package io.synadia.client.impl;

import io.synadia.client.Connection;
import io.synadia.client.Dispatcher;

import java.util.Map;

public class NatsPackageScopeWorkarounds {

    public static Map<String, Dispatcher> getDispatchers(Connection connection) {
        return ((NatsConnection)connection).getDispatchers();
    }
}
