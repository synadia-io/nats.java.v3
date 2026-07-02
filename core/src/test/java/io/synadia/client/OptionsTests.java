package io.synadia.client;

import io.nats.nkey.NKey;
import io.nats.nkey.NKeyProvider;
import io.synadia.client.impl.*;
import io.synadia.client.utils.*;
import io.synadia.client.utils.ssl.SslTestingHelper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.nats.json.Encoding.base64UrlEncodeToString;
import static io.synadia.client.OptionsConstants.*;
import static io.synadia.client.OptionsProperties.*;
import static io.synadia.client.utils.NatsConstants.DEFAULT_PORT;
import static io.synadia.client.utils.ResourceUtils.jwtResource;
import static org.junit.jupiter.api.Assertions.*;

public class OptionsTests extends TestBase {

    public static final String URL_PROTO_HOST_PORT_8080 = "nats://localhost:8080";
    public static final String URL_PROTO_HOST_PORT_8081 = "nats://localhost:8081";
    public static final String URL_HOST_PORT_8081 = "localhost:8081";

    @Test
    public void testClientVersion() {
        assertFalse(Nats.CLIENT_VERSION.isEmpty());
        // Either running in an IDE (no generated properties file) or a proper semver x.y.z
        boolean isDevelopment = Nats.CLIENT_VERSION.equals("development");
        boolean isSemVer = Nats.CLIENT_VERSION.indexOf(".") != Nats.CLIENT_VERSION.lastIndexOf(".");
        assertTrue(isDevelopment || isSemVer);
    }

    @Test
    public void testDefaultOptions() {
        Options o = new OptionsBuilder().build();
        _testDefaultOptions(o);
        _testDefaultOptions(new OptionsBuilder(o).build());
    }

