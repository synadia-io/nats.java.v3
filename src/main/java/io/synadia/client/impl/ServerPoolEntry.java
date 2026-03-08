package io.synadia.client.impl;

import io.synadia.client.support.NatsUri;

public class ServerPoolEntry {
    public final NatsUri nuri;
    public final boolean isGossiped;
    public int failedAttempts;
    public long lastAttempt;

    public ServerPoolEntry(NatsUri nuri, boolean isGossiped) {
        this.nuri = nuri;
        this.isGossiped = isGossiped;
    }

    @Override
    public String toString() {
        return nuri + " " + isGossiped + "/" + failedAttempts;
    }
}
