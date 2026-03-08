package io.nats.client.utils;

import io.nats.client.impl.SocketDataPort;

import java.io.IOException;

public class CloseOnUpgradeAttempt extends SocketDataPort {
    public CloseOnUpgradeAttempt() {
        super(); // Start with a very small buffer size
    }

    @Override
    public void upgradeToSecure() throws IOException {
        this.close();
        super.upgradeToSecure();
    }
}
