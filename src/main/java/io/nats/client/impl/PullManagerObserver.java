package io.nats.client.impl;

import io.nats.client.Message;

interface PullManagerObserver {

    void messageReceived(Message msg);

    void pullCompletedWithStatus(int messages, long bytes);

    void pullTerminatedByError();
}
