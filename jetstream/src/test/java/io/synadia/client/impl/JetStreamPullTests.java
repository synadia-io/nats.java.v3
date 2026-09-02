package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.PullConsumerCreator;
import io.synadia.client.utils.Listener;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
public class JetStreamPullTests extends JetStreamTestBase {

    static NatsConnection conflictNc;
    static Listener conflictListener;

    @AfterAll
    public static void afterAll() {
        if (conflictNc != null) {
            conflictNc.close();
        }
    }

    @Test
    public void testFetch() throws Exception {
        runInShared((nc, ctx) -> {
            long fetchMs = 3000;
            Duration ackWaitDur = Duration.ofMillis(fetchMs * 2);

            PullConsumerCreator creator = new PullConsumerCreator()
                .ackWait(ackWaitDur)
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject())
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
            creator = new PullConsumerCreator().durable(random()).filterSubjects(ctx.subject());
            sub = ctx.js.pullSubscribe(ctx.stream, creator);
            sub.pull(10);
            sleep(500);
            messages = readMessagesAck(sub, null);
            validateRedAndTotal(10, messages.size(), 10, messages.size());

            // publish some more to test never timeout
            jsPublish(ctx.js, ctx.subject(), "E", 10);
            creator = new PullConsumerCreator().durable(random()).filterSubjects(ctx.subject());
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
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject())
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
                .filterSubjects(ctx.subject());

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
                .filterSubjects(ctx.subject())
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
}
