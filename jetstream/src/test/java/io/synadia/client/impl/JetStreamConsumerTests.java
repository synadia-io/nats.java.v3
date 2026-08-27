package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.api.*;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.ListenerStatusType;
import io.synadia.client.utils.VersionUtils;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamConsumerTests extends JetStreamTestBase {

    // ------------------------------------------------------------------------------------------
    // This allows me to intercept messages before it gets to the connection queue,
    // which is before the messages are available for "nextMessage",
    // or before it gets dispatched to a handler.
    static class PushOrderedTestDropSimulator extends PushOrderedMessageManager {
        public PushOrderedTestDropSimulator(NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf) {
            super(conn, js, subConf);
        }

        @Override
        public Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isJetStream()) {
                long ss = msg.metaData().streamSequence();
                long cs = msg.metaData().consumerSequence();
                if ((ss == 2 && cs == 2) || (ss == 5 && cs == 4)) {
                    return false;
                }
            }

            return super.beforeQueueProcessorImpl(msg);
        }
    }

    // Expected consumer sequence numbers
    static long[] EXPECTED_CON_SEQ_NUMS = new long[] {1, 1, 2, 3, 1, 2};

    @Test
    public void testOrderedConsumerSync() throws Exception {
        runInShared((nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 101, 6);

            // Get this in place before any subscriptions are made
            ctx.js._pushOrderedMessageManagerFactory = PushOrderedTestDropSimulator::new;

            // without name (prefix)
            _testOrderedConsumerSync(ctx, null, new PushOrderedConsumerCreator());

            // with name (prefix)
            _testOrderedConsumerSync(ctx, ctx.consumerName(), new PushOrderedConsumerCreator().namePrefix(ctx.consumerName()));
        });
    }

    private static void _testOrderedConsumerSync(JetStreamTestingContext ctx, String consumerNamePrefix, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        JetStreamSubscription sub = ctx.js.pushSubscribe(ctx.stream, creator);
        String firstConsumerName = validateOrderedConsumerNamePrefix(sub, consumerNamePrefix);

        // Messages will be intercepted by the PushOrderedTestDropSimulator

        // Loop through the messages to make sure I get stream sequence 1 to 6
        int expectedStreamSeq = 1;
        while (expectedStreamSeq <= 6) {
            Message m = sub.nextMessage(1000L); // use the duration version here for coverage
            if (m != null) {
                assertEquals(expectedStreamSeq, m.metaData().streamSequence());
                assertEquals(EXPECTED_CON_SEQ_NUMS[expectedStreamSeq-1], m.metaData().consumerSequence());
                ++expectedStreamSeq;
            }
        }
        reValidateOrderedConsumerNamePrefix(sub, consumerNamePrefix, firstConsumerName);
    }

    private static String validateOrderedConsumerNamePrefix(JetStreamSubscription sub, String consumerNamePrefix) throws JetStreamException, InterruptedException {
        String firstConsumerName = sub.getConsumerName();
        if (consumerNamePrefix != null) {
            assertEquals(firstConsumerName, sub.getConsumerInfo().getName());
            assertNotEquals(consumerNamePrefix, firstConsumerName);
            assertTrue(firstConsumerName.startsWith(consumerNamePrefix));
        }
        return firstConsumerName;
    }

    private static void reValidateOrderedConsumerNamePrefix(JetStreamSubscription sub, String consumerNamePrefix, String firstConsumerName) throws JetStreamException, InterruptedException {
        if (consumerNamePrefix != null) {
            String currentConsumerName = sub.getConsumerName();
            assertEquals(currentConsumerName, sub.getConsumerInfo().getName());
            assertNotEquals(firstConsumerName, currentConsumerName);
            assertTrue(currentConsumerName.startsWith(consumerNamePrefix));
        }
    }

    @Test
    public void testOrderedConsumerAsyncNoName() throws Exception {
        runInShared((nc, ctx) -> {
            // without name (prefix)
            _testOrderedConsumerAsync(nc, ctx, null, new PushOrderedConsumerCreator());

            // with name (prefix)
            _testOrderedConsumerAsync(nc, ctx, ctx.consumerName(), new PushOrderedConsumerCreator().namePrefix(ctx.consumerName()));
        });
    }

    private static void _testOrderedConsumerAsync(NatsConnection nc, JetStreamTestingContext ctx, String consumerNamePrefix, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException {
        // Get this in place before any subscriptions are made
        ctx.js._pushOrderedMessageManagerFactory = PushOrderedTestDropSimulator::new;

        // We'll need a dispatcher
        Dispatcher d = nc.createDispatcher();

        // Set up an async subscription
        CountDownLatch msgLatch = new CountDownLatch(6);
        AtomicInteger received = new AtomicInteger();
        AtomicLong[] ssFlags = new AtomicLong[6];
        AtomicLong[] csFlags = new AtomicLong[6];
        MessageHandler handler = hmsg -> {
            int i = received.incrementAndGet() - 1;
            ssFlags[i] = new AtomicLong(hmsg.metaData().streamSequence());
            csFlags[i] = new AtomicLong(hmsg.metaData().consumerSequence());
            msgLatch.countDown();
        };

        JetStreamSubscription sub = ctx.js.pushSubscribe(ctx.stream, creator, handler);
        String firstConsumerName = validateOrderedConsumerNamePrefix(sub, consumerNamePrefix);

        // publish after sub b/c interceptor is set during sub, so before messages come in
        jsPublish(ctx.js, ctx.subject(), 201, 6);

        // wait for the messages
        awaitAndAssert(msgLatch);

        // Loop through the messages to make sure I get stream sequence 1 to 6
        int expectedStreamSeq = 1;
        while (expectedStreamSeq <= 6) {
            int idx = expectedStreamSeq - 1;
            assertEquals(expectedStreamSeq, ssFlags[idx].get());
            assertEquals(EXPECTED_CON_SEQ_NUMS[idx], csFlags[idx].get());
            ++expectedStreamSeq;
        }

        reValidateOrderedConsumerNamePrefix(sub, consumerNamePrefix, firstConsumerName);
    }

    static class SimulatorState {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final AtomicInteger hbCounter = new AtomicInteger();
    }

    static class HeartbeatErrorSimulator extends PushMessageManager {
        final SimulatorState state;

        public HeartbeatErrorSimulator(NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf, SimulatorState state) {
            super(conn, js, subConf);
            this.state = state;
        }

        @Override
        protected void handleHeartbeatError() {
            super.handleHeartbeatError();
            state.latch.countDown();
        }

        @Override
        public Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isStatusMessage() && msg.getStatus().isHeartbeat()) {
                state.hbCounter.incrementAndGet();
            }
            return false;
        }
    }

    static class PushOrderedHeartbeatErrorSimulator extends PushOrderedMessageManager {
        final SimulatorState state;

        public PushOrderedHeartbeatErrorSimulator(NatsConnection conn, JetStream js, JetStreamSubscribeConfig subConf, SimulatorState state) {
            super(conn, js, subConf);
            this.state = state;
        }

        @Override
        protected void handleHeartbeatError() {
            super.handleHeartbeatError();
            state.latch.countDown();
        }

        @Override
        public Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isStatusMessage() && msg.getStatus().isHeartbeat()) {
                state.hbCounter.incrementAndGet();
            }
            return false;
        }
    }

    static class PullHeartbeatErrorSimulator extends PullMessageManager {
        public final SimulatorState state;

        public PullHeartbeatErrorSimulator(NatsConnection conn, JetStreamSubscribeConfig subConf, SimulatorState state) {
            super(conn, subConf);
            this.state = state;
        }

        @Override
        protected void handleHeartbeatError() {
            super.handleHeartbeatError();
            state.latch.countDown();
        }

        @Override
        protected Boolean beforeQueueProcessorImpl(NatsMessage msg) {
            if (msg.isStatusMessage() && msg.getStatus().isHeartbeat()) {
                state.hbCounter.incrementAndGet();
            }
            return false;
        }
    }

    @Test
    public void testHeartbeatError() throws Exception {
        Listener listener = new Listener();
        OptionsBuilder builder = new OptionsBuilder().errorListener(listener);
        runInShared(builder, (nc, ctx) -> {
            SimulatorState state = setupPushFactory(ctx.js);
            PushConsumerCreator creator = new PushConsumerCreator().subjects(ctx.subject()).idleHeartbeat(100);
            JetStreamPushSubscription pushSub = ctx.js.pushSubscribe(ctx.stream, creator);
            validate(pushSub, listener, state);

            state = setupPushFactory(ctx.js);
            pushSub = ctx.js.pushSubscribe(ctx.stream, creator, m -> {});
            validate(pushSub, listener, state);

            state = setupPushOrderedFactory(ctx.js);
            PushOrderedConsumerCreator ordered =
                new PushOrderedConsumerCreator().subjects(ctx.subject()).idleHeartbeat(100);
            pushSub = ctx.js.pushSubscribe(ctx.stream, ordered);
            validate(pushSub, listener, state);

            state = setupPushOrderedFactory(ctx.js);
            ordered =
                new PushOrderedConsumerCreator().subjects(ctx.subject()).idleHeartbeat(100);
            pushSub = ctx.js.pushSubscribe(ctx.stream, ordered, m -> {});
            validate(pushSub, listener, state);

            state = setupPullFactory(ctx.js);
            PullConsumerCreator pull = new PullConsumerCreator().subjects(ctx.subject());
            JetStreamPullSubscription pullSub = ctx.js.pullSubscribe(ctx.stream, pull);
            pullSub.pull(PullRequestOptions.builder(1).idleHeartbeat(100).expiresIn(2000).build());
            validate(pullSub, listener, state);

            state = setupPullOrderedFactory(ctx.js);
            PullOrderedConsumerCreator pullOrdered = new PullOrderedConsumerCreator().subjects(ctx.subject());
            pullSub = ctx.js.pullSubscribe(ctx.stream, pullOrdered);
            pullSub.pull(PullRequestOptions.builder(1).idleHeartbeat(100).expiresIn(2000).build());
            validate(pullSub, listener, state);
        });
    }

    private static void validate(JetStreamSubscription sub, Listener listener, SimulatorState state) throws InterruptedException {
        listener.reset();
        assertTrue(state.latch.await(2, TimeUnit.SECONDS));
        sub.unsubscribe();
        assertEquals(0, state.latch.getCount());
        assertTrue(state.hbCounter.get() > 0);
        boolean gotHbAlarm = false;
        for (int x = 0; x < 50; x++) {
            gotHbAlarm = listener.getHeartbeatAlarmCount() > 0;
            if (gotHbAlarm) {
                break;
            }
            sleep(10);
        }
        assertTrue(gotHbAlarm);
    }

    private static SimulatorState setupPushFactory(JetStream js) {
        SimulatorState state = new SimulatorState();
        js._pushMessageManagerFactory =
            (conn, lJs, subConf) ->
                new HeartbeatErrorSimulator(conn, lJs, subConf, state);
        return state;
    }

    private static SimulatorState setupPushOrderedFactory(JetStream js) {
        SimulatorState state = new SimulatorState();
        js._pushOrderedMessageManagerFactory =
            (conn, lJs, subConf) ->
                new PushOrderedHeartbeatErrorSimulator(conn, lJs, subConf, state);
        return state;
    }

    private static SimulatorState setupPullFactory(JetStream js) {
        SimulatorState state = new SimulatorState();
        js._pullMessageManagerFactory =
            (conn, lJs, subConf) ->
                new PullHeartbeatErrorSimulator(conn, subConf, state);
        return state;
    }

    private static SimulatorState setupPullOrderedFactory(JetStream js) {
        SimulatorState state = new SimulatorState();
        js._pullOrderedMessageManagerFactory =
            (conn, lJs, subConf) ->
                new PullHeartbeatErrorSimulator(conn, subConf, state);
        return state;
    }

    @Test
    public void testMultipleSubjectFilters() throws Exception {
        runInSharedCustomContext(VersionUtils::atLeast2_10, (nc, ctx) -> {
            ctx.createOrReplaceStream(2);
            jsPublish(ctx.js, ctx.subject(0), 10);
            jsPublish(ctx.js, ctx.subject(1), 5);

            // push ephemeral
            PushConsumerCreator pushCreator = new PushConsumerCreator().subjects(ctx.subject(0), ctx.subject(1));
            JetStreamPushSubscription pushSub = ctx.js.pushSubscribe(ctx.stream, pushCreator);
            validateMultipleSubjectFilterSub(pushSub, ctx.subject(0));

            // pull ephemeral
            PullConsumerCreator pullCreator = new PullConsumerCreator().subjects(ctx.subject(0), ctx.subject(1));
            JetStreamPullSubscription pullSub = ctx.js.pullSubscribe(ctx.stream, pullCreator);
            pullSub.pullExpiresIn(15, 1000);
            validateMultipleSubjectFilterSub(pullSub, ctx.subject(0));

            // push named
            String name = random();
            pushCreator = new PushConsumerCreator().subjects(ctx.subject(0), ctx.subject(1)).name(name).deliverSubject(random());
            pushSub = ctx.js.pushSubscribe(ctx.stream, pushCreator);
            assertEquals(name, pushSub.getConsumerInfo().getName());
            validateMultipleSubjectFilterSub(pushSub, ctx.subject(0));

            // pull named
            name = random();
            pullCreator = new PullConsumerCreator().subjects(ctx.subject(0), ctx.subject(1)).name(name);
            pullSub = ctx.js.pullSubscribe(ctx.stream, pullCreator);
            assertEquals(name, pullSub.getConsumerInfo().getName());
            pullSub.pullExpiresIn(15, 1000);
            validateMultipleSubjectFilterSub(pullSub, ctx.subject(0));
        });
    }

    private static void validateMultipleSubjectFilterSub(JetStreamSubscription sub, String subject0) throws InterruptedException {
        int count1 = 0;
        int count2 = 0;
        Message m = sub.nextMessage(1000);
        while (m != null) {
            if (m.getSubject().equals(subject0)) {
                count1++;
            }
            else {
                count2++;
            }
            m = sub.nextMessage(1000);
        }

        assertEquals(10, count1);
        assertEquals(5, count2);
    }

    @Test
    public void testRaiseStatusWarnings1194() throws Exception {
        Listener listener = new Listener();
        OptionsBuilder builder = new OptionsBuilder().errorListener(listener);
        runInShared(builder, (nc, ctx) -> {
            // Setup
            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);

            // Setting maxBatch=1, so we shouldn't allow fetching more messages at once.
            PullConsumerCreator pullCreator = new PullConsumerCreator().subjects(ctx.subject()).maxBatch(1);
            ConsumerContext consumerContext = streamContext.createOrUpdateConsumer(pullCreator);

            int count = 0;

            // Fetching a batch of 100 messages is not allowed, so we rightfully don't get any messages and wait for timeout.
            // But we don't get informed about the status message.
            FetchConsumeOptions fco = FetchConsumeOptions.builder()
                .maxMessages(100)
                .expiresIn(1000)
                .build();
            try (FetchMessageConsumer fetchConsumer = consumerContext.fetch(fco)) {
                Message msg;
                while ((msg = fetchConsumer.nextMessage()) != null) {
                    msg.ack();
                    count++;
                }
            }
            assertEquals(0, count);
            assertEquals(0, listener.getPullStatusWarningsCount());

            listener.queueStatus(ListenerStatusType.PullWarning, 409);

            fco = FetchConsumeOptions.builder()
                .maxMessages(100)
                .expiresIn(1000)
                .raiseStatusWarnings()
                .build();
            try (FetchMessageConsumer fetchConsumer = consumerContext.fetch(fco)) {
                Message msg;
                while ((msg = fetchConsumer.nextMessage()) != null) {
                    msg.ack();
                    count++;
                }
            }
            assertEquals(0, count);
            assertEquals(1, listener.getPullStatusWarningsCount());
            listener.validate();
        });
    }
}
