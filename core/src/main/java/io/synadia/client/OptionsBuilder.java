package io.synadia.client;

import io.synadia.client.impl.DispatcherFactory;
import io.synadia.client.impl.SSLContextFactory;
import io.synadia.client.impl.SSLContextFactoryProperties;
import io.synadia.client.testutils.HttpRequest;
import io.synadia.client.testutils.NatsUri;
import io.synadia.client.testutils.SSLUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.synadia.client.OptionsConstants.*;
import static io.synadia.client.OptionsProperties.*;
import static io.synadia.client.testutils.NatsConstants.*;
import static io.synadia.client.testutils.SSLUtils.DEFAULT_TLS_ALGORITHM;
import static io.synadia.client.testutils.Validator.emptyAsNull;
import static io.synadia.client.testutils.Validator.emptyOrNullAs;

/**
 * Options are created using a Builder. The builder supports chaining and will
 * create a default set of options if no methods are calls. The builder can also
 * be created from a properties object using the property names defined with the
 * prefix PROP_ in this class.
 * <p>A common usage for testing might be {@code new Options.Builder().server(myserverurl).noReconnect.build()}
 */
public class OptionsBuilder {
    // ----------------------------------------------------------------------------------------------------
    // NOTE TO DEVS!!! To add an option, you have to address:
    // ----------------------------------------------------------------------------------------------------
    // BUILDER VARIABLES * add a variable in builder
    // BUILD CONSTRUCTOR PROPS * update build props constructor to read new props
    // BUILDER METHODS * add a chainable method in builder for new variable
    // BUILD IMPL * update build() implementation if needed
    // BUILDER COPY CONSTRUCTOR * update builder constructor to ensure new variables are set

    // ----------------------------------------------------------------------------------------------------
    // BUILDER VARIABLES
    // ----------------------------------------------------------------------------------------------------
    final List<NatsUri> natsServerUris = new ArrayList<>();
    final List<String> unprocessedServers = new ArrayList<>();
    boolean noRandomize = false;
    HostnameResolveMode hostnameResolveMode = HostnameResolveMode.ResolveToAll;
    SubjectValidationType subjectValidationType = SubjectValidationType.Lenient;
    boolean reportNoResponders = false;
    String connectionName = null; // Useful for debugging -> "test: " + NatsTestServer.currentPort();
    boolean verbose = false;
    boolean pedantic = false;
    SSLContext sslContext = null;
    SSLContextFactory sslContextFactory = null;
    int maxControlLine = DEFAULT_MAX_CONTROL_LINE;
    int maxReconnect = DEFAULT_MAX_RECONNECT;
    Duration reconnectWait = DEFAULT_RECONNECT_WAIT;
    Duration reconnectJitter = DEFAULT_RECONNECT_JITTER;
    Duration reconnectJitterTls = DEFAULT_RECONNECT_JITTER_TLS;
    Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    int socketReadTimeoutMillis = 0;
    Duration socketWriteTimeout = DEFAULT_SOCKET_WRITE_TIMEOUT;
    int socketSoLinger = -1;
    int receiveBufferSize = -1;
    int sendBufferSize = -1;
    Duration pingInterval = DEFAULT_PING_INTERVAL;
    Duration requestCleanupInterval = DEFAULT_REQUEST_CLEANUP_INTERVAL;
    Duration writeQueuePushTimeout = DEFAULT_WRITE_QUEUE_PUSH_TIMEOUT;
    int maxPingsOut = DEFAULT_MAX_PINGS_OUT;
    long reconnectBufferSize = DEFAULT_RECONNECT_BUF_SIZE;
    char[] username = null;
    char[] password = null;
    Supplier<char[]> tokenSupplier = new Options.DefaultTokenSupplier();
    int bufferSize = DEFAULT_BUFFER_SIZE;
    boolean trackAdvancedStats = false;
    boolean traceConnection = false;
    boolean noEcho = false;
    boolean noHeaders = false;
    boolean noNoResponders = false;
    boolean clientSideLimitChecks = true;
    boolean supportUTF8Subjects = false;
    String inboxPrefix = DEFAULT_INBOX_PREFIX;
    int maxMessagesInOutgoingQueue = DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE;
    boolean discardMessagesWhenOutgoingQueueFull = DEFAULT_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL;
    boolean ignoreDiscoveredServers = false;
    boolean tlsFirst = false;
    boolean useTimeoutException = false;
    boolean useDispatcherWithExecutor = false;
    boolean forceFlushOnRequest = true; // true since it's the original b/w compatible way
    ServerPool serverPool = null;
    DispatcherFactory dispatcherFactory = null;

    AuthHandler authHandler;
    ReconnectDelayHandler reconnectDelayHandler;

    ErrorListener errorListener = null;
    TimeTraceLogger timeTraceLogger = null;
    ConnectionListener connectionListener = null;
    ReadListener readListener = null;
    StatisticsCollector statisticsCollector = null;
    String dataPortType = DEFAULT_DATA_PORT_TYPE;
    ExecutorService userExecutor;
    ScheduledExecutorService userScheduledExecutor;
    ExecutorService userConnectExecutor;
    ExecutorService userCallbackExecutor;
    ThreadFactory userConnectThreadFactory;
    ThreadFactory userCallbackThreadFactory;
    List<java.util.function.Consumer<HttpRequest>> httpRequestInterceptors;
    Proxy proxy;

    boolean useDefaultTls;
    boolean useTrustAllTls;
    String keystore;
    char[] keystorePassword;
    String truststore;
    char[] truststorePassword;
    String tlsAlgorithm = DEFAULT_TLS_ALGORITHM;
    String credentialPath;

    /**
     * Constructs a new Builder with the default values.
     */
    public OptionsBuilder() {
    }

    // ----------------------------------------------------------------------------------------------------
    // BUILD CONSTRUCTOR PROPS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Constructs a new {@code Builder} from a {@link Properties} object.
     * <p>Methods called on the builder after construction can override the properties.</p>
     *
     * @param props the {@link Properties} object
     */
    public OptionsBuilder(Properties props) throws IllegalArgumentException {
        properties(props);
    }

    /**
     * Constructs a new {@code Builder} from a file that contains properties.
     *
     * @param propertiesFilePath a resolvable path to a file from the location the application is running, either relative or absolute
     * @throws IOException if the properties file cannot be found, opened or read
     */
    public OptionsBuilder(String propertiesFilePath) throws IOException {
        Properties props = new Properties();
        props.load(Files.newInputStream(Paths.get(propertiesFilePath)));
        properties(props);
    }

