package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.JsonParseException;
import io.nats.json.LazyJsonParser;
import io.synadia.client.impl.JetStreamTestBase;
import io.synadia.client.testutils.ResourceUtils;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.synadia.client.api.CompressionOption.None;
import static io.synadia.client.api.CompressionOption.S2;
import static org.junit.jupiter.api.Assertions.*;

public class StreamCreatorConfigurationTests extends JetStreamTestBase {

    public static final String DEFAULT_STREAM_NAME = "sname";

    private static String STREAM_CONFIGURATION_JSON;
    private static String getStreamConfigurationJson() {
        if (STREAM_CONFIGURATION_JSON == null) {
            STREAM_CONFIGURATION_JSON = ResourceUtils.dataAsString("StreamConfiguration.json");
        }
        return STREAM_CONFIGURATION_JSON;
    }

    private StreamCreator getTestStreamCreator(String sname) {
        String json = getStreamConfigurationJson();
        if (sname != null) {
            json = json.replace("sname", sname);
        }
        StreamConfiguration sc = new StreamConfiguration(LazyJsonParser.parseUnchecked(json));
        assertNotNull(sc.toString()); // coverage
        return new StreamCreator(sc);
    }

    @Test
    public void testRoundTrip() throws Exception {
        runInSharedCustom((nc, ctx) -> {
            StreamCreator sc = getTestStreamCreator(ctx.stream)
                .mirror(null)
                .sources()
                .replicas(1)
                .templateOwner(null)
                .allowRollup(false)
                .allowDirect(false)
                .mirrorDirect(false)
                .sealed(false)
                .compressionOption(S2)
                .allowMessageCounter(false)
                .persistMode(null);
            validateTestStreamConfiguration(ctx.createOrReplaceStream(sc).getConfiguration(), true, ctx.stream);
        });
    }

    @Test
    public void testSerializationDeserialization() throws Exception {
        String originalJson = getStreamConfigurationJson();
        StreamConfiguration sc = new StreamConfiguration(LazyJsonParser.parseUnchecked(originalJson));
        validateTestStreamConfiguration(sc, false, DEFAULT_STREAM_NAME);
        validateTestStreamConfiguration(sc.toJson(), false, DEFAULT_STREAM_NAME);
    }

    @Test
    public void testSerializationDeserializationDefaults() throws Exception {
        StreamConfiguration sc = new StreamConfiguration(LazyJsonParser.parseUnchecked("{\"name\":\"name\"}"));
        assertNotNull(sc);
        assertEquals("name", sc.getName());
        String serializedJson = sc.toJson();
        sc = new StreamConfiguration(LazyJsonParser.parseUnchecked(serializedJson));
        assertEquals("name", sc.getName());
    }

