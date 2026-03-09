package io.synadia.client.impl;

import io.synadia.client.support.NatsInetAddress;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for resolving a host to IP addresses
 */
public final class NatsHostResolver {
    private NatsHostResolver() {}  /* ensures cannot be constructed */

    /**
     * Resolve a host to ip addresses
     * @param host the host
     * @param maxOneResult whether to return at max one result
     * @return the list of ips addresses or null if there were no ip addresses for the host.
     */
    public static @Nullable List<String> resolveHostToIps(@NonNull String host, boolean maxOneResult, boolean includeIPV6) {
        // 1. try to resolve the hostname, adding results to list
        List<String> results = new ArrayList<>();
        try {
            InetAddress[] addresses = NatsInetAddress.getAllByName(host);
            for (InetAddress a : addresses) {
                if (includeIPV6 || a instanceof Inet4Address) {
                    results.add(a.getHostAddress());
                }
            }
        }
        catch (UnknownHostException ignore) {
            // A user might have supplied a bad host, but the server shouldn't.
            // Either way, nothing much we can do.
            return null;
        }

        // 2. no results, return null.
        if (results.isEmpty()) {
            return null;
        }

        // 3. If results size == 1, just return
        if (results.size() == 1) {
            return results;
        }

        // 4. if maxOneResult, return the sublist
        if (maxOneResult) {
            return results.subList(0, 1);
        }

        // 5. return all results
        return results;
    }
}
