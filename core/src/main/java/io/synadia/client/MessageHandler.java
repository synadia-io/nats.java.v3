package io.synadia.client;

/**
 * {@link Dispatcher Dispatchers} use the MessageHandler interface to define the listener
 * for their messages. Each Dispatcher can have a single message handler, although the 
 * handler can use the incoming message's subject to branch for the actual work.
 */
public interface MessageHandler {
    /**
     * Called to deliver a message to the handler. This call is in the dispatcher's thread
     * and can block all other messages being delivered.
     * 
     * <p>The thread used to call onMessage will be interrupted if the connection is closed, or the dispatcher is stopped.
     *
     * @param msg the received Message
     * @throws InterruptedException if the dispatcher interrupts this handler
     */
    void onMessage(Message msg) throws InterruptedException;
}
