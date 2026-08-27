package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.PushConsumerCreator;
import io.synadia.client.api.SubscribeBehavior;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class JetStreamSubscribeTests extends JetStreamTestBase {

    @Test
    public void testJetStreamSubscribe() throws Exception {
        runInShared((nc, ctx) -> {
            String data = random();
            jsPublish(ctx.js, ctx.subject(), data);

            // pushSubscribe(ConsumerInfo consumerInfo)
            String durable = random();
            PushConsumerCreator creator = new PushConsumerCreator().durable(durable);
            ConsumerInfo ci = ctx.jsm.createConsumer(ctx.stream, creator);
            assertConsumer(ctx, true, durable, ci);

            JetStreamPushSubscription sub = ctx.js.pushSubscribe(ci);
            assertConsumer(ctx, false, durable, sub.getConsumerInfo());
            validateMessage(data, sub.nextMessage(DEFAULT_TIMEOUT_MS));

            // pushSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler)
            durable = random();
            creator = new PushConsumerCreator().durable(durable);
            ci = ctx.jsm.createConsumer(ctx.stream, creator);
            assertConsumer(ctx, true, durable, ci);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Message> messageRef = new AtomicReference<>();
            MessageHandler handler = message -> {
                messageRef.set(message);
                latch.countDown();
            };

            sub = ctx.js.pushSubscribe(ci, handler);
            assertConsumer(ctx, false, durable, sub.getConsumerInfo());
            assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
            validateMessage(data, messageRef.get());

            // pushSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior)
            SubscribeBehavior behavior = new SubscribeBehavior();

            // pushSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException,
            // pushSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // pushSubscribe(String subject) throws JetStreamException, InterruptedException
            // pushSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            // pushSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushConsumerCreator creator) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            // pushSubscribe(String stream, PushOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
        });
    }

    private static void assertConsumer(JetStreamTestingContext ctx, boolean checkPending, String durable, ConsumerInfo ci) throws JetStreamException, InterruptedException {
        List<String> names = ctx.jsm.getConsumerNames(ctx.stream);
        String name;
        if (durable == null) {
            name = ci.getName();
        }
        else {
            name = durable;
            assertEquals(durable, ci.getConsumerConfiguration().getDurable());
        }
        assertNotNull(name);
        assertEquals(name, ci.getName());
        assertTrue(names.contains(name));
        if (checkPending) {
            assertEquals(1, ci.getNumPending());
        }
//        else {
//            // The push is asynchronous: subscribing does not mean the server has already handed the
//            // message over by the time this consumer info was fetched. Asserting the first read is a
//            // race against the server, so poll to a deadline instead.
//            long stop = System.currentTimeMillis() + 2000;
//            long pending = ci.getNumPending();
//            while (pending != 0 && System.currentTimeMillis() < stop) {
//                Thread.sleep(50);
//                pending = ctx.jsm.getConsumerInfo(ctx.stream, name).getNumPending();
//            }
//            assertEquals(0, pending, "the server never handed the message over");
//        }
    }

    private static void validateMessage(String data, Message m) {
        assertNotNull(m);
        assertEquals(data, new String(m.getData()));
        assertEquals(1, m.metaData().streamSequence());
    }
}
