package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.Statistics;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static io.synadia.client.utils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class NatsStatisticsTests extends TestBase {
    @Test
    public void testHumanReadableString() throws Exception {
        runInSharedOwnNc(optionsBuilder().turnOnAdvancedStats(), nc -> {
            Dispatcher d = nc.createDispatcher(msg -> nc.publish(msg.getReplyTo(), new byte[16]));
            String subject = random();
            d.subscribe(subject);

            nc.flush(500);
            Future<Message> incoming = nc.requestAsync(subject, new byte[8]);
            nc.flush(500);
            Message msg = incoming.get(500, TimeUnit.MILLISECONDS);

            String str = nc.getStatistics().toString();
            assertNotNull(msg);
            assertNotNull(str);
            assertTrue(str.length() > 0);
            assertTrue(str.contains("### NatsConnection ###"));
            assertTrue(str.contains("Socket Writes"));
        });
    }

    @Test
    public void testInOutOKRequestStats() throws Exception {
        runInSharedOwnNc(optionsBuilder().verbose(), nc -> {
            Dispatcher d = nc.createDispatcher(msg -> {
                NatsMessage m = NatsMessage.builder()
                    .subject(msg.getReplyTo())
                    .data("replyreplyreply!") // 16 bytes
                    .headers(new Headers().put("header", "reply"))
                    .build();
                nc.publish(m);
            });
            String subject = random();
            d.subscribe(subject);

            Message m = NatsMessage.builder()
                .subject(subject)
                .data("request!") // 8 bytes
                .headers(new Headers().put("header", "request"))
                .build();
            Future<Message> fRequest = nc.request(m);
            Message msg = fRequest.get(500, TimeUnit.MILLISECONDS);
            assertNotNull(msg);

            Statistics stats = nc.getStatistics();
            assertEquals(0, stats.getOutstandingRequests(), "outstanding");
            assertTrue(stats.getInBytes() > 200, "bytes in");
            assertTrue(stats.getOutBytes() > 400, "bytes out");
            assertEquals(2, stats.getInMsgs(), "messages in"); // reply & request
            assertEquals(6, stats.getOutMsgs(), "messages out"); // ping, sub, pub, msg, pub, msg
            assertEquals(5, stats.getOKs(), "oks"); //sub, pub, msg, pub, msg
        });
    }

    @Test
    public void testReadWriteAdvancedStatsEnabled() throws Exception {
        runInSharedOwnNc(optionsBuilder().verbose().turnOnAdvancedStats(), nc -> {
            Dispatcher d = nc.createDispatcher(msg -> {
                NatsMessage m = NatsMessage.builder()
                    .subject(msg.getReplyTo())
                    .data(new byte[16])
                    .headers(new Headers().put("header", "reply"))
                    .build();
                nc.publish(m);
            });
            String subject = random();
            d.subscribe(subject);

            Message m = NatsMessage.builder()
                .subject(subject)
                .data(new byte[8])
                .headers(new Headers().put("header", "request"))
                .build();
            Future<Message> incoming = nc.request(m);
            Message msg = incoming.get(500, TimeUnit.MILLISECONDS);

            assertNotNull(msg);

            // The read/write advanced stats are only exposed via toString, so assert on that
            Statistics stats = nc.getStatistics();
            String stringStats = stats.toString();
            assertTrue(stringStats.contains("Socket Reads"), "readStats count");
            assertTrue(stringStats.contains("Average Bytes Per Read"), "readStats average bytes");
            assertTrue(stringStats.contains("Min Bytes Per Read"), "readStats min bytes");
            assertTrue(stringStats.contains("Max Bytes Per Read"), "readStats max bytes");

            assertTrue(stringStats.contains("Socket Writes"), "writeStats count");
            assertTrue(stringStats.contains("Average Bytes Per Write"), "writeStats average bytes");
            assertTrue(stringStats.contains("Min Bytes Per Write"), "writeStats min bytes");
            assertTrue(stringStats.contains("Max Bytes Per Write"), "writeStats max bytes");
        });
    }

    @Test
    public void testReadWriteAdvancedStatsDisabled() throws Exception {
        runInSharedOwnNc(optionsBuilder().verbose(), nc -> {
            Dispatcher d = nc.createDispatcher(msg -> {
                NatsMessage m = NatsMessage.builder()
                    .subject(msg.getReplyTo())
                    .data(new byte[16])
                    .headers(new Headers().put("header", "reply"))
                    .build();
                nc.publish(m);
            });
            String subject = random();
            d.subscribe(subject);

            Message m = NatsMessage.builder()
                .subject(subject)
                .data(new byte[8])
                .headers(new Headers().put("header", "request"))
                .build();
            Future<Message> incoming = nc.request(m);
            Message msg = incoming.get(500, TimeUnit.MILLISECONDS);

            assertNotNull(msg);

            incoming = nc.request(m);
            incoming.get(500, TimeUnit.MILLISECONDS);

            assertNotNull(msg);

            incoming = nc.request(m);
            incoming.get(500, TimeUnit.MILLISECONDS);

            assertNotNull(msg);

            incoming = nc.request(m);
            incoming.get(500, TimeUnit.MILLISECONDS);

            assertNotNull(msg);

            // The read/write advanced stats are only exposed via toString, so assert on that
            Statistics stats = nc.getStatistics();
            String stringStats = stats.toString();
            assertFalse(stringStats.contains("Socket Reads"), "readStats count");
            assertFalse(stringStats.contains("Average Bytes Per Read"), "readStats average bytes");
            assertFalse(stringStats.contains("Min Bytes Per Read"), "readStats min bytes");
            assertFalse(stringStats.contains("Max Bytes Per Read"), "readStats max bytes");

            assertFalse(stringStats.contains("Socket Writes"), "writeStats count");
            assertFalse(stringStats.contains("Average Bytes Per Write"), "writeStats average bytes");
            assertFalse(stringStats.contains("Min Bytes Per Write"), "writeStats min bytes");
            assertFalse(stringStats.contains("Max Bytes Per Write"), "writeStats max bytes");
        });
    }

    @Test
    public void testOrphanDuplicateRepliesAdvancedStatsEnabled() throws Exception {
        runInSharedOwnNc(optionsBuilder().turnOnAdvancedStats(), nc -> {
            AtomicInteger requests = new AtomicInteger();
            MessageHandler handler = msg -> {
                requests.incrementAndGet();
                nc.publish(msg.getReplyTo(), null);
            };
            Dispatcher d1 = nc.createDispatcher(handler);
            Dispatcher d2 = nc.createDispatcher(handler);
            Dispatcher d3 = nc.createDispatcher(handler);
            Dispatcher d4 = nc.createDispatcher(msg -> {
                sleep(5000);
                handler.onMessage(msg);
            });
            String subject = random();
            d1.subscribe(subject);
            d2.subscribe(subject);
            d3.subscribe(subject);
            d4.subscribe(subject);

            Message reply = nc.request(subject, null, 2000);
            assertNotNull(reply);
            sleep(2000);
            assertEquals(3, requests.get());
            NatsStatistics stats = (NatsStatistics) nc.getStatistics();
            assertEquals(1, stats.getRepliesReceived());
            assertEquals(2, stats.getDuplicateRepliesReceived());
            assertEquals(0, stats.getOrphanRepliesReceived());

            sleep(3100);
            assertEquals(4, requests.get());
            stats = (NatsStatistics) nc.getStatistics();
            assertEquals(1, stats.getRepliesReceived());
            assertEquals(2, stats.getDuplicateRepliesReceived());
            assertEquals(1, stats.getOrphanRepliesReceived());

            String stringStats = stats.toString();
            assertTrue(stringStats.contains("Duplicate Replies Received"), "duplicate replies");
            assertTrue(stringStats.contains("Orphan Replies Received"), "orphan replies");
        });
    }

    @Test
    public void testOrphanDuplicateRepliesAdvancedStatsDisabled() throws Exception {
        runInSharedOwnNc(optionsBuilder(), nc -> {
            AtomicInteger requests = new AtomicInteger();
            MessageHandler handler = msg -> {
                requests.incrementAndGet();
                nc.publish(msg.getReplyTo(), null);
            };
            Dispatcher d1 = nc.createDispatcher(handler);
            Dispatcher d2 = nc.createDispatcher(handler);
            Dispatcher d3 = nc.createDispatcher(handler);
            Dispatcher d4 = nc.createDispatcher(msg -> {
                sleep(5000);
                handler.onMessage(msg);
            });
            String subject = random();
            d1.subscribe(subject);
            d2.subscribe(subject);
            d3.subscribe(subject);
            d4.subscribe(subject);

            Message reply = nc.request(subject, null, 2000);
            assertNotNull(reply);
            sleep(2000);
            assertEquals(3, requests.get());
            NatsStatistics stats = (NatsStatistics) nc.getStatistics();
            assertEquals(1, stats.getRepliesReceived());
            assertEquals(0, stats.getDuplicateRepliesReceived());
            assertEquals(0, stats.getOrphanRepliesReceived());

            sleep(3100);
            assertEquals(4, requests.get());
            stats = (NatsStatistics) nc.getStatistics();
            assertEquals(1, stats.getRepliesReceived());
            assertEquals(0, stats.getDuplicateRepliesReceived());
            assertEquals(0, stats.getOrphanRepliesReceived());

            String stringStats = stats.toString();
            assertFalse(stringStats.contains("Duplicate Replies Received"), "duplicate replies");
            assertFalse(stringStats.contains("Orphan Replies Received"), "orphan replies");
        });
    }
}
