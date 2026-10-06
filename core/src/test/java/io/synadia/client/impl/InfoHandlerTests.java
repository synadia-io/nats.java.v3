package io.synadia.client.impl;

import io.synadia.client.ConnectionEvent;
import io.synadia.client.ConnectionListener;
import io.synadia.client.NatsServerProtocolMock;
import io.synadia.client.Options;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.ConnectionUtils.standardConnect;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InfoHandlerTests {
    @Test
    public void testInitialInfo() throws IOException, InterruptedException {
        String customInfo = "{\"server_id\":\"myid\", \"version\":\"9.9.99\"}";
        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(null, customInfo)) {
            try (NatsConnection nc = standardConnect(mockTs)) {
                assertEquals("myid", nc.getServerInfo().getServerId(), "got custom info");
            }
        }
    }

    @Test
    public void testUnsolicitedInfo() throws IOException, InterruptedException, ExecutionException {
        String customInfo = "{\"server_id\":\"myid\", \"version\":\"9.9.99\"}";
        CompletableFuture<Boolean> gotPong = new CompletableFuture<>();
        CompletableFuture<Boolean> sendInfo = new CompletableFuture<>();

        NatsServerProtocolMock.Customizer infoCustomizer = (ts, r, w) -> {

            // Wait for client to be ready.
            try {
                sendInfo.get();
            } catch (Exception e) {
                // return, we will fail the test
                gotPong.cancel(true);
                return;
            }

            // System.out.println("*** Mock Server @" + ts.getPort() + " sending INFO ...");
            w.write("INFO {\"server_id\":\"replacement\", \"version\":\"9.9.99\"}\r\n");
            w.flush();

            // System.out.println("*** Mock Server @" + ts.getPort() + " sending PING ...");
            w.write("PING\r\n");
            w.flush();

            // System.out.println("*** Mock Server @" + ts.getPort() + " waiting for PONG ...");
            String pong;
            try {
                pong = r.readLine();
            } catch (Exception e) {
                gotPong.cancel(true);
                return;
            }

            if (pong != null && pong.startsWith("PONG")) {
                // System.out.println("*** Mock Server @" + ts.getPort() + " got PONG ...");
                gotPong.complete(Boolean.TRUE);
            } else {
                // System.out.println("*** Mock Server @" + ts.getPort() + " got something else... " + pong);
                gotPong.complete(Boolean.FALSE);
            }
        };

        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(infoCustomizer, customInfo)) {
            try (NatsConnection nc = standardConnect(mockTs)) {
                assertEquals("myid", nc.getServerInfo().getServerId(), "got custom info");
                sendInfo.complete(Boolean.TRUE);

                assertTrue(gotPong.get(), "Got pong."); // Server round tripped so we should have new info
                assertEquals("replacement", nc.getServerInfo().getServerId(), "got replacement info");
            }
        }
    }

    @Test
    public void testLDM() throws IOException, InterruptedException, ExecutionException, TimeoutException {
        String customInfo = "{\"server_id\":\"myid\", \"version\":\"9.9.99\", \"ldm\":true}";
        CompletableFuture<Boolean> gotPong = new CompletableFuture<>();
        CompletableFuture<Boolean> sendInfo = new CompletableFuture<>();
        CompletableFuture<ConnectionEvent> connectLDM = new CompletableFuture<>();

        NatsServerProtocolMock.Customizer infoCustomizer = (ts, r, w) -> {
            // Wait for client to be ready.
            try {
                sendInfo.get();
            } catch (Exception e) {
                // return, we will fail the test
                gotPong.cancel(true);
                return;
            }

            // System.out.println("*** Mock Server @" + ts.getPort() + " sending INFO ...");
            w.write("INFO {\"server_id\":\"replacement\"}\r\n");
            w.flush();

            // System.out.println("*** Mock Server @" + ts.getPort() + " sending PING ...");
            w.write("PING\r\n");
            w.flush();

            // System.out.println("*** Mock Server @" + ts.getPort() + " waiting for PONG ...");
            String pong;
            try {
                pong = r.readLine();
            } catch (Exception e) {
                gotPong.cancel(true);
                return;
            }

            if (pong != null && pong.startsWith("PONG")) {
                // System.out.println("*** Mock Server @" + ts.getPort() + " got PONG ...");
                gotPong.complete(Boolean.TRUE);
            } else {
                // System.out.println("*** Mock Server @" + ts.getPort() + " got something else... " + pong);
                gotPong.complete(Boolean.FALSE);
            }
        };

        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(infoCustomizer, customInfo)) {

            ConnectionListener cl = (conn, event, time, details) -> {
                if (event.equals(ConnectionEvent.LAME_DUCK)) connectLDM.complete(event);
            };

            Options options = optionsBuilder(mockTs).connectionListener(cl).build();

            try (NatsConnection nc = standardConnect(options)) {
                assertEquals("myid", nc.getServerInfo().getServerId(), "got custom info");
                sendInfo.complete(Boolean.TRUE);

                assertTrue(gotPong.get(), "Got pong."); // Server round tripped so we should have new info
                assertEquals("replacement", nc.getServerInfo().getServerId(), "got replacement info");
            }
        }

        ConnectionEvent event = connectLDM.get(5, TimeUnit.SECONDS);
        assertEquals(ConnectionEvent.LAME_DUCK, event);
        // System.out.println(event);
    }
}
