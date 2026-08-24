package io.synadia.client.impl;

import io.synadia.client.Dispatcher;

import java.util.Set;

public class NatsPackageScopeWorkarounds {

    public static Set<Dispatcher> getDispatchers(NatsConnection connection) {
        return connection.getDispatchers();
    }
}
