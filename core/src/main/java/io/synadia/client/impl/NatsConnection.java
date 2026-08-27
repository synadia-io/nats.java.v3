package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.api.ServerInfo;
import io.synadia.client.global.NatsInetAddress;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static io.synadia.client.ConnectionStatus.*;
import static io.synadia.client.utils.NatsConstants.*;
import static io.synadia.client.utils.NatsRequestCompletableFuture.CancelAction;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The connection to a NATS server.
 *
 * <p>Publish, subscribe, and request methods validate their arguments and throw {@link IllegalArgumentException} for an invalid subject, reply subject, or message.
 */
public class NatsConnection implements AutoCloseable {

    protected final Options options;
    protected final boolean forceFlushOnRequest;

    protected final StatisticsCollector statistics;

    protected boolean connecting; // you can only connect in one thread
    protected boolean disconnecting; // you can only disconnect in one thread
    protected boolean closing; // respect a close call regardless
    protected Exception exceptionDuringConnectChange; // exception occurred in another thread while dis/connecting
    protected final ReentrantLock closeSocketLock;

    private volatile ConnectionStatus status;
    protected final ReentrantLock statusLock;
    protected final Condition statusChanged;

    protected CompletableFuture<DataPort> dataPortFuture;
    protected DataPort dataPort;
    protected NatsUri currentServer;
    protected NatsUri lastServer;
    protected CompletableFuture<Boolean> reconnectWaiter;
    private volatile boolean lameDuckTriggered = false;
    protected final ConcurrentHashMap<NatsUri, String> serverAuthErrors;

    protected NatsConnectionReader reader;
    protected NatsConnectionWriter writer;

    protected final AtomicReference<ServerInfo> serverInfo;

    protected final Map<String, SubscriptionInfo> subscriptions;
    protected final Set<NatsDispatcher> dispatchers; // use a concurrent set so we get more consistent iteration behavior
    protected final Map<String, ConnectionListener> connectionListeners;
    protected final Map<String, ErrorListener> errorListeners;
    protected final Map<String, NatsRequestCompletableFuture> responsesAwaiting;
    protected final Map<String, NatsRequestCompletableFuture> responsesRespondedTo;
    protected final ConcurrentLinkedDeque<CompletableFuture<Boolean>> pongQueue;

    protected final String mainInbox;
    protected final AtomicReference<NatsDispatcher> inboxDispatcher;
    protected final ReentrantLock inboxDispatcherLock;
    protected ScheduledTask pingTask;
    protected ScheduledTask cleanupTask;

    protected final AtomicBoolean needPing;

    protected final AtomicLong nextSid;
    protected final NUID nuid;

    protected final AtomicReference<String> connectError;
    protected final AtomicReference<String> lastError;
    protected final AtomicReference<CompletableFuture<Boolean>> draining;
    protected final AtomicBoolean blockPublishForDrain;
    protected final AtomicBoolean tryingToConnect;

    // these are not final so they can be nullified on close
    protected ExecutorService callbackExecutor;
    protected ExecutorService executor;
    protected ExecutorService connectExecutor;
    protected ExecutorService readerExecutor;
    protected ExecutorService writerExecutor;
    protected ScheduledExecutorService scheduledExecutor;

    protected final boolean advancedTracking;

    protected final ServerPool serverPool;
    protected final DispatcherFactory dispatcherFactory;

    // allows user to opt into the level of subject validation they want
    protected interface SubjectReplyValidator {
        String validate(String subject, boolean required);
    }

    protected final SubjectReplyValidator subjectValidator;
    protected final SubjectReplyValidator replyValidator;

    protected String subjectValidate(String subject) {
        return subjectValidator.validate(subject, true);
    }

    protected String replyValidate(String replyTo) {
        return replyValidator.validate(replyTo, false);
    }

    protected NatsConnection(@NonNull Options options) {
        this.options = options;
        forceFlushOnRequest = options.forceFlushOnRequest();

        advancedTracking = options.isTrackAdvancedStats();
        this.statistics = options.getStatisticsCollector() == null ? new NatsStatistics() : options.getStatisticsCollector();
        statistics.setAdvancedTracking(advancedTracking);

        this.closeSocketLock = new ReentrantLock();

        this.statusLock = new ReentrantLock();
        this.statusChanged = statusLock.newCondition();
        this.status = DISCONNECTED;
        this.reconnectWaiter = new CompletableFuture<>();
        reconnectWaiter.complete(Boolean.TRUE);

        // seeded from the options; two listeners sharing an id means the last one wins
        this.connectionListeners = new ConcurrentHashMap<>();
        for (ConnectionListener cl : options.getConnectionListeners()) {
            addConnectionListener(cl);
        }

        this.errorListeners = new ConcurrentHashMap<>();
        for (ErrorListener el : options.getErrorListeners()) {
            addErrorListener(el);
        }

        this.dispatchers = ConcurrentHashMap.newKeySet();
        this.subscriptions = new ConcurrentHashMap<>();
        this.responsesAwaiting = new ConcurrentHashMap<>();
        this.responsesRespondedTo = new ConcurrentHashMap<>();
        this.serverAuthErrors = new ConcurrentHashMap<>();

        this.nextSid = new AtomicLong(1);
        this.nuid = new NUID();
        this.mainInbox = createInbox() + ".*";

        this.lastError = new AtomicReference<>();
        this.connectError = new AtomicReference<>();

        this.serverInfo = new AtomicReference<>(ServerInfo.EMPTY_INFO); // we want serverInfo.get to never return a null
        this.inboxDispatcher = new AtomicReference<>();
        this.inboxDispatcherLock = new ReentrantLock();
        this.pongQueue = new ConcurrentLinkedDeque<>();
        this.draining = new AtomicReference<>();
        this.blockPublishForDrain = new AtomicBoolean();
        this.tryingToConnect = new AtomicBoolean();

        options.incrementExecutorUse();
        this.executor = options.getExecutor();
        this.callbackExecutor = options.getCallbackExecutor();
        this.connectExecutor = options.getConnectExecutor();
        this.readerExecutor = options.getReaderExecutor();
        this.writerExecutor = options.getWriterExecutor();
        this.scheduledExecutor = options.getScheduledExecutor();

        this.reader = new NatsConnectionReader(this);
        this.writer = new NatsConnectionWriter(this);

        this.needPing = new AtomicBoolean(true);

        serverPool = options.getServerPool() == null ? new NatsServerPool() : options.getServerPool();
        serverPool.initialize(options);
        dispatcherFactory = options.getDispatcherFactory() == null ? new DispatcherFactory() : options.getDispatcherFactory();

        switch (options.subjectValidationType()) {
            case None:
                subjectValidator = (subject, required) -> required
                    ? Validator.required(subject, "Subject")
                    : Validator.emptyAsNull(subject);
                replyValidator = (replyTo, required) -> Validator.emptyAsNull(replyTo);
                break;
            case Strict:
                subjectValidator = (subject, required) ->
                    Validator.validateSubjectTermStrict(subject, "Subject", required);
                replyValidator = Validator::validateReplyTo;
                break;
            default:
                subjectValidator = (subject, required) ->
                    Validator.validateSubjectTerm(subject, "Subject", required);
                replyValidator = Validator::validateReplyTo;
                break;
        }
    }

    /**
     * Set the ReadListener, replacing any listener supplied in the {@link Options Options} or set earlier.
     * <p>Unlike {@link #addErrorListener(ErrorListener) addErrorListener()}
     * and {@link #addConnectionListener(ConnectionListener) addConnectionListener()}, there can be only one... read listener.
     * This is intentional, not an oversight. See {@link ReadListener ReadListener}.
     *
     * @param rl the ReadListener, or null to clear the current one
     */
    public void setReadListener(@Nullable ReadListener rl) {
        reader.setReadListener(rl);
    }

    // Connect is only called after creation
    protected void connect(boolean reconnectOnConnect) throws InterruptedException, IOException {
        if (!tryingToConnect.get()) {
            try {
                tryingToConnect.set(true);
                connectImpl(reconnectOnConnect);
            }
            finally {
                tryingToConnect.set(false);
            }
        }
    }

    protected void connectImpl(boolean reconnectOnConnect) throws InterruptedException, IOException {
        if (options.getServers().isEmpty()) {
            throw new IllegalArgumentException("No servers provided in options");
        }

        lastError.set("");

        Set<NatsUri> failList = new HashSet<>();
        boolean keepGoing = true;
        NatsUri first = null;
        NatsUri cur;
        while (keepGoing && (cur = serverPool.peekNextServer()) != null) {
            if (first == null) {
                first = cur;
            }
            else if (cur.equals(first)) {
                break;  // connect only goes through loop once
            }
            serverPool.nextServer(); // b/c we only peeked.

            // let server pool resolve hostnames, then loop through resolved
            List<NatsUri> resolvedList = resolveHost(cur);
            for (NatsUri resolved : resolvedList) {
                if (isClosed()) {
                    keepGoing = false;
                    break;
                }
                connectError.set(""); // new on each attempt

                updateStatus(CONNECTING, resolved, cur);

                tryToConnect(cur, resolved, NatsSystemClock.nanoTime());

                if (isConnected()) {
                    serverPool.connectSucceeded(cur);
                    keepGoing = false;
                    break;
                }

                updateStatus(DISCONNECTED, resolved, cur);

                String err = connectError.get();

                if (isAuthenticationError(err)) {
                    serverAuthErrors.put(resolved, err);
                }
            }

            if (!isConnected() && !isClosed()) {
                failList.add(cur);
                serverPool.connectFailed(cur);
            }
        }

        if (!isConnected() && !isClosed()) {
            if (reconnectOnConnect) {
                reconnectImpl(); // call the impl here otherwise the tryingToConnect guard will block the behavior
            }
            else {
                close(true, false);

                String err = connectError.get();
                if (isAuthenticationError(err)) {
                    throw new AuthenticationException("Authentication error connecting to NATS server: " + err);
                }
                throw new IOException("Unable to connect to NATS servers: " + failList);
            }
        }
    }

    /**
     * Forces reconnect behavior. Stops the current connection including the reading and writing,
     * copies already queued outgoing messages, and then begins the reconnect logic.
     * Does not flush. Does not force close the connection. See {@link ForceReconnectOptions}.
     * @throws IOException the forceReconnect fails
     * @throws InterruptedException the connection is not connected
     */
    public void forceReconnect() throws IOException, InterruptedException {
        forceReconnect(ForceReconnectOptions.DEFAULT_INSTANCE);
    }

    /**
     * Forces reconnect behavior. Stops the current connection including the reading and writing,
     * copies already queued outgoing messages, and then begins the reconnect logic.
     * If options are not provided, the default options are used meaning Does not flush and Does not force close the connection.
     * See {@link ForceReconnectOptions}.
     * @param options options for how the forceReconnect works.
     * @throws IOException the forceReconnect fails
     * @throws InterruptedException the connection is not connected
     */
    public void forceReconnect(ForceReconnectOptions options) throws IOException, InterruptedException {
        if (!tryingToConnect.get()) {
            try {
                tryingToConnect.set(true);
                forceReconnectImpl(options == null ? ForceReconnectOptions.DEFAULT_INSTANCE : options);
            }
            finally {
                tryingToConnect.set(false);
            }
        }
    }

