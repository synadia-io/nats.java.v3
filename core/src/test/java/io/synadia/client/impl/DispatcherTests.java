package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.NUID;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;


// Some tests are a bit tricky, and depend on the fact that the dispatcher
// uses a single queue, so the subject messages go through before
// the done message (or should) - wanted to note that somewhere

public class DispatcherTests extends TestBase {

    @Test
    public void testDispatcherSubscribingExceptions() throws Exception {
        // InvalidSubjectsAndQueueNames
        runInShared(nc -> {

            Dispatcher dx = nc.createDispatcher(m -> {
            });
            for (String bad : BAD_SUBJECTS_OR_QUEUES) {
                assertThrows(IllegalArgumentException.class, () -> nc.subscribe(bad));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe(bad));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe(bad, m -> {
                }));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe(bad, "q"));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe(bad, "q", m -> {
                }));
                assertThrows(IllegalArgumentException.class, () -> nc.subscribe("s", bad));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe("s", bad));
                assertThrows(IllegalArgumentException.class, () -> dx.subscribe("s", bad, m -> {
                }));
            }

            String subject = random();
            dx.subscribe(subject);

            // can't subscribe to empty subject -> subject, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe("", msg -> {
            }));

            // can't subscribe to null handler -> subject, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe(random(), (MessageHandler) null));

            // can't subscribe null subject -> subject, queue, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe(null, random(), msg -> {
            }));

            // can't subscribe empty subject -> subject, queue, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe("", random(), msg -> {
            }));

            // can't subscribe with null queue -> subject, queue, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe(random(), null, msg -> {
            }));

            // can't subscribe with empty queue -> subject, queue, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe(random(), "", msg -> {
            }));

            // can't subscribe with null handler -> subject, queue, handler
            assertThrows(IllegalArgumentException.class, () -> dx.subscribe(random(), random(), null));

            // can't unsubscribe with null subject
            assertThrows(IllegalArgumentException.class, () -> dx.unsubscribe((String) null));

            // can't unsubscribe with empty subject
            assertThrows(IllegalArgumentException.class, () -> dx.unsubscribe(""));

            nc.closeDispatcher(dx);

            // can't close if already closed
            assertThrows(IllegalArgumentException.class, () -> nc.closeDispatcher(dx));

            // can't unsubscribe if dispatcher is closed
            assertThrows(IllegalStateException.class, () -> dx.unsubscribe(subject));

            // can't subscribe if dispatcher is closed
            assertThrows(IllegalStateException.class, () -> dx.subscribe(random()));

            // If dispatcher was made without a default handler,
            // you must subscribe with a specific handler
            Dispatcher dNoHandler = nc.createDispatcher();
            dNoHandler.subscribe(random(), m -> {
            }); // This is fine
            IllegalStateException ise = assertThrows(IllegalStateException.class, () -> dNoHandler.subscribe(random()));
            assertTrue(ise.getMessage().contains("Dispatcher was made without a default handler."));

            nc.closeDispatcher(dNoHandler);
        });
    }

    @Test
    public void throwsOnCreateIfConnectionClosed() throws Exception {
        runInSharedOwnNc(nc -> {
            Dispatcher d = nc.createDispatcher(msg -> {});
            // close connection
            nc.close();

            // can't create if connection is closed
            assertThrows(IllegalStateException.class, () -> nc.createDispatcher(msg -> {}));

            // can't subscribe if connection is closed
            assertThrows(IllegalStateException.class, () -> d.subscribe(random()));

            // can't subscribe if connection is closed
            assertThrows(IllegalStateException.class, () -> d.subscribe(random()));

            // can't close dispatcher if connection is closed
            assertThrows(IllegalStateException.class, () -> nc.closeDispatcher(d));
        });
    }

    @Test
    public void testProperlyUnsubscribeBySubject() throws Exception {
        runInShared(nc -> {
            // MultipleSubscriptionsBySubject
            String subject1 = random();
            String subject2 = random();

            List<Integer> dflt = Collections.synchronizedList(new ArrayList<>());
            List<Integer> sub21 = Collections.synchronizedList(new ArrayList<>());
            List<Integer> sub22 = Collections.synchronizedList(new ArrayList<>());
            List<Integer> sub31 = Collections.synchronizedList(new ArrayList<>());
            List<Integer> sub32 = Collections.synchronizedList(new ArrayList<>());
            Dispatcher d1 = nc.createDispatcher(m -> dflt.add(getDataId(m)));
            d1.subscribe(subject1);
            d1.subscribe(subject1, m -> sub21.add(getDataId(m)));
            d1.subscribe(subject1, m -> sub22.add(getDataId(m)));
            d1.subscribe(subject2, m -> sub31.add(getDataId(m)));
            d1.subscribe(subject2, m -> sub32.add(getDataId(m)));

            nc.publish(subject1, "1".getBytes());
            nc.publish(subject2, "1".getBytes());
            Thread.sleep(1000);
            d1.unsubscribe(subject1);
            nc.publish(subject1, "2".getBytes());
            nc.publish(subject2, "2".getBytes());
            Thread.sleep(1000);

            assertTrue(dflt.contains(1));
            assertTrue(sub21.contains(1));
            assertTrue(sub22.contains(1));
            assertTrue(sub31.contains(1));
            assertTrue(sub32.contains(1));

            assertFalse(dflt.contains(2));
            assertFalse(sub21.contains(2));
            assertFalse(sub22.contains(2));
            assertTrue(sub31.contains(2));
            assertTrue(sub32.contains(2));
        });
    }

    private static int getDataId(Message m) {
        return Integer.parseInt(new String(m.getData()));
    }

    @Test
    public void testSingleMessage() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Message> msgFuture = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msgFuture::complete);

            String subject = random();
            d.subscribe(subject);
            nc.flush(500);// Get them all to the server

            nc.publish(subject, new byte[16]);

            Message msg = msgFuture.get(500, TimeUnit.MILLISECONDS);

            assertTrue(d.isActive());
            assertEquals(subject, msg.getSubject());
            assertNotNull(msg.getSubscription());
            assertNull(msg.getReplyTo());
            assertEquals(16, msg.getData().length);

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testDispatcherMessageContainsConnection() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Message> msgFuture = new CompletableFuture<>();
            final CompletableFuture<NatsConnection> connFuture = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                msgFuture.complete(msg);
                connFuture.complete(msg.getConnection());
            });

            String subject = random();
            d.subscribe(subject);
            nc.flush(5000);// Get them all to the server

            nc.publish(subject, new byte[16]);

            Message msg = msgFuture.get(5000, TimeUnit.MILLISECONDS);
            NatsConnection conn = connFuture.get(5000, TimeUnit.MILLISECONDS);

            assertTrue(d.isActive());
            assertEquals(subject, msg.getSubject());
            assertNotNull(msg.getSubscription());
            assertNull(msg.getReplyTo());
            assertEquals(16, msg.getData().length);
            assertSame(conn, nc);

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testMultiSubject() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Message> one = new CompletableFuture<>();
            final CompletableFuture<Message> two = new CompletableFuture<>();
            String subject1 = random();
            String subject2 = random();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(subject1)) {
                    one.complete(msg);
                }
                else if (msg.getSubject().equals(subject2)) {
                    two.complete(msg);
                }
            });

            d.subscribe(subject1);
            d.subscribe(subject2);
            nc.flush(500);// Get them all to the server

            nc.publish(subject1, new byte[16]);
            nc.publish(subject2, new byte[16]);

            Message msg = one.get(500, TimeUnit.MILLISECONDS);
            assertEquals(subject1, msg.getSubject());
            msg = two.get(500, TimeUnit.MILLISECONDS);
            assertEquals(subject2, msg.getSubject());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testMultiMessage() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> done = new CompletableFuture<>();
            int msgCount = 100;

            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals("done")) {
                    done.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                }
            });

            String subject = random();
            d.subscribe(subject);
            d.subscribe("done");
            nc.flush(1000); // wait for them to go through

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish("done", new byte[16]);
            nc.flush(1000); // wait for them to go through

            done.get(500, TimeUnit.MILLISECONDS);

            assertEquals(msgCount, q.size());
        });
    }

    @Test
    public void testClosedDispatcherBehavior() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fPhase1 = new CompletableFuture<>();
            final CompletableFuture<Boolean> fPhase2 = new CompletableFuture<>();

            final ConcurrentLinkedQueue<Message> received = new ConcurrentLinkedQueue<>();
            String subject = random();
            String phase1 = random();
            String phase2 = random();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(phase1)) {
                    fPhase1.complete(Boolean.TRUE);
                }
                else if (msg.getSubject().equals(phase2)) {
                    fPhase2.complete(Boolean.TRUE);
                }
                else {
                    received.add(msg);
                }
            });

            d.subscribe(subject);
            d.subscribe(phase1);
            d.subscribe(phase2);
            nc.flush(500);// Get them all to the server

            nc.publish(subject, new byte[16]);
            nc.publish(phase1, null);

            nc.flush(1000); // wait for them to go through
            fPhase1.get(200, TimeUnit.MILLISECONDS);

            assertEquals(1, received.size());

            nc.closeDispatcher(d);

            assertFalse(d.isActive());

            // This won't arrive
            nc.publish(phase2, new byte[16]);

            nc.flush(1000); // wait for them to go through
            assertThrows(TimeoutException.class, () -> fPhase2.get(200, TimeUnit.MILLISECONDS));
        });
    }

    @Test
    public void testQueueSubscribers() throws Exception {
        runInShared(nc -> {
            int msgs = 100;
            AtomicInteger received = new AtomicInteger();
            AtomicInteger sub1Count = new AtomicInteger();
            AtomicInteger sub2Count = new AtomicInteger();

            final CompletableFuture<Boolean> done1 = new CompletableFuture<>();
            final CompletableFuture<Boolean> done2 = new CompletableFuture<>();

            String subject = random();
            String done = random();
            String queue = random();

            Dispatcher d1 = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    done1.complete(Boolean.TRUE);
                }
                else {
                    sub1Count.incrementAndGet();
                    received.incrementAndGet();
                }
            });

            Dispatcher d2 = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    done2.complete(Boolean.TRUE);
                }
                else {
                    sub2Count.incrementAndGet();
                    received.incrementAndGet();
                }
            });

            d1.subscribe(subject, queue);
            d2.subscribe(subject, queue);
            d1.subscribe(done);
            d2.subscribe(done);
            nc.flush(500);

            for (int i = 0; i < msgs; i++) {
                nc.publish(subject, new byte[16]);
            }

            nc.publish(done, null);

            nc.flush(500);
            done1.get(500, TimeUnit.MILLISECONDS);
            done2.get(500, TimeUnit.MILLISECONDS);

            assertEquals(msgs, received.get());
            assertEquals(msgs, sub1Count.get() + sub2Count.get());

            nc.closeDispatcher(d1);
            nc.closeDispatcher(d2);
        });
    }

    @Test
    public void testV3AllowsUnsubSubFromDispatcher() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Message> msgFuture = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msgFuture::complete);

            String subject = random();
            d.subscribe(subject);
            nc.flush(500);

            nc.publish(subject, null);

            Message msg = msgFuture.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);
            msg.getSubscription().unsubscribe();
            nc.closeDispatcher(d);
            assertFalse(d.isActive());
        });
    }

    @Test
    public void testV3AllowsAutoUnsubSubFromDispatcher() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Message> msgFuture = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msgFuture::complete);

            String subject = random();
            NatsSubscription sub = d.subscribe(subject);
            nc.flush(500);// Get them all to the server
            sub.unsubscribe(1);

            nc.publish(subject, null);

            Message msg = msgFuture.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);

            nc.closeDispatcher(d);
            assertFalse(d.isActive());
        });
    }

    @Test
    public void testReSubscribeReplacesTheSid() throws Exception {
        runInShared(nc -> {
            Dispatcher d = nc.createDispatcher(null);

            String subject = random();
            LinkedBlockingQueue<Message> received = new LinkedBlockingQueue<>();
            NatsSubscription sub = d.subscribe(subject, received::add);
            nc.flush(500);

            nc.publish(subject, null);
            assertNotNull(received.poll(500, TimeUnit.MILLISECONDS));

            int sinkCount = nc.getSinkCount();

            String newSubject = random();
            sub.reSubscribe(newSubject);
            nc.flush(500);

            // the old sid is replaced, not left behind
            assertEquals(sinkCount, nc.getSinkCount());
            assertEquals(newSubject, sub.getSubject());

            // and the handler came across with it
            nc.publish(newSubject, null);
            Message msg = received.poll(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);
            assertEquals(newSubject, msg.getSubject());

            // nothing is delivered on the subject it moved off of
            nc.publish(subject, null);
            assertNull(received.poll(200, TimeUnit.MILLISECONDS));

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testPublishAndFlushFromCallback() throws Exception {
        runInShared(nc -> {
            String subject = random();

            long startCount = nc.getStatistics().getFlushCounter();

            final CompletableFuture<Message> msgFuture = new CompletableFuture<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                try {
                    nc.flush(1000);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                msgFuture.complete(msg);
            });

            d.subscribe(subject);
            nc.flush(500);// Get them all to the server

            nc.publish(subject, new byte[16]); // publish one to kick it off

            Message msg = msgFuture.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);

            long diffCount = nc.getStatistics().getFlushCounter() - startCount;
            assertEquals(2, diffCount);
            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testUnsub() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fPhase1 = new CompletableFuture<>();
            final CompletableFuture<Boolean> fPhase2 = new CompletableFuture<>();
            int msgCount = 10;

            String subject = random();
            String phase1 = random();
            String phase2 = random();

            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(phase1)) {
                    fPhase1.complete(Boolean.TRUE);
                }
                else if (msg.getSubject().equals(phase2)) {
                    fPhase2.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                }
            });

            d.subscribe(subject);
            d.subscribe(phase1);
            d.subscribe(phase2);
            nc.flush(1000);// Get them all to the server

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(phase1, new byte[16]);
            nc.flush(1000); // wait for them to go through

            fPhase1.get(5000, TimeUnit.MILLISECONDS);

            d.unsubscribe(subject);
            nc.flush(1000);// Get them all to the server

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(phase2, new byte[16]);
            nc.flush(1000); // wait for them to go through

            fPhase2.get(1000, TimeUnit.MILLISECONDS); // make sure we got them

            assertEquals(msgCount, q.size());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testAutoUnsub() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fPhase1 = new CompletableFuture<>();
            final CompletableFuture<Boolean> fPhase2 = new CompletableFuture<>();
            int msgCount = 100;

            String subject = random();
            String phase1 = random();
            String phase2 = random();

            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            NatsDispatcher d = (NatsDispatcher) nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(phase1)) {
                    fPhase1.complete(Boolean.TRUE);
                }
                else if (msg.getSubject().equals(phase2)) {
                    fPhase2.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                }
            });

            d.subscribe(subject);
            d.subscribe(phase1);
            d.subscribe(phase2);
            nc.flush(500);// Get them all to the server

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(phase1, new byte[16]);

            nc.flush(1000); // wait for them to go through
            fPhase1.get(1000, TimeUnit.MILLISECONDS); // make sure we got them

            assertEquals(msgCount, q.size());

            d.unsubscribe(subject, msgCount + 1);

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(phase2, new byte[16]);

            nc.flush(1000); // Wait for it all to get processed
            fPhase2.get(1000, TimeUnit.MILLISECONDS); // make sure we got them

            assertEquals(msgCount + 1, q.size());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testUnsubFromCallback() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();
            String subject = random();
            String done = random();

            final AtomicReference<Dispatcher> dispatcher = new AtomicReference<>();
            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            final Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    fDone.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                    dispatcher.get().unsubscribe(subject);
                }
            });

            dispatcher.set(d);

            d.subscribe(subject);
            d.subscribe(done);
            nc.flush(500);// Get them all to the server

            nc.publish(subject, new byte[16]);
            nc.publish(subject, new byte[16]);
            nc.publish(done, new byte[16]); // when we get this we know the others are dispatched
            nc.flush(1000); // Wait for the publish, or we will get multiples for sure
            fDone.get(200, TimeUnit.MILLISECONDS); // make sure we got them

            assertEquals(1, q.size());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testAutoUnsubFromCallback() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();

            String subject = random();
            String done = random();
            final AtomicReference<Dispatcher> dispatcher = new AtomicReference<>();
            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            final Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    fDone.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                    dispatcher.get().unsubscribe(subject, 2); // get 1 more, for a total of 2
                }
            });

            dispatcher.set(d);

            d.subscribe(subject);
            d.subscribe(done);
            nc.flush(1000);// Get them all to the server

            nc.publish(subject, new byte[16]);
            nc.publish(subject, new byte[16]);
            nc.publish(subject, new byte[16]);
            nc.publish(done, new byte[16]); // when we get this we know the others are dispatched
            nc.flush(1000); // Wait for the publish

            fDone.get(200, TimeUnit.MILLISECONDS); // make sure we got them

            assertEquals(2, q.size());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testCloseFromCallback() throws Exception {
        // custom connection since we must close it.
        runInSharedOwnNc(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();

            String subject = random();
            final Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(subject)) {
                    nc.close();
                    fDone.complete(Boolean.TRUE);
                }
            });

            d.subscribe(subject);
            sleep(500); // Making sure the "subscribe" has been registered on the server

            nc.publish(subject, new byte[16]);

            fDone.get(5000, TimeUnit.MILLISECONDS);
        });
    }

    @Test
    public void testDispatchHandlesExceptionInHandler() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();
            int msgCount = 100;

            String subject = random();
            String done = random();
            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    fDone.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                    throw new NumberFormatException();
                }
            });

            d.subscribe(subject);
            d.subscribe(done);
            nc.flush(500);// Get them all to the server

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(done, new byte[16]);

            nc.flush(1000); // wait for them to go through
            fDone.get(200, TimeUnit.MILLISECONDS);

            assertEquals(msgCount, q.size());

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testThrowOnBadInput() throws Exception {
        runInShared(nc -> {
            Dispatcher d = nc.createDispatcher(msg -> {
            });
            // Null Subject
            assertThrows(IllegalArgumentException.class, () -> d.subscribe(null));
            // Empty Subject
            assertThrows(IllegalArgumentException.class, () -> d.subscribe(""));
            // Empty Subject
            assertThrows(IllegalArgumentException.class, () -> d.subscribe(""));
            // Null Subject With Queue
            assertThrows(IllegalArgumentException.class, () -> d.subscribe(null, random()));
            // Empty Subject With Queue
            assertThrows(IllegalArgumentException.class, () -> d.subscribe("", random()));
            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testDoubleSubscribe() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();
            int msgCount = 100;

            String subject = random();
            String done = random();

            final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<>();
            Dispatcher d = nc.createDispatcher(msg -> {
                if (msg.getSubject().equals(done)) {
                    fDone.complete(Boolean.TRUE);
                }
                else {
                    q.add(msg);
                }
            });

            d.subscribe(subject);
            d.subscribe(subject);
            d.subscribe(subject);
            d.subscribe(done);
            nc.flush(5000); // wait for them to go through

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(done, new byte[16]);
            nc.flush(5000); // wait for them to go through

            fDone.get(5, TimeUnit.SECONDS);

            assertEquals(msgCount, q.size()); // Should only get one since all the extra subs do nothing??

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testDoubleSubscribeWithCustomHandler() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone = new CompletableFuture<>();
            int msgCount = 100;

            final AtomicInteger count = new AtomicInteger(0);
            Dispatcher d = nc.createDispatcher(msg -> {
            });

            String subject = random();
            String done = random();
            String queue = random();
            d.subscribe(subject, msg -> count.incrementAndGet());
            d.subscribe(subject, queue, msg -> count.incrementAndGet());
            d.subscribe(done, msg -> fDone.complete(Boolean.TRUE));

            nc.flush(5000); // wait for them to go through

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(done, new byte[16]);
            nc.flush(5000); // wait for them to go through

            fDone.get(5, TimeUnit.SECONDS);

            assertEquals(msgCount * 2, count.get()); // We should get 2x the messages because we subscribed 2 times.

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testDoubleSubscribeWithUnsubscribeAfterWithCustomHandler() throws Exception {
        runInShared(nc -> {
            final CompletableFuture<Boolean> fDone1 = new CompletableFuture<>();
            final CompletableFuture<Boolean> fDone2 = new CompletableFuture<>();
            int msgCount = 100;

            String subject = random();
            String done = random();
            final AtomicInteger count = new AtomicInteger(0);
            Dispatcher d = nc.createDispatcher(msg -> {
            });
            NatsSubscription s1 = d.subscribe(subject, msg -> count.incrementAndGet());
            NatsSubscription doneSub = d.subscribe(done, msg -> fDone1.complete(Boolean.TRUE));
            d.subscribe(subject, msg -> count.incrementAndGet());

            nc.flush(5000); // wait for the subs to go through

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(done, new byte[16]);
            nc.flush(5000); // wait for the messages to go through

            fDone1.get(5, TimeUnit.SECONDS);

            assertEquals(msgCount * 2, count.get()); // We should get 2x the messages because we subscribed 2 times.

            count.set(0);
            d.unsubscribe(s1);
            d.unsubscribe(doneSub);
            d.subscribe(done, msg -> fDone2.complete(Boolean.TRUE));
            nc.flush(5000); // wait for the unsub to go through

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }
            nc.publish(done, new byte[16]);
            nc.flush(5000); // wait for the messages to go through

            fDone2.get(5, TimeUnit.SECONDS);

            assertEquals(msgCount, count.get()); // We only have 1 active subscription, so we should only get msgCount.

            nc.closeDispatcher(d);
        });
    }

    @Test
    public void testDispatcherFactoryCoverage() throws Exception {
        runInSharedOwnNc(optionsBuilder().useDispatcherWithExecutor(), nc -> {
            CountDownLatch latch = new CountDownLatch(1);
            Dispatcher d = nc.createDispatcher(msg -> latch.countDown());
            assertInstanceOf(NatsDispatcherWithExecutor.class, d);
            String subject = NUID.nextGlobalSequence();
            d.subscribe(subject);
            nc.publish(subject, null);
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        });
    }
}
