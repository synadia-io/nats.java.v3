package io.synadia.client.api;

import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.testutils.ApiUtils;
import io.synadia.client.testutils.ServerVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;
import static io.synadia.client.testutils.NatsConstants.UNDEFINED;

/**
 * Class holding information about a server
 */
@NullMarked
public class ServerInfo {

    /**
     * Constant representing an empty info. Used to ensure getting a ServerInfo from a connection is never null,
     * it can be based on timing.
     */
    public static final ServerInfo EMPTY_INFO = new ServerInfo("INFO {}");

    private final LazyJsonValue ljv;
    private @Nullable String _version;

    /**
     * Construct a ServerInfo instance from JSON
     * @param json the JSON
     */
    public ServerInfo(@Nullable String json) {
        if (json == null || json.length() < 6 || ('{' != json.charAt(0) && '{' != json.charAt(5))) {
            throw new IllegalArgumentException("Invalid Server Info");
        }
        try {
            ljv = LazyJsonParser.parse(json, json.indexOf("{"));
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Invalid Server Info Json");
        }
    }

    /**
     * true if server is in lame duck mode
     * @return true if server is in lame duck mode
     */
    public boolean isLameDuckMode() {
        return readBoolean(ljv, LAME_DUCK_MODE, false);
    }

    /**
     * the server id
     * @return the server id
     */
    public String getServerId() {
        return ApiUtils.readString(ljv, SERVER_ID, UNDEFINED);
    }

    /**
     * the server name
     * @return the server name
     */
    public String getServerName() {
        return ApiUtils.readString(ljv, SERVER_NAME, UNDEFINED);
    }

    /**
     * the server version
     * @return the server version
     */
    public String getVersion() {
        if (_version == null) {
            _version = ApiUtils.readString(ljv, VERSION, "0.0.0");
        }
        return _version;
    }

    /**
     * the go version the server is built with
     * @return the go version the server is built with
     */
    public String getGoVersion() {
        return ApiUtils.readString(ljv, GO, "0.0.0");
    }

    /**
     * the server host
     * @return the server host
     */
    public String getHost() {
        return ApiUtils.readString(ljv, HOST, UNDEFINED);
    }

    /**
     * the server port
     * @return the server port
     */
    public int getPort() {
        return readInteger(ljv, PORT, 0);
    }

    /**
     * the server protocol version
     * @return the server protocol version
     */
    public int getProtocolVersion() {
        return readInteger(ljv, PROTO, 0);
    }

    /**
     * true if headers are supported by the server
     * @return true if headers are supported by the server
     */
    public boolean isHeadersSupported() {
        return readBoolean(ljv, HEADERS, false);
    }

    /**
     * true if authorization is required by the server
     * @return true if authorization is required by the server
     */
    public boolean isAuthRequired() {
        return readBoolean(ljv, AUTH_REQUIRED, false);
    }

    /**
     * true if TLS is required by the server
     * @return true if TLS is required by the server
     */
    public boolean isTLSRequired() {
        return readBoolean(ljv, TLS_REQUIRED, false);
    }

    /**
     * true if TLS is available on the server
     * @return true if TLS is available on the server
     */
    public boolean isTLSAvailable() {
        return readBoolean(ljv, TLS_AVAILABLE, false);
    }

    /**
     * the server configured max payload
     * @return the max payload
     */
    public long getMaxPayload() {
        return readLong(ljv, MAX_PAYLOAD, 0);
    }

    /**
     * the connectable urls in the cluster
     * @return the connectable urls
     */
    public List<String> getConnectURLs() {
        return readStringListOrEmpty(ljv, CONNECT_URLS, true);
    }

    /**
     * the nonce to use in authentication
     * @return the nonce
     */
    public byte @Nullable [] getNonce() {
        String s = readString(ljv, NONCE);
        return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * true if the server supports JetStream
     * @return true if the server supports JetStream
     */
    public boolean isJetStreamAvailable() {
        return readBoolean(ljv, JETSTREAM, false);
    }

    /**
     * the client id as determined by the server
     * @return the client id
     */
    public int getClientId() {
        return readInteger(ljv, CLIENT_ID, 0);
    }

    /**
     * the client ip address as determined by the server
     * @return the client ip
     */
    public String getClientIp() {
        return ApiUtils.readString(ljv, CLIENT_IP, "0.0.0.0");
    }

    /**
     * the cluster name the server is in
     * @return the cluster name
     */
    @Nullable
    public String getCluster() {
        return readString(ljv, CLUSTER);
    }

    /**
     * function to determine is the server version is newer than the input
     * @param vTarget the target version to compare
     * @return true if the server version is newer than the input
     */
    public boolean isNewerVersionThan(String vTarget) {
        return ServerVersion.isNewer(getVersion(), vTarget);
    }

    /**
     * function to determine is the server version is same as the input
     * @param vTarget the target version to compare
     * @return true if the server version is same as the input
     */
    public boolean isSameVersion(String vTarget) {
        return ServerVersion.isSame(getVersion(), vTarget);
    }

    /**
     * function to determine is the server version is older than the input
     * @param vTarget the target version to compare
     * @return true if the server version is older than the input
     */
    public boolean isOlderThanVersion(String vTarget) {
        return ServerVersion.isOlder(getVersion(), vTarget);
    }

    /**
     * function to determine is the server version is the same or older than the input
     * @param vTarget the target version to compare
     * @return true if the server version is the same or older than the input
     */
    public boolean isSameOrOlderThanVersion(String vTarget) {
        return ServerVersion.isSameOrOlder(getVersion(), vTarget);
    }

    /**
     * function to determine is the server version is same or newer than the input
     * @param vTarget the target version to compare
     * @return true if the server version is same or newer than the input
     */
    public boolean isSameOrNewerThanVersion(String vTarget) {
        return ServerVersion.isSameOrNewer(getVersion(), vTarget);
    }

    @Override
    public String toString() {
        return "ServerInfo " + ljv.toJson();
    }
}
