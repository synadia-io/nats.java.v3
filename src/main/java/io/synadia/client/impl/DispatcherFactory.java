package io.synadia.client.impl;

import io.synadia.client.MessageHandler;

/**
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *
 * WARNING: THIS CLASS IS PUBLIC BUT ITS API IS NOT GUARANTEED TO *
 * BE BACKWARD COMPATIBLE AS IT IS INTENDED AS AN INTERNAL CLASS  *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *
 */
public class DispatcherFactory {
    NatsDispatcher createDispatcher(NatsConnection conn, MessageHandler handler) {
        if (conn.getOptions().useDispatcherWithExecutor()) {
            return new NatsDispatcherWithExecutor(conn, handler);
        }
        return new NatsDispatcher(conn, handler);
    }
}
