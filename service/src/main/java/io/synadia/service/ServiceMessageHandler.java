package io.synadia.service;

/**
 * Interface used to receive service request message.
 */
public interface ServiceMessageHandler {
    /**
     * Called to deliver a service request message to the handler.
     * @param smsg the service message
     */
    void onMessage(ServiceMessage smsg);
}
