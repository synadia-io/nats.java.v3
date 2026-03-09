package io.synadia.client.impl;

import io.synadia.client.Options;

// THIS CLASS IS PACKAGED HERE BECAUSE it needs to be package scoped to have access it needs

public class MockNatsConnection extends NatsConnection {
    public MockNatsConnection(Options options) {
        super(options);
    }
}
