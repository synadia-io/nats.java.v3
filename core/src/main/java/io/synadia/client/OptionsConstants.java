package io.synadia.client;

import io.synadia.client.impl.SocketDataPort;
import io.synadia.client.testutils.NatsConstants;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Constants used by {@link Options} and the connection machinery.
 * <p>Extracted from {@code Options} to reduce that class's size and
 * make the surface easier to reference.</p>
 */
public interface OptionsConstants {
    /**
     * Default server URL. This property is defined as {@value}
     */
    String DEFAULT_URL = "nats://localhost:4222";

    /**
     * Default server port. This property is defined as {@value}
     */
    int DEFAULT_PORT = NatsConstants.DEFAULT_PORT;

    /**
     * Default maximum number of reconnect attempts.
     * This property is defined as {@value}
     */
    int DEFAULT_MAX_RECONNECT = 60;

    /**
     * Default wait time before attempting reconnection to the same server.
     * This property is defined as 2000 milliseconds (2 seconds).
     */
    Duration DEFAULT_RECONNECT_WAIT = Duration.ofMillis(2000);

    /**
     * Default reconnect jitter. Defined as 100 milliseconds.
     */
    Duration DEFAULT_RECONNECT_JITTER = Duration.ofMillis(100);

    /**
     * Default reconnect jitter for TLS. Defined as 1000 milliseconds (1 second).
     */
    Duration DEFAULT_RECONNECT_JITTER_TLS = Duration.ofMillis(1000);

    /**
     * Default connection timeout. Defined as 2 seconds.
     */
    Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Default socket write timeout. Defined as 1 minute.
     */
    Duration DEFAULT_SOCKET_WRITE_TIMEOUT = Duration.ofMinutes(1);

    /**
     * Set to 100 nanos to ensure that the scheduled task can execute.
     */
    long MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS = 100;

    /**
     * Default server ping interval. The client will send a ping to the server on this interval to insure liveness.
     * <p>A value of {@code <=0} means disabled.</p>
     * <p>Defined as 2 minutes.</p>
     */
    Duration DEFAULT_PING_INTERVAL = Duration.ofMinutes(2);

    /**
     * Default interval to clean up cancelled/timed out requests.
     * <p>Defined as 5 seconds.</p>
     */
    Duration DEFAULT_REQUEST_CLEANUP_INTERVAL = Duration.ofSeconds(5);

    /**
     * Default amount of time to try to add something to the outgoing queue.
     * <p>Defined as 2 seconds.</p>
     */
    Duration DEFAULT_WRITE_QUEUE_PUSH_TIMEOUT = Duration.ofSeconds(2);

    /**
     * The minimum amount of time to try to add something to the outgoing queue.
     * <p>Defined as 50 milliseconds.</p>
     */
    Duration MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT = Duration.ofMillis(50);

    /**
     * Default maximum number of pings without a response allowed by the client.
     * <p>Defined as {@value}</p>
     */
    int DEFAULT_MAX_PINGS_OUT = 2;

    /**
     * Default SSL protocol used to create an SSLContext if {@link OptionsProperties#PROP_SECURE} is used.
     * <p>Defined as {@value}</p>
     */
    String DEFAULT_SSL_PROTOCOL = "TLSv1.2";

    /**
     * Default size of the pending message buffer used during disconnect/reconnect.
     * <p>Defined as {@value} bytes, 8 * 1024 * 1024.</p>
     */
    int DEFAULT_RECONNECT_BUF_SIZE = 8_388_608;

    /**
     * The default length, {@value} bytes, the client will allow in an outgoing protocol control line.
     * <p>This value is configurable on the server, and should be set here to match.</p>
     */
    int DEFAULT_MAX_CONTROL_LINE = 4096;

    /**
     * Default dataport class, which will use a TCP socket.
     * <p><em>This option is currently provided only for testing and experimentation; the default
     * should be used in almost all cases.</em></p>
     */
    String DEFAULT_DATA_PORT_TYPE = SocketDataPort.class.getCanonicalName();

    /**
     * Default size for buffers in the connection.
     */
    int DEFAULT_BUFFER_SIZE = 64 * 1024;

    /**
     * Default thread name prefix used by the default executor.
     * Defined as {@value}
     */
    String DEFAULT_THREAD_NAME_PREFIX = "nats";

    /**
     * Default prefix used for inboxes. The trailing {@code .} is required but will be added if missing.
     */
    String DEFAULT_INBOX_PREFIX = "_INBOX.";

    /**
     * Internal limit on the number of messages sent in a single network I/O.
     */
    int MAX_MESSAGES_IN_NETWORK_BUFFER = 1000;

    /**
     * Internal limit on the number of messages allowed in the outgoing queue.
     */
    int DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE = 5000;

    /**
     * Whether to discard messages when the outgoing queue is full.
     */
    boolean DEFAULT_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL = false;

    /**
     * Default supplier for creating a single-threaded executor service.
     */
    Supplier<ExecutorService> DEFAULT_SINGLE_THREAD_EXECUTOR = Executors::newSingleThreadExecutor;
}
