package io.synadia.client.impl;

import io.synadia.client.HostnameResolveMode;
import io.synadia.client.Options;
import io.synadia.client.utils.NatsUri;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

/**
 * A data port represents the connection to the network. This could have been called
 * transport but that seemed too big a concept. This interface just allows a wrapper around
 * the core communication code.
 */
public interface DataPort {
    /**
     * Execute the connect
     * @param conn the NatsConnection object
     * @param uri the NatsUri to connect to
     * @param timeoutNanos the timeout
     * @throws IOException if the data port is unable to connect.
     */
    void connect(@NonNull NatsConnection conn, @NonNull NatsUri uri, long timeoutNanos) throws IOException;

    /**
     * Execute the connect, also providing the uri before any hostname resolution.
     * The connection resolves hostnames to ip addresses in the resolving {@link HostnameResolveMode} modes,
     * so the uri to connect to may have an ip address where the server was configured or discovered with a hostname.
     * A data port that needs the hostname, for instance to present it to the server during the TLS handshake
     * as the server name (SNI), gets it from the unresolved uri.
     * The default implementation ignores the unresolved uri and calls {@link #connect(NatsConnection, NatsUri, long)}.
     * @param conn the NatsConnection object
     * @param uri the NatsUri to connect to, possibly resolved to an ip address
     * @param unresolvedUri the NatsUri before any hostname resolution. The same object as uri when no resolution happened.
     * @param timeoutNanos the timeout
     * @throws IOException if the data port is unable to connect.
     */
    default void connect(@NonNull NatsConnection conn, @NonNull NatsUri uri, @NonNull NatsUri unresolvedUri, long timeoutNanos) throws IOException {
        connect(conn, uri, timeoutNanos);
    }

    /**
     * Called once right after the data port instance is created, before any connect attempt,
     * so the implementation can pick up whatever it needs from the options. Does nothing by default.
     * @param options the options the connection was created with
     */
    default void afterConstruct(@NonNull Options options) {}

    /**
     * Upgrade the port to SSL. If it is already secured, this is a no-op.
     * If the data port type doesn't support SSL it should throw an exception.
     *
     * @throws IOException if the data port is unable to upgrade.
     */
    void upgradeToSecure() throws IOException;

    /**
     * Read bytes from the connection into the buffer, blocking until at least one byte is available.
     * @param dst the buffer to read into
     * @param off the offset in the buffer to start writing at
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or -1 if the connection reached end of stream
     * @throws IOException any IO error on the underlying connection
     */
    int read(byte[] dst, int off, int len) throws IOException;

    /**
     * NOTE: the buffer will be modified if communicating over websockets and
     * the toWrite is greater than 1432.
     * 
     * @param src output byte[]
     * @param toWrite number of bytes to write
     * @throws IOException any IO error on the underlaying connection
     */
    void write(byte[] src, int toWrite) throws IOException;

    /**
     * Close the read side of the connection while leaving the write side open, so that pending
     * output can still be flushed during a graceful shutdown.
     * @throws IOException any IO error on the underlying connection
     */
    void shutdownInput() throws IOException;

    /**
     * Close the connection, releasing the underlying socket and any resources it holds.
     * @throws IOException any IO error on the underlying connection
     */
    void close() throws IOException;

    /**
     * Close the connection without waiting on the normal close path, used when the connection is
     * already known to be unusable. Delegates to {@link #close()} unless the implementation has a
     * faster way to abandon the socket.
     * @throws IOException any IO error on the underlying connection
     */
    default void forceClose() throws IOException {
        close();
    }

    /**
     * Push any buffered output onto the network.
     * @throws IOException any IO error on the underlying connection
     */
    void flush() throws IOException;
}
