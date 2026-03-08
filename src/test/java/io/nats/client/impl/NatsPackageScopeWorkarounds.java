package io.nats.client.impl;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;

import java.util.Map;

public class NatsPackageScopeWorkarounds {

    public static Map<String, Dispatcher> getDispatchers(Connection connection) {
        return ((NatsConnection)connection).getDispatchers();
    }
}