    private static void _testDefaultOptions(Options o) {
        assertEquals(1, o.getServers().size(), "default one server");
        assertEquals(1, o.getUnprocessedServers().size(), "default one server");
        assertEquals(DEFAULT_URL, o.getServers().toArray()[0].toString(), "default url");

        assertEquals(Collections.emptyList(), o.getHttpRequestInterceptors(), "default http request interceptors");
        assertEquals(DEFAULT_DATA_PORT_TYPE, o.getDataPortType(), "default data port type");

        assertFalse(o.isVerbose(), "default verbose");
        assertFalse(o.isPedantic(), "default pedantic");
        assertFalse(o.isNoRandomize(), "default norandomize");
        assertFalse(o.isNoEcho(), "default noEcho");
        assertEquals(DEFAULT_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL, o.isDiscardMessagesWhenOutgoingQueueFull(),
            "default discard messages when outgoing queue full");

        assertNull(o.getUsername(), "default username");
        assertNull(o.getPassword(), "default password");
        assertNull(o.getToken(), "default token");
        assertNull(o.getConnectionName(), "default connection name");

        assertNull(o.getSslContext(), "default ssl context");

        assertEquals(DEFAULT_MAX_RECONNECT, o.getMaxReconnects(), "default max reconnect");
        assertEquals(DEFAULT_MAX_PINGS_OUT, o.getMaxPingsOut(), "default ping max");
        assertEquals(DEFAULT_RECONNECT_BUF_SIZE, o.getReconnectBufferSize(), "default reconnect buffer size");
        assertEquals(DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE, o.getMaxMessagesInOutgoingQueue(),
            "default max messages in outgoing queue");

        assertEquals(DEFAULT_RECONNECT_WAIT, o.getReconnectWait(), "default reconnect wait");
        assertEquals(DEFAULT_CONNECTION_TIMEOUT, o.getConnectionTimeout(), "default connection timeout");
        assertEquals(DEFAULT_PING_INTERVAL, o.getPingInterval(), "default ping interval");
        assertEquals(DEFAULT_REQUEST_CLEANUP_INTERVAL, o.getRequestCleanupInterval(),
            "default cleanup interval");

        assertNull(o.getConnectionListener(), "disconnect listener");
        assertNull(o.getStatisticsCollector(), "statistics collector");

        assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());
    }

    @Test
    public void testChainedBooleanOptions() {
        Options o = new OptionsBuilder().verbose().pedantic().noRandomize()
            .noEcho()
            .discardMessagesWhenOutgoingQueueFull()
            .build();
        _testChainedBooleanOptions(o);
        _testChainedBooleanOptions(new OptionsBuilder(o).build());
    }

    private static void _testChainedBooleanOptions(Options o) {
        assertNull(o.getUsername(), "default username");
        assertTrue(o.isVerbose(), "chained verbose");
        assertTrue(o.isPedantic(), "chained pedantic");
        assertTrue(o.isNoRandomize(), "chained norandomize");
        assertTrue(o.isNoEcho(), "chained noecho");
        assertTrue(o.isDiscardMessagesWhenOutgoingQueueFull(), "chained discard messages when outgoing queue full");
    }

    @Test
    public void testChainedStringOptions() {
        Options o = new OptionsBuilder().userInfo("hello".toCharArray(), "world".toCharArray()).connectionName("name").build();
        _testChainedStringOptions(o);
        _testChainedStringOptions(new OptionsBuilder(o).build());
    }

    private static void _testChainedStringOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertArrayEquals("hello".toCharArray(), o.getUsername(), "chained username");
        assertArrayEquals("world".toCharArray(), o.getPassword(), "chained password");
        assertEquals("name", o.getConnectionName(), "chained connection name");
    }

    @Test
    public void testChainedSecure() throws Exception {
        SSLContext ctx = SslTestingHelper.createTestSSLContext();
        SSLContext.setDefault(ctx);
        Options o = new OptionsBuilder().secure().build();
        _testChainedSecure(ctx, o);
        _testChainedSecure(ctx, new OptionsBuilder(o).build());
    }

    private static void _testChainedSecure(SSLContext ctx, Options o) {
        assertEquals(ctx, o.getSslContext(), "chained context");
    }

    @Test
    public void testChainedSSLOptions() throws Exception {
        SSLContext ctx = SslTestingHelper.createTestSSLContext();
        Options o = new OptionsBuilder().sslContext(ctx).build();
        _testChainedSSLOptions(ctx, o);
        _testChainedSSLOptions(ctx, new OptionsBuilder(o).build());
    }

    private static void _testChainedSSLOptions(SSLContext ctx, Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertEquals(ctx, o.getSslContext(), "chained context");
    }

    @Test
    public void testChainedIntOptions() {
        Options o = new OptionsBuilder().maxReconnects(100).maxPingsOut(200).reconnectBufferSize(300)
            .maxControlLine(400)
            .maxMessagesInOutgoingQueue(500)
            .build();
        _testChainedIntOptions(o);
        _testChainedIntOptions(new OptionsBuilder(o).build());
    }

    private static void _testChainedIntOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertEquals(100, o.getMaxReconnects(), "chained max reconnect");
        assertEquals(200, o.getMaxPingsOut(), "chained ping max");
        assertEquals(300, o.getReconnectBufferSize(), "chained reconnect buffer size");
        assertEquals(400, o.getMaxControlLine(), "chained max control line");
        assertEquals(500, o.getMaxMessagesInOutgoingQueue(), "chained max messages in outgoing queue");
    }

    @Test
    public void testChainedDurationOptions() {
        Options o = new OptionsBuilder().reconnectWait(101L)
            .connectionTimeout(202).pingInterval(303)
            .requestCleanupInterval(404)
            .reconnectJitter(505L)
            .reconnectJitterTls(606L)
            .build();
        _testChainedDurationOptions(o);
        _testChainedDurationOptions(new OptionsBuilder(o).build());
    }

    private static void _testChainedDurationOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertEquals(101L, o.getReconnectWait(), "chained reconnect wait");
        assertEquals(202, o.getConnectionTimeout(), "chained connection timeout");
        assertEquals(303, o.getPingInterval(), "chained ping interval");
        assertEquals(404, o.getRequestCleanupInterval(), "chained cleanup interval");
        assertEquals(505L, o.getReconnectJitter(), "chained reconnect jitter");
        assertEquals(606L, o.getReconnectJitterTls(), "chained cleanup jitter tls");
    }

    @Test
    public void testHttpRequestInterceptors() {
        Consumer<HttpRequest> interceptor1 = req -> req.getHeaders().add("Test1", "Header");
        Consumer<HttpRequest> interceptor2 = req -> req.getHeaders().add("Test2", "Header");
        Options o = new OptionsBuilder()
            .httpRequestInterceptor(interceptor1)
            .httpRequestInterceptor(interceptor2)
            .build();
        assertEquals(o.getHttpRequestInterceptors(), Arrays.asList(interceptor1, interceptor2));

        o = new OptionsBuilder()
            .httpRequestInterceptors(Arrays.asList(interceptor2, interceptor1))
            .build();
        assertEquals(o.getHttpRequestInterceptors(), Arrays.asList(interceptor2, interceptor1));
    }

    @Test
    public void testLongProperties() {
        // positive values are stored as-is
        Properties props = new Properties();
        props.setProperty(PROP_RECONNECT_WAIT, "" + (15 * MINUTE));
        props.setProperty(PROP_RECONNECT_JITTER, "" + (2 * DAY + 3 * HOUR + 4 * MINUTE));
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "" + DAY);
        props.setProperty(PROP_RECONNECT_BUFFER_SIZE, "1234567");
        _testLongProperties(new OptionsBuilder(props).build());

        // negative values are silently skipped — defaults survive
        props = new Properties();
        props.setProperty(PROP_RECONNECT_WAIT, "-1");
        props.setProperty(PROP_RECONNECT_JITTER, "-1");
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "-1");
        props.setProperty(PROP_RECONNECT_BUFFER_SIZE, "-1");
        Options o = new OptionsBuilder(props).build();
        assertEquals(DEFAULT_RECONNECT_WAIT, o.getReconnectWait());
        assertEquals(DEFAULT_RECONNECT_JITTER, o.getReconnectJitter());
        assertEquals(DEFAULT_RECONNECT_JITTER_TLS, o.getReconnectJitterTls());
        assertEquals(DEFAULT_RECONNECT_BUF_SIZE, o.getReconnectBufferSize());

        // non-numeric values throw at build time: IllegalArgumentException (millisProperty) or its
        // NumberFormatException subclass (longGtEqZeroProperty) — assert the common parent type.
        for (String key : new String[]{PROP_RECONNECT_WAIT, PROP_RECONNECT_JITTER, PROP_RECONNECT_JITTER_TLS, PROP_RECONNECT_BUFFER_SIZE}) {
            Properties bad = new Properties();
            bad.setProperty(key, "not-a-number");
            assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder(bad).build(),
                "expected IllegalArgumentException for non-numeric " + key);
        }
    }

    private static void _testLongProperties(Options o) {
        assertEquals(15 * MINUTE, o.getReconnectWait());
        assertEquals(2 * DAY + 3 * HOUR + 4 * MINUTE, o.getReconnectJitter());
        assertEquals(DAY, o.getReconnectJitterTls());
        assertEquals(1234567L, o.getReconnectBufferSize());
    }

    @Test
    public void testDurationProperties() {
        // test millis
        Properties props = new Properties();
        props.setProperty(PROP_CONNECTION_TIMEOUT, "42000");
        props.setProperty(PROP_SOCKET_WRITE_TIMEOUT, "42123");
        props.setProperty(PROP_PING_INTERVAL, "20345");
        props.setProperty(PROP_REQUEST_CLEANUP_INTERVAL, "" + (10 * HOUR));
        _testDurationProperties(new OptionsBuilder(props).build());

        // test ISO-8601 duration strings (accepted, converted to millis)
        props = new Properties();
        props.setProperty(PROP_CONNECTION_TIMEOUT, "PT42S");
        props.setProperty(PROP_SOCKET_WRITE_TIMEOUT, "PT42.123S"); // now millis like the others — ISO-8601 accepted, = 42123 ms
        props.setProperty(PROP_PING_INTERVAL, "PT20.345S");
        props.setProperty(PROP_REQUEST_CLEANUP_INTERVAL, "PT10H");
        _testDurationProperties(new OptionsBuilder(props).build());

        // test negative value gives default
        props = new Properties();
        props.setProperty(PROP_CONNECTION_TIMEOUT, "-1");
        Options o = new OptionsBuilder(props).build();
        assertEquals(DEFAULT_CONNECTION_TIMEOUT, o.getConnectionTimeout());

        // test parse error (neither a millis number nor an ISO-8601 duration)
        Properties px1 = new Properties();
        px1.setProperty(PROP_CONNECTION_TIMEOUT, "A");
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder(px1).build());
    }

    private static final long MINUTE = 1000 * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;

    private static void _testDurationProperties(Options o) {
        assertEquals(42000, o.getConnectionTimeout());
        assertEquals(42123, o.getSocketWriteTimeout());
        assertEquals(20345, o.getPingInterval());
        assertEquals(10 * HOUR, o.getRequestCleanupInterval());
    }

    @Test
    public void testPropertiesDoNotOverrideWithDefaultIfNotSupplied() {
        Options o = new OptionsBuilder().build();
        _testDefaultNotOverridden(o);

        Properties props = new Properties();
        o = new OptionsBuilder(props).build();
        _testDefaultNotOverridden(o);

        o = new OptionsBuilder()
            .properties(setIgnoredValues(props))
            .build();
        _testDefaultNotOverridden(o);

        props = new Properties();
        o = new OptionsBuilder()
            .maxReconnects(42)
            .reconnectBufferSize(43)
            .socketReadTimeout(44)
            .socketSoLinger(45)
            .socketReceiveBufferSize(46)
            .socketSendBufferSize(47)
            .maxControlLine(48)
            .maxPingsOut(49)
            .maxMessagesInOutgoingQueue(50)
            .reconnectWait(73L)
            .reconnectJitter(74L)
            .reconnectJitterTls(75L)
            .connectionTimeout(76)
            .socketWriteTimeout(7700) // millis
            .pingInterval(78)
            .requestCleanupInterval(79)
            .properties(props)
            .build();
        _testNonDefaultNotOverridden(o);

        o = new OptionsBuilder(o)
            .properties(setIgnoredValues(props))
            .build();
        _testNonDefaultNotOverridden(o);
    }

    private static Properties setIgnoredValues(Properties props) {
        props.setProperty(PROP_MAX_CONTROL_LINE, "-1");
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "-1");
        props.setProperty(PROP_RECONNECT_WAIT, "-1");
        props.setProperty(PROP_RECONNECT_JITTER, "-1");
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "-1");
        props.setProperty(PROP_CONNECTION_TIMEOUT, "-1");
        props.setProperty(PROP_SOCKET_WRITE_TIMEOUT, "-1");
        props.setProperty(PROP_PING_INTERVAL, "-1");
        props.setProperty(PROP_REQUEST_CLEANUP_INTERVAL, "-1");
        return props;
    }

    private static void _testNonDefaultNotOverridden(Options o) {
        assertEquals(42, o.getMaxReconnects());
        assertEquals(43, o.getReconnectBufferSize());
        assertEquals(44, o.getSocketReadTimeout());
        assertEquals(45, o.getSocketSoLinger());
        assertEquals(46, o.getSocketReceiveBufferSize());
        assertEquals(47, o.getSocketSendBufferSize());
        assertEquals(48, o.getMaxControlLine());
        assertEquals(49, o.getMaxPingsOut());
        assertEquals(50, o.getMaxMessagesInOutgoingQueue());
        assertEquals(73L, o.getReconnectWait());
        assertEquals(74L, o.getReconnectJitter());
        assertEquals(75L, o.getReconnectJitterTls());
        assertEquals(76, o.getConnectionTimeout());
        assertEquals(7700, o.getSocketWriteTimeout());
        assertEquals(78, o.getPingInterval());
        assertEquals(79, o.getRequestCleanupInterval());
    }

    private static void _testDefaultNotOverridden(Options o) {
        assertEquals(DEFAULT_MAX_RECONNECT, o.getMaxReconnects());
        assertEquals(DEFAULT_RECONNECT_BUF_SIZE, o.getReconnectBufferSize());
        assertEquals(0, o.getSocketReadTimeout());
        assertEquals(-1, o.getSocketSoLinger());
        assertEquals(-1, o.getSocketReceiveBufferSize());
        assertEquals(-1, o.getSocketSendBufferSize());
        assertEquals(DEFAULT_MAX_RECONNECT, o.getMaxReconnects());
        assertEquals(DEFAULT_MAX_CONTROL_LINE, o.getMaxControlLine());
        assertEquals(DEFAULT_MAX_PINGS_OUT, o.getMaxPingsOut());
        assertEquals(DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE, o.getMaxMessagesInOutgoingQueue());
        assertEquals(DEFAULT_RECONNECT_WAIT, o.getReconnectWait());
        assertEquals(DEFAULT_RECONNECT_JITTER, o.getReconnectJitter());
        assertEquals(DEFAULT_RECONNECT_JITTER_TLS, o.getReconnectJitterTls());
        assertEquals(DEFAULT_CONNECTION_TIMEOUT, o.getConnectionTimeout());
        assertEquals(DEFAULT_SOCKET_WRITE_TIMEOUT, o.getSocketWriteTimeout());
        assertEquals(DEFAULT_PING_INTERVAL, o.getPingInterval());
        assertEquals(DEFAULT_REQUEST_CLEANUP_INTERVAL, o.getRequestCleanupInterval());
    }

    @Test
    public void testPropertiesBooleanBuilder() {
        Properties props = new Properties();
        props.setProperty(PROP_VERBOSE, "true");
        props.setProperty(PROP_PEDANTIC, "true");
        props.setProperty(PROP_NO_RANDOMIZE, "true");
        props.setProperty(PROP_OPEN_TLS, "true");
        props.setProperty(PROP_NO_ECHO, "true");
        props.setProperty(PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL, "true");

        Options o = new OptionsBuilder(props).build();
        _testPropertiesBooleanBuilder(o);
        _testPropertiesBooleanBuilder(new OptionsBuilder(o).build());
    }

    private static void _testPropertiesBooleanBuilder(Options o) {
        assertNull(o.getUsername(), "default username chars");
        assertTrue(o.isVerbose(), "property verbose");
        assertTrue(o.isPedantic(), "property pedantic");
        assertTrue(o.isNoRandomize(), "property norandomize");
        assertTrue(o.isNoEcho(), "property noecho");
        assertTrue(o.isDiscardMessagesWhenOutgoingQueueFull(), "property discard messages when outgoing queue full");
        assertNotNull(o.getSslContext(), "property opentls");
    }

    @Test
    public void testPropertiesStringOptions() {
        Properties props = new Properties();
        props.setProperty(PROP_USERNAME, "hello");
        props.setProperty(PROP_PASSWORD, "world");
        props.setProperty(PROP_CONNECTION_NAME, "name");

        Options o = new OptionsBuilder(props).build();
        _testPropertiesStringOptions(o);
        _testPropertiesStringOptions(new OptionsBuilder(o).build());

        // COVERAGE
        props.setProperty(PROP_CONNECTION_NAME, "");
        new OptionsBuilder(props).build();

        props.remove(PROP_CONNECTION_NAME);
        new OptionsBuilder(props).build();
    }

    private static void _testPropertiesStringOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertArrayEquals("hello".toCharArray(), o.getUsername(), "property username");
        assertArrayEquals("world".toCharArray(), o.getPassword(), "property password");
        assertEquals("name", o.getConnectionName(), "property connection name");
    }

    @Test
    public void testPropertiesSSLOptions() throws Exception {
        // don't use default for tests, issues with forcing algorithm exception in other tests break it
        SSLContext.setDefault(SslTestingHelper.createTestSSLContext());
        Properties props = new Properties();
        props.setProperty(PROP_SECURE, "true");

        Options o = new OptionsBuilder(props).build();
        _testPropertiesSSLOptions(o);
        _testPropertiesSSLOptions(new OptionsBuilder(o).build());
    }

    private static void _testPropertiesSSLOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertNotNull(o.getSslContext(), "property context");
    }

    @Test
    public void testSupportUTF8Subjects() {
        Options o = new OptionsBuilder().build();
        assertFalse(o.supportUTF8Subjects());

        o = new OptionsBuilder().supportUTF8Subjects().build();
        assertTrue(o.supportUTF8Subjects());

        Properties props = new Properties();
        props.setProperty(PROP_SUPPORT_UTF8_SUBJECTS, "true");
        o = new OptionsBuilder(props).build();
        assertTrue(o.supportUTF8Subjects());
    }

    @Test
    public void testBuilderCoverageOptions() {
        Options o = new OptionsBuilder().build();
        assertTrue(o.clientSideLimitChecks());
        assertNull(o.getServerPool()); // there is a default provider

        o = new OptionsBuilder().clientSideLimitChecks(true).build();
        assertTrue(o.clientSideLimitChecks());
        o = new OptionsBuilder()
            .clientSideLimitChecks(false)
            .serverPool(new NatsServerPool())
            .build();
        assertFalse(o.clientSideLimitChecks());
        assertNotNull(o.getServerPool());
    }

    @Test
    public void testProperties() throws Exception {
        Properties props = new Properties();

        // stringProperty
        props.setProperty(PROP_CONNECTION_NAME, "name");

        // stringProperty builds an auth handler
        props.setProperty(PROP_CREDENTIAL_PATH, jwtResource("test.creds"));

        // charArrayProperty
        props.setProperty(PROP_USERNAME, "user");

        // intProperty
        props.setProperty(PROP_MAX_RECONNECTS, "10");

        // intGtEqZeroProperty
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "11");

        // longProperty
        props.setProperty(PROP_RECONNECT_BUFFER_SIZE, "2999999999");

        // millisProperty
        props.setProperty(PROP_PING_INTERVAL, "1000");

        // classnameProperty
        props.setProperty(PROP_SERVERS_POOL_IMPLEMENTATION_CLASS, "io.synadia.client.utils.CoverageServerPool");

        Options o = new OptionsBuilder(props).build();
        _testProperties(o);

        props = new Properties();
        props.load(ResourceUtils.resourceAsInputStream("options_coverage_with_prefix.properties"));
        o = new OptionsBuilder(props).build();
        _testProperties(o);

        props = new Properties();
        props.load(ResourceUtils.resourceAsInputStream("options_coverage_without_prefix.properties"));
        o = new OptionsBuilder(props).build();
        _testProperties(o);

        String propertiesFilePath = null;
        try {
            propertiesFilePath = createTempPropertiesFile(props);
            o = new OptionsBuilder(propertiesFilePath).build();
            _testProperties(o);
        }
        finally {
            ResourceUtils.deleteFileOrFolder(propertiesFilePath);
        }

        // intGtEqZeroProperty not gt zero gives default
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "-1");
        o = new OptionsBuilder(props).build();
        assertEquals(DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE, o.getMaxMessagesInOutgoingQueue());

        // last one wins
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "500");
        o = new OptionsBuilder(props)
            .maxMessagesInOutgoingQueue(1000)
            .build();
        assertEquals(1000, o.getMaxMessagesInOutgoingQueue());

        o = new OptionsBuilder()
            .maxMessagesInOutgoingQueue(1000)
            .properties(props)
            .build();
        assertEquals(500, o.getMaxMessagesInOutgoingQueue());
    }

    public static String createTempPropertiesFile(Properties props) throws IOException {
        File f = File.createTempFile("jnats", ".properties");
        BufferedWriter writer = new BufferedWriter(new FileWriter(f));
        for (String key : props.stringPropertyNames()) {
            writer.write(key + "=" + props.getProperty(key) + System.lineSeparator());
        }
        writer.flush();
        writer.close();
        return f.getAbsolutePath();
    }

    private static void _testProperties(Options o) {
        assertEquals("name", o.getConnectionName());
        assertNotNull(o.getUsername());
        assertEquals("user", new String(o.getUsername()));
        assertEquals(10, o.getMaxReconnects());
        assertEquals(11, o.getMaxMessagesInOutgoingQueue());
        assertEquals(2999999999L, o.getReconnectBufferSize());
        assertEquals(1000, o.getPingInterval());
        assertNotNull(o.getAuthHandler());
        assertNotNull(o.getServerPool());
        assertInstanceOf(CoverageServerPool.class, o.getServerPool());
    }

    @Test
    public void testPropertiesCoverageOptions() {
        Properties props = new Properties();
        props.setProperty(PROP_SECURE, "false");
        props.setProperty(PROP_OPEN_TLS, "false");
        props.setProperty(PROP_RECONNECT_JITTER, "1000");
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "2000");
        props.setProperty(PROP_CLIENT_SIDE_LIMIT_CHECKS, "true"); // deprecated
        props.setProperty(PROP_IGNORE_DISCOVERED_SERVERS, "true");
        props.setProperty(PROP_FORCE_FLUSH_ON_REQUEST, "false");

        Options o = new OptionsBuilder(props).build();
        _testPropertiesCoverageOptions(o);
        _testPropertiesCoverageOptions(new OptionsBuilder(o).build());
    }

    private static void _testPropertiesCoverageOptions(Options o) {
        assertNull(o.getSslContext());
        assertTrue(o.clientSideLimitChecks());
        assertTrue(o.isIgnoreDiscoveredServers());
        assertFalse(o.forceFlushOnRequest());
    }

    @Test
    public void testPropertyIntOptions() {
        Properties props = new Properties();
        props.setProperty(PROP_MAX_RECONNECTS, "100");
        props.setProperty(PROP_MAX_PINGS_OUT, "200");
        props.setProperty(PROP_RECONNECT_BUFFER_SIZE, "300");
        props.setProperty(PROP_MAX_CONTROL_LINE, "400");
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "500");

        Options o = new OptionsBuilder(props).build();
        _testPropertyIntOptions(o);
        _testPropertyIntOptions(new OptionsBuilder(o).build());
    }

    private static void _testPropertyIntOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertEquals(100, o.getMaxReconnects(), "property max reconnect");
        assertEquals(200, o.getMaxPingsOut(), "property ping max");
        assertEquals(300, o.getReconnectBufferSize(), "property reconnect buffer size");
        assertEquals(400, o.getMaxControlLine(), "property max control line");
        assertEquals(500, o.getMaxMessagesInOutgoingQueue(), "property max messages in outgoing queue");
    }

    @Test
    public void testDefaultPropertyIntOptions() {
        Properties props = new Properties();
        props.setProperty(PROP_RECONNECT_WAIT, "-1");
        props.setProperty(PROP_RECONNECT_JITTER, "-1");
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "-1");
        props.setProperty(PROP_CONNECTION_TIMEOUT, "-1");
        props.setProperty(PROP_PING_INTERVAL, "-1");
        props.setProperty(PROP_REQUEST_CLEANUP_INTERVAL, "-1");
        props.setProperty(PROP_MAX_CONTROL_LINE, "-1");
        props.setProperty(PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE, "-1");

        Options o = new OptionsBuilder(props).build();
        _testDefaultPropertyIntOptions(o);
        _testDefaultPropertyIntOptions(new OptionsBuilder(o).build());
    }

    private static void _testDefaultPropertyIntOptions(Options o) {
        assertEquals(DEFAULT_MAX_CONTROL_LINE, o.getMaxControlLine(), "default max control line");
        assertEquals(DEFAULT_RECONNECT_WAIT, o.getReconnectWait(), "default reconnect wait");
        assertEquals(DEFAULT_CONNECTION_TIMEOUT, o.getConnectionTimeout(), "default connection timeout");
        assertEquals(DEFAULT_PING_INTERVAL, o.getPingInterval(), "default ping interval");
        assertEquals(DEFAULT_REQUEST_CLEANUP_INTERVAL, o.getRequestCleanupInterval(),
            "default cleanup interval");
        assertEquals(DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE, o.getMaxMessagesInOutgoingQueue(),
            "default max messages in outgoing queue");
    }

    @Test
    public void testPropertyDurationOptions() {
        Properties props = new Properties();
        props.setProperty(PROP_RECONNECT_WAIT, "101");
        props.setProperty(PROP_CONNECTION_TIMEOUT, "202");
        props.setProperty(PROP_PING_INTERVAL, "303");
        props.setProperty(PROP_REQUEST_CLEANUP_INTERVAL, "404");
        props.setProperty(PROP_RECONNECT_JITTER, "505");
        props.setProperty(PROP_RECONNECT_JITTER_TLS, "606");

        Options o = new OptionsBuilder(props).build();
        _testPropertyDurationOptions(o);
        _testPropertyDurationOptions(new OptionsBuilder(o).build());
    }

    private static void _testPropertyDurationOptions(Options o) {
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertEquals(101L, o.getReconnectWait(), "property reconnect wait");
        assertEquals(202, o.getConnectionTimeout(), "property connection timeout");
        assertEquals(303, o.getPingInterval(), "property ping interval");
        assertEquals(404, o.getRequestCleanupInterval(), "property cleanup interval");
        assertEquals(505L, o.getReconnectJitter(), "property reconnect jitter");
        assertEquals(606L, o.getReconnectJitterTls(), "property reconnect jitter tls");
    }

    @Test
    public void testPropertiesSubjectValidationType() {
        // No property set → default Lenient
        Properties props = new Properties();
        Options o = new OptionsBuilder(props).build();
        assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());

        // PROP_SUBJECT_VALIDATION_TYPE — case-insensitive enum name match
        props.clear();
        props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "None");
        o = new OptionsBuilder(props).build();
        assertEquals(SubjectValidationType.None, o.subjectValidationType());

        props.clear();
        props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "lenient");
        o = new OptionsBuilder(props).build();
        assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());

        props.clear();
        props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "STRICT");
        o = new OptionsBuilder(props).build();
        assertEquals(SubjectValidationType.Strict, o.subjectValidationType());

        // Unknown value → default Lenient
        props.clear();
        props.setProperty(PROP_SUBJECT_VALIDATION_TYPE, "bogus");
        o = new OptionsBuilder(props).build();
        assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());

        o = new OptionsBuilder().build();
        assertEquals(SubjectValidationType.Lenient, o.subjectValidationType());
    }

    @Test
    public void testSubjectValidationTypeGet() {
        assertEquals(SubjectValidationType.Lenient, SubjectValidationType.get(null));
        assertEquals(SubjectValidationType.Lenient, SubjectValidationType.get(""));
        assertEquals(SubjectValidationType.Lenient, SubjectValidationType.get("bogus"));
        assertEquals(SubjectValidationType.None, SubjectValidationType.get("none"));
        assertEquals(SubjectValidationType.None, SubjectValidationType.get("NONE"));
        assertEquals(SubjectValidationType.Lenient, SubjectValidationType.get("Lenient"));
        assertEquals(SubjectValidationType.Strict, SubjectValidationType.get("strict"));
        assertEquals(SubjectValidationType.Strict, SubjectValidationType.get("STRICT"));
    }

    @Test
    public void testPropertyErrorListener() {
        Properties props = new Properties();
        props.setProperty(PROP_ERROR_LISTENER_CLASS, Listener.class.getCanonicalName());

        Options o = new OptionsBuilder(props).build();
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertNotNull(o.getErrorListener(), "property error listener");

        o.getErrorListener().errorOccurred(null, "bad subject");
        assertEquals(0, ((Listener) o.getErrorListener()).getExceptionCount(), "property error listener class");
    }

    @Test
    public void testPropertyConnectionListeners() {
        Properties props = new Properties();
        props.setProperty(PROP_CONNECTION_LISTENER_CLASS, Listener.class.getCanonicalName());

        Options o = new OptionsBuilder(props).build();
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertNotNull(o.getConnectionListener(), "property connection listener");

        Listener listener = ((Listener) o.getConnectionListener());
        listener.queueConnectionEvent(ConnectionEvents.DISCONNECTED);
        o.getConnectionListener().connectionEvent(null, ConnectionEvents.DISCONNECTED, null, null);
        listener.validate();

        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        o.getConnectionListener().connectionEvent(null, ConnectionEvents.RECONNECTED, null, null);
        listener.validate();

        listener.queueConnectionEvent(ConnectionEvents.CLOSED);
        o.getConnectionListener().connectionEvent(null, ConnectionEvents.CLOSED, null, null);
        listener.validate();
    }

    @Test
    public void testPropertyStatisticsCollector() {
        Properties props = new Properties();
        props.setProperty(PROP_STATISTICS_COLLECTOR_CLASS, CoverageStatisticsCollector.class.getCanonicalName());

        Options o = new OptionsBuilder(props).build();
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type

        StatisticsCollector stats = o.getStatisticsCollector();
        assertNotNull(stats);

        stats.incrementOut(42);
        assertEquals(1, stats.getStatistics().getOutMsgs());
        assertEquals(42, stats.getStatistics().getOutBytes());
    }

    @Test
    public void testStatisticsCoverage() {
        validateStatistics(new NatsStatistics(), true);

        // exercise the write methods of a concrete collector; only incrementOut is tracked by this one
        StatisticsCollector stats = new CoverageStatisticsCollector();

        validateStatistics(stats.getStatistics(), true);

        // none of the these are tracked, so the read view is still all zeros
        stats.setAdvancedTracking(true);
        stats.incrementPingCount();
        stats.incrementReconnects();
        stats.incrementDroppedCount();
        stats.incrementOkCount();
        stats.incrementErrCount();
        stats.incrementExceptionCount();
        stats.incrementRequestsSent();
        stats.incrementRepliesReceived();
        stats.incrementDuplicateRepliesReceived();
        stats.incrementOrphanRepliesReceived();
        stats.incrementIn(42);
        stats.incrementFlushCounter();
        stats.incrementOutstandingRequests();
        stats.decrementOutstandingRequests();
        stats.registerRead(142);
        stats.registerWrite(173);

        // incrementOut is the one metric this collector tracks
        stats.incrementOut(73);

        validateStatistics(stats.getStatistics(), false);
    }

    private static void validateStatistics(Statistics stats, boolean empty) {
        assertEquals(0, stats.getPings());
        assertEquals(0, stats.getReconnects());
        assertEquals(0, stats.getDroppedCount());
        assertEquals(0, stats.getOKs());
        assertEquals(0, stats.getErrs());
        assertEquals(0, stats.getExceptions());
        assertEquals(0, stats.getRequestsSent());
        assertEquals(0, stats.getRepliesReceived());
        assertEquals(0, stats.getDuplicateRepliesReceived());
        assertEquals(0, stats.getOrphanRepliesReceived());
        assertEquals(0, stats.getInMsgs());
        assertEquals(0, stats.getInBytes());
        assertEquals(0, stats.getFlushCounter());
        assertEquals(0, stats.getOutstandingRequests());

        if (empty) {
            assertEquals(0, stats.getOutMsgs());
            assertEquals(0, stats.getOutBytes());
        }
        else {
            assertEquals(1, stats.getOutMsgs());
            assertEquals(73, stats.getOutBytes());
        }
    }

    @Test
    public void testChainOverridesProperties() {
        Properties props = new Properties();
        props.setProperty(PROP_TOKEN, "token");
        props.setProperty(PROP_CONNECTION_NAME, "name");

        Options o = new OptionsBuilder(props).connectionName("newname").build();
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type
        assertArrayEquals("token".toCharArray(), o.getToken(), "property token");
        assertEquals("newname", o.getConnectionName(), "property connection name");
    }

    @Test
    public void testDefaultConnectOptions() {
        Options o = new OptionsBuilder().build();
        String expected = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,\"headers\":true,\"no_responders\":true}";
        assertEquals(expected, o.buildProtocolConnectOptionsString("nats://localhost:4222", false, null).toString(), "default connect options");
    }

    @Test
    public void testNonDefaultConnectOptions() {
        Options o = new OptionsBuilder().noEcho().pedantic().verbose().build();
        String expected = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":true,\"pedantic\":true,\"tls_required\":false,\"echo\":false,\"headers\":true,\"no_responders\":true}";
        assertEquals(expected, o.buildProtocolConnectOptionsString("nats://localhost:4222", false, null).toString(), "non default connect options");
    }

    @Test
    public void testConnectOptionsWithNameAndContext() throws Exception {
        SSLContext ctx = SslTestingHelper.createTestSSLContext();
        Options o = new OptionsBuilder().sslContext(ctx).connectionName("c1").build();
        String expected = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\",\"name\":\"c1\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":true,\"echo\":true,\"headers\":true,\"no_responders\":true}";
        assertEquals(expected, o.buildProtocolConnectOptionsString("nats://localhost:4222", false, null).toString(), "default connect options");
    }

    @Test
    public void testAuthConnectOptions() {
        Options o = new OptionsBuilder().userInfo("hello".toCharArray(), "world".toCharArray()).build();
        String expectedNoAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,\"headers\":true,\"no_responders\":true}";
        String expectedWithAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,\"headers\":true,\"no_responders\":true"
            + ",\"user\":\"hello\",\"pass\":\"world\"}";
        assertEquals(expectedNoAuth, o.buildProtocolConnectOptionsString("nats://localhost:4222", false, null).toString(), "no auth connect options");
        assertEquals(expectedWithAuth, o.buildProtocolConnectOptionsString("nats://localhost:4222", true, null).toString(), "auth connect options");
    }
    /*
    expected: <{"lang":"java","version":"2.8.0","protocol":1,"verbose":false,"pedantic":false,"tls_required":false,"echo":true}>
    but was: <{"lang":"java","version":"2.8.0","protocol":1,"verbose":false,"pedantic":false,"tls_required":false,"echo":true,"headers":true}>
     */

    @Test
    public void testNKeyConnectOptions() throws Exception {
        AuthHandlerForTesting th = new AuthHandlerForTesting();
        byte[] nonce = "abcdefg".getBytes(StandardCharsets.UTF_8);
        String sig = base64UrlEncodeToString(th.sign(nonce));

        Options o = new OptionsBuilder().authHandler(th).build();
        String expectedNoAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,\"headers\":true,\"no_responders\":true}";
        String expectedWithAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
            + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,\"headers\":true"
            + ",\"no_responders\":true,\"nkey\":\""+new String(th.getID())+"\",\"sig\":\""+sig+"\",\"jwt\":\"\"}";
        assertEquals(expectedNoAuth, o.buildProtocolConnectOptionsString("nats://localhost:4222", false, nonce).toString(), "no auth connect options");
        assertEquals(expectedWithAuth, o.buildProtocolConnectOptionsString("nats://localhost:4222", true, nonce).toString(), "auth connect options");
    }

    // Test for auth handler from nkey, option JWT and user info
    @Test
    public void testNKeyJWTAndUserInfoOptions() {
        // "jwt" is encoded from:
        // Header:    {"alg":"HS256"}
        // Payload:   {"jti":"","iat":2000000000,"iss":"","name":"user_jwt","sub":"","nats":{"pub":{"deny":[">"]},
        //            "sub":{"deny":[">"]},"subs":-1,"data":-1,"payload":-1,"type":"user","version":2}}
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIiLCJpYXQiOjIwMDAwMDAwMDAsImlzcyI6IiIsIm5hbWUiOiJ1c2VyX2p3"
                + "dCIsInN1YiI6IiIsIm5hdHMiOnsicHViIjp7ImRlbnkiOlsiPiJdfSwic3ViIjp7ImRlbnkiOlsiPiJdfSwic3VicyI6LTEsImRh"
                + "dGEiOi0xLCJwYXlsb2FkIjotMSwidHlwZSI6InVzZXIiLCJ2ZXJzaW9uIjoyfX0";
        NKey nkey = NKeyProvider.getProvider().createUser();
        String username = "username";
        String password = "password";
        AuthHandlerForTesting th = new AuthHandlerForTesting(nkey, jwt.toCharArray());
        byte[] nonce = "abcdefg".getBytes(StandardCharsets.UTF_8);
        String sig = Base64.getUrlEncoder().withoutPadding().encodeToString(th.sign(nonce));

        // Assert that no auth and user info is given
        Options options = new OptionsBuilder().authHandler(th)
                .userInfo(username.toCharArray(), password.toCharArray()).build();
        String expectedWithoutAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
                + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,"
                + "\"headers\":true,\"no_responders\":true}";
        String actualWithoutAuth = options
                .buildProtocolConnectOptionsString("nats://localhost:4222", false, nonce).toString();
        assertEquals(expectedWithoutAuth, actualWithoutAuth);

        // Assert that auth and user info is given via options
        String expectedWithAuth = "{\"lang\":\"java\",\"version\":\"" + Nats.CLIENT_VERSION + "\""
                + ",\"protocol\":1,\"verbose\":false,\"pedantic\":false,\"tls_required\":false,\"echo\":true,"
                + "\"headers\":true,\"no_responders\":true,\"nkey\":\"" + new String(th.getID()) + "\",\"sig\":\""
                + sig + "\",\"jwt\":\"" + jwt + "\",\"user\":\"" + username + "\",\"pass\":\"" + password + "\"}";
        String actualWithAuthInOptions = options
                .buildProtocolConnectOptionsString("nats://localhost:4222", true, nonce).toString();
        assertEquals(expectedWithAuth, actualWithAuthInOptions);

        // Assert that auth is given via options and user info is given via server URI
        Options optionsWithoutUserInfo = new OptionsBuilder().authHandler(th).build();
        String serverUriWithAuth = "nats://" + username + ":" + password + "@localhost:4222";
        String actualWithAuthInServerUri = optionsWithoutUserInfo
                .buildProtocolConnectOptionsString(serverUriWithAuth, true, nonce).toString();
        assertEquals(expectedWithAuth, actualWithAuthInServerUri);
    }


    @Test
    public void testDataPort() {
        Options o = new OptionsBuilder().socketWriteTimeout(0).build();
        DataPort dataPort = o.createDataPort();
        assertNotNull(dataPort);
        assertEquals(DEFAULT_DATA_PORT_TYPE, dataPort.getClass().getCanonicalName());

        Properties props = new Properties();
        props.setProperty(PROP_DATA_PORT_TYPE, CloseOnUpgradeAttempt.class.getCanonicalName());

        o = new OptionsBuilder(props).build();
        assertFalse(o.isVerbose(), "default verbose"); // One from a different type

        assertEquals(CloseOnUpgradeAttempt.class.getCanonicalName(), o.createDataPort().getClass().getCanonicalName());
    }

    @Test
    public void testJetStreamProperties() {
        Properties props = new Properties();
        props.setProperty(PROP_INBOX_PREFIX, "custom-inbox-no-dot");
        Options o = new OptionsBuilder(props).build();
        assertEquals("custom-inbox-no-dot.", o.getInboxPrefix());

        props.setProperty(PROP_INBOX_PREFIX, "custom-inbox-ends-dot.");
        o = new OptionsBuilder(props).build();
        assertEquals("custom-inbox-ends-dot.", o.getInboxPrefix());
    }

    @Test
    public void testUserPassInURL() {
        String serverURI = "nats://derek:password@localhost:2222";
        Options o = new OptionsBuilder().server(serverURI).build();

        String connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"user\":\"derek\""));
        assertTrue(connectString.contains("\"pass\":\"password\""));
        assertFalse(connectString.contains("\"token\":"));
    }

    @Test
    public void testTokenInURL() {
        String serverURI = "nats://alberto@localhost:2222";
        Options o = new OptionsBuilder().server(serverURI).build();

        String connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"auth_token\":\"alberto\""));
        assertFalse(connectString.contains("\"user\":"));
        assertFalse(connectString.contains("\"pass\":"));
    }

    @Test
    public void testTokenSupplier() {
        String serverURI = "nats://localhost:2222";
        Options o = new OptionsBuilder().build();
        String connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertFalse(connectString.contains("\"auth_token\""));

        o = new OptionsBuilder().token(null).build();
        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertFalse(connectString.contains("\"auth_token\""));

        o = new OptionsBuilder().token(new char[0]).build();
        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertFalse(connectString.contains("\"auth_token\""));

        AtomicInteger counter = new AtomicInteger(0);
        Supplier<char[]> tokenSupplier = () -> ("short-lived-token-" + counter.incrementAndGet()).toCharArray();
        o = new OptionsBuilder().tokenSupplier(tokenSupplier).build();

        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"auth_token\":\"short-lived-token-1\""));

        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"auth_token\":\"short-lived-token-2\""));

        Properties properties = new Properties();
        properties.setProperty(PROP_TOKEN_SUPPLIER_CLASS, TestingDynamicTokenSupplier.class.getCanonicalName());
        o = new OptionsBuilder().properties(properties).build();

        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"auth_token\":\"dynamic-token-1\""));

        connectString = o.buildProtocolConnectOptionsString(serverURI, true, null).toString();
        assertTrue(connectString.contains("\"auth_token\":\"dynamic-token-2\""));
    }

    @Test
    public void testThrowOnNoOpts() {
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder((Options) null));
    }

    @Test
    public void testThrowOnNoProps() {
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder((Properties) null));
    }

    @Test
    public void testServerInProperties() {
        Properties props = new Properties();
        props.setProperty(PROP_URL, URL_PROTO_HOST_PORT_8080);
        assertServersAndUnprocessed(false, new OptionsBuilder(props).build());
    }

    @Test
    public void testServersInProperties() {
        Properties props = new Properties();
        String urls = URL_PROTO_HOST_PORT_8080 + ", " + URL_HOST_PORT_8081;
        props.setProperty(PROP_SERVERS, urls);
        assertServersAndUnprocessed(true, new OptionsBuilder(props).build());
    }

    @Test
    public void testServers() {
        String[] serverUrls = {URL_PROTO_HOST_PORT_8080, URL_HOST_PORT_8081};
        Options options = new OptionsBuilder().servers(serverUrls).build();
        assertServersAndUnprocessed(true, options);
    }

    @Test
    public void testServersWithCommas() {
        String serverURLs = URL_PROTO_HOST_PORT_8080 + "," + URL_HOST_PORT_8081;
        assertServersAndUnprocessed(true, new OptionsBuilder().server(serverURLs).build());
    }

    @Test
    public void testEmptyAndNullStringsInServers() {
        String[] serverUrls = {"", null, URL_PROTO_HOST_PORT_8080, URL_HOST_PORT_8081};
        assertServersAndUnprocessed(true, new OptionsBuilder().servers(serverUrls).build());
    }

    private void assertServersAndUnprocessed(boolean two, Options o) {
        Collection<URI> servers = o.getServers();
        URI[] serverArray = servers.toArray(new URI[0]);
        List<String> un = o.getUnprocessedServers();

        int size = two ? 2 : 1;
        assertEquals(size, serverArray.length);
        assertEquals(size, un.size());

        assertEquals(URL_PROTO_HOST_PORT_8080, serverArray[0].toString(), "property server");
        assertEquals(URL_PROTO_HOST_PORT_8080, un.get(0), "unprocessed server");

        if (two) {
            assertEquals(URL_PROTO_HOST_PORT_8081, serverArray[1].toString(), "property server");
            assertEquals(URL_HOST_PORT_8081, un.get(1), "unprocessed server");
            assertEquals(URL_PROTO_HOST_PORT_8080 + "," + URL_PROTO_HOST_PORT_8081, NatsUri.join(",", o.getNatsServerUris())); // coverage
        }
    }

    @Test
    public void testBadClassInPropertyConnectionListeners() {
        Properties props = new Properties();
        props.setProperty(PROP_CONNECTION_LISTENER_CLASS, "foo");
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder(props));
    }

    @Test
    public void testBadClassInPropertyStatisticsCollector() {
        Properties props = new Properties();
        props.setProperty(PROP_STATISTICS_COLLECTOR_CLASS, "foo");
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder(props));
    }

    @Test
    public void testTokenAndUserThrows() {
        assertThrows(IllegalStateException.class,
            () -> new OptionsBuilder().token("foo".toCharArray()).userInfo("foo".toCharArray(), "bar".toCharArray()).build());
    }

    @Test
    public void testThrowOnBadServerURI() {
        assertThrows(IllegalArgumentException.class,
            () -> new OptionsBuilder().server("foo:/bar\\:blammer").build());
    }

    @Test
    public void testThrowOnBadServersURI() {
        String[] serverUrls = {URL_PROTO_HOST_PORT_8080, "foo:/bar\\:blammer"};
        assertThrows(IllegalArgumentException.class, () -> new OptionsBuilder().servers(serverUrls).build());
    }

    @Test
    public void testSetExecutor() {
        ExecutorService exec = Executors.newCachedThreadPool();
        Options options = new OptionsBuilder().executor(exec).build();
        assertEquals(exec, options.getExecutor());
    }

    @Test
    public void testDefaultExecutor() throws Exception {
        Options options = new OptionsBuilder().connectionName("test").build();
        Future<String> future = options.getExecutor().submit(() -> Thread.currentThread().getName());
        String name = future.get(5, TimeUnit.SECONDS);
        assertTrue(name.startsWith("test"));

        options = new OptionsBuilder().build();
        future = options.getExecutor().submit(() -> Thread.currentThread().getName());
        name = future.get(5, TimeUnit.SECONDS);
        assertTrue(name.startsWith(DEFAULT_THREAD_NAME_PREFIX));
    }

    @Test
    public void testCallbackExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        ThreadFactory threadFactory = r -> new Thread(r, "test");
        Options options = new OptionsBuilder()
                .callbackThreadFactory(threadFactory)
                .build();
        Future<?> callbackFuture = options.getCallbackExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        callbackFuture.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testConnectExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        ThreadFactory threadFactory = r -> new Thread(r, "test");
        Options options = new OptionsBuilder()
                .connectThreadFactory(threadFactory)
                .build();
        Future<?> connectFuture = options.getConnectExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        connectFuture.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testReaderExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        ThreadFactory threadFactory = r -> new Thread(r, "test");
        Options options = new OptionsBuilder()
                .readerThreadFactory(threadFactory)
                .build();
        assertTrue(options.readerExecutorIsInternal());
        Future<?> readerFuture = options.getReaderExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        readerFuture.get(5, TimeUnit.SECONDS);

        // copy constructor preserves the factory
        Options copy = new OptionsBuilder(options).build();
        Future<?> copyFuture = copy.getReaderExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        copyFuture.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testWriterExecutor() throws ExecutionException, InterruptedException, TimeoutException {
        ThreadFactory threadFactory = r -> new Thread(r, "test");
        Options options = new OptionsBuilder()
                .writerThreadFactory(threadFactory)
                .build();
        assertTrue(options.writerExecutorIsInternal());
        Future<?> writerFuture = options.getWriterExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        writerFuture.get(5, TimeUnit.SECONDS);

        // copy constructor preserves the factory
        Options copy = new OptionsBuilder(options).build();
        Future<?> copyFuture = copy.getWriterExecutor().submit(
            () -> assertEquals("test", Thread.currentThread().getName()));
        copyFuture.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testReaderWriterExecutorDefaultsToSharedExecutor() {
        Options options = new OptionsBuilder().build();
        // neither an executor nor a factory supplied -> fall back to the shared general executor
        assertSame(options.getExecutor(), options.getReaderExecutor());
        assertSame(options.getExecutor(), options.getWriterExecutor());
        assertFalse(options.readerExecutorIsInternal());
        assertFalse(options.writerExecutorIsInternal());
    }

    @Test
    public void testReaderWriterExecutorPrecedence() {
        // a supplied executor wins over a supplied factory, and the caller owns it (not internal)
        ExecutorService readerEs = Executors.newSingleThreadExecutor();
        ExecutorService writerEs = Executors.newSingleThreadExecutor();
        try {
            Options options = new OptionsBuilder()
                    .readerExecutor(readerEs)
                    .readerThreadFactory(r -> new Thread(r, "test"))
                    .writerExecutor(writerEs)
                    .writerThreadFactory(r -> new Thread(r, "test"))
                    .build();
            assertSame(readerEs, options.getReaderExecutor());
            assertSame(writerEs, options.getWriterExecutor());
            assertFalse(options.readerExecutorIsInternal());
            assertFalse(options.writerExecutorIsInternal());
        }
        finally {
            readerEs.shutdownNow();
            writerEs.shutdownNow();
        }
    }

    String[] schemes = new String[]   { "NATS", "unk",  "tls",  "opentls",  "ws",   "wss", "nats"};
    boolean[] secures = new boolean[] { false,  false,  true,   true,       false,  true,  false};
    boolean[] wses = new boolean[]    { false,  false,  false,  false,      true,   true,  false};
    String[] hosts = new String[]     { "host", "1.2.3.4", "[1:2:3:4:5:6:7:8]", null, "nats"};
    boolean[] ips = new boolean[]     { false,  true,      true,           false, false};
    Integer[] ports = new Integer[]   {1122, null};
    String[] userInfos = new String[] {null, "u:p"};

    @Test
    public void testNatsUri() throws URISyntaxException {
        for (int e = 0; e < schemes.length; e++) {
            _testNatsUri(e, null);
            if (e > 1) {
                _testNatsUri(-e, schemes[e]);
            }
        }

        //noinspection DataFlowIssue // parameter is annotated as @NonNull
        assertThrows(NullPointerException.class, () -> new NatsUri((String)null));

        // coverage
        //noinspection SimplifiableAssertion,ConstantValue
        assertFalse(new NatsUri(DEFAULT_URL).equals(null));
        //noinspection SimplifiableAssertion
        assertFalse(new NatsUri(DEFAULT_URL).equals(new Object()));
    }

    private void _testNatsUri(int e, String nullScheme) throws URISyntaxException {
        String scheme = e < 0 ? null : schemes[e];
        e = Math.abs(e);
        for (int h = 0; h < hosts.length; h++) {
            String host = hosts[h];
            for (Integer port : ports) {
                for (String userInfo : userInfos) {
                    StringBuilder sb = new StringBuilder();
                    String expectedScheme;
                    if (scheme == null) {
                        expectedScheme = nullScheme;
                    }
                    else {
                        expectedScheme = scheme;
                        sb.append(scheme).append("://");
                    }
                    if (userInfo != null) {
                        sb.append(userInfo).append("@");
                    }
                    if (host != null) {
                        sb.append(host);
                    }
                    int expectedPort;
                    if (port == null) {
                        expectedPort = DEFAULT_PORT;
                    }
                    else {
                        expectedPort = port;
                        sb.append(":").append(port);
                    }
                    if (host == null || "unk".equals(scheme)) {
                        assertThrows(URISyntaxException.class, () -> new NatsUri(sb.toString()));
                    }
                    else {
                        NatsUri uri1 = scheme == null ? new NatsUri(sb.toString(), nullScheme) : new NatsUri(sb.toString());
                        NatsUri uri2 = new NatsUri(uri1.getUri());
                        assertEquals(uri1, uri2);
                        checkCreate(uri1, secures[e], wses[e], ips[h], expectedScheme, host, expectedPort, userInfo);
                        checkCreate(uri2, secures[e], wses[e], ips[h], expectedScheme, host, expectedPort, userInfo);
                    }
                }
            }
        }
    }

    private static void checkCreate(NatsUri uri, boolean secure, boolean ws, boolean ip, String scheme, String host, int port, String userInfo) throws URISyntaxException {
        scheme = scheme.toLowerCase();
        assertEquals(secure, uri.isSecure());
        assertEquals(ws, uri.isWebsocket());
        assertEquals(scheme, uri.getScheme());
        assertEquals(host, uri.getHost());
        assertEquals(port, uri.getPort());
        assertEquals(userInfo, uri.getUserInfo());
        String expectedUri = userInfo == null
            ? scheme + "://" + host + ":" + port
            : scheme + "://" + userInfo + "@" + host + ":" + port;
        assertEquals(expectedUri, uri.toString());
        expectedUri = userInfo == null
            ? scheme + "://rehost:" + port
            : scheme + "://" + userInfo + "@rehost:" + port;
        assertEquals(expectedUri, uri.reHost("rehost").toString());
        assertEquals(ip, uri.hostIsIpAddress());
    }


    @Test
    public void testNuriRehost() throws URISyntaxException {
        NatsUri nuri = new NatsUri("nats://host:80");
        assertEquals("nats://rehost:80", nuri.reHost("rehost").toString());
        assertEquals("nats://1.2.3.4:80", nuri.reHost("1.2.3.4").toString());
        assertEquals("nats://[1:2:3:4:5:6:7:8]:80", nuri.reHost("[1:2:3:4:5:6:7:8]").toString());
        assertEquals("nats://[1:2:3:4:5:6:7:8]:80", nuri.reHost("1:2:3:4:5:6:7:8").toString());
    }

    @Test
    public void testNuriEquivalent() throws URISyntaxException {
        // same host and port
        assertTrue(new NatsUri("nats://host:4222").equivalent(new NatsUri("nats://host:4222")));

        // case insensitive host
        assertTrue(new NatsUri("nats://HOST:4222").equivalent(new NatsUri("nats://host:4222")));

        // different port
        assertFalse(new NatsUri("nats://host:4222").equivalent(new NatsUri("nats://host:4223")));

        // different host
        assertFalse(new NatsUri("nats://host1:4222").equivalent(new NatsUri("nats://host2:4222")));

        // scheme doesn't matter for equivalence
        assertTrue(new NatsUri("nats://host:4222").equivalent(new NatsUri("tls://host:4222")));

        // host+port collision guard: "host1" port 22 vs "host12" port 2
        assertFalse(new NatsUri("nats://host1:22").equivalent(new NatsUri("nats://host12:2")));
    }

    @Test
    public void testReconnectDelayHandler() {
        ReconnectDelayHandler rdh = (round, opts, secure, ld) -> round * 2000L;

        Options o = new OptionsBuilder().reconnectDelayHandler(rdh).build();
        ReconnectDelayHandler rdhO = o.getReconnectDelayHandler();

        assertNotNull(rdhO);
        assertEquals(10000L, rdhO.getWaitTimeMillis(5L, o, false, false));

        // No custom handler → never null, falls back to the default singleton
        Options dflt = new OptionsBuilder().build();
        assertSame(DefaultReconnectDelayHandler.INSTANCE, dflt.getReconnectDelayHandler());
    }

    @Test
    public void testReconnectDelayBehavior() {
        // Default
        Options o = new OptionsBuilder().build();
        assertEquals(ReconnectDelayBehavior.LameDuckAware, o.reconnectDelayBehavior());

        // Explicit setter
        o = new OptionsBuilder().reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds).build();
        assertEquals(ReconnectDelayBehavior.BeforeAllRounds, o.reconnectDelayBehavior());

        // null resets to default
        o = new OptionsBuilder().reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds)
                                .reconnectDelayBehavior(null).build();
        assertEquals(ReconnectDelayBehavior.LameDuckAware, o.reconnectDelayBehavior());

        // Property — case-insensitive
        Properties props = new Properties();
        props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "BeforeAllRounds");
        o = new OptionsBuilder(props).build();
        assertEquals(ReconnectDelayBehavior.BeforeAllRounds, o.reconnectDelayBehavior());

        props.clear();
        props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "beforesubsequentrounds");
        o = new OptionsBuilder(props).build();
        assertEquals(ReconnectDelayBehavior.BeforeSubsequentRounds, o.reconnectDelayBehavior());

        props.clear();
        props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "LameDuckAware");
        o = new OptionsBuilder(props).build();
        assertEquals(ReconnectDelayBehavior.LameDuckAware, o.reconnectDelayBehavior());

        // Unknown value → default
        props.clear();
        props.setProperty(PROP_RECONNECT_DELAY_BEHAVIOR, "bogus");
        o = new OptionsBuilder(props).build();
        assertEquals(ReconnectDelayBehavior.LameDuckAware, o.reconnectDelayBehavior());

        // Copy-constructor preserves the value
        Options primed = new OptionsBuilder().reconnectDelayBehavior(ReconnectDelayBehavior.BeforeAllRounds).build();
        Options copy = new OptionsBuilder(primed).build();
        assertEquals(ReconnectDelayBehavior.BeforeAllRounds, copy.reconnectDelayBehavior());

        // Static factory direct coverage
        assertEquals(ReconnectDelayBehavior.LameDuckAware, ReconnectDelayBehavior.get(null));
        assertEquals(ReconnectDelayBehavior.LameDuckAware, ReconnectDelayBehavior.get(""));
        assertEquals(ReconnectDelayBehavior.BeforeAllRounds, ReconnectDelayBehavior.get("beforeallrounds"));
        assertEquals(ReconnectDelayBehavior.LameDuckAware, ReconnectDelayBehavior.get("bogus"));
    }

    @Test
    public void testPropertyReconnectDelayHandlerClass() {
        Properties props = new Properties();
        props.setProperty(PROP_RECONNECT_DELAY_HANDLER_CLASS,
                          CoverageReconnectDelayHandler.class.getCanonicalName());

        Options o = new OptionsBuilder(props).build();
        ReconnectDelayHandler handler = o.getReconnectDelayHandler();
        assertNotNull(handler);
        assertEquals(7L, handler.getWaitTimeMillis(7L, o, false, false));
    }

    @Test
    public void testInboxPrefixCoverage() {
        // Non-empty input: "." appended if missing, preserved if present
        Options o = new OptionsBuilder().inboxPrefix("foo").build();
        assertEquals("foo.", o.getInboxPrefix());
        o = new OptionsBuilder().inboxPrefix("foo.").build();
        assertEquals("foo.", o.getInboxPrefix());

        // No-arg builder re-defaults to DEFAULT_INBOX_PREFIX at build() time
        o = new OptionsBuilder().build();
        assertEquals(DEFAULT_INBOX_PREFIX, o.getInboxPrefix());

        // null input → re-default at build()
        o = new OptionsBuilder().inboxPrefix(null).build();
        assertEquals(DEFAULT_INBOX_PREFIX, o.getInboxPrefix());

        // empty input → re-default at build()
        o = new OptionsBuilder().inboxPrefix("").build();
        assertEquals(DEFAULT_INBOX_PREFIX, o.getInboxPrefix());

        // Setter does not NPE on null (audit-plan risk fix)
        new OptionsBuilder().inboxPrefix(null);
    }

    @Test
    public void testTokenSupplierCoverage() {
        // No-arg builder: tokenSupplier defaults at build() time, and the
        // default supplier returns null (no token configured).
        Options o = new OptionsBuilder().build();
        assertNull(o.getToken());

        // Explicit null to the setter → falls back to default at build()
        o = new OptionsBuilder().tokenSupplier(null).build();
        assertNull(o.getToken());

        // Explicit supplier survives intact
        Supplier<char[]> mySupplier = () -> "token-value".toCharArray();
        o = new OptionsBuilder().tokenSupplier(mySupplier).build();
        assertArrayEquals("token-value".toCharArray(), o.getToken());

        // No NPE on build() despite the username/tokenSupplier conflict check
        // dereferencing the supplier — the fallback must run first.
        new OptionsBuilder().build();
    }

    @Test
    public void testSslContextIsProvided() {
        Options o = new OptionsBuilder().server("localhost").build();
        assertNull(o.getSslContext());
        o = new OptionsBuilder().server("ws://localhost").build();
        assertNull(o.getSslContext());
        o = new OptionsBuilder().server("localhost").build();
        assertNull(o.getSslContext());
        o = new OptionsBuilder().server("tls://localhost").build();
        assertNotNull(o.getSslContext());
        o = new OptionsBuilder().server("wss://localhost").build();
        assertNotNull(o.getSslContext());
        o = new OptionsBuilder().server("opentls://localhost").build();
        assertNotNull(o.getSslContext());
        o = new OptionsBuilder().server("nats://localhost,tls://localhost").build();
        assertNotNull(o.getSslContext());
    }

    @Test
    public void testHostnameResolveMode() {
        validateHostnameResolveMode(HostnameResolveMode.ResolveToAll, new OptionsBuilder().build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToAll, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.ResolveToAll).build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToAll, new OptionsBuilder().hostnameResolveMode(null).build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToAll, "ResolveToAll");

        validateHostnameResolveMode(HostnameResolveMode.ResolveToAllIncludeIPV6, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.ResolveToAllIncludeIPV6).build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToAllIncludeIPV6, "ResolveToAllIncludeIPV6");

        validateHostnameResolveMode(HostnameResolveMode.ResolveToFirstIncludeIPV6, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.ResolveToFirstIncludeIPV6).build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToFirstIncludeIPV6, "ResolveToFirstIncludeIPV6");

        validateHostnameResolveMode(HostnameResolveMode.ResolveToFirst, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.ResolveToFirst).build());
        validateHostnameResolveMode(HostnameResolveMode.ResolveToFirst, "ResolveToFirst");

        validateHostnameResolveMode(HostnameResolveMode.Unresolved, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.Unresolved).build());
        validateHostnameResolveMode(HostnameResolveMode.Unresolved, "Unresolved");

        validateHostnameResolveMode(HostnameResolveMode.HappyEyeballs, new OptionsBuilder().hostnameResolveMode(HostnameResolveMode.HappyEyeballs).build());
        validateHostnameResolveMode(HostnameResolveMode.HappyEyeballs, "HappyEyeballs");

        // these test where multiple properties. Only the PROP_HOSTNAME_RESOLVE_MODE wins
        Properties props = new Properties();
        props.setProperty(PROP_HOSTNAME_RESOLVE_MODE, "ResolveToAll");
        Options options = new OptionsBuilder(props).build();
        assertEquals(HostnameResolveMode.ResolveToAll, options.hostnameResolveMode());
    }

    private void validateHostnameResolveMode(HostnameResolveMode expected, Options options)
    {
        assertEquals(expected, options.hostnameResolveMode());
        Options copy = new OptionsBuilder(options).build();
        assertEquals(expected, copy.hostnameResolveMode());
    }

    private void validateHostnameResolveMode(HostnameResolveMode expected, String value)
    {
        Properties props = new Properties();
        props.setProperty(PROP_HOSTNAME_RESOLVE_MODE, value);
        validateHostnameResolveMode(expected, new OptionsBuilder(props).build());
    }

