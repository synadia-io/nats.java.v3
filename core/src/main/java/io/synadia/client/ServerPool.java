package io.synadia.client;

import io.synadia.client.utils.ServerAddress;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Allows the developer to provide the list of servers to try for connecting/reconnecting.
 * The pool holds addresses only (host, port and, for a websocket, a path); the transport for every
 * server comes from the options, see {@link Options#getTransport()}.
 */
public interface ServerPool {

    /**
     * Initialize the pool.
     * @param opts the options that was used to make the connection is supplied
     */
    void initialize(@NonNull Options opts);

    /**
     * When the connection received discovered servers (ServerInfo.getConnectURLs)
     * it parses them and passes the ones that parse on to the provider for later use, together with
     * the server whose INFO supplied them. A discovered server given as a bare ip address has no hostname
     * to present to the server during the TLS handshake; the pool may save the origin's hostname on it for that purpose.
     * @param discoveredServers the list of discovered servers.
     * @param origin the server whose INFO supplied the list, or null when it is not known
     * @return true if there were any unknown servers provided
     */
    boolean acceptDiscoveredUrls(@NonNull List<@NonNull ServerAddress> discoveredServers, @Nullable ServerAddress origin);

    /**
     * Just take a peek at the next server without doing any processing.
     * @return the next server or null if the pool is empty.
     */
    @Nullable
    ServerAddress peekNextServer();

    /**
     * Get the next server to try to connect to.
     * @return the next server or null if the pool is empty.
     */
    @Nullable
    ServerAddress nextServer();

    /**
     * Resolve a host name to an ip address based on the instructions. Replaces resolveHostToIps(String)
     *
     * @param host         the host to resolve
     * @param maxOneResult flag to indicate to only return one result
     * @param includeIPV6  flag to indicate to include IPV6 ip addresses in the results.
     * @return a list of resolved hosts. Can be null if host does not resolve
     */
    @Nullable
    List<String> resolveHostToIps(@NonNull String host, boolean maxOneResult, boolean includeIPV6);

    /**
     * Indicate that the connection to this server succeeded.
     * @param address should match the address given by nextServer
     */
    void connectSucceeded(@NonNull ServerAddress address);

    /**
     * Indicate that the connection to this server failed.
     * @param address should match the address given by nextServer
     * @return true if the pool no longer supplies the server, for example because it failed more times
     * than the reconnect limit allows; the connection reports that through
     * {@link ErrorListener#serverIssue ErrorListener.serverIssue} as {@link ServerIssueType#CountedOut}
     */
    boolean connectFailed(@NonNull ServerAddress address);

    /**
     * Get the list of servers known to the pool, each as {@code host:port[/path]}. Purely informational
     * @return the list of servers
     */
    @NonNull
    List<String> getServerList();
}
