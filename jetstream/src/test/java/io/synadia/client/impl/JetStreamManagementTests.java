package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.synadia.client.Message;
import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.testutils.Listener;
import io.synadia.client.testutils.VersionUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.nats.json.DateTimeUtils.ZONE_ID_GMT;
import static io.synadia.client.OptionsConstants.DEFAULT_INBOX_PREFIX;
import static io.synadia.client.testutils.NatsConstants.*;
import static io.synadia.client.testutils.ThreadUtils.sleep;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamManagementTests extends JetStreamTestBase {

    @Test
    public void testStreamCreate() throws Exception {
        long now = ZonedDateTime.now().toEpochSecond();

        runInSharedCustom((nc, ctx) -> {
            String subject0 = ctx.subject(0);
            String subject1 = ctx.subject(1);

            StreamInfo si = ctx.createOrReplaceStream(ctx.scBuilder(2));

            assertNotNull(si.getStreamState().toString()); // coverage
            assertTrue(now <= si.getCreateTime().toEpochSecond());

            assertNotNull(si.getConfiguration());
            StreamConfiguration sc = si.getConfiguration();
            assertEquals(ctx.stream, sc.getName());

            assertEquals(2, sc.getSubjects().size());
            assertEquals(subject0, sc.getSubjects().get(0));
            assertEquals(subject1, sc.getSubjects().get(1));
            assertTrue(subject0.compareTo(subject1) != 0); // coverage

            assertEquals(RetentionPolicy.Limits, sc.getRetentionPolicy());
            assertEquals(DiscardPolicy.Old, sc.getDiscardPolicy());
            assertEquals(StorageType.Memory, sc.getStorageType());

            assertNotNull(si.getStreamState());
            assertEquals(-1, sc.getMaxConsumers());
            assertEquals(-1, sc.getMaxMessages());
            assertEquals(-1, sc.getMaxBytes());
            assertEquals(-1, sc.getMaxMessageSize());
            assertEquals(1, sc.getReplicas());

            assertEquals(Duration.ZERO, sc.getMaxAge());
            assertEquals(Duration.ofSeconds(120), sc.getDuplicateWindow());
            assertFalse(sc.getNoAck());
            assertNull(sc.getTemplateOwner());

            StreamState ss = si.getStreamState();
            assertEquals(0, ss.getMessageCount());
            assertEquals(0, ss.getByteCount());
            assertEquals(0, ss.getFirstSequence());
            assertEquals(0, ss.getLastSequence());
            assertEquals(0, ss.getConsumerCount());
        });
    }

    @Test
    public void testStreamCreate210() throws Exception {
        runInSharedCustom(VersionUtils::atLeast2_10, (nc, ctx) -> {
            StreamInfo si = ctx.createOrReplaceStream(ctx.scBuilder(1)
                .firstSequence(42));
            assertNotNull(si.getTimestamp());
            assertEquals(42, si.getConfiguration().getFirstSequence());
            PublishAck pa = ctx.js.publish(ctx.subject(), (String)null);
            assertEquals(42, pa.getSequenceNumber());
        });
    }

    @Test
    public void testStreamMetadata() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            Map<String, String> metaData = new HashMap<>(); metaData.put(META_KEY, META_VALUE);
            StreamInfo si = ctx.createOrReplaceStream(ctx.scBuilder(1).metadata(metaData));
            assertNotNull(si.getConfiguration());
            assertMetaData(si.getConfiguration().getMetadata());
        });
    }

    @Test
    public void testStreamCreateWithNoSubject() throws Exception {
        long now = ZonedDateTime.now().toEpochSecond();
        runInSharedCustom((nc, ctx) -> {
            StreamInfo si = ctx.addStream(ctx.scBuilder().subjects());
            assertTrue(now <= si.getCreateTime().toEpochSecond());

            StreamConfiguration sc = si.getConfiguration();
            assertEquals(ctx.stream, sc.getName());

            assertNotNull(sc.getSubjects());
            assertEquals(1, sc.getSubjects().size());
            assertEquals(ctx.stream, sc.getSubjects().getFirst());

            assertEquals(RetentionPolicy.Limits, sc.getRetentionPolicy());
            assertEquals(DiscardPolicy.Old, sc.getDiscardPolicy());
            assertEquals(StorageType.Memory, sc.getStorageType());

            assertNotNull(si.getConfiguration());
            assertNotNull(si.getStreamState());
            assertEquals(-1, sc.getMaxConsumers());
            assertEquals(-1, sc.getMaxMessages());
            assertEquals(-1, sc.getMaxBytes());
            assertEquals(-1, sc.getMaxMessageSize());
            assertEquals(1, sc.getReplicas());

            assertEquals(Duration.ZERO, sc.getMaxAge());
            assertEquals(Duration.ofSeconds(120), sc.getDuplicateWindow());
            assertFalse(sc.getNoAck());
            assertNull(sc.getTemplateOwner());

            StreamState ss = si.getStreamState();
            assertEquals(0, ss.getMessageCount());
            assertEquals(0, ss.getByteCount());
            assertEquals(0, ss.getFirstSequence());
            assertEquals(0, ss.getLastSequence());
            assertEquals(0, ss.getConsumerCount());
        });
    }

    @Test
    public void testUpdateStream() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            ctx.createOrReplaceStream(2);
            String subject0 = ctx.subject(0);
            String subject1 = ctx.subject(1);

            StreamConfiguration sc = ctx.si.getConfiguration();
            assertNotNull(sc);
            assertEquals(ctx.stream, sc.getName());
            assertNotNull(sc.getSubjects());
            assertEquals(2, sc.getSubjects().size());
            assertEquals(subject0, sc.getSubjects().get(0));
            assertEquals(subject1, sc.getSubjects().get(1));
            assertEquals(-1, sc.getMaxMessages());
            assertEquals(-1, sc.getMaxBytes());
            assertEquals(-1, sc.getMaxMessageSize());
            assertEquals(Duration.ZERO, sc.getMaxAge());
            assertEquals(StorageType.Memory, sc.getStorageType());
            assertEquals(DiscardPolicy.Old, sc.getDiscardPolicy());
            assertEquals(1, sc.getReplicas());
            assertFalse(sc.getNoAck());
            assertEquals(Duration.ofMinutes(2), sc.getDuplicateWindow());
            assertNull(sc.getTemplateOwner());

            StreamInfo si = ctx.jsm.updateStream(ctx.scBuilder(3)
                .maxMessages(42)
                .maxBytes(43)
                .maxMessageSize(44)
                .maxAge(Duration.ofDays(100))
                .discardPolicy(DiscardPolicy.New)
                .noAck(true)
                .duplicateWindow(Duration.ofMinutes(3))
                .maxMessagesPerSubject(45));
            assertNotNull(si);
            String subject2 = ctx.subject(2);

            sc = si.getConfiguration();
            assertNotNull(sc);
            assertEquals(ctx.stream, sc.getName());
            assertNotNull(sc.getSubjects());
            assertEquals(3, sc.getSubjects().size());
            assertEquals(subject0, sc.getSubjects().get(0));
            assertEquals(subject1, sc.getSubjects().get(1));
            assertEquals(subject2, sc.getSubjects().get(2));
            assertEquals(42, sc.getMaxMessages());
            assertEquals(43, sc.getMaxBytes());
            assertEquals(44, sc.getMaxMessageSize());
            assertEquals(45, sc.getMaxMessagesPerSubject());
            assertEquals(Duration.ofDays(100), sc.getMaxAge());
            assertEquals(StorageType.Memory, sc.getStorageType());
            assertEquals(DiscardPolicy.New, sc.getDiscardPolicy());
            assertEquals(1, sc.getReplicas());
            assertTrue(sc.getNoAck());
            assertEquals(Duration.ofMinutes(3), sc.getDuplicateWindow());
            assertNull(sc.getTemplateOwner());

            // allowed to change Allow Direct
            ctx.jsm.updateStream(new StreamCreator(sc).allowDirect(true));
            ctx.jsm.updateStream(new StreamCreator(sc).allowDirect(false));

            // allowed to change Mirror Direct
            ctx.createOrReplaceStream(new StreamCreator(sc).mirrorDirect(false));
            ctx.jsm.updateStream(new StreamCreator(sc).mirrorDirect(true));
            ctx.jsm.updateStream(new StreamCreator(sc).mirrorDirect(false));
        });
    }

    @Test
    public void testStreamExceptions() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            //noinspection DataFlowIssue addStream parameter annotated as non null
            assertThrows(IllegalArgumentException.class, () -> ctx.jsm.addStream(null));

            // stream isn't even created yet
            assertStatus(10059, assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(ctx.stream, 1)));

            StreamCreator sc = ctx.scBuilder(1)
                .description(random());
            ctx.createOrReplaceStream(sc);

            // no messages yet
            assertStatus(10037, assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(ctx.stream, 1)));

            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).subjects(random()))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).description(random()))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).retentionPolicy(RetentionPolicy.Interest))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).retentionPolicy(RetentionPolicy.WorkQueue))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).compressionOption(CompressionOption.S2))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).maxConsumers(1))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).maxMessages(1))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).maxMessagesPerSubject(1))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).maxAge(Duration.ofSeconds(1L)))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).maxMessageSize(1))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).storageType(StorageType.File))));

            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).noAck(true))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).discardPolicy(DiscardPolicy.New))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).duplicateWindow(Duration.ofSeconds(1L)))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).allowRollup(true))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).allowDirect(true))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).denyDelete(true))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).denyPurge(true))));
            assertStatus(10058, assertThrows(JetStreamApiException.class, () -> ctx.jsm.addStream(new StreamCreator(sc).firstSequence(100))));
        });
    }

    @Test
    public void testUpdateStreamInvalids() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            //noinspection DataFlowIssue updateStream is specified non null
            assertThrows(IllegalArgumentException.class, () -> ctx.jsm.updateStream(null));

            StreamCreator sc = ctx.scBuilder(2);
            // cannot update non-existent stream
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.updateStream(sc));

            // add the stream
            ctx.createOrReplaceStream(sc);

            // cannot change storage type
            StreamCreator scMemToFile = ctx.scBuilder(2)
                .storageType(StorageType.File);
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.updateStream(scMemToFile));

            if (nc.getServerInfo().isOlderThanVersion("2.14")) {
                // cannot change MaxConsumers
                StreamCreator scMaxCon = ctx.scBuilder(2)
                    .maxConsumers(2);
                assertThrows(JetStreamApiException.class, () -> ctx.jsm.updateStream(scMaxCon));
            }

            StreamCreator scReten = ctx.scBuilder(2)
                .retentionPolicy(RetentionPolicy.Interest);
            if (nc.getServerInfo().isOlderThanVersion("2.10")) {
                // cannot change RetentionPolicy
                assertThrows(JetStreamApiException.class, () -> ctx.jsm.updateStream(scReten));
            }
            else {
                ctx.jsm.updateStream(scReten);
            }
        });
    }

    @Test
    public void testGetStreamInfo() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getStreamInfo(ctx.stream));

            String[] subjects = new String[6];
            String subjectIx5 = random();
            for (int x = 0; x < 5; x++) {
                subjects[x] = random() + x + 1;
            }
            subjects[5] = subjectIx5 + ".>";

            ctx.createOrReplaceStream(subjects);

            StreamInfo si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(ctx.stream, si.getConfiguration().getName());
            assertEquals(0, si.getStreamState().getSubjectCount());
            assertEquals(0, si.getStreamState().getDeletedCount());
            assertEquals(0, si.getStreamState().getDeleted().size());
            assertEquals(0, si.getStreamState().getSubjectMap().size());

            if (nc.getServerInfo().isOlderThanVersion("2.10")) {
                assertNull(si.getTimestamp());
            }
            else {
                assertNotNull(si.getTimestamp());
            }
            assertEquals(1, si.getConfiguration().getFirstSequence());

            List<PublishAck> packs = new ArrayList<>();
            for (int x = 0; x < 5; x++) {
                jsPublish(ctx.js, subjects[x], x + 1);
                PublishAck pa = jsPublish(ctx.js, subjects[x], data(x + 2));
                packs.add(pa);
                ctx.jsm.deleteMessage(ctx.stream, pa.getSequenceNumber());
            }
            jsPublish(ctx.js, subjectIx5 + ".bar", 6);

            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(ctx.stream, si.getConfiguration().getName());
            assertEquals(6, si.getStreamState().getSubjectCount());
            assertEquals(5, si.getStreamState().getDeletedCount());
            assertEquals(0, si.getStreamState().getDeleted().size());
            assertEquals(0, si.getStreamState().getSubjectMap().size());

            si = ctx.jsm.getStreamInfo(ctx.stream, StreamInfoOptions.builder().allSubjects().deletedDetails().build());
            assertEquals(ctx.stream, si.getConfiguration().getName());
            assertEquals(6, si.getStreamState().getSubjectCount());
            List<Subject> list = si.getStreamState().getSubjects();
            assertNotNull(list);
            assertEquals(5, si.getStreamState().getDeletedCount());
            assertNotNull(si.getStreamState().getDeleted());
            assertEquals(5, si.getStreamState().getDeleted().size());
            assertEquals(6, list.size());
            Map<String, Subject> map = new HashMap<>();
            for (Subject su : list) {
                map.put(su.getName(), su);
            }
            for (int x = 0; x < 5; x++) {
                Subject s = map.get(subjects[x]);
                assertNotNull(s);
                assertEquals(x + 1, s.getCount());
            }
            Subject sf = map.get(subjectIx5 + ".bar");
            assertNotNull(sf);
            assertEquals(6, sf.getCount());
            assertNotNull(si.getStreamState().getSubjectMap());
            assertEquals(6, si.getStreamState().getSubjectMap().size());

            for (PublishAck pa : packs) {
                assertTrue(si.getStreamState().getDeleted().contains(pa.getSequenceNumber()));
            }

            jsPublish(ctx.js, subjectIx5 + ".baz", 2);
            sleep(100);

            si = ctx.jsm.getStreamInfo(ctx.stream, StreamInfoOptions.builder().filterSubjects(subjectIx5 + ".>").deletedDetails().build());
            assertEquals(7, si.getStreamState().getSubjectCount());
            list = si.getStreamState().getSubjects();
            assertNotNull(list);
            assertEquals(2, list.size());
            map = new HashMap<>();
            for (Subject su : list) {
                map.put(su.getName(), su);
            }
            Subject s = map.get(subjectIx5 + ".bar");
            assertNotNull(s);
            assertEquals(6, s.getCount());
            s = map.get(subjectIx5 + ".baz");
            assertNotNull(s);
            assertEquals(2, s.getCount());

            si = ctx.jsm.getStreamInfo(ctx.stream, StreamInfoOptions.builder().filterSubjects(subjects[4]).build());
            list = si.getStreamState().getSubjects();
            assertNotNull(list);
            assertEquals(1, list.size());
            s = list.get(0);
            assertEquals(subjects[4], s.getName());
            assertEquals(5, s.getCount());
        });
    }

    @Test
    public void testGetStreamInfoOrNamesPaginationFilter() throws Exception {
        runInOwnJsServer((nc, jsm, js) -> {
            // getStreams pages at 256
            // getStreamNames pages at 1024
            String prefix = random();
            addStreams(jsm, prefix, 300, 0, "x256");

            List<StreamInfo> list = jsm.getStreams();
            assertEquals(300, list.size());

            List<String> names = jsm.getStreamNames();
            assertEquals(300, names.size());

            addStreams(jsm, prefix, 1100, 300, "x1024");

            list = jsm.getStreams();
            assertEquals(1400, list.size());

            names = jsm.getStreamNames();
            assertEquals(1400, names.size());

            list = jsm.getStreams("*.x256.*");
            assertEquals(300, list.size());

            names = jsm.getStreamNames("*.x256.*");
            assertEquals(300, names.size());

            list = jsm.getStreams("*.x1024.*");
            assertEquals(1100, list.size());

            names = jsm.getStreamNames("*.x1024.*");
            assertEquals(1100, names.size());
        });
    }

    private void addStreams(JetStreamManagement jsm, String prefix, int count, int adj, String div) throws IOException, JetStreamApiException {
        for (int x = 0; x < count; x++) {
            createMemoryStream(jsm, prefix + "-" + (x + adj), "sub" + (x + adj) + "." + div + ".*");
        }
    }

    @Test
    public void testGetStreamNamesBySubjectFilter() throws Exception {
        runInOwnJsServer((nc, jsm, js) -> {
            String stream1 = random();
            String stream2 = random();
            String stream3 = random();
            String stream4 = random();
            createMemoryStream(jsm, stream1, "foo");
            createMemoryStream(jsm, stream2, "bar");
            createMemoryStream(jsm, stream3, "a.a");
            createMemoryStream(jsm, stream4, "a.b");

            List<String> list = jsm.getStreamNames("*");
            assertStreamNameList(list, stream1, stream2);

            list = jsm.getStreamNames(">");
            assertStreamNameList(list, stream1, stream2, stream3, stream4);

            list = jsm.getStreamNames("*.*");
            assertStreamNameList(list, stream3, stream4);

            list = jsm.getStreamNames("a.>");
            assertStreamNameList(list, stream3, stream4);

            list = jsm.getStreamNames("a.*");
            assertStreamNameList(list, stream3, stream4);

            list = jsm.getStreamNames("foo");
            assertStreamNameList(list, stream1);

            list = jsm.getStreamNames("a.a");
            assertStreamNameList(list, stream3);

            list = jsm.getStreamNames("nomatch");
            assertStreamNameList(list);
        });
    }

    private void assertStreamNameList(List<String> list, String... streams) {
        assertNotNull(list);
        assertEquals(streams.length, list.size());
        for (String s : streams) {
            assertTrue(list.contains(s));
        }
    }

    @Test
    public void testPushCreatorAutomaticallyAddsDeliverSubject() throws Exception {
        runInShared((nc, ctx) -> {
            PushConsumerCreator cc = new PushConsumerCreator(ctx.stream)
                .durable(ctx.consumerName());
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(cc);
            assertNotNull(ci.getConsumerConfiguration().getDeliverSubject());
            assertTrue(ci.getConsumerConfiguration().getDeliverSubject().contains(DEFAULT_INBOX_PREFIX));
        });
    }

    @Test
    public void testDeleteStream() throws Exception {
        runInShared((nc, ctx) -> {
            JetStreamApiException jsapiEx =
                assertThrows(JetStreamApiException.class, () -> ctx.jsm.deleteStream(random()));
            assertEquals(10059, jsapiEx.getApiErrorCode());

            assertNotNull(ctx.jsm.getStreamInfo(ctx.stream));
            assertTrue(ctx.deleteStream());

            jsapiEx = assertThrows(JetStreamApiException.class, () -> ctx.jsm.getStreamInfo(ctx.stream));
            assertEquals(10059, jsapiEx.getApiErrorCode());

            jsapiEx = assertThrows(JetStreamApiException.class, ctx::deleteStream);
            assertEquals(10059, jsapiEx.getApiErrorCode());
        });
    }

    @Test
    public void testPurgeStreamAndOptions() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            // invalid to have both keep and seq
            assertThrows(IllegalArgumentException.class,
                () -> PurgeOptions.builder().keep(1).sequence(1).build());

            // error to purge a stream that does not exist
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.purgeStream(random()));

            ctx.createOrReplaceStream(2);
            StreamInfo si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(0, si.getStreamState().getMessageCount());

            jsPublish(nc, ctx.subject(0), 10);
            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(10, si.getStreamState().getMessageCount());

            PurgeOptions options = PurgeOptions.builder().keep(7).build();
            PurgeResponse pr = ctx.jsm.purgeStream(ctx.stream, options);
            assertTrue(pr.isSuccess());
            assertEquals(3, pr.getPurged());

            options = PurgeOptions.builder().sequence(9).build();
            pr = ctx.jsm.purgeStream(ctx.stream, options);
            assertTrue(pr.isSuccess());
            assertEquals(5, pr.getPurged());
            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(2, si.getStreamState().getMessageCount());

            pr = ctx.jsm.purgeStream(ctx.stream);
            assertTrue(pr.isSuccess());
            assertEquals(2, pr.getPurged());
            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(0, si.getStreamState().getMessageCount());

            jsPublish(nc, ctx.subject(0), 10);
            jsPublish(nc, ctx.subject(1), 10);
            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(20, si.getStreamState().getMessageCount());
            ctx.jsm.purgeStream(ctx.stream, PurgeOptions.subject(ctx.subject(0)));
            si = ctx.jsm.getStreamInfo(ctx.stream);
            assertEquals(10, si.getStreamState().getMessageCount());

            options = PurgeOptions.builder().subject(ctx.subject(0)).sequence(1).build();
            assertEquals(ctx.subject(0), options.getSubject());
            assertEquals(1, options.getSequence());

            options = PurgeOptions.builder().subject(ctx.subject(0)).keep(2).build();
            assertEquals(2, options.getKeep());
        });
    }

    @Test
    public void testAddDeleteConsumerPart1() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            String subject = random();
            ctx.createOrReplaceStream(subjectGt(subject));

            List<ConsumerInfo> list = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(0, list.size());

            // durable and name can both be null

            PushConsumerCreator pushCreator = new PushConsumerCreator(ctx.stream);
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(pushCreator);
            assertNotNull(ci.getName());

            PullConsumerCreator pullCreator = new PullConsumerCreator(ctx.stream);
            ci = ctx.jsm.addOrUpdateConsumer(pullCreator);
            assertNotNull(ci.getName());

            // threshold can be set for durable
            pushCreator = new PushConsumerCreator(ctx.stream)
                .durable(random())
                .inactiveThreshold(10000);
            ci = ctx.jsm.addOrUpdateConsumer(pushCreator);
            assertNotNull(ci.getName());
            Duration duration = ci.getConsumerConfiguration().getInactiveThreshold();
            assertNotNull(duration);
            assertEquals(10000, duration.toMillis());
        });
    }

    @Test
    public void testAddDeleteConsumerPart2() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            boolean atLeast2dot9 = nc.getServerInfo().isSameOrNewerThanVersion("2.9");
            String subject = random();
            ctx.createOrReplaceStream(subjectGt(subject));

            // with and w/o deliver subject for push/pull
            String dur1 = random();
            String dur = dur1;
            addConsumer(ctx.jsm, atLeast2dot9, dur, null, null, new PushConsumerCreator(ctx.stream)
                .durable(dur));

            String dur2 = random();
            dur = dur2;
            String deliver = random();
            addConsumer(ctx.jsm, atLeast2dot9, dur, deliver, null, new PushConsumerCreator(ctx.stream)
                .durable(dur)
                .deliverSubject(deliver));

            // test delete here
            List<String> consumers = ctx.jsm.getConsumerNames(ctx.stream);
            assertEquals(2, consumers.size());
            assertTrue(ctx.jsm.deleteConsumer(ctx.stream, dur1));
            consumers = ctx.jsm.getConsumerNames(ctx.stream);
            assertEquals(1, consumers.size());
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.deleteConsumer(ctx.stream, dur1));
            assertTrue(ctx.jsm.deleteConsumer(ctx.stream, dur2));
            consumers = ctx.jsm.getConsumerNames(ctx.stream);
            assertEquals(0, consumers.size());

            // some testing of new name
            if (atLeast2dot9) {
                dur = random();
                addConsumer(ctx.jsm, true, dur, null, null, new PullConsumerCreator(ctx.stream)
                    .durable(dur)
                    .name(dur));

                dur = random();
                deliver = random();
                addConsumer(ctx.jsm, true, dur, deliver, null, new PushConsumerCreator(ctx.stream)
                    .durable(dur)
                    .name(dur)
                    .deliverSubject(deliver));

                dur = random();
                addConsumer(ctx.jsm, true, dur, null, ">", new PullConsumerCreator(ctx.stream)
                    .durable(dur)
                    .filterSubject(">"));

                dur = random();
                deliver = random();
                addConsumer(ctx.jsm, true, dur, deliver, ">", new PushConsumerCreator(ctx.stream)
                    .durable(dur)
                    .deliverSubject(deliver)
                    .filterSubject(">"));

                dur = random();
                addConsumer(ctx.jsm, true, dur, null, subjectGt(subject), new PullConsumerCreator(ctx.stream)
                    .durable(dur)
                    .filterSubject(subjectGt(subject)));

                dur = random();
                deliver = random();
                addConsumer(ctx.jsm, true, dur, deliver, subjectGt(subject), new PushConsumerCreator(ctx.stream)
                    .durable(dur)
                    .deliverSubject(deliver)
                    .filterSubject(subjectGt(subject)));

                dur = random();
                addConsumer(ctx.jsm, true, dur, null, subjectDot(subject, "foo"), new PullConsumerCreator(ctx.stream)
                    .durable(dur)
                    .filterSubject(subjectDot(subject, "foo")));

                dur = random();
                deliver = random();
                addConsumer(ctx.jsm, true, dur, deliver, subjectDot(subject, "foo"), new PushConsumerCreator(ctx.stream)
                    .durable(dur)
                    .deliverSubject(deliver)
                    .filterSubject(subjectDot(subject, "foo")));
            }
        });
    }

    @Test
    public void testAddPausedConsumer() throws Exception {
        runInShared(VersionUtils::atLeast2_11, (nc, ctx) -> {
            List<ConsumerInfo> list = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(0, list.size());

            ZonedDateTime pauseUntil = ZonedDateTime.now(ZONE_ID_GMT).plusMinutes(2);
            PushConsumerCreator cc = new PushConsumerCreator(ctx.stream)
                    .durable(ctx.consumerName())
                    .pauseUntil(pauseUntil);

            // Consumer should be paused on creation.
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(cc);
            assertTrue(ci.getPaused());
            assertNotNull(ci.getPauseRemaining());
            assertTrue(ci.getPauseRemaining().toMillis() > 60_000);
            assertEquals(pauseUntil, ci.getConsumerConfiguration().getPauseUntil());
        });
    }

    @Test
    public void testPauseResumeConsumer() throws Exception {
        runInShared(VersionUtils::atLeast2_11, (nc, ctx) -> {
            List<ConsumerInfo> list = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(0, list.size());

            PushConsumerCreator cc = new PushConsumerCreator(ctx.stream).durable(ctx.consumerName());
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(cc);
            assertNotNull(ci.getName());

            // pause consumer
            ZonedDateTime pauseUntil = ZonedDateTime.now(ZONE_ID_GMT).plusMinutes(2);
            ConsumerPauseResponse pauseResponse = ctx.jsm.pauseConsumer(ctx.stream, ci.getName(), pauseUntil);
            assertTrue(pauseResponse.isPaused());
            assertEquals(pauseUntil, pauseResponse.getPauseUntil());

            ci = ctx.jsm.getConsumerInfo(ctx.stream, ci.getName());
            assertTrue(ci.getPaused());
            assertNotNull(ci.getPauseRemaining());
            assertTrue(ci.getPauseRemaining().toMillis() > 60_000);

            // resume consumer
            boolean isResumed = ctx.jsm.resumeConsumer(ctx.stream, ci.getName());
            assertTrue(isResumed);

            ci = ctx.jsm.getConsumerInfo(ctx.stream, ci.getName());
            assertFalse(ci.getPaused());

            assertThrows(JetStreamApiException.class, () -> ctx.jsm.pauseConsumer(random(), ctx.consumerName(), pauseUntil));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.pauseConsumer(ctx.stream, random(), pauseUntil));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.resumeConsumer(random(), ctx.consumerName()));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.resumeConsumer(ctx.stream, random()));

            //noinspection DataFlowIssue stream name is marked non null
            assertThrows(IllegalArgumentException.class, () -> ctx.jsm.pauseConsumer(null, ctx.consumerName(), pauseUntil));
            //noinspection DataFlowIssue consumer name is marked non null
            assertThrows(IllegalArgumentException.class, () -> ctx.jsm.pauseConsumer(ctx.stream, null, pauseUntil));
            //noinspection DataFlowIssue pause until is marked non null
            assertThrows(IllegalArgumentException.class, () -> ctx.jsm.pauseConsumer(ctx.stream, ctx.consumerName(), null));
        });
    }

    private static void addConsumer(JetStreamManagement jsm, boolean atLeast2dot9, String name, String deliver, String fs, ConsumerCreator<?> cc) throws IOException, JetStreamApiException {
        ConsumerInfo ci = jsm.addOrUpdateConsumer(cc);
        assertEquals(name, ci.getName());
        if (atLeast2dot9) {
            assertEquals(name, ci.getConsumerConfiguration().getName());
        }
        assertEquals(name, ci.getConsumerConfiguration().getDurable());
        if (fs == null) {
            assertNull(ci.getConsumerConfiguration().getFilterSubject());
        }
        if (deliver != null) {
            assertEquals(deliver, ci.getConsumerConfiguration().getDeliverSubject());
        }
    }

    @Test
    public void testValidConsumerUpdates() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            String subject = random();
            String subjectGt = subjectGt(subject);
            ctx.createOrReplaceStream(subjectGt);

            PushConsumerCreator creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, null);
            creator = new PushConsumerCreator(creator).deliverSubject(random());
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).ackWait(Duration.ofSeconds(5));
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).rateLimit(100);
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).maxAckPending(100);
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).maxDeliver(4);
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).filterSubject(subjectStar(subject));
            assertValidAddOrUpdate(ctx.jsm, creator, ctx.stream);
        });
    }

    @Test
    public void testInvalidConsumerUpdates() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            String subject = random();
            String subjectGt = subjectGt(subject);
            ctx.createOrReplaceStream(subjectGt);

            PushConsumerCreator creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, null);
            creator = new PushConsumerCreator(creator).deliverPolicy(DeliverPolicy.New);
            assertInvalidConsumerUpdate(ctx.jsm, creator);

            creator = prepForUpdateTest(ctx.jsm, ctx.stream, subjectGt, creator.getDurable());
            creator = new PushConsumerCreator(creator).idleHeartbeat(Duration.ofMillis(111));
            assertInvalidConsumerUpdate(ctx.jsm, creator);
        });
    }

    private PushConsumerCreator prepForUpdateTest(JetStreamManagement jsm, String stream, String subjectGt, String durableToDelete) throws IOException, JetStreamApiException {
        try {
            if (durableToDelete != null) {
                jsm.deleteConsumer(stream, durableToDelete);
            }
        }
        catch (Exception e) { /* ignore */ }
        PushConsumerCreator creator = new PushConsumerCreator(stream)
            .durable(random())
            .ackPolicy(AckPolicy.Explicit)
            .deliverSubject(random())
            .maxDeliver(3)
            .filterSubject(subjectGt);
        assertValidAddOrUpdate(jsm, creator, stream);
        return creator;
    }

    private void assertInvalidConsumerUpdate(JetStreamManagement jsm, PushConsumerCreator creator) {
        JetStreamApiException e = assertThrows(JetStreamApiException.class, () -> jsm.addOrUpdateConsumer(creator));
        assertEquals(10012, e.getApiErrorCode());
        assertEquals(500, e.getErrorCode());
    }

    private void assertValidAddOrUpdate(JetStreamManagement jsm, PushConsumerCreator creator, String stream) throws IOException, JetStreamApiException {
        ConsumerInfo ci = jsm.addOrUpdateConsumer(creator);
        ConsumerConfiguration cicc = ci.getConsumerConfiguration();
        assertEquals(creator.getDurable(), ci.getName());
        assertEquals(creator.getDurable(), cicc.getDurable());
        assertEquals(creator.getDeliverSubject(), cicc.getDeliverSubject());
        assertEquals(creator.getMaxDeliver(), cicc.getMaxDeliver());
        assertEquals(creator.getDeliverPolicy(), cicc.getDeliverPolicy());

        List<String> consumers = jsm.getConsumerNames(stream);
        assertEquals(1, consumers.size());
        assertEquals(creator.getDurable(), consumers.get(0));
    }

    @Test
    public void testConsumerMetadata() throws Exception {
        runInShared((nc, ctx) -> {
            Map<String, String> metaData = new HashMap<>(); metaData.put(META_KEY, META_VALUE);

            PushConsumerCreator cc = new PushConsumerCreator(ctx.stream)
                .durable(random())
                .metadata(metaData);

            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(cc);
            assertMetaData(ci.getConsumerConfiguration().getMetadata());
        });
    }

    @Test
    public void testCreateConsumersWithFilters() throws Exception {
        runInShared((nc, ctx) -> {
            String subject = ctx.subject();

            // plain subject
            PushConsumerCreator creator = new PushConsumerCreator(ctx.stream).durable(random());
            ctx.jsm.addOrUpdateConsumer(creator.filterSubject(subject));
            List<ConsumerInfo> cis = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(subject, cis.get(0).getConsumerConfiguration().getFilterSubject());

            if (nc.getServerInfo().isSameOrNewerThanVersion("2.10")) {
                // 2.10 and later you can set the filter to something that does not match
                ctx.jsm.addOrUpdateConsumer(creator.filterSubject(subjectDot(subject, "two-ten-allows-not-matching")));
                cis = ctx.jsm.getConsumers(ctx.stream);
                assertEquals(subjectDot(subject, "two-ten-allows-not-matching"), cis.get(0).getConsumerConfiguration().getFilterSubject());
            }
            else {
                assertThrows(JetStreamApiException.class,
                    () -> ctx.jsm.addOrUpdateConsumer(creator.filterSubject(subjectDot(subject, "not-match"))));
            }

            // wildcard subject
            ctx.createOrReplaceStream(subjectStar(subject));

            String subjectA = subjectDot(subject, "A");
            ctx.jsm.addOrUpdateConsumer(creator.filterSubject(subjectA));
            cis = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(subjectA, cis.get(0).getConsumerConfiguration().getFilterSubject());

            // gt subject
            ctx.createOrReplaceStream(subjectGt(subject));

            ctx.jsm.addOrUpdateConsumer(creator.filterSubject(subjectA));
            cis = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(subjectA, cis.get(0).getConsumerConfiguration().getFilterSubject());
        });
    }

    @Test
    public void testGetConsumerInfo() throws Exception {
        runInShared((nc, ctx) -> {
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getConsumerInfo(ctx.stream, ctx.consumerName()));
            PushConsumerCreator creator = new PushConsumerCreator(ctx.stream).durable(ctx.consumerName());
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(creator);
            assertEquals(ctx.stream, ci.getStreamName());
            assertEquals(ctx.consumerName(), ci.getName());
            ci = ctx.jsm.getConsumerInfo(ctx.stream, ctx.consumerName());
            assertEquals(ctx.stream, ci.getStreamName());
            assertEquals(ctx.consumerName(), ci.getName());
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getConsumerInfo(ctx.stream, random()));
            if (nc.getServerInfo().isSameOrNewerThanVersion("2.10")) {
                assertNotNull(ci.getTimestamp());
            }
        });
    }

    @Test
    public void testGetConsumers() throws Exception {
        runInShared((nc, ctx) -> {
            addConsumers(ctx.jsm, ctx.stream, 600); // getConsumers pages at 256

            List<ConsumerInfo> list = ctx.jsm.getConsumers(ctx.stream);
            assertEquals(600, list.size());

            addConsumers(ctx.jsm, ctx.stream, 500); // getConsumerNames pages at 1024
            List<String> names = ctx.jsm.getConsumerNames(ctx.stream);
            assertEquals(1100, names.size());
        });
    }

    private void addConsumers(JetStreamManagement jsm, String stream, int count) throws IOException, JetStreamApiException {
        String base = random() ;
        for (int x = 1; x <= count; x++) {
            boolean pull = x % 2 == 0;
            String dur = base + "-" + x;
            ConsumerInfo ci = jsm.addOrUpdateConsumer(pull
                ? new PullConsumerCreator(stream).durable(dur)
                : new PushConsumerCreator(stream).durable(dur)
            );
            assertEquals(dur, ci.getName());
            assertEquals(dur, ci.getConsumerConfiguration().getDurable());
            if (pull) {
                assertNull(ci.getConsumerConfiguration().getDeliverSubject());
            }
            else {
                assertNotNull(ci.getConsumerConfiguration().getDeliverSubject());
            }
        }
    }

    @Test
    public void testDeleteMessage() throws Exception {
        MessageDeleteRequest mdr = new MessageDeleteRequest(1, true);
        assertEquals("{\"seq\":1}", mdr.toJson());
        assertEquals(1, mdr.getSequence());
        assertTrue(mdr.isErase());
        assertFalse(mdr.isNoErase());

        mdr = new MessageDeleteRequest(1, false);
        assertEquals("{\"seq\":1,\"no_erase\":true}", mdr.toJson());
        assertEquals(1, mdr.getSequence());
        assertFalse(mdr.isErase());
        assertTrue(mdr.isNoErase());

        runInShared((nc, ctx) -> {
            Headers h = new Headers();
            h.add("foo", "bar");

            ZonedDateTime timeBeforeCreated = ZonedDateTime.now();
            ctx.js.publish(NatsMessage.builder().subject(ctx.subject()).headers(h).data(dataBytes(1)).build());
            ctx.js.publish(NatsMessage.builder().subject(ctx.subject()).build());

            MessageInfo mi = ctx.jsm.getMessage(ctx.stream, 1);
            assertNotNull(mi.toString());
            assertEquals(ctx.subject(), mi.getSubject());
            assertNotNull(mi.getData());
            assertEquals(data(1), new String(mi.getData()));
            assertEquals(1, mi.getSequence());
            assertNotNull(mi.getTime());
            assertTrue(mi.getTime().toEpochSecond() >= timeBeforeCreated.toEpochSecond());
            assertNotNull(mi.getHeaders());
            List<String> foos = mi.getHeaders().get("foo");
            assertNotNull(foos);
            assertEquals("bar", foos.get(0));

            mi = ctx.jsm.getMessage(ctx.stream, 2);
            assertNotNull(mi.toString());
            assertEquals(ctx.subject(), mi.getSubject());
            assertNull(mi.getData());
            assertEquals(2, mi.getSequence());
            assertNotNull(mi.getTime());
            assertTrue(mi.getTime().toEpochSecond() >= timeBeforeCreated.toEpochSecond());
            assertTrue(mi.getHeaders() == null || mi.getHeaders().isEmpty());

            assertTrue(ctx.jsm.deleteMessage(ctx.stream, 1, false)); // added coverage for use of erase (no_erase) flag.
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.deleteMessage(ctx.stream, 1));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(ctx.stream, 1));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(ctx.stream, 3));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.deleteMessage(random(), 1));
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(random(), 1));
        });
    }

    @Test
    public void testSealed() throws Exception {
        runInShared((nc, ctx) -> {
            assertFalse(ctx.si.getConfiguration().getSealed());

            ctx.js.publish(ctx.subject(), "data1".getBytes());

            StreamInfo si = ctx.jsm.updateStream(new StreamCreator(ctx.si.getConfiguration())
                .seal());
            assertTrue(si.getConfiguration().getSealed());

            assertThrows(JetStreamApiException.class, () -> ctx.js.publish(ctx.subject(), "data2".getBytes()));
        });
    }

    @Test
    public void testStorageTypeCoverage() {
        assertEquals(StorageType.File, StorageType.get("file", StorageType.File));
        assertEquals(StorageType.File, StorageType.get("FILE", StorageType.File));
        assertEquals(StorageType.Memory, StorageType.get("memory", StorageType.File));
        assertEquals(StorageType.Memory, StorageType.get("MEMORY", StorageType.File));
        assertEquals(StorageType.File, StorageType.get("nope", StorageType.File));
        assertEquals(StorageType.Memory, StorageType.get("nope", StorageType.Memory));
    }

    @Test
    public void testConsumerReplica() throws Exception {
        runInShared((nc, ctx) -> {
            final PushConsumerCreator cc0 = new PushConsumerCreator(ctx.stream)
                .durable(ctx.consumerName());
            ConsumerInfo ci = ctx.jsm.addOrUpdateConsumer(cc0);
            // server returns 0 when value is not set
            assertEquals(0, ci.getConsumerConfiguration().getNumReplicas());

            final PushConsumerCreator cc1 = new PushConsumerCreator(ctx.stream)
                .durable(ctx.consumerName())
                .numReplicas(1);
            ci = ctx.jsm.addOrUpdateConsumer(cc1);
            assertEquals(1, ci.getConsumerConfiguration().getNumReplicas());
        });
    }

    @Test
    public void testGetMessage() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            ctx.createOrReplaceStream(2);
            assertFalse(ctx.si.getConfiguration().getAllowDirect());

            ZonedDateTime timeBeforeCreated = ZonedDateTime.now();
            sleep(100);
            ctx.js.publish(buildTestGetMessage(ctx, 0, 1));
            ctx.js.publish(buildTestGetMessage(ctx, 1, 2));
            ctx.js.publish(buildTestGetMessage(ctx, 0, 3));
            ctx.js.publish(buildTestGetMessage(ctx, 1, 4));
            ctx.js.publish(buildTestGetMessage(ctx, 0, 5));
            ctx.js.publish(buildTestGetMessage(ctx, 1, 6));

            validateGetMessage(ctx.jsm, ctx, timeBeforeCreated);

            StreamInfo si = ctx.jsm.updateStream(new StreamCreator(ctx.si.getConfiguration()).allowDirect(true));
            assertTrue(si.getConfiguration().getAllowDirect());
            validateGetMessage(ctx.jsm, ctx, timeBeforeCreated);

            // error case stream doesn't exist
            assertThrows(JetStreamApiException.class, () -> ctx.jsm.getMessage(random(), 1));
        });
    }

    private static NatsMessage buildTestGetMessage(JetStreamTestingContext ctx, int n, int q) {
        String data = "s" + n + "-q" + q;
        return NatsMessage.builder()
            .subject(ctx.subject(n))
            .data("d-" + data)
            .headers(new Headers().put("h", "h-" + data))
            .build();
    }

    private void validateGetMessage(JetStreamManagement jsm, JetStreamTestingContext ctx, ZonedDateTime timeBeforeCreated) throws IOException, JetStreamApiException {
        assertMessageInfo(ctx, 0, 1, jsm.getMessage(ctx.stream, 1), timeBeforeCreated);
        assertMessageInfo(ctx, 0, 5, jsm.getLastMessage(ctx.stream, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 6, jsm.getLastMessage(ctx.stream, ctx.subject(1)), timeBeforeCreated);

        assertMessageInfo(ctx, 0, 1, jsm.getNextMessage(ctx.stream, -1, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getNextMessage(ctx.stream, -1, ctx.subject(1)), timeBeforeCreated);
        assertMessageInfo(ctx, 0, 1, jsm.getNextMessage(ctx.stream, 0, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getNextMessage(ctx.stream, 0, ctx.subject(1)), timeBeforeCreated);
        assertMessageInfo(ctx, 0, 1, jsm.getFirstMessage(ctx.stream, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getFirstMessage(ctx.stream, ctx.subject(1)), timeBeforeCreated);
        assertMessageInfo(ctx, 0, 1, jsm.getFirstMessage(ctx.stream, timeBeforeCreated), timeBeforeCreated);
        assertMessageInfo(ctx, 0, 1, jsm.getFirstMessage(ctx.stream, timeBeforeCreated, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getFirstMessage(ctx.stream, timeBeforeCreated, ctx.subject(1)), timeBeforeCreated);

        assertMessageInfo(ctx, 0, 1, jsm.getNextMessage(ctx.stream, 1, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getNextMessage(ctx.stream, 1, ctx.subject(1)), timeBeforeCreated);

        assertMessageInfo(ctx, 0, 3, jsm.getNextMessage(ctx.stream, 2, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 2, jsm.getNextMessage(ctx.stream, 2, ctx.subject(1)), timeBeforeCreated);

        assertMessageInfo(ctx, 0, 5, jsm.getNextMessage(ctx.stream, 5, ctx.subject(0)), timeBeforeCreated);
        assertMessageInfo(ctx, 1, 6, jsm.getNextMessage(ctx.stream, 5, ctx.subject(1)), timeBeforeCreated);

        assertStatus(10003, assertThrows(JetStreamApiException.class, () -> jsm.getMessage(ctx.stream, -1)));
        assertStatus(10003, assertThrows(JetStreamApiException.class, () -> jsm.getMessage(ctx.stream, 0)));
        assertStatus(10037, assertThrows(JetStreamApiException.class, () -> jsm.getMessage(ctx.stream, 9)));
        assertStatus(10037, assertThrows(JetStreamApiException.class, () -> jsm.getLastMessage(ctx.stream, "not-a-subject")));
        assertStatus(10037, assertThrows(JetStreamApiException.class, () -> jsm.getFirstMessage(ctx.stream, "not-a-subject")));
        assertStatus(10037, assertThrows(JetStreamApiException.class, () -> jsm.getNextMessage(ctx.stream, 9, ctx.subject(0))));
        assertStatus(10037, assertThrows(JetStreamApiException.class, () -> jsm.getNextMessage(ctx.stream, 1, "not-a-subject")));
    }

    private void assertStatus(int apiErrorCode, JetStreamApiException jsae) {
        assertEquals(apiErrorCode, jsae.getApiErrorCode());
    }

    private void assertMessageInfo(JetStreamTestingContext ctx, int subj, long seq, MessageInfo mi, ZonedDateTime timeBeforeCreated) {
        assertEquals(ctx.stream, mi.getStream());
        assertEquals(ctx.subject(subj), mi.getSubject());
        assertEquals(seq, mi.getSequence());
        assertNotNull(mi.getTime());
        assertTrue(mi.getTime().toEpochSecond() >= timeBeforeCreated.toEpochSecond());
        String expectedData = "s" + subj + "-q" + seq;
        assertNotNull(mi.getData());
        assertEquals("d-" + expectedData, new String(mi.getData()));
        assertNotNull(mi.getHeaders());
        assertEquals("h-" + expectedData, mi.getHeaders().getFirst("h"));
        assertNull(mi.getHeaders().getFirst(NATS_SUBJECT));
        assertNull(mi.getHeaders().getFirst(NATS_SEQUENCE));
        assertNull(mi.getHeaders().getFirst(NATS_TIMESTAMP));
        assertNull(mi.getHeaders().getFirst(NATS_STREAM));
        assertNull(mi.getHeaders().getFirst(NATS_LAST_SEQUENCE));
    }

    @Test
    public void testMessageGetRequestObject() {
        ZonedDateTime zdt = ZonedDateTime.now();
        validateMessageGetRequestObject(1, null, null, null, MessageGetRequest.forSequence(1));
        validateMessageGetRequestObject(-1, "last", null, null, MessageGetRequest.lastForSubject("last"));
        validateMessageGetRequestObject(-1, null, "first", null, MessageGetRequest.firstForSubject("first"));
        validateMessageGetRequestObject(1, null, "first", null, MessageGetRequest.nextForSubject(1, "first"));
        validateMessageGetRequestObject(-1, null, null, zdt, MessageGetRequest.firstForStartTime(zdt));
        validateMessageGetRequestObject(-1, null, "first", zdt, MessageGetRequest.firstForStartTimeAndSubject(zdt, "first"));

        // coverage for MessageInfo
        Message m = new NatsMessage("sub", null, new Headers()
            .put(NATS_SUBJECT, "sub")
            .put(NATS_SEQUENCE, "1")
            .put(NATS_LAST_SEQUENCE, "1")
            .put(NATS_TIMESTAMP, DateTimeUtils.toRfc3339(ZonedDateTime.now())),
            null);
        MessageInfo mi = new MessageInfo(m, "stream", true);
        assertEquals(1, mi.getLastSequence());
        assertTrue(mi.toString().contains("last_seq"));
        assertNotNull(mi.toString());
    }

    private void validateMessageGetRequestObject(
        long seq, String lastBySubject, String nextBySubject, ZonedDateTime zdt, MessageGetRequest mgr) {
        assertEquals(seq, mgr.getSequence());
        assertEquals(lastBySubject, mgr.getLastBySubject());
        assertEquals(nextBySubject, mgr.getNextBySubject());
        assertEquals(seq > 0 && nextBySubject == null, mgr.isSequenceOnly());
        assertEquals(lastBySubject != null, mgr.isLastBySubject());
        assertEquals(nextBySubject != null, mgr.isNextBySubject());
        assertEquals(zdt, mgr.getStartTime());
    }

    @Test
    public void testCreateConsumerUpdateConsumer() throws Exception {
        runInOwnJsServer((nc, jsm, js) -> {
            String streamPrefix = random();
            JetStreamManagement jsmNew = new JetStreamManagement(nc);
            JetStreamManagement jsmPre290 = new JetStreamManagement(nc, JetStreamOptions.builder().optOut290ConsumerCreate(true).build());

            // --------------------------------------------------------
            // New without filter
            // --------------------------------------------------------
            String stream1 = streamPrefix + "-new";
            String name = random();
            String subject = random();
            createMemoryStream(jsmNew, stream1, subject + ".*");

            PushConsumerCreator cc11 = new PushConsumerCreator(stream1).name(name);

            // update no good when not exist
            JetStreamApiException e = assertThrows(JetStreamApiException.class, () -> jsmNew.updateConsumer(cc11));
            assertEquals(10149, e.getApiErrorCode());

            // initial create ok
            ConsumerInfo ci = jsmNew.createConsumer(cc11);
            assertEquals(name, ci.getName());
            assertNull(ci.getConsumerConfiguration().getFilterSubject());

            // any other create no good
            e = assertThrows(JetStreamApiException.class, () -> jsmNew.createConsumer(cc11));
            assertEquals(10148, e.getApiErrorCode());

            // update ok when exists
            PushConsumerCreator cc12 = new PushConsumerCreator(stream1).name(name).description(random());
            ci = jsmNew.updateConsumer(cc12);
            assertEquals(name, ci.getName());
            assertNull(ci.getConsumerConfiguration().getFilterSubject());

            // --------------------------------------------------------
            // New with filter subject
            // --------------------------------------------------------
            String stream2 = streamPrefix + "-new-fs";
            name = random();
            subject = random();
            String fs1 = subject + ".A";
            String fs2 = subject + ".B";
            createMemoryStream(jsmNew, stream2, subject + ".*");

            PushConsumerCreator cc21 = new PushConsumerCreator(stream2).name(name).filterSubject(fs1);

            // update no good when not exist
            e = assertThrows(JetStreamApiException.class, () -> jsmNew.updateConsumer(cc21));
            assertEquals(10149, e.getApiErrorCode());

            // initial create ok
            ci = jsmNew.createConsumer(cc21);
            assertEquals(name, ci.getName());
            assertEquals(fs1, ci.getConsumerConfiguration().getFilterSubject());

            // any other create no good
            e = assertThrows(JetStreamApiException.class, () -> jsmNew.createConsumer(cc21));
            assertEquals(10148, e.getApiErrorCode());

            // update ok when exists
            PushConsumerCreator cc22 = new PushConsumerCreator(stream2).name(name).filterSubjects(fs2);
            ci = jsmNew.updateConsumer(cc22);
            assertEquals(name, ci.getName());
            assertEquals(fs2, ci.getConsumerConfiguration().getFilterSubject());

            // --------------------------------------------------------
            // Pre 290 durable pathway
            // --------------------------------------------------------
            String stream3 = streamPrefix + "-old-durable";
            name = random();
            subject = random();
            fs1 = subject + ".A";
            fs2 = subject + ".B";
            String fs3 = subject + ".C";
            createMemoryStream(jsmPre290, stream3, subject + ".*");

            PushConsumerCreator cc31 = new PushConsumerCreator(stream3).durable(name).filterSubject(fs1);

            // update no good when not exist
            e = assertThrows(JetStreamApiException.class, () -> jsmPre290.updateConsumer(cc31));
            assertEquals(10149, e.getApiErrorCode());

            // initial create ok
            ci = jsmPre290.createConsumer(cc31);
            assertEquals(name, ci.getName());
            assertEquals(fs1, ci.getConsumerConfiguration().getFilterSubject());

            // opt out of 209, create on existing ok
            // This is not exactly the same behavior as with the new consumer create api, but it's what the server does
            jsmPre290.createConsumer(cc31);

            PushConsumerCreator cc32 = new PushConsumerCreator(stream3).durable(name).filterSubject(fs2);
            e = assertThrows(JetStreamApiException.class, () -> jsmPre290.createConsumer(cc32));
            assertEquals(10148, e.getApiErrorCode());

            // update ok when exists
            PushConsumerCreator cc33 = new PushConsumerCreator(stream3).durable(name).filterSubjects(fs3);
            ci = jsmPre290.updateConsumer(cc33);
            assertEquals(name, ci.getName());
            assertEquals(fs3, ci.getConsumerConfiguration().getFilterSubject());

            // --------------------------------------------------------
            // Pre 290 ephemeral pathway
            // --------------------------------------------------------
            subject = random();

            String stream4 = streamPrefix + "-old-ephemeral";
            fs1 = subject + ".A";
            createMemoryStream(jsmPre290, stream4, subject + ".*");

            PushConsumerCreator cc4 = new PushConsumerCreator(stream4).filterSubject(fs1);

            // update no good when not exist
            e = assertThrows(JetStreamApiException.class, () -> jsmPre290.updateConsumer(cc4));
            assertEquals(10149, e.getApiErrorCode());

            // initial create ok
            ci = jsmPre290.createConsumer(cc4);
            assertEquals(fs1, ci.getConsumerConfiguration().getFilterSubject());
        });
    }

    @Test
    public void testNoRespondersWhenConsumerDeleted1026() throws Exception {
        Listener listener = new Listener();
        runInSharedOwnNc(listener, VersionUtils::atLeast2_10_26, (nc, ctx) -> {
            String subject = ctx.subject();
            for (int x = 0; x < 5; x++) {
                ctx.js.publish(subject, (String)null);
            }

            ConsumerInfo ci = create1026Consumer(ctx.jsm, ctx.stream, subject);
            JetStreamPullSubscription sub = ctx.js.pullSubscribe(ci);
            ctx.jsm.deleteConsumer(ctx.stream, ci.getName());
            sub.pull(5);
            validate1026(sub.nextMessage(500), listener, false);

            ConsumerContext context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            validate1026(context.next(1000), listener, true); // simplification next never raises warnings, so empty = true

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            //noinspection resource
            FetchConsumer fc = context.fetch(FetchConsumeOptions.builder().maxMessages(1).raiseStatusWarnings(false).build());
            validate1026(fc.nextMessage(), listener, true); // we said not to raise status warnings in the FetchConsumeOptions

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            //noinspection resource
            fc = context.fetch(FetchConsumeOptions.builder().maxMessages(1).raiseStatusWarnings().build());
            validate1026(fc.nextMessage(), listener, false); // we said raise status warnings in the FetchConsumeOptions

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            try (IterableConsumer ic = context.iterate(ConsumeOptions.builder().raiseStatusWarnings(false).build())) {
                validate1026(ic.nextMessage(1000), listener, true); // we said not to raise status warnings in the ConsumeOptions
            }

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            try (IterableConsumer ic = context.iterate(ConsumeOptions.builder().raiseStatusWarnings().build())) {
                validate1026(ic.nextMessage(1000), listener, false); // we said raise status warnings in the ConsumeOptions
            }

            AtomicInteger count = new AtomicInteger();
            MessageHandler handler = m -> count.incrementAndGet();

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            //noinspection resource
            context.consume(ConsumeOptions.builder().raiseStatusWarnings(false).build(), handler);
            Thread.sleep(100); // give time to get a message
            assertEquals(0, count.get());
            validate1026(null, listener, true);

            context = setupFor1026Simplification(ctx, listener, ctx.stream, subject);
            //noinspection resource
            context.consume(ConsumeOptions.builder().raiseStatusWarnings().build(), handler);
            Thread.sleep(100); // give time to get a message
            assertEquals(0, count.get());
            validate1026(null, listener, false);
        });
    }

    private static void validate1026(Message m, Listener listener, boolean empty) {
        assertNull(m);
        sleep(250); // give time for the message to get there
        if (empty) {
            assertEquals(0, listener.getPullStatusWarningsCount());
        }
        else {
            assertTrue(listener.getPullStatusWarningsCount() > 0);
        }
    }

    private static ConsumerContext setupFor1026Simplification(JetStreamTestingContext ctx, Listener listener, String stream, String subject) throws IOException, JetStreamApiException {
        listener.reset();
        ConsumerInfo ci = create1026Consumer(ctx.jsm, stream, subject);
        ConsumerContext cCtx = ctx.js.getConsumerContext(ci);
        ctx.jsm.deleteConsumer(stream, ci.getName());
        return cCtx;
    }

    private static ConsumerInfo create1026Consumer(JetStreamManagement jsm, String stream, String subject) throws IOException, JetStreamApiException {
        return jsm.addOrUpdateConsumer(new PullConsumerCreator(stream)
            .durable(random())
            .filterSubject(subject));
    }

    @Test
    public void testMessageDeleteRequest() {
        MessageDeleteRequest mdr = new MessageDeleteRequest(1, true);
        assertEquals(1, mdr.getSequence());
        assertTrue(mdr.isErase());
        assertFalse(mdr.isNoErase());
        assertTrue(mdr.toJson().contains("\"seq\""));
        assertFalse(mdr.toJson().contains("\"no_erase\""));

        mdr = new MessageDeleteRequest(2, false);
        assertEquals(2, mdr.getSequence());
        assertFalse(mdr.isErase());
        assertTrue(mdr.isNoErase());
        assertTrue(mdr.toJson().contains("\"seq\""));
        assertTrue(mdr.toJson().contains("\"no_erase\""));

        mdr = MessageDeleteRequest.builder().build();
        assertEquals(-1, mdr.getSequence());
        assertTrue(mdr.isErase());
        assertFalse(mdr.isNoErase());
        assertFalse(mdr.toJson().contains("\"seq\""));
        assertFalse(mdr.toJson().contains("\"no_erase\""));

        mdr = MessageDeleteRequest.builder().sequence(3).build();
        assertEquals(3, mdr.getSequence());
        assertTrue(mdr.isErase());
        assertFalse(mdr.isNoErase());
        assertTrue(mdr.toJson().contains("\"seq\""));
        assertFalse(mdr.toJson().contains("\"no_erase\""));

        mdr = MessageDeleteRequest.builder().noErase().erase().build();
        assertEquals(-1, mdr.getSequence());
        assertTrue(mdr.isErase());
        assertFalse(mdr.isNoErase());
        assertFalse(mdr.toJson().contains("\"seq\""));
        assertFalse(mdr.toJson().contains("\"no_erase\""));

        mdr = MessageDeleteRequest.builder().erase().noErase().build();
        assertEquals(-1, mdr.getSequence());
        assertFalse(mdr.isErase());
        assertTrue(mdr.isNoErase());
        assertFalse(mdr.toJson().contains("\"seq\""));
        assertTrue(mdr.toJson().contains("\"no_erase\""));
    }

    @Test
    public void testStreamPersistMode() throws Exception {
        runInOwnJsServer(VersionUtils::atLeast2_12, (nc, jsm, js) -> {
            StreamInfo si = jsm.addStream(new StreamCreator(random())
                .storageType(StorageType.File)
                .subjects(random()));
            assertSame(PersistMode.Default, si.getConfiguration().getPersistMode());

            si = jsm.addStream(new StreamCreator(random())
                .storageType(StorageType.File)
                .subjects(random())
                .persistMode(PersistMode.Default));
            si.getConfiguration().getPersistMode();
            assertSame(PersistMode.Default, si.getConfiguration().getPersistMode());

            si = jsm.addStream(new StreamCreator(random())
                .storageType(StorageType.File)
                .subjects(random())
                .persistMode(PersistMode.Async));
            assertSame(PersistMode.Async, si.getConfiguration().getPersistMode());
        });
    }
    // Stream names that validateStreamName rejects (mirrors the canonical list in JsValidatorTests
    // plus slash variants implied by the validator name: validatePrintableExceptWildDotGtSlashes).
    private static final String[] INVALID_STREAMS = {
        null,
        "",
        HAS_SPACE,
        HAS_DOT,
        STAR_NOT_SEGMENT,
        GT_NOT_SEGMENT,
        HAS_FWD_SLASH,
        HAS_BACK_SLASH,
        HAS_LOW,
        HAS_127
    };

    @Test
    public void testPushConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PushConsumerCreator", PushConsumerCreator::new);
    }

    @Test
    public void testPullConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PullConsumerCreator", PullConsumerCreator::new);
    }

    @Test
    public void testPushOrderedConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PushOrderedConsumerCreator", PushOrderedConsumerCreator::new);
    }

    @Test
    public void testPullOrderedConsumerCreatorRejectsInvalidStream() {
        assertRejectsAll("PullOrderedConsumerCreator", PullOrderedConsumerCreator::new);
    }

    private static void assertRejectsAll(String creatorName, Function<String, ?> factory) {
        for (String invalid : INVALID_STREAMS) {
            assertThrows(IllegalArgumentException.class,
                () -> factory.apply(invalid),
                creatorName + " must reject invalid stream: " + invalid);
        }
    }
}
