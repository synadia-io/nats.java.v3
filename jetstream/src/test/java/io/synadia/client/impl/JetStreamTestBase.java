package io.synadia.client.impl;

import io.nats.NatsServerRunner;
import io.synadia.client.*;
import io.synadia.client.api.*;
import io.synadia.client.utils.JetStreamClientError;
import io.synadia.client.utils.TestBase;
import io.synadia.client.utils.VersionUtils.VersionCheck;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static io.synadia.client.utils.ConnectionUtils.managedConnect;
import static io.synadia.client.utils.JetStreamClientError.KIND_ILLEGAL_ARGUMENT;
import static io.synadia.client.utils.JetStreamClientError.KIND_ILLEGAL_STATE;
import static io.synadia.client.utils.OptionsUtils.options;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static io.synadia.client.utils.VersionUtils.VERSION_SERVER_INFO;
import static io.synadia.client.utils.VersionUtils.initVersionServerInfo;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamTestBase extends TestBase {

    public static final long DEFAULT_TIMEOUT = 1000;

    // ----------------------------------------------------------------------------------------------------
    // Publish / Read
    // ----------------------------------------------------------------------------------------------------
    public static void jsPublish(JetStream js, String subject, String prefix, int count) throws IOException, JetStreamApiException {
        jsPublish(js, subject, prefix, 1, count);
    }

    public static void jsPublish(JetStream js, String subject, String prefix, int startId, int count) throws IOException, JetStreamApiException {
        int end = startId + count - 1;
        for (int x = startId; x <= end; x++) {
            String data = prefix + x;
            js.publish(NatsMessage.builder()
                    .subject(subject)
                    .data(data.getBytes(StandardCharsets.US_ASCII))
                    .build()
            );
        }
    }

    public static void jsPublish(JetStream js, String subject, int startId, int count, long sleep) throws IOException, JetStreamApiException {
        for (int x = 0; x < count; x++) {
            js.publish(NatsMessage.builder().subject(subject).data((dataBytes(startId++))).build());
            sleep(sleep);
        }
    }

    public static void jsPublish(JetStream js, String subject, int startId, int count) throws IOException, JetStreamApiException {
        for (int x = 0; x < count; x++) {
            js.publish(NatsMessage.builder().subject(subject).data((dataBytes(startId++))).build());
        }
    }

    public static void jsPublishNull(JetStream js, String subject, int count) throws IOException, JetStreamApiException {
        for (int x = 0; x < count; x++) {
            js.publish(subject, (String)null);
        }
    }

    public static void jsPublishBytes(JetStream js, String subject, int count, byte[] bytes) throws IOException, JetStreamApiException {
        for (int x = 0; x < count; x++) {
            js.publish(subject, bytes);
        }
    }

    public static void jsPublish(JetStream js, String subject, int count) throws IOException, JetStreamApiException {
        jsPublish(js, subject, 1, count);
    }

    public static void jsPublish(NatsConnection nc, String subject, int count) throws IOException, JetStreamApiException {
        jsPublish(new JetStream(nc), subject, 1, count);
    }

    public static void jsPublish(NatsConnection nc, String subject, int startId, int count) throws IOException, JetStreamApiException {
        jsPublish(new JetStream(nc), subject, startId, count);
    }

    public static PublishAck jsPublish(JetStream js, String subject, String data) throws IOException, JetStreamApiException {
        return js.publish(NatsMessage.builder().subject(subject).data(data.getBytes(StandardCharsets.US_ASCII)).build());
    }

    public static PublishAck jsPublish(JetStream js, String subject) throws IOException, JetStreamApiException {
        return jsPublish(js, subject, DATA);
    }

    public static List<Message> readMessagesAck(JetStreamSubscription sub) throws InterruptedException {
        return readMessagesAck(sub, false, 1000L, -1);
    }

    public static List<Message> readMessagesAck(JetStreamSubscription sub, boolean noisy) throws InterruptedException {
        return readMessagesAck(sub, noisy, 1000L, -1);
    }

    // timeoutMillis follows the nextMessage convention: null = poll once (immediate), <= 0 = wait forever, > 0 = timed
    public static List<Message> readMessagesAck(JetStreamSubscription sub, @Nullable Long timeoutMillis) throws InterruptedException {
        return readMessagesAck(sub, false, timeoutMillis, -1);
    }

    public static List<Message> readMessagesAck(JetStreamSubscription sub, @Nullable Long timeoutMillis, int max) throws InterruptedException {
        return readMessagesAck(sub, false, timeoutMillis, max);
    }

    // Test-helper convenience: preserve the old timeoutMillis magic values by dispatching to the named methods.
    private static Message nextMessageByMode(JetStreamSubscription sub, @Nullable Long timeoutMillis) throws InterruptedException {
        if (timeoutMillis == null) {
            return sub.nextMessageNoWait();
        }
        if (timeoutMillis <= 0) {
            return sub.nextMessageWaitForever();
        }
        return sub.nextMessage(timeoutMillis);
    }

    public static List<Message> readMessagesAck(JetStreamSubscription sub, boolean noisy, @Nullable Long timeoutMillis, int max) throws InterruptedException {
        List<Message> messages = new ArrayList<>();
        Message msg = nextMessageByMode(sub, timeoutMillis);
        while (msg != null) {
            messages.add(msg);
            if (msg.isJetStream()) {
                if (noisy) {
                    System.out.println("ACK " + new String(msg.getData()));
                }
                msg.ack();
            }
            else if (msg.isStatusMessage()) {
                if (noisy) {
                    System.out.println("STATUS " + msg.getStatus());
                }
            }
            else if (noisy) {
                System.out.println("? " + new String(msg.getData()) + "?");
            }
            if (messages.size() == max) {
                return messages;
            }
            msg = nextMessageByMode(sub, timeoutMillis);
        }
        return messages;
    }

    public static List<Message> readMessages(Iterator<Message> iter) {
        List<Message> messages = new ArrayList<>();
        while (iter.hasNext()) {
            messages.add(iter.next());
        }
        return messages;
    }

    public static class Publisher implements Runnable {
        private final JetStream js;
        private final String subject;
        private final int jitter;
        private final AtomicBoolean keepGoing = new AtomicBoolean(true);
        private int dataId;

        public Publisher(JetStream js, String subject, int jitter) {
            this.js = js;
            this.subject = subject;
            this.jitter = jitter;
        }

        public void stop() {
            keepGoing.set(false);
        }

        @Override
        public void run() {
            try {
                while (keepGoing.get()) {
                    if (jitter > 0) {
                        sleep(ThreadLocalRandom.current().nextLong(jitter));
                    }
                    js.publish(subject, dataBytes(++dataId));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Validate / Assert
    // ----------------------------------------------------------------------------------------------------
    public static void validateRedAndTotal(int expectedRed, int actualRed, int expectedTotal, int actualTotal) {
        validateRead(expectedRed, actualRed);
        validateTotal(expectedTotal, actualTotal);
    }

    public static void validateTotal(int expectedTotal, int actualTotal) {
        assertEquals(expectedTotal, actualTotal, "Total does not match");
    }

    public static void validateRead(int expectedRed, int actualRed) {
        assertEquals(expectedRed, actualRed, "Read does not match");
    }

    public static void assertSubscription(JetStreamSubscription sub, String stream, String consumer, String deliver, boolean isPullMode) {
        assertEquals(stream, sub.getStreamName());
        if (consumer == null) {
            assertNotNull(sub.getConsumerName());
        }
        else {
            assertEquals(consumer, sub.getConsumerName());
        }
        if (deliver != null) {
            assertEquals(deliver, sub.getSubject());
        }

        if (isPullMode) {
            assertInstanceOf(JetStreamPullSubscription.class, sub);
        }
        else {
            assertInstanceOf(JetStreamPushSubscription.class, sub);
        }
    }

    public static void assertSameMessages(List<Message> l1, List<Message> l2) {
        assertEquals(l1.size(), l2.size());
        List<String> data1 = l1.stream()
                .map(m -> new String(m.getData()))
                .collect(Collectors.toList());
        List<String> data2 = l2.stream()
                .map(m -> new String(m.getData()))
                .collect(Collectors.toList());
        assertEquals(data1, data2);
    }

    public static void assertAllJetStream(List<Message> messages) {
        for (Message m : messages) {
            assertIsJetStream(m);
        }
    }

    public static void assertIsJetStream(Message m) {
        assertTrue(m.isJetStream());
        assertFalse(m.isStatusMessage());
        assertNull(m.getStatus());
    }

    public static void assertSource(JetStreamManagement jsm, String stream, Long msgCount, Long firstSeq)
            throws IOException, JetStreamApiException {
        sleep(1000);
        StreamInfo si = jsm.getStreamInfo(stream);

        assertConfig(stream, msgCount, firstSeq, si);
    }

    public static void assertMirror(JetStreamManagement jsm, String stream, String mirroring, Long msgCount, Long firstSeq)
            throws IOException, JetStreamApiException {
        sleep(1000);
        StreamInfo si = jsm.getStreamInfo(stream);

        MirrorInfo msi = si.getMirrorInfo();
        assertNotNull(msi);
        assertEquals(mirroring, msi.getName());

        assertConfig(stream, msgCount, firstSeq, si);
    }

    public static void assertConfig(String stream, Long msgCount, Long firstSeq, StreamInfo si) {
        StreamConfiguration sc = si.getConfiguration();
        assertNotNull(sc);
        assertEquals(stream, sc.getName());

        StreamState ss = si.getStreamState();
        if (msgCount != null) {
            assertEquals(msgCount, ss.getMessageCount());
        }
        if (firstSeq != null) {
            assertEquals(firstSeq, ss.getFirstSequence());
        }
    }

    public static void assertStreamSource(MessageInfo info, String stream, int i) {
        assertNotNull(info.getHeaders());
        List<String> nss = info.getHeaders().get("Nats-Stream-Source");
        assertNotNull(nss);
        String hval = nss.get(0);
        assertTrue(hval.contains(stream));
        assertTrue(hval.contains("" + i));
    }

    // ----------------------------------------------------------------------------------------------------
    // Subscription or test macros
    // ----------------------------------------------------------------------------------------------------
    public static void unsubscribeEnsureNotBound(JetStreamSubscription sub) throws IOException, JetStreamApiException {
        sub.unsubscribe();
        ensureNotBound(sub);
    }

    public static void unsubscribeEnsureNotBound(Dispatcher dispatcher, JetStreamSubscription sub) throws JetStreamApiException, IOException {
        dispatcher.unsubscribe(sub);
        ensureNotBound(sub);
    }

    public static void ensureNotBound(JetStreamSubscription sub) throws IOException, JetStreamApiException {
        ConsumerInfo ci = sub.getConsumerInfo();
        long start = System.currentTimeMillis();
        while (ci.isPushBound()) {
            if (System.currentTimeMillis() - start > 5000) {
                return; // don't wait forever
            }
            sleep(5);
            ci = sub.getConsumerInfo();
        }
    }

    // Flapper fix: For whatever reason 10 seconds isn't enough on slow machines
    // I've put this in a function so all latch awaits give plenty of time
    public static void awaitAndAssert(CountDownLatch latch) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (latch.getCount() > 0 && System.currentTimeMillis() - start < 30000) {
            latch.await(1, TimeUnit.SECONDS);
        }
        assertEquals(0, latch.getCount());
    }

    // ----------------------------------------------------------------------------------------------------
    // Subscription or test macros
    // ----------------------------------------------------------------------------------------------------
    public void assertClientError(JetStreamClientError error, Executable executable) {
        Exception e = assertThrows(Exception.class, executable);
        assertTrue(e.getMessage().contains(error.id()));
        if (error.getKind() == KIND_ILLEGAL_ARGUMENT) {
            assertInstanceOf(IllegalArgumentException.class, e);
        }
        else if (error.getKind() == KIND_ILLEGAL_STATE) {
            assertInstanceOf(IllegalStateException.class, e);
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // JetStream JetStream JetStream JetStream JetStream JetStream JetStream JetStream JetStream JetStream
    // ----------------------------------------------------------------------------------------------------
    public static void createMemoryStream(JetStreamManagement jsm, String streamName, String... subjects) throws IOException, JetStreamApiException {
        if (streamName == null) {
            streamName = random();
        }

        if (subjects == null || subjects.length == 0) {
            subjects = new String[]{random()};
        }

        StreamCreator sc = new StreamCreator(streamName)
            .storageType(StorageType.Memory)
            .subjects(subjects);

        jsm.addStream(sc);
    }

    // ----------------------------------------------------------------------------------------------------
    // runners -> own server, yes jetstream
    // ----------------------------------------------------------------------------------------------------
    public interface JetStreamTest {
        void test(NatsConnection nc, JetStreamManagement jsm, JetStream js) throws Exception;
    }

    public interface JetStreamTestingContextTest {
        void test(NatsConnection nc, JetStreamTestingContext ctx) throws Exception;
    }

    private static void _runInOwnJsServer(VersionCheck vc, @NonNull JetStreamTest jsTest) throws Exception {
        if (vc != null && VERSION_SERVER_INFO != null && !vc.runTest(VERSION_SERVER_INFO)) {
            return; // had vc, already had run server info and fails check
        }

        NatsServerRunner.Builder nsrb = NatsServerRunner.builder().jetstream(true);
        try (NatsTestServer ts = new NatsTestServer(nsrb)) {
            try (NatsConnection nc = managedConnect(options(ts))) {
                initVersionServerInfo(nc);
                if (vc == null || vc.runTest(VERSION_SERVER_INFO)) {
                    JetStreamManagement jsm = new JetStreamManagement(nc);
                    JetStream js = new JetStream(nc);
                    jsTest.test(nc, jsm, js);
                }
            }
        }
    }

    public static void runInOwnJsServer(JetStreamTest jetStreamTest) throws Exception {
        _runInOwnJsServer(null, jetStreamTest);
    }

    public static void runInOwnJsServer(VersionCheck vc, JetStreamTest jetStreamTest) throws Exception {
        _runInOwnJsServer(vc, jetStreamTest);
    }

    // ----------------------------------------------------------------------------------------------------
    // runners -> shared
    // ----------------------------------------------------------------------------------------------------
    static final String SHARED_NAME = "SHARED";

    private static void _runInShared(
        OptionsBuilder optionsBuilder,
        VersionCheck vc,
        OneConnectionTest oneNcTest,
        TwoConnectionTest twoNcTest,
        int jstcTestSubjectCount,
        JetStreamTestingContextTest ctxTest
    ) throws Exception {
        if (vc != null && VERSION_SERVER_INFO != null && !vc.runTest(VERSION_SERVER_INFO)) {
            return; // had vc, already had run server info and fails check
        }

        SharedServer shared = SharedServer.getInstance(SHARED_NAME);

        // no builder, we can use the long-running connection since it's totally generic
        // with a builder, just make a fresh connection and close it at the end.
        boolean closeNcWhenDone;
        NatsConnection nc;
        NatsConnection nc2 = null;

        if (optionsBuilder == null) {
            closeNcWhenDone = false;
            nc = shared.getSharedConnection();
            if (twoNcTest != null) {
                nc2 = shared.getSharedConnection();
            }
        }
        else {
            closeNcWhenDone = true;
            nc = shared.newConnection(optionsBuilder);
            if (twoNcTest != null) {
                nc2 = shared.newConnection(optionsBuilder);
            }
        }

        initVersionServerInfo(nc);
        if (vc == null || vc.runTest(VERSION_SERVER_INFO)) {
            try {
                if (ctxTest != null) {
                    try (JetStreamTestingContext ctx = new JetStreamTestingContext(nc, jstcTestSubjectCount)) {
                        ctxTest.test(nc, ctx);
                    }
                }
                else if (oneNcTest != null) {
                    oneNcTest.test(nc);

                }
                else if (twoNcTest != null) {
                    twoNcTest.test(nc, nc2);
                }
            }
            finally {
                if (closeNcWhenDone) {
                    try { nc.close(); } catch (Exception ignore) {}
                    if (nc2 != null) {
                        try { nc2.close(); } catch (Exception ignore) {}
                    }
                }
            }
        }
    }

    // --------------------------------------------------
    // JetStream: 1 stream 1 subject
    // --------------------------------------------------
    public static void runInShared(JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(null, null, null, null, 1, ctxTest);
    }

    public static void runInShared(VersionCheck vc, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(null, vc, null, null, 1, ctxTest);
    }

    public static void runInSharedOwnNc(ErrorListener el, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(optionsBuilder(el), null, null, null, 1, ctxTest);
    }

    public static void runInSharedOwnNc(ErrorListener el, VersionCheck vc, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(optionsBuilder(el), vc, null, null, 1, ctxTest);
    }

    public static void runInSharedOwnNc(OptionsBuilder builder, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(builder, null, null, null, 1, ctxTest);
    }

    public static void runInSharedOwnNc(OptionsBuilder builder, VersionCheck vc, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(builder, vc, null, null, 1, ctxTest);
    }

    // --------------------------------------------------
    // JetStream: 1 stream custom subjects, kv or os
    // --------------------------------------------------
    public static void runInSharedCustom(JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(null, null, null, null, 0, ctxTest);
    }

    public static void runInSharedCustom(VersionCheck vc, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(null, vc, null, null, 0, ctxTest);
    }

    public static void runInSharedCustom(ErrorListener el, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(optionsBuilder(el), null, null, null, 0, ctxTest);
    }

    public static void runInSharedCustom(OptionsBuilder builder, JetStreamTestingContextTest ctxTest) throws Exception {
        _runInShared(builder, null, null, null, 0, ctxTest);
    }

    // --------------------------------------------------
    // Local for debugging
    // --------------------------------------------------
    public static void runInLocal(JetStreamTestingContextTest ctxTest) throws Exception {
        try (NatsConnection nc = Nats.connect()) {
            try (JetStreamTestingContext ctx = new JetStreamTestingContext(nc, 1)) {
                ctxTest.test(nc, ctx);
            }
        }
    }
}
