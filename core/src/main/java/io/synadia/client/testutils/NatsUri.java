package io.synadia.client.testutils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

import static io.synadia.client.OptionsConstants.DEFAULT_URL;
import static io.synadia.client.testutils.NatsConstants.*;

/**
 * Represents a parsed NATS URI with scheme, host, port, and optional user info.
 * Handles various input formats including bare host:port, nats://, tls://, ws://, wss:// schemes.
 * Normalizes the URI by lowercasing the scheme and applying a default port if none is specified.
 */
@NullMarked
public class NatsUri {
    private static final int NO_PORT = -1;
    private static final String UNABLE_TO_PARSE = "Unable to parse URI string.";
    private static final String UNSUPPORTED_SCHEME = "Unsupported NATS URI scheme.";
    private static final String URI_E_ALLOW_TRY_PREFIXED = "Illegal character in scheme name at index";
    private static final Pattern IPV4_RE = Pattern.compile("(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])");
    private static final String COLON_SLASH_SLASH = "://";

    /**
     * A default NatsUri pointing to the default NATS URL (nats://localhost:4222).
     */
    public static final NatsUri DEFAULT_NATS_URI = new NatsUri();

    private final URI uri;
    private boolean isSecure;
    private boolean isWebsocket;
    private boolean hostIsIpAddress;

    /**
     * Get the underlying Java URI.
     * @return the URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Get the URI scheme (e.g., "nats", "tls", "ws", "wss").
     * @return the scheme
     */
    public String getScheme() {
        return uri.getScheme();
    }

    /**
     * Get the host portion of the URI.
     * @return the host
     */
    public String getHost() {
        return uri.getHost();
    }

    /**
     * Get the port portion of the URI.
     * @return the port
     */
    public int getPort() {
        return uri.getPort();
    }

    /**
     * Get the user info portion of the URI, if present.
     * @return the user info or null
     */
    @Nullable
    public String getUserInfo() {
        return uri.getUserInfo();
    }

    /**
     * Whether this URI uses a secure scheme (tls, wss, opentls).
     * @return true if secure
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Whether this URI uses a websocket scheme (ws, wss).
     * @return true if websocket
     */
    public boolean isWebsocket() {
        return isWebsocket;
    }

    /**
     * Whether the host is an IP address (IPv4 or bracketed IPv6).
     * @return true if the host is an IP address
     */
    public boolean hostIsIpAddress() {
        return hostIsIpAddress;
    }

    /**
     * Create a new NatsUri with the same scheme, port, and user info but a different host.
     * Handles IPv6 addresses by adding brackets if needed.
     * @param newHost the new host
     * @return a new NatsUri with the replaced host
     * @throws URISyntaxException if the resulting URI is invalid
     */
    public NatsUri reHost(String newHost) throws URISyntaxException {
        if (newHost.contains(":") && !newHost.startsWith("[")) {
            // this fixes IPV6 where it comes in without []
            // which it needs when making a url
            newHost = "[" + newHost + "]";
        }
        String newUrl = (uri.getRawUserInfo() == null)
            ? uri.getScheme() + "://" + newHost + ":" + uri.getPort()
            : uri.getScheme() + "://" + uri.getRawUserInfo() + "@" + newHost + ":" + uri.getPort();
        return new NatsUri(newUrl, uri.getScheme());
    }

