package io.synadia.service;

import io.nats.json.DateTimeUtils;
import io.synadia.client.Dispatcher;
import io.synadia.client.NUID;
import io.synadia.client.impl.NatsConnection;
import io.synadia.client.utils.ApiUtils;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.Validator.nullOrEmpty;
import static io.synadia.service.ServiceConstants.*;

/**
 * The Services Framework introduces a higher-level API for implementing services with NATS.
 * Services automatically contain Ping, Info and Stats responders.
 * Services have one or more service endpoints. {@link ServiceEndpoint}
 * When multiple instances of a service endpoints are active they work in a queue, meaning only one listener responds to any given request.
 */
public class Service {
    /**
     * Version of the service library
     */
    public static final String LIBRARY_VERSION = ApiUtils.loadVersion(Service.class, "service");

    /**
     * Constant for the PING service
     */
    public static final String SRV_PING = "PING";
    /**
     * Constant for the INFO service
     */
    public static final String SRV_INFO = "INFO";
    /**
     * Constant for the STATS service
     */
    public static final String SRV_STATS = "STATS";
    /**
     * Constant of the service prefix
     */
    public static final String DEFAULT_SERVICE_PREFIX = "$SRV.";

    private final NatsConnection conn;
    private final long drainTimeout;
    private final ConcurrentHashMap<String, EndpointContext> serviceContexts;
    private final List<EndpointContext> discoveryContexts;
    private final List<Dispatcher> dInternals;
    private final AtomicReference<ZonedDateTime> startTimeRef;
    private final CompletableFuture<Boolean> startedFuture;
    private final PingResponse pingResponse;
    private final InfoResponse infoResponse;

    private final ReentrantLock startStopLock;
    private CompletableFuture<Boolean> runningIndicator;

    Service(ServiceBuilder b) {
        String id = new NUID().next();
        conn = b.conn;
        drainTimeout = b.drainTimeout;
        dInternals = new ArrayList<>();
        startStopLock = new ReentrantLock();
        startTimeRef = new AtomicReference<>(DateTimeUtils.DEFAULT_TIME);
        startedFuture = new CompletableFuture<>();

        // build responses first. info needs to be available when adding service endpoints.
        pingResponse = new PingResponse(id, b.name, b.version, b.metadata);
        infoResponse = new InfoResponse(id, b.name, b.version, b.metadata, b.description);

        // set up the service contexts
        // ? do we need an internal dispatcher for any user endpoints !! addServiceEndpoint deals with it
        serviceContexts = new ConcurrentHashMap<>();
        addServiceEndpoints(b.serviceEndpoints.values());

        Dispatcher dTemp = null;
        if (b.pingDispatcher == null || b.infoDispatcher == null || b.statsDispatcher == null) {
            dTemp = conn.createDispatcher();
            dInternals.add(dTemp);
        }

        discoveryContexts = new ArrayList<>();
        addDiscoveryContexts(SRV_PING, pingResponse, b.pingDispatcher, dTemp);
        addDiscoveryContexts(SRV_INFO, infoResponse, b.infoDispatcher, dTemp);
        addStatsContexts(b.statsDispatcher, dTemp);
    }

    /**
     * Adds one or more service endpoint to the list of service contexts and starts it if the service is running.
     * @param serviceEndpoints one or more service endpoints to be added
     */
    public void addServiceEndpoints(ServiceEndpoint... serviceEndpoints) {
        if (!nullOrEmpty(serviceEndpoints)) {
            _addServiceEndpoints(Arrays.asList(serviceEndpoints));
        }
    }

    /**
     * Adds all service endpoints to the list of service contexts and starts it if the service is running.
     * @param serviceEndpoints service endpoints to be added
     */
    public void addServiceEndpoints(Collection<ServiceEndpoint> serviceEndpoints) {
        if (!nullOrEmpty(serviceEndpoints)) {
            _addServiceEndpoints(serviceEndpoints);
        }
    }

    private void _addServiceEndpoints(Collection<ServiceEndpoint> serviceEndpoints) {
        startStopLock.lock();
        try {
            for (ServiceEndpoint se : serviceEndpoints) {
                if (se != null) {
                    // do this first so it's available on start
                    infoResponse.addServiceEndpoint(se);
                    EndpointContext ctx;
                    if (se.getDispatcher() == null) {
                        Dispatcher dTemp = dInternals.isEmpty() ? null : dInternals.get(0);
                        if (dTemp == null) {
                            dTemp = conn.createDispatcher();
                            dInternals.add(dTemp);
                        }
                        ctx = new EndpointContext(conn, dTemp, false, se);
                    }
                    else {
                        ctx = new EndpointContext(conn, null, false, se);
                    }
                    serviceContexts.put(se.getName(), ctx);

                    // if the service is already started, start the newly added context
                    if (runningIndicator != null) {
                        ctx.start();
                    }
                }
            }
        }
        finally {
            startStopLock.unlock();
        }
    }

