package io.synadia.client.impl;

import io.synadia.client.ForceReconnectOptions;
import io.synadia.client.HostnameResolveMode;
import io.synadia.client.Options;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.HappyEyeballsConnector;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.ScheduledTask;
import io.synadia.client.utils.WebSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;
import static io.synadia.client.utils.NatsConstants.SECURE_WEBSOCKET_PROTOCOL;

/**
 * This class is not thread-safe.  Caller must ensure thread safety.
 */
public class SocketDataPort implements DataPort {

    protected NatsConnection connection;

    protected String host;
    protected int port;
    protected Socket socket;
    protected boolean isSecure = false;

    protected InputStream in;
    protected OutputStream out;

    // Write-timeout watch, gated on socketWriteTimeout > 0;
    // - when it is <= 0, there is no watch.
    private long writeTimeoutNanos;
    private long delayPeriodNanos;
    private ScheduledTask writeWatchTask;
    private final AtomicLong writeMustBeDoneBy = new AtomicLong(Long.MAX_VALUE);

    /**
     * Construct an unconnected data port. Data ports are instantiated by the connection,
     * then configured through afterConstruct and opened through connect.
     */
    public SocketDataPort() {}

    @Override
    public void afterConstruct(@NonNull Options options) {
        long millis = options.getSocketWriteTimeout();
        if (millis > 0) {
            writeTimeoutNanos = millis * NANOS_PER_MILLI;
            delayPeriodNanos = writeTimeoutNanos * 51 / 100;
        }
        // millis <= 0 -> writeTimeoutNanos stays 0 -> no write-timeout watch
    }

    @Override
    public void connect(@NonNull NatsConnection conn, @NonNull NatsUri nuri, long timeoutNanos) throws IOException {
        connection = conn;
        Options options = connection.getOptions();
        long timeout = timeoutNanos / 1_000_000; // convert to millis
        host = nuri.getHost();
        port = nuri.getPort();

        try {
            HostnameResolveMode mode = options.hostnameResolveMode();
            if (mode == HostnameResolveMode.HappyEyeballs) {
                socket = HappyEyeballsConnector.connect(
                    options.getExecutor(),
                    () -> createSocket(options),
                    host, port, (int) timeout
                );
            }
            else {
                socket = createSocket(options);
                InetSocketAddress inetSocketAddress;
                if (mode == HostnameResolveMode.Unresolved && !nuri.hostIsIpAddress()) {
                    inetSocketAddress = InetSocketAddress.createUnresolved(host, port);
                }
                else {
                    inetSocketAddress = new InetSocketAddress(host, port);
                }
                socket.connect(inetSocketAddress, (int) timeout);
            }

            if (options.getSocketReadTimeout() > 0) {
                socket.setSoTimeout((int) options.getSocketReadTimeout()); // SO_TIMEOUT is millis and int-typed; a read timeout never exceeds int range
            }

            if (options.getSocketSoLinger() > 0) {
                socket.setSoLinger(true, options.getSocketSoLinger());
            }

            if (options.getSocketReceiveBufferSize() > 0) {
                socket.setReceiveBufferSize(options.getSocketReceiveBufferSize());
            }

            if (options.getSocketSendBufferSize() > 0) {
                socket.setSendBufferSize(options.getSocketSendBufferSize());
            }

            if (nuri.isWebsocket()) {
                if (SECURE_WEBSOCKET_PROTOCOL.equalsIgnoreCase(nuri.getScheme())) {
                    upgradeToSecure();
                }
                try {
                    socket = new WebSocket(socket, host, options.getHttpRequestInterceptors(), nuri.getUri().getPath());
                } catch (Exception ex) {
                    socket.close();
                    throw ex;
                }
            }
            in = socket.getInputStream();
            out = socket.getOutputStream();
        }
        catch (Exception e) {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignore) {}
            }
            socket = null;
            if (e instanceof IOException) {
                throw e;
            }
            throw new IOException(e);
        }

        if (writeTimeoutNanos > 0) {
            writeWatchTask = new ScheduledTask(connection.getScheduledExecutor(), delayPeriodNanos, TimeUnit.NANOSECONDS,
                () -> {
                    // if now is after when the write was supposed to be done by, the socket write is stuck
                    if (NatsSystemClock.nanoTime() > writeMustBeDoneBy.get()) {
                        writeWatchTask.shutdown(); // connection is going to be closed; no need to repeat this
                        connection.notifyErrorListener((c, el) -> el.socketWriteTimeout(c));
                        try {
                            connection.forceReconnect(ForceReconnectOptions.FORCE_CLOSE_INSTANCE);
                        }
                        catch (IOException e) {
                            // retry maybe?
                        }
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            // This task is going to re-run anyway, so no point in throwing
                        }
                    }
                });
        }
    }

    /**
     * Upgrade the port to SSL. If it is already secured, this is a no-op.
     * If the data port type doesn't support SSL it should throw an exception.
     */
    public void upgradeToSecure() throws IOException {
        Options options = connection.getOptions();
        SSLContext context = options.getSslContext();

        SSLSocketFactory factory = context.getSocketFactory();

        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        sslSocket.setUseClientMode(true);

        final CompletableFuture<Void> waitForHandshake = new CompletableFuture<>();
        final HandshakeCompletedListener hcl = (evt) -> waitForHandshake.complete(null);

        sslSocket.addHandshakeCompletedListener(hcl);
        sslSocket.startHandshake();

        try {
            waitForHandshake.get(options.getConnectionTimeout(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            connection.handleCommunicationIssue(ex);
            return;
        }
        finally {
            sslSocket.removeHandshakeCompletedListener(hcl);
        }

        socket = sslSocket;
        in = sslSocket.getInputStream();
        out = sslSocket.getOutputStream();
        isSecure = true;
    }

    public int read(byte[] dst, int off, int len) throws IOException {
        return in.read(dst, off, len);
    }

    public void write(byte[] src, int toWrite) throws IOException {
        if (writeTimeoutNanos > 0) {
            writeMustBeDoneBy.set(NatsSystemClock.nanoTime() + writeTimeoutNanos);
            out.write(src, 0, toWrite);
            writeMustBeDoneBy.set(Long.MAX_VALUE);
        }
        else {
            out.write(src, 0, toWrite);
        }
    }

    public void shutdownInput() throws IOException {
        // cannot call shutdownInput on sslSocket
        if (!isSecure && socket != null) {
            socket.shutdownInput();
        }
    }

    public void close() throws IOException {
        if (writeWatchTask != null) {
            writeWatchTask.shutdown();
        }
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public void forceClose() throws IOException {
        // socket can technically be null, like between states
        // practically it never will be, but guard it anyway
        if (socket != null) {
            try {
                // If we are being asked to force close, there is no need to linger.
                socket.setSoLinger(true, 0);
            }
            catch (SocketException e) {
                // don't want to fail if I couldn't set linger
            }
            close();
        }
    }

    public void flush() throws IOException {
        out.flush();
    }

    private Socket createSocket(Options options) throws SocketException {
        Socket socket;
        if (options.getProxy() != null) {
            socket = new Socket(options.getProxy());
        } else {
            socket = new Socket();
        }
        socket.setTcpNoDelay(true);
        socket.setReceiveBufferSize(2 * 1024 * 1024);
        socket.setSendBufferSize(2 * 1024 * 1024);
        return socket;
    }
}
