// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.synadia.client.utils.NatsConstants.*;

/**
 * The transport a connection uses to every server. It is resolved once, when the options are built,
 * from {@link OptionsBuilder#secure() secure()}, {@link OptionsBuilder#ws() ws()}, any TLS setting
 * and the schemes of the bootstrap servers.
 */
@NullMarked
public enum Transport {
    /**
     * Plain NATS over TCP, scheme {@code nats}, default port 4222.
     */
    Nats(NATS_PROTOCOL, false, false, DEFAULT_PORT),

    /**
     * NATS over TCP upgraded to TLS, scheme {@code tls}, default port 4222.
     */
    Tls(TLS_PROTOCOL, true, false, DEFAULT_PORT),

    /**
     * NATS over a plain websocket, scheme {@code ws}, default port 80.
     */
    Ws(WEBSOCKET_PROTOCOL, false, true, 80),

    /**
     * NATS over a websocket over TLS, scheme {@code wss}, default port 443.
     */
    Wss(SECURE_WEBSOCKET_PROTOCOL, true, true, 443);

    private final String scheme;
    private final boolean secure;
    private final boolean websocket;
    private final int defaultPort;

    Transport(String scheme, boolean secure, boolean websocket, int defaultPort) {
        this.scheme = scheme;
        this.secure = secure;
        this.websocket = websocket;
        this.defaultPort = defaultPort;
    }

    /**
     * The url scheme of this transport.
     * @return the scheme
     */
    public String scheme() {
        return scheme;
    }

    /**
     * Whether this transport uses TLS.
     * @return true for {@link #Tls} and {@link #Wss}
     */
    public boolean isSecure() {
        return secure;
    }

    /**
     * Whether this transport is a websocket.
     * @return true for {@link #Ws} and {@link #Wss}
     */
    public boolean isWebsocket() {
        return websocket;
    }

    /**
     * The port used for a server given without one.
     * @return the default port
     */
    public int defaultPort() {
        return defaultPort;
    }

    /**
     * Get the transport for a secure flag and a websocket flag.
     * @param secure whether the transport uses TLS
     * @param websocket whether the transport is a websocket
     * @return the transport
     */
    public static Transport of(boolean secure, boolean websocket) {
        if (websocket) {
            return secure ? Wss : Ws;
        }
        return secure ? Tls : Nats;
    }

    /**
     * Find the transport for a url scheme, ignoring case.
     * @param scheme the scheme
     * @return the transport, or null if the scheme is not one of {@code nats}, {@code tls}, {@code ws}, {@code wss}
     */
    public static @Nullable Transport forScheme(String scheme) {
        for (Transport t : values()) {
            if (t.scheme.equalsIgnoreCase(scheme)) {
                return t;
            }
        }
        return null;
    }
}
