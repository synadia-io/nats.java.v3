package io.synadia.client;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Properties;

import static io.synadia.client.utils.Validator.emptyAsNull;

/**
 * Constants and static functions used by {@link Options} and the connection machinery.
 */
public interface OptionsProperties {
    // ----------------------------------------------------------------------------------------------------
    // ENVIRONMENT PROPERTIES
    // ----------------------------------------------------------------------------------------------------
    String PFX = "io.nats.client.";
    int PFX_LEN = PFX.length();

    /**
     * Property used to configure the connection callback. {@value}
     */
    String PROP_CONNECTION_LISTENER_CLASS = PFX + "connectionListenerClass";
    /**
     * Property used to configure the error listener. {@value}
     */
    String PROP_ERROR_LISTENER_CLASS = PFX + "errorListenerClass";
    /**
     * Property used to set class name for the ReadListener implementation. {@value}
     */
    String PROP_READ_LISTENER_CLASS = PFX + "readListenerClass";
    /**
     * Property used to configure the data port type. {@value}
     */
    String PROP_DATA_PORT_TYPE = PFX + "dataPortType";
    /**
     * Property used to configure the statistics collector. {@value}
     */
    String PROP_STATISTICS_COLLECTOR_CLASS = PFX + "statisticsCollectorClass";
    /**
     * Property used to configure max pings out. {@value}
     */
    String PROP_MAX_PINGS = PFX + "maxPings";
    /**
     * Property used to configure ping interval. {@value}
     */
    String PROP_PING_INTERVAL = PFX + "pingInterval";
    /**
     * Property used to configure request cleanup interval. {@value}
     */
    String PROP_CLEANUP_INTERVAL = PFX + "cleanupInterval";
    /**
     * Property used to configure write queue push timeout. {@value}
     */
    String PROP_WRITE_QUEUE_PUSH_TIMEOUT = PFX + "writeQueuePushTimeout";
    /**
     * Property used to configure connection timeout. {@value}
     */
    String PROP_CONNECTION_TIMEOUT = PFX + "connectionTimeout";
    /**
     * Property used to configure the socket read timeout (milliseconds). {@value}
     */
    String PROP_SOCKET_READ_TIMEOUT = PFX + "socketReadTimeout";
    /**
     * Property used to configure socket write timeout. {@value}
     */
    String PROP_SOCKET_WRITE_TIMEOUT = PFX + "socketWriteTimeout";
    /**
     * Property used to configure socket SO_LINGER. {@value}
     */
    String PROP_SOCKET_SO_LINGER = PFX + "socketSoLinger";
    /**
     * Property used to configure socket receive buffer size. {@value}
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     */
    String PROP_SOCKET_RECEIVE_BUFFER_SIZE = PFX + "socketReceiveBufferSize";
    /**
     * Property used to configure socket send buffer size. {@value}
     * OVERRIDES THE UNDERLYING JAVA SOCKET IMPLEMENTATION - USE AT YOUR OWN RISK
     */
    String PROP_SOCKET_SEND_BUFFER_SIZE = PFX + "socketSendBufferSize";
    /**
     * Property used to configure reconnect buffer size. {@value}
     */
    String PROP_RECONNECT_BUF_SIZE = PFX + "reconnectBufSize";
    /**
     * Property used to configure reconnect wait. {@value}
     */
    String PROP_RECONNECT_WAIT = PFX + "reconnectWait";
    /**
     * Property used to configure max reconnects. {@value}
     */
    String PROP_MAX_RECONNECT = PFX + "maxReconnect";
    /**
     * Property used to configure reconnect jitter. {@value}
     */
    String PROP_RECONNECT_JITTER = PFX + "reconnectJitter";
    /**
     * Property used to configure reconnect jitter for TLS. {@value}
     */
    String PROP_RECONNECT_JITTER_TLS = PFX + "reconnectJitterTls";
    /**
     * Property used to configure pedantic mode. {@value}
     */
    String PROP_PEDANTIC = PFX + "pedantic";
    /**
     * Property used to configure verbose mode. {@value}
     */
    String PROP_VERBOSE = PFX + "verbose";
    /**
     * Property used to configure noEcho. {@value}
     */
    String PROP_NO_ECHO = PFX + "noEcho";
    /**
     * Property used to configure noHeaders. {@value}
     */
    String PROP_NO_HEADERS = PFX + "noHeaders";
    /**
     * Property used to configure connection name. {@value}
     */
    String PROP_CONNECTION_NAME = PFX + "connectionName";
    /**
     * Property used to configure noNoResponders. {@value}
     */
    String PROP_NO_NO_RESPONDERS = PFX + "noNoResponders";
    /**
     * Property used to configure noRandomize. {@value}
     */
    String PROP_NO_RANDOMIZE = PFX + "noRandomize";
    /**
     * Property used to configure hostname resolve mode. {@value}
     * Takes precedence over PROP_NO_RESOLVE_HOSTNAMES and PROP_FAST_FALLBACK.
     */
    String PROP_HOSTNAME_RESOLVE_MODE = PFX + "hostnameResolveMode";
    /**
     * Property used to configure noSubjectValidation. {@value}
     */
    String PROP_NO_SUBJECT_VALIDATION = PFX + "noSubjectValidation";
    /**
     * Property used to configure strictSubjectValidation. {@value}
     */
    String PROP_STRICT_SUBJECT_VALIDATION = PFX + "strictSubjectValidation";
    /**
     * Property used to configure clientSideLimitChecks. {@value}
     */
    String PROP_CLIENT_SIDE_LIMIT_CHECKS = PFX + "clientSideLimitChecks";
    /**
     * Property used to configure servers. {@value} The value can be a comma-separated list of server URLs.
     */
    String PROP_SERVERS = PFX + "servers";
    /**
     * Property used to configure password. {@value}
     */
    String PROP_PASSWORD = PFX + "password";
    /**
     * Property used to configure username. {@value}
     */
    String PROP_USERNAME = PFX + "username";
    /**
     * Property used to configure token. {@value}
     */
    String PROP_TOKEN = PFX + "token";
    /**
     * Property used to configure the token supplier. {@value}
     */
    String PROP_TOKEN_SUPPLIER_CLASS = PFX + "tokenSupplierClass";
    /**
     * Property used to configure server URL. {@value}
     */
    String PROP_URL = PFX + "url";
    /**
     * Property used to enable secure mode. {@value}
     * Boolean flag — tells the options parser to use the default SSL context.
     */
    String PROP_SECURE = PFX + "secure";
    /**
     * Property used to enable open TLS mode. {@value}
     * Boolean flag — uses an SSL context that takes any server TLS certificate. The server must have tls_verify OFF.
     */
    String PROP_OPEN_TLS = PFX + "openTls";
    /**
     * Property used to configure max messages in outgoing queue. {@value}
     */
    String PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE = PFX + "maxMessagesInOutgoingQueue";
    /**
     * Property used to configure discard messages when outgoing queue is full. {@value}
     */
    String PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL = PFX + "discardMessagesWhenOutgoingQueueFull";
    /**
     * Property used to configure max control line. {@value}
     */
    String PROP_MAX_CONTROL_LINE = PFX + "maxControlLine";
    /**
     * Property used to set the inbox prefix. {@value}
     */
    String PROP_INBOX_PREFIX = PFX + "inboxPrefix";
    /**
     * Property used to set whether to ignore discovered servers when connecting. {@value}
     */
    String PROP_IGNORE_DISCOVERED_SERVERS = PFX + "ignoreDiscoveredServers";
    /**
     * Property used to set class name for ServerPool implementation. {@value}
     */
    String PROP_SERVERS_POOL_IMPLEMENTATION_CLASS = PFX + "serversPoolImplementationClass";
    /**
     * Property used to set class name for the Dispatcher Factory. {@value}
     */
    String PROP_DISPATCHER_FACTORY_CLASS = PFX + "dispatcherFactoryClass";
    /**
     * Property used to set class name for the SSLContextFactory. {@value}
     */
    String PROP_SSL_CONTEXT_FACTORY_CLASS = PFX + "sslContextFactoryClass";
    /**
     * Property for the keystore path used to create an SSLContext. {@value}
     */
    String PROP_KEY_STORE = PFX + "keyStore";
    /**
     * Property for the keystore password used to create an SSLContext. {@value}
     */
    String PROP_KEY_STORE_PASSWORD = PFX + "keyStorePassword";
    /**
     * Property for the truststore path used to create an SSLContext. {@value}
     */
    String PROP_TRUST_STORE = PFX + "trustStore";
    /**
     * Property for the truststore password used to create an SSLContext. {@value}
     */
    String PROP_TRUST_STORE_PASSWORD = PFX + "trustStorePassword";
    /**
     * Property for the algorithm used to create an SSLContext. {@value}
     */
    String PROP_TLS_ALGORITHM = PFX + "tlsAlgorithm";
    /**
     * Property used to set the path to a credentials file to be used in a FileAuthHandler. {@value}
     */
    String PROP_CREDENTIAL_PATH = PFX + "credentialPath";
    /**
     * Property used to configure tls-first behavior. {@value}
     */
    String PROP_TLS_FIRST = PFX + "tlsFirst";
    /**
     * Property used to configure support for UTF8 subjects. {@value}
     */
    String PROP_SUPPORT_UTF8_SUBJECTS = PFX + "supportUtf8Subjects";
    /**
     * Property used to throw {@link java.util.concurrent.TimeoutException} on timeout instead of {@link java.util.concurrent.CancellationException}. {@value}
     */
    String PROP_USE_TIMEOUT_EXCEPTION = PFX + "useTimeoutException";
    /**
     * Property used to make a dispatcher dispatch messages via the executor service instead of with a blocking call. {@value}
     */
    String PROP_USE_DISPATCHER_WITH_EXECUTOR = PFX + "useDispatcherWithExecutor";
    /**
     * Property used to enable forceFlushOnRequest. {@value}
     */
    String PROP_FORCE_FLUSH_ON_REQUEST = PFX + "forceFlushOnRequest";
    /**
     * Property used to set class name for the Executor Service (executor) class. {@value}
     */
    String PROP_EXECUTOR_SERVICE_CLASS = PFX + "executorServiceClass";
    /**
     * Property used to set class name for the Scheduled Executor Service. {@value}
     */
    String PROP_SCHEDULED_EXECUTOR_SERVICE_CLASS = PFX + "scheduledExecutorServiceClass";
    /**
     * Property used to set class name for the Connect Executor Service. {@value}
     */
    String PROP_CONNECT_EXECUTOR_SERVICE_CLASS = PFX + "connectExecutorServiceClass";
    /**
     * Property used to set class name for the Callback Executor Service. {@value}
     */
    String PROP_CALLBACK_EXECUTOR_SERVICE_CLASS = PFX + "callbackExecutorServiceClass";
    /**
     * Property used to set class name for the Connect Thread Factory. {@value}
     */
    String PROP_CONNECT_THREAD_FACTORY_CLASS = PFX + "connectThreadFactoryClass";
    /**
     * Property used to set class name for the Callback Thread Factory. {@value}
     */
    String PROP_CALLBACK_THREAD_FACTORY_CLASS = PFX + "callbackThreadFactoryClass";

