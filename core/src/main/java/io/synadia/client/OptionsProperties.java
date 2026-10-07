package io.synadia.client;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static io.synadia.client.utils.Validator.emptyAsNull;

/**
 * Constants and static functions used by {@link Options} and the connection machinery.
 * These property helpers throw {@link IllegalArgumentException} when a property value cannot be parsed or applied.
 */
public interface OptionsProperties {
    // ----------------------------------------------------------------------------------------------------
    // ENVIRONMENT PROPERTIES
    // ----------------------------------------------------------------------------------------------------
    /** Prefix all option property keys may carry. Keys are accepted with or without it. {@value} */
    String PFX = "io.nats.client.";

    /** Length of {@link #PFX}, used when stripping the prefix from a key. */
    int PFX_LEN = PFX.length();

    /**
     * Property used to configure the connection callback. Accepts a comma-separated list of class names
     * to configure more than one listener. {@value}
     */
    String PROP_CONNECTION_LISTENER_CLASS = PFX + "connectionListenerClass";
    /**
     * Property used to configure the error listener. Accepts a comma-separated list of class names
     * to configure more than one listener. {@value}
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
    String PROP_MAX_PINGS_OUT = PFX + "maxPingsOut";
    /**
     * Property used to configure ping interval. {@value}
     */
    String PROP_PING_INTERVAL = PFX + "pingInterval";
    /**
     * Property used to configure request cleanup interval. {@value}
     */
    String PROP_REQUEST_CLEANUP_INTERVAL = PFX + "requestCleanupInterval";
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
    String PROP_RECONNECT_BUFFER_SIZE = PFX + "reconnectBufferSize";
    /**
     * Property used to configure reconnect wait, in milliseconds (plain integer). {@value}
     */
    String PROP_RECONNECT_WAIT = PFX + "reconnectWait";
    /**
     * Property used to configure max reconnects. {@value}
     */
    String PROP_MAX_RECONNECTS = PFX + "maxReconnects";
    /**
     * Property used to configure reconnect jitter, in milliseconds (plain integer). {@value}
     */
    String PROP_RECONNECT_JITTER = PFX + "reconnectJitter";
    /**
     * Property used to configure reconnect jitter for TLS, in milliseconds (plain integer). {@value}
     */
    String PROP_RECONNECT_JITTER_TLS = PFX + "reconnectJitterTls";
    /**
     * Property used to set the class name for the {@link ReconnectDelayHandler} implementation. {@value}
     * The class must have a public no-arg constructor.
     */
    String PROP_RECONNECT_DELAY_HANDLER_CLASS = PFX + "reconnectDelayHandlerClass";
    /**
     * Property used to set the {@link ReconnectDelayBehavior}. {@value} The value is the case-insensitive
     * name of a {@link ReconnectDelayBehavior} constant ({@code BeforeSubsequentRounds}, {@code BeforeAllRounds}
     * or {@code LameDuckAware}).
     * Unrecognized or missing values fall back to {@link ReconnectDelayBehavior#BeforeSubsequentRounds}.
     */
    String PROP_RECONNECT_DELAY_BEHAVIOR = PFX + "reconnectDelayBehavior";
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
     * Property used to configure connection name. {@value}
     */
    String PROP_CONNECTION_NAME = PFX + "connectionName";
    /**
     * Property used to configure noRandomize. {@value}
     */
    String PROP_NO_RANDOMIZE = PFX + "noRandomize";
    /**
     * Property used to configure hostname resolve mode. {@value}
     * The value is the case-insensitive name of a {@link HostnameResolveMode} constant
     * ({@code ResolveToAll}, {@code ResolveToFirst}, {@code ResolveToAllIncludeIPV6},
     * {@code ResolveToFirstIncludeIPV6}, {@code Unresolved} or {@code HappyEyeballs}).
     * Unlike the other enum properties, an unrecognized value is ignored rather than falling back:
     * the current value stays, which is {@link HostnameResolveMode#ResolveToAll} unless already set.
     */
    String PROP_HOSTNAME_RESOLVE_MODE = PFX + "hostnameResolveMode";
    /**
     * Property used to set the {@link SubjectValidationType}. {@value} The value is the case-insensitive
     * name of a {@link SubjectValidationType} constant (e.g. {@code None}, {@code Lenient}, {@code Strict}).
     * Unrecognized or missing values fall back to {@link SubjectValidationType#Lenient}.
     */
    String PROP_SUBJECT_VALIDATION_TYPE = PFX + "subjectValidationType";
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
     * Property used to choose the connection implementation.
     * The value is the case-insensitive name of a {@link ConnectionImplementation} constant
     * ({@code Classic} or {@code V3}). An unrecognized value is ignored. {@value}
     */
    String PROP_CONNECTION_IMPLEMENTATION = PFX + "connectionImplementation";
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
    /**
     * Property used to set class name for the Reader Executor Service. {@value}
     */
    String PROP_READER_EXECUTOR_SERVICE_CLASS = PFX + "readerExecutorServiceClass";
    /**
     * Property used to set class name for the Writer Executor Service. {@value}
     */
    String PROP_WRITER_EXECUTOR_SERVICE_CLASS = PFX + "writerExecutorServiceClass";
    /**
     * Property used to set class name for the Reader Thread Factory. {@value}
     */
    String PROP_READER_THREAD_FACTORY_CLASS = PFX + "readerThreadFactoryClass";
    /**
     * Property used to set class name for the Writer Thread Factory. {@value}
     */
    String PROP_WRITER_THREAD_FACTORY_CLASS = PFX + "writerThreadFactoryClass";

