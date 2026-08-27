package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.synadia.client.*;
import io.synadia.client.api.*;
import io.synadia.client.utils.ConnectionUtils;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.VersionUtils;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.impl.BaseConsumeOptions.*;
import static io.synadia.client.utils.JetStreamApiUtils.ULONG_UNSET;
import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class SimplificationTests extends JetStreamTestBase {

    @Test
    public void testStreamContextBasics() throws Exception {
        runInShared((nc, ctx) -> {
            assertThrows(JetStreamApiException.class, () -> ctx.js.getStreamContext(random()));
            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            assertEquals(ctx.stream, streamContext.getStreamName());
            _testStreamContext(ctx, streamContext);
        });
    }

    private void _testStreamContext(JetStreamTestingContext ctx, StreamContext streamContext) throws JetStreamException, InterruptedException {
        String durable = random();
        assertThrows(JetStreamApiException.class, () -> streamContext.getConsumerContext(durable));
        assertThrows(JetStreamApiException.class, () -> streamContext.deleteConsumer(durable));

        PullConsumerCreator creator = new PullConsumerCreator().durable(durable);
        ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(creator);
        ConsumerInfo ci = consumerContext.retrieveConsumerInfo();
        assertEquals(ctx.stream, ci.getStreamName());
        assertEquals(durable, ci.getName());

        ci = streamContext.getConsumerInfo(durable);
        assertNotNull(ci);
        assertEquals(ctx.stream, ci.getStreamName());
        assertEquals(durable, ci.getName());

        assertEquals(1, streamContext.getConsumerNames().size());

        assertEquals(1, streamContext.getConsumers().size());
        consumerContext = streamContext.getConsumerContext(durable);
        assertNotNull(consumerContext);
        assertEquals(durable, consumerContext.getConsumerName());

        ci = consumerContext.retrieveConsumerInfo();
        assertNotNull(ci);
        assertEquals(ctx.stream, ci.getStreamName());
        assertEquals(durable, ci.getName());

        ci = consumerContext.getCachedConsumerInfo();
        assertNotNull(ci);
        assertEquals(ctx.stream, ci.getStreamName());
        assertEquals(durable, ci.getName());

        streamContext.deleteConsumer(durable);

        assertThrows(JetStreamApiException.class, () -> streamContext.getConsumerContext(durable));
        assertThrows(JetStreamApiException.class, () -> streamContext.deleteConsumer(durable));

        // coverage
        ctx.js.publish(ctx.subject(), "one".getBytes());
        ctx.js.publish(ctx.subject(), "two".getBytes());
        ctx.js.publish(ctx.subject(), "three".getBytes());
        ctx.js.publish(ctx.subject(), "four".getBytes());
        ctx.js.publish(ctx.subject(), "five".getBytes());
        ctx.js.publish(ctx.subject(), "six".getBytes());

        assertTrue(streamContext.deleteMessage(3));
        assertTrue(streamContext.deleteMessage(4, true));

        MessageInfo mi = streamContext.getMessage(1);
        assertEquals(1, mi.getSequence());

        mi = streamContext.getFirstMessage(ctx.subject());
        assertEquals(1, mi.getSequence());

        mi = streamContext.getLastMessage(ctx.subject());
        assertEquals(6, mi.getSequence());

        mi = streamContext.getNextMessage(3, ctx.subject());
        assertEquals(5, mi.getSequence());

        assertNotNull(streamContext.getStreamInfo());
        assertNotNull(streamContext.getStreamInfo(StreamInfoOptions.builder().build()));

        streamContext.purge(PurgeOptions.builder().sequence(5).build());
        assertThrows(JetStreamApiException.class, () -> streamContext.getMessage(1));

        StreamInfo si = ctx.jsm.getStreamInfo(ctx.stream);
        assertEquals(2, si.getStreamState().getMessageCount());
        assertEquals(5, si.getStreamState().getFirstSequence());
        assertEquals(6, si.getStreamState().getLastSequence());

        ctx.js.publish(ctx.subject(), "aone".getBytes());
        ctx.js.publish(ctx.subject(), "btwo".getBytes());
        ctx.js.publish(ctx.subject(), "cthree".getBytes());
        ctx.js.publish(ctx.subject(), "dfour".getBytes());
        ctx.js.publish(ctx.subject(), "efive".getBytes());
        ctx.js.publish(ctx.subject(), "fsix".getBytes());

        si = ctx.jsm.getStreamInfo(ctx.stream);
        assertEquals(8, si.getStreamState().getMessageCount());
        assertEquals(12, si.getStreamState().getLastSequence());

        streamContext.purge();

        si = ctx.jsm.getStreamInfo(ctx.stream);
        assertEquals(0, si.getStreamState().getMessageCount());
        assertEquals(12, si.getStreamState().getLastSequence());
    }

    private void validateConsumerName(BaseConsumerContext bcc, MessageConsumer mc, String consumerName) throws JetStreamException, InterruptedException {
        assertEquals(consumerName, bcc.getConsumerName());
        if (mc != null) {
            assertNotNull(mc.getCachedConsumerInfo());
            assertEquals(consumerName, mc.getConsumerName());
            assertEquals(consumerName, mc.getConsumerInfo().getName());
        }
    }

    private String validateConsumerNameForOrdered(BaseConsumerContext bcc, MessageConsumer mc, String prefix) throws JetStreamException, InterruptedException {
        String bccConsumerName = bcc.getConsumerName();
        assertNotNull(bccConsumerName);
        if (prefix != null) {
            assertTrue(bccConsumerName.startsWith(prefix));
        }

        if (mc != null) {
            if (prefix == null) {
                assertNotNull(mc.getConsumerName());
                assertNotNull(mc.getConsumerInfo().getName());
            }
            else {
                assertTrue(mc.getConsumerName().startsWith(prefix));
                assertTrue(mc.getConsumerInfo().getName().startsWith(prefix));
            }
        }
        return bccConsumerName;
    }

    static int FETCH_EPHEMERAL = 1;
    static int FETCH_DURABLE = 2;
    static int FETCH_ORDERED = 3;

    @Test
    public void testFetchEphemeral() throws Exception {
        runInShared((nc, ctx) -> _testFetch(ctx, FETCH_EPHEMERAL));
    }

    @Test
    public void testFetchDurable() throws Exception {
        runInShared((nc, ctx) -> _testFetch(ctx, FETCH_DURABLE));
    }

    @Test
    public void testFetchOrdered() throws Exception {
        runInShared((nc, ctx) -> _testFetch(ctx, FETCH_ORDERED));
    }

    private void _testFetch(JetStreamTestingContext ctx, int testType) throws Exception {
        for (int x = 1; x <= 20; x++) {
            ctx.js.publish(ctx.subject(), ("test-fetch-msg-" + testType + "-" + x).getBytes());
        }

        // 1. Different fetch sizes demonstrate expiration behavior

        // 1A. equal number of messages to the fetch size
        _testFetch("1A", ctx, 20, 0, 20, testType, false);

        // 1B. more messages than the fetch size
        _testFetch("1B", ctx, 10, 0, 10, testType, false);

        // 1C. fewer messages than the fetch size
        _testFetch("1C", ctx, 40, 0, 40, testType, false);

        // 1D. simple-consumer-40msgs was created in 1C and has no messages available
        _testFetch("1D", ctx, 40, 0, 40, testType, false);

        // 2. Different max bytes sizes demonstrate expiration behavior
        //    - each test message is approximately 100 bytes

        // 2A. max bytes are reached before message count
        _testFetch("2A", ctx, 0, 750, 20, testType, false);

        // 2B. fetch size is reached before byte count
        _testFetch("2B", ctx, 10, 1500, 10, testType, false);

        if (testType == FETCH_DURABLE) {
            // this is long-running, so don't want to test every time
            // 2C. fewer bytes than the byte count
            _testFetch("2C", ctx, 0, 3000, 40, testType, false);
        }
        else if (testType == FETCH_ORDERED) {
            // just to get coverage of testing with a consumer name prefix
            _testFetch("1A", ctx, 20, 0, 20, testType, true);
            _testFetch("2A", ctx, 0, 750, 20, testType, true);
        }
    }

    private void _testFetch(String testType, JetStreamTestingContext ctx, int maxMessages, int maxBytes, int testAmount, int fetchType, boolean useConsumerPrefix) throws Exception {
        StreamContext streamCtx = ctx.js.getStreamContext(ctx.stream);

        String consumerName = null;
        String consumerNamePrefix = null;
        BaseConsumerContext consumerContext;
        if (fetchType == FETCH_ORDERED) {
            PullOrderedConsumerCreator creator = new PullOrderedConsumerCreator();
            if (useConsumerPrefix) {
                consumerNamePrefix = random();
                creator.namePrefix(consumerNamePrefix);
            }
            consumerContext = streamCtx.createOrderedConsumer(creator);
            assertNull(consumerContext.getConsumerName());
        }
        else {
            // Pre define a consumer
            consumerName = generateConsumerName(maxMessages, maxBytes);
            PullConsumerCreator creator = new PullConsumerCreator();
            if (fetchType == FETCH_DURABLE) {
                consumerName = consumerName + "D";
                creator.durable(consumerName);
            }
            else {
                consumerName = consumerName + "E";
                creator.name(consumerName).inactiveThreshold(10000);
            }
            ctx.jsm.createOrUpdateConsumer(ctx.stream, creator);
            consumerContext = streamCtx.getConsumerContext(consumerName);
            assertEquals(consumerName, consumerContext.getConsumerName());
        }

        // Custom consume options
        FetchConsumeOptions.Builder builder = FetchConsumeOptions.builder().expiresIn(2000);
        if (maxMessages == 0) {
            builder.maxBytes(maxBytes);
        }
        else if (maxBytes == 0) {
            builder.maxMessages(maxMessages);
        }
        else {
            builder.max(maxBytes, maxMessages);
        }
        FetchConsumeOptions fetchConsumeOptions = builder.build();

        long start = System.currentTimeMillis();

        int rcvd = 0;
        long elapsed;
        // create the consumer then use it
        try (FetchMessageConsumer mc = consumerContext.fetch(fetchConsumeOptions)) {
            if (fetchType == FETCH_ORDERED) {
                validateConsumerNameForOrdered(consumerContext, mc, consumerNamePrefix);
            }
            else {
                validateConsumerName(consumerContext, mc, consumerName);
            }
            Message msg = mc.nextMessage();
            while (msg != null) {
                ++rcvd;
                msg.ack();
                msg = mc.nextMessage();
            }
            elapsed = System.currentTimeMillis() - start;
        }

        switch (testType) {
            case "1A":
            case "1B":
            case "2B":
                assertEquals(testAmount, rcvd);
                assertTrue(elapsed < 100);
                break;
            case "1C":
            case "1D":
            case "2C":
                assertTrue(rcvd < testAmount);
                assertTrue(elapsed >= 1500);
                break;
            case "2A":
                assertTrue(rcvd < testAmount);
                assertTrue(elapsed < 100);
                break;
        }
    }

    private String generateConsumerName(int maxMessages, int maxBytes) {
        return maxBytes == 0
            ? random() + "-" + maxMessages + "msgs"
            : random() + "-" + maxBytes + "bytes-" + maxMessages + "msgs";
    }


    @Test
    public void testFetchNoWaitPlusExpires() throws Exception {
        runInShared((nc, ctx) -> {
            ctx.jsm.createOrUpdateConsumer(ctx.stream,
                new PullConsumerCreator()
                    .name(ctx.consumerName())
                    .inactiveThreshold(100000) // I could have used a durable, but this is long enough for the test
                    .subjects(ctx.subject()));

            ConsumerContext cc = ctx.js.getConsumerContext(ctx.stream, ctx.consumerName());
            FetchConsumeOptions fco = FetchConsumeOptions.builder().maxMessages(10).noWait().build();

            // No Wait, No Messages
            FetchMessageConsumer fc = cc.fetch(fco);
            int count = readMessages(fc);
            assertEquals(0, count); // no messages

            // No Wait, One Message
            ctx.js.publish(ctx.subject(), "DATA-A".getBytes());
            fc = cc.fetch(fco);
            count = readMessages(fc);
            assertEquals(1, count); // 1 message

            // No Wait, Two Messages
            ctx.js.publish(ctx.subject(), "DATA-B".getBytes());
            ctx.js.publish(ctx.subject(), "DATA-C".getBytes());
            fc = cc.fetch(fco);
            count = readMessages(fc);
            assertEquals(2, count); // 2 messages

            // With Expires, No Messages
            fco = FetchConsumeOptions.builder().maxMessages(10).noWaitExpiresIn(1000).build();
            fc = cc.fetch(fco);
            count = readMessages(fc);
            assertEquals(0, count); // 0 messages

            // With Expires, One to Three Message
            fco = FetchConsumeOptions.builder().maxMessages(10).noWaitExpiresIn(1000).build();
            fc = cc.fetch(fco);
            ctx.js.publish(ctx.subject(), "DATA-D".getBytes());
            ctx.js.publish(ctx.subject(), "DATA-E".getBytes());
            ctx.js.publish(ctx.subject(), "DATA-F".getBytes());
            count = readMessages(fc);

            // With Long (Default) Expires, Leftovers
            long left = 3 - count;
            fc = cc.fetch(fco);
            count = readMessages(fc);
            assertEquals(left, count);
        });
    }

    private int readMessages(FetchMessageConsumer fc) throws InterruptedException, JetStreamStatusException {
        int count = 0;
        while (!fc.isFinished()) {
            Message m = fc.nextMessage();
            if (m != null) {
                m.ack();
                ++count;
            }
        }
        return count;
    }

    @Test
    public void testIterableConsumer() throws Exception {
        runInShared((nc, ctx) -> {
            // Consumer[Context]
            ConsumerContext consumerContext = ctx.js.createConsumer(ctx.stream,
                new PullConsumerCreator()
                    .durable(ctx.consumerName()));
            validateConsumerName(consumerContext, null, ctx.consumerName());

            int stopCount = 500;
            // create the consumer then use it
            try (IterableMessageConsumer mc = consumerContext.iterate()) {
                validateConsumerName(consumerContext, mc, ctx.consumerName());
                _testIterableBasic(ctx.js, stopCount, mc, ctx.subject());
            }

            // coverage
            IterableMessageConsumer mc = consumerContext.iterate(ConsumeOptions.DEFAULT_CONSUME_OPTIONS);
            validateConsumerName(consumerContext, mc, ctx.consumerName());
            mc.close();
        });
    }

    private ZonedDateTime getStartTimeFirstMessage(JetStreamTestingContext ctx) throws JetStreamException, InterruptedException {
        MessageInfo mi = ctx.jsm.getFirstMessage(ctx.stream, ctx.subject());
        //noinspection DataFlowIssue
        return mi.getTime().plus(30, ChronoUnit.MILLIS);
    }

    @Test
    public void testOrderedConsumerDeliverPolices() throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 101, 3, 100);
            ZonedDateTime startTime = getStartTimeFirstMessage(ctx);

            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            // test a start time
            OrderedConsumerContext occtx = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator()
                    .subjects(ctx.subject())
                    .deliverPolicy(DeliverPolicy.ByStartTime)
                    .startTime(startTime));
            try (IterableMessageConsumer mc = occtx.iterate()) {
                Message m = mc.nextMessage(1000L);
                assertEquals(2, m.metaData().streamSequence());
            }

            // test a start sequence
            occtx = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator()
                    .subjects(ctx.subject())
                    .deliverPolicy(DeliverPolicy.ByStartSequence)
                    .startSequence(2)
            );
            try (IterableMessageConsumer mc = occtx.iterate()) {
                Message m = mc.nextMessage(1000L);
                assertEquals(2, m.metaData().streamSequence());
            }
        });
    }

    @Test
    public void testOrderedIterableConsumerBasic() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            int stopCount = 500;
            PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator().subjects(ctx.subject());
            OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);
            assertNull(occtx.getConsumerName());
            try (IterableMessageConsumer mc = occtx.iterate()) {
                validateConsumerNameForOrdered(occtx, mc, null);
                _testIterableBasic(ctx.js, stopCount, mc, ctx.subject());
            }

            String consumerNamePrefix = random();
            occ = new PullOrderedConsumerCreator().subjects(ctx.subject()).namePrefix(consumerNamePrefix);
            occtx = sctx.createOrderedConsumer(occ);
            assertNull(occtx.getConsumerName());
            try (IterableMessageConsumer mc = occtx.iterate()) {
                validateConsumerNameForOrdered(occtx, mc, consumerNamePrefix);
                _testIterableBasic(ctx.js, stopCount, mc, ctx.subject());
            }
        });
    }

    private void _testIterableBasic(JetStream js, int stopCount, IterableMessageConsumer mc, String subject) throws InterruptedException {
        AtomicInteger count = new AtomicInteger();
        Thread consumeThread = new Thread(() -> {
            try {
                while (count.get() < stopCount) {
                    Message msg = mc.nextMessage(1000L);
                    if (msg != null) {
                        msg.ack();
                        count.incrementAndGet();
                    }
                }

                Thread.sleep(50); // allows more messages to come across
                mc.stop();

                Message msg = mc.nextMessage(1000L);
                while (msg != null) {
                    msg.ack();
                    count.incrementAndGet();
                    msg = mc.nextMessage(1000L);
                }
            }
            catch (Exception e) {
                fail(e);
            }
        });
        consumeThread.start();

        Publisher publisher = new Publisher(js, subject, 25);
        Thread pubThread = new Thread(publisher);
        pubThread.start();

        consumeThread.join();
        publisher.stop();
        pubThread.join();

        assertTrue(count.get() > 500);
    }

    @Test
    public void testConsumeWithHandler() throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 2500);

            // Pre define a consumer
            PullConsumerCreator pcc = new PullConsumerCreator().durable(ctx.consumerName());
            ctx.jsm.createOrUpdateConsumer(ctx.stream, pcc);

            // Consumer[Context]
            ConsumerContext consumerContext = ctx.js.getConsumerContext(ctx.stream, ctx.consumerName());
            validateConsumerName(consumerContext, null, ctx.consumerName());

            int stopCount = 500;

            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger atomicCount = new AtomicInteger();
            MessageHandler handler = msg -> {
                msg.ack();
                if (atomicCount.incrementAndGet() == stopCount) {
                    latch.countDown();
                }
            };

            try (MessageConsumer mcon = consumerContext.consume(handler)) {
                validateConsumerName(consumerContext, mcon, ctx.consumerName());
                latch.await();
                awaitMoreThan(atomicCount, stopCount); // healthy consume over-delivers fast; a genuine stall times out and the assert below still fails
                stopAndWaitForFinished(mcon);
                assertTrue(atomicCount.get() > stopCount);
            }

            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);
            OrderedConsumerContext orderedConsumerContext = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator()
                    .subjects(ctx.subject()));
            assertNull(orderedConsumerContext.getConsumerName());

            CountDownLatch orderedLatch = new CountDownLatch(1);
            atomicCount.set(0);
            handler = msg -> {
                msg.ack();
                if (atomicCount.incrementAndGet() == stopCount) {
                    orderedLatch.countDown();
                }
            };

            try (MessageConsumer mcon = orderedConsumerContext.consume(handler)) {
                validateConsumerNameForOrdered(orderedConsumerContext, mcon, null);
                orderedLatch.await();
                awaitMoreThan(atomicCount, stopCount); // healthy consume over-delivers fast; a genuine stall times out and the assert below still fails
                stopAndWaitForFinished(mcon);
                assertTrue(atomicCount.get() > stopCount);
            }

            String prefix = random();
            OrderedConsumerContext orderedConsumerContextPrefixed = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator()
                    .subjects(ctx.subject()).namePrefix(prefix));
            assertNull(orderedConsumerContextPrefixed.getConsumerName());

            CountDownLatch orderedLatchPrefixed = new CountDownLatch(1);
            atomicCount.set(0);
            handler = msg -> {
                msg.ack();
                if (atomicCount.incrementAndGet() == stopCount) {
                    orderedLatchPrefixed.countDown();
                }
            };

            try (MessageConsumer mcon = orderedConsumerContextPrefixed.consume(handler)) {
                validateConsumerNameForOrdered(orderedConsumerContextPrefixed, mcon, prefix);
                orderedLatchPrefixed.await();
                awaitMoreThan(atomicCount, stopCount); // healthy consume over-delivers fast; a genuine stall times out and the assert below still fails
                stopAndWaitForFinished(mcon);
                assertTrue(atomicCount.get() > stopCount);
            }
        });
    }

    private static void stopAndWaitForFinished(MessageConsumer mcon) throws InterruptedException {
        mcon.stop();
        int fin = 0;
        while (!mcon.isFinished()) {
            //noinspection BusyWait
            Thread.sleep(10);
            if (++fin >= 500) {
                break;
            }
        }
    }

    // Wait (bounded) for the consumer to deliver past 'floor'. This removes the race between the latch
    // (which fires at exactly the floor) and asserting the consumer over-delivered: a healthy endless
    // consume passes this in milliseconds, while a genuine stall at the batch boundary times out here
    // and leaves count == floor so the caller's assertion still fails.
    private static void awaitMoreThan(AtomicInteger count, int floor) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (count.get() <= floor && System.currentTimeMillis() < deadline) {
            //noinspection BusyWait
            Thread.sleep(20);
        }
    }

    @Test
    public void testNext() throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 4);

            String name = random();

            // Pre define a consumer
            PullConsumerCreator pcc = new PullConsumerCreator().durable(name);
            ctx.jsm.createConsumer(ctx.stream, pcc);

            // Consumer[Context]
            ConsumerContext consumerContext = ctx.js.getConsumerContext(ctx.stream, name);
            validateConsumerName(consumerContext, null, name);
            assertThrows(IllegalArgumentException.class, () -> consumerContext.next(1L)); // max wait too small

            assertNotNull(consumerContext.next(1000L));
            assertNotNull(consumerContext.next(1000L));
            assertNotNull(consumerContext.next());
            assertNotNull(consumerContext.next());
            assertNull(consumerContext.next(1000L));

            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            OrderedConsumerContext occtx = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator());
            assertNull(occtx.getConsumerName());
            assertThrows(IllegalArgumentException.class, () -> occtx.next(1L)); // max wait too small

            assertNotNull(occtx.next(1000L));
            String cname1 = validateConsumerNameForOrdered(occtx, null, null);

            assertNotNull(occtx.next(1000L));
            String cname2 = validateConsumerNameForOrdered(occtx, null, null);
            assertNotEquals(cname1, cname2);

            assertNotNull(occtx.next());
            cname1 = validateConsumerNameForOrdered(occtx, null, null);
            assertNotEquals(cname1, cname2);

            assertNotNull(occtx.next());
            cname2 = validateConsumerNameForOrdered(occtx, null, null);
            assertNotEquals(cname1, cname2);

            assertNull(occtx.next(1000L));
            cname1 = validateConsumerNameForOrdered(occtx, null, null);
            assertNotEquals(cname1, cname2);

            String prefix = random();
            OrderedConsumerContext occtxPrefixed = sctx.createOrderedConsumer(
                new PullOrderedConsumerCreator().namePrefix(prefix));
            assertNull(occtxPrefixed.getConsumerName());
            assertThrows(IllegalArgumentException.class, () -> occtxPrefixed.next(1L)); // max wait too small

            assertNotNull(occtxPrefixed.next(1000L));
            cname1 = validateConsumerNameForOrdered(occtxPrefixed, null, prefix);

            assertNotNull(occtxPrefixed.next(1000L));
            cname2 = validateConsumerNameForOrdered(occtxPrefixed, null, prefix);
            assertNotEquals(cname1, cname2);

            assertNotNull(occtxPrefixed.next(1000L));
            cname1 = validateConsumerNameForOrdered(occtxPrefixed, null, prefix);
            assertNotEquals(cname1, cname2);

            assertNotNull(occtxPrefixed.next());
            cname2 = validateConsumerNameForOrdered(occtxPrefixed, null, prefix);
            assertNotEquals(cname1, cname2);

            assertNull(occtxPrefixed.next(1000L));
            cname1 = validateConsumerNameForOrdered(occtxPrefixed, null, prefix);
            assertNotEquals(cname1, cname2);
        });
    }

    @Test
    public void testCoverage() throws Exception {
        runInShared((nc, ctx) -> {
            // Pre define a consumer
            ctx.jsm.createOrUpdateConsumer(ctx.stream, new PullConsumerCreator().durable(ctx.consumerName(1)));
            ctx.jsm.createOrUpdateConsumer(ctx.stream, new PullConsumerCreator().durable(ctx.consumerName(2)));
            ctx.jsm.createOrUpdateConsumer(ctx.stream, new PullConsumerCreator().durable(ctx.consumerName(3)));
            ctx.jsm.createOrUpdateConsumer(ctx.stream, new PullConsumerCreator().durable(ctx.consumerName(4)));

            // Stream[Context]
            StreamContext sctx1 = ctx.js.getStreamContext(ctx.stream);

            // Consumer[Context]
            ConsumerContext cctx1 = ctx.js.getConsumerContext(ctx.stream, ctx.consumerName(1));
            ConsumerContext cctx2 = ctx.js.getConsumerContext(ctx.stream, ctx.consumerName(2));
            ConsumerContext cctx3 = ctx.js.getConsumerContext(ctx.stream, ctx.consumerName(3));
            ConsumerContext cctx4 = sctx1.getConsumerContext(ctx.consumerName(4));
            ConsumerContext cctx5 = sctx1.createOrUpdateConsumer(new PullConsumerCreator().durable(ctx.consumerName(5)));
            ConsumerContext cctx6 = sctx1.createOrUpdateConsumer(new PullConsumerCreator().durable(ctx.consumerName(6)));

            after(cctx1.iterate(), ctx.consumerName(1), true);
            after(cctx2.iterate(ConsumeOptions.DEFAULT_CONSUME_OPTIONS), ctx.consumerName(2), true);
            after(cctx3.consume(m -> {}), ctx.consumerName(3), true);
            after(cctx4.consume(ConsumeOptions.DEFAULT_CONSUME_OPTIONS, m -> {}), ctx.consumerName(4), true);
            after(cctx5.fetchMessages(1), ctx.consumerName(5), false);
            after(cctx6.fetchBytes(1000), ctx.consumerName(6), false);
        });
    }

    private void after(MessageConsumer con, String name, boolean doStop) throws Exception {
        ConsumerInfo ci = con.getConsumerInfo();
        assertEquals(name, ci.getName());
        if (doStop) {
            con.stop();
        }
    }

    @Test
    public void testConsumeOptionsBuilder() {
        ConsumeOptions co = ConsumeOptions.builder().build();
        check_default_values(co);

        co = ConsumeOptions.builder().batchSize(1000).build();
        check_values(co, 1000, 0, DEFAULT_THRESHOLD_PERCENT);

        co = ConsumeOptions.builder().batchSize(1000).thresholdPercent(50).build();
        check_values(co, 1000, 0, 50);

        co = ConsumeOptions.builder().batchBytes(1000).build();
        check_values(co, DEFAULT_MESSAGE_COUNT_WHEN_BYTES, 1000, DEFAULT_THRESHOLD_PERCENT);

        co = ConsumeOptions.builder().thresholdPercent(0).build();
        assertEquals(DEFAULT_THRESHOLD_PERCENT, co.getThresholdPercent());

        co = ConsumeOptions.builder().thresholdPercent(-1).build();
        assertEquals(DEFAULT_THRESHOLD_PERCENT, co.getThresholdPercent());

        co = ConsumeOptions.builder().thresholdPercent(-999).build();
        assertEquals(DEFAULT_THRESHOLD_PERCENT, co.getThresholdPercent());

        co = ConsumeOptions.builder().thresholdPercent(99).build();
        assertEquals(99, co.getThresholdPercent());

        co = ConsumeOptions.builder().thresholdPercent(100).build();
        assertEquals(100, co.getThresholdPercent());

        co = ConsumeOptions.builder().thresholdPercent(101).build();
        assertEquals(100, co.getThresholdPercent());

        co = ConsumeOptions.builder().expiresIn(0).build();
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS, co.getExpiresInMillis());

        co = ConsumeOptions.builder().expiresIn(-1).build();
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS, co.getExpiresInMillis());

        co = ConsumeOptions.builder().expiresIn(-999).build();
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS, co.getExpiresInMillis());

        assertThrows(IllegalArgumentException.class,
            () -> ConsumeOptions.builder().expiresIn(MIN_EXPIRES_MILLS - 1).build());

        co = ConsumeOptions.builder().group("g").minPending(1).minAckPending(2).build();
        assertEquals("g", co.getGroup());
        assertEquals(1, co.getMinPending());
        assertEquals(2, co.getMinAckPending());

        assertEquals("g", co.getGroup());
        assertEquals(1, co.getMinPending());
        assertEquals(2, co.getMinAckPending());
    }

    private void check_default_values(ConsumeOptions co) {
        assertEquals(DEFAULT_MESSAGE_COUNT, co.getBatchSize());
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS, co.getExpiresInMillis());
        assertEquals(DEFAULT_THRESHOLD_PERCENT, co.getThresholdPercent());
        assertEquals(0, co.getBatchBytes());
        assertEquals(DEFAULT_EXPIRES_IN_MILLIS * MAX_IDLE_HEARTBEAT_PERCENT / 100, co.getIdleHeartbeat());
    }

    private void check_values(ConsumeOptions co, int batchSize, int batchBytes, int thresholdPercent) {
        assertEquals(batchSize, co.getBatchSize());
        assertEquals(batchBytes, co.getBatchBytes());
        assertEquals(thresholdPercent, co.getThresholdPercent());
        assertNull(co.getGroup());
        assertEquals(-1, co.getMinPending());
        assertEquals(-1, co.getMinAckPending());
    }

    // this sim is different from the other sim b/c next has a new sub every message
    public static class PullOrderedNextTestDropSimulator extends PullOrderedMessageManager {
        public PullOrderedNextTestDropSimulator(NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf) {
            super(conn, js, subConf);
        }

        // these have to be static or the test keeps repeating
        static boolean ss2 = true;
        static boolean ss5 = true;

        @Override
        protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isJetStream()) {
                long ss = msg.metaData().streamSequence();
                if (ss == 2 && ss2) {
                    ss2 = false;
                    return false;
                }
                if (ss == 5 && ss5) {
                    ss5 = false;
                    return false;
                }
            }

            return super.beforeQueueProcessorImpl(msg);
        }
    }

    @Test
    public void testOrderedBehaviorNext() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);
            ZonedDateTime startTime = getStartTimeFirstMessage(ctx);

            // New pomm factory in place before each subscription is made
            // test with and without a consumer name prefix

            ctx.js._pullOrderedMessageManagerFactory = PullOrderedNextTestDropSimulator::new;
            _testOrderedNext(sctx, 1, new PullOrderedConsumerCreator()
                .subjects(ctx.subject()));
            _testOrderedNext(sctx, 1, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject()));

            ctx.js._pullOrderedMessageManagerFactory = PullOrderedNextTestDropSimulator::new;
            _testOrderedNext(sctx, 2, new PullOrderedConsumerCreator().subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartTime).startTime(startTime));
            _testOrderedNext(sctx, 2, new PullOrderedConsumerCreator().subjects(ctx.subject())
                .namePrefix(random())
                .deliverPolicy(DeliverPolicy.ByStartTime).startTime(startTime));

            ctx.js._pullOrderedMessageManagerFactory = PullOrderedNextTestDropSimulator::new;
            _testOrderedNext(sctx, 2, new PullOrderedConsumerCreator().subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartSequence).startSequence(2));
            _testOrderedNext(sctx, 2, new PullOrderedConsumerCreator().subjects(ctx.subject())
                .namePrefix(random())
                .deliverPolicy(DeliverPolicy.ByStartSequence).startSequence(2));
        });
    }

    private void _testOrderedNext(StreamContext sctx, int expectedStreamSeq, PullOrderedConsumerCreator occ) throws JetStreamException, InterruptedException {
        OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);
        assertNull(occtx.getConsumerName());
        // Loop through the messages to make sure I get stream sequence 1 to 6
        while (expectedStreamSeq <= 6) {
            Message m = occtx.next(1000L);
            if (m != null) {
                if (occ.getNamePrefix() != null) {
                    assertTrue(occtx.getConsumerName().startsWith(occ.getNamePrefix()));
                }
                assertEquals(expectedStreamSeq, m.metaData().streamSequence());
                assertEquals(1, m.metaData().consumerSequence());
                ++expectedStreamSeq;
            }
        }
        validateConsumerNameForOrdered(occtx, null, occ.getNamePrefix());
    }

    public static class PullOrderedTestDropSimulator extends PullOrderedMessageManager {
        long ccForSs3;
        public PullOrderedTestDropSimulator(long ccForSs3,
                                            NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf) {
            super(conn, js, subConf);
            this.ccForSs3 = ccForSs3;
        }

        @Override
        protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isJetStream()
                && msg.metaData().streamSequence() == 3
                && msg.metaData().consumerSequence() == ccForSs3) {
                return false;
            }

            return super.beforeQueueProcessorImpl(msg);
        }
    }

    @Test
    public void testOrderedBehaviorFetch() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);

            // New pomm factory in place before subscriptions are made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(3, conn, js, subConf);

            _testOrderedFetch(sctx, 1, new PullOrderedConsumerCreator()
                .subjects(ctx.subject()));

            _testOrderedFetch(sctx, 1, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject()));
        });
    }

    @Test
    public void testOrderedBehaviorFetchByStartTime() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);
            ZonedDateTime startTime = getStartTimeFirstMessage(ctx);

            // New pomm factory in place before subscriptions are made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(2, conn, js, subConf);

            _testOrderedFetch(sctx, 2, new PullOrderedConsumerCreator()
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(startTime));

            _testOrderedFetch(sctx, 2, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(startTime));
        });
    }

    @Test
    public void testOrderedBehaviorFetchByStartSequence() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);

            // New pomm factory in place before subscriptions are made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(2, conn, js, subConf);

            _testOrderedFetch(sctx, 2, new PullOrderedConsumerCreator()
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(2));

            _testOrderedFetch(sctx, 2, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(2));
        });
    }

    private void _testOrderedFetch(StreamContext sctx, int expectedStreamSeq, PullOrderedConsumerCreator occ) throws Exception {
        OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);
        assertNull(occtx.getConsumerName());
        FetchConsumeOptions fco = FetchConsumeOptions.builder().maxMessages(6).expiresIn(1000).build();
        String prefix = occ.getNamePrefix();
        String firstConsumerName;
        try (FetchMessageConsumer fcon = occtx.fetch(fco)) {
            firstConsumerName = validateConsumerNameForOrdered(occtx, null, prefix);
            Message m = fcon.nextMessage();
            while (m != null) {
                assertEquals(expectedStreamSeq++, m.metaData().streamSequence());
                m = fcon.nextMessage();
            }
            // we know this because the simulator is designed to fail the first time at the third message
            assertEquals(3, expectedStreamSeq);
            // fetch failure will stop the consumer, but make sure it's done b/c with ordered
            // I can't have more than one consuming at a time.
            while (!fcon.isFinished()) {
                sleep(1);
            }
        }
        // this should finish without error
        try (FetchMessageConsumer fcon = occtx.fetch(fco)) {
            validateConsumerNameForOrdered(occtx, null, prefix);
            if (prefix != null) {
                assertNotEquals(firstConsumerName, occtx.getConsumerName());
            }
            Message m = fcon.nextMessage();
            while (expectedStreamSeq <= 6) {
                assertEquals(expectedStreamSeq++, m.metaData().streamSequence());
                m = fcon.nextMessage();
            }
        }
    }

    @Test
    public void testOrderedBehaviorIterable() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);

            // New pomm factory in place before subscription is made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(3, conn, js, subConf);

            _testOrderedIterate(sctx, 1, new PullOrderedConsumerCreator()
                .subjects(ctx.subject()));

            _testOrderedIterate(sctx, 1, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject()));
        });
    }

    @Test
    public void testOrderedBehaviorIterableByStartTime() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);
            ZonedDateTime startTime = getStartTimeFirstMessage(ctx);

            // New pomm factory in place before subscription is made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(2, conn, js, subConf);

            _testOrderedIterate(sctx, 2, new PullOrderedConsumerCreator()
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(startTime));

            _testOrderedIterate(sctx, 2, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartTime)
                .startTime(startTime));

        });
    }

    @Test
    public void testOrderedBehaviorIterableByStartSequence() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            jsPublish(ctx.js, ctx.subject(), 101, 6, 100);

            // New pomm factory in place before subscription is made
            ctx.js._pullOrderedMessageManagerFactory =
                (conn, js, subConf) ->
                    new PullOrderedTestDropSimulator(2, conn, js, subConf);

            _testOrderedIterate(sctx, 2, new PullOrderedConsumerCreator()
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(2));

            _testOrderedIterate(sctx, 2, new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject())
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(2));
        });
    }

    private void _testOrderedIterate(StreamContext sctx, int expectedStreamSeq, PullOrderedConsumerCreator occ) throws Exception {
        OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);
        assertNull(occtx.getConsumerName());
        try (IterableMessageConsumer icon = occtx.iterate()) {
            validateConsumerNameForOrdered(occtx, icon, occ.getNamePrefix());
            // Loop through the messages to make sure I get stream sequence 1 to 5
            while (expectedStreamSeq <= 5) {
                Message m = icon.nextMessage(1000L);
                if (m != null) {
                    assertEquals(expectedStreamSeq++, m.metaData().streamSequence());
                }
            }
        }
    }

    @Test
    public void testOrderedConsume() throws Exception {
        runInShared((nc, ctx) -> {
            PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator()
                .subjects(ctx.subject());
            _testOrderedConsume(ctx, occ);
        });
    }

    @Test
    public void testOrderedConsumeWithPrefix() throws Exception {
        runInShared((nc, ctx) -> {
            PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator()
                .namePrefix(random())
                .subjects(ctx.subject());
            _testOrderedConsume(ctx, occ);
        });
    }

    private void _testOrderedConsume(JetStreamTestingContext ctx, PullOrderedConsumerCreator occ) throws Exception {
        StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

        // Get this in place before subscriptions are made
        ctx.js._pullOrderedMessageManagerFactory =
            (conn, js, subConf) ->
                new PullOrderedTestDropSimulator(3, conn, js, subConf);

        CountDownLatch msgLatch = new CountDownLatch(6);
        AtomicInteger received = new AtomicInteger();
        AtomicLong[] ssFlags = new AtomicLong[6];
        MessageHandler handler = hmsg -> {
            int i = received.incrementAndGet() - 1;
            ssFlags[i] = new AtomicLong(hmsg.metaData().streamSequence());
            msgLatch.countDown();
        };
        OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);
        assertNull(occtx.getConsumerName());
        try (MessageConsumer mcon = occtx.consume(handler)) {
            validateConsumerNameForOrdered(occtx, mcon, occ.getNamePrefix());
            jsPublish(ctx.js, ctx.subject(), 201, 6);

            // wait for the messages
            awaitAndAssert(msgLatch);

            // Loop through the messages to make sure I get stream sequence 1 to 6
            int expectedStreamSeq = 1;
            while (expectedStreamSeq <= 6) {
                int idx = expectedStreamSeq - 1;
                assertEquals(expectedStreamSeq++, ssFlags[idx].get());
            }
        }
    }

    @Test
    public void testOrderedConsumeMultipleSubjects() throws Exception {
        runInSharedCustomContext(VersionUtils::atLeast2_10, (nc, ctx) -> {
            ctx.createOrReplaceStream(2);
            jsPublish(ctx.js, ctx.subject(0), 10);
            jsPublish(ctx.js, ctx.subject(1), 5);

            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator().subjects(ctx.subject(0), ctx.subject(1));
            OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);

            int count0 = 0;
            int count1 = 0;
            try (FetchMessageConsumer fc = occtx.fetch(FetchConsumeOptions.builder().maxMessages(20).expiresIn(2000).build())) {
                Message m = fc.nextMessage();
                while (m != null) {
                    if (m.getSubject().equals(ctx.subject(0))) {
                        count0++;
                    }
                    else {
                        count1++;
                    }
                    m.ack();
                    m = fc.nextMessage();
                }
            }

            assertEquals(10, count0);
            assertEquals(5, count1);
        });
    }

    @Test
    public void testOrderedMultipleWays() throws Exception {
        runInShared((nc, ctx) -> {
            StreamContext sctx = ctx.js.getStreamContext(ctx.stream);

            PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator()
                .subjects(ctx.subject());
            OrderedConsumerContext occtx = sctx.createOrderedConsumer(occ);

            // can't do others while doing next
            CountDownLatch latch1 = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    // make sure there is enough time to call other methods.
                    assertNull(occtx.next(1000L));
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
                finally {
                    latch1.countDown();
                }
            }).start();

            Thread.sleep(100); // make sure there is enough time for the thread to start and get into the next method
            validateCantCallOtherMethods(occtx, true, true);

            //noinspection ResultOfMethodCallIgnored
            latch1.await(3000, TimeUnit.MILLISECONDS);

            // can do others now
            jsPublishNull(ctx.js, ctx.subject(), 1);
            Message m = occtx.next(1000L);
            assertNotNull(m);
            assertEquals(1, m.metaData().streamSequence());

            // can't do others while doing fetch
            int seq = 2;
            try (FetchMessageConsumer fc = occtx.fetchMessages(5)) {
                validateCantCallOtherMethods(occtx, false, true);
                jsPublishNull(ctx.js, ctx.subject(), 5);
                m = fc.nextMessage();
                while (m != null) {
                    assertNotNull(m);
                    assertEquals(seq, m.metaData().streamSequence());
                    assertFalse(fc.isFinished());
                    validateCantCallOtherMethods(occtx, false, true);
                    seq++;
                    m = fc.nextMessage();
                }
                assertNull(fc.nextMessage());
                assertTrue(fc.isFinished());
                assertNull(fc.nextMessage()); // just some coverage for when finished
            }

            // can do others now
            jsPublishNull(ctx.js, ctx.subject(), 1);
            m = occtx.next(1000L);
            assertNotNull(m);
            assertEquals(seq++, m.metaData().streamSequence());

            // can't do others while doing iterate
            ConsumeOptions copts = ConsumeOptions.builder().batchSize(10).expiresIn(3000).build();
            try (IterableMessageConsumer ic = occtx.iterate(copts)) {
                validateCantCallOtherMethods(occtx, true, true);
                jsPublishNull(ctx.js, ctx.subject(), 1);
                m = ic.nextMessage(1000L);
                assertNotNull(m);
                assertEquals(seq++, m.metaData().streamSequence());
                ic.stop();
                while (!ic.isFinished()) {
                    assertNull(ic.nextMessage(100L));
                }
            }

            // can do others now
            jsPublishNull(ctx.js, ctx.subject(), 1);
            m = occtx.next(1000L);
            assertNotNull(m);
            assertEquals(seq++, m.metaData().streamSequence());

            CountDownLatch latch2 = new CountDownLatch(1);
            AtomicLong conSeq = new AtomicLong();
            copts = ConsumeOptions.builder().batchSize(10).expiresIn(1000).build();
            MessageConsumer mc = occtx.consume(copts, cm -> {
                assertNotNull(cm);
                conSeq.set(cm.metaData().streamSequence());
                latch2.countDown();
            });

            validateCantCallOtherMethods(occtx, true, false);

            jsPublishNull(ctx.js, ctx.subject(), 1);
            assertTrue(latch2.await(1000, TimeUnit.MILLISECONDS));
            assertEquals(seq++, conSeq.get());
            mc.stop();
            while (!mc.isFinished()) {
                sleep(100);
            }
            mc.close();

            // can do others now
            jsPublishNull(ctx.js, ctx.subject(), 1);
            m = occtx.next(1000L);
            assertNotNull(m);
            assertEquals(seq, m.metaData().streamSequence());
        });
    }

    @SuppressWarnings("resource")
    private void validateCantCallOtherMethods(OrderedConsumerContext ctx, boolean fetch, boolean consume) {
        assertThrows(IllegalStateException.class, () -> ctx.next(1000L));
        if (fetch) {
            assertThrows(IllegalStateException.class, () -> ctx.fetchMessages(1));
        }
        if (consume) {
            assertThrows(IllegalStateException.class, () -> ctx.consume(m -> {}));
        }
    }

    @Test
    public void testOrderedConsumerBuilder() {
        PullOrderedConsumerCreator occ = new PullOrderedConsumerCreator();
        check_default_values(occ);

        // nulls
        occ = new PullOrderedConsumerCreator()
            .subjects("")
            .deliverPolicy(null)
            .replayPolicy(null);
        check_default_values(occ);

        // you can set headers only to false
        // this tells the underlying consumer configuration that it was specifically set
        occ = new PullOrderedConsumerCreator()
            .subjects("")
            .deliverPolicy(null)
            .replayPolicy(null)
            .headersOnly(false);
        check_default_values(occ);

        // values that set to default
        occ = new PullOrderedConsumerCreator()
            .subjects("")
            .startSequence(-42)
            .headersOnly(false);
        check_default_values(occ);


        // values
        ZonedDateTime zdt = DateTimeUtils.toUtc(ZonedDateTime.now());
        occ = new PullOrderedConsumerCreator()
            .subjects("fs")
            .deliverPolicy(DeliverPolicy.All)
            .startSequence(42)
            .startTime(zdt)
            .replayPolicy(ReplayPolicy.Original)
            .headersOnly(true);
        check_values(occ, zdt);

        // Multiple and COVERAGE
        occ = new PullOrderedConsumerCreator().subjects("fs0", "fs1");
        assertNull(occ.getFilterSubject());
        assertTrue(occ.hasMultipleFilterSubjects());
        assertNotNull(occ.getFilterSubjects());
        assertEquals("fs0", occ.getFilterSubjects().get(0));
        assertEquals("fs1", occ.getFilterSubjects().get(1));
    }

    private void check_default_values(PullOrderedConsumerCreator occ) {
        assertNull(occ.getFilterSubject());
        assertFalse(occ.hasMultipleFilterSubjects());
        assertEquals(ULONG_UNSET, occ.getStartSequence());
        assertNull(occ.getStartTime());
        assertFalse(occ.isHeadersOnly());
    }

    private void check_values(PullOrderedConsumerCreator occ, ZonedDateTime zdt) {
        assertEquals("fs", occ.getFilterSubject());
        assertEquals(DeliverPolicy.All, occ.getDeliverPolicy());
        assertEquals(42, occ.getStartSequence());
        assertEquals(zdt, occ.getStartTime());
        assertEquals(ReplayPolicy.Original, occ.getReplayPolicy());
        assertTrue(occ.isHeadersOnly());
    }

    @Test
    public void testOverflowFetch() throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 100);

            // Testing min ack pending
            String group = random();
            String cname = random();

            PullConsumerCreator pcc = new PullConsumerCreator()
                .name(cname)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .ackWait(10000)
                .subjects(ctx.subject());
            ctx.jsm.createOrUpdateConsumer(ctx.stream, pcc);

            ConsumerContext ctxPrime = ctx.js.getConsumerContext(ctx.stream, cname);
            ConsumerContext ctxOver = ctx.js.getConsumerContext(ctx.stream, cname);

            FetchConsumeOptions fcoNoMin = FetchConsumeOptions.builder()
                .maxMessages(5).expiresIn(1000).group(group)
                .build();

            FetchConsumeOptions fcoOverA = FetchConsumeOptions.builder()
                .maxMessages(5).expiresIn(1000).group(group).minAckPending(5)
                .build();

            FetchConsumeOptions fcoOverB = FetchConsumeOptions.builder()
                .maxMessages(5).expiresIn(1000).group(group).minAckPending(10)
                .build();

            _overflowFetch(cname, ctxPrime, fcoNoMin, true, 5, 0);
            _overflowFetch(cname, ctxOver, fcoNoMin, true, 5, 0);

            _overflowFetch(cname, ctxPrime, fcoNoMin, false, 5, 5);
            _overflowFetch(cname, ctxOver, fcoOverA, true, 5, 5);
            _overflowFetch(cname, ctxOver, fcoOverB, true, 0, 5);
        });
    }

    private void _overflowFetch(String cname, ConsumerContext cctx, FetchConsumeOptions fco, boolean ack, int expected, int ackPendingWhenDone) throws Exception {
        try (FetchMessageConsumer fc = cctx.fetch(fco)) {
            validateConsumerName(cctx, fc, cname);
            int count = 0;
            Message m = fc.nextMessage();
            while (m != null) {
                count++;
                if (ack) {
                    m.ack();
                }
                m = fc.nextMessage();
            }
            assertEquals(expected, count);
            if (ack) {
                sleep(50); // give the server time to process acks given
            }
            assertEquals(ackPendingWhenDone, cctx.retrieveConsumerInfo().getNumAckPending());
        }
    }

    @Test
    public void testOverflowIterate() throws Exception {
        runInShared(VersionUtils::atLeast2_11, (nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 100);

            // Testing min ack pending
            String group = random();
            String cname = random();

            PullConsumerCreator pcc = new PullConsumerCreator()
                .name(cname)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .ackWait(30000)
                .subjects(ctx.subject());
            ctx.jsm.createOrUpdateConsumer(ctx.stream, pcc);

            ConsumerContext ctxPrime = ctx.js.getConsumerContext(ctx.stream, cname);
            ConsumerContext ctxOver = ctx.js.getConsumerContext(ctx.stream, cname);
            validateConsumerName(ctxPrime, null, cname);
            validateConsumerName(ctxOver, null, cname);

            ConsumeOptions coPrime = ConsumeOptions.builder()
                .group(group)
                .build();

            ConsumeOptions coOver = ConsumeOptions.builder()
                .group(group)
                .minAckPending(101)
                .build();

            // start the overflow consumer
            AtomicLong primeCount = new AtomicLong();
            AtomicLong overCount = new AtomicLong();
            AtomicLong left = new AtomicLong(100);

            Thread tOver = new Thread(() -> {
                try {
                    IterableMessageConsumer ic = ctxOver.iterate(coOver);
                    validateConsumerName(ctxOver, ic, cname);
                    while (left.get() > 0 && !Thread.currentThread().isInterrupted()) {
                        Message m = ic.nextMessage(100L);
                        if (m != null) {
                            m.ack();
                            overCount.incrementAndGet();
                            left.decrementAndGet();
                        }
                    }
                }
                catch (InterruptedException ignore) {
                }
                catch (Exception e) {
                    fail(e);
                }
            });
            tOver.start();

            Thread tPrime = new Thread(() -> {
                try {
                    IterableMessageConsumer ic = ctxPrime.iterate(coPrime);
                    validateConsumerName(ctxPrime, ic, cname);
                    while (left.get() > 0 && !Thread.currentThread().isInterrupted()) {
                        Message m = ic.nextMessage(100L);
                        if (m != null) {
                            m.ack();
                            primeCount.incrementAndGet();
                            left.decrementAndGet();
                        }
                    }
                }
                catch (InterruptedException ignore) {
                }
                catch (Exception e) {
                    fail(e);
                }
            });
            tPrime.start();

            tPrime.join();
            tOver.join();
            assertEquals(100, primeCount.get());
            assertEquals(0, overCount.get());
        });
    }

    @Test
    public void testOverflowConsume() throws Exception {
        runInShared(VersionUtils::atLeast2_11, (nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 1000);

            // Testing min ack pending
            String group = random();
            String cname = random();

            PullConsumerCreator pcc = new PullConsumerCreator()
                .name(cname)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .ackWait(30000)
                .subjects(ctx.subject());
            ctx.jsm.createOrUpdateConsumer(ctx.stream, pcc);

            ConsumerContext ctxPrime = ctx.js.getConsumerContext(ctx.stream, cname);
            ConsumerContext ctxOver = ctx.js.getConsumerContext(ctx.stream, cname);
            validateConsumerName(ctxPrime, null, cname);
            validateConsumerName(ctxOver, null, cname);

            ConsumeOptions coPrime = ConsumeOptions.builder()
                .group(group)
                .build();

            ConsumeOptions coOver = ConsumeOptions.builder()
                .group(group)
                .minAckPending(1001)
                .build();

            // start the overflow consumer
            AtomicLong primeCount = new AtomicLong();
            AtomicLong overCount = new AtomicLong();
            AtomicLong left = new AtomicLong(500);

            MessageHandler overHandler = m -> {
                m.ack();
                overCount.incrementAndGet();
                left.decrementAndGet();
            };

            MessageHandler primeHandler = m -> {
                m.ack();
                primeCount.incrementAndGet();
                left.decrementAndGet();
            };

            try (MessageConsumer mcOver = ctxOver.consume(coOver, overHandler);
                 MessageConsumer mcPrime = ctxPrime.consume(coPrime, primeHandler)) {
                validateConsumerName(ctxPrime, mcPrime, cname);
                validateConsumerName(ctxOver, mcOver, cname);
                while (left.get() > 0) {
                    sleep(100);
                }
                mcOver.stop();
                mcPrime.stop();
            }

            assertTrue(primeCount.get() > 0);
            assertEquals(0, overCount.get());
        });
    }

    @Test
    public void testFinishEmptyStream() throws Exception {
        runInShared((nc, ctx) -> {
            String name = random();
            PullConsumerCreator pcc = new PullConsumerCreator()
                .name(name)
                .subjects(ctx.subject());
            ctx.jsm.createOrUpdateConsumer(ctx.stream, pcc);

            ConsumerContext cctx = ctx.js.getConsumerContext(ctx.stream, name);

            MessageHandler handler = Message::ack;

            ConsumeOptions co = ConsumeOptions.builder().expiresIn(1000).build();
            try (MessageConsumer mc = cctx.consume(co, handler)) {
                mc.stop();
                sleep(1200); // more than the expires period for the consume
                assertTrue(mc.isFinished());
            }
        });
    }

    @Test
    public void testReconnectOverOrdered() throws Exception {
        // ------------------------------------------------------------
        // The idea here is...
        // 1. connect with an ordered consumer and start consuming
        // 2. stop the server then restart it causing a disconnect,
        //    but reconnect before the idle heartbeat alarm kicks in
        // 3. stop the server but wait a little before restarting
        //    so the alarm goes off but still disconnected
        //    to make sure the consumer continues after that condition
        // ------------------------------------------------------------
        String stream = random();
        String subject = random();

        AtomicBoolean allInOrder = new AtomicBoolean(true);
        AtomicInteger messageCount = new AtomicInteger();
        AtomicLong nextExpectedSequence = new AtomicLong(0);

        MessageHandler handler = msg -> {
            if (msg.metaData().streamSequence() != nextExpectedSequence.incrementAndGet()) {
                allInOrder.set(false);
            }
            msg.ack();
            messageCount.incrementAndGet();
            sleep(50); // simulate some work and to slow the endless consume
        };

        StreamCreator sc = new StreamCreator(stream)
            .storageType(StorageType.File) // file since we are killing the server and bringing it back up.
            .subjects(subject);

        NatsTestServer ts = new NatsTestServer(NatsTestServer.builder().jetstream());
        /* start server */
        Listener listener = new Listener();
        Options options = optionsBuilder(ts)
            .connectionListener(listener)
            .errorListener(listener)
            .build();
        NatsConnection nc = ConnectionUtils.managedConnect(options);
        JetStreamManagement jsm = new JetStreamManagement(nc);
        JetStream js = jsm.jetStream();
        jsm.addStream(sc);

        for (int x = 0; x < 2000; x++) {
            js.publish(subject);
        }

        ConsumeOptions consumeOptions = ConsumeOptions.builder()
            .batchSize(100) // small batch size means more round trips
            .expiresIn(1000) // idle heartbeat is half of this, alarm time is 3 times
            .build();

        PullOrderedConsumerCreator ocConfig = new PullOrderedConsumerCreator().subjects(subject);
        StreamContext streamContext = js.getStreamContext(stream);
        OrderedConsumerContext orderedConsumerContext = streamContext.createOrderedConsumer(ocConfig);
        assertNull(orderedConsumerContext.getConsumerName());
        MessageConsumer mcon = orderedConsumerContext.consume(consumeOptions, handler);
        validateConsumerNameForOrdered(orderedConsumerContext, mcon, null);
        sleep(500); // time enough to get some messages

        /* close server */ ts.close();

        validateOverOrdered(messageCount, allInOrder);

        // reconnect and get some more messages
        messageCount.set(0);
        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        /* start server */ ts.start();
        listener.validate(); // reconnected
        listener.queueHeartbeat();
        sleep(3500); // long enough to get messages and for the hb alarm to have tripped
        /* close server */ ts.close();

        listener.validate(); // heartbeat
        validateOverOrdered(messageCount, allInOrder);

        // wait enough time to get more heartbeats, then reconnect and get some more messages
        listener.queueHeartbeat();
        sleep(3500);
        listener.validate(); // heartbeat

        messageCount.set(0);
        listener.queueConnectionEvent(ConnectionEvents.RECONNECTED);
        /* start server */ ts.start();
        listener.validate(); // reconnected
        listener.queueHeartbeat();
        sleep(3500); // long enough to get messages and for the hb alarm to have tripped
        /* close server */ ts.close();
        listener.validate(); // heartbeat
        validateOverOrdered(messageCount, allInOrder);
    }

    private static void validateOverOrdered(AtomicInteger atomicCount, AtomicBoolean allInOrder) {
        int count = atomicCount.get();
        assertTrue(allInOrder.get());
        assertTrue(count > 0);
    }

    @Test
    public void testResetSurvivesUncheckedSubscribeFailure() throws Exception {
        // The ordered-consumer reset path is shutdownSub() then doSub(false). A connection or dispatcher
        // that is closing or draining throws IllegalStateException out of the subscribe, and doSub used to
        // catch only the checked JetStreamException. The unchecked one escaped after shutdownSub() had
        // already run, so the consumer was left with no subscription and no heartbeat timer while
        // isStopped() and isFinished() both still reported false - a silent, permanent stall.
        runInShared((nc, ctx) -> {
            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            NatsConsumerContext consumerContext = (NatsConsumerContext)
                streamContext.createOrUpdateConsumer(new PullConsumerCreator().durable(random()));

            // Let the first subscribe through so the consumer constructs, then fail every later one the
            // way a closing connection does.
            AtomicInteger subscribes = new AtomicInteger();
            SimplifiedSubscriptionMaker failsAfterFirst = (mh, d, pmm, threshold) -> {
                if (subscribes.incrementAndGet() > 1) {
                    throw new IllegalStateException("simulated: NatsConnection is Closed");
                }
                return consumerContext.subscribe(mh, d, pmm, threshold);
            };

            NatsMessageConsumer consumer = new NatsMessageConsumer(
                failsAfterFirst, consumerContext.retrieveConsumerInfo(),
                ConsumeOptions.DEFAULT_CONSUME_OPTIONS, null, msg -> {});
            try {
                assertDoesNotThrow(consumer::pullTerminatedByError,
                    "an unchecked subscribe failure on the reset path must be recovered, not thrown");
                assertEquals(2, subscribes.get(), "the reset must have attempted the re-subscribe");

                // Recovered rather than dead: the heartbeat was re-armed, so the reset will be retried.
                assertFalse(consumer.isStopped(), "the consumer must not report itself stopped");
                assertFalse(consumer.isFinished(), "the consumer must not report itself finished");
            }
            finally {
                consumer.stop();
            }
        });
    }


    @Test
    public void testSubscriptionIsCleanedUpWhenDoSubFailsAfterSubscribing() throws Exception {
        // doSub has two statements after the subscribe. If either throws, the attempt is abandoned while
        // holding a live subscription. On the first == true path the consumer never reaches the caller,
        // so without the cleanup both the subscription and its heartbeat timer are unreachable and leak.
        runInShared((nc, ctx) -> {
            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            NatsConsumerContext consumerContext = (NatsConsumerContext)
                streamContext.createOrUpdateConsumer(new PullConsumerCreator().durable(random()));

            // Hold on to whatever subscription the subscribe hands back, so it can be inspected after
            // the constructor fails and the consumer itself is unreachable.
            AtomicReference<JetStreamPullSubscription> made = new AtomicReference<>();
            SimplifiedSubscriptionMaker capturing = (mh, d, pmm, threshold) -> {
                JetStreamPullSubscription sub = consumerContext.subscribe(mh, d, pmm, threshold);
                made.set(sub);
                return sub;
            };

            ConsumerInfo ci = consumerContext.retrieveConsumerInfo();
            assertThrows(IllegalStateException.class,
                () -> new RePullFailsConsumer(capturing, ci, ConsumeOptions.DEFAULT_CONSUME_OPTIONS),
                "the failure must still reach the caller");

            assertNotNull(made.get(), "the subscribe must have succeeded before the failure");
            assertFalse(made.get().isActive(), "the abandoned subscription must have been cleaned up");
        });
    }

    /** Subscribes normally, then fails in rePull - the last statement of doSub. */
    static class RePullFailsConsumer extends NatsMessageConsumer {
        RePullFailsConsumer(SimplifiedSubscriptionMaker maker, ConsumerInfo ci, ConsumeOptions opts)
            throws JetStreamException, InterruptedException {
            super(maker, ci, opts, null, msg -> {});
        }

        @Override
        protected void rePull() {
            throw new IllegalStateException("simulated: NatsConnection is Closed");
        }
    }


    @Test
    public void testConsumerIsDeletedWhenTheSubscribeFails() throws Exception {
        // _createJsSubscription creates nothing itself, but everything in it can throw IllegalStateException
        // when the connection or the dispatcher is closing. By then the consumer already exists, and it
        // exists only to back this subscription, so a failed subscribe would orphan it on the server.
        // A closed dispatcher is the case worth covering: it fails the subscribe while leaving the
        // connection healthy, so the cleanup delete can actually be sent and its effect observed.
        runInShared((nc, ctx) -> {
            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            String durable = random();

            NatsDispatcher dispatcher = (NatsDispatcher) nc.createDispatcher();
            nc.closeDispatcher(dispatcher);

            SubscribeBehavior behavior = new SubscribeBehavior().handler(msg -> {}).dispatcher(dispatcher);
            assertThrows(IllegalStateException.class,
                () -> ctx.js.pullSubscribe(ctx.stream, new PullConsumerCreator().durable(durable), behavior),
                "the subscribe must still fail - the cleanup must not swallow it");

            assertFalse(streamContext.getConsumerNames().contains(durable),
                "a consumer created for a subscribe that failed must be deleted, not orphaned");
        });
    }

}
