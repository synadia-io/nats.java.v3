package io.synadia.client.impl;

import io.synadia.client.Message;

/**
 * Callbacks the pull message manager makes as a pull progresses, so the owner of the pull can
 * decide whether to issue another one.
 */
public interface PullManagerObserver {

    /**
     * A user message was delivered against the current pull.
     * @param msg the message
     */
    void messageReceived(Message msg);

    /**
     * The server ended the pull with a status message instead of running it to completion, for
     * instance when the pull expired or hit its limit.
     * @param messages the number of messages the pull still had outstanding
     * @param bytes the number of bytes the pull still had outstanding
     */
    void pullCompletedWithStatus(int messages, long bytes);

    /**
     * The pull ended on an error that makes it pointless to pull again, so no replacement pull
     * should be issued.
     */
    void pullTerminatedByError();
}