    /**
     * Look up a property value, tolerating the several key spellings the client accepts: with the
     * {@link #PFX} prefix, without it, and with underscores in place of dots.
     * @param props the properties to read
     * @param key the key to look up
     * @return the value, or null if not present or empty
     */
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

    /**
     * Reads a string property and passes it to the consumer when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present
     */
    static void stringProperty(Properties props, String key, Consumer<String> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(value);
        }
    }

    /**
     * Reads a string property and passes it to the consumer as a char array when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present
     */
    static void charArrayProperty(Properties props, String key, Consumer<char[]> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(value.toCharArray());
        }
    }

    /**
     * Reads a boolean property and passes it to the consumer when present. Any value other than
     * "true" (ignoring case) parses as false.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present
     */
    static void booleanProperty(Properties props, String key, Consumer<Boolean> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Boolean.parseBoolean(value));
        }
    }

    /**
     * Reads a boolean property and calls the consumer only when it parses as true, leaving the
     * setting untouched otherwise. Use for flags whose default must not be overwritten by a false.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives true when the property is present and true
     */
    static void booleanPropertyIfTrue(Properties props, String key, Consumer<Boolean> consumer) {
        if (Boolean.parseBoolean(getPropertyValue(props, key))) { // parseBoolean treats null as false
            consumer.accept(true);
        }
    }

    /**
     * Reads an integer property and passes it to the consumer when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present
     * @throws IllegalArgumentException if the property value is not a valid integer
     */
    static void intProperty(Properties props, String key, Consumer<Integer> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Integer.parseInt(value));
        }
    }

    /**
     * Reads an integer property and passes it to the consumer when present and greater than or equal to zero.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present and not negative
     * @throws IllegalArgumentException if the property value is not a valid integer
     */
    static void intGtEqZeroProperty(Properties props, String key, Consumer<Integer> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            int i = Integer.parseInt(value);
            if (i >= 0) {
                consumer.accept(i);
            }
        }
    }

    /**
     * Reads a long property and passes it to the consumer when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present
     * @throws IllegalArgumentException if the property value is not a valid long
     */
    static void longProperty(Properties props, String key, Consumer<Long> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            consumer.accept(Long.parseLong(value));
        }
    }

    /**
     * Reads a long property and passes it to the consumer when present and greater than or equal to zero.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value when the property is present and not negative
     * @throws IllegalArgumentException if the property value is not a valid long
     */
    static void longGtEqZeroProperty(Properties props, String key, Consumer<Long> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            long l = Long.parseLong(value);
            if (l >= 0) {
                consumer.accept(l);
            }
        }
    }

    /**
     * Reads a millisecond timing property. The value may be a plain integer number of milliseconds
     * (e.g. {@code 2000}) or an ISO-8601 duration string (e.g. {@code PT2S}), which is converted to
     * whole milliseconds. The plain millisecond number is tried first (the common case). Negative
     * values are ignored (the default is kept); a value that is neither throws {@link IllegalArgumentException}.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the value in whole milliseconds when the property is present and not negative
     * @throws IllegalArgumentException if the property value is not a valid milliseconds value or ISO-8601 duration
     */
    static void millisProperty(Properties props, String key, Consumer<Long> consumer) {
        String value = getPropertyValue(props, key);
        if (value != null) {
            long millis;
            try {
                millis = Long.parseLong(value); // plain milliseconds (the common case)
            }
            catch (NumberFormatException nfe) {
                try {
                    millis = Duration.parse(value).toMillis(); // ISO-8601 duration form, e.g. PT2S
                }
                catch (DateTimeParseException pe) {
                    throw new IllegalArgumentException("Property '" + key + "' value '" + value
                        + "' is not a valid number of milliseconds or an ISO-8601 duration.");
                }
            }
            if (millis >= 0) {
                consumer.accept(millis);
            }
        }
    }

    /**
     * Reads a class name property, instantiates the named class, and passes the instance to the consumer when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the instance when the property is present
     * @throws IllegalArgumentException if the class cannot be found, has no no-argument constructor, or cannot be instantiated
     */
    static void classnameProperty(Properties props, String key, Consumer<Object> consumer) {
        stringProperty(props, key, className -> consumer.accept(createInstanceOf(className)));
    }

    /**
     * Reads a class name property holding one or more comma-separated class names, instantiates each named class,
     * and passes the instances to the consumer as a list when present.
     * @param props the properties to read
     * @param key the key to look up
     * @param consumer receives the instances when the property is present
     * @throws IllegalArgumentException if any class cannot be found, has no no-argument constructor, or cannot be instantiated
     */
    static void classnameListProperty(Properties props, String key, Consumer<List<Object>> consumer) {
        stringProperty(props, key, value -> {
            List<Object> instances = new ArrayList<>();
            for (String className : value.trim().split(",\\s*")) {
                String trimmed = emptyAsNull(className);
                if (trimmed != null) {
                    instances.add(createInstanceOf(trimmed));
                }
            }
            consumer.accept(instances);
        });
    }

    /**
     * Creates an instance of the named class using its no-argument constructor.
     * @param className the fully qualified class name
     * @return the new instance
     * @throws IllegalArgumentException if the class cannot be found, has no no-argument constructor, or cannot be instantiated
     */
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
