package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.JetStreamClientError.JsSubDispatcherNoHandlerCantReceiveMessages;
import static io.synadia.client.utils.JetStreamClientError.JsSubNoMatchingStreamForSubject;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamSubscribeTests extends JetStreamTestBase {

    @Test
    public void testJetStreamSubscribeConfigCoverage() throws Exception {
        runInShared((nc, ctx) -> {
            NatsDispatcher dispatcher = nc.createDispatcher();

            PushConsumerCreator pushConsumerCreator = new PushConsumerCreator();
            ConsumerInfo pushConsumerInfo = ctx.jsm.createConsumer(ctx.stream, pushConsumerCreator);

            PullConsumerCreator pullConsumerCreator = new PullConsumerCreator();
            ConsumerInfo pullConsumerInfo = ctx.jsm.createConsumer(ctx.stream, pullConsumerCreator);

            // dispatcher provided
            SubscribeBehavior subscribeBehavior =
                new SubscribeBehavior().dispatcher(dispatcher).handler(m -> {});

            JetStreamSubscribeConfig config =
                new JetStreamSubscribeConfig(pushConsumerInfo, subscribeBehavior, null, nc::createDispatcher);
            assertConfigConstruction(config, pushConsumerInfo, dispatcher, false, null, false);

            config =
                new JetStreamSubscribeConfig(pullConsumerInfo, subscribeBehavior, null, nc::createDispatcher);
            assertConfigConstruction(config, pullConsumerInfo, dispatcher, false, null, true);

            // no dispatcher provided, makes internal
            subscribeBehavior = new SubscribeBehavior().handler(m -> {});

            config =
                new JetStreamSubscribeConfig(pushConsumerInfo, subscribeBehavior, null, () -> dispatcher);
            assertConfigConstruction(config, pushConsumerInfo, dispatcher, true, null, false);

            config =
                new JetStreamSubscribeConfig(pullConsumerInfo, subscribeBehavior, null, () -> dispatcher);
            assertConfigConstruction(config, pullConsumerInfo, dispatcher, true, null, true);

            // not ordered
            config = new JetStreamSubscribeConfig(pushConsumerInfo, null, null, nc::createDispatcher);
            assertConfigConstruction(config, pushConsumerInfo, null, false, null, false);

            config = new JetStreamSubscribeConfig(pullConsumerInfo, null, null, nc::createDispatcher);
            assertConfigConstruction(config, pullConsumerInfo, null, false, null, true);

            // ordered, without a name prefix
            PushOrderedConsumerCreator pushOrderedCreator = new PushOrderedConsumerCreator();
            ConsumerInfo orderedInfo = ctx.jsm.createConsumer(ctx.stream, pushOrderedCreator);
            config = new JetStreamSubscribeConfig(orderedInfo, null, pushOrderedCreator, nc::createDispatcher);
            assertConfigConstruction(config, orderedInfo, null, false, pushOrderedCreator, false);

            PullOrderedConsumerCreator pullOrderedCreator = new PullOrderedConsumerCreator();
            orderedInfo = ctx.jsm.createConsumer(ctx.stream, pullOrderedCreator);
            config = new JetStreamSubscribeConfig(orderedInfo, null, pullOrderedCreator, nc::createDispatcher);
            assertConfigConstruction(config, orderedInfo, null, false, pullOrderedCreator, true);

            // ordered, with a name prefix
            String namePrefix = random();
            PushOrderedConsumerCreator pushPrefixedCreator = new PushOrderedConsumerCreator().namePrefix(namePrefix);
            ConsumerInfo prefixedInfo = ctx.jsm.createConsumer(ctx.stream, pushPrefixedCreator);
            config = new JetStreamSubscribeConfig(prefixedInfo, null, pushPrefixedCreator, nc::createDispatcher);
            assertConfigConstruction(config, prefixedInfo, null, false, pushPrefixedCreator, false);
            assertTrue(prefixedInfo.getName().startsWith(namePrefix + "-"));

            namePrefix = random();
            PullOrderedConsumerCreator pullPrefixedCreator = new PullOrderedConsumerCreator().namePrefix(namePrefix);
            prefixedInfo = ctx.jsm.createConsumer(ctx.stream, pullPrefixedCreator);
            config = new JetStreamSubscribeConfig(prefixedInfo, null, pullPrefixedCreator, nc::createDispatcher);
            assertConfigConstruction(config, prefixedInfo, null, false, pullPrefixedCreator, true);
            assertTrue(prefixedInfo.getName().startsWith(namePrefix + "-"));
        });
    }

    @Test
    public void testJetStreamSubscribeErrors() throws Exception {
        runInShared((nc, ctx) -> {
            // stream not found
            JetStreamApiException jsapiEx = assertThrows(JetStreamApiException.class,
                () -> ctx.js.pushSubscribe(random(), new PushConsumerCreator()));
            assertEquals(10059, jsapiEx.getApiErrorCode());

            jsapiEx = assertThrows(JetStreamApiException.class,
                () -> ctx.js.pullSubscribe(random(), new PullConsumerCreator()));
            assertEquals(10059, jsapiEx.getApiErrorCode());

            // JsSubNoMatchingStreamForSubject
            IllegalStateException ise = assertThrows(IllegalStateException.class,
                () -> ctx.js.pushSubscribe(random()));
            assertTrue(JsSubNoMatchingStreamForSubject.matches(ise));

            ise = assertThrows(IllegalStateException.class,
                () -> ctx.js.pullSubscribe(random()));
            assertTrue(JsSubNoMatchingStreamForSubject.matches(ise));

            // JsSubDispatcherNoHandlerCantReceiveMessages
            // you can have a handler without a dispatcher,
            // but you cannot have a dispatcher without a handler
            NatsDispatcher d = ctx.js.createDispatcher();
            SubscribeBehavior behavior = new SubscribeBehavior().dispatcher(d);

            ise = assertThrows(IllegalStateException.class,
                () -> ctx.js.pushSubscribe(ctx.stream, new PushConsumerCreator(), behavior));
            assertTrue(JsSubDispatcherNoHandlerCantReceiveMessages.matches(ise));

            ise = assertThrows(IllegalStateException.class,
                () -> ctx.js.pullSubscribe(ctx.stream, new PullConsumerCreator(), behavior));
            assertTrue(JsSubDispatcherNoHandlerCantReceiveMessages.matches(ise));
        });
    }

    private static void assertConfigConstruction(JetStreamSubscribeConfig config, ConsumerInfo consumerInfo, @Nullable NatsDispatcher dispatcher, boolean internalDispatcher, @Nullable AbstractOrderedConsumerCreator<?> orderedCreator, boolean isPull) {
        assertSame(consumerInfo, config.consumerInfo);
        assertSame(consumerInfo.getConsumerConfiguration(), config.consumerConf);
        assertSame(dispatcher, config.getDispatcher());
        assertEquals(internalDispatcher, config.internalDispatcher);
        assertSame(orderedCreator, config.orderedCreator);
        assertEquals(orderedCreator != null, config.isOrdered);
        assertEquals(orderedCreator == null ? null : orderedCreator.getNamePrefix(), config.orderedNamePrefix);
        assertEquals(!isPull, config.isPush);
        assertEquals(isPull, config.isPull);
    }

    @SuppressWarnings("Convert2MethodRef")
    @Test
    public void testJetStreamPushSubscribeBasics() throws Exception {
        runInShared((nc, ctx) -> {
            String data = random();
            jsPublish(ctx.js, ctx.subject(), data);

            // 1. pushSubscribe(ConsumerInfo consumerInfo)
            SubFunPushSync subfun1 = ci -> ctx.js.pushSubscribe(ci);

            // 1a. durable
            String name1a = random();
            PushConsumerCreator creator = new PushConsumerCreator().durable(name1a);
            ConsumerInfo consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPushSync(ctx, name1a, true, consumerInfo, data, subfun1);

            // 1b. not durable
            String name1b = random();
            creator = new PushConsumerCreator().name(name1b);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPushSync(ctx, name1b, false, consumerInfo, data, subfun1);

            // 1c. no name
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name1c = consumerInfo.getName();
            assertPushSync(ctx, name1c, false, consumerInfo, data, subfun1);

            // 2. pushSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler)
            SubFunPushAsync subfun2 = (ci, h) -> ctx.js.pushSubscribe(ci, h);
            // 2a. durable
            String name2a = random();
            creator = new PushConsumerCreator().durable(name2a);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPushAsync(ctx, name2a, true, null, consumerInfo, data, subfun2);

            // 2b. not durable
            String name2b = random();
            creator = new PushConsumerCreator().name(name2b);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPushAsync(ctx, name2b, false, null, consumerInfo, data, subfun2);

            // 2c. no name
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name2c = consumerInfo.getName();
            assertPushAsync(ctx, name2c, false, null, consumerInfo, data, subfun2);

            // 3. pushSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior)
            // 3a. sync
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name3a = consumerInfo.getName();
            SubscribeBehavior behavior3a = new SubscribeBehavior();
            assertPushSync(ctx, name3a, false, consumerInfo, data, ci -> ctx.js.pushSubscribe(ci, behavior3a));

            // 3b. async
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name3b = consumerInfo.getName();
            BasicsHandler basicsHandler3b = new BasicsHandler();
            SubscribeBehavior behavior3b = new SubscribeBehavior().handler(basicsHandler3b);
            assertPushAsync(ctx, name3b, false, basicsHandler3b, consumerInfo, data, (ci, h) -> ctx.js.pushSubscribe(ci, behavior3b));

            // 4. pushSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name4 = consumerInfo.getName();
            assertPushSync(ctx, name4, false, consumerInfo, data, ci -> ctx.js.pushSubscribe(ctx.stream, name4));

            // 5. pushSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException,
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name5 = consumerInfo.getName();
            assertPushAsync(ctx, name5, false, null, consumerInfo, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, name5, h));

            // 6. pushSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 6a. sync
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name6a = consumerInfo.getName();
            SubscribeBehavior behavior6a = new SubscribeBehavior();
            assertPushSync(ctx, name6a, false, consumerInfo, data, ci -> ctx.js.pushSubscribe(ctx.stream, name6a, behavior6a));

            // 6b. async
            creator = new PushConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name6b = consumerInfo.getName();
            BasicsHandler basicsHandler6b = new BasicsHandler();
            SubscribeBehavior behavior6b = new SubscribeBehavior().handler(basicsHandler6b);
            assertPushAsync(ctx, name6b, false, basicsHandler6b, consumerInfo, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, name6b, behavior6b));

            // 7. pushSubscribe(String subject) throws JetStreamException, InterruptedException
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.subject()));

            // 8. pushSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            BasicsHandler basicsHandler8 = new BasicsHandler();
            assertPushAsync(ctx, null, false, basicsHandler8, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.subject(), h));

            // 9. pushSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 9a. sync
            SubscribeBehavior behavior9a = new SubscribeBehavior();
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.subject(), behavior9a));

            // 9b. async
            BasicsHandler basicsHandler9b = new BasicsHandler();
            SubscribeBehavior behavior9b = new SubscribeBehavior().handler(basicsHandler9b);
            assertPushAsync(ctx, null, false, basicsHandler9b, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.subject(), behavior9b));

            // 10. pushSubscribe(String stream, PushConsumerCreator creator) throws JetStreamException, InterruptedException
            PushConsumerCreator creator10 = new PushConsumerCreator();
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.stream, creator10));

            // 11. pushSubscribe(String stream, PushConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            PushConsumerCreator creator11 = new PushConsumerCreator();
            assertPushAsync(ctx, null, false, null, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, creator11, h));

            // 12. pushSubscribe(String stream, PushConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 12a. sync
            PushConsumerCreator creator12a = new PushConsumerCreator();
            SubscribeBehavior behavior12a = new SubscribeBehavior();
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.stream, creator12a, behavior12a));

            // 12b. async
            BasicsHandler basicsHandler12b = new BasicsHandler();
            SubscribeBehavior behavior12b = new SubscribeBehavior().handler(basicsHandler12b);
            PushConsumerCreator creator12b = new PushConsumerCreator();
            assertPushAsync(ctx, null, false, basicsHandler12b, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, creator12b, behavior12b));

            // 13. pushSubscribe(String stream, PushOrderedConsumerCreator creator) throws JetStreamException, InterruptedException
            PushOrderedConsumerCreator creator13 = new PushOrderedConsumerCreator();
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.stream, creator13));

            // 14. pushSubscribe(String stream, PushOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            PushOrderedConsumerCreator creator14 = new PushOrderedConsumerCreator();
            assertPushAsync(ctx, null, false, null, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, creator14, h));

            // 15. pushSubscribe(String stream, PushOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 15a. sync
            PushOrderedConsumerCreator creator15a = new PushOrderedConsumerCreator();
            SubscribeBehavior behavior15a = new SubscribeBehavior();
            assertPushSync(ctx, null, false, null, data, ci -> ctx.js.pushSubscribe(ctx.stream, creator15a, behavior15a));

            // 15b. async
            BasicsHandler basicsHandler15b = new BasicsHandler();
            SubscribeBehavior behavior15b = new SubscribeBehavior().handler(basicsHandler15b);
            PushOrderedConsumerCreator creator15b = new PushOrderedConsumerCreator();
            assertPushAsync(ctx, null, false, basicsHandler15b, null, data, (ci, h) -> ctx.js.pushSubscribe(ctx.stream, creator15b, behavior15b));
        });
    }

    public interface SubFunPushSync {
        JetStreamPushSubscription createSub(ConsumerInfo consumerInfo) throws JetStreamException, InterruptedException;
    }

    public interface SubFunPushAsync {
        JetStreamPushSubscription createSub(ConsumerInfo consumerInfo, MessageHandler handler) throws JetStreamException, InterruptedException;
    }

    private static void assertPushSync(JetStreamTestingContext ctx, String name, boolean durable, ConsumerInfo ci, String data, SubFunPushSync fun) throws JetStreamException, InterruptedException {
        if (ci != null) {
            assertConsumer(ctx, true, name, durable, ci);
        }

        JetStreamPushSubscription sub = fun.createSub(ci);

        validateMessage(data, sub.nextMessage(DEFAULT_TIMEOUT_MS));

        ci = sub.getConsumerInfo();
        if (name == null) {
            name = ci.getName();
        }
        assertConsumer(ctx, false, name, durable, sub.getConsumerInfo());
    }

    private static void assertPushAsync(JetStreamTestingContext ctx, String name, boolean durable, BasicsHandler handler, ConsumerInfo ci, String data, SubFunPushAsync fun) throws JetStreamException, InterruptedException {
        if (ci != null) {
            assertConsumer(ctx, true, name, durable, ci);
        }

        if (handler == null) {
            handler = new BasicsHandler();
        }

        JetStreamPushSubscription sub = fun.createSub(ci, handler);

        assertTrue(handler.latch.await(1000, TimeUnit.MILLISECONDS));
        validateMessage(data, handler.messageRef.get());

        ci = sub.getConsumerInfo();
        if (name == null) {
            name = ci.getName();
        }
        assertConsumer(ctx, false, name, durable, sub.getConsumerInfo());
    }

    @SuppressWarnings("Convert2MethodRef")
    @Test
    public void testJetStreamPullSubscribeBasics() throws Exception {
        runInShared((nc, ctx) -> {
            String data = random();
            jsPublish(ctx.js, ctx.subject(), data);

            // 1. pullSubscribe(ConsumerInfo consumerInfo)
            SubFunPullSync subfun1 = ci -> ctx.js.pullSubscribe(ci);

            // 1a. durable
            String name1a = random();
            PullConsumerCreator creator = new PullConsumerCreator().durable(name1a);
            ConsumerInfo consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPullSync(ctx, name1a, true, consumerInfo, data, subfun1);

            // 1b. not durable
            String name1b = random();
            creator = new PullConsumerCreator().name(name1b);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPullSync(ctx, name1b, false, consumerInfo, data, subfun1);

            // 1c. no name
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name1c = consumerInfo.getName();
            assertPullSync(ctx, name1c, false, consumerInfo, data, subfun1);

            // 2. pullSubscribe(ConsumerInfo consumerInfo, MessageHandler messageHandler)
            SubFunPullAsync subfun2 = (ci, h) -> ctx.js.pullSubscribe(ci, h);
            // 2a. durable
            String name2a = random();
            creator = new PullConsumerCreator().durable(name2a);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPullAsync(ctx, name2a, true, null, consumerInfo, data, subfun2);

            // 2b. not durable
            String name2b = random();
            creator = new PullConsumerCreator().name(name2b);
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            assertPullAsync(ctx, name2b, false, null, consumerInfo, data, subfun2);

            // 2c. no name
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name2c = consumerInfo.getName();
            assertPullAsync(ctx, name2c, false, null, consumerInfo, data, subfun2);

            // 3. pullSubscribe(ConsumerInfo consumerInfo, SubscribeBehavior subscribeBehavior)
            // 3a. sync
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name3a = consumerInfo.getName();
            SubscribeBehavior behavior3a = new SubscribeBehavior();
            assertPullSync(ctx, name3a, false, consumerInfo, data, ci -> ctx.js.pullSubscribe(ci, behavior3a));

            // 3b. async
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name3b = consumerInfo.getName();
            BasicsHandler basicsHandler3b = new BasicsHandler();
            SubscribeBehavior behavior3b = new SubscribeBehavior().handler(basicsHandler3b);
            assertPullAsync(ctx, name3b, false, basicsHandler3b, consumerInfo, data, (ci, h) -> ctx.js.pullSubscribe(ci, behavior3b));

            // 4. pullSubscribe(String stream, String consumerName) throws JetStreamException, InterruptedException
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name4 = consumerInfo.getName();
            assertPullSync(ctx, name4, false, consumerInfo, data, ci -> ctx.js.pullSubscribe(ctx.stream, name4));

            // 5. pullSubscribe(String stream, String consumerName, MessageHandler messageHandler) throws JetStreamException,
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name5 = consumerInfo.getName();
            assertPullAsync(ctx, name5, false, null, consumerInfo, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, name5, h));

            // 6. pullSubscribe(String stream, String consumerName, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 6a. sync
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name6a = consumerInfo.getName();
            SubscribeBehavior behavior6a = new SubscribeBehavior();
            assertPullSync(ctx, name6a, false, consumerInfo, data, ci -> ctx.js.pullSubscribe(ctx.stream, name6a, behavior6a));

            // 6b. async
            creator = new PullConsumerCreator();
            consumerInfo = ctx.jsm.createConsumer(ctx.stream, creator);
            String name6b = consumerInfo.getName();
            BasicsHandler basicsHandler6b = new BasicsHandler();
            SubscribeBehavior behavior6b = new SubscribeBehavior().handler(basicsHandler6b);
            assertPullAsync(ctx, name6b, false, basicsHandler6b, consumerInfo, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, name6b, behavior6b));

            // 7. pullSubscribe(String subject) throws JetStreamException, InterruptedException
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.subject()));

            // 8. pullSubscribe(String subject, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            BasicsHandler basicsHandler8 = new BasicsHandler();
            assertPullAsync(ctx, null, false, basicsHandler8, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.subject(), h));

            // 9. pullSubscribe(String subject, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 9a. sync
            SubscribeBehavior behavior9a = new SubscribeBehavior();
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.subject(), behavior9a));

            // 9b. async
            BasicsHandler basicsHandler9b = new BasicsHandler();
            SubscribeBehavior behavior9b = new SubscribeBehavior().handler(basicsHandler9b);
            assertPullAsync(ctx, null, false, basicsHandler9b, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.subject(), behavior9b));

            // 10. pullSubscribe(String stream, PullConsumerCreator creator) throws JetStreamException, InterruptedException
            PullConsumerCreator creator10 = new PullConsumerCreator();
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.stream, creator10));

            // 11. pullSubscribe(String stream, PullConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            PullConsumerCreator creator11 = new PullConsumerCreator();
            assertPullAsync(ctx, null, false, null, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, creator11, h));

            // 12. pullSubscribe(String stream, PullConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 12a. sync
            PullConsumerCreator creator12a = new PullConsumerCreator();
            SubscribeBehavior behavior12a = new SubscribeBehavior();
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.stream, creator12a, behavior12a));

            // 12b. async
            BasicsHandler basicsHandler12b = new BasicsHandler();
            SubscribeBehavior behavior12b = new SubscribeBehavior().handler(basicsHandler12b);
            PullConsumerCreator creator12b = new PullConsumerCreator();
            assertPullAsync(ctx, null, false, basicsHandler12b, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, creator12b, behavior12b));

            // 13. pullSubscribe(String stream, PullOrderedConsumerCreator creator) throws JetStreamException, InterruptedException
            PullOrderedConsumerCreator creator13 = new PullOrderedConsumerCreator();
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.stream, creator13));

            // 14. pullSubscribe(String stream, PullOrderedConsumerCreator creator, MessageHandler messageHandler) throws JetStreamException, InterruptedException
            PullOrderedConsumerCreator creator14 = new PullOrderedConsumerCreator();
            assertPullAsync(ctx, null, false, null, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, creator14, h));

            // 15. pullSubscribe(String stream, PullOrderedConsumerCreator creator, SubscribeBehavior subscribeBehavior) throws JetStreamException, InterruptedException
            // 15a. sync
            PullOrderedConsumerCreator creator15a = new PullOrderedConsumerCreator();
            SubscribeBehavior behavior15a = new SubscribeBehavior();
            assertPullSync(ctx, null, false, null, data, ci -> ctx.js.pullSubscribe(ctx.stream, creator15a, behavior15a));

            // 15b. async
            BasicsHandler basicsHandler15b = new BasicsHandler();
            SubscribeBehavior behavior15b = new SubscribeBehavior().handler(basicsHandler15b);
            PullOrderedConsumerCreator creator15b = new PullOrderedConsumerCreator();
            assertPullAsync(ctx, null, false, basicsHandler15b, null, data, (ci, h) -> ctx.js.pullSubscribe(ctx.stream, creator15b, behavior15b));
        });
    }

    public interface SubFunPullSync {
        JetStreamPullSubscription createSub(ConsumerInfo consumerInfo) throws JetStreamException, InterruptedException;
    }

    public interface SubFunPullAsync {
        JetStreamPullSubscription createSub(ConsumerInfo consumerInfo, MessageHandler handler) throws JetStreamException, InterruptedException;
    }

    private static void assertPullSync(JetStreamTestingContext ctx, String name, boolean durable, ConsumerInfo ci, String data, SubFunPullSync fun) throws JetStreamException, InterruptedException {
        if (ci != null) {
            assertConsumer(ctx, true, name, durable, ci);
        }

        JetStreamPullSubscription sub = fun.createSub(ci);
        sub.pull(1);

        validateMessage(data, sub.nextMessage(DEFAULT_TIMEOUT_MS));

        ci = sub.getConsumerInfo();
        if (name == null) {
            name = ci.getName();
        }
        assertConsumer(ctx, false, name, durable, sub.getConsumerInfo());
    }

    private static void assertPullAsync(JetStreamTestingContext ctx, String name, boolean durable, BasicsHandler handler, ConsumerInfo ci, String data, SubFunPullAsync fun) throws JetStreamException, InterruptedException {
        if (ci != null) {
            assertConsumer(ctx, true, name, durable, ci);
        }

        if (handler == null) {
            handler = new BasicsHandler();
        }

        JetStreamPullSubscription sub = fun.createSub(ci, handler);
        sub.pull(1);

        assertTrue(handler.latch.await(1000, TimeUnit.MILLISECONDS));
        validateMessage(data, handler.messageRef.get());

        ci = sub.getConsumerInfo();
        if (name == null) {
            name = ci.getName();
        }
        assertConsumer(ctx, false, name, durable, sub.getConsumerInfo());
    }

    @Test
    public void testFilterSubject() throws Exception {
        runInShared((nc, ctx) -> {
            String subject = random();
            String subjectWild = subject + ".*";
            String subjectA = subject + ".A";
            String subjectB = subject + ".B";
            ctx.createOrReplaceStream(subjectWild);

            jsPublish(ctx.js, subjectA, 1);
            jsPublish(ctx.js, subjectB, 1);
            jsPublish(ctx.js, subjectA, 1);
            jsPublish(ctx.js, subjectB, 1);

            // subscribe to the wildcard
            PushConsumerCreator creator = new PushConsumerCreator()
                .filterSubjects(subjectWild)
                .ackPolicy(AckPolicy.None);
            JetStreamSubscription sub = ctx.js.pushSubscribe(ctx.stream, creator);

            Message m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectA, m.getSubject());
            assertEquals(1, m.metaData().streamSequence());
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectB, m.getSubject());
            assertEquals(2, m.metaData().streamSequence());
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectA, m.getSubject());
            assertEquals(3, m.metaData().streamSequence());
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectB, m.getSubject());
            assertEquals(4, m.metaData().streamSequence());
            m = sub.nextMessage(100); // push, all the messages have already come across the wire
            assertNull(m);

            // subscribe to A
            creator = new PushConsumerCreator()
                .filterSubjects(subjectA)
                .ackPolicy(AckPolicy.None);
            sub = ctx.js.pushSubscribe(ctx.stream, creator);

            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectA, m.getSubject());
            assertEquals(1, m.metaData().streamSequence());
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectA, m.getSubject());
            assertEquals(3, m.metaData().streamSequence());
            m = sub.nextMessage(100); // push, all the messages have already come across the wire
            assertNull(m);

            // subscribe to B
            creator = new PushConsumerCreator()
                .filterSubjects(subjectB)
                .ackPolicy(AckPolicy.None);
            sub = ctx.js.pushSubscribe(ctx.stream, creator);

            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectB, m.getSubject());
            assertEquals(2, m.metaData().streamSequence());
            m = sub.nextMessage(1000);
            assertNotNull(m);
            assertEquals(subjectB, m.getSubject());
            assertEquals(4, m.metaData().streamSequence());
            m = sub.nextMessage(100); // push, all the messages have already come across the wire
            assertNull(m);
        });
    }

    // ----------------------------------------------------------------------------------------------------

    private static class BasicsHandler implements MessageHandler {
        public final CountDownLatch latch = new CountDownLatch(1);
        public final AtomicReference<Message> messageRef = new AtomicReference<>();

        @Override
        public void onMessage(Message msg) throws InterruptedException {
            messageRef.set(msg);
            latch.countDown();
        }
    }

    private static void assertConsumer(JetStreamTestingContext ctx, boolean hasPending, @NonNull String name, boolean durable, ConsumerInfo ci) throws JetStreamException, InterruptedException {
        if (durable) {
            assertEquals(name, ci.getConsumerConfiguration().getDurable());
        }
        assertEquals(name, ci.getName());
        List<String> names = ctx.jsm.getConsumerNames(ctx.stream);
        assertTrue(names.contains(name));

        if (hasPending) {
            assertEquals(1, ci.getNumPending());
        }
        else {
            assertEquals(0, ci.getNumPending());
        }
        assertEquals(ctx.stream, ci.getStreamName());
    }

    private static void validateMessage(String data, Message m) {
        assertNotNull(m);
        assertEquals(data, new String(m.getData()));
        assertEquals(1, m.metaData().streamSequence());
    }
}
