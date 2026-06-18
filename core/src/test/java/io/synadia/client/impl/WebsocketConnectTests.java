package io.synadia.client.impl;

import io.nats.NatsServerRunner;
import io.synadia.client.*;
import io.synadia.client.utils.*;
import io.synadia.client.utils.ssl.SslTestingHelper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static io.synadia.client.ConnectionEvents.CONNECTED;
import static io.synadia.client.ConnectionEvents.RECONNECTED;
import static io.synadia.client.NatsTestServer.configFileBuilder;
import static io.synadia.client.NatsTestServer.nextPort;
import static io.synadia.client.utils.ConnectionUtils.assertConnected;
import static io.synadia.client.utils.ConnectionUtils.managedConnect;
import static io.synadia.client.utils.OptionsUtils.NOOP_EL;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

public class WebsocketConnectTests extends TestBase {

    private static OptionsBuilder builder() {
        return Options.builder()
            .maxReconnects(0)
            .errorListener(NOOP_EL);
    }

    private static OptionsBuilder wsBuilder(NatsTestServer ts) {
        return builder()
            .server(NatsTestServer.getLocalhostUri(WS, ts.getPort(WS)));
    }

    private static OptionsBuilder wssBuilder(NatsTestServer ts) throws Exception {
        return builder()
            .server(NatsTestServer.getLocalhostUri(WSS, ts.getPort(WSS)))
            .sslContext(SslTestingHelper.createTestSSLContext());
    }

    private static void _test(OptionsBuilder builder) throws InterruptedException {
        try (NatsConnection connection = managedConnect(builder.build())) {
            Dispatcher dispatcher = connection.createDispatcher(
                msg -> connection.publish(msg.getReplyTo(), (new String(msg.getData()) + ":reply").getBytes()));
            String subject = random();
            dispatcher.subscribe(subject);
            for (int x = 0; x < 10; x++) {
                String data = random() + x;
                Message response = connection.requestAsync(subject, data.getBytes()).join();
                assertEquals(data + ":reply", new String(response.getData()));
            }
        }
    }

    @Test
    public void testWs() throws Exception {
        runInSharedConfiguredServer("ws.conf", ts -> {
            _test(optionsBuilder(ts));
            _test(wsBuilder(ts));
        });
    }

    @Test
    public void testWss() throws Exception {
        runInSharedConfiguredServer("wss.conf", ts -> {
            _test(optionsBuilder(ts));
            _test(wssBuilder(ts));
        });
    }

    @Test
    public void testWssVerify() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> {
            _test(optionsBuilder(ts));
            _test(wssBuilder(ts));
        });
    }

    private static Consumer<HttpRequest> getInterceptor() {
        return req -> {
            // Ideally we could validate that this header was sent to NATS server
            req.getHeaders().add("X-Ignored", "VALUE");
        };
    }

    @Test
    public void testWsInterceptor() throws Exception {
        runInSharedConfiguredServer("ws.conf",
            ts -> _test(wsBuilder(ts).httpRequestInterceptor(getInterceptor())));
    }

    @Test
    public void testWssInterceptor() throws Exception {
        runInSharedConfiguredServer("wss.conf",
            ts -> _test(wssBuilder(ts).httpRequestInterceptor(getInterceptor())));
    }

    @Test
    public void testWssVerifyInterceptor() throws Exception {
        runInSharedConfiguredServer("wssverify.conf",
            ts -> _test(wssBuilder(ts).httpRequestInterceptor(getInterceptor())));
    }

    @Test
    public void testWsOpenTLS() throws Exception {
        runInSharedConfiguredServer("ws.conf", ts -> _test(wsBuilder(ts).opentls()));
    }

    @Test
    public void testWssOpenTLS() throws Exception {
        runInSharedConfiguredServer("wss.conf", ts -> _test(wssBuilder(ts).opentls()));
    }

    @Test
    public void testWssVerifyOpenTLS() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> _test(wssBuilder(ts).opentls()));
    }

    @Test
    public void testProxyRequestReply() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        RunProxy proxy = new RunProxy(new InetSocketAddress("localhost", 0), null, executor);
        executor.submit(proxy);

        runInSharedConfiguredServer("ws.conf", ts -> {
            OptionsBuilder builder = wsBuilder(ts)
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", proxy.getPort())));
            _test(builder);
        });
    }

    @Test
    public void testWssTlsFirstIgnored() throws Exception {
        runInSharedConfiguredServer("wss.conf", ts -> _test(wssBuilder(ts).tlsFirst()));
    }

    @Test
    public void testWssVerifyTlsFirstIgnored() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> _test(wssBuilder(ts).tlsFirst()));
    }

    @Test
    public void testTLSOnReconnect() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener();
        int port = nextPort();
        int wssPort = nextPort();

        // can't use shared b/c custom ports
        NatsServerRunner.Builder builder = configFileBuilder("wssverify.conf")
            .port(port)
            .port(WSS, wssPort);
        SSLContext ctx = SslTestingHelper.createTestSSLContext();

        // Use two server ports to avoid port release timing issues
        try (NatsTestServer ignored = new NatsTestServer(builder)) {
            Options options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(WSS, wssPort))
                .noRandomize()
                .maxReconnects(-1)
                .sslContext(ctx)
                .connectionListener(listener)
                .errorListener(NOOP_EL)
                .reconnectWait(10L)
                .build();

            listener.queueConnectionEvent(CONNECTED);
            nc = Nats.connect(options);
            assertInstanceOf(SocketDataPort.class, ((NatsConnection) nc).getDataPort(), "Correct data port class");
            listener.validate();
            assertConnected(nc);
        }

        listener.queueConnectionEvent(RECONNECTED);
        try (NatsTestServer ignored = new NatsTestServer(builder)) {
            listener.validate();
            assertConnected(nc);
        }
    }

    @Test
    public void testDisconnectOnUpgrade() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createTestSSLContext();
            Options options = Options.builder()
                .server(ts.getLocalhostUri(WSS))
                .dataPortType(CloseOnUpgradeAttempt.class.getCanonicalName())
                .sslContext(ctx)
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientInsecureServerSecureMismatchWss() throws Exception {
        runInSharedConfiguredServer("wss.conf", ts -> {
            Options options = builder()
                .server(NatsTestServer.getLocalhostUri(WS, ts.getPort(WSS)))
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientInsecureServerSecureMismatchWssVerify() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> {
            Options options = builder()
                .server(NatsTestServer.getLocalhostUri(WS, ts.getPort(WSS)))
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientSecureServerInsecureMismatch() throws Exception {
        runInSharedOwnNc(nc -> {
            //noinspection DataFlowIssue
            Options options = builder()
                .server(nc.getConnectedUrl())
                .sslContext(SslTestingHelper.createTestSSLContext())
                .build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientServerCertMismatchWss() throws Exception {
        runInSharedConfiguredServer("wss.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createEmptySSLContext();
            Options options = wssBuilder(ts).sslContext(ctx).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }

    @Test
    public void testClientServerCertMismatchWssVerify() throws Exception {
        runInSharedConfiguredServer("wssverify.conf", ts -> {
            SSLContext ctx = SslTestingHelper.createEmptySSLContext();
            Options options = wssBuilder(ts).sslContext(ctx).build();
            assertThrows(IOException.class, () -> Nats.connect(options));
        });
    }
}
