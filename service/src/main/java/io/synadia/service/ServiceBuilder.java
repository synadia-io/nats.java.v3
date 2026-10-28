package io.synadia.service;

import io.synadia.client.Dispatcher;
import io.synadia.client.impl.NatsConnection;

import java.util.HashMap;
import java.util.Map;

import static io.synadia.client.utils.Validator.*;
import static io.synadia.service.Service.RESERVED_SERVICE_PREFIX_TOKEN;
import static io.synadia.service.Service.normalizePrefix;

/**
 * Build a Service using a fluent builder.
 * Use the Service static method {@code builder()} or {@code new ServiceBuilder()} to get an instance.
 * Builder methods validate their arguments and throw {@link IllegalArgumentException} for a null or otherwise invalid value.
 */
public class ServiceBuilder {
    /**
     * Constant for the default drain timeout in millis
     */
    public static final long DEFAULT_DRAIN_TIMEOUT_MILLIS = 5000;

    NatsConnection conn;
    String prefix = RESERVED_SERVICE_PREFIX_TOKEN;
    String name;
    String description;
    String version;
    Map<String, String> metadata;
    final Map<String, ServiceEndpoint> serviceEndpoints = new HashMap<>();
    long drainTimeout = DEFAULT_DRAIN_TIMEOUT_MILLIS;
    Dispatcher pingDispatcher;
    Dispatcher infoDispatcher;
    Dispatcher statsDispatcher;

    /**
     * Construct an instance of the builder
     */
    public ServiceBuilder() {}

    /**
     * The connection the service runs on
     * @param conn connection
     * @return the ServiceBuilder
     */
    public ServiceBuilder connection(NatsConnection conn) {
        this.conn = conn;
        return this;
    }

    /**
     * The prefix of the discovery subjects, in place of {@code $SRV}, for instance {@code ACME}
     * gives {@code ACME.PING}, {@code ACME.INFO} and {@code ACME.STATS}.
     * The prefix is trimmed and a trailing dot is dropped; null or empty means {@code $SRV}.
     * It can have more than one token, its case is kept, and it cannot contain whitespace,
     * wildcards, empty tokens or a token that is {@code $SRV} (case-sensitive).
     * A service endpoint subject cannot be the prefix or under it.
     * @param prefix the prefix
     * @return the ServiceBuilder
     * @throws IllegalArgumentException if the prefix contains whitespace, a wildcard, an empty token,
     * a non-printable character or a {@code $SRV} token
     */
    public ServiceBuilder prefix(String prefix) {
        String p = normalizePrefix(prefix);
        if (!p.equals(RESERVED_SERVICE_PREFIX_TOKEN)) {
            validateSubjectTermStrict(p, "Service Prefix", true);
            if (notPrintableOrHasWildGt(p)) {
                throw new IllegalArgumentException("Service Prefix must be in the printable ASCII range and cannot include '*', '>' [" + p + "]");
            }
            for (String token : p.split("\\.")) {
                if (token.equals(RESERVED_SERVICE_PREFIX_TOKEN)) {
                    throw new IllegalArgumentException("Service Prefix cannot contain the token " + RESERVED_SERVICE_PREFIX_TOKEN + " [" + p + "]");
                }
            }
        }
        this.prefix = p;
        return this;
    }

    /**
     * The simple name of the service
     * @param name the name
     * @return the ServiceBuilder
     * @throws IllegalArgumentException if the name is null, empty, or not a valid restricted term (A-Z, a-z, 0-9, '-' or '_')
     */
    public ServiceBuilder name(String name) {
        this.name = validateIsRestrictedTerm(name, "Service Name", true);
        return this;
    }

    /**
     * The simple description of the service
     * @param description the description
     * @return the ServiceBuilder
     */
    public ServiceBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * The simple version of the service.
     * @param version the version
     * @return the ServiceBuilder
     * @throws IllegalArgumentException if the version is null, empty, or not a valid semantic version
     */
    public ServiceBuilder version(String version) {
        this.version = validateSemVer(version, "Service Version", true);
        return this;
    }

    /**
     * Any meta information about this service. The map is copied, so changing it
     * afterward does not change the service's metadata.
     * @param metadata the meta
     * @return the ServiceBuilder
     */
    public ServiceBuilder metadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            this.metadata = null;
        }
        else {
            this.metadata = new HashMap<>(metadata);
        }
        return this;
    }

    /**
     * Add a service endpoint into the service. There can only be one instance of a service endpoint by name
     * @param serviceEndpoint the service endpoint
     * @return the ServiceBuilder
     */
    public ServiceBuilder addServiceEndpoint(ServiceEndpoint serviceEndpoint) {
        serviceEndpoints.put(serviceEndpoint.getName(), serviceEndpoint);
        return this;
    }

    /**
     * The timeout when stopping a service, in milliseconds. A value less than 1 re-defaults to
     * {@value #DEFAULT_DRAIN_TIMEOUT_MILLIS} milliseconds.
     * @param millis the drain timeout in milliseconds
     * @return the ServiceBuilder
     */
    public ServiceBuilder drainTimeout(long millis) {
        this.drainTimeout = millis <= 0 ? DEFAULT_DRAIN_TIMEOUT_MILLIS : millis;
        return this;
    }

    /**
     * Optional dispatcher for the ping service
     * @param pingDispatcher the dispatcher
     * @return the ServiceBuilder
     */
    public ServiceBuilder pingDispatcher(Dispatcher pingDispatcher) {
        this.pingDispatcher = pingDispatcher;
        return this;
    }

    /**
     * Optional dispatcher for the info service
     * @param infoDispatcher the dispatcher
     * @return the ServiceBuilder
     */
    public ServiceBuilder infoDispatcher(Dispatcher infoDispatcher) {
        this.infoDispatcher = infoDispatcher;
        return this;
    }

    /**
     * Optional dispatcher for the stats service
     * @param statsDispatcher the dispatcher
     * @return the ServiceBuilder
     */
    public ServiceBuilder statsDispatcher(Dispatcher statsDispatcher) {
        this.statsDispatcher = statsDispatcher;
        return this;
    }

    /**
     * Build the Service instance.
     * @return the Service instance
     * @throws IllegalArgumentException if the connection is null, or the name or version is null or empty
     */
    public Service build() {
        required(conn, "NatsConnection");
        required(name, "Name");
        required(version, "Version");
        return new Service(this);
    }
}
