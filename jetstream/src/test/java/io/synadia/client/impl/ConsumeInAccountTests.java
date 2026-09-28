package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.Options;
import io.synadia.client.api.*;
import io.synadia.client.utils.ConnectionUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reading a stream that lives in another account, both ways: a push subscription and a simplified
 * consume. The two arrive over different account exports, so each needs its own export in the
 * server config, and each test uses the config that carries only its own.
 * <ul>
 * <li>Push delivery goes to the client's inbox, which JetStream publishes inside the stream's
 *     account, so it rides the {@code _INBOX.>} <b>stream</b> export.</li>
 * <li>Pull delivery is the answer to a service request, so it rides the {@code $JS.API.>}
 *     <b>service</b> export, and needs {@code response_type: Stream} because the answer is more
 *     than one message.</li>
 * </ul>
 */
public class ConsumeInAccountTests extends JetStreamTestBase {

    private static final int COUNT = 5;
    private static final String PUSH_CONF = "account_push.conf";
    private static final String PULL_CONF = "account_pull.conf";
    private static final String PREFIX = "tar.api";

    // ----------------------------------------------------------------------------------------------------
    // Each mechanism works when its config carries it
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testPushReadsTheWholeStreamAcrossAccounts() throws Exception {
        inAccounts(PUSH_CONF, (jsSrc, jsTar, stream, subject) -> {
            List<String> push = readWithPush(jsTar, stream, subject);
            assertEquals(COUNT, push.size(), "push read " + push);
            assertEquals(expected(), push);
        });
    }

    @Test
    public void testConsumeReadsTheWholeStreamAcrossAccounts() throws Exception {
        inAccounts(PULL_CONF, (jsSrc, jsTar, stream, subject) -> {
            List<String> consume = readWithConsume(jsTar, stream, subject);
            assertEquals(COUNT, consume.size(), "consume read " + consume);
            assertEquals(expected(), consume);
        });
    }

    // ----------------------------------------------------------------------------------------------------
    // ...and does not work when it is the other config, which is what makes two configs necessary
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testConsumeNeedsAStreamResponseExport() throws Exception {
        // The push config exports the api with the default singleton response, so the pull request
        // is answered with exactly one message and the rest of the answer is dropped.
        inAccounts(PUSH_CONF, (jsSrc, jsTar, stream, subject) -> {
            List<String> consume = readWithConsume(jsTar, stream, subject);
            assertEquals(1, consume.size(), "consume read " + consume);
        });
    }

    @Test
    public void testPushNeedsAnInboxStreamExport() throws Exception {
        // The pull config has no inbox stream export, so nothing JetStream publishes to the
        // client's inbox crosses the account boundary and no message is delivered.
        inAccounts(PULL_CONF, (jsSrc, jsTar, stream, subject) -> {
            List<String> push = readWithPush(jsTar, stream, subject);
            assertEquals(0, push.size(), "push read " + push);
        });
    }

    // ----------------------------------------------------------------------------------------------------
    // support
    // ----------------------------------------------------------------------------------------------------
    @FunctionalInterface
    private interface AccountTest {
        void test(JetStream jsSrc, JetStream jsTar, String stream, String subject) throws Exception;
    }

    // Makes the stream and the messages in SOURCE, then hands over a JetStream for each account.
    private void inAccounts(String confFile, AccountTest accountTest) throws Exception {
        String stream = "acct-stream";
        String subject = "acct.subject";
        runInConfiguredServer(confFile, ts -> {
            Options optionsSrc = optionsBuilder(ts)
                .userInfo("src".toCharArray(), "spass".toCharArray()).build();
            Options optionsTar = optionsBuilder(ts)
                .userInfo("tar".toCharArray(), "tpass".toCharArray()).build();

            try (NatsConnection ncSrc = ConnectionUtils.managedConnect(optionsSrc);
                 NatsConnection ncTar = ConnectionUtils.managedConnect(optionsTar))
            {
                JetStreamManagement jsmSrc = new JetStreamManagement(ncSrc);
                jsmSrc.addStream(new StreamCreator(stream)
                    .storageType(StorageType.Memory)
                    .subjects(subject));

                JetStream jsSrc = new JetStream(ncSrc);
                for (int x = 1; x <= COUNT; x++) {
                    jsSrc.publish(subject, ("data" + x).getBytes());
                }

                // the target account reaches the source stream through the imported api prefix
                JetStream jsTar = new JetStream(ncTar, JetStreamOptions.builder().prefix(PREFIX).build());

                accountTest.test(jsSrc, jsTar, stream, subject);
            }
        });
    }

    private static List<String> expected() {
        List<String> list = new ArrayList<>();
        for (int seq = 1; seq <= COUNT; seq++) {
            list.add(seq + "|data" + seq);
        }
        return list;
    }

    private static String describe(Message m) {
        return m.metaData().streamSequence() + "|" + new String(m.getData());
    }

    private List<String> readWithPush(JetStream js, String stream, String subject) throws Exception {
        List<String> read = new ArrayList<>();
        JetStreamPushSubscription sub = js.pushSubscribe(stream,
            new PushConsumerCreator().deliverPolicy(DeliverPolicy.All).filterSubject(subject));
        try {
            Message m = sub.nextMessage(2000);
            while (m != null && read.size() < COUNT) {
                read.add(describe(m));
                m = sub.nextMessage(2000);
            }
        }
        finally {
            sub.unsubscribe();
        }
        return read;
    }

    private List<String> readWithConsume(JetStream js, String stream, String subject) throws Exception {
        List<String> read = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(COUNT);
        StreamContext streamContext = js.getStreamContext(stream);
        OrderedConsumerContext occ = streamContext.createOrderedConsumer(
            new PullOrderedConsumerCreator().deliverPolicy(DeliverPolicy.All).filterSubjects(subject));
        MessageHandler handler = m -> {
            synchronized (read) {
                read.add(describe(m));
            }
            latch.countDown();
        };
        try (MessageConsumer mc = occ.consume(handler)) {
            //noinspection ResultOfMethodCallIgnored
            latch.await(3000, TimeUnit.MILLISECONDS);
        }
        synchronized (read) {
            return new ArrayList<>(read);
        }
    }
}
