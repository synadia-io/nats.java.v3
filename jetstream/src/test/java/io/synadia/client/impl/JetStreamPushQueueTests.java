package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.PushConsumerCreator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JetStreamPushQueueTests extends JetStreamTestBase {

    @Test
    public void testQueueSubWorkflow() throws Exception {
        runInShared((nc, ctx) -> {
            // Set up the subscribers
            // - the PushSubscribeOptions can be re-used since all the subscribers are the same
            // - use a concurrent integer to track all the messages received
            // - have a list of subscribers and threads so I can track them
            String queue = random();
            PushConsumerCreator creator = new PushConsumerCreator().durable(ctx.consumerName()).filterSubjects(ctx.subject()).deliverGroup(queue);
            AtomicInteger allReceived = new AtomicInteger();
            List<JsQueueSubscriber> subscribers = new ArrayList<>();
            List<Thread> subThreads = new ArrayList<>();
            for (int id = 1; id <= 3; id++) {
                // set up the subscription
                JetStreamPushSubscription sub = ctx.js.pushSubscribe(ctx.stream, creator);
                // create and track the runnable
                JsQueueSubscriber qs = new JsQueueSubscriber(100, ctx.js, sub, allReceived);
                subscribers.add(qs);
                // create, track and start the thread
                Thread t = new Thread(qs);
                subThreads.add(t);
                t.start();
            }
            nc.flush(1000); // flush outgoing communication with/to the server

            // create and start the publishing
            Thread pubThread = new Thread(new JsPublisher(ctx.js, ctx.subject(), 100));
            pubThread.start();

            // wait for all threads to finish
            pubThread.join(5000, 0);
            for (Thread t : subThreads) {
                t.join(5000, 0);
            }

            Set<String> uniqueDatas = new HashSet<>();
            // count
            int count = 0;
            for (JsQueueSubscriber qs : subscribers) {
                int c = qs.thisReceived;
                assertTrue(c > 0);
                count += c;
                for (String s : qs.datas) {
                    assertTrue(uniqueDatas.add(s));
                }
            }

            assertEquals(100, count);
        });
    }

    static class JsPublisher implements Runnable {
        JetStream js;
        String subject;
        int msgCount;

        public JsPublisher(JetStream js, String subject, int msgCount) {
            this.js = js;
            this.subject = subject;
            this.msgCount = msgCount;
        }

        @Override
        public void run() {
            for (int x = 1; x <= msgCount; x++) {
                try {
                    js.publish(subject, ("Data # " + x).getBytes(StandardCharsets.US_ASCII));
                }
                catch (InterruptedException | JetStreamException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    static class JsQueueSubscriber implements Runnable {
        int msgCount;
        JetStream js;
        JetStreamPushSubscription sub;
        AtomicInteger allReceived;
        int thisReceived;
        List<String> datas;

        public JsQueueSubscriber(int msgCount, JetStream js, JetStreamPushSubscription sub, AtomicInteger allReceived) {
            this.msgCount = msgCount;
            this.js = js;
            this.sub = sub;
            this.allReceived = allReceived;
            this.thisReceived = 0;
            datas = new ArrayList<>();
        }

        @Override
        public void run() {
            while (allReceived.get() < msgCount) {
                try {
                    Message msg = sub.nextMessage(500);
                    while (msg != null) {
                        thisReceived++;
                        allReceived.incrementAndGet();
                        datas.add(new String(msg.getData()));
                        msg.ack();
                        msg = sub.nextMessage(500);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
