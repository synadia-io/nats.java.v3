package io.nats.client.impl;

import io.nats.client.Options;

// THIS CLASS IS PACKAGED HERE BECAUSE it needs to be package scoped to have access it needs

public class MockNatsConnection extends NatsConnection {
    public MockNatsConnection(Options options) {
        super(options);
    }
}
