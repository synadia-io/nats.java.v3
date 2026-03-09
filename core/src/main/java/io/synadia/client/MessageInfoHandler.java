package io.synadia.client;

import io.synadia.client.api.MessageInfo;

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
