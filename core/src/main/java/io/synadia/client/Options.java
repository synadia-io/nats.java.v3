package io.synadia.client;

import io.synadia.client.impl.DataPort;
import io.synadia.client.impl.DispatcherFactory;
import io.synadia.client.impl.SocketDataPort;
import io.synadia.client.utils.HttpRequest;
import io.synadia.client.utils.NatsUri;
import org.jspecify.annotations.NonNull;

import javax.net.ssl.SSLContext;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.nats.json.Encoding.*;
import static io.synadia.client.OptionsConstants.*;
import static io.synadia.client.OptionsProperties.createInstanceOf;
import static io.synadia.client.utils.Validator.nullOrEmpty;

/**
 * The Options class specifies the connection options for a new NATs connection, including the default options.
 * Options are created using a {@link OptionsBuilder Builder}.
 * This class and the builder associated with it, is basically a long list of parameters. The documentation attempts
 * to clarify the value of each parameter in place on the builder and here, but it may be easier to read the documentation
 * starting with the {@link OptionsBuilder Builder}, since it has a simple list of methods that configure the connection.
 */
public class Options {
    // ----------------------------------------------------------------------------------------------------
    // NOTE TO DEVS!!! To add an option, you have to address:
    // ----------------------------------------------------------------------------------------------------
    // OptionsConstants * optionally add a default value constant to OptionsContstants
    // OptionsProperties * always add an environment property to OptionsProperties. Constant always starts with PFX, but code accepts without
    // OptionsBuilder
    // PROTOCOL CONNECT OPTION CONSTANTS * not related to options, but here because Options code uses them
    // CLASS VARIABLES * add a variable to the class
    // CONSTRUCTOR * update constructor to ensure new variables are set from builder
    // GETTERS * update getter to be able to retrieve class variable value
    // HELPER FUNCTIONS * just helpers
    // ----------------------------------------------------------------------------------------------------
    // README - if you add a property or change its comment, add it to or update the readme
    // ----------------------------------------------------------------------------------------------------

    // ----------------------------------------------------------------------------------------------------
    // CLASS VARIABLES
    // ----------------------------------------------------------------------------------------------------
    final List<NatsUri> natsServerUris;
    final List<String> unprocessedServers;
    final boolean noRandomize;
    final HostnameResolveMode hostnameResolveMode;
    final SubjectValidationType subjectValidationType;
    final String connectionName;
    final boolean verbose;
    final boolean pedantic;
    final SSLContext sslContext;
    final int maxReconnects;
    final int maxControlLine;
    final long reconnectWait;
    final long reconnectJitter;
    final long reconnectJitterTls;
    final long connectionTimeout;
    final long socketReadTimeout;
    final long socketWriteTimeout;
    final int socketSoLinger;
    final int socketReceiveBufferSize;
    final int socketSendBufferSize;
    final long pingInterval;
    final long requestCleanupInterval;
    final long writeQueuePushTimeout;
    final int maxPingsOut;
    final long reconnectBufferSize;
    final char[] username;
    final char[] password;
    final Supplier<char[]> tokenSupplier;
    final String inboxPrefix;
    final int bufferSize;
    final boolean noEcho;
    final boolean clientSideLimitChecks;
    final boolean supportUTF8Subjects;
    final int maxMessagesInOutgoingQueue;
    final boolean discardMessagesWhenOutgoingQueueFull;
    final boolean ignoreDiscoveredServers;
    final boolean tlsFirst;
    final boolean useTimeoutException;
    final boolean useDispatcherWithExecutor;
    final boolean forceFlushOnRequest;

    final AuthHandler authHandler;
    final ReconnectDelayHandler reconnectDelayHandler;
    final ReconnectDelayBehavior reconnectDelayBehavior;

    final List<ErrorListener> errorListeners;
    final List<ConnectionListener> connectionListeners;
    final ReadListener readListener;
    final StatisticsCollector statisticsCollector;
    final String dataPortType;

    final boolean trackAdvancedStats;

    final ReentrantLock executorsLock;

    final ThreadFactory userConnectThreadFactory;
    final ThreadFactory userCallbackThreadFactory;
    final ThreadFactory userReaderThreadFactory;
    final ThreadFactory userWriterThreadFactory;
    final ScheduledExecutorService userScheduledExecutor;
    final ExecutorService userExecutor;
    final ExecutorService userConnectExecutor;
    final ExecutorService userCallbackExecutor;
    final ExecutorService userReaderExecutor;
    final ExecutorService userWriterExecutor;

    final ServerPool serverPool;
    final DispatcherFactory dispatcherFactory;

    final List<Consumer<HttpRequest>> httpRequestInterceptors;
    final Proxy proxy;

    // these are not final b/c they are lazy initialized
    // and nulled during shutdownInternalExecutors
    ScheduledExecutorService resolvedScheduledExecutor;
    ExecutorService resolvedExecutor;
    ExecutorService resolvedConnectExecutor;
    ExecutorService resolvedCallbackExecutor;
    ExecutorService resolvedReaderExecutor;
    ExecutorService resolvedWriterExecutor;

    // other state variables
    int executorUseCount = 0;

    static class DefaultThreadFactory implements ThreadFactory {
        final String name;
        final AtomicInteger threadNumber;

