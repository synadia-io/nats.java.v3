package io.synadia.client;

/**
 * The mode of hostname resolving
 */
public enum HostnameResolveMode {
    /**
     * Resolve host to all ip addresses allowing for connection attempts to try all ip addresses for a given hostname.
     * Default mode. Does not include IPV6 addresses.
     */
    ResolveToAll(true, false, false),

    /**
     * Resolve host to the first ip addresses allowing for connection attempts to try just that first ip addresses for a given hostname.
     * Does not include IPV6 addresses.
     */
    ResolveToFirst(true, true, false),

    /**
     * Resolve host to all ip addresses allowing for connection attempts to try all ip addresses for a given hostname.
     * Includes IPV6 addresses.
     */
    ResolveToAllIncludeIPV6(true, false, true),

    /**
     * Resolve host to the first ip addresses allowing for connection attempts to try just that first ip addresses for a given hostname.
     * Includes IPV6 addresses.
     */
    ResolveToFirstIncludeIPV6(true, true, true),

    /**
     * Do not resolve, instead use InetSocketAddress.createUnresolved while creating the socket.
     */
    Unresolved(false, false, false),

    /**
     * Attempt to connect to the fastest ip for a host via the Happy Eyeballs algorithm as described in RFC 6555/8305
     */
    HappyEyeballs(false, false, false);

    public final boolean resolve;
    public final boolean maxOneResult;
    public final boolean includeIPV6;

    HostnameResolveMode(boolean resolve, boolean maxOneResult, boolean includeIPV6) {
        this.resolve = resolve;
        this.maxOneResult = maxOneResult;
        this.includeIPV6 = includeIPV6;
    }

    public static HostnameResolveMode get(String value) {
        for (HostnameResolveMode mode : HostnameResolveMode.values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
