package io.synadia.client.impl;

import io.nats.NatsServerRunner;
import io.synadia.client.*;
import io.synadia.client.utils.CloseOnUpgradeAttempt;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.SSLUtils;
import io.synadia.client.utils.TestBase;
import io.synadia.client.utils.ssl.ExpiringClientCertUtil;
import io.synadia.client.utils.ssl.ExpiringComponents;
import io.synadia.client.utils.ssl.SniTestServer;
import io.synadia.client.utils.ssl.SslTestingHelper;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.OptionsProperties.*;
import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ResourceUtils.createTempDirectory;
import static io.synadia.client.utils.ResourceUtils.deleteFileOrFolder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class TLSConnectTests extends TestBase {

    private static Options createTestOptionsManually(String... servers) throws Exception {
        return optionsBuilder(servers)
            .maxReconnects(0)
            .sslContext(SslTestingHelper.createTestSSLContext())
            .build();
    }

    private static Options createTestOptionsViaProperties(String... servers) {
        Options options;
        Properties props = SslTestingHelper.createTestSSLProperties();
        props.setProperty(PROP_SERVERS, String.join(",", servers));
        props.setProperty(PROP_MAX_RECONNECTS, "0");
        options = new OptionsBuilder(props).build();
        return options;
    }

    private static Options createTestOptionsViaFactoryInstance(String... servers) {
        return optionsBuilder(servers)
            .maxReconnects(0)
            .sslContextFactory(new SSLContextFactoryForTesting())
            .build();
    }

    private static Options createTestOptionsViaFactoryClassName(String... servers) {
        Properties properties = new Properties();
        properties.setProperty(PROP_SSL_CONTEXT_FACTORY_CLASS, SSLContextFactoryForTesting.class.getCanonicalName());
        return optionsBuilder(servers)
            .properties(properties)
            .maxReconnects(0)
            .sslContextFactory(new SSLContextFactoryForTesting())
            .build();
    }

    @Test
    public void testSimpleTLSConnection() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 -> {
            String servers = ts1.getServerUri();
            assertCanConnectAndPubSub(createTestOptionsManually(servers));
            assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
        });
    }

    @Test
    public void testMultipleUrlTLSConnectionSetContext() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 ->
            runInSharedConfiguredServer("tls.conf", 2, ts2 -> {
                String[] servers = NatsTestServer.getLocalhostUris("tls", ts1, ts2);
                assertCanConnectAndPubSub(createTestOptionsManually(servers));
                assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
                assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
                assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
            }));
    }

    @Test
    public void testSimpleIPTLSConnection() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 -> {
            String servers = "127.0.0.1:" + ts1.getNatsPort();
            assertCanConnectAndPubSub(createTestOptionsManually(servers));
            assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
        });
    }

    @Test
    public void testURISchemeTrustAllTLSConnection() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 -> {
            String[] servers = NatsTestServer.getLocalhostUris("tls", ts1);
            Options options = optionsBuilder(servers)
                .maxReconnects(0)
                .sslContext(SSLUtils.createTrustAllTlsContext())
                .build();
            assertCanConnectAndPubSub(options);
        });
    }

    @Test
    public void testMultipleUrlTrustAllTLSConnection() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 ->
            runInSharedConfiguredServer("tls.conf", 2, ts2 -> {
                String[] servers = NatsTestServer.getLocalhostUris("tls", ts1, ts2);
                Options options = optionsBuilder(servers)
                    .maxReconnects(0)
                    .sslContext(SSLUtils.createTrustAllTlsContext())
                    .build();
                assertCanConnectAndPubSub(options);
            }));
    }

    @Test
    public void testProxyNotTlsFirst() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 -> {
            // 1. client regular secure | secure proxy | server insecure -> mismatch exception
            Listener listener = new Listener();
            ProxyConnection connRI = new ProxyConnection(ts1.getServerUri(), false, listener, SERVER_INSECURE);
            listener.queueException(IOException.class, "SSL connection wanted by client");
            assertThrows(Exception.class, () -> connRI.connect(false));
            sleep(100); // give time for listener to get message
            assertEquals(1, listener.getExceptionCount());

            // 2. client regular secure | secure proxy | server tls required  -> connects
            ProxyConnection connRR = new ProxyConnection(ts1.getServerUri(), false, null, SERVER_TLS_REQUIRED);
            connRR.connect(false);
            confirmConnectedThenClosed(connRR);

            // 3. client regular secure | secure proxy | server tls available -> connects
            ProxyConnection connRA = new ProxyConnection(ts1.getServerUri(), false, null, SERVER_TLS_AVAILABLE);
            connRA.connect(false);
            confirmConnectedThenClosed(connRA);
        });
    }

    @Test
    public void testTrustAllTLSConnection() throws Exception {
        runInSharedConfiguredServer("tls.conf", 1, ts1 -> {
            Options options = optionsBuilder()
                .server(ts1.getServerUri())
                .maxReconnects(0)
                .sslContext(SSLUtils.createTrustAllTlsContext())
                .build();
            assertCanConnectAndPubSub(options);
        });
    }

    @Test
    public void testSimpleTlsFirstConnection() throws Exception {
        runInSharedConfiguredServer("tls_first.conf", ts -> {
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .tlsFirst()
                .sslContext(SslTestingHelper.createTestSSLContext())
                .build();
            assertCanConnectAndPubSub(options);
        });
    }

    @Test
    public void testVerifiedTLSConnection() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            String servers = ts.getServerUri();
            assertCanConnectAndPubSub(createTestOptionsManually(servers));
            assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
        });
    }

    @Test
    public void testURISchemeTLSConnection() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            String servers = "tls://localhost:" + ts.getNatsPort();
            assertCanConnectAndPubSub(createTestOptionsManually(servers));
            assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
        });
    }

    @Test
    public void testURISchemeIPTLSConnection() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            String servers = "tls://127.0.0.1:" + ts.getNatsPort();
            assertCanConnectAndPubSub(createTestOptionsManually(servers));
            assertCanConnectAndPubSub(createTestOptionsViaProperties(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryInstance(servers));
            assertCanConnectAndPubSub(createTestOptionsViaFactoryClassName(servers));
        });
    }

    @Test
    public void testTLSMessageFlow() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createTestSSLContext();
            int msgCount = 100;
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .sslContext(ctx)
                .build();
            try (NatsConnection nc = managedConnect(options)) {
                Dispatcher d = nc.createDispatcher(
                    msg -> nc.publish(msg.getReplyTo(), new byte[16]));
                String subject = random();
                d.subscribe(subject);

                for (int i = 0; i < msgCount; i++) {
                    Future<Message> incoming = nc.requestAsync(subject, null);
                    Message msg = incoming.get(500, TimeUnit.MILLISECONDS);
                    assertNotNull(msg);
                    assertEquals(16, msg.getData().length);
                }
            }
        });
    }

    @Test
    public void testTLSOnReconnect() throws Exception {
        AtomicReference<NatsConnection> ncRef = new AtomicReference<>();
        Listener listener = new Listener();

        // Use two server ports to avoid port release timing issues
        int port = NatsTestServer.nextPort();
        int newPort = NatsTestServer.nextPort();

        runInConfiguredServer("tlsverify.conf", port, ts -> {
            SSLContext ctx = SslTestingHelper.createTestSSLContext();
            Options options = optionsBuilder(ts.getServerUri(), NatsTestServer.getLocalhostUri(newPort))
                .maxReconnects(-1)
                .sslContext(ctx)
                .connectionListener(listener)
                .reconnectWait(10L)
                .build();
            ncRef.set(managedConnect(options));
            assertInstanceOf(SocketDataPort.class, ncRef.get().getDataPort(), "Correct data port class");
            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        });

        NatsConnection nc = ncRef.get();
        flushConnection(nc);
        listener.validate();

        listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);
        runInConfiguredServer("tlsverify.conf", newPort, ts -> listener.validate());
        closeAndConfirm(nc);
    }

    @Test
    public void testDisconnectOnUpgrade() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createTestSSLContext();
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .dataPortType(CloseOnUpgradeAttempt.class.getCanonicalName())
                .sslContext(ctx)
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testServerSecureClientNotMismatch() throws Exception {
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            Options options = optionsBuilder(ts).maxReconnects(0).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientSecureServerNotMismatch() throws Exception {
        runInSharedOwnNc(nc -> {
            SSLContext ctx = SslTestingHelper.createTestSSLContext();
            Options options = optionsBuilder(nc).maxReconnects(0).sslContext(ctx).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientServerCertMismatch() throws Exception {
        Listener listener = new Listener();
        listener.queueException(CertificateException.class);
        runInSharedConfiguredServer("tlsverify.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createEmptySSLContext();
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .sslContext(ctx)
                .errorListener(listener)
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
            listener.validate();
        });
    }

    @Test
    public void testSSLContextFactoryPropertiesPassOnCorrectly() throws NoSuchAlgorithmException {
        SSLContextFactoryForTesting factory = new SSLContextFactoryForTesting();
        optionsBuilder()
            .sslContextFactory(factory)
            .keystorePath("keystorePath")
            .keystorePassword("ksp".toCharArray())
            .truststorePath("truststorePath")
            .truststorePassword("tsp".toCharArray())
            .secure()
            .tlsAlgorithm("tlsAlgorithm")
            .build();

        assertEquals("keystorePath", factory.properties.keystorePath);
        assertEquals("keystorePath", factory.properties.getKeystorePath());
        assertEquals("ksp", new String(factory.properties.keystorePassword));
        assertEquals("ksp", new String(factory.properties.getKeystorePassword()));
        assertEquals("truststorePath", factory.properties.truststorePath);
        assertEquals("truststorePath", factory.properties.getTruststorePath());
        assertEquals("tsp", new String(factory.properties.truststorePassword));
        assertEquals("tsp", new String(factory.properties.getTruststorePassword()));
        assertEquals("tlsAlgorithm", factory.properties.tlsAlgorithm);
        assertEquals("tlsAlgorithm", factory.properties.getTlsAlgorithm());
    }

    private static final int SERVER_INSECURE = 1;
    private static final int SERVER_TLS_AVAILABLE = 2;
    private static final int SERVER_TLS_REQUIRED = 3;
    static class ProxyConnection extends NatsConnection {
        int serverType;

        public ProxyConnection(String servers, boolean tlsFirst, ErrorListener listener, int serverType) throws Exception {
            super(makeMiddleman(servers, tlsFirst, listener));
            this.serverType = serverType;
        }

        private static Options makeMiddleman(String servers, boolean tlsFirst, ErrorListener listener) throws Exception {
            OptionsBuilder builder = optionsBuilder(servers)
                .maxReconnects(0)
                .sslContext(SslTestingHelper.createTestSSLContext())
                .errorListener(listener);

            if (tlsFirst) {
                builder.tlsFirst();
            }

            return builder.build();
        }

        @Override
        protected void handleInfo(String infoJson) {
            switch (serverType) {
                case SERVER_INSECURE:
                    super.handleInfo(infoJson.replace(",\"tls_required\":true", "")); break;
                case SERVER_TLS_AVAILABLE:
                    super.handleInfo(infoJson.replace("\"tls_required\":true", "\"tls_available\":true")); break;
                default:
                    super.handleInfo(infoJson);
            }
        }
    }

    /*
        1. client tls first      | secure proxy | server insecure      -> connects
        2. client tls first      | secure proxy | server tls required  -> connects
        3. client tls first      | secure proxy | server tls available -> connects
        4. client regular secure | secure proxy | server insecure      -> mismatch exception
        5. client regular secure | secure proxy | server tls required  -> connects
        6. client regular secure | secure proxy | server tls available -> connects
    */
    @Test
    public void testProxyTlsFirst() throws Exception {
        // cannot check connect b/c tls first
        runInSharedConfiguredServer("tls_first.conf", ts -> {
            // 1. client tls first | secure proxy | server insecure -> connects
            ProxyConnection connTI = new ProxyConnection(ts.getServerUri(), true, null, SERVER_INSECURE);
            connTI.connect(false);
            confirmConnectedThenClosed(connTI);

            // 2. client tls first | secure proxy | server tls required -> connects
            ProxyConnection connTR = new ProxyConnection(ts.getServerUri(), true, null, SERVER_TLS_REQUIRED);
            connTR.connect(false);
            confirmConnectedThenClosed(connTR);

            // 3. client tls first | secure proxy | server tls available -> connects
            ProxyConnection connTA = new ProxyConnection(ts.getServerUri(), true, null, SERVER_TLS_AVAILABLE);
            connTA.connect(false);
            confirmConnectedThenClosed(connTA);
        });
    }

    @Test
    public void testConnectFailsFromSslContext() throws Exception {
        SSLContext sslContext = SslTestingHelper.getFailContext();

        SslTestErrorListener el = new SslTestErrorListener(1);

        runInSharedConfiguredServer("tls.conf", 1, ts -> {
            Options options = new OptionsBuilder()
                .server(ts.getNatsLocalhostUri())
                .sslContext(sslContext)
                .maxReconnects(1)
                .connectionTimeout(2000)
                .errorListener(el)
                .build();

            try (NatsConnection nc = Nats.connect(options)) {
                fail("Should not have connected");
            }
            catch (Exception e) {
                assertTrue(e.getMessage().contains("Unable to connect to NATS servers"));
            }

            assertTrue(el.latch.await(2, TimeUnit.SECONDS));
        });
    }

    @Test
    public void testConnectFailsCertAlreadyExpired() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.createExpired();
            validateExpiry(expiring, true);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestErrorListener el = new SslTestErrorListener(1);

            NatsServerRunner.Builder b = NatsServerRunner.builder().configFilePath(configFilePath);
            try (NatsTestServer ts = new NatsTestServer(b)) {
                Options options = new OptionsBuilder()
                    .server(ts.getNatsLocalhostUri())
                    .sslContext(expiring.sslContext)
                    .maxReconnects(1)
                    .connectionTimeout(5000)
                    .errorListener(el)
                    .build();

                try (NatsConnection nc = Nats.connect(options)) {
                    fail("should have thrown an exception");
                }
                catch (Exception e) {
                    assertTrue(e.getMessage().contains("Unable to connect to NATS servers"));
                }

                assertTrue(el.latch.await(2, TimeUnit.SECONDS));
            }
        }
        finally {
            deleteFileOrFolder(tmpDir);
        }
    }

    static final int CLIENT_CERT_VALIDITY_MILLIS = 5000;

    @Test
    public void testReconnectFailsAfterCertExpires() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.create(CLIENT_CERT_VALIDITY_MILLIS);
            validateExpiry(expiring, false);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestConnectionListener cl = new SslTestConnectionListener(1);
            SslTestErrorListener el = new SslTestErrorListener(2);

            NatsConnection nc;
            NatsServerRunner.Builder b1 = NatsServerRunner.builder().configFilePath(configFilePath);
            NatsServerRunner.Builder b2 = NatsServerRunner.builder().configFilePath(configFilePath);
            try (NatsTestServer ts1 = new NatsTestServer(b1)) {
                try (NatsTestServer ts2 = new NatsTestServer(b2)) {
                    Options options = new OptionsBuilder()
                        .servers(new String[]{ts2.getNatsLocalhostUri(), ts1.getNatsLocalhostUri()})
                        .noRandomize()
                        .sslContext(expiring.sslContext)
                        .maxReconnects(1)
                        .connectionTimeout(5000)
                        .connectionListener(cl)
                        .errorListener(el)
                        .build();

                    nc = Nats.connect(options);
                    assertEquals(ConnectionStatus.CONNECTED, nc.getStatus());
                    assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                    sleep(CLIENT_CERT_VALIDITY_MILLIS); // sleep enough time for the cert to expire
                    validateExpiry(expiring, true);
                }
                assertTrue(el.latch.await(2, TimeUnit.SECONDS));
                nc.close();
            }
        }
        finally {
            deleteFileOrFolder(tmpDir);
        }
    }

    @Test
    public void testForceReconnectFailsAfterCertExpires() throws Exception {
        Path tmpDir = null;
        try {
            ExpiringComponents expiring = ExpiringClientCertUtil.create(CLIENT_CERT_VALIDITY_MILLIS);
            validateExpiry(expiring, false);

            tmpDir = createTempDirectory();
            String configFilePath = expiring.writeNatsConfig(tmpDir);

            SslTestConnectionListener cl = new SslTestConnectionListener(1);
            SslTestErrorListener el = new SslTestErrorListener(2);

            NatsServerRunner.Builder b = NatsServerRunner.builder().configFilePath(configFilePath);
            try (NatsTestServer ts = new NatsTestServer(b)) {
                Options options = new OptionsBuilder()
                    .server(ts.getNatsLocalhostUri())
                    .sslContext(expiring.sslContext)
                    .maxReconnects(1)
                    .connectionTimeout(5000)
                    .connectionListener(cl)
                    .errorListener(el)
                    .build();

                try (NatsConnection nc = Nats.connect(options)) {
                    assertEquals(ConnectionStatus.CONNECTED, nc.getStatus());
                    assertTrue(cl.latch.await(2, TimeUnit.SECONDS));
                    sleep(CLIENT_CERT_VALIDITY_MILLIS); // sleep enough time for the cert to expire
                    validateExpiry(expiring, true);
                    nc.forceReconnect();
                    assertTrue(el.latch.await(2, TimeUnit.SECONDS));
                }
            }
        }
        finally {
            deleteFileOrFolder(tmpDir);
        }
    }

    private void validateExpiry(ExpiringComponents expiring, boolean expired) {
        Date expiry = expiring.sslContext.getClientCertificateExpiry();
        Date now = new Date();
        if (expired) {
            assertTrue(expiry.before(now));
        }
        else {
            assertFalse(expiry.before(now));
        }
    }

    static class SslTestConnectionListener implements ConnectionListener {
        public CountDownLatch latch;

        public SslTestConnectionListener(int latchAmount) {
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void connectionEvent(NatsConnection conn, ConnectionEvent type, Long date, String uriDetails) {
            if (type == ConnectionEvent.CONNECTED) {
                latch.countDown();
            }
        }
    }

    static class SslTestErrorListener implements ErrorListener {
        public CountDownLatch latch;

        public SslTestErrorListener(int latchAmount) {
            latch = new CountDownLatch(latchAmount);
        }

        @Override
        public void exceptionOccurred(NatsConnection conn, Exception exp) {
            if (hasSslOrSocketCauseInChain(exp)) {
                if (latch.getCount() > 0) {
                    latch.countDown();
                }
            }
        }

        boolean hasSslOrSocketCauseInChain(Throwable t) {
            while (t != null) {
                if (t instanceof SSLException || t instanceof SocketException) {
                    return true;
                }
                t = t.getCause();
            }
            return false;
        }
    }

    // ---- a ws:// server when the options require TLS ----

    static class RejectionListener extends Listener {
        final List<String> exceptionMessages = new CopyOnWriteArrayList<>();

        @Override
        public void exceptionOccurred(NatsConnection conn, Exception exp) {
            super.exceptionOccurred(conn, exp);
            exceptionMessages.add(String.valueOf(exp.getMessage()));
        }
    }

    private static OptionsBuilder tlsRequiring(SniTestServer server, SniTestServer.Certificates certificates, Listener listener) throws Exception {
        // the certificate carries 127.0.0.1 as an ip subject alternative name, so the default hostname verification passes
        return Options.builder()
            .server("tls://127.0.0.1:" + server.port())
            .sslContext(certificates.clientContext())
            .connectionListener(listener)
            .errorListener(listener)
            .noRandomize()
            .connectionTimeout(2000);
    }

    private static void assertRejectedOnce(RejectionListener listener, String server) {
        for (int x = 0; x < 200 && listener.getServerRejectedEvents().isEmpty(); x++) {
            sleep(10);
        }
        List<Listener.ServerRejectedEvent> events = listener.getServerRejectedEvents();
        assertEquals(1, events.size(), events.toString());
        assertEquals(server, events.get(0).server());
        assertTrue(events.get(0).reason().contains("never upgraded to TLS"), events.get(0).reason());
        for (String m : listener.exceptionMessages) {
            assertFalse(m.contains(server), m);
        }
    }

    // a pool that hands out the given server first; it can ignore serverRejected and keep the server,
    // and it can hold only that server
    private static ServerPool poolWithFirst(NatsUri first, boolean keepsRejected, boolean only) {
        return new NatsServerPool() {
            @Override
            public void initialize(@NonNull Options opts) {
                super.initialize(opts);
                listLock.lock();
                try {
                    if (only) {
                        entryList.clear();
                    }
                    entryList.add(0, new ServerPoolEntry(first, false));
                }
                finally {
                    listLock.unlock();
                }
            }

            @Override
            public void serverRejected(@NonNull NatsUri nuri) {
                if (!keepsRejected) {
                    super.serverRejected(nuri);
                }
            }
        };
    }

    @Test
    public void testDiscoveredPlainWebsocketServerIsRejectedOnce() throws Exception {
        // a ws:// entry in connect_urls enters the pool like any other entry. When the pool first hands it out,
        // a connection that requires TLS skips it without dialing it, goes on to the next server, reports it
        // through serverRejected, and tells the pool, which drops it and does not take it back from a later INFO
        SniTestServer.Certificates certificates = new SniTestServer.Certificates(SniTestServer.HOST, true);
        RejectionListener listener = new RejectionListener();
        try (ServerSocket plainWs = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            String wsUrl = "ws://127.0.0.1:" + plainWs.getLocalPort();
            try (SniTestServer second = new SniTestServer(certificates, false, Collections.singletonList(wsUrl), true);
                 SniTestServer first = new SniTestServer(certificates, false, Arrays.asList(wsUrl, "127.0.0.1:" + second.port()), true)) {
                Options options = tlsRequiring(first, certificates, listener)
                    .reconnectWait(50)
                    .maxReconnects(3)
                    .build();
                listener.queueConnectionEvent(ConnectionEvent.DISCOVERED_SERVERS);
                try (NatsConnection nc = Nats.connect(options)) {
                    nc.flush(2000);
                    listener.validate();
                    Collection<String> servers = nc.getServers();
                    assertTrue(servers.contains(wsUrl), servers.toString());
                    assertTrue(servers.contains("tls://127.0.0.1:" + second.port()), servers.toString());
                    assertTrue(listener.getServerRejectedEvents().isEmpty());

                    // the first server goes away: the pool hands out the ws entry, then the second server
                    listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);
                    first.close();
                    listener.validate();
                    assertEquals("tls://127.0.0.1:" + second.port(), nc.getConnectedUrl());

                    assertRejectedOnce(listener, wsUrl);
                    // the second server's INFO advertised the ws entry again and the pool did not take it back
                    servers = nc.getServers();
                    assertFalse(servers.contains(wsUrl), servers.toString());
                    plainWs.setSoTimeout(500);
                    assertThrows(SocketTimeoutException.class, plainWs::accept);
                }
            }
        }
    }

    @Test
    public void testPlainWebsocketServerFromThePoolIsSkipped() throws Exception {
        // a custom ServerPool can hand the connection a ws:// server. With TLS required it is skipped without
        // being dialed, reported through serverRejected, and handed back to the pool, which drops it;
        // the connection goes on to the next server
        SniTestServer.Certificates certificates = new SniTestServer.Certificates(SniTestServer.HOST, true);
        RejectionListener listener = new RejectionListener();
        try (ServerSocket plainWs = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             SniTestServer server = new SniTestServer(certificates, false, 0, true)) {
            NatsUri wsUri = new NatsUri("ws://127.0.0.1:" + plainWs.getLocalPort());
            Options options = tlsRequiring(server, certificates, listener).serverPool(poolWithFirst(wsUri, false, false)).build();
            try (NatsConnection nc = Nats.connect(options)) {
                nc.flush(2000);
                assertEquals("tls://127.0.0.1:" + server.port(), nc.getConnectedUrl());
                assertRejectedOnce(listener, wsUri.toString());
                assertFalse(nc.getServers().contains(wsUri.toString()), nc.getServers().toString());
                plainWs.setSoTimeout(500);
                assertThrows(SocketTimeoutException.class, plainWs::accept);
            }
        }
    }

    @Test
    public void testConnectEndsWhenThePoolKeepsOnlyARejectedServer() throws Exception {
        // a pool that ignores serverRejected and holds only a ws:// server: the connect ends instead of
        // taking the same server forever, and reports it once
        SniTestServer.Certificates certificates = new SniTestServer.Certificates(SniTestServer.HOST, true);
        RejectionListener listener = new RejectionListener();
        try (ServerSocket plainWs = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             SniTestServer server = new SniTestServer(certificates, false, 0, true)) {
            NatsUri wsUri = new NatsUri("ws://127.0.0.1:" + plainWs.getLocalPort());
            Options options = tlsRequiring(server, certificates, listener).serverPool(poolWithFirst(wsUri, true, true)).build();
            IOException e = assertThrows(IOException.class, () -> Nats.connect(options));
            assertTrue(e.getMessage().contains("Unable to connect"), e.getMessage());
            assertRejectedOnce(listener, wsUri.toString());
            plainWs.setSoTimeout(500);
            assertThrows(SocketTimeoutException.class, plainWs::accept);
        }
    }

    @Test
    public void testReconnectEndsWhenThePoolKeepsOnlyARejectedServer() throws Exception {
        // a pool that ignores serverRejected keeps a ws:// server next to a TLS server. The connect and the
        // reconnect each report it once. When the TLS server has used up its attempts and the pool holds only
        // the ws:// server, the reconnect ends and the connection closes instead of taking the same server forever
        SniTestServer.Certificates certificates = new SniTestServer.Certificates(SniTestServer.HOST, true);
        RejectionListener listener = new RejectionListener();
        try (ServerSocket plainWs = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            NatsUri wsUri = new NatsUri("ws://127.0.0.1:" + plainWs.getLocalPort());
            SniTestServer server = new SniTestServer(certificates, false, 0, true);
            Options options = tlsRequiring(server, certificates, listener)
                .serverPool(poolWithFirst(wsUri, true, false))
                .reconnectWait(50)
                .maxReconnects(2)
                .build();
            try (NatsConnection nc = Nats.connect(options)) {
                nc.flush(2000);
                listener.queueConnectionEvent(ConnectionEvent.CLOSED, 10000);
                server.close();
                listener.validate();
                List<Listener.ServerRejectedEvent> events = listener.getServerRejectedEvents();
                assertEquals(2, events.size(), events.toString());
                plainWs.setSoTimeout(500);
                assertThrows(SocketTimeoutException.class, plainWs::accept);
            }
            finally {
                server.close();
            }
        }
    }
}
