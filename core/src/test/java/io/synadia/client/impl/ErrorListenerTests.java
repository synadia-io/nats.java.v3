package io.synadia.client.impl;

import io.synadia.client.*;
import io.synadia.client.api.Status;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.client.utils.ConnectionUtils.*;
import static io.synadia.client.utils.Listener.LONG_VALIDATE_TIMEOUT;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class ErrorListenerTests extends TestBase {

    @Test
    public void testLastError_ClearError_AuthViolation() throws Exception {
        NatsConnection nc;
        Listener listener = new Listener(true);
        String[] customArgs = {"--user", "stephen", "--pass", "password"};

        try (NatsTestServer ts = new NatsTestServer();
             NatsTestServer ts2 = new NatsTestServer(customArgs); //ts2 requires auth
             NatsTestServer ts3 = new NatsTestServer()) {
            Options options = optionsBuilder(ts.getServerUri(), ts2.getServerUri(), ts3.getServerUri())
                .noRandomize()
                .connectionListener(listener)
                .errorListener(listener)
                .maxReconnects(-1)
                .build();
            nc = Nats.connect(options);
            assertConnected(nc);
            assertEquals(ts.getServerUri(), nc.getConnectedUrl());
            listener.queueConnectionEvent(ConnectionEvents.DISCONNECTED);
            listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
            listener.queueError("Authorization Violation");

            ts.close();

            try {
                nc.flush(1000);
            }
            catch (Exception exp) {
                // this usually fails
            }

            listener.validateAll();

            confirmConnected(nc); // wait for reconnect
            assertEquals(ts3.getServerUri(), nc.getConnectedUrl());

            nc.clearLastError();
            assertNull(nc.getLastError());
        }
    }

    @Test
    public void testExceptionOnBadDispatcher() throws Exception {
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .errorListener(listener)
                .build();
            try (NatsConnection nc = Nats.connect(options)) {
                Dispatcher d = nc.createDispatcher(msg -> {
                    throw new ArithmeticException();
                });
                String subject = random();
                d.subscribe(subject);
                Future<Message> incoming = nc.requestAsync(subject, null);

                try {
                    incoming.get(200, TimeUnit.MILLISECONDS);
                    fail();
                }
                catch (TimeoutException te) {
                    // expected
                }
                assertEquals(1, listener.getExceptionCount());
            }
        }
    }

    @Test
    public void testExceptionInErrorHandler() throws Exception {
        String[] customArgs = {"--user", "stephen", "--pass", "password"};
        BadHandler listener = new BadHandler();
        try (NatsTestServer ts = new NatsTestServer(customArgs)) {
            // See config file for user/pass
            // don't put u/p in options
            Options options = optionsBuilder(ts)
                .maxReconnects(0)
                .errorListener(listener)
                .build();
            //noinspection resource
            assertThrows(IOException.class, () -> Nats.connect(options));
        }
    }

    @Test
    public void testExceptionInSlowConsumerHandler() throws Exception {
        BadHandler listener = new BadHandler();
        try (NatsTestServer ts = new NatsTestServer();
             NatsConnection nc = Nats.connect(optionsBuilder(ts).errorListener(listener).build())) {

            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            sub.setPendingLimits(1, -1);

            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);
            nc.publish(subject, null);

            nc.flush(5000);

            assertEquals(3, sub.getDroppedCount());

            nc.close();

            // The slow-consumer listener runs on the callback executor. In these tests that
            // executor is user-supplied, so close() does not drain it (close only shuts down
            // internal executors). Wait for the listener's exception to be recorded rather
            // than reading the counter immediately after close.
            long stopAt = System.currentTimeMillis() + DEFAULT_WAIT;
            while (nc.getStatistics().getExceptions() == 0 && System.currentTimeMillis() < stopAt) {
                sleep(50);
            }
            assertTrue(nc.getStatistics().getExceptions() > 0);
        }
    }

    @Test
    public void testExceptionInExceptionHandler() throws Exception {
        BadHandler listener = new BadHandler();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts).maxReconnects(0).errorListener(listener).build();
            NatsConnection nc = Nats.connect(options);
            try {
                Dispatcher d = nc.createDispatcher(msg -> {
                    throw new ArithmeticException();
                });
                String subject = random();
                d.subscribe(subject);
                Future<Message> incoming = nc.requestAsync(subject, null);

                Message msg;

                try {
                    msg = incoming.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    msg = null;
                }

                assertNull(msg);
                assertEquals(2, nc.getStatistics().getExceptions()); // 1 for the dispatcher, 1 for the handlers
            } finally {
                closeAndConfirm(nc);
            }
        }
    }

    @Test
    public void testDiscardedMessageFastProducer() throws Exception {
        String subject = random();
        int maxMessages = 10;
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .maxMessagesInOutgoingQueue(maxMessages)
                .discardMessagesWhenOutgoingQueueFull()
                .errorListener(listener)
                .pingInterval(100_000) // make this long so we don't ping during test
                .build();
            NatsConnection nc = Nats.connect(options);

            try {
                nc.flush(2000);
                nc.getWriter().stop().get(2, TimeUnit.SECONDS);
                for (int i = 0; i < maxMessages + 1; i++) {
                    nc.publish(subject + i, ("message" + i).getBytes());
                }
                nc.getWriter().start(nc.getDataPortFuture());

                nc.flush(2000);
            } finally {
                closeAndConfirm(nc);
            }
        }

        List<Message> discardedMessages = listener.getDiscardedMessages();
        assertFalse(discardedMessages.isEmpty(), "expected discardedMessages > 0, got " + discardedMessages.size());
        int offset = maxMessages + 1 - discardedMessages.size();
        assertEquals(subject + offset, discardedMessages.get(0).getSubject());
        assertEquals("message" + offset, new String(discardedMessages.get(0).getData()));
    }

    @Test
    public void testDiscardedMessageServerClosed() throws Exception {
        String subject = random();
        int maxMessagesInOutgoingQueue = 10;
        int publishNoMoreThan = 1000;
        Listener listener = new Listener();
        try (NatsTestServer ts = new NatsTestServer()) {
            Options options = optionsBuilder(ts)
                .maxMessagesInOutgoingQueue(maxMessagesInOutgoingQueue)
                .discardMessagesWhenOutgoingQueueFull()
                .connectionListener(listener)
                .errorListener(listener)
                .pingInterval(100_000) // make this long so we don't ping during test
                .build();
            listener.queueConnectionEvent(ConnectionEvents.CONNECTED, LONG_VALIDATE_TIMEOUT);
            listener.queueConnectionEvent(ConnectionEvents.DISCONNECTED, LONG_VALIDATE_TIMEOUT);
            try (NatsConnection nc = managedConnect(options)) {
                nc.flush(1000);
                listener.validate();
                ts.close();
                listener.validate();
                for (int i = 0; i < publishNoMoreThan; i++) {
                    nc.publish(subject + i, ("message" + i).getBytes());
                    // don't check every time
                    if (i % maxMessagesInOutgoingQueue == 0 && !listener.getDiscardedMessages().isEmpty()) {
                        break;
                    }
                }
            }
        }

        List<Message> discardedMessages = listener.getDiscardedMessages();
        assertFalse(discardedMessages.isEmpty(), "At least one message discarded");
        assertTrue(discardedMessages.get(0).getSubject().startsWith(subject), "Message subject");
        assertTrue(new String(discardedMessages.get(0).getData()).startsWith("message"), "Message data");
    }

    @Test
    public void testCoverage() {
        // this exercises default interface implementation
        _cover(new ErrorListener() {});

        // exercises a little more than the defaults
        AtomicBoolean errorOccurredFlag = new AtomicBoolean();
        AtomicBoolean exceptionOccurredFlag = new AtomicBoolean();
        AtomicBoolean slowConsumerDetectedFlag = new AtomicBoolean();
        AtomicBoolean messageDiscardedFlag = new AtomicBoolean();
        AtomicBoolean heartbeatAlarmFlag = new AtomicBoolean();
        AtomicBoolean unhandledStatusFlag = new AtomicBoolean();
        AtomicBoolean pullStatusWarningFlag = new AtomicBoolean();
        AtomicBoolean pullStatusErrorFlag = new AtomicBoolean();
        AtomicBoolean flowControlProcessedFlag = new AtomicBoolean();

        _cover(new ErrorListener() {
            @Override
            public void errorOccurred(NatsConnection conn, String error) {
                errorOccurredFlag.set(true);
            }

            @Override
            public void exceptionOccurred(NatsConnection conn, Exception exp) {
                exceptionOccurredFlag.set(true);
            }

            @Override
            public void slowConsumerDetected(NatsConnection conn, Subscription subscription) {
                slowConsumerDetectedFlag.set(true);
            }

            @Override
            public void messageDiscarded(NatsConnection conn, Message msg) {
                messageDiscardedFlag.set(true);
            }

            @Override
            public void heartbeatAlarm(NatsConnection conn, Subscription sub, long lastStreamSequence, long lastConsumerSequence) {
                heartbeatAlarmFlag.set(true);
            }

            @Override
            public void unhandledStatus(NatsConnection conn, Subscription sub, Status status) {
                unhandledStatusFlag.set(true);
            }

            @Override
            public void pullStatusWarning(NatsConnection conn, Subscription sub, Status status) {
                pullStatusWarningFlag.set(true);
            }

            @Override
            public void pullStatusError(NatsConnection conn, Subscription sub, Status status) {
                pullStatusErrorFlag.set(true);
            }

            @Override
            public void flowControlProcessed(NatsConnection conn, Subscription sub, String subject, FlowControlSource source) {
                flowControlProcessedFlag.set(true);
            }
        });

        assertTrue(errorOccurredFlag.get());
        assertTrue(exceptionOccurredFlag.get());
        assertTrue(slowConsumerDetectedFlag.get());
        assertTrue(messageDiscardedFlag.get());
        assertTrue(heartbeatAlarmFlag.get());
        assertTrue(unhandledStatusFlag.get());
        assertTrue(pullStatusWarningFlag.get());
        assertTrue(pullStatusErrorFlag.get());
        assertTrue(flowControlProcessedFlag.get());

        _cover(new ReaderListenerConsoleImpl());
        _cover(new ReadListener() {});
    }

    private void _cover(ErrorListener el) {
        el.errorOccurred(null, null);
        el.exceptionOccurred(null, null);
        el.slowConsumerDetected(null, null);
        el.messageDiscarded(null, null);
        el.heartbeatAlarm(null, null, -1, -1);
        el.unhandledStatus(null, null, null);
        el.pullStatusWarning(null, null, null);
        el.pullStatusError(null, null, null);
        el.flowControlProcessed(null, null, null, null);
        el.socketWriteTimeout(null);
    }

    private void _cover(ReadListener rl) {
        rl.protocol("OP", null);
        rl.message("OP", new NatsMessage("subject", "replyTo", null));
        rl.message("OP", new NatsMessage("subject", "replyTo", "body".getBytes()));
    }

    static class CapturingErrorListener implements ErrorListener {
        final String name;
        final Set<String> captured;
        final CountDownLatch latch;

        CapturingErrorListener(String name, Set<String> captured, CountDownLatch latch) {
            this.name = name;
            this.captured = captured;
            this.latch = latch;
        }

        @Override
        public void errorOccurred(NatsConnection conn, String error) {
            captured.add(name + "-" + error);
            latch.countDown();
        }
    }

    @Test
    public void testMultipleErrorListeners() throws Exception {
        Set<String> captured = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(3);

        CapturingErrorListener fromOptions = new CapturingErrorListener("EL1", captured, latch);
        CapturingErrorListener added = new CapturingErrorListener("EL2", captured, latch);
        CapturingErrorListener alsoAdded = new CapturingErrorListener("EL3", captured, latch);
        CapturingErrorListener removed = new CapturingErrorListener("NEVER INVOKED", captured, latch);

        OptionsBuilder builder = optionsBuilder().errorListener(fromOptions);
        runInSharedOwnNc(builder, nc -> {
            //noinspection DataFlowIssue // parameter is annotated as @NonNull
            assertThrows(NullPointerException.class, () -> nc.addErrorListener(null));
            //noinspection DataFlowIssue // parameter is annotated as @NonNull
            assertThrows(NullPointerException.class, () -> nc.removeErrorListener(null));

            nc.addErrorListener(removed);
            nc.addErrorListener(added);
            nc.addErrorListener(new ErrorListener() {
                @Override
                public void errorOccurred(NatsConnection conn, String error) {
                    throw new RuntimeException("should not interfere with other listeners");
                }
            });
            nc.addErrorListener(alsoAdded);
            nc.removeErrorListener(removed);

            nc.processError("boom");
            assertTrue(latch.await(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS), "all listeners notified");
        });

        Set<String> expected = new HashSet<>(Arrays.asList("EL1-boom", "EL2-boom", "EL3-boom"));
        assertEquals(expected, captured);
    }

    @Test
    public void testRemoveErrorListenerById() throws Exception {
        Set<String> captured = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(1);
        CapturingErrorListener stays = new CapturingErrorListener("STAYS", captured, latch);
        CapturingErrorListener goes = new CapturingErrorListener("NEVER INVOKED", captured, latch);

        runInSharedOwnNc(nc -> {
            nc.addErrorListener(stays);
            nc.addErrorListener(goes);
            nc.removeErrorListenerById(goes.getErrorListenerId());
            nc.removeErrorListenerById("not-a-registered-id"); // no-op

            nc.processError("boom");
            assertTrue(latch.await(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS), "remaining listener notified");
        });

        assertEquals(new HashSet<>(Collections.singletonList("STAYS-boom")), captured);
    }

    @Test
    public void testAddErrorListenerWithSameIdReplaces() throws Exception {
        Set<String> captured = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(1);

        // same id, so the second add replaces the first rather than adding a second listener
        CapturingErrorListener first = new SameIdErrorListener("FIRST", captured, latch);
        CapturingErrorListener second = new SameIdErrorListener("SECOND", captured, latch);

        runInSharedOwnNc(nc -> {
            nc.addErrorListener(first);
            nc.addErrorListener(second);

            nc.processError("boom");
            assertTrue(latch.await(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS), "listener notified");
            sleep(200); // give the replaced listener a chance to fire if it wrongly remained
        });

        assertEquals(new HashSet<>(Collections.singletonList("SECOND-boom")), captured);
    }

    static class SameIdErrorListener extends CapturingErrorListener {
        SameIdErrorListener(String name, Set<String> captured, CountDownLatch latch) {
            super(name, captured, latch);
        }

        @Override
        public String getErrorListenerId() {
            return "shared-id";
        }
    }

    // The no-error-listener state is only reachable now that the builder no longer seeds a no-op default.
    // The test helper optionsBuilder() supplies its own NOOP_EL, so clear it with the no-arg varargs call.
    @Test
    public void testNoErrorListeners() throws Exception {
        OptionsBuilder builder = optionsBuilder().errorListener();
        assertTrue(builder.build().getErrorListeners().isEmpty(), "no error listener is seeded");

        runInSharedOwnNc(builder, nc -> {
            assertTrue(nc.getOptions().getErrorListeners().isEmpty());

            // every path that used to be guaranteed a listener must now be a silent no-op
            nc.processError("boom");
            nc.processException(new IOException("boom"));
            nc.processSlowConsumer(null);
            nc.notifyErrorListener((c, el) -> el.socketWriteTimeout(c));
            nc.queueOutgoing(new NatsMessage("subject", null, "body".getBytes()));

            sleep(200); // let any callback that was wrongly scheduled run and blow up
            assertConnected(nc);
        });
    }

    @Test
    public void testConnectAsynchronouslyNotifiesErrorListeners() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Options options = optionsBuilder("nats://localhost:" + NatsTestServer.nextPort())
            .maxReconnects(0)
            .connectionListener(new Listener())
            .errorListener(new ErrorListener() {
                @Override
                public void exceptionOccurred(NatsConnection conn, Exception exp) {
                    latch.countDown();
                }
            })
            .build();

        // the error listener is notified in addition to the future completing exceptionally
        CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, false);
        assertTrue(latch.await(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS), "error listener notified");
        ExecutionException ee = assertThrows(ExecutionException.class,
            () -> future.get(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS));
        assertInstanceOf(IOException.class, ee.getCause());
    }

    // The async connect failure path reads the listeners straight off the Options (there is no
    // NatsConnection to attach to), so it must tolerate the now-legal empty list.
    @Test
    public void testConnectAsynchronouslyWithNoErrorListener() throws Exception {
        Options options = optionsBuilder("nats://localhost:" + NatsTestServer.nextPort())
            .maxReconnects(0)
            .connectionListener(new Listener())
            .errorListener() // clear the test helper's default
            .build();
        assertTrue(options.getErrorListeners().isEmpty());

        // with no error listener to notify, the future is the only report of the failure
        CompletableFuture<NatsConnection> future = Nats.connectAsynchronously(options, false);
        assertThrows(ExecutionException.class, () -> future.get(LONG_VALIDATE_TIMEOUT, TimeUnit.MILLISECONDS));
    }
}
