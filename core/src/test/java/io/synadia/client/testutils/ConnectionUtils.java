package io.synadia.client.testutils;

import io.synadia.client.ConnectionStatus;
import io.synadia.client.Nats;
import io.synadia.client.NatsServerProtocolMock;
import io.synadia.client.Options;
import io.synadia.client.impl.NatsConnection;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;

import static io.synadia.client.testutils.OptionsUtils.options;
import static io.synadia.client.testutils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.assertSame;

public abstract class ConnectionUtils {

    public static final int DEFAULT_WAIT   =  5000;
    public static final int MEDIUM_WAIT    =  8000;
    public static final int LONG_WAIT      = 12000;
    public static final int VERY_LONG_WAIT = 20000;

    public static final long STANDARD_FLUSH_TIMEOUT_MS = 2000;
    public static final long MEDIUM_FLUSH_TIMEOUT_MS = 5000;

    // ----------------------------------------------------------------------------------------------------
    // connectWithRetry
    // ----------------------------------------------------------------------------------------------------
    private static final int RETRY_DELAY_INCREMENT = 50;
    private static final int CONNECTION_RETRIES = 10;
    private static final long RETRY_DELAY = 100;

    public static NatsConnection managedConnect(Options options) {
        try {
            return managedConnect(options, DEFAULT_WAIT);
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to make a connection.", e);
        }
    }

    public static NatsConnection managedConnect(Options options, long waitTime) throws IOException, InterruptedException {
        IOException last = null;
        long delay = RETRY_DELAY - RETRY_DELAY_INCREMENT;
        for (int x = 1; x <= CONNECTION_RETRIES; x++) {
            if (x > 1) {
                delay += RETRY_DELAY_INCREMENT;
                sleep(delay);
            }
            try {
                return waitUntilStatus(Nats.connect(options), waitTime, ConnectionStatus.CONNECTED);
            }
            catch (IOException ioe) {
                last = ioe;
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw ie;
            }
        }
        throw last;
    }

    // ----------------------------------------------------------------------------------------------------
    // standardConnect
    // ----------------------------------------------------------------------------------------------------
    public static NatsConnection standardConnect(NatsServerProtocolMock ts) throws IOException, InterruptedException {
        return confirmConnected(Nats.connect(options(ts)));
    }

    public static NatsConnection standardConnect(Options options) throws IOException, InterruptedException {
        return confirmConnected(Nats.connect(options));
    }

    // ----------------------------------------------------------------------------------------------------
    // connect or wait for a connection
    // ----------------------------------------------------------------------------------------------------
    @SuppressWarnings("UnusedReturnValue")
    public static NatsConnection confirmConnected(NatsConnection conn) {
        return waitUntilStatus(conn, DEFAULT_WAIT, ConnectionStatus.CONNECTED);
    }

    public static NatsConnection confirmConnected(NatsConnection conn, long waitTime) {
        return waitUntilStatus(conn, waitTime, ConnectionStatus.CONNECTED);
    }

    // ----------------------------------------------------------------------------------------------------
    // connect or wait for a connection
    // ----------------------------------------------------------------------------------------------------
    public static void confirmConnectedThenClosed(NatsConnection conn) {
        closeAndConfirm(confirmConnected(conn, DEFAULT_WAIT), DEFAULT_WAIT);
    }

    public static void confirmConnectedThenClosed(NatsConnection conn, long waitTime) {
        closeAndConfirm(confirmConnected(conn, waitTime), DEFAULT_WAIT);
    }

    public static void confirmConnectedThenClosed(NatsConnection conn, long waitTime, long closeTime) {
        closeAndConfirm(confirmConnected(conn, waitTime), closeTime);
    }

    // ----------------------------------------------------------------------------------------------------
    // close
    // ----------------------------------------------------------------------------------------------------
    public static void closeAndConfirm(NatsConnection conn) {
        closeAndConfirm(conn, DEFAULT_WAIT);
    }

    public static void closeAndConfirm(NatsConnection conn, long millis) {
        if (conn != null) {
            close(conn);
            waitUntilStatus(conn, millis, ConnectionStatus.CLOSED);
            assertClosed(conn);
        }
    }

    public static void close(NatsConnection conn) {
        try {
            conn.close();
        }
        catch (InterruptedException e) { /* ignored */ }
    }

    // ----------------------------------------------------------------------------------------------------
    // connection waiting
    // ----------------------------------------------------------------------------------------------------
    public static NatsConnection waitUntilStatus(NatsConnection conn, long millis, ConnectionStatus waitUntilStatus) {
        long times = (millis + 99) / 100;
        for (long x = 0; x < times; x++) {
            sleep(100);
            if (conn.getStatus() == waitUntilStatus) {
                return conn;
            }
        }

        throw new AssertionFailedError(expectingMessage(conn, waitUntilStatus));
    }

    // ----------------------------------------------------------------------------------------------------
    // assertions
    // ----------------------------------------------------------------------------------------------------
    public static void assertConnected(NatsConnection conn) {
        assertSame(ConnectionStatus.CONNECTED, conn.getStatus(),
            () -> expectingMessage(conn, ConnectionStatus.CONNECTED));
    }

    public static void assertClosed(NatsConnection conn) {
        assertSame(ConnectionStatus.CLOSED, conn.getStatus(),
            () -> expectingMessage(conn, ConnectionStatus.CLOSED));
    }

    public static void assertCanConnect(NatsServerProtocolMock ts) {
        closeAndConfirm(managedConnect(options(ts)));
    }

    public static void assertCanConnect(Options options) {
        closeAndConfirm(managedConnect(options));
    }

    private static String expectingMessage(NatsConnection conn, ConnectionStatus expecting) {
        return "Failed expecting NatsConnection Status " + expecting.name() + " but was " + conn.getStatus();
    }
}
