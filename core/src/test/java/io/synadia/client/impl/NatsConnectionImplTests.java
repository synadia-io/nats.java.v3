package io.synadia.client.impl;

import io.synadia.client.NatsTestServer;
import io.synadia.client.Options;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.utils.ConnectionUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class NatsConnectionImplTests extends TestBase {

    @Test
    public void testConnectionClosedProperly() throws Exception {
        runInSharedServer(server -> {
            Options options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(server.getNatsPort()))
                .build();
            verifyInternalExecutors(options);

            // using options copied from options to demonstrate the executors
            // came from the internal factory and were not reused
            options = new OptionsBuilder(options).build();
            verifyInternalExecutors(options);

            ExecutorService es = Executors.newFixedThreadPool(3);
            ScheduledExecutorService ses = Executors.newScheduledThreadPool(3);
            ExecutorService callbackEs = Executors.newSingleThreadExecutor();
            ExecutorService connectEs = Executors.newSingleThreadExecutor();
            ExecutorService readerEs = Executors.newCachedThreadPool();
            ExecutorService writerEs = Executors.newCachedThreadPool();
            assertFalse(es.isShutdown());
            assertFalse(ses.isShutdown());

            options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(server.getNatsPort()))
                .executor(es)
                .scheduledExecutor(ses)
                .callbackExecutor(callbackEs)
                .connectExecutor(connectEs)
                .readerExecutor(readerEs)
                .writerExecutor(writerEs)
                .build();
            verifyExternalExecutors(options, es, ses, callbackEs, connectEs, readerEs, writerEs);

            // also shows the executors where not shutdown
            verifyExternalExecutors(options, es, ses, callbackEs, connectEs, readerEs, writerEs);

            ThreadFactory callbackThreadFactory = r -> new Thread(r, "callback");
            ThreadFactory connectThreadFactory = r -> new Thread(r, "connect");
            options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(server.getNatsPort()))
                .executor(es)
                .scheduledExecutor(ses)
                .callbackThreadFactory(callbackThreadFactory)
                .connectThreadFactory(connectThreadFactory)
                .build();
            verifyExternalExecutors(options, es, ses, null, null, null, null);

            es.shutdownNow();
            ses.shutdownNow();
            callbackEs.shutdownNow();
            connectEs.shutdownNow();
            readerEs.shutdownNow();
            writerEs.shutdownNow();
            assertTrue(es.isShutdown());
            assertTrue(ses.isShutdown());
            assertTrue(callbackEs.isShutdown());
            assertTrue(connectEs.isShutdown());
            assertTrue(readerEs.isShutdown());
            assertTrue(writerEs.isShutdown());
        });
    }

    private static void verifyInternalExecutors(Options options) throws InterruptedException {
        try (NatsConnection nc = (NatsConnection) managedConnect(options)) {
            ExecutorService es = options.getExecutor();
            ScheduledExecutorService ses = options.getScheduledExecutor();
            ExecutorService callbackEs = options.getCallbackExecutor();
            ExecutorService connectEs = options.getConnectExecutor();

            assertTrue(options.executorIsInternal());
            assertTrue(options.scheduledExecutorIsInternal());
            assertTrue(options.callbackExecutorIsInternal());
            assertTrue(options.connectExecutorIsInternal());

            assertFalse(nc.executorIsClosed());
            assertFalse(nc.scheduledExecutorIsClosed());
            assertFalse(nc.callbackExecutorIsClosed());
            assertFalse(nc.connectExecutorIsClosed());

            assertFalse(es.isShutdown());
            assertFalse(ses.isShutdown());
            assertFalse(callbackEs.isShutdown());
            assertFalse(connectEs.isShutdown());

            nc.subscribe("*");
            Thread.sleep(1000);
            nc.close();

            assertTrue(nc.callbackExecutorIsClosed());
            assertTrue(nc.connectExecutorIsClosed());
            assertTrue(nc.executorIsClosed());
            assertTrue(nc.scheduledExecutorIsClosed());

            assertTrue(es.isShutdown());
            assertTrue(ses.isShutdown());
            assertTrue(callbackEs.isShutdown());
            assertTrue(connectEs.isShutdown());
        }
    }

    private static void verifyExternalExecutors(Options options,
                                                ExecutorService userEs, ScheduledExecutorService userSes,
                                                ExecutorService userCallbackEs, ExecutorService userConnectEs,
                                                ExecutorService userReaderEs, ExecutorService userWriterEs
    ) throws InterruptedException {
        try (NatsConnection nc = (NatsConnection) managedConnect(options)) {
            ExecutorService es = options.getExecutor();
            ScheduledExecutorService ses = options.getScheduledExecutor();
            ExecutorService callbackEs = options.getCallbackExecutor();
            ExecutorService connectEs = options.getConnectExecutor();

            assertEquals(es, userEs);
            assertEquals(ses, userSes);
            if (userCallbackEs != null) {
                assertEquals(callbackEs, userCallbackEs);
            }
            if (userConnectEs != null) {
                assertEquals(connectEs, userConnectEs);
            }
            if (userReaderEs != null) {
                assertEquals(options.getReaderExecutor(), userReaderEs);
                assertFalse(options.readerExecutorIsInternal());
            }
            if (userWriterEs != null) {
                assertEquals(options.getWriterExecutor(), userWriterEs);
                assertFalse(options.writerExecutorIsInternal());
            }

            assertFalse(options.executorIsInternal());
            assertFalse(options.scheduledExecutorIsInternal());
            assertFalse(options.callbackExecutorIsInternal());
            assertFalse(options.connectExecutorIsInternal());

            assertFalse(nc.executorIsClosed());
            assertFalse(nc.scheduledExecutorIsClosed());
            assertFalse(nc.callbackExecutorIsClosed());
            assertFalse(nc.connectExecutorIsClosed());

            assertFalse(es.isShutdown());
            assertFalse(ses.isShutdown());
            assertFalse(callbackEs.isShutdown());
            assertFalse(connectEs.isShutdown());

            assertFalse(userEs.isShutdown());
            assertFalse(userSes.isShutdown());
            if (userCallbackEs != null) {
                assertFalse(userCallbackEs.isShutdown());
            }
            if (userConnectEs != null) {
                assertFalse(userConnectEs.isShutdown());
            }
            if (userReaderEs != null) {
                assertFalse(userReaderEs.isShutdown());
            }
            if (userWriterEs != null) {
                assertFalse(userWriterEs.isShutdown());
            }

            nc.subscribe("*");
            Thread.sleep(1000);
            nc.close();

            assertTrue(nc.executorIsClosed());
            assertTrue(nc.scheduledExecutorIsClosed());
            assertTrue(nc.callbackExecutorIsClosed());
            assertTrue(nc.connectExecutorIsClosed());

            assertFalse(es.isShutdown());
            assertFalse(ses.isShutdown());
            assertFalse(callbackEs.isShutdown());
            assertFalse(connectEs.isShutdown());

            assertFalse(userEs.isShutdown());
            assertFalse(userSes.isShutdown());
            if (userCallbackEs != null) {
                assertFalse(userCallbackEs.isShutdown());
            }
            if (userConnectEs != null) {
                assertFalse(userConnectEs.isShutdown());
            }
            // user supplied reader/writer executors must NOT be shut down by the connection (caller owns them)
            if (userReaderEs != null) {
                assertFalse(userReaderEs.isShutdown());
            }
            if (userWriterEs != null) {
                assertFalse(userWriterEs.isShutdown());
            }
        }
    }

    @Test
    void testExecutorUseCount() throws Exception {
        runInSharedServer(server -> {
            AtomicLong count1 = new AtomicLong();
            AtomicLong count2 = new AtomicLong();

            Options options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(server.getNatsPort()))
                .build();

            // THESE SHARE THE EXACT SAME OPTIONS INSTANCE
            NatsConnection nc1 = (NatsConnection) managedConnect(options);
            NatsConnection nc2 = (NatsConnection) managedConnect(options);

            // Both connections live, both callbacks work
            nc1.makeCallback(count1::incrementAndGet);
            nc2.makeCallback(count2::incrementAndGet);
            Thread.sleep(250); // allow time for callbacks to happen
            assertEquals(1, count1.get());
            assertEquals(1, count2.get());

            // Close first connection, second connection callback will still work
            closeAndConfirm(nc1);
            Thread.sleep(250); // allow time for shutdownExecutors() to process

            nc1.makeCallback(count1::incrementAndGet);
            nc2.makeCallback(count2::incrementAndGet);
            Thread.sleep(250); // allow time for callbacks to happen
            assertEquals(1, count1.get());
            assertEquals(2, count2.get());

            // Close second connection, no callbacks will work
            closeAndConfirm(nc2);
            Thread.sleep(250); // allow time for shutdownExecutors() to process

            nc1.makeCallback(count1::incrementAndGet);
            nc2.makeCallback(count2::incrementAndGet);
            Thread.sleep(250); // allow time for callbacks to happen
            assertEquals(1, count1.get());
            assertEquals(2, count2.get());
        });
    }

    @Test
    public void testReaderWriterThreadFactories() throws Exception {
        runInSharedServer(server -> {
            // record the names of the threads the factories create, so we can prove the reader/writer
            // actually ran on threads from OUR factories rather than the shared general executor
            var created = ConcurrentHashMap.<String>newKeySet();
            ThreadFactory readerTf = r -> { Thread t = new Thread(r, "reader"); created.add(t.getName()); return t; };
            ThreadFactory writerTf = r -> { Thread t = new Thread(r, "writer"); created.add(t.getName()); return t; };

            Options options = Options.builder()
                .server(NatsTestServer.getLocalhostUri(server.getNatsPort()))
                .readerThreadFactory(readerTf)
                .writerThreadFactory(writerTf)
                .build();

            assertTrue(options.readerExecutorIsInternal());
            assertTrue(options.writerExecutorIsInternal());

            // dedicated executors, distinct from the shared general executor
            ExecutorService readerEs = options.getReaderExecutor();
            ExecutorService writerEs = options.getWriterExecutor();
            assertNotSame(options.getExecutor(), readerEs);
            assertNotSame(options.getExecutor(), writerEs);

            try (NatsConnection nc = managedConnect(options)) {
                assertFalse(nc.readerExecutorIsClosed());
                assertFalse(nc.writerExecutorIsClosed());
                nc.subscribe("*");
                Thread.sleep(500);
                nc.close();

                assertTrue(nc.readerExecutorIsClosed());
                assertTrue(nc.writerExecutorIsClosed());
                // dedicated (factory-built) executors are shut down by the connection
                assertTrue(readerEs.isShutdown());
                assertTrue(writerEs.isShutdown());
            }

            // the factories were actually used to create the reader and writer threads
            assertTrue(created.contains("reader"));
            assertTrue(created.contains("writer"));
        });
    }

    @Test
    public void testOutgoingPendingCountCoverage() throws Exception {
        runInOwnServer(nc -> {
            // Stop the writer so nothing drains the outgoing queue, giving a deterministic backlog
            // to exercise the pending-count getters against. Reading them while the writer is live
            // is an unwinnable race (a fast machine drains to 0; a slow machine backs up past the
            // reconnect buffer and the publish throws), and they can't be read during reconnect at
            // all because that path holds closeSocketLock for the whole reconnect.
            nc.getWriter().stop().get(DEFAULT_WAIT, TimeUnit.MILLISECONDS);

            String subject = random();
            byte[] data = new byte[2 * 1024]; // > 1000 bytes so pending bytes > count * 1000
            for (int x = 0; x < 20; x++) {
                nc.publish(subject, data);
            }

            assertTrue(nc.outgoingPendingMessageCount() > 0);
            assertTrue(nc.outgoingPendingBytes() > nc.outgoingPendingMessageCount() * 1000);
        });
    }
}
