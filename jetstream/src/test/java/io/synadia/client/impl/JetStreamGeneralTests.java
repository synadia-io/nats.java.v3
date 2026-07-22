package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Options;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.PublishAck;
import io.synadia.client.api.StorageType;
import io.synadia.client.api.StreamCreator;
import io.synadia.client.utils.ConnectionUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.OptionsUtils.optionsBuilder;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamGeneralTests extends JetStreamTestBase {

    @Test
    public void testJetStreamGeneral() throws Exception {
        runInShared((nc, ctx) -> {
            ctx.jsm.getAccountStatistics(); // another management

            String subject = ctx.subject();

            // distinct headers for each variation that carries them
            String hk3 = random();
            String hv3 = random();
            Headers h3 = new Headers().put(hk3, hv3);

            String hk4 = random();
            String hv4 = random();
            Headers h4 = new Headers().put(hk4, hv4);

            String hk5 = random();
            String hv5 = random();
            Headers h5 = new Headers().put(hk5, hv5);

            // all the publish variations, new data for each, never a null body.
            // it's a new stream, so the stream sequence starts at 1 and increments.
            PublishAck pa1 = ctx.js.publish(subject, dataBytes(1));      // publish(subject, byte[])
            PublishAck pa2 = ctx.js.publish(subject, data(2));          // publish(subject, String)
            PublishAck pa3 = ctx.js.publish(subject, h3, dataBytes(3)); // publish(subject, headers, byte[])
            PublishAck pa4 = ctx.js.publish(subject, h4, data(4));      // publish(subject, headers, String)
            PublishAck pa5 = ctx.js.publish(NatsMessage.builder()       // publish(message)
                .subject(subject).headers(h5).data(dataBytes(5)).build());

            assertEquals(1, pa1.getSequenceNumber());
            assertEquals(2, pa2.getSequenceNumber());
            assertEquals(3, pa3.getSequenceNumber());
            assertEquals(4, pa4.getSequenceNumber());
            assertEquals(5, pa5.getSequenceNumber());

            // read them all back, validating the data and headers that were sent
            JetStreamPushSubscription sub = ctx.js.pushSubscribe(subject);
            nc.flush(1000);
            List<Message> msgs = readMessagesAck(sub, 1000L, 5);
            assertEquals(5, msgs.size());

            assertEquals(data(1), new String(msgs.get(0).getData()));
            assertFalse(msgs.get(0).hasHeaders());

            assertEquals(data(2), new String(msgs.get(1).getData()));
            assertFalse(msgs.get(1).hasHeaders());

            assertEquals(data(3), new String(msgs.get(2).getData()));
            assertEquals(hv3, msgs.get(2).getHeaders().getFirst(hk3));

            assertEquals(data(4), new String(msgs.get(3).getData()));
            assertEquals(hv4, msgs.get(3).getHeaders().getFirst(hk4));

            assertEquals(data(5), new String(msgs.get(4).getData()));
            assertEquals(hv5, msgs.get(4).getHeaders().getFirst(hk5));
        });
    }

    @Test
    public void testJetNotEnabled() throws Exception {
        runInOwnServer(nc -> {
            // get normal context, try to do an operation
            // get management context, try to do an operation
            JetStreamManagement jsm = new JetStreamManagement(nc);
            assertThrows(JetStreamException.class, jsm::getAccountStatistics);
        });
    }

    @Test
    public void testCoverageIncludingExceptions() throws Exception {
        runInSharedOwnNc(nc -> {
            JetStreamManagement jsm = new JetStreamManagement(nc);
            JetStream js = new JetStream(nc);
            jsm = js.jetStreamManagement();
            js = jsm.jetStream();

            JetStreamOptions jso = JetStreamOptions.builder().build();
            new JetStreamManagement(nc, jso).jetStream();
            new JetStream(nc, jso);

            nc.close();
            assertThrows(IllegalStateException.class, () -> new JetStreamManagement(nc));
            assertThrows(IllegalStateException.class, () -> new JetStream(nc));

        });
    }

    @Test
    public void testPrefix() throws Exception {
        String prefix = "tar.api";
        String streamMadeBySrc = "stream-made-by-src";
        String streamMadeByTar = "stream-made-by-tar";
        String subjectMadeBySrc = "sub-made-by.src";
        String subjectMadeByTar = "sub-made-by.tar";
        runInConfiguredServer("js_prefix.conf", ts -> {
            Options optionsSrc = optionsBuilder(ts)
                .userInfo("src".toCharArray(), "spass".toCharArray()).build();

            Options optionsTar = optionsBuilder(ts)
                .userInfo("tar".toCharArray(), "tpass".toCharArray()).build();

            try (NatsConnection ncSrc = ConnectionUtils.managedConnect(optionsSrc);
                 NatsConnection ncTar = ConnectionUtils.managedConnect(optionsTar)
            ) {
                // Setup JetStreamOptions. SOURCE does not need prefix
                JetStreamOptions jsoSrc = JetStreamOptions.builder().build();
                JetStreamOptions jsoTar = JetStreamOptions.builder().prefix(prefix).build();

                // Management api allows us to create streams
                JetStreamManagement jsmSrc = new JetStreamManagement(ncSrc, jsoSrc);
                JetStreamManagement jsmTar = new JetStreamManagement(ncTar, jsoTar);

                // add streams with both account
                StreamCreator scSrc = new StreamCreator(streamMadeBySrc)
                    .storageType(StorageType.Memory)
                    .subjects(subjectMadeBySrc);
                jsmSrc.addStream(scSrc);

                StreamCreator scTar = new StreamCreator(streamMadeByTar)
                    .storageType(StorageType.Memory)
                    .subjects(subjectMadeByTar);
                jsmTar.addStream(scTar);

                JetStream jsSrc = new JetStream(ncSrc, jsoSrc);
                JetStream jsTar = new JetStream(ncTar, jsoTar);

                jsSrc.publish(subjectMadeBySrc, "src-src".getBytes());
                jsSrc.publish(subjectMadeByTar, "src-tar".getBytes());
                jsTar.publish(subjectMadeBySrc, "tar-src".getBytes());
                jsTar.publish(subjectMadeByTar, "tar-tar".getBytes());

                // subscribe and read messages
                readPrefixMessages(ncSrc, jsSrc, subjectMadeBySrc, "src");
                readPrefixMessages(ncSrc, jsSrc, subjectMadeByTar, "tar");
                readPrefixMessages(ncTar, jsTar, subjectMadeBySrc, "src");
                readPrefixMessages(ncTar, jsTar, subjectMadeByTar, "tar");
            }
        });
    }

    private void readPrefixMessages(NatsConnection nc, JetStream js, String subject, String dest) throws InterruptedException, JetStreamException, TimeoutException {
        JetStreamPushSubscription sub = js.pushSubscribe(subject);
        nc.flush(1000);
        List<Message> msgs = readMessagesAck(sub);
        assertEquals(2, msgs.size());
        assertEquals(subject, msgs.get(0).getSubject());
        assertEquals(subject, msgs.get(1).getSubject());

        assertEquals("src-" + dest, new String(msgs.get(0).getData()));
        assertEquals("tar-" + dest, new String(msgs.get(1).getData()));
    }

    @Test
    public void testInternalLookupConsumerInfoCoverage() throws Exception {
        runInShared((nc, ctx) -> {
            // - consumer not found
            // - stream does not exist
            JetStream js = new JetStream(nc);
            JetStreamPushSubscription sub = js.pushSubscribe(ctx.subject());
            String cname = sub.getConsumerInfo().getName();

            assertNotNull(ctx.js.strictGetConsumerInfo(ctx.stream, cname));
            assertThrows(JetStreamApiException.class,
                () -> ctx.js.strictGetConsumerInfo(ctx.stream, random()));
            assertThrows(JetStreamApiException.class,
                () -> ctx.js.strictGetConsumerInfo(random(), random()));

            assertNotNull(ctx.js.lenientGetConsumerInfo(ctx.stream, cname));
            assertNull(ctx.js.lenientGetConsumerInfo(ctx.stream, random()));
            assertThrows(JetStreamApiException.class,
                () -> ctx.js.lenientGetConsumerInfo(random(), random()));
        });
    }
}