    @Test
    public void testConstruction() {
        StreamCreator testSc = new StreamCreator(DEFAULT_STREAM_NAME);

        testSc
            .description(null)
            .maxAge(null)
            .templateOwner(null)
            .duplicateWindow(null)
            .placement(null)
            .republish(null)
            .subjectTransform(null)
            .consumerLimits(null)
            .mirror(null)
            .subjectDeleteMarkerTtl(null)
            .persistMode(null);
        validateDefaultNullsAndEmpty(testSc);

        testSc
            .placementCreator(null)
            .republishCreator(null)
            .subjectTransformCreator(null)
            .mirrorCreator(null)
            .consumerLimitsCreator(null)
            .persistMode(null);
        validateDefaultNullsAndEmpty(testSc);

        testSc = getTestStreamCreator(null);

        // from json
        validateTestStreamConfiguration(new StreamConfiguration(LazyJsonParser.parseUnchecked(testSc.toJson())), false, DEFAULT_STREAM_NAME);

        // builder
        StreamCreator creator = new StreamCreator(testSc.getName())
            .description(testSc.getDescription())
            .subjects(testSc.getSubjects())
            .retentionPolicy(testSc.getRetentionPolicy())
            .compressionOption(testSc.getCompressionOption())
            .maxConsumers(testSc.getMaxConsumers())
            .maxMessages(testSc.getMaxMessages())
            .maxMessagesPerSubject(testSc.getMaxMessagesPerSubject())
            .maxBytes(testSc.getMaxBytes())
            .maxAge(testSc.getMaxAge())
            .maxMessageSize(testSc.getMaxMessageSize())
            .storageType(testSc.getStorageType())
            .replicas(testSc.getReplicas())
            .noAck(testSc.getNoAck())
            .templateOwner(testSc.getTemplateOwner())
            .discardPolicy(testSc.getDiscardPolicy())
            .duplicateWindow(testSc.getDuplicateWindow())
            .sourceCreators(testSc.getSourceCreators())
            .sealed(testSc.getSealed())
            .allowRollup(testSc.getAllowRollup())
            .allowDirect(testSc.getAllowDirect())
            .mirrorDirect(testSc.getMirrorDirect())
            .denyDelete(testSc.getDenyDelete())
            .denyPurge(testSc.getDenyPurge())
            .discardNewPerSubject(testSc.isDiscardNewPerSubject())
            .metadata(testSc.getMetadata())
            .firstSequence(testSc.getFirstSequence())
            .subjectDeleteMarkerTtl(testSc.getSubjectDeleteMarkerTtl())
            .allowMessageTtl(testSc.getAllowMessageTtl())
            .allowMessageSchedules(testSc.getAllowMessageSchedules())
            .allowMessageCounter(testSc.getAllowMessageCounter())
            .allowAtomicPublish(testSc.getAllowAtomicPublish())
            .placementCreator(testSc.getPlacementCreator())
            .republishCreator(testSc.getRepublishCreator())
            .subjectTransformCreator(testSc.getSubjectTransformCreator())
            .consumerLimitsCreator(testSc.getConsumerLimitsCreator())
            .mirrorCreator(testSc.getMirrorCreator())
            .allowBatched(testSc.getAllowBatched())
            .persistMode(testSc.getPersistMode())
            ;

        validateTestStreamConfiguration(creator, false, DEFAULT_STREAM_NAME);

        // COVERAGE of builder methods since I know these to be true
        creator
            // clear the flags
            .allowMessageTtl(false)
            .allowMessageSchedules(false)
            .allowMessageCounter(false)
            .allowAtomicPublish(false)
            // set the flags
            .allowMessageTtl()
            .allowMessageSchedules()
            .allowMessageCounter()
            .allowAtomicPublish()
        ;

        // COVERAGE
        creator.subjectDeleteMarkerTtl(-1);
        assertNull(creator.getSubjectDeleteMarkerTtl());
        creator.subjectDeleteMarkerTtl(1000);
        Duration d = creator.getSubjectDeleteMarkerTtl();
        assertNotNull(d);
        assertEquals(1000, d.toMillis());
        creator.subjectDeleteMarkerTtl(testSc.getSubjectDeleteMarkerTtl()); // set it back for the rest of the test

        validateTestStreamConfiguration(creator, false, DEFAULT_STREAM_NAME);

        // SourceCreator round trips: SourceCreator -> JSON -> Source -> SourceCreator
        assertNotNull(testSc.getSourceCreators());
        List<SourceCreator> sourceCreators = testSc.getSourceCreators();
        assertFalse(sourceCreators.isEmpty());
        for (SourceCreator originalCreator : sourceCreators) {
            // SourceCreator -> JSON -> Source -> SourceCreator
            Source source = new Source(LazyJsonParser.parseUnchecked(originalCreator.toJson()));
            SourceCreator roundTrip = new SourceCreator(source);
            assertEquals(originalCreator.toJson(), roundTrip.toJson());

            // round trip through the SourceCreator copy constructor (basis)
            SourceCreator copy = new SourceCreator(originalCreator.getStreamName(), roundTrip);
            assertEquals(originalCreator.toJson(), copy.toJson());

            // verify the round-tripped Source matches the original SourceCreator content
            ExternalCreator originalExternal = originalCreator.getExternalCreator();
            External roundTripExternal = source.getExternal();
            if (originalExternal == null) {
                assertNull(roundTripExternal);
            }
            else {
                assertNotNull(roundTripExternal);
                assertEquals(originalExternal.getApi(), roundTripExternal.getApi());
                assertEquals(originalExternal.getDeliver(), roundTripExternal.getDeliver());
            }
        }

        List<String> lines = ResourceUtils.dataAsLines("MirrorsSources.json");
        for (String l1 : lines) {
            if (l1.startsWith("{")) {
                Mirror m1 = new Mirror(LazyJsonParser.parseUnchecked(l1));
                //noinspection EqualsWithItself
                assertEquals(m1, m1);
                assertEquals(m1, new Mirror(LazyJsonParser.parseUnchecked(m1.toJson())));
                Source s1 = new Source(LazyJsonParser.parseUnchecked(l1));
                //noinspection EqualsWithItself
                assertEquals(s1, s1);
                assertEquals(s1, new Source(LazyJsonParser.parseUnchecked(s1.toJson())));
                //this provides testing coverage
                //noinspection ConstantConditions,SimplifiableAssertion
                assertTrue(!m1.equals(null));
                //noinspection MisorderedAssertEqualsArguments
                assertNotEquals(m1, new Object());
                for (String l2 : lines) {
                    if (l2.startsWith("{")) {
                        Mirror m2 = new Mirror(LazyJsonParser.parseUnchecked(l2));
                        Source s2 = new Source(LazyJsonParser.parseUnchecked(l2));
                        if (l1.equals(l2)) {
                            assertEquals(m1, m2);
                            assertEquals(s1, s2);
                        }
                        else {
                            assertNotEquals(m1, m2);
                            assertNotEquals(s1, s2);
                        }
                    }
                }
            }
        }

        lines = ResourceUtils.dataAsLines("ExternalJson.txt");
        for (String l1 : lines) {
            External e1 = new External(LazyJsonParser.parseUnchecked(l1));
            //noinspection EqualsWithItself
            assertEquals(e1, e1);
            //noinspection MisorderedAssertEqualsArguments
            assertNotEquals(e1, null);
            //noinspection MisorderedAssertEqualsArguments
            assertNotEquals(e1, new Object());
            for (String l2 : lines) {
                External e2 = new External(LazyJsonParser.parseUnchecked(l2));
                if (l1.equals(l2)) {
                    assertEquals(e1, e2);
                }
                else {
                    assertNotEquals(e1, e2);
                }
            }
        }

        // coverage for millis maxAge, millis duplicateWindow
        StreamCreator scCov = new StreamCreator(random())
            .maxAge(1111)
            .duplicateWindow(2222);

        assertNotNull(scCov.getName());
        assertEquals(Duration.ofMillis(1111), scCov.getMaxAge());
        assertEquals(Duration.ofMillis(2222), scCov.getDuplicateWindow());
    }

