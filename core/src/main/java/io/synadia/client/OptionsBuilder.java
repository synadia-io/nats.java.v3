package io.synadia.client;

import io.synadia.client.impl.DefaultReconnectDelayHandler;
import io.synadia.client.impl.DispatcherFactory;
import io.synadia.client.impl.SSLContextFactory;
import io.synadia.client.impl.SSLContextFactoryProperties;
import io.synadia.client.utils.HttpRequest;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.SSLUtils;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
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
import static io.synadia.client.utils.NatsConstants.*;
import static io.synadia.client.utils.SSLUtils.DEFAULT_TLS_ALGORITHM;
import static io.synadia.client.utils.Validator.emptyAsNull;
import static io.synadia.client.utils.Validator.emptyOrNullAs;

/**
 * Options are created using a Builder. The builder supports chaining and will
 * create a default set of options if no methods are calls. The builder can also
 * be created from a properties object using the property names defined with the
 * prefix PROP_ in this class.
 * <p>A common usage for testing might be {@code new Options.Builder().server(myserverurl).noReconnect.build()}
 * <p>Builder methods validate their arguments and throw {@link IllegalArgumentException} for an invalid value; see individual methods for specifics.
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
    String connectionName = null; // Useful for debugging -> "test: " + NatsTestServer.currentPort();
    boolean verbose = false;
    boolean pedantic = false;
    SSLContext sslContext = null;
    SSLContextFactory sslContextFactory = null;
    int maxControlLine = DEFAULT_MAX_CONTROL_LINE;
    int maxReconnects = DEFAULT_MAX_RECONNECT;
    long reconnectWait = DEFAULT_RECONNECT_WAIT;
    long reconnectJitter = DEFAULT_RECONNECT_JITTER;
    long reconnectJitterTls = DEFAULT_RECONNECT_JITTER_TLS;
    long connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    long socketReadTimeout = 0;
    long socketWriteTimeout = DEFAULT_SOCKET_WRITE_TIMEOUT;
    int socketSoLinger = -1;
    int socketReceiveBufferSize = -1;
    int socketSendBufferSize = -1;
    long pingInterval = DEFAULT_PING_INTERVAL;
    long requestCleanupInterval = DEFAULT_REQUEST_CLEANUP_INTERVAL;
    long writeQueuePushTimeout = DEFAULT_WRITE_QUEUE_PUSH_TIMEOUT;
    int maxPingsOut = DEFAULT_MAX_PINGS_OUT;
    long reconnectBufferSize = DEFAULT_RECONNECT_BUF_SIZE;
    char[] username = null;
    char[] password = null;
    Supplier<char[]> tokenSupplier = null;
    int bufferSize = DEFAULT_BUFFER_SIZE;
    boolean trackAdvancedStats = false;
    boolean noEcho = false;
    boolean clientSideLimitChecks = true;
    boolean supportUTF8Subjects = false;
    String inboxPrefix = null;
    int maxMessagesInOutgoingQueue = DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE;
    boolean discardMessagesWhenOutgoingQueueFull = DEFAULT_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL;
    boolean ignoreDiscoveredServers = false;
    boolean tlsFirst = false;
    boolean useTimeoutException = false;
    boolean useDispatcherWithExecutor = false;
    boolean forceFlushOnRequest = true; // true since it's the original b/w compatible way
    ServerPool serverPool = null;
    DispatcherFactory dispatcherFactory = null;

    AuthHandler authHandler = null;
    ReconnectDelayHandler reconnectDelayHandler = null;
    ReconnectDelayBehavior reconnectDelayBehavior = ReconnectDelayBehavior.LameDuckAware;

    ErrorListener errorListener = null;
    ConnectionListener connectionListener = null;
    ReadListener readListener = null;
    StatisticsCollector statisticsCollector = null;
    String dataPortType = DEFAULT_DATA_PORT_TYPE;
    ThreadFactory userConnectThreadFactory = null;
    ThreadFactory userCallbackThreadFactory = null;
    ThreadFactory userReaderThreadFactory = null;
    ThreadFactory userWriterThreadFactory = null;
    ScheduledExecutorService userScheduledExecutor = null;
    ExecutorService userExecutor = null;
    ExecutorService userConnectExecutor = null;
    ExecutorService userCallbackExecutor = null;
    ExecutorService userReaderExecutor = null;
    ExecutorService userWriterExecutor = null;
    List<Consumer<HttpRequest>> httpRequestInterceptors = null;
    Proxy proxy = null;

    boolean useDefaultTls = false;
    boolean useTrustAllTls = false;
    String keystore = null;
    char[] keystorePassword = null;
    String truststore = null;
    char[] truststorePassword = null;
    String tlsAlgorithm = DEFAULT_TLS_ALGORITHM;
    String credentialPath = null;

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
     * @throws IllegalArgumentException if the properties object is null
     */
    public OptionsBuilder(Properties props) {
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

        charArrayProperty(props, PROP_USERNAME, this::username);
        charArrayProperty(props, PROP_PASSWORD, this::password);
        charArrayProperty(props, PROP_TOKEN, this::token);
        //noinspection unchecked
        classnameProperty(props, PROP_TOKEN_SUPPLIER_CLASS, o -> tokenSupplier((Supplier<char[]>) o));

        booleanProperty(props, PROP_SECURE, this::secure);
        booleanProperty(props, PROP_OPEN_TLS, this::openTls);

        classnameProperty(props, PROP_SSL_CONTEXT_FACTORY_CLASS, o -> sslContextFactory((SSLContextFactory) o));
        stringProperty(props, PROP_KEY_STORE, this::keystorePath);
        charArrayProperty(props, PROP_KEY_STORE_PASSWORD, this::keystorePassword);
        stringProperty(props, PROP_TRUST_STORE, this::truststorePath);
        charArrayProperty(props, PROP_TRUST_STORE_PASSWORD, this::truststorePassword);
        stringProperty(props, PROP_TLS_ALGORITHM, this::tlsAlgorithm);

        stringProperty(props, PROP_CREDENTIAL_PATH, this::credentialPath);

        booleanProperty(props, PROP_NO_RANDOMIZE, this::noRandomize);
        stringProperty(props, PROP_SUBJECT_VALIDATION_TYPE, s -> subjectValidationType(SubjectValidationType.get(s)));

        stringProperty(props, PROP_CONNECTION_NAME, this::connectionName);
        booleanProperty(props, PROP_VERBOSE, this::verbose);
        booleanProperty(props, PROP_NO_ECHO, this::noEcho);
        booleanProperty(props, PROP_CLIENT_SIDE_LIMIT_CHECKS, this::clientSideLimitChecks);
        booleanProperty(props, PROP_SUPPORT_UTF8_SUBJECTS, this::supportUTF8Subjects);
        booleanProperty(props, PROP_PEDANTIC, this::pedantic);

        intProperty(props, PROP_MAX_RECONNECTS, this::maxReconnects);
        millisProperty(props, PROP_RECONNECT_WAIT, this::reconnectWait);
        millisProperty(props, PROP_RECONNECT_JITTER, this::reconnectJitter);
        millisProperty(props, PROP_RECONNECT_JITTER_TLS, this::reconnectJitterTls);
        longGtEqZeroProperty(props, PROP_RECONNECT_BUFFER_SIZE, this::reconnectBufferSize);
        classnameProperty(props, PROP_RECONNECT_DELAY_HANDLER_CLASS, o -> reconnectDelayHandler((ReconnectDelayHandler) o));
        stringProperty(props, PROP_RECONNECT_DELAY_BEHAVIOR, s -> reconnectDelayBehavior(ReconnectDelayBehavior.get(s)));
        millisProperty(props, PROP_CONNECTION_TIMEOUT, this::connectionTimeout);
        longProperty(props, PROP_SOCKET_READ_TIMEOUT, this::socketReadTimeout);
        millisProperty(props, PROP_SOCKET_WRITE_TIMEOUT, this::socketWriteTimeout);
        intProperty(props, PROP_SOCKET_SO_LINGER, this::socketSoLinger);
        intProperty(props, PROP_SOCKET_RECEIVE_BUFFER_SIZE, this::socketReceiveBufferSize);
        intProperty(props, PROP_SOCKET_SEND_BUFFER_SIZE, this::socketSendBufferSize);

        intGtEqZeroProperty(props, PROP_MAX_CONTROL_LINE, this::maxControlLine);
        millisProperty(props, PROP_PING_INTERVAL, this::pingInterval);
        millisProperty(props, PROP_REQUEST_CLEANUP_INTERVAL, this::requestCleanupInterval);
        millisProperty(props, PROP_WRITE_QUEUE_PUSH_TIMEOUT, this::writeQueuePushTimeout);
        intProperty(props, PROP_MAX_PINGS_OUT, this::maxPingsOut);

        classnameProperty(props, PROP_CONNECTION_LISTENER_CLASS, o -> connectionListener((ConnectionListener) o));
        classnameProperty(props, PROP_ERROR_LISTENER_CLASS, o -> errorListener((ErrorListener) o));
        classnameProperty(props, PROP_READ_LISTENER_CLASS, o -> readListener((ReadListener) o));
        classnameProperty(props, PROP_STATISTICS_COLLECTOR_CLASS, o -> statisticsCollector((StatisticsCollector) o));

        stringProperty(props, PROP_DATA_PORT_TYPE, this::dataPortType);
        stringProperty(props, PROP_INBOX_PREFIX, this::inboxPrefix);
        intGtEqZeroProperty(props, PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, this::maxMessagesInOutgoingQueue);
        booleanProperty(props, PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL, this::discardMessagesWhenOutgoingQueueFull);

        booleanProperty(props, PROP_IGNORE_DISCOVERED_SERVERS, this::ignoreDiscoveredServers);
        booleanProperty(props, PROP_TLS_FIRST, this::tlsFirst);
        booleanProperty(props, PROP_USE_TIMEOUT_EXCEPTION, this::useTimeoutException);
        booleanProperty(props, PROP_USE_DISPATCHER_WITH_EXECUTOR, this::useDispatcherWithExecutor);
        booleanProperty(props, PROP_FORCE_FLUSH_ON_REQUEST, this::forceFlushOnRequest);

        stringProperty(props, PROP_HOSTNAME_RESOLVE_MODE, s -> {
            HostnameResolveMode mode = HostnameResolveMode.get(s);
            if (mode != null) {
                hostnameResolveMode(mode);
            }
        });

        classnameProperty(props, PROP_SERVERS_POOL_IMPLEMENTATION_CLASS, o -> serverPool((ServerPool) o));
        classnameProperty(props, PROP_DISPATCHER_FACTORY_CLASS, o -> dispatcherFactory((DispatcherFactory) o));

        classnameProperty(props, PROP_CONNECT_THREAD_FACTORY_CLASS, o -> connectThreadFactory((ThreadFactory) o));
        classnameProperty(props, PROP_CALLBACK_THREAD_FACTORY_CLASS, o -> callbackThreadFactory((ThreadFactory) o));
        classnameProperty(props, PROP_READER_THREAD_FACTORY_CLASS, o -> readerThreadFactory((ThreadFactory) o));
        classnameProperty(props, PROP_WRITER_THREAD_FACTORY_CLASS, o -> writerThreadFactory((ThreadFactory) o));

        classnameProperty(props, PROP_SCHEDULED_EXECUTOR_SERVICE_CLASS, o -> scheduledExecutor((ScheduledExecutorService) o));
        classnameProperty(props, PROP_EXECUTOR_SERVICE_CLASS, o -> executor((ExecutorService) o));
        classnameProperty(props, PROP_CONNECT_EXECUTOR_SERVICE_CLASS, o -> connectExecutor((ExecutorService) o));
        classnameProperty(props, PROP_CALLBACK_EXECUTOR_SERVICE_CLASS, o -> callbackExecutor((ExecutorService) o));
        classnameProperty(props, PROP_READER_EXECUTOR_SERVICE_CLASS, o -> readerExecutor((ExecutorService) o));
        classnameProperty(props, PROP_WRITER_EXECUTOR_SERVICE_CLASS, o -> writerExecutor((ExecutorService) o));
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
     * Set whether to keep the configured server order on reconnect (no randomize).
     * Applied in the default server pool implementation
     * @param noRandomize true to keep the configured order, false to randomize (the default)
     * @return the Builder for chaining
     */
    public OptionsBuilder noRandomize(boolean noRandomize) {
        this.noRandomize = noRandomize;
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
     * Set whether to turn off echo.
     * @param noEcho true to turn off echo
     * @return the Builder for chaining
     */
    public OptionsBuilder noEcho(boolean noEcho) {
        this.noEcho = noEcho;
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
     * Set whether to support UTF8 subjects.
     * @param supportUTF8Subjects true to enable UTF8 subjects
     * @return the Builder for chaining
     */
    public OptionsBuilder supportUTF8Subjects(boolean supportUTF8Subjects) {
        this.supportUTF8Subjects = supportUTF8Subjects;
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
     * Passing {@code null} or empty re-defaults the prefix to {@link OptionsConstants#DEFAULT_INBOX_PREFIX}
     * at {@link #build()} time. A non-empty prefix that does not end in "." has one appended.
     *
     * @param prefix prefix to use, or {@code null}/empty to re-default
     * @return the Builder for chaining
     */
    public OptionsBuilder inboxPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            this.inboxPrefix = null;
            return this;
        }
        this.inboxPrefix = prefix.endsWith(".") ? prefix : prefix + ".";
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
     * Set whether to turn on verbose mode with the server.
     * @param verbose true to turn on verbose mode
     * @return the Builder for chaining
     */
    public OptionsBuilder verbose(boolean verbose) {
        this.verbose = verbose;
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
     * Set whether to turn on pedantic mode for the server.
     * @param pedantic true to turn on pedantic mode
     * @return the Builder for chaining
     */
    public OptionsBuilder pedantic(boolean pedantic) {
        this.pedantic = pedantic;
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
     * Set whether to use the default SSL Context (the SSL context is created at build() time).
     * @param useDefaultTls true to use the default SSL context
     * @return the Builder for chaining
     */
    public OptionsBuilder secure(boolean useDefaultTls) {
        this.useDefaultTls = useDefaultTls;
        return this;
    }

    /**
     * Set the options to use an SSL context that accepts any server certificate and has no client certificates.
     *
     * @return the Builder for chaining
     * @throws NoSuchAlgorithmException <em>Not thrown, deferred to build() method, left in for backward compatibility</em>
     */
    public OptionsBuilder openTls() throws NoSuchAlgorithmException {
        useTrustAllTls = true;
        return this;
    }

    /**
     * Set whether to use an SSL context that accepts any server certificate (the context is created at build() time).
     * @param useTrustAllTls true to use the trust-all SSL context
     * @return the Builder for chaining
     */
    public OptionsBuilder openTls(boolean useTrustAllTls) {
        this.useTrustAllTls = useTrustAllTls;
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
        this.maxReconnects = 0;
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
        this.maxReconnects = max;
        return this;
    }

    /**
     * Set the time, in milliseconds, to wait between reconnect attempts to the same server. This setting is only used
     * by the client when the same server appears twice in the reconnect attempts, either because it is the
     * only known server or by random chance. Note, the randomization of the server list doesn't occur per
     * attempt, it is performed once at the start, so if there are 2 servers in the list you will never encounter
     * the reconnect wait.
     *
     * @param millis the time to wait, in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectWait(long millis) {
        this.reconnectWait = millis;
        return this;
    }

    /**
     * Set the jitter time, in milliseconds, to wait between reconnect attempts to the same server. This setting is used to vary
     * the reconnect wait to avoid multiple clients trying to reconnect to servers at the same time.
     *
     * @param millis the time to wait, in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectJitter(long millis) {
        this.reconnectJitter = millis;
        return this;
    }

    /**
     * Set the jitter time, in milliseconds, for a tls/secure connection to wait between reconnect attempts to the same server.
     * This setting is used to vary the reconnect wait to avoid multiple clients trying to reconnect to
     * servers at the same time.
     *
     * @param millis the time to wait, in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectJitterTls(long millis) {
        this.reconnectJitterTls = millis;
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
     * @param millis the time to wait in milliseconds. A value {@code <= 0} uses the default.
     * @return the Builder for chaining
     */
    public OptionsBuilder connectionTimeout(long millis) {
        this.connectionTimeout = millis <= 0 ? DEFAULT_CONNECTION_TIMEOUT : millis;
        return this;
    }

    /**
     * Set the timeout to use around socket reads. A value {@code <= 0} disables the read timeout.
     * <p>Keep this comfortably longer than the {@link #pingInterval(long) pingInterval} (which defaults to 2 minutes):
     * a socket read timeout shorter than the ping interval can fire during normal idle periods and cause spurious
     * read-timeout disconnects. The library deliberately does not validate this relationship.</p>
     *
     * @param millis the timeout milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder socketReadTimeout(long millis) {
        this.socketReadTimeout = millis < 1 ? 0 : millis; // < 1 disables (connection compares to > 0)
        return this;
    }

    /**
     * Set the timeout to use around socket writes, in milliseconds.
     * A value below {@link OptionsConstants#MINIMUM_SOCKET_WRITE_TIMEOUT} (including {@code <= 0}) means no write timeout.
     *
     * @param millis the timeout milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder socketWriteTimeout(long millis) {
        this.socketWriteTimeout = millis < MINIMUM_SOCKET_WRITE_TIMEOUT ? 0 : millis; // below the minimum (incl. <= 0) means no write timeout
        return this;
    }

    /**
     * Set the value of the socket SO_LINGER property, in <b>seconds</b> — not milliseconds. This is the unit
     * Java's {@code socket.setSoLinger(boolean, int)} takes, and the only timing option here measured in seconds
     * rather than milliseconds, so take care not to pass a millisecond value out of habit.
     * This feature is used by library data port implementations.
     * Setting this is a last resort if socket closes are a problem
     * in your environment, otherwise it's generally not necessary to set this.
     * A value less than 1 disables linger; a value of 1 or greater calls {@code socket.setSoLinger(true, value)}.
     *
     * @param seconds the number of <b>seconds</b> to linger
     * @return the Builder for chaining
     */
    public OptionsBuilder socketSoLinger(int seconds) {
        this.socketSoLinger = seconds < 1 ? -1 : seconds; // < 1 disables
        return this;
    }

    /**
     * Set the value of the socket SO_RCVBUF property in bytes
     * The SO_RCVBUF option is used by the platform's networking code as a hint for the size to set the underlying network I/O buffers.
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     *
     * @param bytes the size in bytes
     * @return the Builder for chaining
     */
    public OptionsBuilder socketReceiveBufferSize(int bytes) {
        this.socketReceiveBufferSize = bytes < 1 ? -1 : bytes; // < 1 uses the OS default
        return this;
    }

    /**
     * Set the value of the socket SO_SNDBUF property in bytes
     * The SO_SNDBUF option is used by the platform's networking code as a hint for the size to set the underlying network I/O buffers.
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     *
     * @param bytes the size in bytes
     * @return the Builder for chaining
     */
    public OptionsBuilder socketSendBufferSize(int bytes) {
        this.socketSendBufferSize = bytes < 1 ? -1 : bytes; // < 1 uses the OS default
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
     * @param millis the time in milliseconds between client to server pings. A value {@code <= 0} disables pings.
     * @return the Builder for chaining
     */
    public OptionsBuilder pingInterval(long millis) {
        this.pingInterval = millis;
        return this;
    }

    /**
     * Set the interval between cleaning passes on outstanding request futures that are cancelled or timeout
     * in the application code.
     *
     * <p>The default value is probably reasonable, but this interval is useful in a very noisy network
     * situation where lots of requests are used.
     *
     * @param millis the cleaning interval in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder requestCleanupInterval(long millis) {
        this.requestCleanupInterval = millis;
        return this;
    }

    /**
     * Set the amount of time to wait to acquire the lock and to offer a message to the outgoing message queue
     *
     * @param millis the wait time in milliseconds
     * @return the Builder for chaining
     */
    public OptionsBuilder writeQueuePushTimeout(long millis) {
        this.writeQueuePushTimeout = millis;
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
     *
     * @param userName a non-empty userName
     * @param password the password
     * @return the Builder for chaining
     */
    public OptionsBuilder userInfo(char[] userName, char[] password) {
        this.username = userName;
        this.password = password;
        return this;
    }

    /**
     * Set the username for basic authentication. See {@link #userInfo(char[], char[])} to set both at once.
     * @param username the username
     * @return the Builder for chaining
     */
    public OptionsBuilder username(char[] username) {
        this.username = username;
        return this;
    }

    /**
     * Set the password for basic authentication. See {@link #userInfo(char[], char[])} to set both at once.
     * @param password the password
     * @return the Builder for chaining
     */
    public OptionsBuilder password(char[] password) {
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
        this.tokenSupplier = tokenSupplier;
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
     * Set the {@link ReconnectDelayBehavior} that controls when the
     * {@link ReconnectDelayHandler} is invoked during reconnect attempts. Defaults to
     * {@link ReconnectDelayBehavior#BeforeSubsequentRounds}. A null value resets to
     * {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
     *
     * @param reconnectDelayBehavior the behavior
     * @return the Builder for chaining
     */
    public OptionsBuilder reconnectDelayBehavior(ReconnectDelayBehavior reconnectDelayBehavior) {
        this.reconnectDelayBehavior = reconnectDelayBehavior == null
            ? ReconnectDelayBehavior.LameDuckAware
            : reconnectDelayBehavior;
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
     * Sets a custom {@link ExecutorService} used to run the connection's reader.
     * Takes precedence over {@link #readerThreadFactory(ThreadFactory) readerThreadFactory}; the caller
     * owns this executor's lifecycle (it is not shut down by the connection). When neither is set, the
     * reader uses the shared connection executor.
     *
     * @param readerExecutor the executor service to run the reader
     * @return the Builder for chaining
     */
    public OptionsBuilder readerExecutor(ExecutorService readerExecutor) {
        this.userReaderExecutor = readerExecutor;
        return this;
    }

    /**
     * Sets a custom {@link ExecutorService} used to run the connection's writer.
     * Takes precedence over {@link #writerThreadFactory(ThreadFactory) writerThreadFactory}; the caller
     * owns this executor's lifecycle (it is not shut down by the connection). When neither is set, the
     * writer uses the shared connection executor.
     *
     * @param writerExecutor the executor service to run the writer
     * @return the Builder for chaining
     */
    public OptionsBuilder writerExecutor(ExecutorService writerExecutor) {
        this.userWriterExecutor = writerExecutor;
        return this;
    }

    /**
     * Sets a custom thread factory used to run the connection's reader.
     * If both readerThreadFactory and readerExecutor are set, only readerExecutor is used.
     *
     * @param threadFactory the thread factory to use for the reader
     * @return the Builder for chaining
     */
    public OptionsBuilder readerThreadFactory(ThreadFactory threadFactory) {
        this.userReaderThreadFactory = threadFactory;
        return this;
    }

    /**
     * Sets a custom thread factory used to run the connection's writer.
     * If both writerThreadFactory and writerExecutor are set, only writerExecutor is used.
     *
     * @param threadFactory the thread factory to use for the writer
     * @return the Builder for chaining
     */
    public OptionsBuilder writerThreadFactory(ThreadFactory threadFactory) {
        this.userWriterThreadFactory = threadFactory;
        return this;
    }

    /**
     * Add an HttpRequest interceptor which can be used to modify the HTTP request when using websockets
     *
     * @param interceptor The interceptor
     * @return the Builder for chaining
     */
    public OptionsBuilder httpRequestInterceptor(Consumer<HttpRequest> interceptor) {
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
     * Set whether to discard messages when the outgoing queue is full.
     * @param discardMessagesWhenOutgoingQueueFull true to discard
     * @return the Builder for chaining
     */
    public OptionsBuilder discardMessagesWhenOutgoingQueueFull(boolean discardMessagesWhenOutgoingQueueFull) {
        this.discardMessagesWhenOutgoingQueueFull = discardMessagesWhenOutgoingQueueFull;
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
     * Set whether to ignore discovered servers when connecting / reconnecting.
     * @param ignoreDiscoveredServers true to ignore discovered servers
     * @return the Builder for chaining
     */
    public OptionsBuilder ignoreDiscoveredServers(boolean ignoreDiscoveredServers) {
        this.ignoreDiscoveredServers = ignoreDiscoveredServers;
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
     * Set TLS Handshake First behavior.
     * @param tlsFirst true to perform the TLS handshake first
     * @return the Builder for chaining
     */
    public OptionsBuilder tlsFirst(boolean tlsFirst) {
        this.tlsFirst = tlsFirst;
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
     * Set whether to throw {@link java.util.concurrent.TimeoutException} on timeout instead of {@link java.util.concurrent.CancellationException}.
     * @param useTimeoutException true to throw TimeoutException
     * @return the Builder for chaining
     */
    public OptionsBuilder useTimeoutException(boolean useTimeoutException) {
        this.useTimeoutException = useTimeoutException;
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
     * Set whether dispatchers dispatch messages as a task (via the executor) instead of directly from the dispatcher thread.
     * @param useDispatcherWithExecutor true to dispatch via the executor
     * @return the Builder for chaining
     */
    public OptionsBuilder useDispatcherWithExecutor(boolean useDispatcherWithExecutor) {
        this.useDispatcherWithExecutor = useDispatcherWithExecutor;
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
     * Set whether requests force a flush of the outgoing buffer after publishing.
     * @param forceFlushOnRequest true to force a flush on requests
     * @return the Builder for chaining
     */
    public OptionsBuilder forceFlushOnRequest(boolean forceFlushOnRequest) {
        this.forceFlushOnRequest = forceFlushOnRequest;
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
        // Resolve build-time defaults for sentinel-null fields. These must run before any code
        // that dereferences the field (e.g. the username/tokenSupplier conflict check below).
        if (tokenSupplier == null) {
            tokenSupplier = new Options.DefaultTokenSupplier();
        }

        if (inboxPrefix == null) {
            inboxPrefix = DEFAULT_INBOX_PREFIX;
        }

        if (reconnectDelayHandler == null) {
            reconnectDelayHandler = DefaultReconnectDelayHandler.INSTANCE;
        }

        if (this.username != null && tokenSupplier.get() != null) {
            throw new IllegalStateException("Options can't have token and username");
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

        // socketReadTimeout / socketWriteTimeout / socketSoLinger / socketReceiveBufferSize / socketSendBufferSize
        // are normalized in their setters (and the field initial values are already the normalized defaults),
        // and the property loaders route through those setters — so no build()-time clamp is needed here.

        if (errorListener == null) {
            errorListener = new ErrorListener() {};
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
     * @throws IllegalArgumentException if the options is null
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
        this.connectionName = o.connectionName;
        this.verbose = o.verbose;
        this.pedantic = o.pedantic;
        this.sslContext = o.sslContext;
        this.maxReconnects = o.maxReconnects;
        this.reconnectWait = o.reconnectWait;
        this.reconnectJitter = o.reconnectJitter;
        this.reconnectJitterTls = o.reconnectJitterTls;
        this.connectionTimeout = o.connectionTimeout;
        this.socketReadTimeout = o.socketReadTimeout;
        this.socketWriteTimeout = o.socketWriteTimeout;
        this.socketSoLinger = o.socketSoLinger;
        this.socketReceiveBufferSize = o.socketReceiveBufferSize;
        this.socketSendBufferSize = o.socketSendBufferSize;
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
        this.clientSideLimitChecks = o.clientSideLimitChecks;
        this.supportUTF8Subjects = o.supportUTF8Subjects;
        this.inboxPrefix = o.inboxPrefix;
        this.maxMessagesInOutgoingQueue = o.maxMessagesInOutgoingQueue;
        this.discardMessagesWhenOutgoingQueueFull = o.discardMessagesWhenOutgoingQueueFull;

        this.authHandler = o.authHandler;
        this.reconnectDelayHandler = o.reconnectDelayHandler;
        this.reconnectDelayBehavior = o.reconnectDelayBehavior;

        this.errorListener = o.errorListener;
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
        this.userReaderExecutor = o.userReaderExecutor;
        this.userWriterExecutor = o.userWriterExecutor;
        this.userReaderThreadFactory = o.userReaderThreadFactory;
        this.userWriterThreadFactory = o.userWriterThreadFactory;

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
