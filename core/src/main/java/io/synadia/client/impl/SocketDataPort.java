package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.Options.HostnameResolveMode;
import io.synadia.client.support.HappyEyeballsConnector;
import io.synadia.client.support.NatsUri;
import io.synadia.client.support.WebSocket;
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
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.synadia.client.support.NatsConstants.SECURE_WEBSOCKET_PROTOCOL;

/**
 * This class is not thread-safe.  Caller must ensure thread safety.
 */
@SuppressWarnings("ClassEscapesDefinedScope") // NatsConnection
public class SocketDataPort implements DataPort {

    protected NatsConnection connection;

    protected String host;
    protected int port;
    protected Socket socket;
    protected boolean isSecure = false;

    protected InputStream in;
    protected OutputStream out;

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

            if (options.getSocketReadTimeoutMillis() > 0) {
                socket.setSoTimeout(options.getSocketReadTimeoutMillis());
            }

            if (options.getSocketSoLinger() > 0) {
                socket.setSoLinger(true, options.getSocketSoLinger());
            }

            if (options.getReceiveBufferSize() > 0) {
                socket.setReceiveBufferSize(options.getReceiveBufferSize());
            }

            if (options.getSendBufferSize() > 0) {
                socket.setSendBufferSize(options.getSendBufferSize());
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
    }

    /**
     * Upgrade the port to SSL. If it is already secured, this is a no-op.
     * If the data port type doesn't support SSL it should throw an exception.
     */
    public void upgradeToSecure() throws IOException {
        Options options = connection.getOptions();
        SSLContext context = options.getSslContext();

        SSLSocketFactory factory = context.getSocketFactory();
        Duration timeout = options.getConnectionTimeout();

        SSLSocket sslSocket = (SSLSocket) factory.createSocket(socket, host, port, true);
        sslSocket.setUseClientMode(true);

        final CompletableFuture<Void> waitForHandshake = new CompletableFuture<>();
        final HandshakeCompletedListener hcl = (evt) -> waitForHandshake.complete(null);

        sslSocket.addHandshakeCompletedListener(hcl);
        sslSocket.startHandshake();

        try {
            waitForHandshake.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
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
        out.write(src, 0, toWrite);
    }

    public void shutdownInput() throws IOException {
        // cannot call shutdownInput on sslSocket
        if (!isSecure && socket != null) {
            socket.shutdownInput();
        }
    }

    public void close() throws IOException {
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
