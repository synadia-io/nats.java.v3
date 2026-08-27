package io.synadia.client.impl;

import io.synadia.client.MessageHandler;

class NatsDispatcherWithExecutor extends NatsDispatcher {

    NatsDispatcherWithExecutor(NatsConnection conn, MessageHandler handler) {
        super(conn, handler);
    }

    @Override
    protected void deliverToHandler(MessageHandler handler, NatsMessage msg, NatsSubscription sub) {
        connection.getExecutor().execute(() -> super.deliverToHandler(handler, msg, sub));
    }
}