        public DefaultThreadFactory (String name){
            this.name = name;
            threadNumber = new AtomicInteger(0);
        }

        @Override
		public Thread newThread(@NonNull Runnable r) {
            String threadName = name + ":" + threadNumber.incrementAndGet();
            Thread t = new Thread(r, threadName);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    static class DefaultTokenSupplier implements Supplier<char[]> {
        final char[] token;

        public DefaultTokenSupplier() {
            token = null;
        }

        public DefaultTokenSupplier(char[] token) {
            this.token = token == null || token.length == 0 ? null : token;
        }

        @Override
        public char[] get() {
            return token;
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // BUILDER
    // ----------------------------------------------------------------------------------------------------
    /**
     * Creates a builder for the options in a fluent style
     * @return the builder.
     */
    public static OptionsBuilder builder() {
        return new OptionsBuilder();
    }

    // ----------------------------------------------------------------------------------------------------
    // CONSTRUCTOR
    // ----------------------------------------------------------------------------------------------------
    Options(OptionsBuilder b) {
        this.natsServerUris = Collections.unmodifiableList(b.natsServerUris);
        this.unprocessedServers = Collections.unmodifiableList(b.unprocessedServers);  // exactly how the user gave them
        this.noRandomize = b.noRandomize;
        this.hostnameResolveMode = b.hostnameResolveMode;
        this.subjectValidationType = b.subjectValidationType;
        this.connectionName = b.connectionName;
        this.verbose = b.verbose;
        this.pedantic = b.pedantic;
        this.sslContext = b.sslContext;
        this.maxReconnects = b.maxReconnects;
        this.reconnectWait = b.reconnectWait;
        this.reconnectJitter = b.reconnectJitter;
        this.reconnectJitterTls = b.reconnectJitterTls;
        this.connectionTimeout = b.connectionTimeout;
        this.socketReadTimeout = b.socketReadTimeout;
        this.socketWriteTimeout = b.socketWriteTimeout;
        this.socketSoLinger = b.socketSoLinger;
        this.socketReceiveBufferSize = b.socketReceiveBufferSize;
        this.socketSendBufferSize = b.socketSendBufferSize;
        this.pingInterval = b.pingInterval;
        this.requestCleanupInterval = b.requestCleanupInterval;
        this.writeQueuePushTimeout = b.writeQueuePushTimeout;
        this.maxPingsOut = b.maxPingsOut;
        this.reconnectBufferSize = b.reconnectBufferSize;
        this.username = b.username;
        this.password = b.password;
        this.tokenSupplier = b.tokenSupplier;
        this.maxControlLine = b.maxControlLine;
        this.bufferSize = b.bufferSize;
        this.noEcho = b.noEcho;
        this.clientSideLimitChecks = b.clientSideLimitChecks;
        this.supportUTF8Subjects = b.supportUTF8Subjects;
        this.inboxPrefix = b.inboxPrefix;
        this.maxMessagesInOutgoingQueue = b.maxMessagesInOutgoingQueue;
        this.discardMessagesWhenOutgoingQueueFull = b.discardMessagesWhenOutgoingQueueFull;

        this.authHandler = b.authHandler;
        this.reconnectDelayHandler = b.reconnectDelayHandler;
        this.reconnectDelayBehavior = b.reconnectDelayBehavior;

        this.errorListeners = Collections.unmodifiableList(new ArrayList<>(b.errorListeners));
        this.connectionListeners = Collections.unmodifiableList(new ArrayList<>(b.connectionListeners));
        this.readListener = b.readListener;
        this.statisticsCollector = b.statisticsCollector;
        this.dataPortType = b.dataPortType;
        this.trackAdvancedStats = b.trackAdvancedStats;

        executorsLock = new ReentrantLock();
        this.userConnectThreadFactory = b.userConnectThreadFactory;
        this.userCallbackThreadFactory = b.userCallbackThreadFactory;
        this.userReaderThreadFactory = b.userReaderThreadFactory;
        this.userWriterThreadFactory = b.userWriterThreadFactory;
        this.userScheduledExecutor = b.userScheduledExecutor;
        this.userExecutor = b.userExecutor;
        this.userConnectExecutor = b.userConnectExecutor;
        this.userCallbackExecutor = b.userCallbackExecutor;
        this.userReaderExecutor = b.userReaderExecutor;
        this.userWriterExecutor = b.userWriterExecutor;

        this.httpRequestInterceptors = b.httpRequestInterceptors;
        this.proxy = b.proxy;

        this.ignoreDiscoveredServers = b.ignoreDiscoveredServers;
        this.tlsFirst = b.tlsFirst;
        this.useTimeoutException = b.useTimeoutException;
        this.useDispatcherWithExecutor = b.useDispatcherWithExecutor;
        this.forceFlushOnRequest = b.forceFlushOnRequest;

        this.serverPool = b.serverPool;
        this.dispatcherFactory = b.dispatcherFactory;
    }

    // ----------------------------------------------------------------------------------------------------
    // GETTERS
    // ----------------------------------------------------------------------------------------------------
    /**
     * Get the general executor
     * @return the executor, see {@link OptionsBuilder#executor(ExecutorService) executor()} in the builder doc
     */
    public ExecutorService getExecutor() {
        executorsLock.lock();
        try {
            if (resolvedExecutor == null || resolvedExecutor.isShutdown()) {
                resolvedExecutor = userExecutor == null ? _getInternalExecutor() : userExecutor;
            }
            return resolvedExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    private ExecutorService _getInternalExecutor() {
        String threadPrefix = nullOrEmpty(this.connectionName) ? DEFAULT_THREAD_NAME_PREFIX : this.connectionName;
        return _getInternalExecutor(new DefaultThreadFactory(threadPrefix));
    }

    // a cached pool that creates a thread per task from the given factory (so each submitted task — e.g.
    // a reader/writer loop that is re-submitted on reconnect — gets its own thread and never serializes
    // behind a not-yet-returned prior task, even when an Options is shared across connections)
    private ExecutorService _getInternalExecutor(ThreadFactory threadFactory) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            500L, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(),
            threadFactory);
    }

    /**
     * Get the ScheduledExecutorService instance
     * @return the ScheduledExecutorService, see {@link OptionsBuilder#scheduledExecutor(ScheduledExecutorService) scheduledExecutor()} in the builder doc
     */
    public ScheduledExecutorService getScheduledExecutor() {
        executorsLock.lock();
        try {
            if (resolvedScheduledExecutor == null || resolvedScheduledExecutor.isShutdown()) {
                resolvedScheduledExecutor = userScheduledExecutor == null ? _getInternalScheduledExecutor() : userScheduledExecutor;
            }
            return resolvedScheduledExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    private ScheduledExecutorService _getInternalScheduledExecutor() {
        String threadPrefix = nullOrEmpty(this.connectionName) ? DEFAULT_THREAD_NAME_PREFIX : this.connectionName;
        // the core pool size of 3 is chosen considering where we know the scheduler is used.
        // 1. Ping timer, 2. cleanup timer, 3. SocketDataPort write-timeout watch
        // Pull message managers also use a scheduler, but we don't even know if this will be consuming
        ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(3, new DefaultThreadFactory(threadPrefix));
        stpe.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        stpe.setRemoveOnCancelPolicy(true);
        return stpe;
    }

    /**
     * the callback executor, see {@link OptionsBuilder#callbackExecutor(ExecutorService) callbackExecutor()}
     * and {@link OptionsBuilder#callbackThreadFactory(ThreadFactory) callbackThreadFactory()} in the builder doc
     * @return the executor
     */
    public ExecutorService getCallbackExecutor() {
        executorsLock.lock();
        try {
            if (resolvedCallbackExecutor == null || resolvedCallbackExecutor.isShutdown()) {
                if (userCallbackExecutor != null) {
                    resolvedCallbackExecutor = userCallbackExecutor;
                }
                else if (userCallbackThreadFactory != null) {
                    resolvedCallbackExecutor = Executors.newSingleThreadExecutor(userCallbackThreadFactory);
                }
                else {
                    resolvedCallbackExecutor = DEFAULT_SINGLE_THREAD_EXECUTOR.get();
                }
            }
            return resolvedCallbackExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * the connect executor, see {@link OptionsBuilder#connectExecutor(ExecutorService) connectExecutor()}
     * and {@link OptionsBuilder#connectThreadFactory(ThreadFactory) connectThreadFactory()} in the builder doc
     * @return the executor
     */
    public ExecutorService getConnectExecutor() {
        executorsLock.lock();
        try {
            if (resolvedConnectExecutor == null || resolvedConnectExecutor.isShutdown()) {
                if (userConnectExecutor != null) {
                    resolvedConnectExecutor = userConnectExecutor;
                }
                else if (userConnectThreadFactory != null) {
                    resolvedConnectExecutor = Executors.newSingleThreadExecutor(userConnectThreadFactory);
                }
                else {
                    resolvedConnectExecutor = DEFAULT_SINGLE_THREAD_EXECUTOR.get();
                }
            }
            return resolvedConnectExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * the reader executor, used to run the connection's reader, see
     * {@link OptionsBuilder#readerExecutor(ExecutorService) readerExecutor()} and
     * {@link OptionsBuilder#readerThreadFactory(ThreadFactory) readerThreadFactory()} in the builder doc.
     * Falls back to the shared connection executor when neither is set.
     * @return the executor
     */
    public ExecutorService getReaderExecutor() {
        if (userReaderExecutor == null && userReaderThreadFactory == null) {
            return getExecutor();
        }
        executorsLock.lock();
        try {
            if (resolvedReaderExecutor == null || resolvedReaderExecutor.isShutdown()) {
                resolvedReaderExecutor = userReaderExecutor != null
                    ? userReaderExecutor
                    : _getInternalExecutor(userReaderThreadFactory);
            }
            return resolvedReaderExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * the writer executor, used to run the connection's writer, see
     * {@link OptionsBuilder#writerExecutor(ExecutorService) writerExecutor()} and
     * {@link OptionsBuilder#writerThreadFactory(ThreadFactory) writerThreadFactory()} in the builder doc.
     * Falls back to the shared connection executor when neither is set.
     * @return the executor
     */
    public ExecutorService getWriterExecutor() {
        if (userWriterExecutor == null && userWriterThreadFactory == null) {
            return getExecutor();
        }
        executorsLock.lock();
        try {
            if (resolvedWriterExecutor == null || resolvedWriterExecutor.isShutdown()) {
                resolvedWriterExecutor = userWriterExecutor != null
                    ? userWriterExecutor
                    : _getInternalExecutor(userWriterThreadFactory);
            }
            return resolvedWriterExecutor;
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * whether the general executor is the internal one versus a user supplied one
     * @return true if the executor is internal
     */
    public boolean executorIsInternal() {
        return this.userExecutor == null;
    }

    /**
     * whether the scheduled executor is the internal one versus a user supplied one
     * @return true if the executor is internal
     */
    public boolean scheduledExecutorIsInternal() {
        return this.userScheduledExecutor == null;
    }

    /**
     * whether the callback executor is the internal one versus a user supplied one
     * @return true if the executor is internal
     */
    public boolean callbackExecutorIsInternal() {
        return userCallbackExecutor == null && userCallbackThreadFactory == null;
    }

    /**
     * whether the connect executor is the internal one versus a user supplied one
     * @return true if the executor is internal
     */
    public boolean connectExecutorIsInternal() {
        return userConnectExecutor == null && userConnectThreadFactory == null;
    }

    /**
     * whether the reader executor is an internal, dedicated one this Options created (from a supplied
     * reader thread factory) and shuts down — as opposed to a user-supplied executor (caller owns it) or
     * the shared connection executor used when neither is supplied
     * @return true if the reader executor is internal/dedicated
     */
    public boolean readerExecutorIsInternal() {
        return userReaderExecutor == null && userReaderThreadFactory != null;
    }

    /**
     * whether the writer executor is an internal, dedicated one this Options created (from a supplied
     * writer thread factory) and shuts down — as opposed to a user-supplied executor (caller owns it) or
     * the shared connection executor used when neither is supplied
     * @return true if the writer executor is internal/dedicated
     */
    public boolean writerExecutorIsInternal() {
        return userWriterExecutor == null && userWriterThreadFactory != null;
    }

    /**
     * Called by NatsConnection to let the options know the executors are being used
     * Fixes the problem of executors being closed if the actual instance of Options
     * is shared among multiple connections.
     */
    public void incrementExecutorUse() {
        // Lock intentionally used of Atomic to synchronize fully with shutdownExecutors
        executorsLock.lock();
        try {
            executorUseCount++;
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * Shutdown the executors.
     * Will only shut down internal executors, not user executors.
     * Uses the executorUseCount to ensure shared Options doesn't prematurely shut down internal executors
     * @throws InterruptedException if any shutdown was interrupted
     */
    public void shutdownExecutors() throws InterruptedException {
        executorsLock.lock();
        try {
            if (--executorUseCount == 0) {
                // internal here means a dedicated executor built from a user-supplied factory (the
                // no-factory case uses the shared executor handled below); we created those, so we shut them down

                if (resolvedCallbackExecutor != null && callbackExecutorIsInternal()) {
                    // we don't just shutdownNow to give any callbacks a chance to finish
                    ExecutorService es = resolvedCallbackExecutor;
                    resolvedCallbackExecutor = null;
                    es.shutdown();
                    try {
                        //noinspection ResultOfMethodCallIgnored
                        es.awaitTermination(connectionTimeout, TimeUnit.MILLISECONDS);
                    }
                    finally {
                        es.shutdownNow();
                    }
                }

                if (resolvedConnectExecutor != null && connectExecutorIsInternal()) {
                    ExecutorService es = resolvedConnectExecutor;
                    resolvedConnectExecutor = null;
                    es.shutdownNow(); // There's no need to wait...
                }

                if (resolvedExecutor != null && executorIsInternal()) {
                    ExecutorService es = resolvedExecutor;
                    resolvedExecutor = null;
                    es.shutdownNow(); // There's no need to wait...
                }

                if (resolvedScheduledExecutor != null && scheduledExecutorIsInternal()) {
                    ScheduledExecutorService ses = resolvedScheduledExecutor;
                    resolvedScheduledExecutor = null;
                    ses.shutdownNow(); // There's no need to wait...
                }

                if (resolvedReaderExecutor != null && readerExecutorIsInternal()) {
                    ExecutorService es = resolvedReaderExecutor;
                    resolvedReaderExecutor = null;
                    es.shutdownNow(); // There's no need to wait...
                }

                if (resolvedWriterExecutor != null && writerExecutorIsInternal()) {
                    ExecutorService es = resolvedWriterExecutor;
                    resolvedWriterExecutor = null;
                    es.shutdownNow(); // There's no need to wait...
                }
            }
        }
        finally {
            executorsLock.unlock();
        }
    }

    /**
     * the list of HttpRequest interceptors.
     * @return the list
     */
    public List<Consumer<HttpRequest>> getHttpRequestInterceptors() {
        return null == this.httpRequestInterceptors
            ? Collections.emptyList()
            : Collections.unmodifiableList(this.httpRequestInterceptors);
    }

    /**
     * the proxy to used for all sockets.
     * @return the proxy
     */
    public Proxy getProxy() {
        return this.proxy;
    }

    /**
     * the error listeners, empty if none were supplied. See {@link OptionsBuilder#errorListener(ErrorListener...) errorListener()} in the builder doc
     * @return an unmodifiable list of the listeners
     */
    @NonNull
    public List<ErrorListener> getErrorListeners() {
        return this.errorListeners;
    }

    /**
     * the connection listeners, empty if none were supplied. See {@link OptionsBuilder#connectionListener(ConnectionListener...) connectionListener()} in the builder doc
     * @return an unmodifiable list of the listeners
     */
    @NonNull
    public List<ConnectionListener> getConnectionListeners() {
        return this.connectionListeners;
    }

    /**
     * the read listener, or null, see {@link OptionsBuilder#readListener(ReadListener) readListener()} in the builder doc.
     * <p>Singular where {@link #getErrorListeners()} and {@link #getConnectionListeners()} are plural. This is
     * intentional, not an oversight - only one read listener is supported. See {@link ReadListener ReadListener}.
     * @return the listener
     */
    public ReadListener getReadListener() {
        return this.readListener;
    }

    /**
     * the statistics collector, or null, see {@link OptionsBuilder#statisticsCollector(StatisticsCollector) statisticsCollector()} in the builder doc
     * @return the collector
     */
    public StatisticsCollector getStatisticsCollector() {
        return this.statisticsCollector;
    }

    /**
     * the auth handler, or null, see {@link OptionsBuilder#authHandler(AuthHandler) authHandler()} in the builder doc
     * @return the handler
     */
    public AuthHandler getAuthHandler() {
        return this.authHandler;
    }

    /**
     * the reconnection delay handler. Never null — defaults to {@link io.synadia.client.impl.DefaultReconnectDelayHandler#INSTANCE}
     * when no custom handler is supplied via {@link OptionsBuilder#reconnectDelayHandler(ReconnectDelayHandler) reconnectDelayHandler()}.
     * @return the handler, never null
     */
    public ReconnectDelayHandler getReconnectDelayHandler() {
        return this.reconnectDelayHandler;
    }

    /**
     * The reconnect delay behavior. Defaults to {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
     * See {@link OptionsBuilder#reconnectDelayBehavior(ReconnectDelayBehavior) reconnectDelayBehavior()} in the builder doc.
     * @return the behavior, never null
     */
    public ReconnectDelayBehavior reconnectDelayBehavior() {
        return this.reconnectDelayBehavior;
    }

    /**
     * the DataPort class type for connections created by this options object, see {@link OptionsBuilder#dataPortType(String) dataPortType()} in the builder doc
     * @return the DataPort class type
     */
    public String getDataPortType() {
        return this.dataPortType;
    }

    /**
     * create a new instance of the data port described by these options
     * @return the data port
     */
    public DataPort createDataPort() {
        DataPort dp;
        if (dataPortType.equals(DEFAULT_DATA_PORT_TYPE)) {
            dp = new SocketDataPort();
        }
        else {
            dp = (DataPort) createInstanceOf(dataPortType);
        }
        dp.afterConstruct(this);
        return dp;
    }

    /**
     * the servers as configured in options as URI's, see {@link OptionsBuilder#servers(String[]) servers()} in the builder doc
     * @return the processed servers
     */
    public List<URI> getServers() {
        List<URI> list = new ArrayList<>();
        for (NatsUri nuri : natsServerUris) {
            list.add(nuri.getUri());
        }
        return list;
    }

    /**
     * the servers as configured in options as NatsUri's, see {@link OptionsBuilder#servers(String[]) servers()} in the builder doc
     * @return the processed servers
     */
    public List<NatsUri> getNatsServerUris() {
        return natsServerUris;
    }

    /**
     * the servers as given to the options, since the servers are normalized
     * @return the raw servers
     */
    public List<String> getUnprocessedServers() {
        return unprocessedServers;
    }

    /**
     * should we turn off randomization for server connection attempts, see {@link OptionsBuilder#noRandomize() noRandomize()} in the builder doc
     * @return true if we should turn off randomization
     */
    public boolean isNoRandomize() {
        return noRandomize;
    }

    /**
     * Get the Hostname Resolve Mode
     * @return the mode
     */
    public HostnameResolveMode hostnameResolveMode() {
        return hostnameResolveMode;
    }

    /**
     * what type of subject validation should be done
     * @return the configured SubjectValidationType
     */
    public SubjectValidationType subjectValidationType() {
        return subjectValidationType;
    }

    /**
     * the connectionName, see {@link OptionsBuilder#connectionName(String) connectionName()} in the builder doc
     * @return the connectionName
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * are we in verbose mode, see {@link OptionsBuilder#verbose() verbose()} in the builder doc
     * @return true if we are in verbose mode
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * is echo-ing disabled, see {@link OptionsBuilder#noEcho() noEcho()} in the builder doc
     * @return true if echo-ing is disabled
     */
    public boolean isNoEcho() {
        return noEcho;
    }

    /**
     * clientSideLimitChecks
     * @return true if the client will perform limit checks
     */
    public boolean clientSideLimitChecks() {
        return clientSideLimitChecks;
    }

    /**
     * whether utf8 subjects are supported, see {@link OptionsBuilder#supportUTF8Subjects() supportUTF8Subjects()} in the builder doc.
     * @return true if utf8 subjects are supported
     */
    public boolean supportUTF8Subjects() {
        return supportUTF8Subjects;
    }

    /**
     * are we using pedantic protocol, see {@link OptionsBuilder#pedantic() pedantic()} in the builder doc
     * @return true if using pedantic protocol
     */
    public boolean isPedantic() {
        return pedantic;
    }

    /**
     * should we track advanced stats, see {@link OptionsBuilder#turnOnAdvancedStats() turnOnAdvancedStats()} in the builder doc
     * @return true is advance stat tracking is on
     */
    public boolean isTrackAdvancedStats() {
        return trackAdvancedStats;
    }

    /**
     * the maximum length of a control line, see {@link OptionsBuilder#maxControlLine(int) maxControlLine()} in the builder doc
     * @return the maximum length
     */
    public int getMaxControlLine() {
        return maxControlLine;
    }

    /**
     *
     * is there an sslContext for these Options, otherwise false, see {@link OptionsBuilder#secure() secure()} in the builder doc
     * @return true if there is an sslContext
     */
    public boolean isTLSRequired() {
        return sslContext != null;
    }

    /**
     * the sslContext, see {@link OptionsBuilder#secure() secure()} in the builder doc
     * @return the sslContext
     */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /**
     * the maxReconnects attempts to make before failing, see {@link OptionsBuilder#maxReconnects(int) maxReconnects()} in the builder doc
     * @return the maxReconnects attempts
     */
    public int getMaxReconnects() {
        return maxReconnects;
    }

    /**
     * the reconnect wait in milliseconds, used between reconnect attempts, see {@link OptionsBuilder#reconnectWait(long) reconnectWait()} in the builder doc
     * @return the reconnect wait in milliseconds
     */
    public long getReconnectWait() {
        return reconnectWait;
    }

    /**
     * the reconnect jitter in milliseconds, used between reconnect attempts to vary the reconnect wait, see {@link OptionsBuilder#reconnectJitter(long) reconnectJitter()} in the builder doc
     * @return the reconnect jitter in milliseconds
     */
    public long getReconnectJitter() {
        return reconnectJitter;
    }

    /**
     * the reconnect jitter in milliseconds for tls/secure connections, used between reconnect attempts to vary the reconnect wait, see {@link OptionsBuilder#reconnectJitterTls(long) reconnectJitterTls()} in the builder doc
     * @return the reconnect jitter in milliseconds for tls/secure
     */
    public long getReconnectJitterTls() {
        return reconnectJitterTls;
    }

    /**
     * the connectionTimeout in milliseconds, see {@link OptionsBuilder#connectionTimeout(long) connectionTimeout()} in the builder doc
     * @return the connectionTimeout in milliseconds
     */
    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * the socketReadTimeout in milliseconds, see {@link OptionsBuilder#socketReadTimeout(long) socketReadTimeout} in the builder doc
     * @return the socketReadTimeout in milliseconds
     */
    public long getSocketReadTimeout() {
        return socketReadTimeout;
    }

    /**
     * the socketWriteTimeout in milliseconds, see {@link OptionsBuilder#socketWriteTimeout(long) socketWriteTimeout} in the builder doc
     * @return the socketWriteTimeout in milliseconds, {@code <= 0} means disabled
     */
    public long getSocketWriteTimeout() {
        return socketWriteTimeout;
    }

    /**
     * the socket so linger number of seconds, see {@link OptionsBuilder#socketSoLinger(int) socketSoLinger()} in the builder doc
     * @return the socket so linger number of seconds
     */
    public int getSocketSoLinger() {
        return socketSoLinger;
    }

    /**
     * the number of bytes to set the for the SO_RCVBUF property on the socket
     * @return the number of bytes
     */
    public int getSocketReceiveBufferSize() {
        return socketReceiveBufferSize;
    }

    /**
     * the number of bytes to set the for the SO_SNDBUF property on the socket
     * @return the number of bytes
     */
    public int getSocketSendBufferSize() {
        return socketSendBufferSize;
    }

    /**
     * the pingInterval in milliseconds, see {@link OptionsBuilder#pingInterval(long) pingInterval()} in the builder doc
     * @return the interval in milliseconds, {@code <= 0} means disabled
     */
    public long getPingInterval() {
        return pingInterval;
    }

    /**
     * the request cleanup interval in milliseconds, see {@link OptionsBuilder#requestCleanupInterval(long) requestCleanupInterval()} in the builder doc
     * @return the interval in milliseconds
     */
    public long getRequestCleanupInterval() {
        return requestCleanupInterval;
    }

    /**
     * the write queue push timeout in milliseconds, see {@link OptionsBuilder#writeQueuePushTimeout(long) writeQueuePushTimeout()} in the builder doc
     * @return the time in milliseconds given to lock and offer a message to the outgoing queue
     */
    public long getWriteQueuePushTimeout() {
        return writeQueuePushTimeout;
    }

    /**
     * the maxPingsOut to limit the number of pings on the wire, see {@link OptionsBuilder#maxPingsOut(int) maxPingsOut()} in the builder doc
     * @return the max pings out
     */
    public int getMaxPingsOut() {
        return maxPingsOut;
    }

    /**
     * the reconnectBufferSize, to limit the amount of data held during
     * reconnection attempts, see {@link OptionsBuilder#reconnectBufferSize(long) reconnectBufferSize()} in the builder doc
     * @return the reconnectBufferSize
     */
    public long getReconnectBufferSize() {
        return reconnectBufferSize;
    }

    /**
     * the default size for buffers in the connection code, see {@link OptionsBuilder#bufferSize(int) bufferSize()} in the builder doc
     * @return the default size in bytes
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * the username to use for basic authentication, see {@link OptionsBuilder#userInfo(char[], char[]) userInfo()} in the builder doc
     * @return the username
     */
    public char[] getUsername() {
        return username;
    }

    /**
     * the password to use for basic authentication, see {@link OptionsBuilder#userInfo(char[], char[]) userInfo()} in the builder doc
     * @return the password
     */
    public char[] getPassword() {
        return password;
    }

    /**
     * the token to be used for token-based authentication, see {@link OptionsBuilder#token(char[]) token()} in the builder doc
     * generated from the token supplier if the user supplied one.
     * @return the token
     */
    public char[] getToken() {
        return tokenSupplier.get();
    }

    /**
     * the inbox prefix to use for requests, see {@link OptionsBuilder#inboxPrefix(String) inboxPrefix()} in the builder doc
     * @return the inbox prefix
     */
    public String getInboxPrefix() {
        return inboxPrefix;
    }

    /**
     * the maximum number of messages in the outgoing queue, see {@link OptionsBuilder#maxMessagesInOutgoingQueue(int)
     * maxMessagesInOutgoingQueue(int)} in the builder doc
     * @return the maximum number of messages
     */
    public int getMaxMessagesInOutgoingQueue() {
        return maxMessagesInOutgoingQueue;
    }

    /**
     * should we discard messages when the outgoing queue is full, see {@link OptionsBuilder#discardMessagesWhenOutgoingQueueFull()
     * discardMessagesWhenOutgoingQueueFull()} in the builder doc
     * @return true if we should discard messages when the outgoing queue is full
     */
    public boolean isDiscardMessagesWhenOutgoingQueueFull() {
        return discardMessagesWhenOutgoingQueueFull;
    }

    /**
     * Get whether to ignore discovered servers
     * @return the flag
     */
    public boolean isIgnoreDiscoveredServers() {
        return ignoreDiscoveredServers;
    }

    /**
     * Get whether to do tls first
     * @return the flag
     */
    public boolean isTlsFirst() {
        return tlsFirst;
    }

    /**
     * Get whether to throw {@link java.util.concurrent.TimeoutException} on timeout instead of {@link java.util.concurrent.CancellationException}.
     * @return the flag
     */
    public boolean useTimeoutException() {
        return useTimeoutException;
    }

    /**
     * Whether the dispatcher should use an executor to async messages to handlers
     * @return the flag
     */
    public boolean useDispatcherWithExecutor() { return useDispatcherWithExecutor; }

    /**
     * Whether to flush on any user request
     * @return the flag
     */
    public boolean forceFlushOnRequest() {
        return forceFlushOnRequest;
    }

    /**
     * Get the ServerPool implementation. If null, a default implementation is used.
     * @return the ServerPool implementation
     */
    public ServerPool getServerPool() {
        return serverPool;
    }

    /**
     * Get the DispatcherFactory implementation. If null, a default implementation is used.
     * @return the DispatcherFactory implementation
     */
    public DispatcherFactory getDispatcherFactory() {
        return dispatcherFactory;
    }

    /**
     * create a URI from a server uri.
     * @param serverURI the text uri
     * @return the URI object
     * @throws URISyntaxException if the text version is malformed or illegal
     */
    public URI createURIForServer(String serverURI) throws URISyntaxException {
        return new NatsUri(serverURI).getUri();
    }

    // ----------------------------------------------------------------------------------------------------
    // PROTOCOL CONNECT OPTION CONSTANTS
    // ----------------------------------------------------------------------------------------------------
    /** Protocol key {@value}. */
    static final String OPTION_VERBOSE = "verbose";
    /** Protocol key {@value}. */
    static final String OPTION_PEDANTIC = "pedantic";
    /** Protocol key {@value}. */
    static final String OPTION_TLS_REQUIRED = "tls_required";
    /** Protocol key {@value}. */
    static final String OPTION_AUTH_TOKEN = "auth_token";
    /** Protocol key {@value}. */
    static final String OPTION_USER = "user";
    /** Protocol key {@value}. */
    static final String OPTION_PASSWORD = "pass";
    /** Protocol key {@value}. */
    static final String OPTION_NAME = "name";
    /** Protocol key {@value}, will be set to "Java". */
    static final String OPTION_LANG = "lang";
    /** Protocol key {@value}. */
    static final String OPTION_VERSION = "version";
    /** Protocol key {@value}, will be set to 1. */
    static final String OPTION_PROTOCOL = "protocol";
    /** Echo key {@value}, determines if the server should echo to the client. */
    static final String OPTION_ECHO = "echo";
    /** NKey key {@value}, the public key being used for sign-in. */
    static final String OPTION_NKEY = "nkey";
    /** SIG key {@value}, the signature of the nonce sent by the server. */
    static final String OPTION_SIG = "sig";
    /** JWT key {@value}, the user JWT to send to the server. */
    static final String OPTION_JWT = "jwt";
    /** Headers key if headers are supported. */
    static final String OPTION_HEADERS = "headers";
    /** No Responders key if noresponders are supported. */
    static final String OPTION_NORESPONDERS = "no_responders";

    /**
     * Create the options string sent with the connect message.
     * If includeAuth is true the auth information is included:
     * If the server URIs have auth info it is used. Otherwise, the userInfo is used.
     * @param serverURI the current server uri
     * @param includeAuth tells the options to build a connection string that includes auth information
     * @param nonce if the client is supposed to sign the nonce for authentication
     * @return this instance for chaining. String, basically JSON
     */
    public CharBuffer buildProtocolConnectOptionsString(String serverURI, boolean includeAuth, byte[] nonce) {
        CharBuffer connectString = CharBuffer.allocate(this.maxControlLine);
        connectString.append("{");

        appendOption(connectString, OPTION_LANG, Nats.CLIENT_LANGUAGE, true, false);
        appendOption(connectString, OPTION_VERSION, Nats.CLIENT_VERSION, true, true);

        if (this.connectionName != null) {
            appendOption(connectString, OPTION_NAME, this.connectionName, true, true);
        }

        appendOption(connectString, OPTION_PROTOCOL, "1", false, true);

        appendOption(connectString, OPTION_VERBOSE, String.valueOf(this.isVerbose()), false, true);
        appendOption(connectString, OPTION_PEDANTIC, String.valueOf(this.isPedantic()), false, true);
        appendOption(connectString, OPTION_TLS_REQUIRED, String.valueOf(this.isTLSRequired()), false, true);
        appendOption(connectString, OPTION_ECHO, String.valueOf(!this.isNoEcho()), false, true);
        appendOption(connectString, OPTION_HEADERS, "true", false, true);
        appendOption(connectString, OPTION_NORESPONDERS, "true", false, true);

        if (includeAuth) {
            if (nonce != null && this.getAuthHandler() != null) {
                char[] nkey = this.getAuthHandler().getID();
                byte[] sig = this.getAuthHandler().sign(nonce);
                char[] jwt = this.getAuthHandler().getJWT();

                if (sig == null) {
                    sig = new byte[0];
                }

                if (jwt == null) {
                    jwt = new char[0];
                }

                if (nkey == null) {
                    nkey = new char[0];
                }

                String encodedSig = base64UrlEncodeToString(sig);

                appendOption(connectString, OPTION_NKEY, nkey, true);
                appendOption(connectString, OPTION_SIG, encodedSig, true, true);
                appendOption(connectString, OPTION_JWT, jwt, true);
            }

            String uriUser = null;
            String uriPass = null;
            String uriToken = null;

            // Values from URI override options
            try {
                URI uri = this.createURIForServer(serverURI);
                String userInfo = uri.getRawUserInfo();
                if (userInfo != null) {
                    int at = userInfo.indexOf(":");
                    if (at == -1) {
                        uriToken = uriDecode(userInfo);
                    }
                    else {
                        uriUser = uriDecode(userInfo.substring(0, at));
                        uriPass = uriDecode(userInfo.substring(at + 1));
                    }
                }
            }
            catch (URISyntaxException e) {
                // the createURIForServer call is the one that potentially throws this
                // uriUser, uriPass and uriToken will already be null
            }

            if (uriUser != null) {
                appendOption(connectString, OPTION_USER, jsonEncode(uriUser), true, true);
            }
            else if (this.username != null) {
                appendOption(connectString, OPTION_USER, jsonEncode(this.username), true, true);
            }

            if (uriPass != null) {
                appendOption(connectString, OPTION_PASSWORD, jsonEncode(uriPass), true, true);
            }
            else if (this.password != null) {
                appendOption(connectString, OPTION_PASSWORD, jsonEncode(this.password), true, true);
            }

            if (uriToken != null) {
                appendOption(connectString, OPTION_AUTH_TOKEN, uriToken, true, true);
            }
            else {
                char[] token = this.tokenSupplier.get();
                if (token != null) {
                    appendOption(connectString, OPTION_AUTH_TOKEN, token, true);
                }
            }
        }

        connectString.append("}");
        connectString.flip();
        return connectString;
    }

    // ----------------------------------------------------------------------------------------------------
    // HELPER FUNCTIONS
    // ----------------------------------------------------------------------------------------------------
    private static void appendOption(CharBuffer builder, String key, String value, boolean quotes, boolean comma) {
        _appendStart(builder, key, quotes, comma);
        builder.append(value);
        _appendOptionEnd(builder, quotes);
    }

    @SuppressWarnings("SameParameterValue")
    private static void appendOption(CharBuffer builder, String key, char[] value, boolean comma) {
        _appendStart(builder, key, true, comma);
        builder.put(value);
        _appendOptionEnd(builder, true);
    }

    private static void _appendStart(CharBuffer builder, String key, boolean quotes, boolean comma) {
        if (comma) {
            builder.append(',');
        }
        builder.append('"');
        builder.append(key);
        builder.append('"');
        builder.append(':');
        _appendOptionEnd(builder, quotes);
    }

    private static void _appendOptionEnd(CharBuffer builder, boolean quotes) {
        if (quotes) {
            builder.append('"');
        }
    }
}