    static String getPropertyValue(Properties props, String key) {
        String value = emptyAsNull(props.getProperty(key));
        if (value != null) {
            return value;
        }
        if (key.startsWith(PFX)) { // if the key starts with the PFX, check the non PFX
            return emptyAsNull(props.getProperty(key.substring(PFX_LEN)));
        }
        // otherwise check with the PFX
        value = emptyAsNull(props.getProperty(PFX + key));
        if (value == null && key.contains("_")) {
            // addressing where underscore was used in a key value instead of dot
            return getPropertyValue(props, key.replace("_", "."));
        }
        return value;
    }

    static void stringProperty(Properties props, String key, java.util.function.Consumer<String> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(value);
        }
    }

    static void charArrayProperty(Properties props, String key, java.util.function.Consumer<char[]> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(value.toCharArray());
        }
    }

    static void booleanProperty(Properties props, String key, java.util.function.Consumer<Boolean> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Boolean.parseBoolean(value));
        }
    }

    static void booleanPropertyIfTrue(Properties props, String key, java.util.function.Consumer<Boolean> consumer) {
        if (Boolean.parseBoolean(getPropertyValue(props, key))) { // parseBoolean treats null as false
            consumer.accept(true);
        }
    }

    static void intProperty(Properties props, String key, java.util.function.Consumer<Integer> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Integer.parseInt(value));
        }
    }

    static void intGtEqZeroProperty(Properties props, String key, java.util.function.Consumer<Integer> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            int i = Integer.parseInt(value);
            if (i >= 0) {
                consumer.accept(i);
            }
        }
    }

    static void longProperty(Properties props, String key, java.util.function.Consumer<Long> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Long.parseLong(value));
        }
    }

    static void durationProperty(Properties props, String key, java.util.function.Consumer<Duration> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            try {
                Duration d = Duration.parse(value);
                if (d.toNanos() >= 0) {
                    consumer.accept(d);
                }
            }
            catch (DateTimeParseException pe) {
                int ms = Integer.parseInt(value);
                if (ms >= 0) {
                    consumer.accept(Duration.ofMillis(ms));
                }
            }
        }
    }

    static void classnameProperty(Properties props, String key, java.util.function.Consumer<Object> consumer) {
        stringProperty(props, key, className -> consumer.accept(createInstanceOf(className)));
    }

    static Object createInstanceOf(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor();
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
