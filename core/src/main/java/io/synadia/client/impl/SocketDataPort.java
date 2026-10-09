package io.synadia.client.impl;

import io.synadia.client.ForceReconnectOptions;
import io.synadia.client.HostnameResolveMode;
import io.synadia.client.Options;
import io.synadia.client.global.NatsInetAddress;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.HappyEyeballsConnector;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.ScheduledTask;
import io.synadia.client.utils.WebSocket;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
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
    protected String tlsHost;
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
    public void connect(@NonNull NatsConnection conn, @NonNull NatsUri nuri, @NonNull NatsUri unresolvedUri, long timeoutNanos) throws IOException {
        // The unresolved uri is the server as configured or discovered, before any hostname resolution.
        // Its tls host is its hostname, or the hostname saved on it when it was discovered as a bare
        // ip address from a server that had one. That is the server name to present during the TLS
        // handshake, whatever the hostname resolution mode turned the host of nuri into.
        String name = unresolvedUri.getTlsHost();
        if (!unresolvedUri.hostIsIpAddress() || !name.equals(unresolvedUri.getHost())) {
            tlsHost = name;
        }
        connect(conn, nuri, timeoutNanos);
    }

    @Override
    public void connect(@NonNull NatsConnection conn, @NonNull NatsUri nuri, long timeoutNanos) throws IOException {
        connection = conn;
        Options options = connection.getOptions();
        long timeout = timeoutNanos / 1_000_000; // convert to millis
        host = nuri.getHost();
        port = nuri.getPort();
        if (tlsHost == null && !nuri.hostIsIpAddress()) {
            // connect was called without the unresolved uri, or the host was not resolved.
            tlsHost = host;
        }

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
                    if (options.getProxy() == null) {
                        // There is no proxy to hand the hostname to, and a plain socket rejects an unresolved
                        // address. Resolve one address here, at connect time, through NatsInetAddress, the way
                        // InetSocketAddress(String, int) would have. host and tlsHost keep the hostname.
                        inetSocketAddress = new InetSocketAddress(NatsInetAddress.getByName(host), port);
                    }
                    else {
                        // The proxy receives the hostname and resolves it.
                        inetSocketAddress = InetSocketAddress.createUnresolved(host, port);
                    }
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

        String peerHost = tlsHost == null ? host : tlsHost;
        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, peerHost, port, true);
        sslSocket.setUseClientMode(true);

        boolean verifyHostname = options.isTlsVerifyHostname();
        if (tlsHost != null || verifyHostname) {
            SSLParameters sslParameters = sslSocket.getSSLParameters();
            if (tlsHost != null) {
                // Present the hostname to the server as the TLS server name (SNI) in every hostname resolution mode.
                // Setting it explicitly also covers names the provider would not derive from the peer host on its own,
                // for instance a single label hostname like a kubernetes service name.
                try {
                    sslParameters.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName(tlsHost)));
                }
                catch (IllegalArgumentException e) {
                    // not a legal SNI host name, for instance it has a trailing dot.
                    // Leave the server name to the provider's default behavior for the peer host.
                }
            }
            if (verifyHostname) {
                // The trust manager checks the certificate against the server name, or the peer host when there is none.
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            }
            sslSocket.setSSLParameters(sslParameters);
        }

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
