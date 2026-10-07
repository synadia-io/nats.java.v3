// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.utils.NatsUri;
import org.jspecify.annotations.NonNull;

import java.io.IOException;

/**
 * A {@link DataPort} that serves some already-read bytes before reading from the real port.
 * {@link NatsConnectionV3} reads the connect handshake itself; anything the server sent after the
 * handshake's PONG in the same read is handed to the reader through this.
 */
final class PrefixedDataPort implements DataPort {
    private final DataPort port;
    private byte[] prefix;
    private int prefixPos;

    PrefixedDataPort(DataPort port, byte[] prefix) {
        this.port = port;
        this.prefix = prefix;
    }

    @Override
    public void connect(@NonNull NatsConnection conn, @NonNull NatsUri uri, long timeoutNanos) throws IOException {
        port.connect(conn, uri, timeoutNanos);
    }

    @Override
    public void afterConstruct(@NonNull Options options) {
        port.afterConstruct(options);
    }

    @Override
    public void upgradeToSecure() throws IOException {
        port.upgradeToSecure();
    }

    @Override
    public int read(byte[] dst, int off, int len) throws IOException {
        byte[] p = prefix;
        if (p != null) {
            int available = p.length - prefixPos;
            int n = available < len ? available : len;
            System.arraycopy(p, prefixPos, dst, off, n);
            prefixPos += n;
            if (prefixPos == p.length) {
                prefix = null;
            }
            return n;
        }
        return port.read(dst, off, len);
    }

    @Override
    public void write(byte[] src, int toWrite) throws IOException {
        port.write(src, toWrite);
    }

    @Override
    public void shutdownInput() throws IOException {
        port.shutdownInput();
    }

    @Override
    public void close() throws IOException {
        port.close();
    }

    @Override
    public void forceClose() throws IOException {
        port.forceClose();
    }

    @Override
    public void flush() throws IOException {
        port.flush();
    }
}
