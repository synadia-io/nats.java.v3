// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.PullConsumerCreator;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class JetStreamLegacyPullTests extends JetStreamTestBase {

    @ParameterizedTest
    @EnumSource(JetStreamLegacyPullForm.class)
    public void testFetch(JetStreamLegacyPullForm form) throws Exception {
        runInShared((nc, ctx) -> {
            long fetchMs = 3000;
            Duration ackWaitDur = Duration.ofMillis(fetchMs * 2);

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(ackWaitDur)
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(ctx.js, ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);
            JetStreamLegacyPull legacy = form.of(sub);

            List<Message> messages = legacy.fetch(10, fetchMs);
            validateRead(0, messages.size());
            messages.forEach(Message::ack);
            sleep(ackWaitDur.toMillis()); // let the pull expire

            jsPublish(ctx.js, ctx.subject(), "A", 10);
            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "B", 20);
            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "C", 5);
            messages = legacy.fetch(10, fetchMs);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // let the pull expire

            jsPublish(ctx.js, ctx.subject(), "D", 15);
            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            messages = legacy.fetch(10, fetchMs);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "E", 10);
            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            sleep(ackWaitDur.toMillis()); // let the acks wait expire, pull will also expire it's shorter

            // message were not ack'ed
            messages = legacy.fetch(10, fetchMs);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            assertThrows(IllegalArgumentException.class, () -> legacy.fetch(10, -1));
        });
    }

    @ParameterizedTest
    @EnumSource(JetStreamLegacyPullForm.class)
    public void testIterate(JetStreamLegacyPullForm form) throws Exception {
        runInShared((nc, ctx) -> {
            long fetchMs = 5000;
            Duration ackWaitDur = Duration.ofMillis(fetchMs * 2);

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(ackWaitDur)
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(ctx.js, ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);
            JetStreamLegacyPull legacy = form.of(sub);

            Iterator<Message> iterator = legacy.iterate(10, fetchMs);
            List<Message> messages = readMessages(iterator);
            validateRead(0, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "A", 10);
            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "B", 20);
            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "C", 5);
            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // give time for the pull to expire

            jsPublish(ctx.js, ctx.subject(), "D", 15);
            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(5, messages.size());
            messages.forEach(Message::ack);
            sleep(fetchMs); // give time for the pull to expire

            jsPublish(ctx.js, ctx.subject(), "E", 10);
            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            sleep(ackWaitDur.toMillis()); // give time for the pull and the ack wait to expire

            iterator = legacy.iterate(10, fetchMs);
            messages = readMessages(iterator);
            validateRead(10, messages.size());
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "F", 1);
            iterator = legacy.iterate(1, fetchMs);
            //noinspection ResultOfMethodCallIgnored
            iterator.hasNext(); // calling hasNext twice in a row is for coverage
            //noinspection ResultOfMethodCallIgnored
            iterator.hasNext(); // calling hasNext twice in a row is for coverage
        });
    }

    @ParameterizedTest
    @EnumSource(JetStreamLegacyPullForm.class)
    public void testFetchAndIterateExpires(JetStreamLegacyPullForm form) throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(ctx.js, ctx.stream, creator);
            assertSubscription(sub, ctx.stream, ctx.consumerName(), null, true);
            JetStreamLegacyPull legacy = form.of(sub);

            long expires = 500; // millis

            jsPublish(ctx.js, ctx.subject(), "H", 10);
            List<Message> messages = legacy.fetch(10, expires);
            assertEquals(10, messages.size());
            assertAllJetStream(messages);
            messages.forEach(Message::ack);

            jsPublish(ctx.js, ctx.subject(), "J", 10);
            Iterator<Message> i = legacy.iterate(10, expires);
            int count = 0;
            while (i.hasNext()) {
                Message m = i.next();
                assertIsJetStream(m);
                m.ack();
                ++count;
            }
            assertEquals(10, count);

            assertThrows(IllegalArgumentException.class, () -> legacy.iterate(10, -1));
        });
    }

    @ParameterizedTest
    @EnumSource(JetStreamLegacyPullForm.class)
    public void testAlreadyBufferedAndReaderReads(JetStreamLegacyPullForm form) throws Exception {
        runInShared((nc, ctx) -> {
            PullConsumerCreator creator = new PullConsumerCreator()
                .filterSubject(ctx.subject())
                .durable(ctx.consumerName());

            JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(ctx.js, ctx.stream, creator);
            JetStreamLegacyPull legacy = form.of(sub);

            // 10 messages pulled and buffered before fetch and iterate are called: a full batch issues no pull
            jsPublish(ctx.js, ctx.subject(), "A", 10);
            sub.pull(10);
            waitForPending(sub, 10);

            List<Message> messages = legacy.fetch(5, 1000);
            assertEquals(5, messages.size());
            messages.forEach(Message::ack);

            Iterator<Message> iterator = legacy.iterate(5, 1000);
            messages = readMessages(iterator);
            assertEquals(5, messages.size());
            assertFalse(iterator.hasNext());
            messages.forEach(Message::ack);

            // the reader read methods other than nextMessage(long)
            jsPublish(ctx.js, ctx.subject(), "B", 3);
            JetStreamReader reader = legacy.reader(3, 3);
            waitForPending(sub, 3);
            Message m = reader.nextMessage(1000, TimeUnit.MILLISECONDS);
            assertNotNull(m);
            m.ack();
            m = reader.nextMessageNoWait();
            assertNotNull(m);
            m.ack();
            m = reader.nextMessageWaitForever();
            assertNotNull(m);
            m.ack();
            reader.stop();
        });
    }

    private static void waitForPending(JetStreamPullSubscription sub, int count) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (sub.getPendingMessageCount() < count && System.currentTimeMillis() < end) {
            sleep(10);
        }
        assertEquals(count, sub.getPendingMessageCount());
    }

    @ParameterizedTest
    @EnumSource(JetStreamLegacyPullForm.class)
    public void testReader(JetStreamLegacyPullForm form) throws Exception {
        runInShared((nc, ctx) -> {
            // Pre define a consumer
            PullConsumerCreator creator = new PullConsumerCreator().durable(ctx.consumerName()).filterSubject(ctx.subject());
            ctx.jsm.createConsumer(ctx.stream, creator);

            JetStreamPullSubscription sub = JetStreamPullSubscribe.pullSubscribe(ctx.js, ctx.stream, creator);
            JetStreamReader reader = form.of(sub).reader(500, 125);

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
