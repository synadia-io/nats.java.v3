package io.nats.client.utils;

import io.nats.client.Options;
import io.nats.client.ServerPool;
import io.nats.client.support.NatsUri;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is simply to have a concrete implementation to test setting properties and calling the builder in Options
 */
public class CoverageServerPool implements ServerPool {
    @Override
    public void initialize(@NonNull Options opts) {
    }

    @Override
    public boolean acceptDiscoveredUrls(@NonNull List<String> discoveredServers) {
        return false;
    }

    @Override
    public NatsUri peekNextServer() {
        return new NatsUri();
    }

    @Override
    public NatsUri nextServer() {
        return new NatsUri();
    }

    @Override
    @Nullable public List<String> resolveHostToIps(@NonNull String host) {
        return null;
    }

    @Override
    public void connectSucceeded(@NonNull NatsUri nuri) {
    }

    @Override
    public void connectFailed(@NonNull NatsUri nuri) {
    }

    @Override
    @NonNull
    public List<String> getServerList() {
        return new ArrayList<>();
    }

    @Override
    public boolean hasSecureServer() {
        return false;
    }
}
