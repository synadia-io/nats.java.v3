package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsConstants;
import io.synadia.client.api.*;
import io.synadia.client.utils.Listener;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamPushTests extends JetStreamTestBase {

    @Test
    public void testPushEphemeralNullDeliver() throws Exception {
        _testPushEphemeral(null);
    }

    @Test
    public void testPushEphemeralWithDeliver() throws Exception {
        _testPushEphemeral(random());
    }

    private void _testPushEphemeral(String deliverSubject) throws Exception {
        runInShared((nc, ctx) -> {
            // publish some messages
            jsPublish(ctx.js, ctx.subject(), 1, 5);

            // Subscription 1
            JetStreamPushSubscription sub1 = deliverSubject == null
                ? ctx.js.pushSubscribe(ctx.subject())
                : ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).deliverSubject(deliverSubject));
            assertSubscription(sub1, ctx.stream, null, deliverSubject, false);
            nc.flush(1000); // flush outgoing communication with/to the server

            // read what is available
            List<Message> messages1 = readMessagesAck(sub1);
            int total = messages1.size();
            validateRedAndTotal(5, messages1.size(), 5, total);

            // read again, nothing should be there
            List<Message> messages0 = readMessagesAck(sub1);
            total += messages0.size();
            validateRedAndTotal(0, messages0.size(), 5, total);

            // needed for deliver subject version b/c the sub
            // would be identical. without ds, the ds is generated each
            // time so is unique
            unsubscribeEnsureNotBound(sub1);

            // Subscription 2
            JetStreamPushSubscription sub2 = deliverSubject == null
                ? ctx.js.pushSubscribe(ctx.subject())
                : ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).deliverSubject(deliverSubject));
            nc.flush(1000); // flush outgoing communication with/to the server

            // read what is available, same messages
            List<Message> messages2 = readMessagesAck(sub2);
            total = messages2.size();
            validateRedAndTotal(5, messages2.size(), 5, total);

            // read again, nothing should be there
            messages0 = readMessagesAck(sub2);
            total += messages0.size();
            validateRedAndTotal(0, messages0.size(), 5, total);

            assertSameMessages(messages1, messages2);

            unsubscribeEnsureNotBound(sub2);

            // Subscription 3 testing null timeout
            JetStreamPushSubscription sub3 = deliverSubject == null
                ? ctx.js.pushSubscribe(ctx.subject())
                : ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).deliverSubject(deliverSubject));
            nc.flush(1000); // flush outgoing communication with/to the server
            sleep(1000); // give time to make sure the messages get to the client

            messages0 = readMessagesAck(sub3, null);
            validateRedAndTotal(5, messages0.size(), 5, 5);

            unsubscribeEnsureNotBound(sub3);

            // Subscription 4 testing timeout <= 0 duration / millis
            JetStreamPushSubscription sub4 = deliverSubject == null
                ? ctx.js.pushSubscribe(ctx.subject())
                : ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).deliverSubject(deliverSubject));
            nc.flush(1000); // flush outgoing communication with/to the server
            sleep(1000); // give time to make sure the messages get to the client

            Message m = sub4.nextMessageWaitForever();
            assertNotNull(m);
            m.ack();
            m = sub4.nextMessageNoWait();
            assertNotNull(m);
            m.ack();

            // get the rest
            messages0 = readMessagesAck(sub4, null);
            validateRedAndTotal(3, messages0.size(), 3, 3);
        });
    }

    @Test
    public void testPushDurableNullDeliver() throws Exception {
        _testPushDurable(false);
    }

    @Test
    public void testPushDurableWithDeliver() throws Exception {
        _testPushDurable(true);
    }

    private void _testPushDurable(boolean useDeliverSubject) throws Exception {
        runInSharedCustom((nc, ctx) -> {
            String subjectDotGt = random() + ".>";
            ctx.createOrReplaceStream(subjectDotGt);

            String stream = ctx.stream;

            // For async, create a dispatcher without a default handler.
            NatsDispatcher dispatcher = nc.createDispatcher();

            _testPushDurableSubSync(ctx, stream, subjectDotGt, useDeliverSubject);
            _testPushDurableSubAsync(ctx, dispatcher, stream, subjectDotGt, useDeliverSubject);
        });
    }

    private void _testPushDurableSubSync(JetStreamTestingContext ctx, String stream, String subjectDotGt, boolean useDeliverSubject) throws Exception {
        String subject = subjectDotGt.replace(">", random());

        // publish some messages
        jsPublish(ctx.js, subject, 1, 5);

        String durable = random();
        String deliverSubject = useDeliverSubject ? random() : null;
        PushConsumerCreator creator = new PushConsumerCreator()
            .durable(durable)
            .deliverSubject(deliverSubject)
            .subjects(subject);
        ctx.jsm.createOrUpdateConsumer(stream, creator);

        JetStreamPushSubscription sub = ctx.js.pushSubscribe(stream, creator);
        assertSubscription(sub, stream, durable, deliverSubject, false);

        // read what is available
        List<Message> messages = readMessagesAck(sub);
        int total = messages.size();
        validateRedAndTotal(5, messages.size(), 5, total);

        // read again, nothing should be there
        messages = readMessagesAck(sub);
        total += messages.size();
        validateRedAndTotal(0, messages.size(), 5, total);

        unsubscribeEnsureNotBound(sub);

        // re-subscribe
        sub = ctx.js.pushSubscribe(stream, creator);

        // read again, nothing should be there
        messages = readMessagesAck(sub);
        total += messages.size();
        validateRedAndTotal(0, messages.size(), 5, total);

        unsubscribeEnsureNotBound(sub);
    }

    private void _testPushDurableSubAsync(JetStreamTestingContext ctx, NatsDispatcher dispatcher, String stream, String subjectDotGt, boolean useDeliverSubject) throws JetStreamException, InterruptedException {
        String subject = subjectDotGt.replace(">", random());

        // publish some messages
        jsPublish(ctx.js, subject, 5);

        String deliverSubject = useDeliverSubject ? random() : null;
        PushConsumerCreator creator = new PushConsumerCreator()
            .durable(random())
            .deliverSubject(deliverSubject)
            .subjects(subject);
        ctx.jsm.createOrUpdateConsumer(stream, creator);

        CountDownLatch msgLatch = new CountDownLatch(5);
        AtomicInteger received = new AtomicInteger();

        MessageHandler handler = (Message msg) -> {
            received.incrementAndGet();
            msg.ack();
            msgLatch.countDown();
        };

        SubscribeBehavior behavior = new SubscribeBehavior().handler(handler).dispatcher(dispatcher);

        // Subscribe using the handler
        JetStreamPushSubscription sub = ctx.js.pushSubscribe(stream, creator, behavior);

        // Wait for messages to arrive using the countdown latch.
        awaitAndAssert(msgLatch);

        unsubscribeEnsureNotBound(dispatcher, sub);

        assertEquals(5, received.get());
    }

    @Test
    public void testHeadersOnly() throws Exception {
        runInShared((nc, ctx) -> {
            JetStreamPushSubscription subRegular = ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator());
            JetStreamPushSubscription subHeadersOnly = ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().headersOnly(true));
            nc.flush(1000); // flush outgoing communication with/to the server

            jsPublish(ctx.js, ctx.subject(), 5);

            List<Message> messages = readMessagesAck(subRegular, 0L, 5);
            assertEquals(5, messages.size());
            assertTrue(messages.get(0).getData().length > 0);
            assertNull(messages.get(0).getHeaders());

            messages = readMessagesAck(subHeadersOnly, 0L, 5);
            assertEquals(5, messages.size());
            assertEquals(0, messages.get(0).getData().length);
            assertNotNull(messages.get(0).getHeaders());
            assertEquals("6", messages.get(0).getHeaders().getFirst(JetStreamConstants.MSG_SIZE_HDR));
        });
    }

    @Test
    public void testAcks() throws Exception {
        runInShared((nc, ctx) -> {
            JetStreamPushSubscription sub = ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().ackWait(Duration.ofMillis(1500)));
            nc.flush(1000); // flush outgoing communication with/to the server

            // TERM
            jsPublish(ctx.js, ctx.subject(), "TERM", 1);

            Message message = sub.nextMessage(1000L);
            assertNotNull(message);
            String data = new String(message.getData());
            assertEquals("TERM1", data);
            message.term();
            assertEquals(AckType.AckTerm, message.lastAck());

            assertNull(sub.nextMessage(500L));

            // Ack Wait timeout
            jsPublish(ctx.js, ctx.subject(), "WAIT", 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("WAIT1", data);
            sleep(2000);
            message.ack(); // this ack came too late so will be ignored
            assertEquals(AckType.AckAck, message.lastAck());

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("WAIT1", data);

            // In Progress
            jsPublish(ctx.js, ctx.subject(), "PRO", 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("PRO1", data);
            message.inProgress();
            assertEquals(AckType.AckProgress, message.lastAck());
            sleep(750);
            message.inProgress();
            assertEquals(AckType.AckProgress, message.lastAck());
            sleep(750);
            message.inProgress();
            assertEquals(AckType.AckProgress, message.lastAck());
            sleep(750);
            message.inProgress();
            assertEquals(AckType.AckProgress, message.lastAck());
            sleep(750);
            message.ack();
            assertEquals(AckType.AckAck, message.lastAck());

            assertNull(sub.nextMessage(500L));

            // ACK Sync
            jsPublish(ctx.js, ctx.subject(), "ACKSYNC", 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("ACKSYNC1", data);
            message.ackSync(1000);
            assertEquals(AckType.AckAck, message.lastAck());

            assertNull(sub.nextMessage(500L));

            // NAK
            jsPublish(ctx.js, ctx.subject(), "NAK", 1, 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK1", data);
            message.nak();
            assertEquals(AckType.AckNak, message.lastAck());

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK1", data);
            message.ack();
            assertEquals(AckType.AckAck, message.lastAck());

            assertNull(sub.nextMessage(500L));

            jsPublish(ctx.js, ctx.subject(), "NAK", 2, 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK2", data);
            message.nakWithDelay(3000);
            assertEquals(AckType.AckNak, message.lastAck());

            assertNull(sub.nextMessage(500L));

            message = sub.nextMessage(3000000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK2", data);
            message.ack();
            assertEquals(AckType.AckAck, message.lastAck());

            assertNull(sub.nextMessage(500L));

            jsPublish(ctx.js, ctx.subject(), "NAK", 3, 1);

            message = sub.nextMessage(1000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK3", data);
            message.nakWithDelay(3000);
            assertEquals(AckType.AckNak, message.lastAck());

            assertNull(sub.nextMessage(500L));

            message = sub.nextMessage(3000000L);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK3", data);
            message.ack();
            assertEquals(AckType.AckAck, message.lastAck());

            assertNull(sub.nextMessage(500L));
        });
    }

    @Test
    public void testDeliveryPolicy() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            String subject = ctx.subject();
            String subjectStar = subjectStar(subject);
            ctx.createOrReplaceStream(subjectStar);

            String subjectA = subjectDot(subject, "A");
            String subjectB = subjectDot(subject, "B");

            ctx.js.publish(subjectA, dataBytes(1));
            ctx.js.publish(subjectA, dataBytes(2));
            sleep(1500);
            ctx.js.publish(subjectA, dataBytes(3));
            ctx.js.publish(subjectB, dataBytes(91));
            ctx.js.publish(subjectB, dataBytes(92));

            ctx.jsm.deleteMessage(ctx.stream, 4);

            // DeliverPolicy.All
            JetStreamPushSubscription sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.All));
            Message m1 = sub.nextMessage(1000L);
            assertMessage(m1, 1);
            Message m2 = sub.nextMessage(1000L);
            assertMessage(m2, 2);
            Message m3 = sub.nextMessage(1000L);
            assertNotNull(m3);
            assertNotNull(m3.metaData());
            assertMessage(m3, 3);

            // DeliverPolicy.Last
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.Last));
            Message m = sub.nextMessage(1000L);
            assertMessage(m, 3);
            assertNull(sub.nextMessage(200L));

            // DeliverPolicy.New - No new messages between subscribe and next message
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.New));
            assertNull(sub.nextMessage(1000L));

            // DeliverPolicy.New - New message between subscribe and next message
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.New));
            ctx.js.publish(subjectA, dataBytes(4));
            m = sub.nextMessage(1000L);
            assertMessage(m, 4);

            // DeliverPolicy.ByStartSequence
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.ByStartSequence)
                    .startSequence(3));
            m = sub.nextMessage(1000L);
            assertMessage(m, 3);
            m = sub.nextMessage(1000L);
            assertMessage(m, 4);

            // DeliverPolicy.ByStartTime
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.ByStartTime)
                    .startTime(m3.metaData().timestamp().minusSeconds(1)));
            m = sub.nextMessage(1000L);
            assertMessage(m, 3);
            m = sub.nextMessage(1000L);
            assertMessage(m, 4);

            // DeliverPolicy.LastPerSubject
            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.LastPerSubject));
            m = sub.nextMessage(1000L);
            assertMessage(m, 4);

            // DeliverPolicy.ByStartSequence with a deleted record
            PublishAck pa4 = ctx.js.publish(subjectA, dataBytes(4));
            PublishAck pa5 = ctx.js.publish(subjectA, dataBytes(5));
            ctx.js.publish(subjectA, dataBytes(6));
            ctx.jsm.deleteMessage(ctx.stream, pa4.getSequenceNumber());
            ctx.jsm.deleteMessage(ctx.stream, pa5.getSequenceNumber());

            sub = ctx.js.pushSubscribe(ctx.stream,
                new PushConsumerCreator()
                    .subjects(subjectA)
                    .deliverPolicy(DeliverPolicy.ByStartSequence)
                    .startSequence(pa4.getSequenceNumber()));
            m = sub.nextMessage(1000L);
            assertMessage(m, 6);
        });
    }

    private void assertMessage(Message m, int i) {
        assertNotNull(m);
        assertEquals(data(i), new String(m.getData()));
    }

    @Test
    public void testPushSyncFlowControl() throws Exception {
        Listener listener = new Listener();
        runInSharedOwnNc(listener, (nc, ctx) -> {
            byte[] data = new byte[1024 * 10];
            int MSG_COUNT = 1000;

            // publish some messages
            for (int x = 100_000; x < MSG_COUNT + 100_000; x++) {
                byte[] fill = ("" + x).getBytes();
                System.arraycopy(fill, 0, data, 0, 6);
                ctx.js.publish(NatsMessage.builder().subject(ctx.subject()).data(data).build());
            }

            // reset the counters
            Set<String> set = new HashSet<>();

            JetStreamPushSubscription sub = ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).flowControl(1000));
            for (int x = 0; x < MSG_COUNT; x++) {
                Message msg = sub.nextMessage(1000L);
                assertNotNull(msg);
                assertNotNull(msg.getData());
                set.add(new String(Arrays.copyOf(msg.getData(), 6)));
                msg.ack();
                sleep(5); // slow it down, easier to get flow control
            }

            assertEquals(MSG_COUNT, set.size());
            assertTrue(listener.getFlowControlCount() > 0);

            // coverage for subscribe options heartbeat directly
            sub = ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator().subjects(ctx.subject()).idleHeartbeat(100));
            for (int x = 0; x < MSG_COUNT; x++) {
                Message msg = sub.nextMessage(1000L);
                assertNotNull(msg);
                assertNotNull(msg.getData());
                set.add(new String(Arrays.copyOf(msg.getData(), 6)));
                msg.ack();
                sleep(5); // slow it down, easier to get flow control
            }

            assertEquals(MSG_COUNT, set.size());
            assertTrue(listener.getFlowControlCount() > 0);
        });
    }
    
    @Test
    public void testPendingLimits() throws Exception {
        runInShared((nc, ctx) -> {
            int customMessageLimit = 1000;
            int customByteLimit = 1024 * 1024;

            PushConsumerCreator creator = new PushConsumerCreator().subjects(ctx.subject());
            
            SubscribeBehavior bhDefaultSync = new SubscribeBehavior();

            SubscribeBehavior bhCustomSync = new SubscribeBehavior()
                .pendingMessageLimit(customMessageLimit)
                .pendingByteLimit(customByteLimit);

            SubscribeBehavior bhCustomSyncUnlimited0 = new SubscribeBehavior()
                .pendingMessageLimit(0)
                .pendingByteLimit(0);

            SubscribeBehavior bhCustomSyncUnlimitedUnlimitedNegative = new SubscribeBehavior()
                .pendingMessageLimit(-1)
                .pendingByteLimit(-1);

            JetStreamPushSubscription syncSub = ctx.js.pushSubscribe(ctx.stream, creator, bhDefaultSync);
            assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, syncSub.getPendingMessageLimit());
            assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, syncSub.getPendingByteLimit());

            syncSub = ctx.js.pushSubscribe(ctx.stream, creator, bhCustomSync);
            assertEquals(customMessageLimit, syncSub.getPendingMessageLimit());
            assertEquals(customByteLimit, syncSub.getPendingByteLimit());

            syncSub = ctx.js.pushSubscribe(ctx.stream, creator, bhCustomSyncUnlimited0);
            assertEquals(-1, syncSub.getPendingMessageLimit());
            assertEquals(-1, syncSub.getPendingByteLimit());

            syncSub = ctx.js.pushSubscribe(ctx.stream, creator, bhCustomSyncUnlimitedUnlimitedNegative);
            assertEquals(-1, syncSub.getPendingMessageLimit());
            assertEquals(-1, syncSub.getPendingByteLimit());

            NatsDispatcher d = nc.createDispatcher();
            d.setPendingLimits(customMessageLimit, customByteLimit);
            assertEquals(customMessageLimit, d.getPendingMessageLimit());
            assertEquals(customByteLimit, d.getPendingByteLimit());

            SubscribeBehavior bhAsyncDefault = new SubscribeBehavior().dispatcher(d).handler(m -> {});
            SubscribeBehavior bhAsyncNonDefaultValid = new SubscribeBehavior()
                .dispatcher(d)
                .handler(m -> {})
                .pendingMessageLimit(OptionsConstants.DEFAULT_MAX_MESSAGES)
                .pendingByteLimit(OptionsConstants.DEFAULT_MAX_BYTES);

            JetStreamPushSubscription subAsync = ctx.js.pushSubscribe(ctx.stream, creator, bhAsyncDefault);
            assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, subAsync.getPendingMessageLimit());
            assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, subAsync.getPendingByteLimit());

            subAsync = ctx.js.pushSubscribe(ctx.stream, creator, bhAsyncNonDefaultValid);
            assertEquals(OptionsConstants.DEFAULT_MAX_MESSAGES, subAsync.getPendingMessageLimit());
            assertEquals(OptionsConstants.DEFAULT_MAX_BYTES, subAsync.getPendingByteLimit());
        });
    }
}