    // ----------------------------------------------------------------------------------------------------
    // BUILDER METHODS
    // ----------------------------------------------------------------------------------------------------

    /**
     * Add settings defined in the properties object
     *
     * @param props the properties object
     * @return the Builder for chaining
     * @throws IllegalArgumentException if the properties object is null
     */
    public OptionsBuilder properties(Properties props) {
        if (props == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }
        stringProperty(props, PROP_URL, this::server);
        stringProperty(props, PROP_SERVERS, str -> {
            String[] servers = str.trim().split(",\\s*");
            this.servers(servers);
        });

        charArrayProperty(props, PROP_USERNAME, ca -> this.username = ca);
        charArrayProperty(props, PROP_PASSWORD, ca -> this.password = ca);
        charArrayProperty(props, PROP_TOKEN, ca -> this.tokenSupplier = new Options.DefaultTokenSupplier(ca));
        //noinspection unchecked
        classnameProperty(props, PROP_TOKEN_SUPPLIER_CLASS, o -> this.tokenSupplier = (Supplier<char[]>) o);

        booleanProperty(props, PROP_SECURE, b -> this.useDefaultTls = b);
        booleanProperty(props, PROP_OPEN_TLS, b -> this.useTrustAllTls = b);

        classnameProperty(props, PROP_SSL_CONTEXT_FACTORY_CLASS, o -> this.sslContextFactory = (SSLContextFactory) o);
        stringProperty(props, PROP_KEY_STORE, s -> this.keystore = s);
        charArrayProperty(props, PROP_KEY_STORE_PASSWORD, ca -> this.keystorePassword = ca);
        stringProperty(props, PROP_TRUST_STORE, s -> this.truststore = s);
        charArrayProperty(props, PROP_TRUST_STORE_PASSWORD, ca -> this.truststorePassword = ca);
        stringProperty(props, PROP_TLS_ALGORITHM, s -> this.tlsAlgorithm = s);

        stringProperty(props, PROP_CREDENTIAL_PATH, s -> this.credentialPath = s);

        stringProperty(props, PROP_CONNECTION_NAME, s -> this.connectionName = s);

        booleanProperty(props, PROP_NO_RANDOMIZE, b -> this.noRandomize = b);
        booleanPropertyIfTrue(props, PROP_NO_SUBJECT_VALIDATION, b -> subjectValidationType = SubjectValidationType.None);
        booleanPropertyIfTrue(props, PROP_STRICT_SUBJECT_VALIDATION, b -> subjectValidationType = SubjectValidationType.Strict);
        booleanProperty(props, PROP_REPORT_NO_RESPONDERS, b -> this.reportNoResponders = b);

        stringProperty(props, PROP_CONNECTION_NAME, s -> this.connectionName = s);
        booleanProperty(props, PROP_VERBOSE, b -> this.verbose = b);
        booleanProperty(props, PROP_NO_ECHO, b -> this.noEcho = b);
        booleanProperty(props, PROP_NO_HEADERS, b -> this.noHeaders = b);
        booleanProperty(props, PROP_NO_NO_RESPONDERS, b -> this.noNoResponders = b);
        booleanProperty(props, PROP_CLIENT_SIDE_LIMIT_CHECKS, b -> this.clientSideLimitChecks = b);
        booleanProperty(props, PROP_SUPPORT_UTF8_SUBJECTS, b -> this.supportUTF8Subjects = b);
        booleanProperty(props, PROP_PEDANTIC, b -> this.pedantic = b);

        intProperty(props, PROP_MAX_RECONNECT, i -> this.maxReconnect = i);
        durationProperty(props, PROP_RECONNECT_WAIT, d -> this.reconnectWait = d);
        durationProperty(props, PROP_RECONNECT_JITTER, d -> this.reconnectJitter = d);
        durationProperty(props, PROP_RECONNECT_JITTER_TLS, d -> this.reconnectJitterTls = d);
        longProperty(props, PROP_RECONNECT_BUF_SIZE, l -> this.reconnectBufferSize = l);
        durationProperty(props, PROP_CONNECTION_TIMEOUT, d -> this.connectionTimeout = d);
        intProperty(props, PROP_SOCKET_READ_TIMEOUT, i -> this.socketReadTimeoutMillis = i);
        durationProperty(props, PROP_SOCKET_WRITE_TIMEOUT, d -> this.socketWriteTimeout = d);
        intProperty(props, PROP_SOCKET_SO_LINGER, i -> socketSoLinger = i);
        intProperty(props, PROP_SOCKET_RECEIVE_BUFFER_SIZE, i -> this.receiveBufferSize = i);
        intProperty(props, PROP_SOCKET_SEND_BUFFER_SIZE, i -> this.sendBufferSize = i);

        intGtEqZeroProperty(props, PROP_MAX_CONTROL_LINE, i -> this.maxControlLine = i);
        durationProperty(props, PROP_PING_INTERVAL, d -> this.pingInterval = d);
        durationProperty(props, PROP_CLEANUP_INTERVAL, d -> this.requestCleanupInterval = d);
        durationProperty(props, PROP_WRITE_QUEUE_PUSH_TIMEOUT, d -> this.writeQueuePushTimeout = d);
        intProperty(props, PROP_MAX_PINGS, i -> this.maxPingsOut = i);

        classnameProperty(props, PROP_CONNECTION_LISTENER_CLASS, o -> this.connectionListener = (ConnectionListener) o);
        classnameProperty(props, PROP_ERROR_LISTENER_CLASS, o -> this.errorListener = (ErrorListener) o);
        classnameProperty(props, PROP_READ_LISTENER_CLASS, o -> this.readListener = (ReadListener) o);
        classnameProperty(props, PROP_TIME_TRACE_LOGGER_CLASS, o -> this.timeTraceLogger = (TimeTraceLogger) o);
        classnameProperty(props, PROP_STATISTICS_COLLECTOR_CLASS, o -> this.statisticsCollector = (StatisticsCollector) o);

        stringProperty(props, PROP_DATA_PORT_TYPE, s -> this.dataPortType = s);
        stringProperty(props, PROP_INBOX_PREFIX, this::inboxPrefix);
        intGtEqZeroProperty(props, PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, i -> this.maxMessagesInOutgoingQueue = i);
        booleanProperty(props, PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL, b -> this.discardMessagesWhenOutgoingQueueFull = b);

        booleanProperty(props, PROP_IGNORE_DISCOVERED_SERVERS, b -> this.ignoreDiscoveredServers = b);
        booleanProperty(props, PROP_TLS_FIRST, b -> this.tlsFirst = b);
        booleanProperty(props, PROP_USE_TIMEOUT_EXCEPTION, b -> this.useTimeoutException = b);
        booleanProperty(props, PROP_USE_DISPATCHER_WITH_EXECUTOR, b -> this.useDispatcherWithExecutor = b);
        booleanProperty(props, PROP_FORCE_FLUSH_ON_REQUEST, b -> this.forceFlushOnRequest = b);

        stringProperty(props, PROP_HOSTNAME_RESOLVE_MODE, s -> {
            HostnameResolveMode mode = HostnameResolveMode.get(s);
            if (mode != null) {
                hostnameResolveMode = mode;
            }
        });

        classnameProperty(props, PROP_SERVERS_POOL_IMPLEMENTATION_CLASS, o -> this.serverPool = (ServerPool) o);
        classnameProperty(props, PROP_DISPATCHER_FACTORY_CLASS, o -> this.dispatcherFactory = (DispatcherFactory) o);
        classnameProperty(props, PROP_EXECUTOR_SERVICE_CLASS, o -> this.userExecutor = (ExecutorService) o);
        classnameProperty(props, PROP_CONNECT_EXECUTOR_SERVICE_CLASS, o -> this.userConnectExecutor = (ExecutorService) o);
        classnameProperty(props, PROP_CALLBACK_EXECUTOR_SERVICE_CLASS, o -> this.userCallbackExecutor = (ExecutorService) o);
        classnameProperty(props, PROP_SCHEDULED_EXECUTOR_SERVICE_CLASS, o -> this.userScheduledExecutor = (ScheduledExecutorService) o);
        classnameProperty(props, PROP_CONNECT_THREAD_FACTORY_CLASS, o -> this.userConnectThreadFactory = (ThreadFactory) o);
        classnameProperty(props, PROP_CALLBACK_THREAD_FACTORY_CLASS, o -> this.userCallbackThreadFactory = (ThreadFactory) o);
        return this;
    }

