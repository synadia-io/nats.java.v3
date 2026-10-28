// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.utils;

import io.synadia.client.Transport;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * The address of one server: host, port and, for a websocket, an optional path. It carries no scheme;
 * the transport comes from the options, see {@link Transport}. Equality and the hash code use the host,
 * ignoring case, and the port.
 */
@NullMarked
public final class ServerAddress {
    private static final Pattern IPV4_RE = Pattern.compile("(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])");

    private final String host;
    private final int port;
    private final @Nullable String path;
    private final @Nullable String tlsHost;
    private final boolean hostIsIpAddress;

    /**
     * Construct an address with no path.
     * @param host the host name or ip address; an IPv6 address may be given with or without brackets
     * @param port the port, 1 to 65535
     * @throws IllegalArgumentException if the host is empty or the port is out of range
     */
    public ServerAddress(String host, int port) {
        this(host, port, null);
    }

    /**
     * Construct an address.
     * @param host the host name or ip address; an IPv6 address may be given with or without brackets
     * @param port the port, 1 to 65535
     * @param path the websocket path, starting with {@code /}, or null or empty for none
     * @throws IllegalArgumentException if the host is empty, the port is out of range or the path does not start with {@code /}
     */
    public ServerAddress(String host, int port, @Nullable String path) {
        if (host.isEmpty()) {
            throw new IllegalArgumentException("Server host cannot be empty.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535: " + port);
        }
        if (path != null && path.isEmpty()) {
            path = null;
        }
        if (path != null && !path.startsWith("/")) {
            throw new IllegalArgumentException("Server path must start with /: " + path);
        }
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]"; // IPv6 is bracketed so the address can be part of a url
        }
        this.host = host;
        this.port = port;
        this.path = path;
        this.tlsHost = null;
        hostIsIpAddress = IPV4_RE.matcher(host).matches() || host.startsWith("[") && host.endsWith("]");
    }

    private ServerAddress(ServerAddress source, @Nullable String tlsHost) {
        host = source.host;
        port = source.port;
        path = source.path;
        hostIsIpAddress = source.hostIsIpAddress;
        this.tlsHost = tlsHost;
    }

    /**
     * Parse a server given strictly as {@code host:port}, the form of a discovered server.
     * @param hostPort the text
     * @return the address
     * @throws URISyntaxException if the text is not exactly a host name or ip address and a port
     */
    public static ServerAddress parse(String hostPort) throws URISyntaxException {
        String s = hostPort.trim();
        if (s.isEmpty()) {
            throw new URISyntaxException(hostPort, "Server is empty");
        }
        URI uri = new URI("nats://" + s); // the scheme only lets URI find the authority
        if (uri.getRawUserInfo() != null) {
            throw new URISyntaxException(hostPort, "Server has user info");
        }
        if (uri.getHost() == null) {
            throw new URISyntaxException(hostPort, "Server has no valid host");
        }
        if (uri.getPort() == -1) {
            throw new URISyntaxException(hostPort, "Server has no port");
        }
        String rawPath = uri.getRawPath();
        if ((rawPath != null && !rawPath.isEmpty()) || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new URISyntaxException(hostPort, "Server is not host:port");
        }
        try {
            return new ServerAddress(uri.getHost(), uri.getPort());
        }
        catch (IllegalArgumentException e) {
            throw new URISyntaxException(hostPort, e.getMessage());
        }
    }

    /**
     * Get the host. An IPv6 address is in brackets.
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Get the port.
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the websocket path.
     * @return the path, starting with {@code /}, or null if there is none
     */
    public @Nullable String getPath() {
        return path;
    }

    /**
     * Whether the host is an IP address (IPv4 or bracketed IPv6).
     * @return true if the host is an IP address
     */
    public boolean hostIsIpAddress() {
        return hostIsIpAddress;
    }

    /**
     * The hostname to present to the server during the TLS handshake. It differs from the host
     * when this address was rehosted to an ip address by hostname resolution, or when it was discovered
     * as a bare ip address from a server that had a hostname.
     * @return the saved hostname, or the host when none has been saved
     */
    public String getTlsHost() {
        return tlsHost == null ? host : tlsHost;
    }

    /**
     * Copy this address with a hostname to present to the server during the TLS handshake.
     * This does not change the host, the port, the path, equality, the hash code or the string form.
     * @param tlsHost the hostname
     * @return a copy with the hostname
     */
    public ServerAddress withTlsHost(String tlsHost) {
        return new ServerAddress(this, tlsHost);
    }

    /**
     * Copy this address with a different host, keeping the port and the path. When the new host is an ip
     * address, the copy keeps this address's hostname to present during the TLS handshake.
     * @param newHost the new host; an IPv6 address may be given with or without brackets
     * @return the copy
     */
    public ServerAddress reHost(String newHost) {
        ServerAddress rehosted = new ServerAddress(newHost, port, path);
        if (rehosted.hostIsIpAddress && (tlsHost != null || !hostIsIpAddress)) {
            return rehosted.withTlsHost(getTlsHost());
        }
        return rehosted;
    }

    /**
     * The url for this address with a transport, {@code scheme://host:port[/path]}.
     * @param transport the transport
     * @return the url
     */
    public String toUrl(Transport transport) {
        return transport.scheme() + "://" + this;
    }

    /**
     * The address as {@code host:port[/path]}.
     * @return the text
     */
    @Override
    public String toString() {
        return path == null ? host + ":" + port : host + ":" + port + path;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerAddress other)) return false;
        return port == other.port && host.equalsIgnoreCase(other.host);
    }

    @Override
    public int hashCode() {
        return 31 * host.toLowerCase().hashCode() + port;
    }
}
