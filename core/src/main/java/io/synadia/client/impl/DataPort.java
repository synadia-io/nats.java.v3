package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.testutils.NatsUri;
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

    default void afterConstruct(@NonNull Options options) {}

    /**
     * Upgrade the port to SSL. If it is already secured, this is a no-op.
     * If the data port type doesn't support SSL it should throw an exception.
     *
     * @throws IOException if the data port is unable to upgrade.
     */
    void upgradeToSecure() throws IOException;

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

    void shutdownInput() throws IOException;

    void close() throws IOException;

    default void forceClose() throws IOException {
        close();
    }

    void flush() throws IOException;
}
