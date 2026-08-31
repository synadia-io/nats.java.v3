package io.synadia.client;

import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.NatsSubscription;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.CompletableFuture;

import static io.synadia.client.utils.ConnectionUtils.standardConnect;
import static io.synadia.client.utils.TestBase.*;
import static org.junit.jupiter.api.Assertions.*;

public class SubscribeTests {

    @Test
    public void testCreateInbox() throws Exception {
        runInShared(nc -> {
            HashSet<String> check = new HashSet<>();
            for (int i=0; i < 100; i++) {
                String inbox = nc.createInbox();
                assertFalse(check.contains(inbox));
                check.add(inbox);
            }
        });
    }

    @Test
    public void testSingleMessage() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L);

            assertTrue(sub.isActive());
            assertNotNull(msg);
            assertEquals(subject, msg.getSubject());
            assertEquals(sub, msg.getSubscription());
            assertNull(msg.getReplyTo());
            assertEquals(16, msg.getData().length);
        });
    }

    @Test
    public void testMessageFromSubscriptionContainsConnection() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L);

            assertTrue(sub.isActive());
            assertNotNull(msg);
            assertEquals(subject, msg.getSubject());
            assertEquals(sub, msg.getSubscription());
            assertNull(msg.getReplyTo());
            assertEquals(16, msg.getData().length);
            assertSame(msg.getConnection(), nc);
        });
    }

    @Test
    public void testTabInProtocolLine() throws Exception {
        CompletableFuture<Boolean> gotSub = new CompletableFuture<>();
        CompletableFuture<Boolean> sendMsg = new CompletableFuture<>();

        NatsServerProtocolMock.Customizer receiveMessageCustomizer = (ts, r,w) -> {
            String subLine;
            
            // System.out.println("*** Mock Server @" + ts.getPort() + " waiting for SUB ...");
            try {
                subLine = r.readLine();
            } catch(Exception e) {
                gotSub.cancel(true);
                return;
            }

            if (subLine.startsWith("SUB")) {
                gotSub.complete(Boolean.TRUE);
            }

            String[] parts = subLine.split("\\s");
            String subject = parts[1];
            int subId = Integer.parseInt(parts[2]);

            try {
                sendMsg.get();
            } catch (Exception e) {
                //keep going
            }

            w.write("MSG\t"+subject+"\t"+subId+"\t0\r\n\r\n");
            w.flush();
        };

        try (NatsServerProtocolMock mockTs = new NatsServerProtocolMock(receiveMessageCustomizer)) {
            try (NatsConnection nc = standardConnect(mockTs)) {
                String subject = random();
                NatsSubscription sub = nc.subscribe(subject);

                gotSub.get();
                sendMsg.complete(Boolean.TRUE);

                Message msg = sub.nextMessageWaitForever();

                assertTrue(sub.isActive());
                assertNotNull(msg);
                assertEquals(subject, msg.getSubject());
                assertEquals(sub, msg.getSubscription());
                assertNull(msg.getReplyTo());
                assertEquals(0, msg.getData().length);
            }
        }
    }

    @Test
    public void testMultiMessage() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.publish(subject, new byte[16]);
            nc.publish(subject, new byte[16]);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L);

            assertNotNull(msg);
            assertEquals(subject, msg.getSubject());
            assertEquals(sub, msg.getSubscription());
            assertNull(msg.getReplyTo());
            assertEquals(16, msg.getData().length);
            msg = sub.nextMessage(100L); // coverage for nextMessage(millis)
            assertNotNull(msg);
            msg = sub.nextMessage(100L);
            assertNotNull(msg);
            msg = sub.nextMessage(100L); // coverage for nextMessage(millis)
            assertNull(msg);
        });
    }

    @Test
    public void testQueueSubscribers() throws Exception {
        runInShared(nc -> {
            int msgs = 100;
            int received = 0;
            int sub1Count = 0;
            int sub2Count = 0;
            Message msg;

            String subject = random();
            String queue = random();
            NatsSubscription sub1 = nc.subscribe(subject, queue);
            NatsSubscription sub2 = nc.subscribe(subject, queue);

            for (int i = 0; i < msgs; i++) {
                nc.publish(subject, new byte[16]);
            }

            nc.flush(200);// Get them all to the server

            for (int i = 0; i < msgs; i++) {
                msg = sub1.nextMessageNoWait();

                if (msg != null) {
                    assertEquals(subject, msg.getSubject());
                    assertNull(msg.getReplyTo());
                    assertEquals(16, msg.getData().length);
                    received++;
                    sub1Count++;
                }
            }

            for (int i = 0; i < msgs; i++) {
                msg = sub2.nextMessageNoWait();

                if (msg != null) {
                    assertEquals(subject, msg.getSubject());
                    assertNull(msg.getReplyTo());
                    assertEquals(16, msg.getData().length);
                    received++;
                    sub2Count++;
                }
            }

            assertEquals(msgs, received);
            assertEquals(msgs, sub1Count + sub2Count);
        });
    }

    @Test
    public void testUnsubscribe() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L);
            assertNotNull(msg);

            sub.unsubscribe();
            assertFalse(sub.isActive());
            assertThrows(IllegalStateException.class, () -> sub.nextMessage(500L));
        });
    }

    @Test
    public void testAutoUnsubscribe() throws Exception {
        runInShared(nc -> {
            String subject = random();
            Subscription sub = nc.subscribe(subject).unsubscribe(1);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L); // should get 1
            assertNotNull(msg);

            assertThrows(IllegalStateException.class, () -> sub.nextMessage(500L));
        });
    }

    @Test
    public void testMultiAutoUnsubscribe() throws Exception {
        runInShared(nc -> {
            String subject = random();
            int msgCount = 10;
            Subscription sub = nc.subscribe(subject).unsubscribe(msgCount);

            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }

            Message msg;
            for (int i = 0; i < msgCount; i++) {
                msg = sub.nextMessage(500L); // should get 1
                assertNotNull(msg);
            }

            assertThrows(IllegalStateException.class, () -> sub.nextMessage(500L));
        });
    }

    @Test
    public void testOnlyOneUnsubscribe() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            sub.unsubscribe();
            assertThrows(IllegalStateException.class, sub::unsubscribe);
        });
    }

    @Test
    public void testOnlyOneAutoUnsubscribe() throws Exception {
        runInShared(nc -> {
            String subject = random();

            Subscription sub = nc.subscribe(subject).unsubscribe(1);
            nc.publish(subject, new byte[16]);

            Message msg = sub.nextMessage(500L); // should get 1
            assertNotNull(msg);

            assertThrows(IllegalStateException.class, sub::unsubscribe);
        });
    }

    @Test
    public void testUnsubscribeInAnotherThread() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            new Thread(sub::unsubscribe).start();
            assertThrows(IllegalStateException.class, () -> sub.nextMessage(5000L));
        });
    }

    @Test
    public void testAutoUnsubAfterMaxIsReached() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);

            int msgCount = 10;
            for (int i = 0; i < msgCount; i++) {
                nc.publish(subject, new byte[16]);
            }

            nc.flush(1000); // Slow things down so we have time to unsub

            for (int i = 0; i < msgCount; i++) {
                sub.nextMessageNoWait();
            }

            sub.unsubscribe(msgCount); // we already have that many

            assertThrows(IllegalStateException.class, () -> sub.nextMessage(5000L));
        });
    }

    @Test
    public void testSubscribesThatException() throws Exception {
        // own nc b/c we will close
        runInShared(nc -> {

            // null subject
            //noinspection DataFlowIssue
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(null));

            // empty subject
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(""));

            // null queue
            //noinspection DataFlowIssue
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(random(), null));

            // empty queue
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(random(), ""));

            // null subject with queue
            //noinspection DataFlowIssue
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(null, random()));

            // empty subject with queue
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe("", random()));
        });
    }

    @Test
    public void testSubscribesThatExceptionAfterClose() throws Exception {
        // own nc b/c we will close
        runInSharedOwnNc(nc -> {
            NatsSubscription sub = nc.subscribe(random());
            nc.close();

            /// can't subscribe when closed
            assertThrows(IllegalStateException.class, () -> nc.subscribe(random()));

            /// can't unsubscribe when closed
            assertThrows(IllegalStateException.class, sub::unsubscribe);

            /// can't auto unsubscribe when closed
            assertThrows(IllegalStateException.class, () -> sub.unsubscribe(1));
        });
    }

    @Test
    public void testUnsubscribeWhileWaiting() throws Exception {
        runInShared(nc -> {
            String subject = random();
            NatsSubscription sub = nc.subscribe(subject);
            nc.flush(1000);

            new Thread(() -> {
                try {
                    Thread.sleep(100);
                }
                catch (Exception e) { /* ignored */ }
                sub.unsubscribe();
            }).start();

            assertThrows(IllegalStateException.class, () -> sub.nextMessage(5000L));
        });
    }

    @Test
    public void testSubjectValidationTypeNone() throws Exception {
        OptionsBuilder optionsBuilder = new OptionsBuilder().subjectValidationType(SubjectValidationType.None);
        runInSharedOwnNc(optionsBuilder, nc -> {
            nc.subscribe(STAR_SEGMENT);
            nc.subscribe(GT_NOT_LAST_SEGMENT);
            nc.subscribe(GT_LAST_SEGMENT);
            nc.subscribe(STARTS_WITH_DOT);
            nc.subscribe(ENDS_WITH_DOT);
            nc.subscribe(ENDS_WITH_DOT_SPACE);
            nc.subscribe(ENDS_WITH_CR);
            nc.subscribe(ENDS_WITH_LF);
            nc.subscribe(ENDS_WITH_TAB);
            nc.subscribe(STAR_NOT_SEGMENT);
            nc.subscribe(GT_NOT_SEGMENT);
            nc.subscribe(EMPTY_SEGMENT);
            nc.subscribe(PLAIN);
            nc.subscribe(HAS_SPACE);
            nc.subscribe(STARTS_SPACE);
            nc.subscribe(ENDS_SPACE);
            nc.subscribe(HAS_PRINTABLE);
            nc.subscribe(HAS_DOT);
            nc.subscribe(HAS_DASH);
            nc.subscribe(HAS_UNDER);
            nc.subscribe(HAS_DOLLAR);
            nc.subscribe(HAS_CR);
            nc.subscribe(HAS_LF);
            nc.subscribe(HAS_TAB);
            nc.subscribe(HAS_LOW);
            nc.subscribe(HAS_127);
            nc.subscribe(HAS_FWD_SLASH);
            nc.subscribe(HAS_BACK_SLASH);
            nc.subscribe(HAS_EQUALS);
            nc.subscribe(HAS_TIC);
        });
    }

    @Test
    public void testSubjectValidationTypeLenient() throws Exception {
        OptionsBuilder optionsBuilder = new OptionsBuilder().subjectValidationType(SubjectValidationType.Lenient);
        runInSharedOwnNc(optionsBuilder, nc -> {
            nc.subscribe(STAR_SEGMENT);
            nc.subscribe(GT_NOT_LAST_SEGMENT);
            nc.subscribe(GT_LAST_SEGMENT);
            nc.subscribe(STARTS_WITH_DOT);
            nc.subscribe(ENDS_WITH_DOT);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_DOT_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_CR));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_LF));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_TAB));
            nc.subscribe(STAR_NOT_SEGMENT);
            nc.subscribe(GT_NOT_SEGMENT);
            nc.subscribe(EMPTY_SEGMENT);
            nc.subscribe(PLAIN);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(STARTS_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_SPACE));
            nc.subscribe(HAS_PRINTABLE);
            nc.subscribe(HAS_DOT);
            nc.subscribe(HAS_DASH);
            nc.subscribe(HAS_UNDER);
            nc.subscribe(HAS_DOLLAR);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_CR));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_LF));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_TAB));
            nc.subscribe(HAS_LOW);
            nc.subscribe(HAS_127);
            nc.subscribe(HAS_FWD_SLASH);
            nc.subscribe(HAS_BACK_SLASH);
            nc.subscribe(HAS_EQUALS);
            nc.subscribe(HAS_TIC);
        });
    }

    @Test
    public void testSubjectValidationTypeStrict() throws Exception {
        OptionsBuilder optionsBuilder = new OptionsBuilder().subjectValidationType(SubjectValidationType.Strict);
        runInSharedOwnNc(optionsBuilder, nc -> {
            nc.subscribe(STAR_SEGMENT);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(GT_NOT_LAST_SEGMENT));
            nc.subscribe(GT_LAST_SEGMENT);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(STARTS_WITH_DOT));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_DOT));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_DOT_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_CR));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_LF));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_WITH_TAB));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(STAR_NOT_SEGMENT));
            assertThrows(IllegalArgumentException.class, () ->nc.subscribe(GT_NOT_SEGMENT));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(EMPTY_SEGMENT));
            nc.subscribe(PLAIN);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(STARTS_SPACE));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(ENDS_SPACE));
            nc.subscribe(HAS_PRINTABLE);
            nc.subscribe(HAS_DOT);
            nc.subscribe(HAS_DASH);
            nc.subscribe(HAS_UNDER);
            nc.subscribe(HAS_DOLLAR);
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_CR));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_LF));
            assertThrows(IllegalArgumentException.class, () -> nc.subscribe(HAS_TAB));
            nc.subscribe(HAS_LOW);
            nc.subscribe(HAS_127);
            nc.subscribe(HAS_FWD_SLASH);
            nc.subscribe(HAS_BACK_SLASH);
            nc.subscribe(HAS_EQUALS);
            nc.subscribe(HAS_TIC);
        });
    }
}
