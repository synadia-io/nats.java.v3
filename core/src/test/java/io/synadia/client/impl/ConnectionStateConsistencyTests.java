package io.synadia.client.impl;

import io.synadia.client.ConnectionStatus;
import io.synadia.client.NatsTestServer;
import io.synadia.client.Options;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.ConnectionUtils.closeAndConfirm;
import static io.synadia.client.utils.ConnectionUtils.standardConnect;
import static io.synadia.client.utils.OptionsUtils.NOOP_EL;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The status and the current server are two separate fields and are not published together, so
 * the order they are updated in decides whether an application sampling the connection can ever
 * see a combination that was never true.
 * <p>
 * closeSocket used to tear the socket down first and update the status afterwards. Since
 * closeSocketImpl clears the current server as its first act and then waits on the reader and
 * writer stop futures, that left a window - bounded by those stop timeouts, so up to seconds
 * under a slow reader - in which getStatus() returned CONNECTED while getConnectedUrl() returned
 * null. Any health check, metric or log line that sampled the connection during a communication
 * failure teardown could see it, with no callback delay involved.
 */
@Isolated
public class ConnectionStateConsistencyTests extends TestBase {

    @Test
    public void testStatusAndConnectedUrlAreNeverInconsistentDuringTeardown() throws Exception {
        int port = NatsTestServer.nextPort();
        NatsTestServer ts = new NatsTestServer(port);
        NatsConnection nc = null;
        AtomicBoolean run = new AtomicBoolean(true);
        Thread poller = null;

        AtomicLong samples = new AtomicLong();
        AtomicLong inconsistent = new AtomicLong();
        AtomicReference<String> firstExample = new AtomicReference<>();

        try {
            Options options = optionsBuilder(ts)
                .maxReconnects(-1)
                .reconnectWait(50)
                .connectionTimeout(500)
                .errorListener(NOOP_EL)
                .build();

            nc = standardConnect(options);
            assertNotNull(nc.getConnectedUrl());

            final NatsConnection conn = nc;
            poller = new Thread(() -> {
                while (run.get()) {
                    samples.incrementAndGet();
                    ConnectionStatus status = conn.getStatus();
                    String url = conn.getConnectedUrl();
                    if (status == ConnectionStatus.CONNECTED && url == null) {
                        inconsistent.incrementAndGet();
                        firstExample.compareAndSet(null, "status=" + status + " getConnectedUrl()=null");
                    }
                }
            }, "state-pair-poller");
            poller.setDaemon(true);
            poller.start();

            // Take the server away. The reader raises a communication issue, which is the path
            // that reaches closeSocket. forceReconnect would exercise forceReconnectImpl instead,
            // which already updated the status before tearing down.
            ts.close();

            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end && nc.getStatus() == ConnectionStatus.CONNECTED) {
                sleep(5);
            }
            sleep(1000);

            run.set(false);
            poller.join(5000);

            assertTrue(samples.get() > 1_000_000,
                "poller took " + samples.get() + " samples, too few to conclude anything");
            assertEquals(0, inconsistent.get(),
                "observed CONNECTED with a null connected url " + inconsistent.get()
                    + " times in " + samples.get() + " samples, first: " + firstExample.get());
        }
        finally {
            run.set(false);
            if (poller != null) {
                poller.join(5000);
            }
            ts.close();
            closeAndConfirm(nc);
        }
    }
}
