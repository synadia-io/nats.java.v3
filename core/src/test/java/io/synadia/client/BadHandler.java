package io.synadia.client;

public class BadHandler implements ErrorListener, ConnectionListener {
    public void exceptionOccurred(Connection conn, Exception exp) {
        throw new IllegalStateException("Intentional");
    }

    public void errorOccurred(Connection conn, String type) {
        throw new IllegalStateException("Intentional");
    }

    public void connectionEvent(Connection conn, Events type) {
        throw new IllegalStateException("Intentional");
    }
    
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        throw new IllegalStateException("Intentional");
    }
}
