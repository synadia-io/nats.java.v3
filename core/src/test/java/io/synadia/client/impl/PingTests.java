package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.NatsServerProtocolMock.ExitAt;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class PingTests extends TestBase {
    @Test
    public void testHandlingPing() throws Exception {
        CompletableFuture<Boolean> gotPong = new CompletableFuture<>();

        NatsServerProtocolMock.Customizer pingPongCustomizer = (ts, r, w) -> {
//            System.out.println("*** Mock Server @" + ts.getPort() + " sending PING ...");
            w.write("PING\r\n");
            w.flush();

            String pong;
//            System.out.println("*** Mock Server @" + ts.getPort() + " waiting for PONG ...");
            try {
                pong = r.readLine();
            } catch(Exception e) {
                gotPong.cancel(true);
                return;
            }

            if (pong.startsWith("PONG")) {
//                System.out.println("*** Mock Server @" + ts.getPort() + " got PONG ...");
                gotPong.complete(Boolean.TRUE);
            } else {
//                System.out.println("*** Mock Server @" + ts.getPort() + " got something else... " + pong);
                gotPong.complete(Boolean.FALSE);
            }
        };

        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(pingPongCustomizer)) {
            try (NatsConnection nc = standardConnect(mockTs)) {
                assertTrue(gotPong.get(), "Got pong.");
            }
        }
    }

    @Test
    public void testPingTimer() throws Exception {
        OptionsBuilder builder = optionsBuilder()
            .pingInterval(5)
            .maxPingsOut(10000); // just don't want this to be what fails the test
        runInSharedOwnNc(builder, nc -> {
            Statistics stats = nc.getStatistics();
            sleep(200); // 1200 / 100 ... should get 10+ pings
            assertTrue(stats.getPings() > 1, "got pings");
        });
    }

    @Test
    public void testPingFailsWhenClosed() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            Options options = optionsBuilder(mockTs).maxReconnects(0).build();
            NatsConnection nc = standardConnect(options);
            nc.close();
            CompletableFuture<Boolean> pong = nc.sendPing();
            assertNotNull(pong);
            assertFalse(pong.get(10, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testMaxPingsOut() throws Exception {
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            Options options = optionsBuilder(mockTs)
                .pingInterval(10000) // Avoid auto pings
                .maxPingsOut(2)
                .maxReconnects(0)
                .build();
            try (NatsConnection nc = standardConnect(options)) {
                nc.sendPing();
                nc.sendPing();
                assertNull(nc.sendPing(), "No future returned when past max");
            }
        }
    }

    @Test
    public void testFlushTimeout() throws Exception {
        Listener listener = new Listener();
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(ExitAt.NO_EXIT)) {
            Options options = optionsBuilder(mockTs)
                .maxReconnects(0)
                .connectionListener(listener)
                .errorListener(listener)
                .build();
            try (NatsConnection nc = standardConnect(options)) {
                // fake server so flush will time out
                assertThrows(TimeoutException.class, () -> nc.flush(50));
            }
        }
    }

    @Test
    public void testFlushTimeoutDisconnected() throws Exception {
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts).connectionListener(listener).build();
            try (NatsConnection nc = managedConnect(options)) {
                nc.flush(2000);
                listener.queueConnectionEvent(ConnectionEvent.DISCONNECTED);
                ts.close();
                listener.validate();
                assertThrows(TimeoutException.class, () -> nc.flush(2000));
            }
        }
    }

    @Test
    public void testPingTimerThroughReconnect() throws Exception {
        try (NatsTestServer ts = new NatsTestServer()) {
            try (NatsTestServer ts2 = new NatsTestServer()) {
                Options options = optionsBuilder(ts.getServerUri(), ts2.getServerUri())
                    .pingInterval(500)
                    .maxPingsOut(100) // just don't want this to be what fails the test
                    .build();
                try (NatsConnection nc = managedConnect(options)) {
                    Statistics stats = nc.getStatistics();
                    sleep(1000);
                    long pings = stats.getPings();
                    assertTrue(pings > 1, "got pings");
                    ts.close();
                    confirmConnected(nc);
                    sleep(1000); // should get more pings
                    assertTrue(stats.getPings() > pings, "more pings");
                }
            }
        }
    }

    @Test
    public void testMessagesDelayPings() throws Exception {
        OptionsBuilder builder = optionsBuilder().pingInterval(200);
        runInSharedOwnNc(builder, nc -> {
            String subject = random();
            String doneSubject = random();
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(doneSubject)) {
                    done.complete(Boolean.TRUE);
                }
            });
            d.subscribe(subject);
            d.subscribe(doneSubject);
            nc.flush(1000); // wait for the subscriptions to go through

            Statistics stats = nc.getStatistics();
            long before = stats.getPings();
            for (int i = 0; i < 10; i++) {
                sleep(50);
                nc.publish(subject, new byte[16]);
            }
            assertEquals(before, stats.getPings(), "pings hidden");
            nc.publish(doneSubject, new byte[16]);
            nc.flush(1000); // wait for the messages to go through
            done.get(500, TimeUnit.MILLISECONDS);

            // no more messages, pings should start to go through
            before = stats.getPings();
            sleep(500);
            assertTrue(stats.getPings() > before, "pings restarted");
        });
    }
}
