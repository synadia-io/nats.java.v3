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

    /** Whether the hostname is resolved to ip addresses at all, as opposed to left unresolved. */
    public final boolean resolve;

    /** Whether resolving stops at the first ip address rather than returning all of them. */
    public final boolean maxOneResult;

    /** Whether IPV6 addresses are included in the resolved results. */
    public final boolean includeIPV6;

    HostnameResolveMode(boolean resolve, boolean maxOneResult, boolean includeIPV6) {
        this.resolve = resolve;
        this.maxOneResult = maxOneResult;
        this.includeIPV6 = includeIPV6;
    }

    /**
     * Get the mode with the given name, ignoring case.
     * @param value the mode name
     * @return the matching mode, or null if the name does not match one
     */
    public static HostnameResolveMode get(String value) {
        for (HostnameResolveMode mode : HostnameResolveMode.values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
