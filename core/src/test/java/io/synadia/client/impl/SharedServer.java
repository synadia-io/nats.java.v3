package io.synadia.client.impl;

import io.synadia.client.ConnectionStatus;
import io.synadia.client.NUID;
import io.synadia.client.NatsTestServer;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.utils.ConnectionUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.client.NatsTestServer.configFileBuilder;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static io.synadia.client.utils.VersionUtils.initVersionServerInfo;

/**
 * This class is in the impl package instead of the support package
 * so it can access the package scope class NatsConnection
 */
public class SharedServer {

    private static final int NUM_REUSABLE_CONNECTIONS = 3;
    private static final Thread SHARED_SHUTDOWN_HOOK_THREAD;
    private static final Map<String, SharedServer> SHARED_BY_NAME;
    private static final Map<String, SharedServer> SHARED_BY_URL;
    private static final ReentrantLock STATIC_LOCK;

    static {
        STATIC_LOCK = new ReentrantLock();
        SHARED_BY_NAME = new HashMap<>();
        SHARED_BY_URL = new HashMap<>();
        SHARED_SHUTDOWN_HOOK_THREAD = new Thread("Reusables-Shutdown-Hook") {
            @Override
            public void run() {
                for (SharedServer rs : SHARED_BY_NAME.values()) {
                    rs.shutdown();
                }
                SHARED_BY_NAME.clear();
                SHARED_BY_URL.clear();
            }
        };
        Runtime.getRuntime().addShutdownHook(SHARED_SHUTDOWN_HOOK_THREAD);
    }

    private final ReentrantLock instanceLock;
    private final String reusableConnectionPrefix;
    private final Map<String, NatsConnection> connectionMap;
    private final AtomicInteger currentReusableId;
    private NatsTestServer natsTestServer;

    public final String serverUrl;

    public static SharedServer getInstance(String name) throws IOException {
        return getInstance(name, null);
    }

    public static SharedServer getInstance(String name, String confFile) throws IOException {
        STATIC_LOCK.lock();
        try {
            SharedServer shared = SHARED_BY_NAME.get(name);
            if (shared == null) {
                shared = new SharedServer(name, confFile);
                SHARED_BY_NAME.put(name, shared);
                SHARED_BY_URL.put(shared.serverUrl, shared);
            }
            return shared;
        }
        finally {
            STATIC_LOCK.unlock();
        }
    }

    public static void shutdown(String... names) {
        for (String name : names) {
            SharedServer shared = SHARED_BY_NAME.get(name);
            if (shared != null) {
                SHARED_BY_NAME.remove(name);
                SHARED_BY_URL.remove(shared.serverUrl);
                shared.shutdown();
            }
        }
    }

    private SharedServer(@NonNull String name, @Nullable String confFile) throws IOException {
        instanceLock = new ReentrantLock();
        reusableConnectionPrefix = new NUID().next();
        connectionMap = new HashMap<>();
        currentReusableId = new AtomicInteger(-1);
        if (confFile == null) {
            natsTestServer = new NatsTestServer(
                NatsTestServer.builder()
                    .jetstream(true)
                    .customName(name)
            );
        }
        else {
            natsTestServer = new NatsTestServer(configFileBuilder(confFile)
                .customName(name));
        }
        serverUrl = natsTestServer.getServerUri();
    }

    public NatsTestServer getServer() {
        return natsTestServer;
    }

    public NatsConnection getSharedConnection() {
        int id = currentReusableId.incrementAndGet();
        if (id >= NUM_REUSABLE_CONNECTIONS) {
            currentReusableId.set(0);
            id = 0;
        }
        return getSharedConnection(reusableConnectionPrefix + "-" + id);
    }

    public static NatsConnection sharedConnectionForServer(NatsTestServer ts) {
        for (Map.Entry<String, SharedServer> entry : SHARED_BY_NAME.entrySet()) {
            SharedServer shared = entry.getValue();
            if (shared.natsTestServer == ts) {
                return shared.getSharedConnection();
            }
        }
        throw new RuntimeException("No shared matching server.");
    }

    public static NatsConnection sharedConnectionForSameServer(NatsConnection nc) {
        SharedServer shared = SHARED_BY_URL.get(nc.getConnectedUrl());
        if (shared == null) {
            throw new RuntimeException("No shared server for that connection.");
        }
        return shared.getSharedConnection();
    }

    public static NatsConnection connectionForSameServer(NatsConnection nc, OptionsBuilder builder) {
        SharedServer shared = SHARED_BY_URL.get(nc.getConnectedUrl());
        if (shared == null) {
            throw new RuntimeException("No shared server for that connection.");
        }
        return shared.newConnection(builder);
    }

    private void waitUntilStatus(NatsConnection conn) {
        for (long x = 0; x < 100; x++) {
            sleep(100);
            if (conn.getStatus() == ConnectionStatus.CONNECTED) {
                return;
            }
        }
    }

    private NatsConnection getSharedConnection(String name) {
        instanceLock.lock();
        try {
            NatsConnection ncs = connectionMap.get(name);
            if (ncs == null) {
                ncs = newConnection(optionsBuilder());
                connectionMap.put(name, ncs);
                waitUntilStatus(ncs);
                initVersionServerInfo(ncs);
            }
            else if (ncs.getStatus() != ConnectionStatus.CONNECTED) {
                try { ncs.close(); } catch (Exception ignore) {}
                return getSharedConnection(name);
            }
            return ncs;
        }
        finally {
            instanceLock.unlock();
        }
    }

    public NatsConnection newConnection(OptionsBuilder builder) {
        return ConnectionUtils.managedConnect(builder.server(serverUrl).build());
    }

    public void shutdown() {
        instanceLock.lock();
        try {
            for (NatsConnection nc : connectionMap.values()) {
                try {
                    nc.close(false, true);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (natsTestServer != null) {
                try {
                    natsTestServer.shutdown(false);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        finally {
            connectionMap.clear();
            natsTestServer = null;
            instanceLock.unlock();
        }
    }
}