    protected void forceReconnectImpl(@NonNull ForceReconnectOptions frOpts) throws InterruptedException {
        if (frOpts.isFlush()) {
            try {
                flush(frOpts.getFlushWait());
            }
            catch (TimeoutException e) {
                // Ignored. Manual test demonstrates that if the connection is dropped
                // in the middle of the flush, the most likely reason for a TimeoutException,
                // the socket is closed.
            }
        }

        closeSocketLock.lock();
        try {
            updateStatus(DISCONNECTED);

            // Close and reset the current data port and future
            if (dataPortFuture != null) {
                dataPortFuture.cancel(true);
                dataPortFuture = null;
            }

            // Stop i/o BEFORE closing the port. stop(false) clears the reader's running flag without
            // shutting down input, so when the close below unblocks the pending read, the reader sees the
            // resulting IOException as an expected shutdown rather than a communication issue. Both stop
            // methods return a future completed when the thread actually exits; both are initialized to an
            // already-completed future at construction, so joining one that never started returns at once.
            Future<Boolean> readerStopped = reader.stop(false);
            Future<Boolean> writerStopped = writer.stop();

            // close the data port as a task so as not to block reconnecting
            if (dataPort != null) {
                final DataPort dataPortToClose = dataPort;
                dataPort = null;
                executor.submit(() -> {
                    try {
                        if (frOpts.isForceClose()) {
                            dataPortToClose.forceClose();
                        }
                        else {
                            dataPortToClose.close();
                        }
                    }
                    catch (IOException ignore) {
                        // ignored since running as a task and nothing we can do.
                    }
                });
            }

            // Join the i/o threads with the full connection timeout, not a token wait. These reader and
            // writer instances are reused by the reconnect, so a thread that outlives this point can still
            // fire handleCommunicationIssue on the healthy connection and stomp the shared running flag.
            // The close above normally lands sub-millisecond, so these return immediately in the common case.
            long timeoutMillis = options.getConnectionTimeout();
            try {
                readerStopped.get(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            catch (Exception ex) {
                processException(ex);
            }
            try {
                writerStopped.get(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            catch (Exception ex) {
                processException(ex);
            }
        }
        finally {
            closeSocketLock.unlock();
        }

        reconnectImpl();
    }

    protected void reconnect() throws InterruptedException {
        if (!tryingToConnect.get()) {
            try {
                tryingToConnect.set(true);
                reconnectImpl();
            }
            finally {
                tryingToConnect.set(false);
            }
        }
    }

    // Reconnect can only be called when the connection is disconnected
    protected void reconnectImpl() throws InterruptedException {
        if (isClosed()) {
            return;
        }

        if (options.getMaxReconnects() == 0) {
            close(true, false);
            return;
        }

        writer.enterReconnectMode();

        if (!isConnected() && !isClosed() && !isClosing()) {
            reconnectImplConnect();
        }

        if (!isConnected()) {
            close(true, false);
            return;
        }

        subscriptions.forEach((sid, subscription) -> {
            if (subscription.dispatcher == null && !subscription.sub.isDraining()) {
                sendSubscriptionMessage(sid, subscription.sub.getSubject(), subscription.sub.getQueueName(), true);
            }
        });

        dispatchers.forEach(d -> {
            if (!d.isDraining()) {
                d.resendSubscriptions();
            }
        });

        writer.enterWaitingForEndReconnectMode();

        processConnectionEvent(ConnectionEvents.RESUBSCRIBED, uriDetail(currentServer));
    }

    protected void reconnectImplConnect() {
        long round = 0;
        NatsUri first = null;
        NatsUri cur;
        while ((cur = serverPool.nextServer()) != null) {
            if (first == null) {
                first = cur;
                ++round;   // round becomes 1
                // Consume the lame duck signal here whatever the behavior does with it, otherwise a
                // signal received under a behavior that ignores it survives into a later campaign.
                boolean lameDuck = lameDuckTriggered;
                lameDuckTriggered = false;
                if (delayBeforeFirstRound(lameDuck)) {
                    invokeReconnectDelayHandler(round, lameDuck);
                }
            }
            else if (first.equals(cur)) {
                // went around the pool an entire time
                invokeReconnectDelayHandler(++round, false);
            }

            // let server list provider resolve hostnames
            // then loop through resolved
            List<NatsUri> resolvedList = resolveHost(cur);
            for (NatsUri resolved : resolvedList) {
                if (isClosed()) {
                    return;
                }
                connectError.set(""); // reset on each loop
                if (isDisconnectingOrClosed() || isClosing()) {
                    return;
                }
                updateStatus(RECONNECTING, resolved, cur);

                tryToConnect(cur, resolved, NatsSystemClock.nanoTime());

                if (isConnected()) {
                    serverPool.connectSucceeded(cur);
                    statistics.incrementReconnects();
                    return;
                }

                String err = connectError.get();
                if (isAuthenticationError(err)) {
                    if (err.equals(serverAuthErrors.get(resolved))) {
                        return; // double auth error
                    }
                    serverAuthErrors.put(resolved, err);
                }
            }

            if (!isConnected() && !isClosed()) {
                serverPool.connectFailed(cur);
            }
        }
    }

    protected long timeCheck(long endNanos) throws TimeoutException {
        long remainingNanos = endNanos - NatsSystemClock.nanoTime();
        if (remainingNanos < 0) {
            throw new TimeoutException("connection timed out");
        }
        return remainingNanos;
    }

    // is called from reconnect and connect
    // will wait for any previous attempt to complete, using the reader.stop and
    // writer.stop
    protected void tryToConnect(NatsUri cur, NatsUri resolved, long nowNanos) {
        clearCurrentServer();

        try {
            long end = nowNanos + (options.getConnectionTimeout() * NANOS_PER_MILLI);
            timeCheck(end);

            statusLock.lock();
            try {
                if (connecting) {
                    return;
                }
                this.connecting = true;
                statusChanged.signalAll();
            }
            finally {
                statusLock.unlock();
            }

            // Create a new future for the DataPort, the reader/writer will use this
            // to wait for the connect/failure.
            this.dataPortFuture = new CompletableFuture<>();

            // Make sure the reader and writer are stopped
            long timeLeftNanos = timeCheck(end);
            if (reader.isRunning()) {
                reader.stop().get(timeLeftNanos, TimeUnit.NANOSECONDS);
            }
            timeLeftNanos = timeCheck(end);
            if (writer.isRunning()) {
                writer.stop().get(timeLeftNanos, TimeUnit.NANOSECONDS);
            }

            timeCheck(end);
            cleanUpPongQueue();

            timeLeftNanos = timeCheck(end);
            DataPort newDataPort = options.createDataPort();
            newDataPort.connect(this, resolved, timeLeftNanos);

            // Notify any threads waiting on the sockets
            this.dataPort = newDataPort;
            dataPortFuture.complete(dataPort);

            // Wait for the INFO message manually.
            // All other traffic will use the reader and writer
            // TLS First, don't read info until after upgrade
            // ---
            // Also this task does not have any exception catching
            // Since it is submitted as an async task, the future
            // will be aware of any exception thrown, and the future.get()
            // will throw an ExecutionException which is handled futher down
            Callable<Object> connectTask = () -> {
                if (!options.isTlsFirst()) {
                    readInitialInfo();
                    checkVersionRequirements();
                }
                upgradeToSecureIfNeeded(resolved);
                if (options.isTlsFirst()) {
                    readInitialInfo();
                    checkVersionRequirements();
                }
                return null;
            };

            timeLeftNanos = timeCheck(end);
            Future<Object> future = connectExecutor.submit(connectTask);
            try {
                future.get(timeLeftNanos, TimeUnit.NANOSECONDS);
            }
            finally {
                future.cancel(true);
            }

            // start the reader and writer after we secured the connection, if necessary
            timeCheck(end);
            reader.start(dataPortFuture);
            timeCheck(end);
            writer.start(dataPortFuture);

            timeCheck(end);
            sendConnect(resolved);

            timeLeftNanos = timeCheck(end);
            Future<Boolean> pongFuture = sendPing();

            if (pongFuture != null) {
                pongFuture.get(timeLeftNanos, TimeUnit.NANOSECONDS);
            }

            if (pingTask == null) {
                timeCheck(end);
                long pingMillis = options.getPingInterval();
                if (pingMillis > 0) {
                    pingTask = new ScheduledTask(scheduledExecutor, pingMillis, () -> {
                        if (isConnected() && !isClosing()) {
                            try {
                                softPing(); // The timer always uses the standard queue
                            }
                            catch (Exception e) {
                                // it's running in a thread, there is no point throwing here
                            }
                        }
                    });
                }

                long cleanMillis = options.getRequestCleanupInterval();
                if (cleanMillis > 0) {
                    cleanupTask = new ScheduledTask(scheduledExecutor, cleanMillis, () -> cleanResponses(false));
                }
            }

            // Set connected status
            timeCheck(end);
            statusLock.lock();
            try {
                this.connecting = false;

                if (exceptionDuringConnectChange != null) {
                    throw exceptionDuringConnectChange;
                }

                this.currentServer = cur;
                serverAuthErrors.clear(); // reset on successful connection
                updateStatus(CONNECTED); // will signal status change, we also signal in finally
            }
            finally {
                statusLock.unlock();
            }
        }
        catch (Exception exp) {
            processException(exp);
            try {
                // allow force reconnect since this is pretty exceptional,
                // a connection failure while trying to connect
                closeSocket(false, true);
            }
            catch (InterruptedException e) {
                processException(e);
                Thread.currentThread().interrupt();
            }
        }
        finally {
            statusLock.lock();
            try {
                this.connecting = false;
                statusChanged.signalAll();
            }
            finally {
                statusLock.unlock();
            }
        }
    }

    protected void clearCurrentServer() {
        if (currentServer != null) {
            lastServer = currentServer;
        }
        currentServer = null;
    }

    protected void checkVersionRequirements() throws IOException {
        Options opts = getOptions();
        ServerInfo info = getServerInfo();

        if (opts.isNoEcho() && info.getProtocolVersion() < 1) {
            throw new IOException("Server does not support no echo.");
        }
    }

    protected void upgradeToSecureIfNeeded(NatsUri nuri) throws IOException {
        // When already communicating over "https" websocket, do NOT try to upgrade to secure.
        if (!nuri.isWebsocket()) {
            if (options.isTlsFirst()) {
                dataPort.upgradeToSecure();
            }
            else {
                // server    | client options      | result
                // --------- | ------------------- | --------
                // required  | not isTLSRequired() | mismatch
                // available | not isTLSRequired() | ok
                // neither   | not isTLSRequired() | ok
                // required  | isTLSRequired()     | ok
                // available | isTLSRequired()     | ok
                // neither   | isTLSRequired()     | mismatch
                ServerInfo serverInfo = getServerInfo();
                if (options.isTLSRequired()) {
                    if (!serverInfo.isTLSRequired() && !serverInfo.isTLSAvailable()) {
                        throw new IOException("SSL connection wanted by client.");
                    }
                    dataPort.upgradeToSecure();
                }
                else if (serverInfo.isTLSRequired()) {
                    throw new IOException("SSL required by server.");
                }
            }
        }
    }

    // Called from reader/writer thread
    protected void handleCommunicationIssue(Exception io) {
        // If we are connecting or disconnecting, note exception and leave
        statusLock.lock();
        try {
            if (connecting || disconnecting || status == CLOSED || isDraining()) {
                this.exceptionDuringConnectChange = io;
                return;
            }
        }
        finally {
            statusLock.unlock();
        }

        processException(io);
        if (currentServer != null) {
            serverPool.connectFailed(currentServer);
        }

        // Spawn a thread so we don't have timing issues with
        // waiting on read/write threads
        executor.submit(() -> {
            if (!tryingToConnect.get()) {
                try {
                    tryingToConnect.set(true);

                    // any issue that brings us here is pretty serious
                    // so we are comfortable forcing the close
                    closeSocket(true, true);
                }
                catch (InterruptedException e) {
                    processException(e);
                    Thread.currentThread().interrupt();
                }
                finally {
                    tryingToConnect.set(false);
                }
            }
        });
    }

    // Close socket is called when another connect attempt is possible
    // Close is called when the connection should shut down, period
    protected void closeSocket(boolean tryReconnectIfConnected, boolean forceClose) throws InterruptedException {
        // Ensure we close the socket exclusively within one thread.
        closeSocketLock.lock();
        try {
            boolean wasConnected;
            statusLock.lock();
            try {
                if (isDisconnectingOrClosed()) {
                    waitForDisconnectOrClose(options.getConnectionTimeout());
                    return;
                }
                this.disconnecting = true;
                this.exceptionDuringConnectChange = null;
                wasConnected = (status == CONNECTED);

                // Update the status before tearing the socket down, not after. closeSocketImpl
                // clears the current server as its first act and can then block for as long as
                // the reader and writer stop timeouts allow, so updating afterwards leaves a
                // window where the connection reports CONNECTED with a null connected url.
                // This is also the order forceReconnectImpl already uses.
                updateStatus(DISCONNECTED);

                statusChanged.signalAll();
            }
            finally {
                statusLock.unlock();
            }

            closeSocketImpl(forceClose);

            statusLock.lock();
            try {
                this.exceptionDuringConnectChange = null; // Ignore IOExceptions during closeSocketImpl()
                this.disconnecting = false;
                statusChanged.signalAll();
            }
            finally {
                statusLock.unlock();
            }

            if (isClosing()) { // isClosing() means we are in the close method or were asked to be
                close(true, false);
            }
            else if (wasConnected && tryReconnectIfConnected) {
                reconnectImpl(); // call the impl here otherwise the tryingToConnect guard will block the behavior
            }
        }
        finally {
            closeSocketLock.unlock();
        }
    }

    // Close socket is called when another connect attempt is possible
    // Close is called when the connection should shut down, period

    /**
     * Close the connection and release all blocking calls like {@link #flush flush}
     * and {@code Subscription.nextMessage}.
     * If close() is called after {@link #drain(long) drain} it will wait up to the connection timeout
     * to return, but it will not initiate a close. The drain takes precedence and will initiate the close.
     * <p>If the calling thread is interrupted while close is waiting, the close stops waiting,
     * the thread's interrupt status is restored, and the method returns.
     */
    @Override
    public void close() {
        try {
            close(true, false);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt status; do not propagate from close()
        }
    }

    // Several paths reach this (public close(), connect/reconnect failure, closeSocket, drain, final flush),
    // but every caller passes forceClose=false, hence the SameParameterValue suppression.
    @SuppressWarnings("SameParameterValue")
    protected void close(boolean checkDrainStatus, boolean forceClose) throws InterruptedException {
        statusLock.lock();
        try {
            if (checkDrainStatus && isDraining()) {
                waitForDisconnectOrClose(options.getConnectionTimeout());
                return;
            }

            this.closing = true;// We were asked to close, so do it
            if (isDisconnectingOrClosed()) {
                waitForDisconnectOrClose(options.getConnectionTimeout());
                return;
            }
            else {
                this.disconnecting = true;
                this.exceptionDuringConnectChange = null;
                statusChanged.signalAll();
            }
        }
        finally {
            statusLock.unlock();
        }

        // Stop the reconnect wait timer after we stop the writer/reader (only if we are
        // really closing, not on errors)
        if (reconnectWaiter != null) {
            reconnectWaiter.cancel(true);
        }

        closeSocketImpl(forceClose);

        dispatchers.forEach(d -> d.stop(false));

        subscriptions.forEach((sid, subscription) -> subscription.sub.invalidate());

        dispatchers.clear();
        subscriptions.clear();

        if (pingTask != null) {
            pingTask.shutdown();
            pingTask = null;
        }
        if (cleanupTask != null) {
            cleanupTask.shutdown();
            cleanupTask = null;
        }

        cleanResponses(true);

        cleanUpPongQueue();

        statusLock.lock();
        try {
            updateStatus(CLOSED); // will signal, we also signal when we stop disconnecting

            /*
             * if (exceptionDuringConnectChange != null) {
             * processException(exceptionDuringConnectChange); exceptionDuringConnectChange
             * = null; }
             */
        }
        finally {
            statusLock.unlock();
        }

        callbackExecutor = null;
        executor = null;
        connectExecutor = null;
        readerExecutor = null;
        writerExecutor = null;
        scheduledExecutor = null;
        options.shutdownExecutors();

        statusLock.lock();
        try {
            this.disconnecting = false;
            statusChanged.signalAll();
        }
        finally {
            statusLock.unlock();
        }
    }

    // these *ExecutorIsClosed() are only used for tests
    protected boolean callbackExecutorIsClosed() { return callbackExecutor == null; }
    protected boolean executorIsClosed() { return executor == null; }
    protected boolean connectExecutorIsClosed() { return connectExecutor == null; }
    protected boolean readerExecutorIsClosed() { return readerExecutor == null; }
    protected boolean writerExecutorIsClosed() { return writerExecutor == null; }
    protected boolean scheduledExecutorIsClosed() { return scheduledExecutor == null; }

    // Should only be called from closeSocket or close
    protected void closeSocketImpl(boolean forceClose) {
        clearCurrentServer();

        // Signal both to stop.
        final Future<Boolean> readStop = reader.stop();
        final Future<Boolean> writeStop = writer.stop();

        // Now wait until they both stop before closing the socket.
        try {
            readStop.get(1, TimeUnit.SECONDS);
        }
        catch (Exception ex) {
            //
        }
        try {
            writeStop.get(1, TimeUnit.SECONDS);
        }
        catch (Exception ex) {
            //
        }

        // Close and reset the current data port and future
        if (dataPortFuture != null) {
            dataPortFuture.cancel(true);
            dataPortFuture = null;
        }

        // Close the current socket and cancel anyone waiting for it
        try {
            if (dataPort != null) {
                if (forceClose) {
                    dataPort.forceClose();
                }
                else {
                    dataPort.close();
                }
            }

        }
        catch (IOException ex) {
            processException(ex);
        }
        cleanUpPongQueue();

        try {
            reader.stop().get(10, TimeUnit.SECONDS);
        }
        catch (Exception ex) {
            processException(ex);
        }
        try {
            writer.stop().get(10, TimeUnit.SECONDS);
        }
        catch (Exception ex) {
            processException(ex);
        }
    }

    protected void cleanUpPongQueue() {
        Future<Boolean> b;
        while ((b = pongQueue.poll()) != null) {
            b.cancel(true);
        }
    }

    /**
     * Send a message to the specified subject.
     * @param subject the subject to send the message to
     * @param data the message data
     * @throws IllegalArgumentException if the subject is invalid
     */
    public void publish(@NonNull String subject, byte @Nullable [] data) {
        publish(subject, null, null, data, false);
    }

    /**
     * Send a message to the specified subject. The message data <strong>will
     * not</strong> be copied. The expected usage with string content is something
     * like:
     *
     * <pre>
     * nc = Nats.connect()
     * Headers h = new Headers().put("key", "value");
     * nc.publish("destination", h, "message".getBytes("UTF-8"))
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * See {@link #publish(String, String, byte[]) publish()} for more details on
     * publish during reconnect.
     *
     * @param subject the subject to send the message to
     * @param headers Optional headers to publish with the message.
     * @param data the message data
     * @throws IllegalStateException if the reconnect buffer is exceeded
     * @throws IllegalArgumentException if the subject is invalid, or the headers are not supported by the connected server
     */
    public void publish(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data) {
        publish(subject, null, headers, data, false);
    }

    /**
     * Send a request to the specified subject, providing a replyTo subject. The
     * message data <strong>will not</strong> be copied. The expected usage with
     * string content is something like:
     *
     * <pre>
     * nc = Nats.connect()
     * nc.publish("destination", "reply-to", "message".getBytes("UTF-8"))
     * </pre>
     *
     * where the sender creates a byte array immediately before calling publish.
     * <p>
     * During reconnect the client will try to buffer messages. The buffer size is set
     * in the connect options, see {@link OptionsBuilder#reconnectBufferSize(long) reconnectBufferSize()}
     * with a default value of {@link OptionsConstants#DEFAULT_RECONNECT_BUF_SIZE 8 * 1024 * 1024} bytes.
     * If the buffer is exceeded an IllegalStateException is thrown. Applications should use
     * this exception as a signal to wait for reconnect before continuing.
     * </p>
     * @param subject the subject to send the message to
     * @param replyTo the subject the receiver should send any response to
     * @param data the message data
     * @throws IllegalStateException if the reconnect buffer is exceeded
     * @throws IllegalArgumentException if the subject is invalid, or the reply subject is invalid
     */
    public void publish(@NonNull String subject, @Nullable String replyTo, byte @Nullable [] data) {
        publish(subject, replyTo, null, data, false);
    }

    /**
     * Send a message to the specified subject.
     * @param subject the subject to send the message to
     * @param replyTo the subject the receiver should send any response to
     * @param headers Optional headers to publish with the message.
     * @param data the message data
     * @throws IllegalStateException if the reconnect buffer is exceeded
     * @throws IllegalArgumentException if the subject is invalid, or the reply subject is invalid, or the headers are not supported by the connected server
     */
    public void publish(@NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable [] data) {
        subject = subjectValidate(subject);
        replyTo = replyValidate(replyTo);
        validateNotClosed();
        _publish(new InternalPublishableMessage(data, subject, replyTo, headers, false));
    }

    /**
     * Send a message to the specified subject.
     * @param subject the subject to send the message to
     * @param replyTo the subject the receiver should send any response to
     * @param headers Optional headers to publish with the message.
     * @param data the message data
     * @param flushImmediatelyAfterPublish whether to flush the outgoing buffer immediately after publishing
     * @throws IllegalArgumentException if the subject is invalid, or the reply subject is invalid, or the headers are not supported by the connected server
     */
    public void publish(@NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable [] data, boolean flushImmediatelyAfterPublish) {
        subject = subjectValidate(subject);
        replyTo = replyValidate(replyTo);
        validateNotClosed();
        _publish(new InternalPublishableMessage(data, subject, replyTo, headers, flushImmediatelyAfterPublish));
    }

    /**
     * Send a message.
     * @param message the message to send
     * @throws IllegalArgumentException if the message is null, the subject or reply subject is invalid, or the headers are not supported by the connected server
     */
    public void publish(@NonNull NatsMessage message) {
        Validator.validateNotNull(message, "Message");
        subjectValidate(message.getSubject());
        replyValidate(message.getReplyTo());
        validateNotClosed();
        _publish(new InternalPublishableMessage(message, false));
    }

    /**
     * Send a message.
     * @param message the message to send
     * @param flushImmediatelyAfterPublish whether to flush the outgoing buffer immediately after publishing
     * @throws IllegalArgumentException if the message is null, the subject or reply subject is invalid, or the headers are not supported by the connected server
     */
    public void publish(@NonNull NatsMessage message, boolean flushImmediatelyAfterPublish) {
        Validator.validateNotNull(message, "Message");
        subjectValidate(message.getSubject());
        replyValidate(message.getReplyTo());
        validateNotClosed();
        _publish(new InternalPublishableMessage(message, flushImmediatelyAfterPublish));
    }

    // MUST CALL validateNotClosed(); BEFORE CALLING THIS
    private void _publish(InternalPublishableMessage ipm) {
        if (ipm.hasHeaders && !serverInfo.get().isHeadersSupported()) {
            throw new IllegalArgumentException("Headers are not supported by the server, version: " + serverInfo.get().getVersion());
        }

        if ((status == RECONNECTING || status == DISCONNECTED)
            && !writer.canQueueDuringReconnect(ipm)) {
            throw new IllegalStateException(
                "Unable to queue any more messages during reconnect, max buffer is " + options.getReconnectBufferSize());
        }

        queueOutgoing(ipm);
    }

    private void validateNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
        else if (blockPublishForDrain.get()) {
            throw new IllegalStateException("NatsConnection is Draining"); // Ok to publish while waiting on subs
        }
    }

    /**
     * Create a synchronous subscription to the specified subject.
     *
     * <p>Use the {@code Subscription.nextMessage}
     * method to read messages for this subscription.
     *
     * <p>See {@link #createDispatcher(MessageHandler) createDispatcher} for
     * information about creating an asynchronous subscription with callbacks.
     *
     * <p>This method will throw an IllegalArgumentException if the subject is invalid.
     *
     * @param subject the subject to subscribe to
     * @return an object representing the subscription
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public NatsSubscription subscribe(@NonNull String subject) {
        subjectValidate(subject);
        return _createSubscriptionByFactory(subject, null, null, null);
    }

    /**
     * Create a synchronous subscription to the specified subject and queue.
     *
     * <p>Use the {@code Subscription.nextMessage} method to read
     * messages for this subscription.
     *
     * <p>See {@link #createDispatcher(MessageHandler) createDispatcher} for
     * information about creating an asynchronous subscription with callbacks.
     *
     * <p>This method will throw an IllegalArgumentException if the subject is invalid.
     *
     * @param subject the subject to subscribe to
     * @param queueName the queue group to join
     * @return an object representing the subscription
     * @throws IllegalArgumentException if the subject or the queue name is invalid
     */
    @NonNull
    public NatsSubscription subscribe(@NonNull String subject, @NonNull String queueName) {
        subjectValidate(subject);
        Validator.validateQueueName(queueName, true);
        return _createSubscriptionByFactory(subject, queueName, null, null);
    }

    protected void invalidate(NatsSubscription sub) {
        remove(sub);
        sub.invalidate();
    }

    protected void remove(NatsSubscription sub) {
        SubscriptionInfo subscription = subscriptions.remove(sub.getSID());

        if (subscription != null && subscription.dispatcher != null) {
            subscription.dispatcher.remove(sub);
        }
    }

    protected void unsubscribe(NatsSubscription sub, int after) {
        if (isClosed()) { // last chance, usually sub will catch this
            throw new IllegalStateException("NatsConnection is Closed");
        }

        if (after <= 0) {
            invalidate(sub); // Will clean it up
        }
        else {
            sub.setUnsubLimit(after);

            if (sub.reachedUnsubLimit()) {
                invalidate(sub); // takes the sid out of subscriptions, not just the sub out of service
            }
        }

        if (!isConnected()) {
            return; // We will set up sub on reconnect or ignore
        }

        sendUnsub(sub, after);
    }

    protected void sendUnsub(@NonNull NatsSubscription sub, int after) {
        ByteArrayBuilder bab =
            new ByteArrayBuilder().append(UNSUB_SP_BYTES).append(sub.getSID());
        if (after > 0) {
            bab.append(SP).append(after);
        }
        queueOutgoing(new ProtocolMessage(bab, true));
    }

    /**
     * What the connection knows about one sid: the subscription, and the dispatcher delivering to it
     * if there is one. The connection owns this link, so delivery, reconnect and drain never have to
     * ask a subscription what it belongs to.
     */
    protected static final class SubscriptionInfo {
        protected final NatsSubscription sub;
        protected final @Nullable NatsDispatcher dispatcher;

        protected SubscriptionInfo(NatsSubscription sub, @Nullable NatsDispatcher dispatcher) {
            this.sub = sub;
            this.dispatcher = dispatcher;
        }
    }

    // Assumes the null/empty checks were handled elsewhere
    @NonNull
    NatsSubscription _createSubscriptionByFactory(@NonNull String subject,
                                                  @Nullable String queueGroup,
                                                  @Nullable NatsDispatcher dispatcher,
                                                  @Nullable NatsSubscriptionFactory factory) {
        if (isClosed()) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
        else if (isDraining() && (dispatcher == null || dispatcher != inboxDispatcher.get())) {
            throw new IllegalStateException("NatsConnection is Draining");
        }

        NatsSubscription sub;
        String sid = getNextSid();

        if (factory == null) {
            sub = new NatsSubscription(sid, subject, queueGroup, this, dispatcher);
        }
        else {
            sub = factory.createNatsSubscription(sid, subject, queueGroup, this, dispatcher);
        }
        subscriptions.put(sid, new SubscriptionInfo(sub, dispatcher));

        sendSubscriptionMessage(sid, subject, queueGroup, false);
        return sub;
    }

    protected String getNextSid() {
        return Long.toString(nextSid.getAndIncrement());
    }

    // The sid comes from the caller so that anything keyed to it - a dispatcher's handler - can be
    // registered before this runs. The put stays ahead of the send for the same reason: the server
    // can push as soon as it has the SUB, and deliverMessage drops a message with no entry here.
    void reSubscribe(String resubSid, NatsSubscription sub, String subject, String queueGroup, @Nullable NatsDispatcher dispatcher) {
        subscriptions.put(resubSid, new SubscriptionInfo(sub, dispatcher));
        sendSubscriptionMessage(resubSid, subject, queueGroup, false);
    }

    protected void sendSubscriptionMessage(String sid, String subject, String queueGroup, boolean treatAsInternal) {
        if (!isConnected()) {
            return; // We will set up sub on reconnect or ignore
        }

        ByteArrayBuilder bab = new ByteArrayBuilder(UTF_8).append(SUB_SP_BYTES).append(subject);
        if (queueGroup != null) {
            bab.append(SP).append(queueGroup);
        }
        bab.append(SP).append(sid);

        // setting this to filter on stop.
        // if it's an "internal" message, it won't be filtered
        // if it's a normal message, the subscription will already be registered
        // and therefore will be re-subscribed after a stop anyway
        ProtocolMessage subMsg = new ProtocolMessage(bab, true);
        if (treatAsInternal) {
            queueInternalOutgoing(subMsg);
        }
        else {
            queueOutgoing(subMsg);
        }
    }

    /**
     * Create a new inbox subject, can be used for directed replies from
     * subscribers. These are guaranteed to be unique, but can be shared and subscribed
     * to by others.
     * @return the inbox
     */
    @NonNull
    public String createInbox() {
        return options.getInboxPrefix() + nuid.next();
    }

    protected int getRespInboxLength() {
        return options.getInboxPrefix().length() + 22 + 1; // 22 for nuid, 1 for .
    }

    protected String createResponseInbox(String inbox) {
        // Substring gets rid of the * [trailing]
        return inbox.substring(0, getRespInboxLength()) + nuid.next();
    }

    // If the inbox is long enough, pull out the end part, otherwise, just use the
    // full thing
    protected String getResponseToken(String responseInbox) {
        int len = getRespInboxLength();
        if (responseInbox.length() <= len) {
            return responseInbox;
        }
        return responseInbox.substring(len);
    }

    protected void cleanResponses(boolean closing) {
        ArrayList<String> toRemove = new ArrayList<>();
        boolean wasInterrupted = false;

        for (Map.Entry<String, NatsRequestCompletableFuture> entry : responsesAwaiting.entrySet()) {
            boolean remove = false;
            NatsRequestCompletableFuture future = entry.getValue();
            if (future.hasExceededTimeout()) {
                remove = true;
                future.cancelTimedOut();
            }
            else if (closing) {
                remove = true;
                future.cancelClosing();
            }
            else if (future.isDone()) {
                // done should have already been removed, not sure if
                // this even needs checking, but it won't hurt
                remove = true;
                try {
                    future.get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // We might have collected some entries already, but were interrupted.
                    // Break out so we finish as quick as possible,
                    // cleanResponses will be called again anyway
                    wasInterrupted = true;
                    break;
                }
                catch (Throwable ignore) {
                }
            }

            if (remove) {
                toRemove.add(entry.getKey());
                statistics.decrementOutstandingRequests();
            }
        }

        for (String key : toRemove) {
            responsesAwaiting.remove(key);
        }

        if (advancedTracking && !wasInterrupted) {
            toRemove.clear(); // we can reuse this but it needs to be cleared
            for (Map.Entry<String, NatsRequestCompletableFuture> entry : responsesRespondedTo.entrySet()) {
                NatsRequestCompletableFuture future = entry.getValue();
                if (future.hasExceededTimeout()) {
                    toRemove.add(entry.getKey());
                    future.cancelTimedOut();
                }
            }

            for (String token : toRemove) {
                responsesRespondedTo.remove(token);
            }
        }
    }

    /**
     * Send a request and returns the reply or null. This version of request is equivalent
     * to calling get on the future returned from {@link #requestAsync(String, byte[]) request()} with
     * the timeout and handling the ExecutionException and TimeoutException.
     *
     * @param subject the subject for the service that will handle the request
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default connection timeout
     * @return the reply message or null if the timeout is reached
     * @throws InterruptedException if one is thrown while waiting, in order to propagate it up
     * @throws IllegalArgumentException if the subject is invalid
     */
    @Nullable
    public Message request(@NonNull String subject, byte @Nullable [] data, long timeoutMillis) throws InterruptedException {
        return request(subject, null, data, timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request and returns the reply or null. This version of request is equivalent
     * to calling get on the future returned from {@link #requestAsync(String, byte[]) request()} with
     * the timeout and handling the ExecutionException and TimeoutException.
     *
     * @param subject the subject for the service that will handle the request
     * @param headers Optional headers to publish with the message.
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default connection timeout
     * @return the reply message or null if the timeout is reached
     * @throws InterruptedException if one is thrown while waiting, in order to propagate it up
     * @throws IllegalArgumentException if the subject is invalid
     */
    @Nullable
    public Message request(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data, long timeoutMillis) throws InterruptedException {
        return request(subject, headers, data, timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request and returns the reply or null. This version of request is equivalent
     * to calling get on the future returned from {@link #requestAsync(String, byte[]) request()} with
     * the timeout and handling the ExecutionException and TimeoutException.
     *
     * <p>The Message object allows you to set a replyTo, but in requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the consumer's reply.</p>
     *
     * @param message the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default connection timeout
     * @return the reply message or null if the timeout is reached
     * @throws InterruptedException if one is thrown while waiting, in order to propagate it up
     * @throws IllegalArgumentException if the message is null, or the subject is invalid
     */
    @Nullable
    public Message request(@NonNull Message message, long timeoutMillis) throws InterruptedException {
        Validator.validateNotNull(message, "Message");
        return request(message.getSubject(), message.getHeaders(), message.getData(), timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request and returns the reply or null. Same as the other request methods except that it lets
     * the caller choose what happens to the outstanding request if no reply arrives in time.
     *
     * @param subject the subject for the service that will handle the request
     * @param headers optional headers to publish with the message
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default connection timeout
     * @param cancelAction what to do with the future if the request is cancelled (cancel, report, or complete)
     * @return the reply message or null if the timeout is reached
     * @throws InterruptedException if one is thrown while waiting, in order to propagate it up
     * @throws IllegalArgumentException if the subject is invalid
     */
    @Nullable
    public Message request(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data, long timeoutMillis, @NonNull CancelAction cancelAction) throws InterruptedException {
        return request(subject, headers, data, timeoutMillis, cancelAction, forceFlushOnRequest);
    }

    /**
     * Send a request and returns the reply or null. This is the core blocking request used by all the
     * other request methods; it publishes, then waits on the future for the reply.
     *
     * @param subject the subject for the service that will handle the request
     * @param headers optional headers to publish with the message
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default connection timeout
     * @param cancelAction what to do with the future if the request is cancelled (cancel, report, or complete)
     * @param flushImmediatelyAfterPublish whether to flush the outgoing buffer immediately after publishing the request
     * @return the reply message or null if the timeout is reached
     * @throws InterruptedException if one is thrown while waiting, in order to propagate it up
     * @throws IllegalArgumentException if the subject is invalid
     */
    @Nullable
    public Message request(@NonNull String subject,
                           @Nullable Headers headers,
                           byte @Nullable [] data,
                           long timeoutMillis,
                           @NonNull CancelAction cancelAction,
                           boolean flushImmediatelyAfterPublish) throws InterruptedException
    {
        CompletableFuture<Message> incoming = requestAsync(subject, headers, data, timeoutMillis, cancelAction, flushImmediatelyAfterPublish);
        try {
            long getMillis = timeoutMillis <= 0 ? getOptions().getConnectionTimeout() : timeoutMillis;
            return incoming.get(getMillis, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException | ExecutionException | CancellationException e) {
            return null;
        }
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * @param subject the subject for the service that will handle the request
     * @param data the content of the message
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject, byte @Nullable [] data) {
        return requestAsync(subject, null, data, -1, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * @param subject the subject for the service that will handle the request
     * @param headers Optional headers to publish with the message.
     * @param data the content of the message
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data) {
        return requestAsync(subject, headers, data, -1, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * @param subject the subject for the service that will handle the request
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject, byte @Nullable [] data, long timeoutMillis) {
        return requestAsync(subject, null, data, timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * @param subject the subject for the service that will handle the request
     * @param data the content of the message
     * @param headers Optional headers to publish with the message.
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject, @Nullable Headers headers, byte @Nullable [] data, long timeoutMillis) {
        return requestAsync(subject, headers, data, timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * <p>The Message object allows you to set a replyTo, but in requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the consumer's reply.</p>
     *
     * @param message the message
     * @param timeoutMillis the time in milliseconds to wait for a response; a value less than 1 uses the default
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the message is null, or the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull Message message, long timeoutMillis) {
        Validator.validateNotNull(message, "Message");
        return requestAsync(message.getSubject(), message.getHeaders(), message.getData(), timeoutMillis, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request. The returned future will be completed when the
     * response comes back.
     *
     * <p>The Message object allows you to set a replyTo, but in requests,
     * the replyTo is reserved for internal use as the address for the
     * server to respond to the client with the consumer's reply.</p>
     *
     * @param message the message
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the message is null, or the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> request(@NonNull Message message) {
        Validator.validateNotNull(message, "Message");
        return requestAsync(message.getSubject(), message.getHeaders(), message.getData(), -1, CancelAction.REPORT, forceFlushOnRequest);
    }

    /**
     * Send a request, returning a future for the response. This is the core request-send used by all the
     * other request / requestAsync methods.
     *
     * @param subject the subject for the service that will handle the request
     * @param headers optional headers to publish with the message
     * @param data the content of the message
     * @param timeoutMillis the time in milliseconds before the outstanding request is cleaned up; a value
     *                       less than 0 uses the request cleanup interval default
     * @param cancelAction what to do with the future if the request is cancelled (cancel, report, or complete)
     * @param flushImmediatelyAfterPublish whether to flush the outgoing buffer immediately after publishing the request
     * @return a Future for the response, which may be cancelled on error or timed out
     * @throws IllegalArgumentException if the subject is invalid
     */
    @NonNull
    public CompletableFuture<Message> requestAsync(@NonNull String subject,
                                                   @Nullable Headers headers,
                                                   byte @Nullable [] data,
                                                   long timeoutMillis,
                                                   @NonNull CancelAction cancelAction,
                                                   boolean flushImmediatelyAfterPublish)
    {
        validateNotClosed();
        subjectValidate(subject);

        if (inboxDispatcher.get() == null) {
            inboxDispatcherLock.lock();
            try {
                if (inboxDispatcher.get() == null) {
                    NatsDispatcher d = dispatcherFactory.createDispatcher(this, this::deliverReply);

                    // Ensure the dispatcher is started before publishing messages
                    dispatchers.add(d);
                    d.start();
                    d.subscribe(mainInbox);
                    inboxDispatcher.set(d);
                }
            }
            finally {
                inboxDispatcherLock.unlock();
            }
        }

        String responseInbox = createResponseInbox(mainInbox);
        String responseToken = getResponseToken(responseInbox);
        NatsRequestCompletableFuture future =
            new NatsRequestCompletableFuture(cancelAction,
                timeoutMillis <= 0 ? options.getRequestCleanupInterval() : timeoutMillis, options.useTimeoutException());

        responsesAwaiting.put(responseToken, future);
        statistics.incrementOutstandingRequests();

        try {
            _publish(new InternalPublishableMessage(data, subject, responseInbox, headers, flushImmediatelyAfterPublish));
        }
        catch (RuntimeException e) {
            // The publish never made it onto the queue (queue full/busy, or the calling thread was
            // interrupted). Undo the registration so we don't leak a never-completable future waiting
            // for the cleanup timer, then let the caller see the failure.
            responsesAwaiting.remove(responseToken);
            statistics.decrementOutstandingRequests();
            throw e;
        }

        statistics.incrementRequestsSent();

        return future;
    }

    protected void deliverReply(Message msg) {
        String subject = msg.getSubject();
        String key = getResponseToken(subject);
        NatsRequestCompletableFuture f = responsesAwaiting.remove(key);
        if (f != null) {
            if (advancedTracking) {
                responsesRespondedTo.put(key, f);
            }
            statistics.decrementOutstandingRequests();
            if (msg.isStatusMessage() && msg.getStatus().getCode() == 503) {
                switch (f.getCancelAction()) {
                    case COMPLETE:
                        f.complete(msg);
                        break;
                    case REPORT:
                        f.completeExceptionally(new StatusException(msg.getStatus()));
                        break;
                    case CANCEL:
                    default:
                        f.cancel(true);
                }
            }
            else {
                f.complete(msg);
            }
            statistics.incrementRepliesReceived();
        }
        else if (!subject.startsWith(mainInbox)) {
            if (advancedTracking) {
                if (responsesRespondedTo.get(key) != null) {
                    statistics.incrementDuplicateRepliesReceived();
                }
                else {
                    statistics.incrementOrphanRepliesReceived();
                }
            }
        }
    }

    /**
     * Convenience method to create a dispatcher with no default handler. Only used
     * with JetStream push subscriptions that require specific handlers per subscription.
     *
     * @return a new Dispatcher
     */
    @NonNull
    public NatsDispatcher createDispatcher() {
        return createDispatcher(null);
    }

    /**
     * Create a {@code Dispatcher} for this connection. The dispatcher can group one
     * or more subscriptions into a single callback thread. All messages go to the
     * same {@code MessageHandler}.
     *
     * <p>Use the Dispatcher's {@link Dispatcher#subscribe(String)} and
     * {@link Dispatcher#subscribe(String, String)} methods to add subscriptions.
     *
     * <pre>
     * nc = Nats.connect()
     * d = nc.createDispatcher((m) -&gt; System.out.println(m)).subscribe("hello");
     * </pre>
     *
     * @param handler The target for the messages. If the handler is null, subscribing without
     *                using its API that accepts a handler will discard messages.
     * @return a new Dispatcher
     */
    @NonNull
    public NatsDispatcher createDispatcher(@Nullable MessageHandler handler) {
        if (isClosed()) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
        else if (isDraining()) {
            throw new IllegalStateException("NatsConnection is Draining");
        }

        NatsDispatcher dispatcher = dispatcherFactory.createDispatcher(this, handler);
        dispatchers.add(dispatcher);
        dispatcher.start();
        return dispatcher;
    }

    /**
     * Close a dispatcher. This will unsubscribe any subscriptions and stop the delivery thread.
     *
     * <p>Once closed the dispatcher will throw an exception on subsequent subscribe or unsubscribe calls.
     *
     * @param d the dispatcher to close
     * @throws IllegalArgumentException if the dispatcher was not created by this connection, or has already been closed
     */
    public void closeDispatcher(@NonNull Dispatcher d) {
        if (isClosed()) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
        else if (!(d instanceof NatsDispatcher)) {
            throw new IllegalArgumentException("NatsConnection can only manage its own dispatchers");
        }

        NatsDispatcher nd = (NatsDispatcher) d;

        if (nd.isDraining()) {
            return; // No op while draining
        }

        if (!dispatchers.contains(nd)) {
            throw new IllegalArgumentException("Dispatcher is already closed.");
        }

        cleanupDispatcher(nd);
    }

    protected void cleanupDispatcher(NatsDispatcher nd) {
        nd.stop(true);
        dispatchers.remove(nd);
    }

    protected Set<Dispatcher> getDispatchers() {
        return Collections.unmodifiableSet(dispatchers);
    }

    /**
     * Attach another ConnectionListener. Adding a listener whose id is already registered replaces it.
     *
     * <p>The ConnectionListener will only receive NatsConnection events arriving after it has been attached.  When
     * a NatsConnection event is raised, the invocation order and parallelism of multiple ConnectionListeners is not
     * specified.
     *
     * <p>Listeners attached here are not written back to the Options - another connection built from the same
     * Options starts with only the listeners the Options carries.
     *
     * @param connectionListener the ConnectionListener to attach
     */
    public void addConnectionListener(@NonNull ConnectionListener connectionListener) {
        connectionListeners.put(connectionListener.getConnectionListenerId(), connectionListener);
    }

    /**
     * Detach a ConnectionListener. This will cease delivery of any further NatsConnection events to this instance.
     *
     * @param connectionListener the ConnectionListener to detach
     */
    public void removeConnectionListener(@NonNull ConnectionListener connectionListener) {
        connectionListeners.remove(connectionListener.getConnectionListenerId());
    }

    /**
     * Detach the ConnectionListener registered under the given id. See {@link ConnectionListener#getConnectionListenerId()}.
     * Removing an id that is not registered is a no-op.
     *
     * @param id the id of the ConnectionListener to detach
     */
    public void removeConnectionListenerById(@NonNull String id) {
        connectionListeners.remove(id);
    }

    /**
     * Attach an ErrorListener, which will receive error events for this connection in addition to any
     * supplied in the {@link Options Options}. Adding a listener whose id is already registered replaces it.
     *
     * <p>The ErrorListener will only receive events arriving after it has been attached. When an event is
     * raised, the invocation order and parallelism of multiple ErrorListeners is not specified.
     *
     * <p>Listeners attached here are not written back to the Options - another connection built from the same
     * Options starts with only the listeners the Options carries.
     *
     * @param errorListener the ErrorListener to attach
     */
    public void addErrorListener(@NonNull ErrorListener errorListener) {
        errorListeners.put(errorListener.getErrorListenerId(), errorListener);
    }

    /**
     * Detach an ErrorListener. This will cease delivery of any further error events to this instance.
     *
     * @param errorListener the ErrorListener to detach
     */
    public void removeErrorListener(@NonNull ErrorListener errorListener) {
        errorListeners.remove(errorListener.getErrorListenerId());
    }

    /**
     * Detach the ErrorListener registered under the given id. See {@link ErrorListener#getErrorListenerId()}.
     * Removing an id that is not registered is a no-op.
     *
     * @param id the id of the ErrorListener to detach
     */
    public void removeErrorListenerById(@NonNull String id) {
        errorListeners.remove(id);
    }

    /**
     * Flush the connection's buffer of outgoing messages, including sending a
     * protocol message to and from the server.
     * If called while the connection is closed, this method will immediately
     * throw a TimeoutException, regardless of the timeout.
     * If called while the connection is disconnected due to network issues this
     * method will wait for up to the timeout for a reconnect or close.
     *
     * @param timeoutMillis the time in milliseconds to wait for the flush to succeed; pass 0 (or a negative value) to wait forever.
     * @throws TimeoutException if the timeout is exceeded
     * @throws InterruptedException if the underlying thread is interrupted
     */
    public void flush(long timeoutMillis) throws TimeoutException, InterruptedException {
        // The timeout comes in as millis, but we operate in nanos: we do work below
        // (waitForConnectOrClose, then sendPing) before blocking on the future, so we
        // recompute the remaining time right at the get() call for a precise wait.
        long startNanos = NatsSystemClock.nanoTime();
        boolean forever = timeoutMillis <= 0; // 0 (or negative) waits forever
        long timeoutNanos = forever ? 0 : timeoutMillis * NANOS_PER_MILLI;
        waitForConnectOrClose(forever ? 0 : timeoutMillis);

        if (isClosed()) {
            throw new TimeoutException("Attempted to flush while closed");
        }

        if (!forever && NatsSystemClock.nanoTime() - startNanos >= timeoutNanos) {
            throw new TimeoutException("Timeout out waiting for connection before flush.");
        }

        try {
            Future<Boolean> waitForIt = sendPing();

            if (waitForIt == null) { // error in the send ping code
                return;
            }

            if (forever) {
                waitForIt.get();
            }
            else {
                // recompute the time left here, after the work above, so the future waits for a precise remainder
                long remainingNanos = timeoutNanos - (NatsSystemClock.nanoTime() - startNanos);
                if (remainingNanos <= 0) {
                    remainingNanos = 1; // let the future timeout if it isn't resolved
                }
                waitForIt.get(remainingNanos, TimeUnit.NANOSECONDS);
            }

            statistics.incrementFlushCounter();
        }
        catch (ExecutionException | CancellationException e) {
            throw new TimeoutException(e.toString());
        }
    }

    protected void sendConnect(NatsUri nuri) throws IOException {
        try {
            ServerInfo info = serverInfo.get();
            // This is changed - we used to use info.isAuthRequired(), but are changing it to
            // better match older versions of the server. It may change again in the future.
            CharBuffer connectOptions = options.buildProtocolConnectOptionsString(
                nuri.toString(), true, info.getNonce());
            ByteArrayBuilder bab =
                new ByteArrayBuilder(OP_CONNECT_SP_LEN + connectOptions.limit(), UTF_8)
                    .append(CONNECT_SP_BYTES).append(connectOptions);
            queueInternalOutgoing(new ProtocolMessage(bab, false));
        }
        catch (Exception exp) {
            throw new IOException("Error sending connect string", exp);
        }
    }

    protected CompletableFuture<Boolean> sendPing() {
        return sendPing(true);
    }

    protected void softPing() {
        sendPing(false);
    }

    /**
     * Calculates the round trip time between this client and the server.
     * @return the RTT as a duration
     * @throws IOException various IO exception such as timeout or interruption
     */
    @NonNull
    public Duration RTT() throws IOException {
        if (!isConnected()) {
            throw new IOException("Must be connected to do RTT.");
        }

        long timeout = options.getConnectionTimeout();
        CompletableFuture<Boolean> pongFuture = new CompletableFuture<>();
        pongQueue.add(pongFuture);
        try {
            long time = NatsSystemClock.nanoTime();
            writer.queue(new ProtocolMessage(PING_PROTO));
            pongFuture.get(timeout, TimeUnit.MILLISECONDS);
            return Duration.ofNanos(NatsSystemClock.nanoTime() - time);
        }
        catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        catch (TimeoutException e) {
            throw new IOException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    // Send a ping request and push a pong future on the queue.
    // Futures are completed in order, keep this one if a thread wants to wait
    // for a specific pong. Note, if no pong returns, the wait will not return
    // without setting a timeout.
    @Nullable
    protected CompletableFuture<Boolean> sendPing(boolean treatAsInternal) {
        if (!isConnectedOrConnecting()) {
            CompletableFuture<Boolean> retVal = new CompletableFuture<>();
            retVal.complete(Boolean.FALSE);
            return retVal;
        }

        if (!treatAsInternal && !needPing.get()) {
            CompletableFuture<Boolean> retVal = new CompletableFuture<>();
            retVal.complete(Boolean.TRUE);
            needPing.set(true);
            return retVal;
        }

        int max = options.getMaxPingsOut();
        if (max > 0 && pongQueue.size() + 1 > max) {
            handleCommunicationIssue(new IllegalStateException("Max outgoing Ping count exceeded."));
            return null;
        }

        CompletableFuture<Boolean> pongFuture = new CompletableFuture<>();
        pongQueue.add(pongFuture);

        if (treatAsInternal) {
            queueInternalOutgoing(new ProtocolMessage(PING_PROTO));
        }
        else {
            queueOutgoing(new ProtocolMessage(PING_PROTO));
        }

        needPing.set(true);
        statistics.incrementPingCount();
        return pongFuture;
    }

    // This is a minor speed / memory enhancement.
    // We can't reuse the same instance of any NatsMessage b/c of the "NatsMessage next" state,
    // but it is safe to share the data bytes and the size since those fields are just being read
    // This constructor "ProtocolMessage(ProtocolMessage pm)" shares the data and size
    // reducing allocation of data for something that is often created and used.
    // These static instances are the ones that are used for copying in sendPing and sendPong
    protected static final ProtocolMessage PING_PROTO = new ProtocolMessage(OP_PING_BYTES, true);
    protected static final ProtocolMessage PONG_PROTO = new ProtocolMessage(OP_PONG_BYTES, true);

    protected void sendPong() {
        queueInternalOutgoing(new ProtocolMessage(PONG_PROTO));
    }

    // Called by the reader
    protected void handlePong() {
        CompletableFuture<Boolean> pongFuture = pongQueue.pollFirst();
        if (pongFuture != null) {
            pongFuture.complete(Boolean.TRUE);
        }
    }

    protected void readInitialInfo() throws IOException {
        byte[] readBuffer = new byte[options.getBufferSize()];
        ByteBuffer protocolBuffer = ByteBuffer.allocate(options.getBufferSize());
        boolean gotCRLF = false;
        boolean gotCR = false;

        while (!gotCRLF) {
            int read = dataPort.read(readBuffer, 0, readBuffer.length);

            if (read < 0) {
                break;
            }

            int i = 0;
            while (i < read) {
                byte b = readBuffer[i++];

                if (gotCR) {
                    if (b != LF) {
                        throw new IOException("Missed LF after CR waiting for INFO.");
                    }
                    else if (i < read) {
                        throw new IOException("Read past initial info message.");
                    }

                    gotCRLF = true;
                    break;
                }

                if (b == CR) {
                    gotCR = true;
                }
                else {
                    if (!protocolBuffer.hasRemaining()) {
                        protocolBuffer = enlargeBuffer(protocolBuffer); // just double it
                    }
                    protocolBuffer.put(b);
                }
            }
        }

        if (!gotCRLF) {
            throw new IOException("Failed to read initial info message.");
        }

        protocolBuffer.flip();

        String infoJson = UTF_8.decode(protocolBuffer).toString();
        infoJson = infoJson.trim();
        String[] msg = infoJson.split("\\s");
        String op = msg[0].toUpperCase();

        if (!OP_INFO.equals(op)) {
            throw new IOException("Received non-info initial message.");
        }

        handleInfo(infoJson);
    }

    protected void handleInfo(String infoJson) {
        ServerInfo newServerInfo = new ServerInfo(infoJson);
        serverInfo.set(newServerInfo);

        List<String> urls = newServerInfo.getConnectURLs();
        if (!urls.isEmpty()) {
            if (serverPool.acceptDiscoveredUrls(urls)) {
                processConnectionEvent(ConnectionEvents.DISCOVERED_SERVERS, urls.toString());
            }
        }

        if (newServerInfo.isLameDuckMode()) {
            processConnectionEvent(ConnectionEvents.LAME_DUCK, uriDetail(currentServer));
            this.lameDuckTriggered = true;
        }
    }

    protected void validatePayloadAndControlLineSizes(NatsMessage msg) {
        if (options.clientSideLimitChecks()) {
            if (getMaxPayload() > 0 && msg.getPayloadSize() > getMaxPayload()) {
                throw new IllegalArgumentException(
                    "Message payload size exceed server configuration " + msg.getPayloadSize() + " vs " + getMaxPayload());
            }
            if (msg.getControlLineLength() > options.getMaxControlLine()) {
                throw new IllegalArgumentException("Control line is too long");
            }
        }
    }

    protected void queueOutgoing(NatsMessage msg) {
        validatePayloadAndControlLineSizes(msg);
        if (!writer.queue(msg)) {
            notifyErrorListener((c, el) -> el.messageDiscarded(c, msg));
        }
    }

    protected void queueInternalOutgoing(NatsMessage msg) {
        validatePayloadAndControlLineSizes(msg);
        writer.queueInternalMessage(msg);
    }

    protected void deliverMessage(NatsMessage msg) {
        needPing.set(false);
        statistics.incrementIn(msg.getSizeInBytes());

        SubscriptionInfo subscription = subscriptions.get(msg.getSID());

        if (subscription != null) {
            NatsSubscription sub = subscription.sub;
            msg.setSubscription(sub);

            NatsDispatcher d = subscription.dispatcher;
            NatsMessageSink s = (d == null) ? sub : d;
            // The only read of the queue reference on this path: q is what is judged below and q is
            // what is pushed to, so an invalidate() on another thread cannot change it underneath
            // the decision.
            ConsumerMessageQueue q = ((d == null) ? sub.getMessageQueue() : d.getMessageQueue());

            switch (s.getDeliverabilityState(q)) {
                case AVAILABLE -> {
                    s.markNotSlow();
                    // the before queue processor contract is to return true if the message is allowed to be queued
                    if (sub.getBeforeQueueProcessor().apply(msg)) {
                        q.push(msg);
                    }
                }
                case FULL -> {
                    // Drop the message and count it
                    statistics.incrementDroppedCount();
                    s.incrementDroppedCount();

                    // Notify the first time
                    if (!s.isMarkedSlow()) {
                        s.markSlow();
                        processSlowConsumer(sub);
                    }
                }
                case NOT_AVAILABLE -> {
                    // The subscription went away between registration and delivery. Count the drop,
                    // but do not mark slow - a subscription that is gone is not a slow consumer.
                    statistics.incrementDroppedCount();
                    s.incrementDroppedCount();
                }
            }
        }
//      else Drop messages we don't have a subscriber for (could be extras on an auto-unsub for example)
    }

    protected void processOK() {
        statistics.incrementOkCount();
    }

    protected void makeCallback(Runnable callback) {
        if (callbackExecutor != null) {
            try {
                callbackExecutor.execute(() -> {
                    try {
                        callback.run();
                    }
                    catch (Exception ex) {
                        statistics.incrementExceptionCount();
                    }
                });
            }
            catch (RejectedExecutionException re) {
                // Timing with shutdown probably, let it go
            }
        }
    }

    /**
     * Report that a subscription or dispatcher has fallen behind its pending limits. Only the first
     * detection is reported until the consumer catches up again.
     * @param subscription the subscription or dispatcher that is behind
     */
    public void processSlowConsumer(Subscription subscription) {
        notifyErrorListener((c, el) -> el.slowConsumerDetected(c, subscription));
    }

    /**
     * Report an exception the client caught and handled, incrementing the exception statistic.
     * @param exp the exception
     */
    public void processException(Exception exp) {
        statistics.incrementExceptionCount();
        notifyErrorListener((c, el) -> el.exceptionOccurred(c, exp));
    }

    /**
     * Handle a -ERR protocol message from the server. The text becomes the last error, and an
     * authentication error is also remembered against the current server so the connect logic can
     * stop retrying a server that will never accept these credentials.
     * @param errorText the error text as sent by the server
     */
    public void processError(String errorText) {
        statistics.incrementErrCount();

        lastError.set(errorText);
        connectError.set(errorText); // even if this isn't during connection, save it just in case

        // If we get an authentication error, save it
        if (isAuthenticationError(errorText) && currentServer != null) {
            serverAuthErrors.put(currentServer, errorText);
        }

        notifyErrorListener((c, el) -> el.errorOccurred(c, errorText));
    }

    /**
     * Picks which {@link ErrorListener} method to invoke, so {@link #notifyErrorListener(ErrorListenerCaller)}
     * can do the fan-out once for every event.
     */
    public interface ErrorListenerCaller {
        /**
         * Invoke the event method on one listener.
         * @param conn the connection the event is for
         * @param el the listener to call
         */
        void call(NatsConnection conn, ErrorListener el);
    }

    /**
     * The single fan-out point for every ErrorListener event. One callback per listener, so a slow or
     * throwing listener cannot block the others.
     * @param elc the call to make on each listener
     */
    public void notifyErrorListener(ErrorListenerCaller elc) {
        for (ErrorListener listener : errorListeners.values()) {
            makeCallback(() -> elc.call(this, listener));
        }
    }

    protected String uriDetail(NatsUri uri) {
        return uri == null ? null : uri.toString();
    }

    protected String uriDetail(NatsUri uri, NatsUri hostOrlast) {
        if (uri != null) {
            if (hostOrlast == null || uri.equals(hostOrlast)) {
                return uri.toString();
            }
            return uri + " [" + hostOrlast + "]";
        }
        return hostOrlast == null ? null : hostOrlast.toString();
    }

    protected void processConnectionEvent(ConnectionEvents type, String uriDetails) {
        long time = System.currentTimeMillis();
        for (ConnectionListener listener : connectionListeners.values()) {
            makeCallback(() -> listener.connectionEvent(this, type, time, uriDetails));
        }
    }

    /**
     * Return the server info object. Will never be null, but will be an instance of {@link ServerInfo#EMPTY_INFO}
     * before a connection is made, and will represent the last connected server once connected and while disconnected
     * until a new connection is made.
     * @return the server information such as id, client info, etc.
     */
    @NonNull
    public ServerInfo getServerInfo() {
        return serverInfo.get();
    }

    /**
     * the InetAddress of client as known by the NATS server, otherwise null.
     * @return the InetAddress
     */
    @Nullable
    public InetAddress getClientInetAddress() {
        try {
            ServerInfo si = getServerInfo();
            return si == ServerInfo.EMPTY_INFO ? null : NatsInetAddress.getByName(si.getClientIp());
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * the read-only options used to create this connection
     * @return the Options
     */
    @NonNull
    public Options getOptions() {
        return options;
    }

    /**
     * a wrapper for useful statistics about the connection
     * @return the Statistics implementation
     */
    @NonNull
    public Statistics getStatistics() {
        return statistics.getStatistics();
    }

    protected StatisticsCollector getStatisticsCollector() {
        return statistics;
    }

    protected DataPort getDataPort() {
        return dataPort;
    }

    // Used for testing
    protected int getSinkCount() {
        return subscriptions.size() + dispatchers.size();
    }

    /**
     * MaxPayload returns the size limit that a message payload can have. This is
     * set by the server configuration and delivered to the client upon connect.
     *
     * @return the maximum size of a message payload
     */
    public long getMaxPayload() {
        ServerInfo info = serverInfo.get();

        if (info == null) {
            return -1;
        }

        return info.getMaxPayload();
    }

    /**
     * Return the list of known server urls, including additional servers discovered
     * after a connection has been established.
     * Will be empty (but not null) before a connection is made and will represent the last connected server while disconnected
     * @return this connection's list of known server URLs
     */
    @NonNull
    public Collection<String> getServers() {
        return serverPool.getServerList();
    }

    protected List<NatsUri> resolveHost(NatsUri nuri) {
        // 1. If the nuri host is not already an ip address
        //      -and- the nuri is not for websocket
        //      -and- the HostnameResolveMode is Resolve
        //    let the pool resolve it.
        HostnameResolveMode resolveMode = options.hostnameResolveMode();
        List<NatsUri> results = new ArrayList<>();
        if (!nuri.hostIsIpAddress()
            && !nuri.isWebsocket()
            && resolveMode.resolve)
        {
            List<String> ips = serverPool.resolveHostToIps(
                nuri.getHost(), resolveMode.maxOneResult, resolveMode.includeIPV6);
            if (ips != null) {
                for (String ip : ips) {
                    try {
                        results.add(nuri.reHost(ip));
                    }
                    catch (URISyntaxException u) {
                        // ??? should never happen
                        throw new RuntimeException(u);
                    }
                }
            }
        }

        // 2. If there were no results,
        //    - host was already an ip address
        //    - host was for websocket
        //    - hostnameResolveMode did not want to be resolved
        //    - pool returned nothing
        //    - resolving failed...
        //    so the list just becomes the original host.
        if (results.isEmpty()) {
            results.add(nuri);
        }
        return results;
    }

    /**
     * the url used for the current connection, or null if disconnected
     * @return the url string
     */
    @Nullable
    public String getConnectedUrl() {
        return currentServer == null ? null : currentServer.toString();
    }

    /**
     * Returns the connection's current status.
     *
     * @return the connection's status
     */
    @NonNull
    public ConnectionStatus getStatus() {
        return status;
    }

    /**
     * the error text from the last error sent by the server to this client
     * @return the last error text
     */
    @Nullable
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Clear the last error from the server
     */
    public void clearLastError() {
        lastError.set(null);
    }

    protected ExecutorService getExecutor() {
        return executor;
    }

    protected ExecutorService getReaderExecutor() {
        return readerExecutor;
    }

    protected ExecutorService getWriterExecutor() {
        return writerExecutor;
    }

    protected ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    protected void updateStatus(ConnectionStatus newStatus) {
        updateStatus(newStatus, uriDetail(currentServer == null ? lastServer : currentServer));
    }

    protected void updateStatus(ConnectionStatus newStatus, NatsUri resolvedUri, NatsUri hostUri) {
        updateStatus(newStatus, uriDetail(resolvedUri, hostUri));
    }

    protected void updateStatus(ConnectionStatus newStatus, String uriDetail) {
        // The status is read under the lock and the event is chosen from the transition this
        // call actually made, not from re-reading the field afterwards. Re-reading raises the
        // event for whatever status another thread has since installed - or none at all, which
        // is how a DISCONNECTED event goes missing when a reconnect follows closely behind.
        ConnectionStatus oldStatus;
        statusLock.lock();
        try {
            oldStatus = status;
            if (oldStatus == CLOSED || newStatus == oldStatus) {
                return;
            }
            this.status = newStatus;
            statusChanged.signalAll();
        }
        finally {
            statusLock.unlock();
        }

        if (newStatus == DISCONNECTED) {
            processConnectionEvent(ConnectionEvents.DISCONNECTED, uriDetail);
        }
        else if (newStatus == CLOSED) {
            processConnectionEvent(ConnectionEvents.CLOSED, uriDetail);
        }
        else if (oldStatus == RECONNECTING && newStatus == CONNECTED) {
            processConnectionEvent(ConnectionEvents.RECONNECTED, uriDetail);
        }
        else if (newStatus == CONNECTED) {
            processConnectionEvent(ConnectionEvents.CONNECTED, uriDetail);
        }
    }

    protected boolean isClosing() {
        return closing;
    }

    protected boolean isClosed() {
        return status == CLOSED;
    }

    protected boolean isConnected() {
        return status == CONNECTED;
    }

    protected boolean isDisconnected() {
        return status == DISCONNECTED;
    }

    protected boolean isConnectedOrConnecting() {
        statusLock.lock();
        try {
            return status == CONNECTED || connecting;
        } finally {
            statusLock.unlock();
        }
    }

    protected boolean isDisconnectingOrClosed() {
        statusLock.lock();
        try {
            return status == CLOSED || disconnecting;
        } finally {
            statusLock.unlock();
        }
    }

    protected boolean isDisconnecting() {
        statusLock.lock();
        try {
            return disconnecting;
        } finally {
            statusLock.unlock();
        }
    }

    protected void waitForDisconnectOrClose(long timeoutMillis) throws InterruptedException {
        waitWhile(timeoutMillis < 0 ? -1 : timeoutMillis * NANOS_PER_MILLI, (Void) -> isDisconnecting() && !isClosed() );
    }

    protected void waitForConnectOrClose(long timeoutMillis) throws InterruptedException {
        waitWhile(timeoutMillis < 0 ? -1 : timeoutMillis * NANOS_PER_MILLI, (Void) -> !isConnected() && !isClosed());
    }

    // operates purely in nanoseconds (precise, monotonic); the millis-facing wrappers above convert once at the edge.
    // < 0 returns immediately, 0 waits forever, > 0 waits up to that many nanos.
    protected void waitWhile(long timeoutNanos, Predicate<Void> waitWhileTrue) throws InterruptedException {
        statusLock.lock();
        try {
            long currentWaitNanos = timeoutNanos;
            long start = NatsSystemClock.nanoTime();
            while (currentWaitNanos >= 0 && waitWhileTrue.test(null)) {
                if (currentWaitNanos > 0) {
                    if (statusChanged.await(currentWaitNanos, TimeUnit.NANOSECONDS) && !waitWhileTrue.test(null)) {
                        break;
                    }
                    long now = NatsSystemClock.nanoTime();
                    currentWaitNanos = currentWaitNanos - (now - start);
                    start = now;

                    if (currentWaitNanos <= 0) {
                        break;
                    }
                }
                else {
                    statusChanged.await();
                }
            }
        }
        finally {
            statusLock.unlock();
        }
    }

    /**
     * Whether the reconnect delay handler is invoked before the first round of a reconnect campaign.
     * Rounds after the first always invoke it. This is the only place {@link ReconnectDelayBehavior}
     * is read, so the setting applies to every handler, including a custom one.
     * @param lameDuck whether the campaign was triggered by a server lame duck signal
     * @return true to invoke the handler before round 1
     */
    private boolean delayBeforeFirstRound(boolean lameDuck) {
        return switch (options.reconnectDelayBehavior()) {
            case BeforeAllRounds -> true;
            case LameDuckAware -> lameDuck;
            default -> false;   // BeforeSubsequentRounds
        };
    }

    protected void invokeReconnectDelayHandler(long round, boolean lameDuckTriggered) {
        long currentWaitMillis = options.getReconnectDelayHandler()
            .getWaitTimeMillis(round, options, serverPool.hasSecureServer(), lameDuckTriggered);

        this.reconnectWaiter = new CompletableFuture<>();

        long start = NatsSystemClock.nanoTime();
        while (currentWaitMillis > 0 && !isDisconnectingOrClosed() && !isConnected() && !reconnectWaiter.isDone()) {
            try {
                reconnectWaiter.get(currentWaitMillis, TimeUnit.MILLISECONDS);
            } catch (Exception exp) {
                // ignore, try to loop again
            }
            long elapsedMillis = (NatsSystemClock.nanoTime() - start) / 1_000_000L;
            currentWaitMillis -= elapsedMillis;
            start = NatsSystemClock.nanoTime();
        }

        reconnectWaiter.complete(Boolean.TRUE);
    }

    protected ByteBuffer enlargeBuffer(ByteBuffer buffer) {
        int current = buffer.capacity();
        int newSize = current * 2;
        ByteBuffer newBuffer = ByteBuffer.allocate(newSize);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    // For testing
    protected NatsConnectionReader getReader() {
        return reader;
    }

    // For testing
    protected NatsConnectionWriter getWriter() {
        return writer;
    }

    // For testing
    protected Future<DataPort> getDataPortFuture() {
        return dataPortFuture;
    }

    protected boolean isDraining() {
        return draining.get() != null;
    }

    protected boolean isDrained() {
        CompletableFuture<Boolean> tracker = draining.get();

        try {
            if (tracker != null && tracker.getNow(false)) {
                return true;
            }
        } catch (Exception e) {
            // These indicate the tracker was cancelled/timed out
        }

        return false;
    }

    /**
     * Drain tells the connection to process in flight messages before closing.
     * Drain initially drains all the sinks, stopping incoming messages.
     * Next, publishing is halted and a flush call is used to insure all published
     * messages have reached the server.
     * Finally, the connection is closed.
     * In order to drain subscribers, an unsub protocol message is sent to the server followed by a flush.
     * These two steps occur before drain returns. The remaining steps occur in a background thread.
     * This method tries to manage the timeout properly, so that if the timeout is 1 second, and the flush
     * takes 100ms, the remaining steps have 900ms in the background thread.
     * The connection will try to let all messages be drained, but when the timeout is reached
     * the connection is closed and any outstanding dispatcher threads are interrupted.
     * A future allows this call to be treated as synchronous or asynchronous as
     * needed by the application. The value of the future will be true if all the subscriptions
     * were drained in the timeout, and false otherwise. The future completes after the connection
     * is closed, so any connection handler notifications will happen before the future completes.
     *
     * @param timeoutMillis The time in milliseconds to wait for the drain to succeed, pass 0 or less to wait
     *                    forever. Drain involves moving messages to and from the server
     *                    so a very short timeout is not recommended. If the timeout is reached before
     *                    the drain completes, the connection is simply closed, which can result in message
     *                    loss.
     * @return A future that can be used to check if the drain has completed
     * @throws InterruptedException if the thread is interrupted
     * @throws TimeoutException if the initial flush times out
     */
    @NonNull
    public CompletableFuture<Boolean> drain(long timeoutMillis) throws TimeoutException, InterruptedException {

        if (isClosing() || isClosed()) {
            throw new IllegalStateException("A connection can't be drained during close.");
        }

        statusLock.lock();
        try {
            if (isDraining()) {
                return draining.get();
            }
            draining.set(new CompletableFuture<>());
        } finally {
            statusLock.unlock();
        }

        final CompletableFuture<Boolean> tracker = draining.get();
        long startNanos = NatsSystemClock.nanoTime();

        // Don't include subscriptions with dispatchers
        HashSet<NatsSubscription> pureSubscriptions = new HashSet<>();
        for (SubscriptionInfo subscription : subscriptions.values()) {
            if (subscription.dispatcher == null) {
                pureSubscriptions.add(subscription.sub);
            }
        }

        final HashSet<NatsMessageSink> sinks = new HashSet<>();
        sinks.addAll(pureSubscriptions);
        sinks.addAll(dispatchers);

        NatsDispatcher inboxer = inboxDispatcher.get();

        if (inboxer != null) {
            sinks.add(inboxer);
        }

        // Stop the sinks NOW so that when this method returns they are blocked
        sinks.forEach((sink) -> {
            sink.markDraining(tracker);
            sink.sendUnsubForDrain();
        });

        try {
            flush(timeoutMillis); // Flush and wait up to the timeout, if this fails, let the caller know
        } catch (Exception e) {
            close(false, false);
            throw e;
        }

        sinks.forEach(NatsMessageSink::markUnsubedForDrain);

        // Wait for the timeout or all sinks are drained
        executor.submit(() -> {
            try {
                long timeoutNanos = timeoutMillis <= 0 ? Long.MAX_VALUE : timeoutMillis * NANOS_PER_MILLI;
                long startTime = System.nanoTime();
                while (NatsSystemClock.nanoTime() - startTime < timeoutNanos && !Thread.interrupted()) {
                    sinks.removeIf(NatsMessageSink::isDrained);
                    if (sinks.isEmpty()) {
                        break;
                    }
                    //noinspection BusyWait
                    Thread.sleep(1); // Sleep 1 milli
                }

                // Stop publishing
                blockPublishForDrain.set(true);

                // One last flush
                if (timeoutMillis <= 0) {
                    flush(0);
                } else {
                    long remainingMillis = timeoutMillis - (NatsSystemClock.nanoTime() - startNanos) / NANOS_PER_MILLI;
                    if (remainingMillis > 0) {
                        flush(remainingMillis);
                    }
                }
                close(false, false); // close the connection after the last flush
                tracker.complete(sinks.isEmpty());
            } catch (TimeoutException e) {
                processException(e);
            } catch (InterruptedException e) {
                processException(e);
                Thread.currentThread().interrupt();
            } finally {
                try {
                    close(false, false);// close the connection after the last flush
                } catch (InterruptedException e) {
                    processException(e);
                    Thread.currentThread().interrupt();
                }
                tracker.complete(false);
            }
        });

        return tracker;
    }

    protected boolean isAuthenticationError(String err) {
        if (err == null) {
            return false;
        }
        err = err.toLowerCase();
        return err.startsWith("user authentication")
            || err.contains("authorization violation")
            || err.startsWith("account authentication expired");
    }

    /**
     * Immediately flushes the underlying connection buffer if the connection is valid.
     * @throws IOException if the connection flush fails
     */
    public void flushBuffer() throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("NatsConnection is not active.");
        }
        writer.flushBuffer();
    }

    /**
     * Get the number of messages in the outgoing queue for this connection.
     * This value is volatile in the sense that it changes often and may be adjusted by more than one message.
     * It changes every time a message is published (put in the outgoing queue)
     * and every time a message is removed from the queue to be written over the socket
     * @return the number of messages in the outgoing queue
     */
    public long outgoingPendingMessageCount() {
        closeSocketLock.lock();
        try {
            return writer == null ? -1 : writer.outgoingPendingMessageCount();
        }
        finally {
            closeSocketLock.unlock();
        }
    }

    /**
     * Get the number of bytes based to be written calculated from the messages in the outgoing queue for this connection.
     * This value is volatile in the sense that it changes often and may be adjusted by more than one message's bytes.
     * It changes every time a message is published (put in the outgoing queue)
     * and every time a message is removed from the queue to be written over the socket
     * @return the number of messages in the outgoing queue
     */
    public long outgoingPendingBytes() {
        closeSocketLock.lock();
        try {
            return writer == null ? -1 : writer.outgoingPendingBytes();
        }
        finally {
            closeSocketLock.unlock();
        }
    }

    /**
     * The default this connection applies to the flushImmediatelyAfterPublish parameter of the request methods,
     * taken from the options at construction. Flushing trades throughput for latency on a single request.
     * @return true if requests flush the outgoing buffer immediately
     */
    public boolean isForceFlushOnRequest() {
        return forceFlushOnRequest;
    }
}
