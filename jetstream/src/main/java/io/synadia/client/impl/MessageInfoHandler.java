package io.synadia.client.impl;

/**
 * Handler for {@link MessageInfo}.
 */
public interface MessageInfoHandler {
    /**
     * Called to deliver a {@link MessageInfo} to the handler.
     *
     * @param messageInfo the received {@link MessageInfo}
     */
    void onMessageInfo(MessageInfo messageInfo);
}
