package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class ConnectionListenerTests extends TestBase {

    @Test
    public void testToString() {
        assertEquals("nats: connection closed", ConnectionEvent.CLOSED.toString());
    }
    
    @Test
    public void testCloseEvent() throws Exception {
        Listener listener = new Listener();
        listener.queueConnectionEvent(ConnectionEvent.CLOSED);
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
                listener.queueConnectionEvent(ConnectionEvent.DISCOVERED_SERVERS);
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
                .reconnectWait(100L)
                .maxReconnects(-1)
                .connectionListener(listener)
                .build();
            port = ts.getNatsPort();
            nc = managedConnect(options);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        }

        try { nc.flush(250); } catch (Exception exp) { /* ignored */ }

        listener.validate();
        assertNull(nc.getConnectedUrl());

        listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
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
    public void testMultipleConnectionListenersInOptions() throws Exception {
        // listeners supplied in the Options are attached from construction, so they also see CONNECTED
        // and DISCONNECTED - only CLOSED is recorded to keep the assertion deterministic
        Set<String> capturedEvents = ConcurrentHashMap.newKeySet();
        ConnectionListener cl1 = (conn, event, time, details) -> {
            if (event == ConnectionEvent.CLOSED) {
                capturedEvents.add("CL1-" + event.name());
            }
        };
        ConnectionListener cl2 = (conn, event, time, details) -> {
            if (event == ConnectionEvent.CLOSED) {
                capturedEvents.add("CL2-" + event.name());
            }
        };

        OptionsBuilder builder = optionsBuilder().connectionListener(cl1, cl2);
        assertEquals(2, builder.build().getConnectionListeners().size());

        runInSharedOwnNc(builder, nc -> {
            closeAndConfirm(nc);
            assertNull(nc.getConnectedUrl());
        });

        Set<String> expectedEvents = new HashSet<>(Arrays.asList("CL1-CLOSED", "CL2-CLOSED"));
        assertEquals(expectedEvents, capturedEvents);
    }

    @Test
    public void testRemoveConnectionListenerById() throws Exception {
        Set<String> capturedEvents = ConcurrentHashMap.newKeySet();
        ConnectionListener stays = (conn, event, time, details) -> capturedEvents.add("STAYS-" + event.name());
        ConnectionListener goes = (conn, event, time, details) -> capturedEvents.add("NEVER INVOKED");

        runInSharedOwnNc(nc -> {
            nc.addConnectionListener(stays);
            nc.addConnectionListener(goes);
            nc.removeConnectionListenerById(goes.getConnectionListenerId());
            nc.removeConnectionListenerById("not-a-registered-id"); // no-op

            closeAndConfirm(nc);
        });

        assertEquals(new HashSet<>(Collections.singletonList("STAYS-CLOSED")), capturedEvents);
    }

    @Test
    public void testMultipleConnectionListeners() throws Exception {
        Set<String> capturedEvents = ConcurrentHashMap.newKeySet();
        Listener listener = new Listener();
        listener.queueConnectionEvent(ConnectionEvent.CLOSED);
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
        assertTrue(ConnectionEvent.CONNECTED.isConnectionEvent());
        assertTrue(ConnectionEvent.CLOSED.isConnectionEvent());
        assertTrue(ConnectionEvent.DISCONNECTED.isConnectionEvent());
        assertTrue(ConnectionEvent.RECONNECTED.isConnectionEvent());
        assertFalse(ConnectionEvent.RESUBSCRIBED.isConnectionEvent());
        assertFalse(ConnectionEvent.DISCOVERED_SERVERS.isConnectionEvent());
        assertFalse(ConnectionEvent.LAME_DUCK.isConnectionEvent());

        assertEquals("opened", ConnectionEvent.CONNECTED.getEvent());
        assertEquals("nats: connection opened", ConnectionEvent.CONNECTED.getNatsEvent());
        assertEquals(ConnectionEvent.CONNECTED.getNatsEvent(), ConnectionEvent.CONNECTED.toString());

        assertEquals("lame duck mode", ConnectionEvent.LAME_DUCK.getEvent());
        assertEquals("nats: lame duck mode", ConnectionEvent.LAME_DUCK.getNatsEvent());
        assertEquals(ConnectionEvent.LAME_DUCK.getNatsEvent(), ConnectionEvent.LAME_DUCK.toString());
    }
}
