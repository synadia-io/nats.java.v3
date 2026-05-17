package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.testutils.Listener;
import io.synadia.client.testutils.TestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.testutils.ConnectionUtils.*;
import static io.synadia.client.testutils.OptionsUtils.optionsBuilder;
import static io.synadia.client.testutils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class ConnectionListenerTests extends TestBase {

    @Test
    public void testToString() {
        assertEquals("nats: connection closed", ConnectionEvents.CLOSED.toString());
    }
    
    @Test
    public void testCloseEvent() throws Exception {
        Listener listener = new Listener();
        listener.queueConnectionEvent(ConnectionEvents.CLOSED);
        OptionsBuilder builder = optionsBuilder().connectionListener(listener);
        runInSharedOwnNc(builder, nc -> {
            closeAndConfirm(nc);
            assertNull(nc.getConnectedUrl());
        });
        listener.validate();
    }

    @Test
    public void testDiscoveredServersCountAndListenerInOptions() throws Exception {
        runInSharedServer(ts -> {
            String customInfo = "{\"server_id\":\"myid\", \"version\":\"9.9.99\",\"connect_urls\": [\""+ts.getServerUri()+"\"]}";
            try (NatsServerProtocolMock mockTs2 = new NatsServerProtocolMock(null, customInfo)) {
                Listener listener = new Listener();
                Options options = optionsBuilder(mockTs2)
                    .maxReconnects(0)
                    .connectionListener(listener)
                    .build();
                listener.queueConnectionEvent(ConnectionEvents.DISCOVERED_SERVERS);
                try (NatsConnection ignore = standardConnect(options)) {
                    listener.validate();
                }
            }
        });
    }

    @Test
    public void testDisconnectReconnectCount() throws Exception {
        int port;
        NatsConnection nc;
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .reconnectWait(Duration.ofMillis(100))
                .maxReconnects(-1)
                .connectionListener(listener)
                .build();
            port = ts.getPort();
            nc = managedConnect(options);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            listener.queueConnectionEvent(ConnectionEvents.DISCONNECTED);
        }

        try { nc.flush(Duration.ofMillis(250)); } catch (Exception exp) { /* ignored */ }

        listener.validate();
        assertNull(nc.getConnectedUrl());

        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        try (NatsTestServer ts = new NatsTestServer(port)) {
            confirmConnected(nc); // wait for reconnect
            listener.validate();
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testExceptionInConnectionListener() throws Exception {
        BadHandler badHandler = new BadHandler();
        OptionsBuilder builder = optionsBuilder().connectionListener(badHandler);
        AtomicReference<Statistics> stats = new AtomicReference<>();
        runInSharedOwnNc(builder, nc -> stats.set(nc.getStatistics()));
        sleep(100); // it needs time here
        assertTrue(stats.get().getExceptions() > 0);
    }

    @Test
    public void testMultipleConnectionListeners() throws Exception {
        Set<String> capturedEvents = ConcurrentHashMap.newKeySet();
        Listener listener = new Listener();
        listener.queueConnectionEvent(ConnectionEvents.CLOSED);
        AtomicReference<Statistics> stats = new AtomicReference<>();
        OptionsBuilder builder = optionsBuilder().connectionListener(listener);
        runInSharedOwnNc(builder, nc -> {
            stats.set(nc.getStatistics());

            //noinspection DataFlowIssue // parameter is annotated as @NonNull
            assertThrows(NullPointerException.class, () -> nc.addConnectionListener(null));
            //noinspection DataFlowIssue // parameter is annotated as @NonNull
            assertThrows(NullPointerException.class, () -> nc.removeConnectionListener(null));

            ConnectionListener removedConnectionListener = (conn, event, time, details) -> capturedEvents.add("NEVER INVOKED");
            nc.addConnectionListener(removedConnectionListener);
            nc.addConnectionListener((conn, event, time, details) -> capturedEvents.add("CL1-" + event.name()));
            nc.addConnectionListener((conn, event, time, details) -> capturedEvents.add("CL2-" + event.name()));
            nc.addConnectionListener((conn, event, time, details) -> { throw new RuntimeException("should not interfere with other listeners"); });
            nc.addConnectionListener((conn, event, time, details) -> capturedEvents.add("CL3-" + event.name()));
            nc.addConnectionListener((conn, event, time, details) -> capturedEvents.add("CL4-" + event.name()));
            nc.removeConnectionListener(removedConnectionListener);

            closeAndConfirm(nc);
            assertNull(nc.getConnectedUrl());
        });

        assertTrue(stats.get().getExceptions() > 0);
        listener.validate();

        Set<String> expectedEvents = new HashSet<>(Arrays.asList(
                "CL1-CLOSED",
                "CL2-CLOSED",
                "CL3-CLOSED",
                "CL4-CLOSED"));
        assertEquals(expectedEvents, capturedEvents);
    }

    @Test
    public void testConnectionListenerEventCoverage() {
        assertTrue(ConnectionEvents.CONNECTED.isConnectionEvent());
        assertTrue(ConnectionEvents.CLOSED.isConnectionEvent());
        assertTrue(ConnectionEvents.DISCONNECTED.isConnectionEvent());
        assertTrue(ConnectionEvents.RECONNECTED.isConnectionEvent());
        assertFalse(ConnectionEvents.RESUBSCRIBED.isConnectionEvent());
        assertFalse(ConnectionEvents.DISCOVERED_SERVERS.isConnectionEvent());
        assertFalse(ConnectionEvents.LAME_DUCK.isConnectionEvent());

        assertEquals("opened", ConnectionEvents.CONNECTED.getEvent());
        assertEquals("nats: connection opened", ConnectionEvents.CONNECTED.getNatsEvent());
        assertEquals(ConnectionEvents.CONNECTED.getNatsEvent(), ConnectionEvents.CONNECTED.toString());

        assertEquals("lame duck mode", ConnectionEvents.LAME_DUCK.getEvent());
        assertEquals("nats: lame duck mode", ConnectionEvents.LAME_DUCK.getNatsEvent());
        assertEquals(ConnectionEvents.LAME_DUCK.getNatsEvent(), ConnectionEvents.LAME_DUCK.toString());
    }
}