    private void addDiscoveryContexts(String discoveryName, Dispatcher dUser, Dispatcher dInternal, ServiceMessageHandler handler) {
        Endpoint[] endpoints = new Endpoint[] {
            newEndpoint(discoveryName, null, null),
            newEndpoint(discoveryName, pingResponse.getName(), null),
            newEndpoint(discoveryName, pingResponse.getName(), pingResponse.getId())
        };

        for (Endpoint endpoint : endpoints) {
            discoveryContexts.add(
                new EndpointContext(conn, dInternal, true,
                    new ServiceEndpoint(endpoint, handler, dUser)));
        }
    }

    /**
     * Adds discovery contexts for the service, reusing the same static bytes at registration.
     * @param discoveryName the name of the discovery
     * @param sr the service response
     * @param dUser the user dispatcher
     * @param dInternal the internal dispatcher
     */
    private void addDiscoveryContexts(String discoveryName, ServiceResponse sr, Dispatcher dUser, Dispatcher dInternal) {
        ServiceMessageHandler handler = smsg -> smsg.respond(conn, sr.serialize());
        addDiscoveryContexts(discoveryName, dUser, dInternal, handler);
    }

    private void addStatsContexts(Dispatcher dUser, Dispatcher dInternal) {
        ServiceMessageHandler handler = smsg -> smsg.respond(conn, getStatsResponse().serialize());
        addDiscoveryContexts(SRV_STATS, dUser, dInternal, handler);
    }

    private Endpoint newEndpoint(String discoveryName, String optionalServiceNameSegment, String optionalServiceIdSegment) {
        String subject = toDiscoverySubject(discoveryName, optionalServiceNameSegment, optionalServiceIdSegment);
        return new Endpoint(subject, subject, null, null, false);
    }

    static String toDiscoverySubject(String discoveryName, String optionalServiceNameSegment, String optionalServiceIdSegment) {
        if (nullOrEmpty(optionalServiceIdSegment)) {
            if (nullOrEmpty(optionalServiceNameSegment)) {
                return DEFAULT_SERVICE_PREFIX + discoveryName;
            }
            return DEFAULT_SERVICE_PREFIX + discoveryName + "." + optionalServiceNameSegment;
        }
        return DEFAULT_SERVICE_PREFIX + discoveryName + "." + optionalServiceNameSegment + "." + optionalServiceIdSegment;
    }

    /**
     * Start the service
     * @return a future that can be held to see if another thread called stop
     */
    public CompletableFuture<Boolean> startService() {
        startStopLock.lock();
        try {
            if (runningIndicator == null) {
                runningIndicator = new CompletableFuture<>();
                for (EndpointContext ctx : serviceContexts.values()) {
                    ctx.start();
                }
                for (EndpointContext ctx : discoveryContexts) {
                    ctx.start();
                }
                startTimeRef.set(DateTimeUtils.utcNow());
                startedFuture.complete(true);
            }
            return runningIndicator;
        }
        finally {
            startStopLock.unlock();
        }
    }

    /**
     * Get an instance of a ServiceBuilder.
     * @return the instance
     */
    public static ServiceBuilder builder() {
        return new ServiceBuilder();
    }

    /**
     * Stop the service by draining.
     */
    public void stop() {
        stop(true, null);
    }

    /**
     * Stop the service by draining. Mark the future that was received from the start method that the service completed exceptionally.
     * @param t the error cause
     */
    public void stop(Throwable t) {
        stop(true, t);
    }

    /**
     * Stop the service, optionally draining.
     * @param drain the flag indicating to drain or not
     */
    public void stop(boolean drain) {
        stop(drain, null);
    }

    /**
     * Stop the service, optionally draining and optionally with an error cause
     * @param drain the flag indicating to drain or not
     * @param t the optional error cause. If supplied, mark the future that was received from the start method that the service completed exceptionally
     */
    public void stop(boolean drain, Throwable t) {
        startStopLock.lock();
        try {
            if (runningIndicator != null) {
                if (drain) {
                    List<CompletableFuture<Boolean>> futures = new ArrayList<>();

                    for (Dispatcher d : dInternals) {
                        try {
                            futures.add(d.drain(drainTimeout));
                        }
                        catch (Exception e) { /* nothing I can really do, we are stopping anyway */ }
                    }

                    for (EndpointContext c : serviceContexts.values()) {
                        if (c.isNotInternalDispatcher()) {
                            try {
                                futures.add(c.drain(drainTimeout));
                            }
                            catch (Exception e) { /* nothing I can really do, we are stopping anyway */ }
                        }
                    }

                    for (EndpointContext c : discoveryContexts) {
                        if (c.isNotInternalDispatcher()) {
                            try {
                                futures.add(c.drain(drainTimeout));
                            }
                            catch (Exception e) { /* nothing I can really do, we are stopping anyway */ }
                        }
                    }

                    // make sure drain is done before closing dispatcher
                    for (CompletableFuture<Boolean> f : futures) {
                        try {
                            f.get(drainTimeout, TimeUnit.MILLISECONDS);
                        }
                        catch (Exception ignore) {
                            // don't care if it completes successfully or not, just that it's done.
                        }
                    }
                }

                // close internal dispatchers
                for (Dispatcher d : dInternals) {
                    conn.closeDispatcher(d);
                }

                // ok we are done
                if (t == null) {
                    runningIndicator.complete(true);
                }
                else {
                    runningIndicator.completeExceptionally(t);
                }
                runningIndicator = null; // we don't need a copy anymore
            }
        }
        finally {
            startStopLock.unlock();
        }
    }

