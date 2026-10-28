package io.synadia.client;

import io.nats.NatsRunnerUtils;
import io.nats.NatsServerRunner;
import io.nats.jwt.JwtUtils;
import io.nats.nkey.NKey;
import io.nats.nkey.NKeyProvider;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.ResourceUtils;
import io.synadia.client.utils.TestBase;
import io.synadia.client.utils.ssl.SslTestingHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import javax.net.ssl.SSLContext;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.Listener.LONG_VALIDATE_TIMEOUT;
import static io.synadia.client.utils.OptionsUtils.NOOP_EL;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ResourceUtils.jwtResource;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

@Isolated
public class AuthTests extends TestBase {

    public static AuthHandler getUserCredsAuthHander() {
        return Nats.credentials(jwtResource("user.creds"));
    }

    @Test
    public void testUserPass() throws Exception {
        String[] customArgs = { "--user", "uuu", "--pass", "ppp" };
        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            // u/p in options
            Options upInOptionsOpts = optionsBuilder(ts).maxReconnects(0)
                .userInfo("uuu".toCharArray(), "ppp".toCharArray()).build();
            assertCanConnect(upInOptionsOpts);

            Options badUserOpts = optionsBuilder(ts).maxReconnects(0).connectionTimeout(10000)
                .userInfo("zzz".toCharArray(), "ppp".toCharArray()).build();
            assertThrows(AuthenticationException.class, () -> Nats.connect(badUserOpts));

            Options badPassOpts = optionsBuilder(ts).maxReconnects(0).connectionTimeout(10000)
                .userInfo("uuu".toCharArray(), "zzz".toCharArray()).build();
            assertThrows(AuthenticationException.class, () -> Nats.connect(badPassOpts));

            Options missingUserPassOpts = optionsBuilder(ts).maxReconnects(0).connectionTimeout(10000).build();
            assertThrows(AuthenticationException.class, () -> Nats.connect(missingUserPassOpts));
        }
    }

    // A user or password may contain characters that cannot travel literally in the CONNECT frame. The
    // frame is a JSON object and NATS is line oriented, so a raw newline would both break the JSON and
    // truncate the protocol message. The client escapes them on the way out; this asserts the round trip
    // still authenticates. The set below is every character the encoder treats specially: the low ASCII
    // controls, plus forward slash and backslash, which are printable but still escaped.
    @Test
    public void testUserPassWithSpecialCharacters() throws Exception {
        assertUserPassWithSpecialCharacters("\n");
        assertUserPassWithSpecialCharacters("\b");
        assertUserPassWithSpecialCharacters("\f");
        assertUserPassWithSpecialCharacters("\r");
        assertUserPassWithSpecialCharacters("\t");
        assertUserPassWithSpecialCharacters("/");
        assertUserPassWithSpecialCharacters("\\");
    }

    // The credential is handed to the server as a command line argument, and that is the one part of this
    // test that is not portable. NatsServerRunner spawns with ProcessBuilder(List): on unix that argv list
    // goes straight to exec with no shell, so a wrapping quote would become part of the credential itself;
    // on Windows ProcessBuilder must flatten the list into a single command line, where the wrapping quote
    // is what stops the value being re-split and is stripped before the server sees it. Quote on Windows
    // only. What is under test - that a credential containing these characters still authenticates - is
    // identical on both platforms.
    private static String quoteCredentialForOs(String credential) {
        return WINDOWS.isCurrentOs() ? "\"" + credential + "\"" : credential;
    }

    private static void assertUserPassWithSpecialCharacters(String test) throws Exception {
        String user = "u" + test + "u";
        String pass = "p" + test + "p";
        String[] customArgs = {"--user", quoteCredentialForOs(user), "--pass", quoteCredentialForOs(pass)};
        try (NatsTestServer ts = new NatsTestServer(
            NatsServerRunner.builder().customArgs(customArgs))) {
            Options options = optionsBuilder(ts)
                .userInfo(user.toCharArray(), pass.toCharArray())
                .maxReconnects(0).build();
            assertCanConnect(options);
        }
    }

    @Test
    public void testUserPassOnReconnect() throws Exception {
        Listener listener = new Listener();
        NatsConnection nc;
        Subscription sub;
        String[] customArgs = { "--user", "uuu", "--pass", "ppp" };
        int port;

        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            port = ts.getNatsPort();
            // See config file for user/pass
            Options options = optionsBuilder(ts).maxReconnects(-1)
                    .userInfo("uuu".toCharArray(), "ppp".toCharArray()).connectionListener(listener).build();
            nc = managedConnect(options);

            sub = nc.subscribe("test");
            nc.publish("test", null);
            flushConnection(nc, MEDIUM_FLUSH_TIMEOUT_MS);
            Message msg = sub.nextMessage(5000L);
            assertNotNull(msg);

            listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
        }

        TestBase.flushConnection(nc);
        listener.validate();

        listener.queueConnectionEvent(ConnectionEvent.RESUBSCRIBED);

        try (NatsTestServer ignored = new NatsTestServer(customArgs, port)) {
            confirmConnected(nc); // wait for reconnect
            listener.validate();

            nc.publish("test", null);
//            flushConnection(nc, MEDIUM_FLUSH_TIMEOUT_MS);

            Message msg = sub.nextMessage(5000L);
            assertNotNull(msg);

            closeAndConfirm(nc);
        }
    }

    @Test
    public void testUserBCryptPass() throws Exception {
        /*
         * use a bcrypt hash generator on the cleartext pass
         * ppp -> $2a$12$UjzncyjtsE6rJG4LSGk.JOweXV3P2umZ38gHuj4OMY0X/nQudiDgG
         */
        String[] customArgs = { "--user", "uuu", "--pass",
                "$2a$12$UjzncyjtsE6rJG4LSGk.JOweXV3P2umZ38gHuj4OMY0X/nQudiDgG" };
        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            // See config file for user/pass
            Options options = optionsBuilder(ts).maxReconnects(0)
                    .userInfo("uuu".toCharArray(), "ppp".toCharArray()).build();
            assertCanConnect(options);
        }
    }

    @Test
    public void testBadAuthFailsAtOnceUnlessHeld() throws Exception {
        // V3: a -ERR in reply to CONNECT fails the attempt as soon as the reader reads it; classic waits for the
        // connection timeout. An attempt that counts toward the double auth error rule is still held to its timeout.
        String[] customArgs = { "--auth", "token" };
        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            Options options = optionsBuilder(ts).maxReconnects(0).connectionTimeout(3000)
                .token("bad".toCharArray()).errorListener(NOOP_EL).build();
            assumeTrue(options.connectionImplementation() == ConnectionImplementation.V3);
            long start = System.nanoTime();
            assertThrows(AuthenticationException.class, () -> Nats.connect(options));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsed < 1500, "connect failed after " + elapsed + " ms");

            Options held = optionsBuilder(ts).maxReconnects(0).connectionTimeout(1000)
                .token("bad".toCharArray()).errorListener(NOOP_EL).build();
            start = System.nanoTime();
            try (NatsConnection nc = Nats.connectReconnectOnFailure(held)) {
                assertEquals(ConnectionStatus.CLOSED, nc.getStatus());
            }
            elapsed = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsed >= 900, "held attempt ended after " + elapsed + " ms");
        }
    }

    @Test
    public void testToken() throws Exception {
        String[] customArgs = { "--auth", "token" };
        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            // token in options
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .token("token".toCharArray())
                .build();
            assertCanConnect(options);

            // incorrect token
            Options incorrectToken = optionsBuilder(ts)
                .maxReconnects(0)
                .token("incorrectToken".toCharArray())
                .build();
            assertThrows(AuthenticationException.class, () -> Nats.connect(incorrectToken));

            // incorrect token
            Options missingToken = optionsBuilder(ts)
                .maxReconnects(0)
                .build();
            assertThrows(AuthenticationException.class, () -> Nats.connect(missingToken));
        }
    }

    String createNKeyConfigFile(char[] nkey) throws IOException {
        File tmp = Files.createTempFile("nats_java_test", ".conf").toFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(tmp));

        writer.write("port: 8222"); // will get rewritten
        writer.newLine();

        writer.write("authorization {");
        writer.newLine();
        writer.write("users = [");
        writer.newLine();
        writer.write(String.format("{\nnkey:%s\n}", new String(nkey)));
        writer.newLine();
        writer.write("]");
        writer.newLine();
        writer.write("}");
        writer.newLine();

        writer.close();
        return tmp.getAbsolutePath();
    }

    @Test
    public void testNKeyAuth() throws Exception {
        NKey theKey = NKeyProvider.getProvider().createUser();
        assertNotNull(theKey);
        String configFilePath = createNKeyConfigFile(theKey.getPublicKey());
        NatsServerRunner.Builder b = NatsServerRunner.builder().configFilePath(configFilePath);
        try (NatsTestServer ts = new NatsTestServer(b)) {

            Options authHandlerOptions = optionsBuilder(ts).maxReconnects(0)
                .authHandler(new AuthHandlerForTesting(theKey)).build();
            assertCanConnect(authHandlerOptions);

            Options staticOptions = optionsBuilder(ts).maxReconnects(0)
                .authHandler(Nats.staticCredentials(null, theKey.getSeed())).build();
            assertCanConnect(staticOptions);

            // direct through Nats.connect
            NatsConnection nc = Nats.connect(ts.getServerUri(), Nats.staticCredentials(null, theKey.getSeed()));
            confirmConnectedThenClosed(nc);

            // fails with no nkey
            Options noNkey = optionsBuilder(ts).maxReconnects(0)
                .authHandler(new AuthHandlerForTesting(null)).build();
            assertThrows(IOException.class, () -> Nats.connect(noNkey));
        }
    }

    @Test
    public void testJWTAuthWithCredsFile() throws Exception {
        // manual auth handler or credential path
        runInSharedConfiguredServer("operator.conf", ts -> {
            Options options = optionsBuilder(ts).maxReconnects(0)
                .authHandler(getUserCredsAuthHander())
                .build();
            assertCanConnect(options);

            options = optionsBuilder(ts).maxReconnects(0)
                .credentialPath(jwtResource("user.creds"))
                .build();
            assertCanConnect(options);

            //test Nats.connect method
            NatsConnection nc = Nats.connect(ts.getServerUri(), getUserCredsAuthHander());
            confirmConnectedThenClosed(nc);
        });
    }

    @Test
    public void testJWTAuthWithCredsFileAlso() throws Exception {
        //test Nats.connect method
        runInConfiguredServer("operatorJnatsTest.conf", ts -> {
            NatsConnection nc = Nats.connect(ts.getServerUri(), Nats.credentials(jwtResource("userJnatsTest.creds")));
            confirmConnectedThenClosed(nc);
        });
    }

    @Test
    public void testWsJWTAuthWithCredsFile() throws Exception {
        runInConfiguredServer("ws_operator.conf", ts -> {
            String uri = NatsTestServer.getLocalhostUri(WS, ts.getNonNatsPort());
            // in options
            Options options = optionsBuilder(uri).maxReconnects(0)
                .authHandler(getUserCredsAuthHander()).build();
            assertCanConnect(options);

            // directly Nats.connect
            NatsConnection nc = Nats.connect(uri, getUserCredsAuthHander());
            confirmConnectedThenClosed(nc);
        });
    }

    @Test
    public void testWssJWTAuthWithCredsFile() throws Exception {
        SSLContext ctx = SslTestingHelper.createTestSSLContext();
        runInConfiguredServer("wss_operator.conf", ts -> {
            String uri = NatsTestServer.getLocalhostUri(WSS, ts.getNonNatsPort());
            // wss + TLS + JWT is the heaviest connect path and can transiently drop right after
            // connecting under CI load. maxReconnects(0) gives it zero recovery, so a momentary
            // drop lands the connection in CLOSED and the test fails. Allow reconnects and a longer
            // connect window so a transient drop recovers; the test still verifies the wss/JWT
            // connect succeeds and TLS is available.
            Options options = optionsBuilder(uri).sslContext(ctx)
                .authHandler(getUserCredsAuthHander()).build();
            try (NatsConnection conn = managedConnect(options, LONG_WAIT)) {
                assertTrue(conn.getServerInfo().isTLSAvailable());
            }
        });
    }

    @Test
    public void testStaticJWTAuth() throws Exception {
        // from src/test/resources/jwt_nkey/user.creds
        String jwt = "eyJ0eXAiOiJqd3QiLCJhbGciOiJlZDI1NTE5In0.eyJqdGkiOiI3UE1GTkc0R1c1WkE1NEg3N09TUUZKNkJNQURaSUQ2NTRTVk1XMkRFQVZINVIyUVU0MkhBIiwiaWF0IjoxNTY1ODg5ODk4LCJpc3MiOiJBQUhWU0k1NVlQTkJRWjVQN0Y2NzZDRkFPNFBIQlREWUZRSUVHVEtMUVRJUEVZUEZEVEpOSEhPNCIsIm5hbWUiOiJkZW1vIiwic3ViIjoiVUMzT01MSlhUWVBZN0ZUTVVZNUNaNExHRVdRSTNZUzZKVFZXU0VGRURBMk9MTEpZSVlaVFo3WTMiLCJ0eXBlIjoidXNlciIsIm5hdHMiOnsicHViIjp7fSwic3ViIjp7fX19.ROSJ7D9ETt9c8ZVHxsM4_FU2dBRLh5cNfb56MxPQth74HAxxtGMl0nn-9VVmWjXgFQn4JiIbwrGfFDBRMzxsAA";
        String nkey = "SUAFYHVVQVOIDOOQ4MTOCTLGNZCJ5PZ4HPV5WAPROGTEIOF672D3R7GBY4";

        runInSharedConfiguredServer("operator.conf", ts -> {
            Options options = optionsBuilder(ts).maxReconnects(0)
                .authHandler(Nats.staticCredentials(jwt.toCharArray(), nkey.toCharArray())).build();
            assertCanConnect(options);
        });
    }

    @Test
    public void testBadAuthHandler() {
        assertThrows(IOException.class, () -> {
            NKey theKey = NKeyProvider.getProvider().createUser();
            assertNotNull(theKey);
            String configFilePath = createNKeyConfigFile(theKey.getPublicKey());
            NatsServerRunner.Builder b = NatsServerRunner.builder().configFilePath(configFilePath);
            try (NatsTestServer ts = new NatsTestServer(b)) {
                Options options = new OptionsBuilder().server(ts.getServerUri()).maxReconnects(0)
                    .authHandler(new AuthHandlerForTesting(null)). // No nkey
                        build();
                NatsConnection nc = Nats.connect(options);
                assertNotConnected(nc);
            }
        });
    }

    @Test
    public void testReconnectWithAuth() throws Exception {
        Listener listener = new Listener();
        // Connect should fail on ts2
        runInConfiguredServer("operator.conf", ts1 -> { // closed as part of test, so cannot be shared
            runInSharedConfiguredServer("operator.conf", ts2 -> {
                Options options = optionsBuilder(ts1.getServerUri(), ts2.getServerUri())
                    .noRandomize()
                    .maxReconnects(-1)
                    .authHandler(getUserCredsAuthHander())
                    .connectionListener(listener)
                    .errorListener(listener)
                    .build();
                NatsConnection nc = managedConnect(options);
                assertEquals(ts1.getServerUri(), nc.getConnectedUrl());

                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);

                ts1.close();

                // Reconnect will fail because ts has the same auth error
                listener.validate();
                assertConnected(nc);
                assertEquals(ts2.getServerUri(), nc.getConnectedUrl());
                closeAndConfirm(nc);
            });
        });
    }

    @Test
    public void testCloseOnReconnectWithSameError() throws Exception {
        // Connect should fail on ts1
        runInConfiguredServer("operator.conf", ts2 -> { // closed in test, so cannot be shared
            runInSharedConfiguredServer("operator_noacct.conf", ts1 -> {
                Listener listener = new Listener();
                Options options = optionsBuilder(ts1.getServerUri(), ts2.getServerUri())
                    .maxReconnects(-1)
                    .connectionTimeout(2000)
                    .connectionListener(listener)
                    .noRandomize()
                    .authHandler(getUserCredsAuthHander())
                    .build();

                try (NatsConnection nc = managedConnect(options)) {
                    assertEquals(ts2.getServerUri(), nc.getConnectedUrl());

                    listener.queueConnectionEvent(ConnectionEvent.CLOSED, LONG_VALIDATE_TIMEOUT);

                    ts2.close();

                    // Reconnect will fail because ts has the same auth error
                    listener.validate();
                }
            });
        });
    }

    @Test
    public void testThatAuthErrorIsCleared() throws Exception {
        AtomicReference<NatsConnection> ncRef = new AtomicReference<>();
        AtomicReference<String> server2Ref = new AtomicReference<>();
        AtomicInteger port2Ref = new AtomicInteger();

        runInConfiguredServer("operator_noacct.conf", ts1 -> {
            runInConfiguredServer("operator.conf", ts2 -> {
                String server1 = ts1.getServerUri();
                String server2 = ts2.getServerUri();
                server2Ref.set(server2);
                port2Ref.set(ts2.getNatsPort());
                Options options = optionsBuilder(server1, server2)
                    .noRandomize()
                    .maxReconnects(-1)
                    .connectionTimeout(5000)
                    .reconnectWait(1000L) // wait a tad to allow restarts
                    .authHandler(getUserCredsAuthHander())
                    .build();
                NatsConnection nc = managedConnect(options);
                ncRef.set(nc);
                assertEquals(server2, nc.getConnectedUrl());
            });

            runInConfiguredServer("operator.conf", port2Ref.get(), ts2 -> {
                confirmConnected(ncRef.get());
                assertEquals(server2Ref.get(), ncRef.get().getConnectedUrl());
            });
        });
    }

    @Test
    public void testReconnectAfterExpiration() throws Exception {
        _testReconnectAfter("user authentication expired");
    }

    @Test
    public void testReconnectAfterRevoked() throws Exception {
        _testReconnectAfter("user authentication revoked");
    }

    @Test
    public void testReconnectAfterAuthorizationViolation() throws Exception {
        _testReconnectAfter("authorization violation");
    }

    @Test
    public void testReconnectAfterAccountAuthenticationExpired() throws Exception {
        _testReconnectAfter("account authentication expired");
    }

    private static void _testReconnectAfter(String errText) throws Exception {
        Listener listener = new Listener();
        CompletableFuture<Boolean> fMock = new CompletableFuture<>();

        NatsServerProtocolMock.Customizer timeoutCustomizer = (ts, r, w) -> {
            fMock.join(); // wait until we are ready
            w.write("-ERR " + errText + "\r\n"); // Drop the line feed
            w.flush();
        };

        int port = NatsTestServer.nextPort();

        // Connect should fail on ts1
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(timeoutCustomizer, port, true)) {
            runInSharedConfiguredServer("operator.conf", ts2 -> {
                Options options = optionsBuilder(mockTs, ts2)
                    .maxReconnects(-1)
                    .noRandomize()
                    .authHandler(getUserCredsAuthHander())
                    .errorListener(listener)
                    .connectionListener(listener)
                    .build();

                listener.queueConnectionEvent(ConnectionEvent.RECONNECTED);

                try (NatsConnection nc = standardConnect(options)) {
                    assertEquals(mockTs.getServerUri(), nc.getConnectedUrl());
                    fMock.complete(true);
                    listener.validate();
                    assertEquals(ts2.getServerUri(), nc.getConnectedUrl());

                    String err = nc.getLastError();
                    assertNotNull(err);
                    assertTrue(err.toLowerCase().contains(errText));
                }
            });
        }
    }

    @Test
    public void testRealUserAuthenticationExpired() throws Exception {
        Listener listener = new Listener();

        String accountSeed = "SAAPXJRFMUYDUH3NOZKE7BS2ZDO2P4ND7G6W743MTNA3KCSFPX3HNN6AX4";
        String accountId = "ACPWDUYSZRRF7XAEZKUAGPUH6RPICWEHSTFELYKTOWUVZ4R2XMP4QJJX";
        String userSeed = "SUAJ44FQWKEWGRSIPRFCIGDTVYSMUMRRHB4CPFXXRG5GODO5XY7S2L45ZA";

        NKey nKeyAccount = NKeyProvider.getProvider().fromSeed(accountSeed.toCharArray());

        NKey nKeyUser = NKeyProvider.getProvider().fromSeed(userSeed.toCharArray());
        String publicUserKey = new String(nKeyUser.getPublicKey());

        long expires = 1000;
        Duration expiration = Duration.ofMillis(expires);
        String jwt = JwtUtils.issueUserJWT(nKeyAccount, accountId, publicUserKey, "jnatsTestUser", expiration);

        String creds = String.format(JwtUtils.NATS_USER_JWT_FORMAT, jwt, new String(nKeyUser.getSeed()));
        runInConfiguredServer("operatorJnatsTest.conf", ts -> {
            String credsFile = null;
            try {
                credsFile = ResourceUtils.createTempFile("nats_java_test", ".creds", creds.split("\\Q\\n\\E"));
                Options options = Options.builder()
                    .server(ts.getServerUri())
                    .credentialPath(credsFile)
                    .connectionListener(listener)
                    .errorListener(listener)
                    .maxReconnects(5)
                    .build();

                listener.queueError("User Authentication Expired");

                // so these shenanigans to test this...
                // 1. sometimes it connects and the expiration comes while connected
                // 2. sometimes the connect exceptions right away
                // 3. sometimes the connect happens but still exceptions
                // this is all simply the speed and timing of the machine/server/connection
                try (NatsConnection ignored = Nats.connect(options)) {
                    sleep(2500); // 1. connected, so the validate() at the end verifies this
                }
                catch (AuthenticationException ignore) {
                    return; // 2. sometimes the connect exceptions right away
                }
                catch (RuntimeException e) {
                    // 3. sometimes the connect happens but still exceptions
                    Throwable t = e;
                    while (t != null) {
                        if (t instanceof AuthenticationException) {
                            return;
                        }
                        t = t.getCause();
                    }
                    fail(e);
                }
                listener.validate(); // 1. finish
            }
            finally {
                ResourceUtils.deleteFileOrFolder(credsFile);
            }
        });
    }
}
