package io.synadia.client.impl;

import io.synadia.client.MessageHandler;

import java.util.concurrent.TimeUnit;

class NatsDispatcherWithExecutor extends NatsDispatcher {

    NatsDispatcherWithExecutor(NatsConnection conn, MessageHandler handler) {
        super(conn, handler);
    }

    @Override
    public void run() {
        try {
            while (running.get() && !Thread.interrupted()) {
                NatsMessage msg = this.incoming.pop(WAIT_FOR_MESSAGE_MINUTES, TimeUnit.MINUTES);
                if (msg != null) {
                    NatsSubscription sub = msg.getNatsSubscription();
                    if (sub != null && sub.isActive()) {
                        MessageHandler handler = nonDefaultHandlerBySid.get(sub.getSID());
                        if (handler == null) {
                            handler = defaultHandler;
                        }
                        // A dispatcher can have a null defaultHandler. You can't subscribe without a handler,
                        // but messages might come in while the dispatcher is being closed or after unsubscribe
                        // and the [non-default] handler has already been removed from subscriptionHandlers
                        if (handler != null) {
                            sub.incrementDeliveredCount();
                            this.incrementDeliveredCount();

                            MessageHandler finalHandler = handler;
                            connection.getExecutor().execute(() -> {
                                try {
                                    finalHandler.onMessage(msg);
                                } catch (Exception exp) {
                                    connection.processException(exp);
                                } catch (Error err) {
                                    connection.processException(new Exception(err));
                                }

                                if (sub.reachedUnsubLimit()) {
                                    connection.invalidate(sub);
                                }
                            });
                        }
                    }
                }

                if (breakRunLoop()) {
                    return;
                }
            }
        }
        catch (InterruptedException exp) {
            if (this.running.get()){
                this.connection.processException(exp);
            } //otherwise we did it
            Thread.currentThread().interrupt();
        }
        finally {
            this.running.set(false);
            this.thread = null;
        }
    }
}