    @Override
    public String toString() {
        return uri.toString();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof NatsUri other)) return false;
        return uri.equals(other.uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }

    /**
     * Construct a NatsUri pointing to the default NATS URL.
     */
    public NatsUri() {
        try {
            uri = new URI(DEFAULT_URL);
        } catch (URISyntaxException e) {
            // seriously, this better not happen!
            throw new RuntimeException(e);
        }
        postConstruct();
    }

    /**
     * Construct a NatsUri from a Java URI.
     * @param uri the URI to parse
     * @throws URISyntaxException if the URI is not a valid NATS URI
     */
    public NatsUri(URI uri) throws URISyntaxException {
        this(uri.toString(), null);
    }

    /**
     * Construct a NatsUri from a string, using the default nats:// scheme if none is specified.
     * @param url the URL string to parse
     * @throws URISyntaxException if the string is not a valid NATS URI
     */
    public NatsUri(String url) throws URISyntaxException {
        this(url, null);
    }

    /**
     * Construct a NatsUri from a string with an optional default scheme.
     * Handles various input formats including bare host:port, user:pass@host:port, and full URIs.
     * @param url the URL string to parse
     * @param defaultScheme the scheme to prepend if none is present, or null for nats://
     * @throws URISyntaxException if the string is not a valid NATS URI
     */
    public NatsUri(String url, @Nullable String defaultScheme) throws URISyntaxException {
    /*
        test string --> result of new URI(String)

        [1] provide protocol and try again
        1.2.3.4:4222 --> Illegal character in scheme name at index 0: 1.2.3.4:4222

        [2] throw exception
        proto:// --> Expected authority at index

        [3] null scheme, non-empty path? provide protocol and try again
        host    --> scheme:'null', host:'null', up:'null', port:-1, path:'host'
        1.2.3.4 --> scheme:'null', host:'null', up:'null', port:-1, path:'1.2.3.4'

        [4] has scheme but null host/path, provide protocol and try again
        x:p@host         --> scheme:'x', host:'null', up:'null', port:-1, path:'null'
        x:p@1.2.3.4      --> scheme:'x', host:'null', up:'null', port:-1, path:'null'
        x:4222           --> scheme:'x', host:'null', up:'null', port:-1, path:'null'
        x:p@host:4222    --> scheme:'x', host:'null', up:'null', port:-1, path:'null'
        x:p@1.2.3.4:4222 --> scheme:'x', host:'null', up:'null', port:-1, path:'null'

        [5X] has scheme, null host, non-null path, make/throw
        proto://u:p@      --> scheme:'proto', host:'null', up:'null', port:-1, path:''
        proto://:4222     --> scheme:'proto', host:'null', up:'null', port:-1, path:''
        proto://u:p@:4222 --> scheme:'proto', host:'null', up:'null', port:-1, path:''

        [5V6] also IPV6 ends up here
        proto://0:0:0:0:0:0:0:1:4222

        [6] has scheme and host just needs port
        proto://host        --> scheme:'proto', host:'host', up:'null', port:-1, path:''
        proto://u:p@host    --> scheme:'proto', host:'host', up:'u:p', port:-1, path:''
        proto://1.2.3.4     --> scheme:'proto', host:'1.2.3.4', up:'null', port:-1, path:''
        proto://u:p@1.2.3.4 --> scheme:'proto', host:'1.2.3.4', up:'u:p', port:-1, path:''

        [7] has scheme, host and port
        proto://host:4222        --> scheme:'proto', host:'host', up:'null', port:4222, path:''
        proto://u:p@host:4222    --> scheme:'proto', host:'host', up:'u:p', port:4222, path:''
        proto://1.2.3.4:4222     --> scheme:'proto', host:'1.2.3.4', up:'null', port:4222, path:''
        proto://u:p@1.2.3.4:4222 --> scheme:'proto', host:'1.2.3.4', up:'u:p', port:4222, path:''
     */

        String prefix;
        if (defaultScheme == null) {
            prefix = NATS_PROTOCOL_SLASH_SLASH;
        }
        else {
            prefix = defaultScheme.toLowerCase();
            if (!prefix.endsWith(COLON_SLASH_SLASH)) {
                prefix += COLON_SLASH_SLASH;
            }
        }

        url = url.trim();
        Helper helper = parse(url, true, prefix);
        String scheme = helper.uri.getScheme();
        String path = helper.uri.getPath();
        if (scheme == null) {
            if (path != null) {
                // [3]
                helper = tryPrefixed(helper.url, prefix);
                scheme = helper.uri.getScheme();
                path = helper.uri.getPath();
            }
            else {
                // [X] not in the examples so don't know what to do, we are done
                throw new URISyntaxException(url, UNABLE_TO_PARSE);
            }
        }

        String host = helper.uri.getHost();
        if (host == null) {
            if (path == null) {
                // [4]
                helper = tryPrefixed(helper.url, prefix);
                scheme = helper.uri.getScheme();
                host = helper.uri.getHost();
            }
            else {
                // [5X]
                throw new URISyntaxException(url, UNABLE_TO_PARSE);
            }
        }

        if (host == null) {
            // if these aren't here by now, nothing we can do
            throw new URISyntaxException(url, UNABLE_TO_PARSE);
        }

        String lower = scheme.toLowerCase();
        if (!KNOWN_PROTOCOLS.contains(lower)) {
            throw new URISyntaxException(url, UNSUPPORTED_SCHEME);
        }
        if (!lower.equals(scheme)) {
            helper.url = helper.url.replace(scheme, lower);
        }

        if (helper.uri.getPort() == NO_PORT) {
            // [6]
            uri = new URI(helper.url + ":" + DEFAULT_PORT);
        }
        else {
            uri = new URI(helper.url);
        }

        postConstruct();
    }

    private void postConstruct() {
        String s = uri.getScheme().toLowerCase();
        isSecure = SECURE_PROTOCOLS.contains(s);
        isWebsocket = WEBSOCKET_PROTOCOLS.contains(s);
        s = uri.getHost();
        hostIsIpAddress = IPV4_RE.matcher(s).matches() || s.startsWith("[") && s.endsWith("]");
    }

    static class Helper {
        String url;
        URI uri;

        public Helper(String url) throws URISyntaxException {
            this.url = url;
            this.uri = new URI(url);
        }
    }

    private Helper tryPrefixed(String url, String prefix) throws URISyntaxException {
        return parse(prefix + url, false, prefix);
    }

    private Helper parse(String url, boolean allowTryPrefixed, String prefix) throws URISyntaxException {
        try {
            return new Helper(url);
        }
        catch (URISyntaxException e) {
            if (allowTryPrefixed && e.getMessage().contains(URI_E_ALLOW_TRY_PREFIXED)) {
                // [4]
                return tryPrefixed(url, prefix);
            }
            else {
                // [5]
                throw e;
            }
        }
    }

    /**
     * Join a list of NatsUri instances into a single string with a delimiter.
     * @param delimiter the delimiter between URIs
     * @param uris the list of URIs to join
     * @return the joined string
     */
    public static String join(String delimiter, List<NatsUri> uris) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uris.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(uris.get(i));
        }
        return sb.toString();
    }

    private String equivalentComparable() {
        return uri.getHost().toLowerCase() + ":" + uri.getPort();
    }

    /**
     * Check if this URI is equivalent to another by comparing host (case-insensitive) and port.
     * @param other the other URI to compare
     * @return true if host and port match
     */
    public boolean equivalent(NatsUri other) {
        return equivalentComparable().compareTo(other.equivalentComparable()) == 0;
    }
}