    private static void validateDefaultNullsAndEmpty(StreamCreator testSc) {
        assertNull(testSc.getDescription());
        assertNull(testSc.getMaxAge());
        assertNull(testSc.getTemplateOwner());
        assertNull(testSc.getDuplicateWindow());
        assertNull(testSc.getPlacementCreator());
        assertNull(testSc.getRepublishCreator());
        assertNull(testSc.getSubjectTransformCreator());
        assertNull(testSc.getConsumerLimitsCreator());
        assertNull(testSc.getMirrorCreator());
        assertNull(testSc.getSubjectDeleteMarkerTtl());
        assertNull(testSc.getPersistMode());
    }

    @Test
    public void testConstructionInvalidsCoverage() {
        //noinspection DataFlowIssue passing null to annotated @NonNull
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator((String)null));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxConsumers(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxConsumers(-2));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessages(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessages(-2));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessagesPerSubject(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessagesPerSubject(-2));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxBytes(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxBytes(-2));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxAge(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxAge(-1));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessageSize(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").maxMessageSize(-2));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").replicas(0));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").replicas(6));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").duplicateWindow(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").duplicateWindow(-1));
        assertThrows(IllegalArgumentException.class, () -> new StreamCreator("x").subjectDeleteMarkerTtl(1));
    }

    @Test
    public void testSubjects() {
        StreamCreator creator = new StreamCreator(random());

        String subject = random();
        // subjects(...) replaces
        creator.subjects(subject);
        assertSubjects(creator, subject);

        // subjects(...) replaces
        creator.subjects();
        assertSubjects(creator);

        // subjects(...) replaces
        subject = random();
        creator.subjects(subject);
        assertSubjects(creator, subject);

        // subjects(...) replaces
        creator.subjects((String)null);
        assertSubjects(creator);

        // subjects(...) replaces
        String subjectA = random();
        String subjectB = random();
        creator.subjects(subjectA, subjectB);
        assertSubjects(creator, subjectA, subjectB);

        // subjects(...) replaces
        subjectA = random();
        subjectB = random();
        creator.subjects(subjectA, null, subjectB);
        assertSubjects(creator, subjectA, subjectB);

        // subjects(...) replaces
        subjectA = random();
        subjectB = random();
        creator.subjects(Arrays.asList(subjectA, subjectB));
        assertSubjects(creator, subjectA, subjectB);
    }

    private void assertSubjects(StreamCreator sc, String... subjects) {
        assertEquals(subjects.length, sc.getSubjects().size());
        for (String s : subjects) {
            assertTrue(sc.getSubjects().contains(s));
        }
    }

    @Test
    public void testRetentionPolicy() {
        StreamCreator builder = new StreamCreator(random());
        assertEquals(RetentionPolicy.Limits, builder.getRetentionPolicy());

        builder.retentionPolicy(RetentionPolicy.Limits);
        assertEquals(RetentionPolicy.Limits, builder.getRetentionPolicy());

        builder.retentionPolicy(null);
        assertEquals(RetentionPolicy.Limits, builder.getRetentionPolicy());

        builder.retentionPolicy(RetentionPolicy.Interest);
        assertEquals(RetentionPolicy.Interest, builder.getRetentionPolicy());

        builder.retentionPolicy(RetentionPolicy.WorkQueue);
        assertEquals(RetentionPolicy.WorkQueue, builder.getRetentionPolicy());
    }

    @Test
    public void testCompressionOption() {
        StreamCreator builder = new StreamCreator(random());
        assertEquals(None, builder.getCompressionOption());

        builder.compressionOption(None);
        assertEquals(None, builder.getCompressionOption());

        builder.compressionOption(null);
        assertEquals(None, builder.getCompressionOption());
        assertFalse(builder.toJson().contains("\"compression\""));

        builder.compressionOption(S2);
        assertEquals(S2, builder.getCompressionOption());
        assertTrue(builder.toJson().contains("\"compression\":\"s2\""));
    }

    @Test
    public void testStorageType() {
        StreamCreator builder = new StreamCreator(random());
        assertEquals(StorageType.File, builder.getStorageType());

        builder.storageType(StorageType.Memory);
        assertEquals(StorageType.Memory, builder.getStorageType());

        builder.storageType(null);
        assertEquals(StorageType.File, builder.getStorageType());
    }

    @Test
    public void testDiscardPolicy() {
        StreamCreator builder = new StreamCreator(random());
        assertEquals(DiscardPolicy.Old, builder.getDiscardPolicy());

        builder.discardPolicy(DiscardPolicy.New);
        assertEquals(DiscardPolicy.New, builder.getDiscardPolicy());

        builder.discardPolicy(null);
        assertEquals(DiscardPolicy.Old, builder.getDiscardPolicy());
    }

    @SuppressWarnings("SameParameterValue")
    private void validateTestStreamConfiguration(StreamCreator sc, boolean serverTest, String name) {
        validateTestStreamConfiguration(new StreamConfiguration(LazyJsonParser.parseUnchecked(sc.toJson())), serverTest, name);
    }

    @SuppressWarnings("SameParameterValue")
    private void validateTestStreamConfiguration(String json, boolean serverTest, String name) {
        validateTestStreamConfiguration(new StreamConfiguration(LazyJsonParser.parseUnchecked(json)), serverTest, name);
    }

    private void validateTestStreamConfiguration(StreamConfiguration sc, boolean serverTest, String name) {
        assertEquals(name, sc.getName());
        assertEquals("blah blah", sc.getDescription());
        assertEquals(4, sc.getSubjects().size());
        assertEquals("foo", sc.getSubjects().get(0));
        assertEquals("bar", sc.getSubjects().get(1));
        assertEquals("repub.>", sc.getSubjects().get(2));
        assertEquals("st.>", sc.getSubjects().get(3));

        assertSame(RetentionPolicy.Interest, sc.getRetentionPolicy());
        assertEquals(730, sc.getMaxConsumers());
        assertEquals(731, sc.getMaxMessages());
        assertEquals(741, sc.getMaxMessagesPerSubject());
        assertEquals(732, sc.getMaxBytes());
        assertEquals(731, sc.getMaxMessages());
        assertEquals(732, sc.getMaxBytes());
        assertEquals(Duration.ofNanos(43000000000L), sc.getMaxAge());
        assertEquals(Duration.ofNanos(42000000000L), sc.getDuplicateWindow());
        assertEquals(734, sc.getMaxMessageSize());
        assertEquals(StorageType.Memory, sc.getStorageType());
        assertSame(DiscardPolicy.New, sc.getDiscardPolicy());

        assertTrue(sc.getAllowMessageTtl());

        assertEquals(Duration.ofNanos(73000000000L), sc.getSubjectDeleteMarkerTtl());

        assertNotNull(sc.getPlacement());
        assertEquals("clstr", sc.getPlacement().getCluster());
        assertNotNull(sc.getPlacement().getTags());
        assertEquals(2, sc.getPlacement().getTags().size());
        assertEquals("tag1", sc.getPlacement().getTags().get(0));
        assertEquals("tag2", sc.getPlacement().getTags().get(1));

        assertNotNull(sc.getRepublish());
        assertEquals("repub.>", sc.getRepublish().getSource());
        assertEquals("dest.>", sc.getRepublish().getDestination());
        assertTrue(sc.getRepublish().isHeadersOnly());

        ZonedDateTime zdt = DateTimeUtils.parseDateTime("2020-11-05T19:33:21.163377Z");

        if (serverTest) {
            assertEquals(1, sc.getReplicas());
        }
        else {
            assertTrue(sc.getNoAck());
            assertTrue(sc.getSealed());
            assertTrue(sc.getDenyDelete());
            assertTrue(sc.getDenyPurge());
            assertTrue(sc.isDiscardNewPerSubject());
            assertTrue(sc.getAllowRollup());
            assertTrue(sc.getAllowDirect());
            assertTrue(sc.getMirrorDirect());

            assertEquals(5, sc.getReplicas());
            assertEquals("twnr", sc.getTemplateOwner());

            Mirror mirror = sc.getMirror();
            assertNotNull(mirror);
            assertEquals("eman", mirror.getStreamName());
            assertEquals(736, mirror.getStartSequence());
            assertEquals(zdt, mirror.getStartTime());
            assertEquals("mfsub", mirror.getFilterSubject());

            assertNotNull(mirror.getExternal());
            assertEquals("apithing", mirror.getExternal().getApi());
            assertEquals("dlvrsub", mirror.getExternal().getDeliver());

            validateSubjectTransforms(mirror.getSubjectTransforms(), 2, "m");

            assertNotNull(sc.getSources());
            assertEquals(2, sc.getSources().size());
            validateSource(sc.getSources().get(0), 0, zdt);
            validateSource(sc.getSources().get(1), 1, zdt);

            assertNotNull(sc.getMetadata());
            assertEquals(1, sc.getMetadata().size());
            assertEquals(META_VALUE, sc.getMetadata().get(META_KEY));
            assertEquals(82942, sc.getFirstSequence());

            assertSame(S2, sc.getCompressionOption());

            validateSubjectTransforms(Collections.singletonList(sc.getSubjectTransform()), 1, "sc");

            assertNotNull(sc.getConsumerLimits());
            assertEquals(Duration.ofSeconds(50), sc.getConsumerLimits().getInactiveThreshold());
            assertEquals(42, sc.getConsumerLimits().getMaxAckPending());

            assertTrue(sc.getAllowMessageSchedules());
            assertTrue(sc.getAllowMessageCounter());
            assertTrue(sc.getAllowAtomicPublish());
            assertNotNull(sc.getPersistMode());
            assertSame(PersistMode.Async, sc.getPersistMode());
            assertSame(PersistMode.Async.getMode(), sc.getPersistMode().getMode());
        }
    }

    private void validateSource(Source source, int index, ZonedDateTime zdt) {
        long seq = 737 + index;
        String name = "s" + index;
        assertEquals(name, source.getStreamName());
        assertEquals(seq, source.getStartSequence());
        assertEquals(zdt, source.getStartTime());
        assertEquals(name + "sub", source.getFilterSubject());

        assertNotNull(source.getExternal());
        assertEquals(name + "api", source.getExternal().getApi());
        assertEquals(name + "dlvrsub", source.getExternal().getDeliver());

        validateSubjectTransforms(source.getSubjectTransforms(), 2, name);
    }

    public static void validateSubjectTransforms(List<SubjectTransform> subjectTransforms, int count, String name) {
        assertNotNull(subjectTransforms);
        assertEquals(count, subjectTransforms.size());
        for (int x = 0; x < count; x++) {
            SubjectTransform st = subjectTransforms.get(x);
            assertEquals(name + "_st_src" + x, st.getSource());
            assertEquals(name + "_st_dest" + x, st.getDestination());
        }
    }

