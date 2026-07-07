package io.synadia.client;

import io.synadia.client.impl.NatsConnection;

public class BadHandler implements ErrorListener, ConnectionListener {
    public void exceptionOccurred(NatsConnection conn, Exception exp) {
        throw new IllegalStateException("Intentional");
    }

    public void errorOccurred(NatsConnection conn, String type) {
        throw new IllegalStateException("Intentional");
    }

    public void slowConsumerDetected(NatsConnection conn, Subscription subscription) {
        throw new IllegalStateException("Intentional");
    }

    @Override
    public void connectionEvent(NatsConnection conn, ConnectionEvents type, Long time, String uriDetails) {
        throw new IllegalStateException("Intentional");
    }
}
