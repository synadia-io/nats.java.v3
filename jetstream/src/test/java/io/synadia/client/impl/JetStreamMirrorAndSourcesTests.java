package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.synadia.client.Message;
import io.synadia.client.api.*;
import io.synadia.client.utils.VersionUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JetStreamMirrorAndSourcesTests extends JetStreamTestBase {

    @Test
    public void testMirrorBasics() throws Exception {
        String S1 = random();
        String S2 = random();
        String S3 = random();
        String S4 = random();
        String U1 = random();
        String U2 = random();
        String U3 = random();
        String M1 = random();

        runInSharedCustomContext((nc, ctx) -> {
            // Create source stream
            StreamCreator sc = ctx.streamCreatorNoSubjects(S1).subjects(U1, U2, U3);
            StreamInfo si = ctx.addStream(sc);
            StreamConfiguration conf = si.getConfiguration();
            assertNotNull(conf);
            assertEquals(S1, conf.getName());

            // Now create our mirror stream.
            sc = ctx.streamCreatorNoSubjects(M1)
                .mirrorCreator(new MirrorCreator(S1));
            ctx.addStream(sc);
            assertMirror(ctx.jsm, M1, S1, null, null);

            // Send 100 messages.
            jsPublish(ctx.js, U2, 100);

            // Check the state
            assertMirror(ctx.jsm, M1, S1, 100L, null);

            // Purge the source stream.
            ctx.jsm.purgeStream(S1);

            jsPublish(ctx.js, U2, 50);

            // Create second mirror
            sc = ctx.streamCreatorNoSubjects(S2)
                .mirrorCreator(new MirrorCreator(S1));
            ctx.createOrReplaceStream(sc);

            // Check the state
            assertMirror(ctx.jsm, S2, S1, 50L, 101L);

            jsPublish(ctx.js, U3, 100);

            // third mirror checks start seq
            sc = ctx.streamCreatorNoSubjects(S3)
                .subjects() // scBuilder added subjects
                .mirrorCreator(new MirrorCreator(S1).startSequence(150));
            ctx.createOrReplaceStream(sc);

            // Check the state
            assertMirror(ctx.jsm, S3, S1, 101L, 150L);

            // third mirror checks start seq
            ZonedDateTime zdt = DateTimeUtils.fromNow(Duration.ofHours(-2));
            sc = ctx.streamCreatorNoSubjects(S4)
                .subjects() // scBuilder added subjects
                .mirrorCreator(new MirrorCreator(S1).startTime(zdt));
            ctx.createOrReplaceStream(sc);

            // Check the state
            assertMirror(ctx.jsm, S4, S1, 150L, 101L);
        });
    }

    @Test
    public void testMirrorReading() throws Exception {
        String S1 = random();
        String U1 = random();
        String U2 = random();
        String M1 = random();

        runInSharedCustomContext((nc, ctx) -> {
            // Create source stream
            StreamCreator sc = ctx.streamCreatorNoSubjects(S1).subjects(U1, U2);
            StreamInfo si = ctx.createOrReplaceStream(sc);
            StreamConfiguration conf = si.getConfiguration();
            assertNotNull(conf);
            assertEquals(S1, conf.getName());

            // Now create our mirror stream.
            sc = ctx.streamCreatorNoSubjects(M1)
                .mirrorCreator(new MirrorCreator(S1));
            ctx.addStream(sc);
            assertMirror(ctx.jsm, M1, S1, null, null);

            // Send messages.
            jsPublish(ctx.js, U1, 10);
            jsPublish(ctx.js, U2, 20);

            assertMirror(ctx.jsm, M1, S1, 30L, null);

            PushConsumerCreator creator = new PushConsumerCreator().filterSubject(U1);
            JetStreamPushSubscription sub = ctx.js.pushSubscribe(S1, creator);
            List<Message> list = readMessagesAck(sub);
            assertEquals(10, list.size());
            for (Message m : list) {
                assertEquals(S1, m.metaData().getStream());
            }

            creator = new PushConsumerCreator().filterSubject(U2);
            sub = ctx.js.pushSubscribe(S1, creator);
            list = readMessagesAck(sub);
            assertEquals(20, list.size());
            for (Message m : list) {
                assertEquals(S1, m.metaData().getStream());
            }

            creator = new PushConsumerCreator().filterSubject(U1);
            sub = ctx.js.pushSubscribe(M1, creator);
            list = readMessagesAck(sub);
            assertEquals(10, list.size());
            for (Message m : list) {
                assertEquals(M1, m.metaData().getStream());
            }

            creator = new PushConsumerCreator().filterSubject(U2);
            sub = ctx.js.pushSubscribe(M1, creator);
            list = readMessagesAck(sub);
            assertEquals(20, list.size());
            for (Message m : list) {
                assertEquals(M1, m.metaData().getStream());
            }
        });
    }

    @Test
    public void testMirrorExceptions() throws Exception {
        runInSharedCustomContext((nc, ctx) -> {
            StreamCreator scEx = new StreamCreator(random())
                .subjects(random())
                .mirrorCreator(new MirrorCreator(random()));

            // stream mirrors can not contain subjects [10034]
            JetStreamApiException e =
                assertThrows(JetStreamApiException.class, () -> ctx.createOrReplaceStream(scEx));
            assertEquals(10034, e.getApiErrorCode());
        });
    }

    @Test
    public void testSourceBasics() throws Exception {
        String S1 = random();
        String S2 = random();
        String S3 = random();
        String S4 = random();
        String S5 = random();
        String S99 = random();
        String R1 = random();
        String R2 = random();
        String R3 = random();

        runInSharedCustomContext((nc, ctx) -> {
            // Create streams
            StreamInfo si = ctx.addStream(new StreamCreator(S1).storageType(StorageType.Memory));
            StreamConfiguration sc = si.getConfiguration();
            assertNotNull(sc);
            assertEquals(S1, sc.getName());

            si = ctx.addStream(new StreamCreator(S2).storageType(StorageType.Memory));
            sc = si.getConfiguration();
            assertNotNull(sc);
            assertEquals(S2, sc.getName());

            si = ctx.addStream(new StreamCreator(S3).storageType(StorageType.Memory));
            sc = si.getConfiguration();
            assertNotNull(sc);
            assertEquals(S3, sc.getName());

            // Populate each one.
            jsPublish(ctx.js, S1, 10);
            jsPublish(ctx.js, S2, 15);
            jsPublish(ctx.js, S3, 25);

            StreamCreator cr = new StreamCreator(R1)
                .storageType(StorageType.Memory)
                .sourceCreators(
                    new SourceCreator(S1),
                    new SourceCreator(S2),
                    new SourceCreator(S3)
                );

            ctx.addStream(cr);

            assertSource(ctx.jsm, R1, 50L, null);

            cr = new StreamCreator(R1)
                .storageType(StorageType.Memory)
                .sourceCreators(
                    new SourceCreator(S1),
                    new SourceCreator(S2),
                    new SourceCreator(S4)
                );

            ctx.jsm.updateStream(cr);

            cr = new StreamCreator(S99)
                .storageType(StorageType.Memory)
                .subjects(S4, S5);
            ctx.addStream(cr);

            jsPublish(ctx.js, S4, 20);
            jsPublish(ctx.js, S5, 20);
            jsPublish(ctx.js, S4, 10);

            cr = new StreamCreator(R2)
                .storageType(StorageType.Memory)
                .sourceCreators(
                    new SourceCreator(S99).startSequence(26)
                );
            ctx.addStream(cr);
            assertSource(ctx.jsm, R2, 25L, null);

            MessageInfo info = ctx.jsm.getMessage(R2, 1);
            assertStreamSource(info, S99, 26);

            cr = new StreamCreator(R3)
                .storageType(StorageType.Memory)
                .sourceCreators(
                    new SourceCreator(S99).startSequence(11).filterSubject(S4)
                );
            ctx.addStream(cr);
            assertSource(ctx.jsm, R3, 20L, null);

            info = ctx.jsm.getMessage(R3, 1);
            assertStreamSource(info, S99, 11);
        });
    }

    @Test
    public void testSourceAndTransformsRoundTrips() throws Exception {
        runInOwnJsServer(VersionUtils::atLeast2_10, (nc, jsm, js) -> {
            StreamCreator creator = new StreamCreator("sourcingstream")
                .retentionPolicy(RetentionPolicy.Limits)
                .maxConsumers(-1)
                .maxMessagesPerSubject(-1)
                .maxMessages(-1)
                .maxBytes(-1)
                .maxAge(0)
                .storageType(StorageType.Memory)
                .discardPolicy(DiscardPolicy.Old)
                .replicas(1)
                .duplicateWindow(Duration.ofSeconds(120))
                .sourceCreators(
                    new SourceCreator("sourcedfoo")
                        .subjectTransformCreators(
                            new SubjectTransformCreator("foo", "foo-transformed")),
                    new SourceCreator("sourcedbar")
                        .subjectTransformCreators(
                            new SubjectTransformCreator("bar", "bar-transformed"))
                )
                .sealed(false)
                .denyDelete(false)
                .denyPurge(false)
                .allowRollup(false)
                .allowDirect(false)
                .mirrorDirect(false);

            StreamInfo si = jsm.addStream(creator);
            StreamConfiguration scSource = si.getConfiguration();
            assertNull(si.getMirrorInfo());

            assertNotNull(scSource.getSources());
            assertNotNull(si.getSources());
            Source source = scSource.getSources().get(0);
            SourceInfo info = si.getSources().get(0);
            assertNotNull(info);
            assertNotNull(info.getSubjectTransforms());
            assertEquals(1, info.getSubjectTransforms().size());

            assertEquals(source.getStreamName(), info.getName());
            assertNotNull(source.getSubjectTransforms());
            assertEquals(1, source.getSubjectTransforms().size());

            SubjectTransform st = source.getSubjectTransforms().get(0);
            SubjectTransform infoSt = info.getSubjectTransforms().get(0);
            assertEquals(st.getSource(), infoSt.getSource());
            assertEquals(st.getDestination(), infoSt.getDestination());

            source = scSource.getSources().get(1);
            info = si.getSources().get(1);
            assertNotNull(scSource.getSources());
            assertNotNull(si.getSources());
            assertEquals(source.getStreamName(), info.getName());
            assertNotNull(info.getSubjectTransforms());
            assertEquals(1, info.getSubjectTransforms().size());
            assertNotNull(source.getSubjectTransforms());
            st = source.getSubjectTransforms().get(0);
            infoSt = info.getSubjectTransforms().get(0);
            assertEquals(st.getSource(), infoSt.getSource());
            assertEquals(st.getDestination(), infoSt.getDestination());
        });
    }

    @Test
    public void testMirror() throws Exception {
        runInOwnJsServer(VersionUtils::atLeast2_10, (nc, jsm, js) -> {
            StreamCreator creator = new StreamCreator("sourcingstream")
                .retentionPolicy(RetentionPolicy.Limits)
                .maxConsumers(-1)
                .maxMessagesPerSubject(-1)
                .maxMessages(-1)
                .maxBytes(-1)
                .maxAge(0)
                .storageType(StorageType.Memory)
                .discardPolicy(DiscardPolicy.Old)
                .replicas(1)
                .duplicateWindow(Duration.ofSeconds(120))
                .mirrorCreator(
                    new MirrorCreator("sourcedstream")
                        .subjectTransformCreators(
                            new SubjectTransformCreator("foo", "foo-transformed"),
                            new SubjectTransformCreator("bar", "bar-transformed")
                        )
                )
                .sealed(false)
                .denyDelete(false)
                .denyPurge(false)
                .allowRollup(false)
                .allowDirect(false)
                .mirrorDirect(false);

            StreamInfo si = jsm.addStream(creator);
            StreamConfiguration scMirror = si.getConfiguration();
            Mirror m = scMirror.getMirror();
            MirrorInfo mi = si.getMirrorInfo();
            assertNotNull(m);
            assertNotNull(mi);
            assertEquals(m.getStreamName(), mi.getName());
            assertEquals(m.getSubjectTransforms(), mi.getSubjectTransforms());
            assertNotNull(scMirror.getSources());
            assertTrue(scMirror.getSources().isEmpty());
            assertNotNull(si.getSources());
            assertTrue(si.getSources().isEmpty());
        });
    }
}