//    @Test
//    public void testPlacement() {
//        assertFalse(Placement.builder().build().hasData());
//        assertFalse(Placement.builder().cluster(null).build().hasData());
//        assertFalse(Placement.builder().cluster("").build().hasData());
//        assertFalse(Placement.builder().tags((List<String>)null).build().hasData());
//        assertFalse(Placement.builder().tags(new ArrayList<>()).build().hasData());
//        assertFalse(Placement.builder().tags().build().hasData());
//        assertFalse(Placement.builder().tags((String[])null).build().hasData());
//        String[] a = new String[0];
//        assertFalse(Placement.builder().tags(a).build().hasData());
//        a = new String[2];
//        a[0] = "";
//        assertFalse(Placement.builder().tags(a).build().hasData());
//
//        Placement p = Placement.builder().cluster("cluster").build();
//        assertEquals("cluster", p.getCluster());
//        assertNull(p.getTags());
//        assertTrue(p.hasData());
//
//        p = Placement.builder().cluster("cluster").build();
//        assertEquals("cluster", p.getCluster());
//        assertNull(p.getTags());
//        assertTrue(p.hasData());
//
//        p = Placement.builder().tags("a", "b").build();
//        assertNull(p.getCluster());
//        assertNotNull(p.getTags());
//        assertEquals(2, p.getTags().size());
//        assertTrue(p.hasData());
//
//        p = Placement.builder().tags("a", "b").build();
//        assertNull(p.getCluster());
//        assertNotNull(p.getTags());
//        assertEquals(2, p.getTags().size());
//        assertTrue(p.hasData());
//
//        p = Placement.builder().cluster("cluster").tags(Arrays.asList("a", "b")).build();
//        assertEquals("cluster", p.getCluster());
//        assertNotNull(p.getTags());
//        assertEquals(2, p.getTags().size());
//        assertTrue(p.hasData());
//
//        String s = p.toString();
//        assertTrue(s != null && !s.isEmpty()); // COVERAGE
//
//        // COVERAGE
//        p = new Placement("cluster", null);
//        assertEquals("cluster", p.getCluster());
//        assertNull(p.getTags());
//        assertTrue(p.hasData());
//
//        p = new Placement("cluster", new ArrayList<>());
//        assertEquals("cluster", p.getCluster());
//        assertNull(p.getTags());
//        assertTrue(p.hasData());
//
//        p = new Placement("cluster", Arrays.asList("a", "b"));
//        assertEquals("cluster", p.getCluster());
//        assertNotNull(p.getTags());
//        assertEquals(2, p.getTags().size());
//        assertTrue(p.hasData());
//    }
//
//    @Test
//    public void testRepublish() {
//        assertThrows(IllegalArgumentException.class, () -> Republish.builder().build());
//        assertThrows(IllegalArgumentException.class, () -> Republish.builder().source("src.>").build());
//        assertThrows(IllegalArgumentException.class, () -> Republish.builder().destination("dest.>").build());
//
//        Republish r = Republish.builder().source("src.>").destination("dest.>").build();
//        assertEquals("src.>", r.getSource());
//        assertEquals("dest.>", r.getDestination());
//        assertFalse(r.isHeadersOnly());
//
//        r = Republish.builder().source("src.>").destination("dest.>").headersOnly(true).build();
//        assertEquals("src.>", r.getSource());
//        assertEquals("dest.>", r.getDestination());
//        assertTrue(r.isHeadersOnly());
//    }
//
//    @Test
//    public void testSubjectTransform() {
//        SubjectTransform st = SubjectTransform.builder().source("src.>").destination("dest.>").build();
//        assertEquals("src.>", st.getSource());
//        assertEquals("dest.>", st.getDestination());
//
//        assertThrows(IllegalArgumentException.class, () -> SubjectTransform.builder().build());
//        assertThrows(IllegalArgumentException.class, () -> SubjectTransform.builder().source("source").build());
//        assertThrows(IllegalArgumentException.class, () -> SubjectTransform.builder().destination("dest").build());
//
//        EqualsVerifier.simple().forClass(SubjectTransform.class).verify();
//    }
//
//    @Test
//    public void testConsumerLimits() {
//        ConsumerLimits cl = ConsumerLimits.builder().build();
//        assertNull(cl.getInactiveThreshold());
//        assertEquals(-1, cl.getMaxAckPending());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(Duration.ofMillis(0)).build();
//        assertEquals(Duration.ZERO, cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(0L).build();
//        assertEquals(Duration.ZERO, cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(Duration.ofMillis(1)).build();
//        assertEquals(Duration.ofMillis(1), cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(1L).build();
//        assertEquals(Duration.ofMillis(1), cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(Duration.ofMillis(-1)).build();
//        assertEquals(DURATION_UNSET, cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().inactiveThreshold(-1).build();
//        assertEquals(DURATION_UNSET, cl.getInactiveThreshold());
//
//        cl = ConsumerLimits.builder().maxAckPending(STANDARD_MIN).build();
//        assertEquals(STANDARD_MIN, cl.getMaxAckPending());
//
//        cl = ConsumerLimits.builder().maxAckPending(-1).build();
//        assertEquals(-1, cl.getMaxAckPending());
//
//        cl = ConsumerLimits.builder().maxAckPending(-2).build();
//        assertEquals(-1, cl.getMaxAckPending());
//
//        cl = ConsumerLimits.builder().maxAckPending((Long)null).build();
//        assertEquals(-1, cl.getMaxAckPending());
//
//        cl = ConsumerLimits.builder().maxAckPending(Long.MAX_VALUE).build();
//        assertEquals(Integer.MAX_VALUE, cl.getMaxAckPending());
//    }

    @Test
    public void equalsContract() {
        // really testing SourceBase
        EqualsVerifier.simple().forClass(Mirror.class).verify();
        EqualsVerifier.simple().forClass(Source.class).verify();
    }

    public static StreamConfiguration getStreamConfigurationFromJson(String jsonFile) throws JsonParseException {
        return new StreamConfiguration(LazyJsonParser.parse(ResourceUtils.dataAsString(jsonFile)));
    }
}