    /**
     * Add a server to the list of known servers.
     *
     * @param serverURL the URL for the server to add
     * @return the Builder for chaining
     * @throws IllegalArgumentException if the url is not formatted correctly.
     */
    public OptionsBuilder server(String serverURL) {
        return servers(serverURL.trim().split(","));
    }

    /**
     * Add an array of servers to the list of known servers.
     *
     * @param servers A list of server URIs
     * @return the Builder for chaining
     * @throws IllegalArgumentException if any url is not formatted correctly.
     */
    public OptionsBuilder servers(String[] servers) {
        for (String s : servers) {
            if (s != null && !s.isEmpty()) {
                try {
                    String unprocessed = s.trim();
                    NatsUri nuri = new NatsUri(unprocessed);
                    if (!natsServerUris.contains(nuri)) {
                        natsServerUris.add(nuri);
                        unprocessedServers.add(unprocessed);
                    }
                }
                catch (URISyntaxException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return this;
    }

    /**
     * For the default server list provider, turn off server pool randomization.
     * The default provider will pick servers from its list randomly on a reconnect.
     * When noRandomize is set to true the default provider supplies a list that
     * first contains servers as configured and then contains the servers as sent
     * from the connected server.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder noRandomize() {
        this.noRandomize = true;
        return this;
    }

    /**
     * Set the hostname resolve mode
     *
     * @param hostnameResolveMode the enum value
     * @return the Builder for chaining
     */
    public OptionsBuilder hostnameResolveMode(HostnameResolveMode hostnameResolveMode) {
        this.hostnameResolveMode = hostnameResolveMode == null ? HostnameResolveMode.ResolveToAll : hostnameResolveMode;
        return this;
    }

    /**
     * Directly set the subjectValidationType. Null sets to the default, Lenient.
     *
     * @param subjectValidationType an enum for SubjectValidationType indicating the type of validation, or null for default
     * @return the Builder for chaining
     */
    public OptionsBuilder subjectValidationType(SubjectValidationType subjectValidationType) {
        this.subjectValidationType = subjectValidationType == null ? SubjectValidationType.Lenient : subjectValidationType;
        return this;
    }

    /**
     * set to report no responders
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder reportNoResponders() {
        this.reportNoResponders = true;
        return this;
    }

    /**
     * Turn off echo. If supported by the nats-server version you are connecting to this
     * flag will prevent the server from echoing messages back to the connection if it
     * has subscriptions on the subject being published to.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder noEcho() {
        this.noEcho = true;
        return this;
    }

    /**
     * Turn off header support. Some versions of the server don't support it.
     * It's also not required if you don't use headers
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder noHeaders() {
        this.noHeaders = true;
        return this;
    }

    /**
     * Turn off noresponder support. Some versions of the server don't support it.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder noNoResponders() {
        this.noNoResponders = true;
        return this;
    }

    /**
     * Set client side limit checks. Default is true
     *
     * @param checks the checks flag
     * @return the Builder for chaining
     */
    public OptionsBuilder clientSideLimitChecks(boolean checks) {
        this.clientSideLimitChecks = checks;
        return this;
    }

    /**
     * The client protocol is not clear about the encoding for subject names. For
     * performance reasons, the Java client defaults to ASCII. You can enable UTF8
     * with this method. The server, written in go, treats byte to string as UTF8 by default
     * and should allow UTF8 subjects, but make sure to test any clients when using them.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder supportUTF8Subjects() {
        this.supportUTF8Subjects = true;
        return this;
    }

    /**
     * Set the connection's optional Name.
     *
     * @param name the connections new name.
     * @return the Builder for chaining
     */
    public OptionsBuilder connectionName(String name) {
        this.connectionName = name;
        return this;
    }

    /**
     * Set the connection's inbox prefix. All inboxes will start with this string.
     *
     * @param prefix prefix to use.
     * @return the Builder for chaining
     */
    public OptionsBuilder inboxPrefix(String prefix) {
        this.inboxPrefix = prefix;

        if (!this.inboxPrefix.endsWith(".")) {
            this.inboxPrefix = this.inboxPrefix + ".";
        }
        return this;
    }

    /**
     * Turn on verbose mode with the server.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder verbose() {
        this.verbose = true;
        return this;
    }

    /**
     * Turn on pedantic mode for the server, in relation to this connection.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder pedantic() {
        this.pedantic = true;
        return this;
    }

    /**
     * Turn on advanced stats, primarily for test/benchmarks. These are visible if you
     * call toString on the {@link Statistics Statistics} object.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder turnOnAdvancedStats() {
        this.trackAdvancedStats = true;
        return this;
    }

    /**
     * Enable connection trace messages. Messages are printed to standard out. This option is for very
     * fine-grained debugging of connection issues.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder traceConnection() {
        this.traceConnection = true;
        return this;
    }

    /**
     * Sets the options to use the default SSL Context, if it exists.
     *
     * @return the Builder for chaining
     * @throws NoSuchAlgorithmException <em>Not thrown, deferred to build() method, left in for backward compatibility</em>
     */
    public OptionsBuilder secure() throws NoSuchAlgorithmException {
        useDefaultTls = true;
        return this;
    }

    /**
     * Set the options to use an SSL context that accepts any server certificate and has no client certificates.
     *
     * @return the Builder for chaining
     * @throws NoSuchAlgorithmException <em>Not thrown, deferred to build() method, left in for backward compatibility</em>
     */
    public OptionsBuilder opentls() throws NoSuchAlgorithmException {
        useTrustAllTls = true;
        return this;
    }

    /**
     * Set the SSL context, requires that the server supports TLS connections and
     * the URI specifies TLS.
     * If provided, the context takes precedence over any other TLS/SSL properties
     * set in the builder, including the sslContextFactory
     *
     * @param ctx the SSL Context to use for TLS connections
     * @return the Builder for chaining
     */
    public OptionsBuilder sslContext(SSLContext ctx) {
        this.sslContext = ctx;
        return this;
    }

    /**
     * Set the factory that provides the ssl context. The factory is superseded
     * by an instance of SSLContext
     *
     * @param sslContextFactory the SSL Context for use to create a ssl context
     * @return the Builder for chaining
     */
    public OptionsBuilder sslContextFactory(SSLContextFactory sslContextFactory) {
        this.sslContextFactory = sslContextFactory;
        return this;
    }

    /**
     * the path to the keystore file
     *
     * @param keystore the path to the keystore file
     * @return the Builder for chaining
     */
    public OptionsBuilder keystorePath(String keystore) {
        this.keystore = emptyAsNull(keystore);
        return this;
    }

    /**
     * the password for the keystore
     *
     * @param keystorePassword the password for the keystore
     * @return the Builder for chaining
     */
    public OptionsBuilder keystorePassword(char[] keystorePassword) {
        this.keystorePassword = keystorePassword == null || keystorePassword.length == 0 ? null : keystorePassword;
        return this;
    }

    /**
     * the path to the trust store file
     *
     * @param truststore the path to the trust store file
     * @return the Builder for chaining
     */
    public OptionsBuilder truststorePath(String truststore) {
        this.truststore = emptyAsNull(truststore);
        return this;
    }

    /**
     * The password for the trust store
     *
     * @param truststorePassword the password for the trust store
     * @return the Builder for chaining
     */
    public OptionsBuilder truststorePassword(char[] truststorePassword) {
        this.truststorePassword = truststorePassword == null || truststorePassword.length == 0 ? null : truststorePassword;
        return this;
    }

    /**
     * The tls algorithm to use Default is {@value SSLUtils#DEFAULT_TLS_ALGORITHM}
     *
     * @param tlsAlgorithm the tls algorithm.
     * @return the Builder for chaining
     */
    public OptionsBuilder tlsAlgorithm(String tlsAlgorithm) {
        this.tlsAlgorithm = emptyOrNullAs(tlsAlgorithm, DEFAULT_TLS_ALGORITHM);
        return this;
    }

    /**
     * the path to the credentials file for creating an {@link AuthHandler AuthHandler}
     *
     * @param credentialPath the path to the credentials file
     * @return the Builder for chaining
     */
    public OptionsBuilder credentialPath(String credentialPath) {
        this.credentialPath = emptyAsNull(credentialPath);
        return this;
    }

    /**
     * Equivalent to calling maxReconnects with 0, {@link #maxReconnects(int) maxReconnects}.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder noReconnect() {
        this.maxReconnect = 0;
        return this;
    }

    /**
     * Set the maximum number of reconnect attempts. Use 0 to turn off
     * auto-reconnect. Use -1 to turn on infinite reconnects.
     *
     * <p>The reconnect count is incremented on a per-server basis, so if the server list contains 5 servers
     * but max reconnects is set to 3, only 3 of those servers will be tried.</p>
     *
     * <p>This library has a slight difference from some NATS clients, if you set the maxReconnects to zero
     * there will not be any reconnect attempts, regardless of the number of known servers.</p>
     *
     * <p>The reconnect state is entered when the connection is connected and loses
     * that connection. During the initial connection attempt, the client will cycle over
     * its server list one time, regardless of what maxReconnects is set to. The only exception
     * to this is the async connect method {@link Nats#connectAsynchronously(Options, boolean) connectAsynchronously}.</p>
     *
     * @param max the maximum reconnect attempts
     * @return the Builder for chaining
     */
    public OptionsBuilder maxReconnects(int max) {
        this.maxReconnect = max;
        return this;
    }

    /**
     * Set the time to wait between reconnect attempts to the same server. This setting is only used
     * by the client when the same server appears twice in the reconnect attempts, either because it is the
     * only known server or by random chance. Note, the randomization of the server list doesn't occur per
     * attempt, it is performed once at the start, so if there are 2 servers in the list you will never encounter
     * the reconnect wait.
     *
     * @param time the time to wait
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectWait(Duration time) {
        this.reconnectWait = time;
        return this;
    }

    /**
     * Set the jitter time to wait between reconnect attempts to the same server. This setting is used to vary
     * the reconnect wait to avoid multiple clients trying to reconnect to servers at the same time.
     *
     * @param time the time to wait
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectJitter(Duration time) {
        this.reconnectJitter = time;
        return this;
    }

    /**
     * Set the jitter time for a tls/secure connection to wait between reconnect attempts to the same server.
     * This setting is used to vary the reconnect wait to avoid multiple clients trying to reconnect to
     * servers at the same time.
     *
     * @param time the time to wait
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectJitterTls(Duration time) {
        this.reconnectJitterTls = time;
        return this;
    }

    /**
     * Set the maximum length of a control line sent by this connection. This value is also configured
     * in the server but the protocol doesn't currently forward that setting. Configure it here so that
     * the client can ensure that messages are valid before sending to the server.
     *
     * @param bytes the max byte count
     * @return the Builder for chaining
     */
    public OptionsBuilder maxControlLine(int bytes) {
        this.maxControlLine = bytes < 0 ? DEFAULT_MAX_CONTROL_LINE : bytes;
        return this;
    }

    /**
     * Set the timeout for connection attempts. Each server in the options is allowed this timeout
     * so if 3 servers are tried with a timeout of 5s the total time could be 15s.
     *
     * @param connectionTimeout the time to wait
     * @return the Builder for chaining
     */
    public OptionsBuilder connectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    /**
     * Set the timeout for connection attempts. Each server in the options is allowed this timeout
     * so if 3 servers are tried with a timeout of 5s the total time could be 15s.
     *
     * @param connectionTimeoutMillis the time to wait in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder connectionTimeout(long connectionTimeoutMillis) {
        this.connectionTimeout = Duration.ofMillis(connectionTimeoutMillis);
        return this;
    }

    /**
     * Set the timeout to use around socket reads
     *
     * @param socketReadTimeoutMillis the timeout milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder socketReadTimeoutMillis(int socketReadTimeoutMillis) {
        this.socketReadTimeoutMillis = socketReadTimeoutMillis;
        return this;
    }

    /**
     * Set the timeout to use around socket writes
     *
     * @param socketWriteTimeoutMillis the timeout milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder socketWriteTimeout(long socketWriteTimeoutMillis) {
        socketWriteTimeout = Duration.ofMillis(socketWriteTimeoutMillis);
        return this;
    }

    /**
     * Set the timeout to use around socket writes
     *
     * @param socketWriteTimeout the timeout duration
     * @return the Builder for chaining
     */
    public OptionsBuilder socketWriteTimeout(Duration socketWriteTimeout) {
        this.socketWriteTimeout = socketWriteTimeout;
        return this;
    }

    /**
     * Set the value of the socket SO LINGER property in seconds.
     * This feature is used by library data port implementations.
     * Setting this is a last resort if socket closes are a problem
     * in your environment, otherwise it's generally not necessary
     * to set this. The value must be greater than or equal to 0
     * to have the code call socket.setSoLinger with true and the timeout value
     *
     * @param socketSoLinger the number of seconds to linger
     * @return the Builder for chaining
     */
    public OptionsBuilder socketSoLinger(int socketSoLinger) {
        this.socketSoLinger = socketSoLinger;
        return this;
    }

    /**
     * Set the value of the socket SO_RCVBUF property in bytes
     * The SO_RCVBUF option is used by the platform's networking code as a hint for the size to set the underlying network I/O buffers.
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     *
     * @param receiveBufferSize the size in bytes
     * @return the Builder for chaining
     */
    public OptionsBuilder receiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    /**
     * Set the value of the socket SO_SNDBUF property in bytes
     * The SO_SNDBUF option is used by the platform's networking code as a hint for the size to set the underlying network I/O buffers.
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     *
     * @param sendBufferSize the size in bytes
     * @return the Builder for chaining
     */
    public OptionsBuilder sendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    /**
     * Set the interval between attempts to pings the server. These pings are automated,
     * and capped by {@link #maxPingsOut(int) maxPingsOut()}. As of 2.4.4 the library
     * may wait up to 2 * time to send a ping. Incoming traffic from the server can postpone
     * the next ping to avoid pings taking up bandwidth during busy messaging.
     * Keep in mind that a ping requires a round trip to the server. Setting this value to a small
     * number can result in quick failures due to maxPingsOut being reached, these failures will
     * force a disconnect/reconnect which can result in messages being held back or failed. In general,
     * the ping interval should be set in seconds but this value is not enforced as it would result in
     * an API change from the 2.0 release.
     *
     * @param time the time between client to server pings
     * @return the Builder for chaining
     */
    public OptionsBuilder pingInterval(Duration time) {
        this.pingInterval = time == null ? DEFAULT_PING_INTERVAL : time;
        return this;
    }

    /**
     * Set the interval between cleaning passes on outstanding request futures that are cancelled or timeout
     * in the application code.
     *
     * <p>The default value is probably reasonable, but this interval is useful in a very noisy network
     * situation where lots of requests are used.
     *
     * @param time the cleaning interval
     * @return the Builder for chaining
     */
    public OptionsBuilder requestCleanupInterval(Duration time) {
        this.requestCleanupInterval = time;
        return this;
    }

    /**
     * Set the amount of time to wait to acquire the lock and to offer a message to the outgoing message queue
     *
     * @param time the wait time
     * @return the Builder for chaining
     */
    public OptionsBuilder writeQueuePushTimeout(Duration time) {
        this.writeQueuePushTimeout = time;
        return this;
    }

    /**
     * Set the maximum number of pings the client can have in flight.
     *
     * @param max the max pings
     * @return the Builder for chaining
     */
    public OptionsBuilder maxPingsOut(int max) {
        this.maxPingsOut = max;
        return this;
    }

    /**
     * Sets the initial size for buffers in the connection, primarily for testing.
     *
     * @param size the size in bytes to make buffers for connections created with this options
     * @return the Builder for chaining
     */
    public OptionsBuilder bufferSize(int size) {
        this.bufferSize = size;
        return this;
    }

    /**
     * Set the maximum number of bytes to buffer in the client when trying to
     * reconnect. When this value is exceeded the client will start to drop messages.
     * The count of dropped messages can be read from the {@link Statistics#getDroppedCount() Statistics}.
     * A value of zero will disable the reconnect buffer, a value less than zero means unlimited. Caution
     * should be used for negative numbers as they can result in an unreliable network connection plus a
     * high message rate leading to an out of memory error.
     *
     * @param size the size in bytes
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectBufferSize(long size) {
        this.reconnectBufferSize = size;
        return this;
    }

    /**
     * Set the username and password for basic authentication.
     * If the user and password are set in the server URL, they will override these values. However, in a clustering situation,
     * these values can be used as a fallback.
     * use the char[] version instead for better security
     *
     * @param userName a non-empty userName
     * @param password the password, in plain text
     * @return the Builder for chaining
     */
    public OptionsBuilder userInfo(String userName, String password) {
        this.username = userName.toCharArray();
        this.password = password.toCharArray();
        return this;
    }

    /**
     * Set the username and password for basic authentication.
     * If the user and password are set in the server URL, they will override these values. However, in a clustering situation,
     * these values can be used as a fallback.
     *
     * @param userName a non-empty userName
     * @param password the password, in plain text
     * @return the Builder for chaining
     */
    public OptionsBuilder userInfo(char[] userName, char[] password) {
        this.username = userName;
        this.password = password;
        return this;
    }

    /**
     * Set the token for token-based authentication.
     * If a token is provided in a server URI, it overrides this value.
     *
     * @param token The token
     * @return the Builder for chaining
     */
    public OptionsBuilder token(char[] token) {
        this.tokenSupplier = new Options.DefaultTokenSupplier(token);
        return this;
    }

    /**
     * Set the token supplier for token-based authentication.
     * If a token is provided in a server URI, it overrides this value.
     *
     * @param tokenSupplier The tokenSupplier
     * @return the Builder for chaining
     */
    public OptionsBuilder tokenSupplier(Supplier<char[]> tokenSupplier) {
        this.tokenSupplier = tokenSupplier == null ? new Options.DefaultTokenSupplier() : tokenSupplier;
        return this;
    }

    /**
     * Set the {@link AuthHandler AuthHandler} to sign the server nonce for authentication in
     * nonce-mode.
     *
     * @param handler The new AuthHandler for this connection.
     * @return the Builder for chaining
     */
    public OptionsBuilder authHandler(AuthHandler handler) {
        this.authHandler = handler;
        return this;
    }

    /**
     * Set the {@link ReconnectDelayHandler ReconnectDelayHandler} for custom reconnect duration
     *
     * @param handler The new ReconnectDelayHandler for this connection.
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectDelayHandler(ReconnectDelayHandler handler) {
        this.reconnectDelayHandler = handler;
        return this;
    }

    /**
     * Set the {@link ErrorListener ErrorListener} to receive asynchronous error events related to this
     * connection.
     *
     * @param listener The new ErrorListener for this connection.
     * @return the Builder for chaining
     */
    public OptionsBuilder errorListener(ErrorListener listener) {
        this.errorListener = listener;
        return this;
    }

    /**
     * Set the {@link TimeTraceLogger TimeTraceLogger} to receive trace events related to this connection.
     *
     * @param logger The new TimeTraceLogger for this connection.
     * @return the Builder for chaining
     */
    public OptionsBuilder timeTraceLogger(TimeTraceLogger logger) {
        this.timeTraceLogger = logger;
        return this;
    }

    /**
     * Set the {@link ConnectionListener ConnectionListener} to receive asynchronous notifications of disconnect
     * events.
     *
     * @param listener The new ConnectionListener for this type of event.
     * @return the Builder for chaining
     */
    public OptionsBuilder connectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
        return this;
    }

    /**
     * Sets a listener to be notified on incoming protocol/message
     *
     * @param readListener the listener
     * @return the Builder for chaining
     */
    public OptionsBuilder readListener(ReadListener readListener) {
        this.readListener = readListener;
        return this;
    }

    /**
     * Set the {@link StatisticsCollector StatisticsCollector} to collect connection metrics.
     * <p>
     * If not set, then a default implementation will be used.
     *
     * @param collector the new StatisticsCollector for this connection.
     * @return the Builder for chaining
     */
    public OptionsBuilder statisticsCollector(StatisticsCollector collector) {
        this.statisticsCollector = collector;
        return this;
    }

    /**
     * Set the {@link ExecutorService} used to run threaded tasks. The default is a
     * cached thread pool that names threads after the connection name (or a default). This executor
     * is used for reading and writing the underlying sockets as well as for each Dispatcher.
     * The default executor uses a short keepalive time, 500ms, to insure quick shutdowns. This is reasonable
     * since most threads from the executor are long-lived. If you customize, be sure to keep the shutdown
     * effect in mind, executors can block for their keepalive time. The default executor also marks threads
     * with priority normal and as non-daemon.
     *
     * @param executor The ExecutorService to use for connections built with these options.
     * @return the Builder for chaining
     */
    public OptionsBuilder executor(ExecutorService executor) {
        this.userExecutor = executor;
        return this;
    }

    /**
     * Set the {@link ScheduledExecutorService} used to run scheduled task like
     * heartbeat timers
     * The default is a ScheduledThreadPoolExecutor that does not
     * execute delayed tasks after shutdown and removes tasks on cancel;
     *
     * @param scheduledExecutor The ScheduledExecutorService to use for timer tasks
     * @return the Builder for chaining
     */
    public OptionsBuilder scheduledExecutor(ScheduledExecutorService scheduledExecutor) {
        this.userScheduledExecutor = scheduledExecutor;
        return this;
    }

    /**
     * Set the {@link ExecutorService} used to make connections.
     * The default is a Single Thread Executor
     *
     * @param connectExecutor The ExecutorService to make connections with.
     * @return the Builder for chaining
     */
    public OptionsBuilder connectExecutor(ExecutorService connectExecutor) {
        this.userConnectExecutor = connectExecutor;
        return this;
    }

    /**
     * Set the {@link ExecutorService} used to make event callbacks with.
     * The default is a Single Thread Executor
     *
     * @param callbackExecutor The ExecutorService to make event callbacks with.
     * @return the Builder for chaining
     */
    public OptionsBuilder callbackExecutor(ExecutorService callbackExecutor) {
        this.userCallbackExecutor = callbackExecutor;
        return this;
    }

    /**
     * Sets custom thread factory for the connect executor service to use when making threads
     * If both connectThreadFactory and callbackExecutor are set, only callbackExecutor is used.
     *
     * @param threadFactory the thread factory to use for the executor service
     * @return the Builder for chaining
     */
    public OptionsBuilder connectThreadFactory(ThreadFactory threadFactory) {
        this.userConnectThreadFactory = threadFactory;
        return this;
    }

    /**
     * Sets custom thread factory for the callback executor service to use when making threads
     * If both callbackThreadFactory and callbackExecutor are set, only callbackExecutor is used.
     *
     * @param threadFactory the thread factory to use for the executor service
     * @return the Builder for chaining
     */
    public OptionsBuilder callbackThreadFactory(ThreadFactory threadFactory) {
        this.userCallbackThreadFactory = threadFactory;
        return this;
    }

    /**
     * Add an HttpRequest interceptor which can be used to modify the HTTP request when using websockets
     *
     * @param interceptor The interceptor
     * @return the Builder for chaining
     */
    public OptionsBuilder httpRequestInterceptor(java.util.function.Consumer<HttpRequest> interceptor) {
        if (null == this.httpRequestInterceptors) {
            this.httpRequestInterceptors = new ArrayList<>();
        }
        this.httpRequestInterceptors.add(interceptor);
        return this;
    }

    /**
     * Overwrite the list of HttpRequest interceptors which can be used to modify the HTTP request when using websockets
     *
     * @param interceptors The list of interceptors
     * @return the Builder for chaining
     */
    public OptionsBuilder httpRequestInterceptors(Collection<? extends Consumer<HttpRequest>> interceptors) {
        this.httpRequestInterceptors = new ArrayList<>(interceptors);
        return this;
    }

    /**
     * Define a proxy to use when connecting.
     *
     * @param proxy is the HTTP or socks proxy to use.
     * @return the Builder for chaining
     */
    public OptionsBuilder proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * The class to use for this connections data port. This is an advanced setting
     * and primarily useful for testing.
     *
     * @param dataPortClassName a valid and accessible class name
     * @return the Builder for chaining
     */
    public OptionsBuilder dataPortType(String dataPortClassName) {
        this.dataPortType = dataPortClassName == null ? DEFAULT_DATA_PORT_TYPE : dataPortClassName;
        return this;
    }

    /**
     * Set the maximum number of messages in the outgoing queue.
     *
     * @param maxMessagesInOutgoingQueue the maximum number of messages in the outgoing queue
     * @return the Builder for chaining
     */
    public OptionsBuilder maxMessagesInOutgoingQueue(int maxMessagesInOutgoingQueue) {
        this.maxMessagesInOutgoingQueue = maxMessagesInOutgoingQueue < 0
            ? DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE
            : maxMessagesInOutgoingQueue;
        return this;
    }

    /**
     * Enable discard messages when the outgoing queue full. See {@link OptionsBuilder#maxMessagesInOutgoingQueue(int) maxMessagesInOutgoingQueue}
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder discardMessagesWhenOutgoingQueueFull() {
        this.discardMessagesWhenOutgoingQueueFull = true;
        return this;
    }

    /**
     * Turn off use of discovered servers when connecting / reconnecting. Used in the default server list provider.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder ignoreDiscoveredServers() {
        this.ignoreDiscoveredServers = true;
        return this;
    }

    /**
     * Set TLS Handshake First behavior on. Default is off.
     * TLS Handshake First is used to instruct the library perform
     * the TLS handshake right after the connect and before receiving
     * the INFO protocol from the server. If this option is enabled
     * but the server is not configured to perform the TLS handshake
     * first, the connection will fail.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder tlsFirst() {
        this.tlsFirst = true;
        return this;
    }

    /**
     * Throw {@link java.util.concurrent.TimeoutException} on timeout instead of {@link java.util.concurrent.CancellationException}?
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder useTimeoutException() {
        this.useTimeoutException = true;
        return this;
    }

    /**
     * Instruct dispatchers to dispatch all messages as a task, instead of directly from dispatcher thread
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder useDispatcherWithExecutor() {
        this.useDispatcherWithExecutor = true;
        return this;
    }

    /**
     * Instruct requests to turn off flush on requests.
     *
     * @return the Builder for chaining
     */
    public OptionsBuilder dontForceFlushOnRequest() {
        this.forceFlushOnRequest = false;
        return this;
    }

    /**
     * Set the ServerPool implementation for connections to use instead of the default implementation
     *
     * @param serverPool the implementation
     * @return the Builder for chaining
     */
    public OptionsBuilder serverPool(ServerPool serverPool) {
        this.serverPool = serverPool;
        return this;
    }

    /**
     * Set the DispatcherFactory implementation for connections to use instead of the default implementation
     *
     * @param dispatcherFactory the implementation
     * @return the Builder for chaining
     */
    public OptionsBuilder dispatcherFactory(DispatcherFactory dispatcherFactory) {
        this.dispatcherFactory = dispatcherFactory;
        return this;
    }

    /**
     * Build an Options object from this Builder.
     *
     * <p>If the Options builder was not provided with a server, a default one will be included
     * {@link OptionsConstants#DEFAULT_URL}. If only a single server URI is included, the builder
     * will try a few things to make connecting easier:
     * <ul>
     * <li>If there is no user/password is set but the URI has them, {@code nats://user:password@server:port}, they will be used.
     * <li>If there is no token is set but the URI has one, {@code nats://token@server:port}, it will be used.
     * <li>If the URI is of the form tls:// and no SSL context was assigned, one is created, see {@link OptionsBuilder#secure() secure()}.
     * <li>If the URI is of the form opentls:// and no SSL context was assigned one will be created
     * that does not check the servers certificate for validity. This is not secure and only provided
     * for tests and development.
     * </ul>
     *
     * @return the new options object
     * @throws IllegalStateException if there is a conflict in the options, like a token and a user/pass
     */
    public Options build() throws IllegalStateException {
        // ----------------------------------------------------------------------------------------------------
        // BUILD IMPL
        // ----------------------------------------------------------------------------------------------------
        if (this.username != null && tokenSupplier.get() != null) {
            throw new IllegalStateException("Options can't have token and username");
        }

        if (inboxPrefix == null) {
            inboxPrefix = DEFAULT_INBOX_PREFIX;
        }

        boolean checkUrisForSecure = true;
        if (natsServerUris.isEmpty()) {
            server(DEFAULT_URL);
            checkUrisForSecure = false;
        }

        // ssl context can be directly provided, but if it's not
        // there might be a factory, or just see if we should make it ourselves
        if (sslContext == null) {
            if (sslContextFactory != null) {
                sslContext = sslContextFactory.createSSLContext(new SSLContextFactoryProperties.Builder()
                    .keystore(keystore)
                    .keystorePassword(keystorePassword)
                    .truststore(truststore)
                    .truststorePassword(truststorePassword)
                    .tlsAlgorithm(tlsAlgorithm)
                    .build());
            }
            else {
                if (keystore != null || truststore != null) {
                    // the user provided keystore/truststore properties, the want us to make the sslContext that way
                    try {
                        sslContext = SSLUtils.createSSLContext(keystore, keystorePassword, truststore, truststorePassword, tlsAlgorithm);
                    }
                    catch (Exception e) {
                        throw new IllegalStateException("Unable to create SSL context", e);
                    }
                }
                else {
                    // the sslContext has not been requested via factory or keystore/truststore properties
                    // If we haven't been told to use the default or the trust all context
                    // and the server isn't the default url, check to see if the server uris
                    // suggest we need the ssl context.
                    if (!useDefaultTls && !useTrustAllTls && checkUrisForSecure) {
                        for (int i = 0; sslContext == null && i < natsServerUris.size(); i++) {
                            NatsUri natsUri = natsServerUris.get(i);
                            switch (natsUri.getScheme()) {
                                case TLS_PROTOCOL:
                                case SECURE_WEBSOCKET_PROTOCOL:
                                    useDefaultTls = true;
                                    break;
                                case OPENTLS_PROTOCOL:
                                    useTrustAllTls = true;
                                    break;
                            }
                        }
                    }

                    // check trust all (open) first, in case they provided both
                    // PROP_SECURE (secure) and PROP_OPEN_TLS (openTls)
                    if (useTrustAllTls) {
                        try {
                            this.sslContext = SSLUtils.createTrustAllTlsContext();
                        }
                        catch (GeneralSecurityException e) {
                            throw new IllegalStateException("Unable to create SSL context", e);
                        }
                    }
                    else if (useDefaultTls) {
                        try {
                            this.sslContext = SSLContext.getDefault();
                        }
                        catch (NoSuchAlgorithmException e) {
                            throw new IllegalStateException("Unable to create default SSL context", e);
                        }
                    }
                }
            }
        }

        if (tlsFirst && sslContext == null) {
            throw new IllegalStateException("SSL context required for tls handshake first");
        }

        if (credentialPath != null) {
            File file = new File(credentialPath).getAbsoluteFile();
            authHandler = Nats.credentials(file.toString());
        }

        if (socketReadTimeoutMillis < 1) {
            socketReadTimeoutMillis = 0; // just for consistency. The connection compares to gt 0
        }

        if (socketWriteTimeout != null && socketWriteTimeout.toNanos() < MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS) {
            throw new IllegalArgumentException("Socket Write Timeout cannot be less than " + MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS + " nanoseconds.");
        }

        if (socketSoLinger < 1) {
            socketSoLinger = -1;
        }

        if (receiveBufferSize < 1) {
            receiveBufferSize = -1;
        }

        if (sendBufferSize < 1) {
            sendBufferSize = -1;
        }

        if (errorListener == null) {
            errorListener = new ErrorListener() {
            };
        }

        if (timeTraceLogger == null) {
            if (traceConnection) {
                timeTraceLogger = (format, args) -> {
                    String timeStr = DateTimeFormatter.ISO_TIME.format(LocalDateTime.now());
                    System.out.println("[" + timeStr + "] connect trace: " + String.format(format, args));
                };
            }
            else {
                timeTraceLogger = (f, a) -> {
                };
            }
        }
        else {
            // if the dev provided an impl, we assume they meant to time trace the connection
            traceConnection = true;
        }

        return new Options(this);
    }

    // ----------------------------------------------------------------------------------------------------
    // BUILDER COPY CONSTRUCTOR
    // ----------------------------------------------------------------------------------------------------

    /**
     * Construction an Options.Builder copying an existing Options
     *
     * @param o the options
     */
    public OptionsBuilder(Options o) {
        if (o == null) {
            throw new IllegalArgumentException("Options cannot be null");
        }

        this.natsServerUris.addAll(o.natsServerUris);
        this.unprocessedServers.addAll(o.unprocessedServers);
        this.noRandomize = o.noRandomize;
        this.hostnameResolveMode = o.hostnameResolveMode;
        this.subjectValidationType = o.subjectValidationType;
        this.reportNoResponders = o.reportNoResponders;
        this.connectionName = o.connectionName;
        this.verbose = o.verbose;
        this.pedantic = o.pedantic;
        this.sslContext = o.sslContext;
        this.maxReconnect = o.maxReconnect;
        this.reconnectWait = o.reconnectWait;
        this.reconnectJitter = o.reconnectJitter;
        this.reconnectJitterTls = o.reconnectJitterTls;
        this.connectionTimeout = o.connectionTimeout;
        this.socketReadTimeoutMillis = o.socketReadTimeoutMillis;
        this.socketWriteTimeout = o.socketWriteTimeout;
        this.socketSoLinger = o.socketSoLinger;
        this.receiveBufferSize = o.receiveBufferSize;
        this.sendBufferSize = o.sendBufferSize;
        this.pingInterval = o.pingInterval;
        this.requestCleanupInterval = o.requestCleanupInterval;
        this.writeQueuePushTimeout = o.writeQueuePushTimeout;
        this.maxPingsOut = o.maxPingsOut;
        this.reconnectBufferSize = o.reconnectBufferSize;
        this.username = o.username;
        this.password = o.password;
        this.tokenSupplier = o.tokenSupplier;
        this.maxControlLine = o.maxControlLine;
        this.bufferSize = o.bufferSize;
        this.noEcho = o.noEcho;
        this.noHeaders = o.noHeaders;
        this.noNoResponders = o.noNoResponders;
        this.clientSideLimitChecks = o.clientSideLimitChecks;
        this.supportUTF8Subjects = o.supportUTF8Subjects;
        this.inboxPrefix = o.inboxPrefix;
        this.traceConnection = o.traceConnection;
        this.maxMessagesInOutgoingQueue = o.maxMessagesInOutgoingQueue;
        this.discardMessagesWhenOutgoingQueueFull = o.discardMessagesWhenOutgoingQueueFull;

        this.authHandler = o.authHandler;
        this.reconnectDelayHandler = o.reconnectDelayHandler;

        this.errorListener = o.errorListener;
        this.timeTraceLogger = o.timeTraceLogger;
        this.connectionListener = o.connectionListener;
        this.readListener = o.readListener;
        this.statisticsCollector = o.statisticsCollector;
        this.dataPortType = o.dataPortType;
        this.trackAdvancedStats = o.trackAdvancedStats;

        this.userExecutor = o.userExecutor;
        this.userScheduledExecutor = o.userScheduledExecutor;
        this.userConnectExecutor = o.userConnectExecutor;
        this.userCallbackExecutor = o.userCallbackExecutor;
        this.userCallbackThreadFactory = o.userCallbackThreadFactory;
        this.userConnectThreadFactory = o.userConnectThreadFactory;

        this.httpRequestInterceptors = o.httpRequestInterceptors;
        this.proxy = o.proxy;

        this.ignoreDiscoveredServers = o.ignoreDiscoveredServers;
        this.tlsFirst = o.tlsFirst;
        this.useTimeoutException = o.useTimeoutException;
        this.useDispatcherWithExecutor = o.useDispatcherWithExecutor;
        this.forceFlushOnRequest = o.forceFlushOnRequest;

        this.serverPool = o.serverPool;
        this.dispatcherFactory = o.dispatcherFactory;
    }
}
