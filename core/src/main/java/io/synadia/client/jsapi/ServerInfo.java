package io.synadia.client.jsapi;

import io.nats.json.*;
import io.synadia.client.support.ServerVersion;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.nats.json.JsonValueUtils.readBytes;
import static io.nats.json.JsonValueUtils.readStringListOrEmpty;
import static io.synadia.client.support.ApiConstants.*;
import static io.synadia.client.support.ApiUtils.*;
import static io.synadia.client.support.NatsConstants.UNDEFINED;

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

    private final String serverId;
    private final String serverName;
    private final String version;
    private final String go;
    private final String host;
    private final int port;
    private final boolean headersSupported;
    private final boolean authRequired;
    private final boolean tlsRequired;
    private final boolean tlsAvailable;
    private final long maxPayload;
    private final List<String> connectURLs;
    private final int protocolVersion;
    private final byte @Nullable [] nonce;
    private final boolean lameDuckMode;
    private final boolean jetStream;
    private final int clientId;
    private final String clientIp;
    private final @Nullable String cluster;

    /**
     * Construct a ServerInfo instance from JSON
     * @param json the JSON
     */
    public ServerInfo(@Nullable String json) {
        // INFO<sp>{ INFO<\t>{ or {
        if (json == null || json.length() < 6 || ('{' != json.charAt(0) && '{' != json.charAt(5))) {
            throw new IllegalArgumentException("Invalid Server Info");
        }

        JsonValue jv;
        try {
            jv = JsonParser.parse(json, json.indexOf("{"));
        }
        catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid Server Info Json");
        }

        serverId = readString(jv, SERVER_ID, UNDEFINED);
        serverName = readString(jv, SERVER_NAME, UNDEFINED);
        version = readString(jv, VERSION, "0.0.0");
        go = readString(jv, GO, "0.0.0");
        host = readString(jv, HOST, UNDEFINED);
        headersSupported = readBoolean(jv, HEADERS, false);
        authRequired = readBoolean(jv, AUTH_REQUIRED, false);
        nonce = readBytes(jv, NONCE);
        tlsRequired = readBoolean(jv, TLS_REQUIRED, false);
        tlsAvailable = readBoolean(jv, TLS_AVAILABLE, false);
        lameDuckMode = readBoolean(jv, LAME_DUCK_MODE, false);
        jetStream = readBoolean(jv, JETSTREAM, false);
        port = readInteger(jv, PORT, 0);
        protocolVersion = readInteger(jv, PROTO, 0);
        maxPayload = readLong(jv, MAX_PAYLOAD, 0);
        clientId = readInteger(jv, CLIENT_ID, 0);
        clientIp = readString(jv, CLIENT_IP, "0.0.0.0");
        cluster = JsonValueUtils.readString(jv, CLUSTER);
        connectURLs = readStringListOrEmpty(jv, CONNECT_URLS, true);
    }

    /**
     * true if server is in lame duck mode
     * @return true if server is in lame duck mode
     */
    public boolean isLameDuckMode() {
        return lameDuckMode;
    }

    /**
     * the server id
     * @return the server id
     */
    public String getServerId() {
        return this.serverId;
    }

    /**
     * the server name
     * @return the server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * the server version
     * @return the server version
     */
    public String getVersion() {
        return this.version;
    }

    /**
     * the go version the server is built with
     * @return the go version the server is built with
     */
    public String getGoVersion() {
        return this.go;
    }

    /**
     * the server host
     * @return the server host
     */
    public String getHost() {
        return this.host;
    }

    /**
     * the server port
     * @return the server port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * the server protocol version
     * @return the server protocol version
     */
    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    /**
     * true if headers are supported by the server
     * @return true if headers are supported by the server
     */
    public boolean isHeadersSupported() { return this.headersSupported; }

    /**
     * true if authorization is required by the server
     * @return true if authorization is required by the server
     */
    public boolean isAuthRequired() {
        return this.authRequired;
    }

    /**
     * true if TLS is required by the server
     * @return true if TLS is required by the server
     */
    public boolean isTLSRequired() {
        return this.tlsRequired;
    }

    /**
     * true if TLS is available on the server
     * @return true if TLS is available on the server
     */
    public boolean isTLSAvailable() {
        return tlsAvailable;
    }

    /**
     * the server configured max payload
     * @return the max payload
     */
    public long getMaxPayload() {
        return this.maxPayload;
    }

    /**
     * the connectable urls in the cluster
     * @return the connectable urls
     */
    public List<String> getConnectURLs() {
        return this.connectURLs;
    }

    /**
     * the nonce to use in authentication
     * @return the nonce
     */
    public byte @Nullable [] getNonce() {
        return this.nonce;
    }

    /**
     * true if the server supports JetStream
     * @return true if the server supports JetStream
     */
    public boolean isJetStreamAvailable() {
        return this.jetStream;
    }

    /**
     * the client id as determined by the server
     * @return the client id
     */
    public int getClientId() {
        return clientId;
    }

    /**
     * the client ip address as determined by the server
     * @return the client ip
     */
    public String getClientIp() {
        return clientIp;
    }

    /**
     * the cluster name the server is in
     * @return the cluster name
     */
    @Nullable
    public String getCluster() {
        return cluster;
    }

    /**
     * function to determine is the server version is newer than the input
     * @param vTarget the target version to compare
     * @return true if the server version is newer than the input
     */
    public boolean isNewerVersionThan(String vTarget) {
        return ServerVersion.isNewer(version, vTarget);
    }

    /**
     * function to determine is the server version is same as the input
     * @param vTarget the target version to compare
     * @return true if the server version is same as the input
     */
    public boolean isSameVersion(String vTarget) {
        return ServerVersion.isSame(version, vTarget);
    }

    /**
     * function to determine is the server version is older than the input
     * @param vTarget the target version to compare
     * @return true if the server version is older than the input
     */
    public boolean isOlderThanVersion(String vTarget) {
        return ServerVersion.isOlder(version, vTarget);
    }

    /**
     * function to determine is the server version is the same or older than the input
     * @param vTarget the target version to compare
     * @return true if the server version is the same or older than the input
     */
    public boolean isSameOrOlderThanVersion(String vTarget) {
        return ServerVersion.isSameOrOlder(version, vTarget);
    }

    /**
     * function to determine is the server version is same or newer than the input
     * @param vTarget the target version to compare
     * @return true if the server version is same or newer than the input
     */
    public boolean isSameOrNewerThanVersion(String vTarget) {
        return ServerVersion.isSameOrNewer(version, vTarget);
    }

    @Override
    public String toString() {
        return "ServerInfo " + new MapBuilder()
            .put(SERVER_ID, serverId)
            .put(SERVER_NAME, serverName)
            .put(VERSION, version)
            .put(GO, go)
            .put(HOST, host)
            .put(PORT, port)
            .put(HEADERS, headersSupported)
            .put(AUTH_REQUIRED, authRequired)
            .put(TLS_REQUIRED, tlsRequired)
            .put(TLS_AVAILABLE, tlsAvailable)
            .put(MAX_PAYLOAD, maxPayload)
            .put(PROTO, protocolVersion)
            .put(LAME_DUCK_MODE, lameDuckMode)
            .put(JETSTREAM, jetStream)
            .put(CLIENT_ID, clientId)
            .put(CLIENT_IP, clientIp)
            .put(CLUSTER, cluster)
            .toJson();
    }
}