    /**
     * Reset the statistics for the endpoints
     */
    public void reset() {
        if (isStarted()) {
            // has actually been started if the ref has been set
            startTimeRef.set(DateTimeUtils.utcNow());
        }
        for (EndpointContext c : discoveryContexts) {
            c.reset();
        }
        for (EndpointContext c : serviceContexts.values()) {
            c.reset();
        }
    }

    /**
     * Get the id of the service
     * @return the id
     */
    public String getId() {
        return infoResponse.getId();
    }

    /**
     * Get the name of the service
     * @return the name
     */
    public String getName() {
        return infoResponse.getName();
    }

    /**
     * Get the version of the service
     * @return the version
     */
    public String getVersion() {
        return infoResponse.getVersion();
    }

    /**
     * Get the description of the service
     * @return the description
     */
    public String getDescription() {
        return infoResponse.getDescription();
    }

    /**
     * Get whether {@link #startService()} has been called on this service.
     * <p>This is a local check only. It does not confirm that the server has registered the
     * service's subscriptions, so a service can report started here and still not answer a
     * request. Use {@link #isStarted(long)} when readiness is what you actually need.
     * @return true if the service has been started locally
     */
    public boolean isStarted() {
        return startedFuture.isDone();
    }

    /**
     * Get whether the service is started and the server has registered its subscriptions.
     * <p>Confirmed with a round trip on the service's connection. The server reads one
     * connection's stream in order, so a completed round trip proves it has already processed
     * every subscription {@link #startService()} queued before it.
     * <p>Only subscriptions on the service's own connection are confirmed. An endpoint given its
     * own {@link ServiceEndpoint.Builder#dispatcher(Dispatcher) dispatcher} built from a
     * different connection is not covered by this check.
     * @param timeoutMillis the maximum time to wait for the round trip, in milliseconds;
     *                      less than 1 uses the connection timeout
     * @return true if started and confirmed by the server within the timeout
     */
    public boolean isStarted(long timeoutMillis) {
        if (!startedFuture.isDone()) {
            return false;
        }
        try {
            conn.RTT(timeoutMillis);
            return true;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (IllegalStateException | IOException | TimeoutException e) {
            return false;
        }
    }

    /**
     * Get the drain timeout setting, in milliseconds
     * @return the drain timeout setting in milliseconds
     */
    public long getDrainTimeout() {
        return drainTimeout;
    }

    /**
     * Get the pre-constructed ping response.
     * @return the ping response
     */
    public PingResponse getPingResponse() {
        return pingResponse;
    }

    /**
     * Get the pre-constructed info response.
     * @return the info response
     */
    public InfoResponse getInfoResponse() {
        return infoResponse;
    }

    /**
     * Get the up-to-date stats response which contains a list of all {@link EndpointStats}
     * @return the stats response
     */
    public StatsResponse getStatsResponse() {
        List<EndpointStats> endpointStats = new ArrayList<>();
        for (EndpointContext c : serviceContexts.values()) {
            endpointStats.add(c.getEndpointStats());
        }
        // StatsResponse handles a start time of DateTimeUtils.DEFAULT_TIME
        return new StatsResponse(pingResponse, startTimeRef.get(), endpointStats);
    }

    /**
     * Get the up-to-date {@link EndpointStats} for a specific endpoint
     * @param endpointName the endpoint name
     * @return the EndpointStats or null if the name is not found.
     */
    public EndpointStats getEndpointStats(String endpointName) {
        EndpointContext c = serviceContexts.get(endpointName);
        return c == null ? null : c.getEndpointStats();
    }

    @Override
    public String toString() {
        StringBuilder sb = beginJsonPrefixed("\"Service\":");
        addField(sb, ID, infoResponse.getId());
        addField(sb, NAME, infoResponse.getName());
        addField(sb, VERSION, infoResponse.getVersion());
        addField(sb, DESCRIPTION, infoResponse.getDescription());
        addField(sb, STARTED, startTimeRef.get());
        return endJson(sb).toString();
    }
}
