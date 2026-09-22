package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.utils.Listener;
import io.synadia.client.utils.VersionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.client.api.Status.CONFLICT_CODE;
import static io.synadia.client.impl.JetStreamConstants.NATS_PIN_ID_HDR;
import static io.synadia.client.utils.JetStreamClientError.JsConsumerPinnedNotAllowed;
import static io.synadia.client.utils.ListenerStatusType.PullWarning;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class JetStreamPullTests extends JetStreamTestBase {

    @Test
    public void testFetch() throws Exception {
        runInShared((nc, ctx) -> {
            long fetchMs = 3000;
            Duration ackWaitDur = Duration.ofMillis(fetchMs * 2);

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(ackWaitDur)
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            List<Message> messages = sub.fetch(10, fetchMs);
            validateRead(0, messages.size());
            messages.forEach(Message::ack);
            sleep(ackWaitDur.toMillis()); // let the pull expire

            jsPublish(ctx.js, ctx.subject(), "A", 10);
            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "B", 20);
            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "C", 5);
            messages = sub.fetch(10, fetchMs);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // let the pull expire

            jsPublish(ctx.js, ctx.subject(), "D", 15);
            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            messages = sub.fetch(10, fetchMs);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "E", 10);
            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            sleep(ackWaitDur.toMillis()); // let the acks wait expire, pull will also expire it's shorter

            // message were not ack'ed
            messages = sub.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            assertThrows(IllegalArgumentException.class, () -> sub.fetch(10, -1));
        });
    }

    @Test
    public void testIterate() throws Exception {
        runInShared((nc, ctx) -> {
            long fetchMs = 5000;
            Duration ackWaitDur = Duration.ofMillis(fetchMs * 2);

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(ackWaitDur)
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            Iterator<Message> iterator = sub.iterate(10, fetchMs);
            List<Message> messages = readMessages(iterator);
            validateRead(0, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "A", 10);
            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "B", 20);
            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "C", 5);
            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // give time for the pull to expire

            jsPublish(ctx.js, ctx.subject(), "D", 15);
            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // give time for the pull to expire

            jsPublish(ctx.js, ctx.subject(), "E", 10);
            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            sleep(ackWaitDur.toMillis()); // give time for the pull and the ack wait to expire

            iterator = sub.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "F", 1);
            iterator = sub.iterate(1, fetchMs);
            //noinspection ResultOfMethodCallIgnored
            iterator.hasNext(); // calling hasNext twice in a row is for coverage
            //noinspection ResultOfMethodCallIgnored
            iterator.hasNext(); // calling hasNext twice in a row is for coverage
        });
    }

    @Test
    public void testBasic() throws Exception {
        runInShared((nc, ctx) -> {

            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            // publish some amount of messages, but not entire pull size
            jsPublish(ctx.js, ctx.subject(), "A", 4);

            // start the pull
            sub.pull(10);

            // read what is available, expect 4
            List<Message> messages = readMessagesAck(sub);
            int total = messages.size();
            validateRedAndTotal(4, messages.size(), 4, total);

            // publish some more covering our initial pull and more
            jsPublish(ctx.js, ctx.subject(), "B", 10);

            // read what is available, expect 6 more
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(6, messages.size(), 10, total);

            // read what is available, should be zero since we didn't re-pull
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(0, messages.size(), 10, total);

            // re-issue the pull
            sub.pull(PullRequestOptions.builder(10).build()); // coverage of the build api

            // read what is available, should be 4 left over
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(4, messages.size(), 14, total);

            // publish some more
            jsPublish(ctx.js, ctx.subject(), "C", 10);

            // read what is available, should be 6 since we didn't finish the last batch
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(6, messages.size(), 20, total);

            // re-issue the pull, but a smaller amount
            sub.pull(2);

            // read what is available, should be 2 since we changed the pull size
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(2, messages.size(),22, total);

            // re-issue the pull, since we got the full batch size
            sub.pull(2);

            // read what is available, should be 2
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(2, messages.size(), 24, total);

            // re-issue the pull, any amount there are no messages
            sub.pull(1);

            // read what is available, there are none
            messages = readMessagesAck(sub);
            total += messages.size();
            validateRedAndTotal(0, messages.size(), 24, total);

            // publish some more to test null timeout
            jsPublish(ctx.js, ctx.subject(), "D", 10);
            creator = new PullConsumerCreator().durable(random()).filterSubject(ctx.subject());
            sub = ctx.js.pullSubscribe(ctx.stream, creator);
            sub.pull(10);
            sleep(500);
            messages = readMessagesAck(sub, null);
            validateRedAndTotal(10, messages.size(), 10, messages.size());

            // publish some more to test never timeout
            jsPublish(ctx.js, ctx.subject(), "E", 10);
            creator = new PullConsumerCreator().durable(random()).filterSubject(ctx.subject());
            sub = ctx.js.pullSubscribe(ctx.stream, creator);
            sub.pull(10);
            sleep(500);
            messages = readMessagesAck(sub, 0L, 10);
            validateRedAndTotal(10, messages.size(), 10, messages.size());
        });
    }

    @Test
    public void testNoWait() throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            // publish 10 messages
            // no wait, batch size 10, there are 10 messages, we will read them all and not trip nowait
            jsPublish(ctx.js, ctx.subject(), "A", 10);
            sub.pullNoWait(10);
            List<Message> messages = readMessagesAck(sub);
            assertEquals(10, messages.size());
            assertAllJetStream(messages);

            // publish 20 messages
            // no wait, batch size 10, there are 20 messages, we will read 10
            jsPublish(ctx.js, ctx.subject(), "B", 20);
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            // there are still ten messages
            // no wait, batch size 10, there are 20 messages, we will read 10
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            // publish 5 messages
            // no wait, batch size 10, there are 5 messages, we WILL trip nowait
            jsPublish(ctx.js, ctx.subject(), "C", 5);
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(5, messages.size());

            // publish 12 messages
            // no wait, batch size 10, there are more than batch messages we will read 10
            jsPublish(ctx.js, ctx.subject(), "D", 12);
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            // 2 messages left
            // no wait, less than batch size will trip nowait
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(2, messages.size());

            // this is just coverage of the pullNoWait api + expires, not really validating server functionality
            // publish 12 messages
            // no wait, batch size 10, there are more than batch messages we will read 10
            jsPublish(ctx.js, ctx.subject(), "E", 12);
            sub.pullNoWait(10, 10000);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            // 2 messages left
            // no wait, less than batch size will trip nowait
            sub.pullNoWait(10, 1000);
            messages = readMessagesAck(sub);
            assertEquals(2, messages.size());
        });
    }

    @Test
    public void testPullExpires() throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            long expires = 500; // millis

            // publish 10 messages
            jsPublish(ctx.js, ctx.subject(), "A", 5);
            sub.pullExpiresIn(10, expires);
            List<Message> messages = readMessagesAck(sub);
            assertEquals(5, messages.size());
            assertAllJetStream(messages);
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "B", 10);
            sub.pullExpiresIn(10, expires);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "C", 5);
            sub.pullExpiresIn(10, expires);
            messages = readMessagesAck(sub);
            assertEquals(5, messages.size());
            assertAllJetStream(messages);
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "D", 10);
            sub.pull(10);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            jsPublish(ctx.js, ctx.subject(), "E", 5);
            sub.pullExpiresIn(10, expires); // using millis version here
            messages = readMessagesAck(sub);
            assertEquals(5, messages.size());
            assertAllJetStream(messages);
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "F", 10);
            sub.pullNoWait(10);
            messages = readMessagesAck(sub);
            assertEquals(10, messages.size());

            jsPublish(ctx.js, ctx.subject(), "G", 5);
            sub.pullExpiresIn(10, expires); // using millis version here
            messages = readMessagesAck(sub);
            assertEquals(5, messages.size());
            assertAllJetStream(messages);
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "H", 10);
            messages = sub.fetch(10, expires);
            assertEquals(10, messages.size());
            assertAllJetStream(messages);

            jsPublish(ctx.js, ctx.subject(), "I", 5);
            sub.pullExpiresIn(10, expires);
            messages = readMessagesAck(sub);
            assertEquals(5, messages.size());
            assertAllJetStream(messages);
            sleep(expires); // make sure the pull actually expires

            jsPublish(ctx.js, ctx.subject(), "J", 10);
            Iterator<Message> i = sub.iterate(10, expires);
            int count = 0;
            while (i.hasNext()) {
                assertIsJetStream(i.next());
                ++count;
            }
            assertEquals(10, count);

            assertThrows(IllegalArgumentException.class, () -> sub.pullExpiresIn(10, -1000));
        });
    }

    @Test
    public void testAckNak() throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            // NAK
            jsPublish(ctx.js, ctx.subject(), "NAK", 1);

            sub.pull(1);

            Message message = sub.nextMessage(1000);
            assertNotNull(message);
            String data = new String(message.getData());
            assertEquals("NAK1", data);
            message.nak();

            sub.pull(1);
            message = sub.nextMessage(1000);
            assertNotNull(message);
            data = new String(message.getData());
            assertEquals("NAK1", data);
            message.ack();

            sub.pull(1);
            assertNull(sub.nextMessage(1000));
        });
    }

    @Test
    public void testAckTerm() throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);

            // TERM
            jsPublish(ctx.js, ctx.subject(), "TERM", 1);

            sub.pull(1);
            Message message = sub.nextMessage(1000);
            assertNotNull(message);
            String data = new String(message.getData());
            assertEquals("TERM1", data);
            message.term();

            sub.pull(1);
            assertNull(sub.nextMessage(1000));
        });
    }

    @Test
    public void testAckReplySyncCoverage() throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);

            jsPublish(ctx.js, ctx.subject(), "COVERAGE", 1);
            sub.pull(1);

            Message message = sub.nextMessage(1000);
            assertNotNull(message);

            JetStreamMessage njsMsg = (JetStreamMessage)message;

            njsMsg.replyTo = "$tsc.js.ACK.stream.LS0k4eeN.1.1.1.1627472530542070600.0";

            assertThrows(TimeoutException.class, () -> njsMsg.ackSync(1));
        });
    }

    @Test
    public void testAckWaitTimeout() throws Exception {
        runInShared((nc, ctx) -> {

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(1500)
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);

            // Ack Wait timeout
            jsPublish(ctx.js, ctx.subject(), "WAIT", 2);

            sub.pull(2);
            Message m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals("WAIT1", new String(m.getData()));

            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals("WAIT2", new String(m.getData()));

            sleep(2000);

            sub.pull(2);
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals("WAIT1", new String(m.getData()));
            m.ack();

            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals("WAIT2", new String(m.getData()));
            m.ack();

            sub.pull(2);
            m = sub.nextMessage(1000);
            assertNull(m);
        });
    }

    @Test
    public void testDoesNotExceedMaxRequestBytesExactBytes() throws Exception {
        Listener listener = new Listener();
        runInSharedOwnNc(listener, nc -> {
            listener.queueStatus(PullWarning, CONFLICT_CODE);

            JetStreamTestingContext ctx = new JetStreamTestingContext(nc, 0);
            String stream = random(6); // six letters so I can count
            String subject = random(5); // five letters so I can count
            String durable = random(); // default random is 10 chars so is short enough to keeps under max bytes

            StreamCreator streamCreator = new StreamCreator(stream).subjects(subject);
            ctx.createOrReplaceStream(streamCreator);

            PullConsumerCreator consumerCreator = new PullConsumerCreator().durable(durable).ackPolicy(AckPolicy.None).filterSubject(subject);
            JetStreamPullSubscription sub = ctx.js.pullSubscribe(stream, consumerCreator);

            // 159 + 180 + 661 = 1000 // subject includes crlf
            // subject 7 + reply 52 + bytes 100 = 159
            // subject 7 + reply 52 + bytes 100 + headers 21 = 180
            // subject 7 + reply 52 + bytes 602 = 661
            ctx.js.publish(subject, new byte[100]);
            ctx.js.publish(subject, new Headers().add("foo", "bar"), new byte[100]);
            ctx.js.publish(subject, new byte[602]);

            sub.pull(PullRequestOptions.builder(10).maxBytes(1000).expiresIn(1000).build());
            assertNotNull(sub.nextMessage(500));
            assertNotNull(sub.nextMessage(500));
            assertNotNull(sub.nextMessage(500));
            assertNull(sub.nextMessage(500)); // there are no more messages
            listener.validateNotReceived();
        });
    }

    @Test
    public void testOverflow() throws Exception {
        runInShared(VersionUtils::atLeast2_11, (nc, ctx) -> {
            jsPublish(ctx.js, ctx.subject(), 100);

            // Setting PriorityPolicy requires at least one PriorityGroup to be set
            PullConsumerCreator ccNoGroup = new PullConsumerCreator()
                .priorityPolicy(PriorityPolicy.Overflow);

            JetStreamApiException jsae = assertThrows(JetStreamApiException.class,
                () -> ctx.jsm.createConsumer(ctx.stream, ccNoGroup));
            assertEquals(10159, jsae.getApiErrorCode());

            // Testing errors
            String group = random();
            String consumer = random();

            PullConsumerCreator cc = new PullConsumerCreator()
                .name(consumer)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .filterSubject(ctx.subject());

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, cc);

            // 400 Bad Request - Priority Group missing
            sub.pull(1);
            JetStreamStatusException jssex = assertThrows(JetStreamStatusException.class, () -> sub.nextMessage(1000));
            assertTrue(jssex.getMessage().contains("Priority Group missing"));

            // 400 Bad Request - Invalid Priority Group
            sub.pull(PullRequestOptions.builder(5).group("bogus").build());
            assertThrows(JetStreamStatusException.class, () -> sub.nextMessage(1000));

            // Testing min ack pending
            group = random();
            consumer = random();

            cc = new PullConsumerCreator()
                .name(consumer)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .ackWait(60_000)
                .filterSubject(ctx.subject());
            ConsumerInfo ci = ctx.jsm.createConsumer(ctx.stream, cc);

            JetStreamPullSubscription subPrime = ctx.js.pullSubscribe(ci);
            JetStreamPullSubscription subOver = ctx.js.pullSubscribe(ci);

            PullRequestOptions proNoMin = PullRequestOptions.builder(5)
                .group(group)
                .build();

            PullRequestOptions proOverA = PullRequestOptions.builder(5)
                .group(group)
                .minAckPending(5)
                .build();

            PullRequestOptions proOverB = PullRequestOptions.builder(5)
                .group(group)
                .minAckPending(10)
                .build();

            _overflowCheck(subPrime, proNoMin, true, 5);
            _overflowCheck(subOver, proNoMin, true, 5);

            _overflowCheck(subPrime, proNoMin, false, 5);
            _overflowCheck(subOver, proOverA, true, 5);
            _overflowCheck(subOver, proOverB, true, 0);

            // Testing min pending
            group = random();
            consumer = random();

            cc = new PullConsumerCreator()
                .name(consumer)
                .priorityPolicy(PriorityPolicy.Overflow)
                .priorityGroups(group)
                .filterSubject(ctx.subject());
            ci = ctx.jsm.createConsumer(ctx.stream, cc);

            subPrime = ctx.js.pullSubscribe(ci);
            subOver = ctx.js.pullSubscribe(ci);

            proNoMin = PullRequestOptions.builder(5)
                .group(group)
                .build();

            proOverA = PullRequestOptions.builder(5)
                .group(group)
                .minPending(78)
                .build();

            _overflowCheck(subPrime, proNoMin, true, 5);
            _overflowCheck(subOver, proNoMin, true, 5);
            _overflowCheck(subOver, proOverA, true, 5);
            _overflowCheck(subOver, proOverA, true, 5);
            // exactly 80 messages now pending, gt or eq to pull min pending for 3 (80, 79, 78)
            _overflowCheck(subOver, proOverA, true, 3);
            // exactly 77 messages now pending lt pull min pending
            _overflowCheck(subOver, proOverA, true, 0);
        });
    }

    private static void _overflowCheck(JetStreamPullSubscription sub, PullRequestOptions pro, boolean ack, int expected) throws InterruptedException {
        sub.pull(pro);
        int count = 0;
        Message m = sub.nextMessage(1000);
        while (m != null) {
            count++;
            if (ack) {
                m.ack();
            }
            m = sub.nextMessage(100);
        }
        assertEquals(expected, count);
    }

    @Test
    public void testPrioritized() throws Exception {
        // PriorityPolicy.Prioritized
        // start a priority 1 (#1) and a priority 2 (#2) consumer, #1 should get messages, #2 should get none
        // close the #1, #2 should get messages
        // start another priority 1 (#3), #2 should stop getting messages #3 should get messages
        runInShared(VersionUtils::atLeast2_12, (nc, ctx) -> {
            String consumer = random();
            String group = random();

            PullConsumerCreator cc = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .name(consumer)
                .priorityGroups(group)
                .priorityPolicy(PriorityPolicy.Prioritized);

            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            ConsumerContext consumerContext1 = streamContext.createOrUpdateConsumer(cc);
            ConsumerContext consumerContext2 = streamContext.getConsumerContext(consumer);

            AtomicInteger count1 = new AtomicInteger();
            CountDownLatch latch1 = new CountDownLatch(20);
            MessageHandler handler1 = msg -> {
                msg.ack();
                count1.incrementAndGet();
                latch1.countDown();
            };

            AtomicInteger count2 = new AtomicInteger();
            CountDownLatch latch2 = new CountDownLatch(20);
            MessageHandler handler2 = msg -> {
                msg.ack();
                count2.incrementAndGet();
                latch2.countDown();
            };

            AtomicInteger count3 = new AtomicInteger();
            MessageHandler handler3 = msg -> {
                msg.ack();
                count3.incrementAndGet();
            };

            ConsumeOptions coP1 = ConsumeOptions.builder()
                .batchSize(10)
                .group(group)
                .priority(1)
                .build();
            ConsumeOptions coP2 = ConsumeOptions.builder()
                .batchSize(10)
                .group(group)
                .priority(2)
                .build();

            MessageConsumer mc1 = consumerContext1.consume(coP1, handler1);
            MessageConsumer mc2 = consumerContext2.consume(coP2, handler2);

            AtomicBoolean pub = new AtomicBoolean(true);
            Thread t = new Thread(() -> {
                int count = 0;
                while (pub.get()) {
                    ++count;
                    try {
                        ctx.js.publish(ctx.subject(), ("x" + count).getBytes());
                        sleep(20);
                    }
                    catch (Exception e) {
                        fail(e);
                        return;
                    }
                }
            });
            t.start();

            if (!latch1.await(5, TimeUnit.SECONDS)) {
                fail("Didn't get messages consumer 1");
            }
            assertEquals(0, count2.get());
            mc1.close();

            if (!latch2.await(5, TimeUnit.SECONDS)) {
                fail("Didn't get messages consumer 2");
            }
            MessageConsumer mc3 = consumerContext2.consume(coP1, handler3);

            Thread.sleep(200);
            pub.set(false);
            t.join();
            mc2.close();
            mc3.close();

            assertTrue(count1.get() >= 20);
            assertTrue(count2.get() >= 20);
            assertTrue(count3.get() > 0);
        });
    }

    @Test
    public void testPinnedClient() throws Exception {
        // have 3 consumers in the same group all PriorityPolicy.PinnedClient
        // start consuming, tracking pin ids and counts
        // unpin 10 times and make sure that new pins are made
        runInShared(VersionUtils::atLeast2_12, (nc, ctx) -> {
            String consumer = random();
            String group = random();

            PullConsumerCreator cc = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .name(consumer)
                .priorityGroups(group)
                .priorityPolicy(PriorityPolicy.PinnedClient);

            StreamContext streamContext = ctx.js.getStreamContext(ctx.stream);
            ConsumerContext consumerContext1 = streamContext.createOrUpdateConsumer(cc);
            ConsumerContext consumerContext2 = streamContext.getConsumerContext(consumer);
            ConsumerContext consumerContext3 = streamContext.getConsumerContext(consumer);

            //noinspection resource
            IllegalStateException ise = assertThrows(IllegalStateException.class, () -> consumerContext1.fetchMessages(10));
            assertTrue(JsConsumerPinnedNotAllowed.matches(ise));

            Set<String> pinIds = new HashSet<>();
            AtomicInteger count1 = new AtomicInteger();
            AtomicInteger count2 = new AtomicInteger();
            AtomicInteger count3 = new AtomicInteger();
            MessageHandler handler1 = msg -> {
                msg.ack();
                assertNotNull(msg.getHeaders());
                String natsPinId = msg.getHeaders().getFirst(NATS_PIN_ID_HDR);
                assertNotNull(natsPinId);
                pinIds.add(natsPinId);
                count1.incrementAndGet();
            };
            MessageHandler handler2 = msg -> {
                msg.ack();
                assertNotNull(msg.getHeaders());
                String natsPinId = msg.getHeaders().getFirst(NATS_PIN_ID_HDR);
                assertNotNull(natsPinId);
                pinIds.add(natsPinId);
                count2.incrementAndGet();
            };
            MessageHandler handler3 = msg -> {
                msg.ack();
                assertNotNull(msg.getHeaders());
                String natsPinId = msg.getHeaders().getFirst(NATS_PIN_ID_HDR);
                assertNotNull(natsPinId);
                pinIds.add(natsPinId);
                count3.incrementAndGet();
            };

            ConsumeOptions co = ConsumeOptions.builder()
                .batchSize(10)
                .expiresIn(1000)
                .group(group)
                .build();

            MessageConsumer mc1 = consumerContext1.consume(co, handler1);
            MessageConsumer mc2 = consumerContext2.consume(co, handler2);
            MessageConsumer mc3 = consumerContext3.consume(co, handler3);

            AtomicBoolean pub = new  AtomicBoolean(true);
            Thread t = new Thread(() -> {
                int count = 0;
                while (pub.get()) {
                    ++count;
                    try {
                        ctx.js.publish(ctx.subject(), ("x" + count).getBytes());
                        sleep(20);
                    }
                    catch (Exception e) {
                        fail(e);
                        return;
                    }
                }
            });
            t.start();

            int unpins = 0;
            while (unpins++ < 10) {
                sleep(650);
                switch (ThreadLocalRandom.current().nextInt(0, 4)) {
                    case 0:
                        assertTrue(consumerContext1.unpin(group));
                        break;
                    case 1:
                        assertTrue(consumerContext2.unpin(group));
                        break;
                    case 2:
                        assertTrue(consumerContext3.unpin(group));
                        break;
                    case 3:
                        assertTrue(ctx.jsm.unpinConsumer(ctx.stream, consumer, group));
                        break;
                }
                assertTrue(consumerContext1.unpin(group));
            }
            sleep(650);

            pub.set(false);
            t.join();
            mc1.close();
            mc2.close();
            mc3.close();

            assertTrue(pinIds.size() > 3);
            int c1 = count1.get();
            int c2 = count2.get();
            int c3 = count3.get();
            if (c1 > 0) {
                assertTrue(c2 > 0 || c3 > 0);
            }
            else if (c2 > 0) {
                assertTrue(c3 > 0);
            }
            else {
                fail("At least 2 consumers should have gotten messages");
            }
        });
    }

    @Test
    public void testReader() throws Exception {
        runInShared((nc, ctx) -> {
            // Pre define a consumer
            PullConsumerCreator creator = new PullConsumerCreator().durable(ctx.consumerName()).filterSubject(ctx.subject());
            ctx.jsm.createConsumer(ctx.stream, creator);

            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ctx.stream, creator);
            JetStreamReader reader = sub.reader(500, 125);

            int stopCount = 500;

            // create the consumer then use it
            AtomicInteger count = new AtomicInteger();
            Thread readerThread = getReaderThread(count, stopCount, reader);

            Publisher publisher = new Publisher(ctx.js, ctx.subject(), 25);
            Thread pubThread = new Thread(publisher);
            pubThread.start();

            readerThread.join();
            publisher.stop();
            pubThread.join();

            assertTrue(count.incrementAndGet() > 500);
        });
    }

    private static Thread getReaderThread(AtomicInteger count, int stopCount, JetStreamReader reader) {
        Thread readerThread = new Thread(() -> {
            try {
                while (count.get() < stopCount) {
                    Message msg = reader.nextMessage(1000);
                    if (msg != null) {
                        msg.ack();
                        count.incrementAndGet();
                    }
                }

                Thread.sleep(50); // allows more messages to come across
                reader.stop();

                Message msg = reader.nextMessage(1000); // also coverage next message
                while (msg != null) {
                    msg.ack();
                    count.incrementAndGet();
                    msg = reader.nextMessage(1000);
                }
            }
            catch (Exception e) {
                fail(e);
            }
        });
        readerThread.start();
        return readerThread;
    }
}
