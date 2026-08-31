package io.synadia.client;

import io.synadia.client.NatsServerProtocolMock.ExitAt;
import io.synadia.client.api.ServerInfo;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.SimulateSocketDataPortException;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.SSLUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.OptionsUtils.options;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.TestBase.*;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class ConnectTests {
    @Test
    public void testConnectWithConfig() throws Exception {
        runInConfiguredServer("simple.conf", ts -> assertCanConnect(optionsBuilder(ts).build()));
    }

    @Test
    public void testConnectVariants() throws Exception {
        try (NatsTestServer ts1 = new NatsTestServer()) {
            try (NatsTestServer ts2 = new NatsTestServer()) {
                // commas in one server url
                Options options = optionsBuilder().server(ts1.getServerUri() + "," + ts2.getServerUri()).build();
                try (NatsConnection nc = managedConnect(options)) {
                    // coverage for getClientAddress
                    InetAddress inetAddress = nc.getClientInetAddress();
                    assertNotNull(inetAddress);
                    assertTrue(inetAddress.equals(InetAddress.getLoopbackAddress())
                        || inetAddress.equals(InetAddress.getLocalHost()));
                }

                // Randomize
                boolean needOne = true;
                boolean needTwo = true;
                int tries = 20;
                options = options(ts1, ts2);
                while (tries-- > 0 && (needOne || needTwo)) {
                    try (NatsConnection nc = managedConnect(options)) {
                        Collection<String> servers = nc.getServers();
                        assertTrue(servers.contains(ts1.getServerUri()));
                        assertTrue(servers.contains(ts2.getServerUri()));
                        if (ts1.getServerUri().equals(nc.getConnectedUrl())) {
                            needOne = false;
                        }
                        else {
                            needTwo = false;
                        }
                    }
                }
                assertFalse(needOne);
                assertFalse(needTwo);

                // noRandomize
                tries = 3;
                int gotOne = 0;
                int gotTwo = 0;

                // should never get a two
                options = optionsBuilder(ts1.getServerUri(), ts2.getServerUri()).noRandomize().build();
                for (int i = 0; i < tries; i++) {
                    try (NatsConnection nc = managedConnect(options)) {
                        Collection<String> servers = nc.getServers();
                        assertTrue(servers.contains(ts1.getServerUri()));
                        assertTrue(servers.contains(ts2.getServerUri()));
                        if (ts1.getServerUri().equals(nc.getConnectedUrl())) {
                            gotOne++;
                        }
                        else {
                            gotTwo++;
                        }
                    }
                }

                assertEquals(tries, gotOne, "should always ge one");
                assertEquals(0, gotTwo, "should never get two");
            }
        }
    }

    @Test
    public void testFullFakeConnect() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            assertCanConnect(mockTs);
        }
    }

    @Test
    public void testFullFakeConnectWithTabs() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            mockTs.useTabs();
            assertCanConnect(mockTs);
        }
    }

    @Test
    public void testConnectExitBeforeInfo() throws IOException {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.EXIT_BEFORE_INFO)) {
            Options options = optionsBuilder(mockTs).noReconnect().build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testConnectExitAfterInfo() throws IOException {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.EXIT_AFTER_INFO)) {
            Options options = optionsBuilder(mockTs).noReconnect().build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testConnectExitAfterConnect() throws IOException {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.EXIT_AFTER_CONNECT)) {
            Options options = optionsBuilder(mockTs).noReconnect().build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testConnectExitAfterPing() throws IOException {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.EXIT_AFTER_PING)) {
            Options options = optionsBuilder(mockTs).noReconnect().build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testConnectionFailureWithFallback() throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            try (NatsServerProtocolMock mock = new NatsServerProtocolMock(ExitAt.EXIT_AFTER_PING)) {
                Options options = optionsBuilder(mock, ts)
                    .noRandomize()
                    .build();
                assertCanConnect(options);
            }
        }
    }

    @Test
    public void testFailWithMissingLineFeedAfterInfo() throws Exception {
        String badInfo = "{\"server_id\":\"test\", \"version\":\"9.9.99\"}\rmore stuff";
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(null, badInfo)) {
            // 86_400_000L == 1 day == really long
            Options options = optionsBuilder(mockTs).reconnectWait(86_400_000L).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testFailWithStuffAfterInitialInfo() throws Exception {
        String badInfo = "{\"server_id\":\"test\", \"version\":\"9.9.99\"}\r\nmore stuff";
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(null, badInfo)) {
            // 86_400_000L == 1 day == really long
            Options options = optionsBuilder(mockTs).reconnectWait(86_400_000L).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testFailWrongInitialInfoOP() throws Exception {
        String badInfo = "PING {\"server_id\":\"test\", \"version\":\"9.9.99\"}\r\n"; // wrong op code
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(null, badInfo)) {
            mockTs.useCustomInfoAsFullInfo();
            // 86_400_000L == 1 day == really long
            Options options = optionsBuilder(mockTs).reconnectWait(86_400_000L).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testIncompleteInitialInfo() throws Exception {
        String badInfo = "{\"server_id\"\r\n";
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(null, badInfo)) {
            // 86_400_000L == 1 day == really long
            Options options = optionsBuilder(mockTs).reconnectWait(86_400_000L).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testAsyncConnection() throws Exception {
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts).connectionListener(listener).build();
            listener.queueConnectionEvent(ConnectionEvents.CONNECTED);
            CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, false);
            listener.validate();

            // both ways of getting the connection must work, and must be the same connection
            NatsConnection fromFuture = future.get(DEFAULT_WAIT, TimeUnit.MILLISECONDS);
            NatsConnection fromListener = listener.getLastConnectionEventConnection();
            assertNotNull(fromFuture);
            assertNotNull(fromListener);
            assertSame(fromFuture, fromListener);

            assertConnected(fromFuture);
            closeAndConfirm(fromFuture);
        }
    }

    @Test
    public void testAsyncConnectionWithSuppliedExecutor() throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts).build();
            AtomicBoolean executorUsed = new AtomicBoolean(false);
            Executor executor = r -> {
                executorUsed.set(true);
                new Thread(r, "test-supplied-async-connect").start();
            };

            NatsConnection nc = Nats.connectAsynchronously(options, false, executor)
                .get(DEFAULT_WAIT, TimeUnit.MILLISECONDS);
            assertTrue(executorUsed.get(), "the supplied executor ran the connect");
            assertNotNull(nc);
            assertConnected(nc);
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testAsyncConnectionSuppliedExecutorFailureStillReportsCause() throws Exception {
        Options options = optionsBuilder(NatsTestServer.nextPort()).noReconnect().build();
        Executor executor = r -> new Thread(r, "test-supplied-async-connect").start();

        CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, false, executor);
        ExecutionException ee = assertThrows(ExecutionException.class,
            () -> future.get(DEFAULT_WAIT, TimeUnit.MILLISECONDS));
        // supplyAsync wraps in CompletionException, but get() unwraps it back to the original cause
        assertInstanceOf(IOException.class, ee.getCause());
    }

    @Test
    public void testAsyncConnectionFutureWithoutListener() throws Exception {
        // no ConnectionListener at all - the future is the only handle, and that is now legal
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts).build();
            assertTrue(options.getConnectionListeners().isEmpty());

            NatsConnection nc = Nats.connectAsynchronously(options, false).get(DEFAULT_WAIT, TimeUnit.MILLISECONDS);
            assertNotNull(nc);
            assertConnected(nc);
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testAsyncConnectionWithReconnect() throws Exception {
        Listener listener = new Listener();
        int port = NatsTestServer.nextPort();
        Options options = optionsBuilder(port).maxReconnects(-1)
                .reconnectWait(100L).connectionListener(listener).build();

        CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, true);

        sleep(5000); // No server at this point, let it fail and try to start over

        // The retry loop runs on the connecting thread, so the future is still pending here - but the
        // listener already has the connection. This is the timing difference between the two paths.
        assertFalse(future.isDone(), "future is still pending while retrying");
        NatsConnection nc = listener.getLastConnectionEventConnection(); // will be disconnected, but should be there
        assertNotNull(nc);

        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        try (NatsTestServer ignored = new NatsTestServer(port)) {
            // once a server is up the retry succeeds and the future completes with the same connection
            assertSame(nc, future.get(DEFAULT_WAIT, TimeUnit.MILLISECONDS));
            confirmConnectedThenClosed(nc);
        }
    }

    @Test
    public void testErrorOnAsync() throws Exception {
        Listener listener = new Listener();
        Options options = optionsBuilder(NatsTestServer.nextPort())
            .connectionListener(listener)
            .errorListener(listener)
            .noReconnect()
            .build();
        listener.queueConnectionEvent(ConnectionEvents.CLOSED);
        CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, false);
        listener.validate();
        assertTrue(listener.getExceptionCount() > 0);

        // the failure surfaces through the future as well as the error listener
        ExecutionException ee = assertThrows(ExecutionException.class,
            () -> future.get(DEFAULT_WAIT, TimeUnit.MILLISECONDS));
        assertInstanceOf(IOException.class, ee.getCause());
    }

    @Test
    public void testConnectionTimeout() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.SLEEP_BEFORE_INFO)) { // will sleep for 3
            Options options = optionsBuilder(mockTs)
                .noReconnect()
                .connectionTimeout(2000) // 2 is also the default but explicit for test
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testSlowConnectionNoTimeout() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.SLEEP_BEFORE_INFO)) {
            Options options = optionsBuilder(mockTs)
                .noReconnect()
                .connectionTimeout(6000) // longer than the sleep
                .build();
            assertCanConnect(options);
        }
    }

    @Test
    public void testConnectExceptionHasURLS() {
        try {
            //noinspection resource
            Nats.connect(options("nats://testserver.notnats:4222, nats://testserver.alsonotnats:4223"));
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("testserver.notnats:4222"));
            assertTrue(e.getMessage().contains("testserver.alsonotnats:4223"));
        }
    }

    @Test
    public void testFlushBuffer() throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            NatsConnection nc = managedConnect(options(ts));

            // test connected
            nc.flushBuffer();

            ts.shutdown();
            while (nc.getStatus() == ConnectionStatus.CONNECTED) {
                sleep(10);
            }

            // test while reconnecting
            assertThrows(IllegalStateException.class, nc::flushBuffer);
            closeAndConfirm(nc);

            // test when closed.
            assertThrows(IllegalStateException.class, nc::flushBuffer);
        }
    }

    @Test
    public void testFlushBufferThreadSafety() throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            NatsConnection nc = managedConnect(options(ts));

            // use two latches to sync the threads as close as
            // possible.
            CountDownLatch pubLatch = new CountDownLatch(1);
            CountDownLatch flushLatch = new CountDownLatch(1);
            CountDownLatch completedLatch = new CountDownLatch(1);

            Thread t = new Thread("publisher") {
                @SuppressWarnings("ResultOfMethodCallIgnored")
                public void run() {
                    byte[] payload = new byte[5];
                    pubLatch.countDown();
                    try {
                        flushLatch.await(2, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // NOOP
                    }
                    String subject = random();
                    for (int i = 1; i <= 50000; i++) {
                        nc.publish(subject, payload);
                        if (i % 2000 == 0) {
                            try {
                                nc.flushBuffer();
                            }
                            catch (IllegalStateException e) {
                                // connection momentarily not active (e.g. transient reconnect); keep publishing
                            }
                            catch (IOException e) {
                                break;
                            }
                        }
                    }
                    completedLatch.countDown();
                }
            };

            t.start();

            // sync up the current thread and the publish thread
            // to get the most out of the test.
            try {
                //noinspection ResultOfMethodCallIgnored
                pubLatch.await(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                // NOOP
            }
            flushLatch.countDown();

            // flush as fast as we can while the publisher
            // is publishing.

            // flushBuffer throws IllegalStateException when the connection is momentarily
            // not active (e.g. a transient reconnect under load); that is a valid state and
            // not what this concurrency test targets, so tolerate it while the publisher runs.
            while (t.isAlive()) {
                try {
                    nc.flushBuffer();
                }
                catch (IllegalStateException e) {
                    // keep flushing while the publisher is alive
                }
            }

            // cleanup and double-check the thread is done.
            t.join(2000);

            // make sure the publisher actually completed.
            assertTrue(completedLatch.await(10, TimeUnit.SECONDS));

            closeAndConfirm(nc);
        }
    }

    @SuppressWarnings({"unused", "UnusedAssignment"})
    @Test
    public void testSocketLevelException() throws Exception {
        int port = NatsTestServer.nextPort();

        AtomicBoolean simExReceived = new AtomicBoolean();
        Listener listener = new Listener();
        ErrorListener el = new ErrorListener() {
            @Override
            public void exceptionOccurred(NatsConnection conn, Exception exp) {
                if (exp.getMessage().contains("Simulated Exception")) {
                    simExReceived.set(true);
                }
            }
        };

        Options options = optionsBuilder(port)
            .dataPortType("io.synadia.client.impl.SimulateSocketDataPortException")
            .connectionListener(listener)
            .errorListener(el)
            .reconnectDelayHandler((l, o, s, d) -> 1000L)
            .build();

        NatsConnection connection = null;

        // 1. DO NOT RECONNECT ON CONNECT
        try (NatsTestServer ts = new NatsTestServer(port)) {
            try {
                SimulateSocketDataPortException.THROW_ON_CONNECT.set(true);
                connection = Nats.connect(options);
                fail();
            }
            catch (Exception ignore) {}
        }

        Thread.sleep(200); // just making sure messages get through
        assertNull(connection);
        assertTrue(simExReceived.get());
        simExReceived.set(false);

        // 2. RECONNECT ON CONNECT
        try (NatsTestServer ts = new NatsTestServer(port)) {
            try {
                SimulateSocketDataPortException.THROW_ON_CONNECT.set(true);
                listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
                connection = Nats.connectReconnectOnConnect(options);
                listener.validate();
                listener.queueConnectionEvent(ConnectionEvents.DISCONNECTED);
            }
            catch (Exception e) {
                fail("should have connected " + e);
            }
        }
        listener.validate();
        assertTrue(simExReceived.get());
        simExReceived.set(false);

        // 2. NORMAL RECONNECT
        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        try (NatsTestServer ts = new NatsTestServer(port)) {
            SimulateSocketDataPortException.THROW_ON_CONNECT.set(true);
            listener.validate();
        }
    }

    @Test
    public void testRunInJsCluster() throws Exception {
        Listener[] listeners = new Listener[3];
        listeners[0] = new Listener();
        listeners[1] = new Listener();
        listeners[2] = new Listener();

        ThreeServerTestOptions tstOpts = new ThreeServerTestOptions() {
            @Override
            public void append(int index, OptionsBuilder builder) {
                builder.connectionListener(listeners[index]).errorListener(listeners[index]);
            }

            @Override
            public boolean configureAccount() {
                return true;
            }

            @Override
            public boolean includeAllServers() {
                return true;
            }

            @Override
            public boolean jetStream() {
                return true;
            }
        };

        listeners[0] = new Listener();
        listeners[1] = new Listener();
        listeners[2] = new Listener();

        runInCluster(tstOpts, (nc1, nc2, nc3) -> {
            Thread.sleep(200);
            ServerInfo si1 = nc1.getServerInfo();
            ServerInfo si2 = nc2.getServerInfo();
            ServerInfo si3 = nc3.getServerInfo();
            assertTrue(si1.isJetStreamAvailable());
            assertTrue(si2.isJetStreamAvailable());
            assertTrue(si3.isJetStreamAvailable());
            assertEquals(si1.getCluster(), si2.getCluster());
            assertEquals(si1.getCluster(), si3.getCluster());
            String port1 = "" + si1.getPort();
            String port2 = "" + si2.getPort();
            String port3 = "" + si3.getPort();
            String urls1 = String.join(",", si1.getConnectURLs());
            String urls2 = String.join(",", si2.getConnectURLs());
            String urls3 = String.join(",", si3.getConnectURLs());
            assertTrue(urls1.contains(port1));
            assertTrue(urls1.contains(port2));
            assertTrue(urls1.contains(port3));
            assertTrue(urls2.contains(port1));
            assertTrue(urls2.contains(port2));
            assertTrue(urls2.contains(port3));
            assertTrue(urls3.contains(port1));
            assertTrue(urls3.contains(port2));
            assertTrue(urls3.contains(port3));
        });
    }

    // https://github.com/nats-io/nats.java/issues/1201
    @Test
    void testLowConnectionTimeoutResultsInIOException() {
        Options options = Options.builder()
                .connectionTimeout(0)
                .build();
        //noinspection resource
        assertThrows(IOException.class, () -> Nats.connect(options));
    }

    @Test
    void testConnectWithHappyEyeballsShortCircuitCoverage() throws Exception {
        Options options = Options.builder()
            .server("tls://demo.nats.io")
            .sslContext(SSLUtils.createTrustAllTlsContext())
            .hostnameResolveMode(HostnameResolveMode.HappyEyeballs)
            .build();
        try (NatsConnection nc = Nats.connect(options)) {
            assertConnected(nc);
        }
    }

}
