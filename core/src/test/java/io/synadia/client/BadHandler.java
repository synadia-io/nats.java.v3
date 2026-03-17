package io.synadia.client;

import io.synadia.client.impl.NatsConnection;

public class BadHandler implements ErrorListener, ConnectionListener {
    public void exceptionOccurred(NatsConnection conn, Exception exp) {
        throw new IllegalStateException("Intentional");
    }

    public void errorOccurred(NatsConnection conn, String type) {
        throw new IllegalStateException("Intentional");
    }

    public void connectionEvent(NatsConnection conn, ConnectionEvents type) {
        throw new IllegalStateException("Intentional");
    }
    
    public void slowConsumerDetected(NatsConnection conn, Consumer consumer) {
        throw new IllegalStateException("Intentional");
    }
}
