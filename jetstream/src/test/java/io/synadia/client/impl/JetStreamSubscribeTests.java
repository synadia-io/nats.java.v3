package io.synadia.client.impl;

public class JetStreamSubscribeTests extends JetStreamTestBase {
//    @Test
//    public void testJetStreamSubscribe() throws Exception {
//        runInShared((nc, ctx) -> {
//            jsPublish(ctx.js, ctx.subject());
//
//            // default ephemeral subscription.
//            Subscription s = ctx.js.subscribe(ctx.subject());
//            Message m = s.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            List<String> names = ctx.jsm.getConsumerNames(ctx.stream);
//            assertEquals(1, names.size());
//
//            // default subscribe options // ephemeral subscription.
//            s = ctx.js.subscribe(ctx.subject(), PushSubscribeOptions.builder().build());
//            m = s.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            names = ctx.jsm.getConsumerNames(ctx.stream);
//            assertEquals(2, names.size());
//
//            // set the stream
//            String durable = random();
//            PushSubscribeOptions pso = PushSubscribeOptions.builder().stream(ctx.stream).durable(durable).build();
//            s = ctx.js.subscribe(ctx.subject(), pso);
//            m = s.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            names = ctx.jsm.getConsumerNames(ctx.stream);
//            assertEquals(3, names.size());
//
//            // coverage
//            Dispatcher dispatcher = nc.createDispatcher();
//            ctx.js.subscribe(ctx.subject());
//            ctx.js.subscribe(ctx.subject(), (PushSubscribeOptions) null);
//            ctx.js.subscribe(ctx.subject(), random(), null);
//            ctx.js.subscribe(ctx.subject(), dispatcher, mh -> {
//            }, false);
//            ctx.js.subscribe(ctx.subject(), dispatcher, mh -> {
//            }, false, null);
//            ctx.js.subscribe(ctx.subject(), random(), dispatcher, mh -> {
//            }, false, null);
//
//            // bind with w/o subject
//            durable = random();
//            String deliver = random();
//            ctx.jsm.addOrUpdateConsumer(ctx.stream,
//                builder()
//                    .durable(durable)
//                    .deliverSubject(deliver)
//                    .build());
//
//            PushSubscribeOptions psoBind = PushSubscribeOptions.bind(ctx.stream, durable);
//            unsubscribeEnsureNotBound(ctx.js.subscribe(null, psoBind));
//            unsubscribeEnsureNotBound(ctx.js.subscribe("", psoBind));
//            JetStreamSubscription sub = ctx.js.subscribe(null, dispatcher, mh -> {
//            }, false, psoBind);
//            unsubscribeEnsureNotBound(dispatcher, sub);
//            ctx.js.subscribe("", dispatcher, mh -> {
//            }, false, psoBind);
//
//            durable = random();
//            deliver = random();
//            String queue = random();
//            ctx.jsm.addOrUpdateConsumer(ctx.stream,
//                builder()
//                    .durable(durable)
//                    .deliverSubject(deliver)
//                    .deliverGroup(queue)
//                    .build());
//
//            psoBind = PushSubscribeOptions.bind(ctx.stream, durable);
//            unsubscribeEnsureNotBound(ctx.js.subscribe(null, queue, psoBind));
//            unsubscribeEnsureNotBound(ctx.js.subscribe("", queue, psoBind));
//            sub = ctx.js.subscribe(null, queue, dispatcher, mh -> {
//            }, false, psoBind);
//            unsubscribeEnsureNotBound(dispatcher, sub);
//            ctx.js.subscribe("", queue, dispatcher, mh -> {
//            }, false, psoBind);
//
//            String name = random();
//            ConsumerConfiguration cc = builder().name(name).build();
//            pso = PushSubscribeOptions.builder().configuration(cc).build();
//            sub = ctx.js.subscribe(ctx.subject(), pso);
//            m = sub.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            ConsumerInfo ci = sub.getConsumerInfo();
//            assertEquals(name, ci.getName());
//            assertEquals(name, ci.getConsumerConfiguration().getName());
//            assertNull(ci.getConsumerConfiguration().getDurable());
//
//            durable = random();
//            cc = builder().durable(durable).build();
//            pso = PushSubscribeOptions.builder().configuration(cc).build();
//            sub = ctx.js.subscribe(ctx.subject(), pso);
//            m = sub.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            ci = sub.getConsumerInfo();
//            assertEquals(durable, ci.getName());
//            assertEquals(durable, ci.getConsumerConfiguration().getName());
//            assertEquals(durable, ci.getConsumerConfiguration().getDurable());
//
//            String durName = random();
//            cc = builder().durable(durName).name(durName).build();
//            pso = PushSubscribeOptions.builder().configuration(cc).build();
//            sub = ctx.js.subscribe(ctx.subject(), pso);
//            m = sub.nextMessage(DEFAULT_TIMEOUT);
//            assertNotNull(m);
//            assertEquals(DATA, new String(m.getData()));
//            ci = sub.getConsumerInfo();
//            assertEquals(durName, ci.getName());
//            assertEquals(durName, ci.getConsumerConfiguration().getName());
//            assertEquals(durName, ci.getConsumerConfiguration().getDurable());
//
//            // test opt out
//            JetStreamOptions jso = JetStreamOptions.builder().optOut290ConsumerCreate(true).build();
//            JetStream jsOptOut = nc.jetStream(jso);
//            ConsumerConfiguration ccOptOut = builder().name(random()).build();
//            PushSubscribeOptions psoOptOut = PushSubscribeOptions.builder().configuration(ccOptOut).build();
//            assertClientError(JsConsumerCreate290NotAvailable, () -> jsOptOut.subscribe(ctx.subject(), psoOptOut));
//        });
//    }
}
