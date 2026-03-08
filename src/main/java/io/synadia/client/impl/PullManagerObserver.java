package io.synadia.client.impl;

import io.synadia.client.Message;

interface PullManagerObserver {

    void messageReceived(Message msg);

    void pullCompletedWithStatus(int messages, long bytes);

    void pullTerminatedByError();
}