/* These next three require that no default is set anywhere, if another test
    requires SSLContext.setDefault() and runs before these, they will fail. Commenting
    out for now, this can be run manually.

    @Test(expected=NoSuchAlgorithmException.class)
    public void testThrowOnBadContextForSecure() throws Exception {
        try {
            System.setProperty("javax.net.ssl.keyStore", "foo");
            System.setProperty("javax.net.ssl.trustStore", "bar");
            new Options.Builder().secure().build();
            assertFalse(true);
        }
        finally {
            System.clearProperty("javax.net.ssl.keyStore");
            System.clearProperty("javax.net.ssl.trustStore");
        }
    }

    @Test(expected=IllegalStateException.class)
    public void testThrowOnBadContextForTLSUrl() throws Exception {
        try {
            System.setProperty("javax.net.ssl.keyStore", "foo");
            System.setProperty("javax.net.ssl.trustStore", "bar");
            new Options.Builder().server("tls://localhost:4242").build();
            assertFalse(true);
        }
        finally {
            System.clearProperty("javax.net.ssl.keyStore");
            System.clearProperty("javax.net.ssl.trustStore");
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowOnBadContextSecureProp() {
        try {
            System.setProperty("javax.net.ssl.keyStore", "foo");
            System.setProperty("javax.net.ssl.trustStore", "bar");

            Properties props = new Properties();
            props.setProperty(PROP_SECURE, "true");
            new Options.Builder(props).build();
            assertFalse(true);
        }
        finally {
            System.clearProperty("javax.net.ssl.keyStore");
            System.clearProperty("javax.net.ssl.trustStore");
        }
    }
    */
}
