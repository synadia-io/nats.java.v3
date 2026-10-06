package io.synadia.client.impl;

import io.nats.NatsServerRunner;
import io.synadia.client.*;
import io.synadia.client.api.ServerInfo;
import io.synadia.client.utils.ConnectionUtils;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.ssl.SslTestingHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static io.synadia.client.AuthTests.getUserCredsAuthHander;
import static io.synadia.client.NatsTestServer.configFileBuilder;
import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.Listener.LONG_VALIDATE_TIMEOUT;
import static io.synadia.client.utils.Listener.VERY_LONG_VALIDATE_TIMEOUT;
import static io.synadia.client.utils.NatsConstants.OUTPUT_QUEUE_IS_FULL;
import static io.synadia.client.utils.OptionsUtils.*;
import static io.synadia.client.utils.TestBase.*;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class ReconnectTests {

    void checkNotConnected(NatsConnection nc) {
        ConnectionStatus status = nc.getStatus();
        assertTrue(ConnectionStatus.RECONNECTING == status || ConnectionStatus.DISCONNECTED == status, "Reconnecting status");
    }

    @Test
    public void testSimpleReconnect() throws Exception { //Includes test for subscriptions and dispatchers across reconnect
        _testReconnect(NatsServerRunner.builder(), (ts, optionsBuilder) -> optionsBuilder.server(ts.getServerUri()));
    }

    @Test
    public void testWsReconnect() throws Exception { //Includes test for subscriptions and dispatchers across reconnect
        _testReconnect(configFileBuilder("ws_operator.conf"),
            (ts, optionsBuilder) -> {
                String uri = NatsTestServer.getLocalhostUri(WS, ts.getNonNatsPort());
                optionsBuilder.server(uri).authHandler(getUserCredsAuthHander());
            });
    }

    private void _testReconnect(NatsServerRunner.Builder nsrb, BiConsumer<NatsTestServer, OptionsBuilder> optSetter) throws Exception {
        int port = NatsTestServer.nextPort();
        nsrb.port(port); // set the port into the builder
        Listener listener = new Listener();
        NatsConnection nc;
        NatsSubscription sub;
        long start;
        long end;
        String subsubject = random();
        String dispatchSubject = random();
        try (NatsTestServer ts = new NatsTestServer(nsrb)) {
            OptionsBuilder builder = optionsBuilder() // server intentionally not set
                .maxReconnects(-1)
                .reconnectWait(1000L)
                .connectionListener(listener);
            optSetter.accept(ts, builder);
            Options options = builder.build();

            nc = managedConnect(options);

            sub = nc.subscribe(subsubject);

            final NatsConnection nnc = nc; // final for the lambda
            Dispatcher d = nc.createDispatcher(msg -> nnc.publish(msg.getReplyTo(), msg.getData()) );
            d.subscribe(dispatchSubject);
            flushConnection(nc);

            Future<Message> inc = nc.requestAsync(dispatchSubject, "test".getBytes(StandardCharsets.UTF_8));
            Message msg = inc.get();
            assertNotNull(msg);

            nc.publish(subsubject, null);
            msg = sub.nextMessage(100L);
            assertNotNull(msg);

            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
            start = System.nanoTime();
        }

        flushConnection(nc);
        listener.validate();

        listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);

        try (NatsTestServer ignored = new NatsTestServer(nsrb)) {
            confirmConnected(nc); // wait for reconnect
            listener.validate();

            end = System.nanoTime();

            assertTrue(1_000_000 * (end-start) > 1000, "reconnect wait");

            // Make sure dispatcher and subscription are still there
            Future<Message> inc = nc.requestAsync(dispatchSubject, "test".getBytes(StandardCharsets.UTF_8));
            Message msg = inc.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);

            // make sure the subscription survived
            nc.publish(subsubject, null);
            msg = sub.nextMessage(100L);
            assertNotNull(msg);
        }

        assertEquals(1, nc.getStatistics().getReconnects(), "reconnect count");
        assertTrue(nc.getStatistics().getExceptions() > 0, "exception count");
        closeAndConfirm(nc);
    }

    @Test
    public void testSubscribeDuringReconnect() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port;
        NatsSubscription sub;

        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .maxReconnects(-1)
                .reconnectWait(20L)
                .connectionListener(listener)
                .build();
            port = ts.getNatsPort();
            nc = managedConnect(options);
            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        }

        flushConnection(nc);
        listener.validate();

        String subsubject = random();
        String dispatchSubject = random();
        sub = nc.subscribe(subsubject);

        final NatsConnection nnc = nc;
        Dispatcher d = nc.createDispatcher(msg -> nnc.publish(msg.getReplyTo(), msg.getData()));
        d.subscribe(dispatchSubject);

        listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);

        try (NatsTestServer ignored = new NatsTestServer(port)) {
            confirmConnected(nc); // wait for reconnect
            listener.validate();

            // Make sure the dispatcher and subscription are still there
            Future<Message> inc = nc.requestAsync(dispatchSubject, "test".getBytes(StandardCharsets.UTF_8));
            Message msg = inc.get();
            assertNotNull(msg);

            // make sure the subscription survived
            nc.publish(subsubject, null);
            msg = sub.nextMessage(100L);
            assertNotNull(msg);
        }

        assertEquals(1, nc.getStatistics().getReconnects(), "reconnect count");
        assertTrue(nc.getStatistics().getExceptions() > 0, "exception count");
        closeAndConfirm(nc);
    }

    @Test
    public void testReconnectBuffer() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port = NatsTestServer.nextPort();
        NatsSubscription sub;
        long start;
        long end;
        String[] customArgs = {"--user","stephen","--pass","password"};
        String subsubject = random();
        String dispatchSubject = random();

        try (NatsTestServer ts = new NatsTestServer(customArgs, port)) {
            Options options = optionsBuilder(ts)
                .maxReconnects(-1)
                .userInfo("stephen".toCharArray(), "password".toCharArray())
                .reconnectWait(1000L)
                .connectionListener(listener)
                .build();
            nc = managedConnect(options);

            sub = nc.subscribe(subsubject);

            final NatsConnection nnc = nc;
            Dispatcher d = nc.createDispatcher(msg -> nnc.publish(msg.getReplyTo(), msg.getData()));
            d.subscribe(dispatchSubject);
            nc.flush(1000);

            Future<Message> inc = nc.requestAsync(dispatchSubject, "test".getBytes(StandardCharsets.UTF_8));
            Message msg = inc.get();
            assertNotNull(msg);

            nc.publish(subsubject, null);
            msg = sub.nextMessage(100L);
            assertNotNull(msg);

            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
            start = System.nanoTime();
        }

        flushConnection(nc);
        listener.validate();

        // Send a message to the dispatcher and one to the subscriber
        // These should be sent on reconnect
        Future<Message> inc = nc.requestAsync(dispatchSubject, "test".getBytes(StandardCharsets.UTF_8));
        nc.publish(subsubject, null);
        nc.publish(subsubject, null);

        listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);

        try (NatsTestServer ignored = new NatsTestServer(customArgs, port)) {
            confirmConnected(nc); // wait for reconnect
            listener.validate();

            end = System.nanoTime();

            assertTrue(1_000_000 * (end-start) > 1000, "reconnect wait");

            // Check the message we sent to the dispatcher
            Message msg = inc.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);

            // Check the two we sent to subscriber
            msg = sub.nextMessage(500L);
            assertNotNull(msg);

            msg = sub.nextMessage(500L);
            assertNotNull(msg);
        }

        assertEquals(1, nc.getStatistics().getReconnects(), "reconnect count");
        assertTrue(nc.getStatistics().getExceptions() > 0, "exception count");
        closeAndConfirm(nc);
    }

    @Test
    public void testMaxReconnects() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port = NatsTestServer.nextPort();

        try (NatsTestServer ts = new NatsTestServer(port)) {
            Options options = optionsBuilder(ts)
                .maxReconnects(1)
                .connectionListener(listener)
                .reconnectWait(10L)
                .build();
            nc = managedConnect(options);
            listener.queueConnectionEvent(ConnectionEvent.CLOSED);
        }
        flushConnection(nc);
        listener.validate();
    }

    @Test
    public void testReconnectToSecondServerInBootstrap() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        try (NatsTestServer ts1 = new NatsTestServer()) {
            try (NatsTestServer ts2 = new NatsTestServer()) {
                // need both in bootstrap b/c these are not clustered
                Options options = optionsBuilder(ts2.getServerUri(), ts1.getServerUri())
                    .noRandomize()
                    .connectionListener(listener)
                    .maxReconnects(-1)
                    .build();
                nc = managedConnect(options);
                assertEquals(ts2.getServerUri(), nc.getConnectedUrl());
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
            }

            flushConnection(nc);
            listener.validate();
            assertConnected(nc);
            assertEquals(ts1.getServerUri(), nc.getConnectedUrl());
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testNoRandomizeReconnectToSecondServer() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            try (NatsTestServer ts2 = new NatsTestServer()) {
                Options options = optionsBuilder(ts2.getServerUri(), ts.getServerUri())
                    .noRandomize()
                    .connectionListener(listener)
                    .maxReconnects(-1)
                    .build();
                nc = managedConnect(options);
                assertEquals(ts2.getServerUri(), nc.getConnectedUrl());
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
            }

            flushConnection(nc);
            listener.validate();
            assertConnected(nc);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testReconnectToSecondServerFromInfo() throws Exception {
        Listener listener = new Listener();
        runInSharedServer(ts -> {
            NatsConnection nc;
            String striped = ts.getServerUri().substring("nats://".length()); // info doesn't have protocol
            String customInfo = "{\"server_id\":\"myid\", \"version\":\"9.9.99\",\"connect_urls\": [\""+striped+"\"]}";
            try (NatsServerProtocolMock mockTs2 = new NatsServerProtocolMock(null, customInfo)) {
                Options options = optionsBuilder(mockTs2)
                    .connectionListener(listener)
                    .maxReconnects(-1)
                    .connectionTimeout(5000)
                    .reconnectWait(1000L)
                    .build();
                nc = standardConnect(options);
                assertEquals(mockTs2.getServerUri(), nc.getConnectedUrl());
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
            }

            flushConnection(nc);
            listener.validate();
            assertConnected(nc);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            closeAndConfirm(nc);
        });
    }

    @Test
    public void testOverflowReconnectBuffer() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .connectionListener(listener)
                .reconnectBufferSize(4*512)
                .reconnectWait(480_000L)
                .build();
            nc = managedConnect(options);
        }

        listener.validate();

        String subject = random();
        assertThrows(IllegalStateException.class, () -> {
            for (int i = 0; i < 20; i++) {
                nc.publish(subject, new byte[512]);// Should be full by the 5th message
            }
        });

        closeAndConfirm(nc);
    }

    @Test
    public void testInfiniteReconnectBuffer() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .maxReconnects(5)
                .connectionListener(listener)
                .reconnectBufferSize(-1)
                .reconnectWait(30000L)
                .build();
            nc = managedConnect(options);
            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        }

        flushConnection(nc);
        listener.validate();

        byte[] payload = new byte[1024];
        for (int i=0;i<1000;i++) {
            nc.publish("test", payload);
        }

        checkNotConnected(nc);
        closeAndConfirm(nc);
    }

    @Test
    public void testReconnectDropOnLineFeed() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port = NatsTestServer.nextPort();
        long reconnectWait = 100L; // thrash, ms
        int thrashCount = 5;
        CompletableFuture<Boolean> gotSub = new CompletableFuture<>();
        AtomicReference<CompletableFuture<Boolean>> subRef = new AtomicReference<>(gotSub);
        CompletableFuture<Boolean> sendMsg = new CompletableFuture<>();
        AtomicReference<CompletableFuture<Boolean>> sendRef = new AtomicReference<>(sendMsg);

        NatsServerProtocolMock.Customizer receiveMessageCustomizer = (ts, r,w) -> {
            String subLine;

            // System.out.println("*** Mock Server @" + ts.getPort() + " waiting for SUB ...");
            try {
                subLine = r.readLine();
            } catch(Exception e) {
                subRef.get().cancel(true);
                return;
            }

            if (subLine.startsWith("SUB")) {
                subRef.get().complete(Boolean.TRUE);
            }

            try {
                sendRef.get().get();
            } catch (Exception e) {
                //keep going
            }

            w.write("MSG\r"); // Drop the line feed
            w.flush();
        };

        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(receiveMessageCustomizer, port, true)) {
            Options options = optionsBuilder(mockTs)
                .maxReconnects(-1)
                .reconnectWait(reconnectWait)
                .connectionListener(listener)
                .build();
            port = mockTs.getNatsPort();
            nc = standardConnect(options);
            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
            nc.subscribe("test");
            subRef.get().get();
            sendRef.get().complete(true);
            flushConnection(nc); // mock server will close so we do this inside the curly
            listener.validate();
        }

        // Thrash in and out of connect status
        // server starts thrashCount times, so we should succeed thrashCount x
        for (int i=0;i<thrashCount;i++) {
            checkNotConnected(nc);

            // connect good then bad
            listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);
            try (NatsTestServer ignored = new NatsTestServer(port)) {
                confirmConnected(nc); // wait for reconnect
                listener.validate();
                listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED); // do it here because we are about to disconnect
            }

            flushConnection(nc); // client won't close until we tell it, so put this outside the curly
            listener.validate();

            gotSub = new CompletableFuture<>();
            subRef.set(gotSub);
            sendMsg = new CompletableFuture<>();
            sendRef.set(sendMsg);

            listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);
            try (NatsServerProtocolMock ignored = new NatsServerProtocolMock(receiveMessageCustomizer, port, true)) {
                confirmConnected(nc); // wait for reconnect
                listener.validate();
                subRef.get().get();
                listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
                sendRef.get().complete(true);
                flushConnection(nc); // mock server will close so we do this inside the curly
                listener.validate();
            }
        }

        assertEquals(2 * thrashCount, nc.getStatistics().getReconnects(), "reconnect count");
        closeAndConfirm(nc);
    }

    @Test
    public void testTlsNoIpConnection() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();

        int tsPort = NatsTestServer.nextPort();
        int ts2Port = NatsTestServer.nextPort();
        int tsCPort = NatsTestServer.nextPort();
        int ts2CPort = NatsTestServer.nextPort();

        String[] tsInserts = {
                "cluster {",
                "name: testClusterName",
                "listen: localhost:" + tsCPort,
                "routes = [",
                "nats-route://localhost:" + ts2CPort,
                "]",
                "}"
        };
        String[] ts2Inserts = {
                "cluster {",
                "name: testClusterName",
                "listen: localhost:" + ts2CPort,
                "routes = [",
                "nats-route://127.0.0.1:" + tsCPort,
                "]",
                "}"
        };

        SslTestingHelper.setKeystoreSystemParameters();

        // Regular tls for first connection, then no ip for second
        try (NatsTestServer ts = new NatsTestServer( "tls_noip.conf", tsInserts, tsPort);
             NatsTestServer ts2 = new NatsTestServer( "tls_noip.conf", ts2Inserts, ts2Port) ) {

            // Test 1. tls Scheme
            Options options = optionsBuilder(ts, "tls")
                .connectionTimeout(5000)
                .maxReconnects(0)
                .build();
            assertCanConnect(options);

            // Test 2. opentls Scheme
            options = optionsBuilder(ts, "opentls")
                .maxReconnects(0)
                .build();
            assertCanConnect(options);

            // Test 3. Reconnect
            options = optionsBuilder(ts)
                .secure()
                .connectionListener(listener)
                .maxReconnects(20)
                .reconnectWait(100L)
                .connectionTimeout(5000)
                .noRandomize()
                .build();

            listener.queueConnectionEvent(ConnectionEvent.DISCOVERED_SERVERS);
            nc = ConnectionUtils.managedConnect(options);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());

            flushConnection(nc); // make sure we get the new server via info
            listener.validate();

            listener.queueConnectionEvent(ConnectionEvent.RECONNECTED, VERY_LONG_VALIDATE_TIMEOUT);

            ts.close();

            flushConnection(nc);

            listener.validate();

            URI uri = options.createURIForServer(nc.getConnectedUrl());
            assertEquals(ts2.getNatsPort(), uri.getPort()); // full uri will have some ip address, just check port
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testWriterFilterTiming() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port = NatsTestServer.nextPort();

        try (NatsTestServer ts = new NatsTestServer(port)) {
            Options options = optionsBuilder(ts)
                .noReconnect()
                .connectionListener(listener)
                .build();

            nc = Nats.connect(options);
            assertConnected(nc);

            for (int i = 0; i < 100; i++) {
                // stop and start in a loop without waiting for the future to complete
                nc.getWriter().stop();
                nc.getWriter().start(nc.getDataPortFuture());
            }

            nc.getWriter().stop();
            sleep(1000);
            // Should have thrown an exception if #203 isn't fixed
            closeAndConfirm(nc);
        }
    }

    @Test
    public void testReconnectWait() throws Exception {
        Listener listener = new Listener();

        int port = NatsTestServer.nextPort();

        try (NatsTestServer ts = new NatsTestServer(port)) {
            Options options = optionsBuilder(ts)
                .maxReconnects(-1)
                .connectionTimeout(1000)
                .reconnectWait(250L)
                .connectionListener(listener)
                .build();

            //noinspection unused
            try (NatsConnection nc = Nats.connect(options)) {
                ts.close();
                sleep(250);
                assertTrue(listener.getConnectionEventCount(ConnectionEvent.DISCONNECTED) < 3, "disconnectCount");
            }
        }
    }

    @Test
    public void testReconnectOnConnect() throws Exception {
        int port = NatsTestServer.nextPort();
        Options options = options(port);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NatsConnection> testConn = new AtomicReference<>();

        Thread t = getReconnectOnConnectTestThread(testConn, port, latch);

        try {
            testConn.set(Nats.connectReconnectOnConnect(options));
            latch.countDown();
        }
        catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        t.join(5000);
    }

    private static Thread getReconnectOnConnectTestThread(AtomicReference<NatsConnection> testConn, int port, CountDownLatch latch) {
        Thread t = new Thread(() -> {
            assertNull(testConn.get());
            try {
                Thread.sleep(2000); // give testConn time to be alive and trying.
            }
            catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            assertNull(testConn.get());

            // start a server that test conn can connect to
            try (NatsTestServer ignored = new NatsTestServer(port)) {
                //noinspection ResultOfMethodCallIgnored
                latch.await(2000, TimeUnit.MILLISECONDS);
                assertConnected(testConn.get());
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        return t;
    }

    @Test
    public void testForceReconnectOptionsBuilder() {
        ForceReconnectOptions fro = ForceReconnectOptions.builder().build();
        assertFalse(fro.isForceClose());
        assertFalse(fro.isFlush());
        assertEquals(0, fro.getFlushWait());

        fro = ForceReconnectOptions.builder().forceClose().build();
        assertTrue(fro.isForceClose());
        assertFalse(fro.isFlush());
        assertEquals(0, fro.getFlushWait());

        fro = ForceReconnectOptions.builder().flush(42).build();
        assertFalse(fro.isForceClose());
        assertTrue(fro.isFlush());
        assertEquals(42, fro.getFlushWait());

        fro = ForceReconnectOptions.builder().flush(0).build();
        assertFalse(fro.isForceClose());
        assertFalse(fro.isFlush());
        assertEquals(0, fro.getFlushWait());

        fro = ForceReconnectOptions.builder().flush(-1).build();
        assertFalse(fro.isForceClose());
        assertFalse(fro.isFlush());
        assertEquals(0, fro.getFlushWait());
    }

    @Test
    public void testForceReconnect() throws Exception {
        Listener listener = new Listener();
        ThreeServerTestOptions tstOpts = makeThreeServerTestOptions(listener, false);
        runInCluster(tstOpts, (nc0, nc1, nc2) -> _testForceReconnect(nc0, listener));
    }

    @Test
    public void testForceReconnectWithAccount() throws Exception {
        Listener listener = new Listener();
        ThreeServerTestOptions tstOpts = makeThreeServerTestOptions(listener, true);
        runInCluster(tstOpts, (nc0, nc1, nc2) -> _testForceReconnect(nc0, listener));
    }

    // forceReconnectImpl stops the reader with stop(false) - which clears `running` but does NOT
    // shutdownInput - so the reader stays blocked in read() until the port close (an async task) wakes it.
    // With a close delayed past the join window, a stale reader can outlive forceReconnect, then wake on
    // the now-healthy connection, see `running` flipped back true by the new reader's start(), and call
    // handleCommunicationIssue -> spurious disconnect. Its finally{running.set(false)} also stomps the
    // flag the new reader loops on. Joining the stopped-future with the full connection timeout means the
    // old thread is dead before its reader instance is reused, so nothing fires after the reconnect.
    @Test
    public void testForceReconnectWaitsForStaleReaderToStop() throws Exception {
        long closeDelay = 1000; // past the old 100ms join, well inside the test connection timeout
        ForceReconnectQueueCheckDataPort.resetAll();
        ForceReconnectQueueCheckDataPort.CLOSE_DELAY = closeDelay;
        try (NatsTestServer ts = new NatsTestServer()) {
            List<ConnectionEvent> events = Collections.synchronizedList(new ArrayList<>());
            Options options = optionsBuilder(ts)
                .dataPortType(ForceReconnectQueueCheckDataPort.class.getCanonicalName())
                .maxReconnects(-1)
                .reconnectWait(100)
                .connectionListener((conn, event, time, details) -> events.add(event))
                .build();

            try (NatsConnection nc = standardConnect(options)) {
                assertConnected(nc);

                nc.forceReconnect();
                confirmConnected(nc); // the deliberate DISCONNECTED/RECONNECTED pair lands here

                // Everything from here on must be quiet. Measured against the unfixed code, a stale
                // reader wakes when the delayed close lands (~closeDelay) and calls
                // handleCommunicationIssue -> processException, bumping the exception count; the
                // resulting spurious DISCONNECTED/RECONNECTED pair follows roughly 2s after that.
                // Wait past both. The exception count is the earlier and more direct signal - it moves
                // the moment the stale reader misbehaves - so it is asserted first.
                int settledEvents = events.size();
                long settledExceptions = nc.getStatistics().getExceptions();
                sleep(closeDelay + 3500);

                assertEquals(settledExceptions, nc.getStatistics().getExceptions(),
                    "a stale reader fired handleCommunicationIssue after the reconnect settled");
                assertEquals(settledEvents, events.size(),
                    "no connection events after the reconnect settled, saw " + events);
                assertConnected(nc);
            }
        }
        finally {
            ForceReconnectQueueCheckDataPort.resetAll();
        }
    }

    private static void _testForceReconnect(NatsConnection nc0, Listener listener) throws IOException, InterruptedException {
        ServerInfo si = nc0.getServerInfo();
        String connectedServer = si.getServerId();

        listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
        nc0.forceReconnect();
        confirmConnected(nc0); // wait for reconnect

        si = nc0.getServerInfo();
        assertNotEquals(connectedServer, si.getServerId());
        listener.validateAll();
    }

    private static ThreeServerTestOptions makeThreeServerTestOptions(Listener listener, final boolean configureAccount) {
        return new ThreeServerTestOptions() {
            @Override
            public void append(int index, OptionsBuilder builder) {
                if (index == 0) {
                    builder
                        .connectionListener(listener)
                        .errorListener(NOOP_EL)
                        .ignoreDiscoveredServers()
                        .noRandomize();
                }
            }

            @Override
            public boolean configureAccount() {
                return configureAccount;
            }

            @Override
            public boolean includeAllServers() {
                return true;
            }
        };
    }

    @Test
    public void testForceReconnectQueueBehaviorCheck() throws Exception {
        runInCluster((nc0, nc1, nc2) -> {
            int pubCount = 100_000;
            int subscribeTime = 5000;
            int flushWait = 2500;
            int port = nc0.getServerInfo().getPort();

            ForceReconnectQueueCheckDataPort.DELAY = 75;

            String subject = random();
            ForceReconnectQueueCheckDataPort.setCheck("PUB " + subject);
            _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, false, 0);

            subject = random();
            ForceReconnectQueueCheckDataPort.setCheck("PUB " + subject);
            _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, false, flushWait);

            subject = random();
            ForceReconnectQueueCheckDataPort.setCheck("PUB " + subject);
            _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, true, 0);

            subject = random();
            ForceReconnectQueueCheckDataPort.setCheck("PUB " + subject);
            _testForceReconnectQueueCheck(subject, pubCount, subscribeTime, port, true, flushWait);
        });
    }

    private static void _testForceReconnectQueueCheck(String subject, int pubCount, int subscribeTime, int port, boolean forceClose, int flushWait) throws InterruptedException {
        ReconnectQueueCheckSubscriber subscriber = new ReconnectQueueCheckSubscriber(subject, pubCount, port);
        Thread tsub = new Thread(subscriber);
        tsub.start();

        ForceReconnectOptions.Builder froBuilder = ForceReconnectOptions.builder();
        if (flushWait > 0) {
            froBuilder.flush(flushWait);
        }
        if (forceClose) {
            froBuilder.forceClose();
        }

        Listener listener = new Listener();

        Options options = optionsBuilder(port)
            .connectionListener(listener)
            .dataPortType(ForceReconnectQueueCheckDataPort.class.getCanonicalName())
            .build();

        try (NatsConnection nc = Nats.connect(options)) {
            for (int x = 1; x <= pubCount; x++) {
                nc.publish(subject, (x + "").getBytes());
            }

            listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
            nc.forceReconnect(froBuilder.build());

            listener.validate();

            long maxTime = subscribeTime;
            while (!subscriber.subscriberDone.get() && maxTime > 0) {
                //noinspection BusyWait
                Thread.sleep(50);
                maxTime -= 50;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        subscriber.subscriberDone.set(true);
        tsub.join();

        if (flushWait > 0) {
            assertEquals(pubCount, subscriber.lastNotSkipped);
        }
    }

    static class ReconnectQueueCheckSubscriber implements Runnable {
        final AtomicBoolean subscriberDone;
        final String subject;
        final int pubCount;
        final int port;
        boolean completed;
        int lastNotSkipped;
        int firstAfterSkip;

        public ReconnectQueueCheckSubscriber(String subject, int pubCount, int port) {
            this.subscriberDone = new AtomicBoolean(false);
            this.subject = subject;
            this.pubCount = pubCount;
            this.port = port;
            lastNotSkipped = 0;
            firstAfterSkip = -1;
            completed = false;
        }

        @Override
        public void run() {
            Options options = options(port);
            try (NatsConnection nc = Nats.connect(options)) {
                NatsSubscription sub = nc.subscribe(subject);
                while (!subscriberDone.get()) {
                    Message m = sub.nextMessage(100L);
                    if (m != null) {
                        String next = "" + (lastNotSkipped + 1);
                        String md = new String(m.getData());
                        if (md.equals(next)) {
                            if (++lastNotSkipped >= pubCount) {
                                completed = true;
                                subscriberDone.set(true);
                            }
                        }
                        else {
                            firstAfterSkip = Integer.parseInt(md);
                            subscriberDone.set(true);
                        }
                    }
                }
            }
            catch (Exception e) {
                fail(e);
            }
        }
    }

    @Test
    public void testSocketDataPortTimeout() throws Exception {
        Listener listener = new Listener();
        OptionsBuilder builder = Options.builder()
            .noRandomize()
            .socketWriteTimeout(5000) // millis; long enough that we reach OUTPUT_QUEUE_IS_FULL first
            .writeQueuePushTimeout(5000)
            .pingInterval(100000) // avoid pings messing the test
            .maxMessagesInOutgoingQueue(100)
            .dataPortType(SocketDataPortBlockSimulator.class.getCanonicalName())
            .connectionListener(listener)
            .errorListener(listener);

        AtomicBoolean gotOutputQueueIsFull = new AtomicBoolean();
        try (NatsTestServer ts1 = new NatsTestServer()) {
            try (NatsTestServer ts2 = new NatsTestServer()) {
                String[] servers = new String[]{
                    ts1.getNatsLocalhostUri(),
                    ts2.getNatsLocalhostUri()
                };
                try (NatsConnection nc = standardConnect(builder.servers(servers).build())) {
                    listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED, LONG_VALIDATE_TIMEOUT);
                    listener.queueSocketWriteTimeout(LONG_VALIDATE_TIMEOUT);
                    listener.queueConnectionEvent(ConnectionEvent.RECONNECTED, LONG_VALIDATE_TIMEOUT);

                    String subject = random();
                    int pubId = 0;
                    while (pubId++ < 50000) {
                        try {
                            nc.publish(subject, null);
                            if (pubId == 10) {
                                SocketDataPortBlockSimulator.simulateBlock();
                            }
                        }
                        catch (Exception e) {
                            if (e.getMessage().contains(OUTPUT_QUEUE_IS_FULL)) {
                                gotOutputQueueIsFull.set(true);
                                break;
                            }
                        }
                    }
                    listener.validate(); // disconnected
                    assertTrue(gotOutputQueueIsFull.get());

                    // The write watch raises socketWriteTimeout on its own schedule, it is not
                    // sequenced against the disconnect, so wait for it instead of assuming the
                    // disconnect implies it already landed.
                    listener.validate();
                    assertTrue(listener.getSocketWriteTimeoutCount() > 0);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // ReconnectDelayBehavior gating. The behavior decides WHETHER the handler is invoked before
    // round 1; rounds after the first always invoke it. The gate applies to a custom handler too,
    // which is the part that is invisible when only the default handler is exercised.
    //
    // Every test here keeps a second server listening for the whole test and kills the one the
    // client is on, so the reconnect lands somewhere that is already up. Nothing waits for a port
    // to become reusable, and maxReconnects is bounded, so a campaign that cannot succeed ends
    // instead of looping forever.
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testDelayBehaviorSkipsFirstRoundWhenBeforeSubsequentRounds() throws Exception {
        List<Long> rounds = roundsSeenAcrossReconnect(ReconnectDelayBehavior.BeforeSubsequentRounds, null);
        assertFalse(rounds.contains(1L), "round 1 must not invoke the handler: " + rounds);
    }

    @Test
    public void testDelayBehaviorDelaysFirstRoundWhenBeforeAllRounds() throws Exception {
        List<Long> rounds = roundsSeenAcrossReconnect(ReconnectDelayBehavior.BeforeAllRounds, null);
        assertTrue(rounds.contains(1L), "round 1 must invoke the handler: " + rounds);
    }

    @Test
    public void testDelayBehaviorSkipsFirstRoundWhenLameDuckAwareWithoutLameDuck() throws Exception {
        // No lame duck signal, so LameDuckAware must behave as BeforeSubsequentRounds.
        List<Long> rounds = roundsSeenAcrossReconnect(ReconnectDelayBehavior.LameDuckAware, null);
        assertFalse(rounds.contains(1L), "round 1 must not invoke the handler without a lame duck signal: " + rounds);
    }

    @Test
    public void testDelayHandlerSeesNoLameDuckOnOrdinaryReconnect() throws Exception {
        List<Boolean> lameDucks = Collections.synchronizedList(new ArrayList<>());
        List<Long> rounds = roundsSeenAcrossReconnect(ReconnectDelayBehavior.BeforeAllRounds, lameDucks);
        assertFalse(rounds.isEmpty(), "the handler must have been invoked at least once");
        assertFalse(lameDucks.contains(true), "no invocation may report a lame duck: " + lameDucks);
    }

    @Test
    public void testLameDuckSignalDelaysFirstRoundWhenLameDuckAware() throws Exception {
        List<Long> rounds = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> lameDucks = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Boolean> clientReady = new CompletableFuture<>();
        Listener listener = new Listener();

        try (NatsTestServer landing = new NatsTestServer();
             NatsServerProtocolMock draining = drainingServer(clientReady)) {

            Options options = delayBehaviorOptions(ReconnectDelayBehavior.LameDuckAware, rounds, lameDucks)
                .servers(new String[]{draining.getServerUri(), landing.getServerUri()})
                .connectionListener(listener)
                .build();

            try (NatsConnection ignored = managedConnect(options)) {
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
                clientReady.complete(Boolean.TRUE); // release the mock: announce, then go away
                listener.validate();                // reconnected onto the landing server
            }
        }

        assertFalse(rounds.isEmpty(), "the handler must have been invoked");
        assertEquals(1L, rounds.get(0), "the lame duck signal must make round 1 invoke the handler: " + rounds);
        assertTrue(lameDucks.get(0), "round 1 must see lameDuckTriggered true: " + lameDucks);
    }

    @Test
    public void testLameDuckSignalIsConsumedAfterTheCampaignItTriggered() throws Exception {
        List<Long> rounds = Collections.synchronizedList(new ArrayList<>());
        List<Boolean> lameDucks = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Boolean> clientReady = new CompletableFuture<>();
        Listener listener = new Listener();

        // Three servers, all up front: the draining mock, then two landing spots so the second
        // campaign also has somewhere already listening to go.
        try (NatsTestServer landing1 = new NatsTestServer();
             NatsTestServer landing2 = new NatsTestServer();
             NatsServerProtocolMock draining = drainingServer(clientReady)) {

            Options options = delayBehaviorOptions(ReconnectDelayBehavior.LameDuckAware, rounds, lameDucks)
                .servers(new String[]{draining.getServerUri(), landing1.getServerUri(), landing2.getServerUri()})
                .connectionListener(listener)
                .build();

            try (NatsConnection ignored = managedConnect(options)) {
                // Campaign 1 - triggered by the lame duck signal.
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
                clientReady.complete(Boolean.TRUE);
                listener.validate();
                assertTrue(lameDucks.contains(true), "campaign 1 must have seen the lame duck: " + lameDucks);

                // Campaign 2 - landing1 goes away with no lame duck signal. The flag was consumed by
                // campaign 1, so round 1 must be skipped again. A sticky flag fails here.
                rounds.clear();
                lameDucks.clear();
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
                landing1.close();
                listener.validate();
            }
        }

        assertFalse(rounds.contains(1L), "a consumed lame duck signal must not survive into a later campaign: " + rounds);
        assertFalse(lameDucks.contains(true), "no invocation may report a lame duck: " + lameDucks);
    }

    /**
     * A mock that announces lame duck mid-connection and then goes away, which is the shape of a real
     * drain. Returning from the customizer exits the mock, so the client sees the announcement and the
     * drop in that order.
     */
    private NatsServerProtocolMock drainingServer(CompletableFuture<Boolean> clientReady) throws IOException {
        NatsServerProtocolMock.Customizer customizer = (ts, r, w) -> {
            try {
                clientReady.get(5, TimeUnit.SECONDS);
            }
            catch (Exception e) {
                return; // the assertions in the test will report this
            }
            w.write("INFO {\"server_id\":\"draining\",\"ldm\":true}\r\n");
            w.flush();
        };
        return new NatsServerProtocolMock(customizer, NatsTestServer.nextPort(), true);
    }

    /**
     * Options with a recording reconnect delay handler. The handler returns zero so the reconnect is not
     * actually slowed; the point is which rounds reach it at all, and what they are told about lame duck.
     * Callers add the servers. maxReconnects is bounded on purpose - an unbounded reconnect against a
     * server that never returns keeps non-daemon threads alive and hangs the test JVM rather than failing.
     */
    private OptionsBuilder delayBehaviorOptions(ReconnectDelayBehavior behavior,
                                                List<Long> rounds,
                                                List<Boolean> lameDucksOut) {
        return optionsBuilder()
            .noRandomize()
            .maxReconnects(10)
            .reconnectWait(50L)
            .reconnectJitter(0L)
            .reconnectDelayBehavior(behavior)
            .reconnectDelayHandler((round, o, secure, lameDuckTriggered) -> {
                rounds.add(round);
                if (lameDucksOut != null) {
                    lameDucksOut.add(lameDuckTriggered);
                }
                return 0L;
            });
    }

    /**
     * Connect to the first of two running servers, kill it, and return the round numbers the reconnect
     * delay handler was invoked with as the client moves to the second. The second server is up the whole
     * time, so nothing here depends on a port becoming reusable.
     */
    private List<Long> roundsSeenAcrossReconnect(ReconnectDelayBehavior behavior,
                                                 List<Boolean> lameDucksOut) throws Exception {
        List<Long> rounds = Collections.synchronizedList(new ArrayList<>());
        Listener listener = new Listener();

        try (NatsTestServer dying = new NatsTestServer();
             NatsTestServer landing = new NatsTestServer()) {

            Options options = delayBehaviorOptions(behavior, rounds, lameDucksOut)
                .servers(new String[]{dying.getServerUri(), landing.getServerUri()})
                .connectionListener(listener)
                .build();

            try (NatsConnection ignored = managedConnect(options)) {
                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
                dying.close();
                listener.validate();
            }
        }

        return rounds;
    }
}
