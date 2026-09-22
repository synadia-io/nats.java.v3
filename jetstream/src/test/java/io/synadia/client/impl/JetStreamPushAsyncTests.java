package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.api.PushConsumerCreator;
import io.synadia.client.api.SubscribeBehavior;
import io.synadia.client.utils.Listener;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamPushAsyncTests extends JetStreamTestBase {

    @Test
    public void testHandlerSub() throws Exception {
        runInShared((nc, ctx) -> {
            // publish some messages
            jsPublish(ctx.js, ctx.subject(), 10);

            CountDownLatch msgLatch = new CountDownLatch(10);
            AtomicInteger received = new AtomicInteger();

            // create our message handler.
            MessageHandler handler = (Message msg) -> {
                received.incrementAndGet();
                msg.ack();
                msgLatch.countDown();
            };

            // Subscribe using the handler
            SubscribeBehavior behavior = new SubscribeBehavior().handler(handler);
            ctx.js.pushSubscribe(ctx.subject(), behavior);

            // Wait for messages to arrive using the countdown latch.
            // make sure we don't wait forever
            awaitAndAssert(msgLatch);

            assertEquals(10, received.get());
        });
    }

    @Test
    public void testCantNextMessageOnAsyncPushSub() throws Exception {
        runInShared((nc, ctx) -> {
            SubscribeBehavior behavior = new SubscribeBehavior().handler(msg -> {});
            JetStreamPushSubscription sub = ctx.js.pushSubscribe(ctx.subject(), behavior);

            // this should exception, can't next message on an async push sub
            assertThrows(IllegalStateException.class, () -> sub.nextMessage(1000));
            assertThrows(IllegalStateException.class, () -> sub.nextMessage(1000));
        });
    }

    @Test
    public void testPushAsyncFlowControl() throws Exception {
        Listener listener = new Listener();
        OptionsBuilder builder = new OptionsBuilder().errorListener(listener);
        runInShared(builder, (nc, ctx) -> {
            byte[] data = new byte[8192];

            int MSG_COUNT = 1000;

            // publish some messages
            for (int x = 100_000; x < MSG_COUNT + 100_000; x++) {
                byte[] fill = (""+ x).getBytes();
                System.arraycopy(fill, 0, data, 0, 6);
                ctx.js.publish(NatsMessage.builder().subject(ctx.subject()).data(data).build());
            }

            CountDownLatch msgLatch = new CountDownLatch(MSG_COUNT);
            AtomicInteger count = new AtomicInteger();
            AtomicReference<Set<String>> set = new AtomicReference<>(new HashSet<>());

            // create our message handler.
            MessageHandler handler = (Message msg) -> {
                String id = new String(Arrays.copyOf(msg.getData(), 6));
                if (set.get().add(id)) {
                    count.incrementAndGet();
                }
                sleep(5); // slow the process down to hopefully get flow control more often
                msg.ack();
                msgLatch.countDown();
            };

            PushConsumerCreator creator = new PushConsumerCreator().flowControl(1000).filterSubjects(ctx.subject());
            SubscribeBehavior behavior = new SubscribeBehavior().handler(handler);
            ctx.js.pushSubscribe(ctx.stream, creator, behavior);

            // Wait for messages to arrive using the countdown latch.
            // make sure we don't wait forever
            awaitAndAssert(msgLatch);

            assertEquals(MSG_COUNT, count.get());
            assertTrue(listener.getFlowControlCount() > 0);
        });
    }
}
