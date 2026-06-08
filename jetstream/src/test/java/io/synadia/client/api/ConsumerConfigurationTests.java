package io.synadia.client.api;

import io.nats.json.DateTimeUtils;
import io.nats.json.JsonParseException;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.utils.TestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.synadia.client.utils.ResourceUtils.dataAsString;
import static org.junit.jupiter.api.Assertions.*;

public class ConsumerConfigurationTests extends TestBase {

    static final String STREAM = "test-stream";
    static final String DELIVER_SUBJECT = "deliver-subject";

    static final ZonedDateTime TEST_START_TIME = DateTimeUtils.parseDateTime("2020-11-05T19:33:21.163377000Z");
    static final ZonedDateTime TEST_PAUSE_UNTIL = DateTimeUtils.parseDateTime("2024-03-02T10:43:32.062847087Z");

    static final String CONSUMER_FIELDS_FILTER_SUBJECT_JSON = dataAsString("ConsumerFieldsFilterSubject.json");
    static final String CONSUMER_FIELDS_FILTER_SUBJECTS_JSON = dataAsString("ConsumerFieldsFilterSubjects.json");

    // ----------------------------------------------------------------------------------------------------
    // Setters (on ConsumerCreator subclasses)
    // ----------------------------------------------------------------------------------------------------

    static <T extends ConsumerCreator<T>> void setConsumerCreatorFields(T creator, boolean multipleFilterSubjects) {
        creator.description("foo-desc");
        creator.deliverPolicy(DeliverPolicy.All);
        creator.startSequence(42);
        creator.startTime(TEST_START_TIME);
        creator.replayPolicy(ReplayPolicy.Original);
        creator.sampleFrequency("sample_freq-value");
        creator.rateLimit(73);
        creator.inactiveThreshold(Duration.ofSeconds(50));
        creator.headersOnly(true);
        creator.metadata(Map.of("meta-test-key", "meta-test-value"));
        if (multipleFilterSubjects) {
            creator.filterSubjects("sub.a", "sub.b");
        }
        else {
            creator.filterSubject("sub.single");
        }
    }

    static <T extends AbstractEphemeralConsumerCreator<T>> void setAbstractEphemeralFields(T creator) {
        creator.name("foo-name");
        creator.ackPolicy(AckPolicy.All);
        creator.maxDeliver(10);
        creator.flowControl(Duration.ofSeconds(20));
        creator.numReplicas(5);
        creator.pauseUntil(TEST_PAUSE_UNTIL);
        creator.memStorage(true);
        creator.backoff(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3));
        creator.ackWait(Duration.ofSeconds(5));
        creator.maxAckPending(42);
    }

    static void setPullSpecificFields(PullConsumerCreator creator) {
        creator.maxPullWaiting(128);
        creator.maxBatch(55);
        creator.maxBytes(6666666666L);
        creator.maxExpires(Duration.ofSeconds(40));
        creator.priorityGroups("pgroup1", "pgroup2");
        creator.priorityPolicy(PriorityPolicy.Overflow);
        creator.priorityTimeout(Duration.ofSeconds(60));
    }

    // ----------------------------------------------------------------------------------------------------
    // Verifiers for ConsumerCreator (eager)
    // ----------------------------------------------------------------------------------------------------

    static void verifyConsumerCreatorFields(ConsumerCreator<?> cc, boolean multipleFilterSubjects) {
        assertEquals("foo-desc", cc.getDescription());
        assertEquals(DeliverPolicy.All, cc.getDeliverPolicy());
        assertEquals(42, cc.getStartSequence());
        assertEquals(TEST_START_TIME, cc.getStartTime());
        assertEquals(ReplayPolicy.Original, cc.getReplayPolicy());
        assertEquals("sample_freq-value", cc.getSampleFrequency());
        assertEquals(73, cc.getRateLimit());
        assertEquals(Duration.ofSeconds(50), cc.getInactiveThreshold());
        assertTrue(cc.isHeadersOnly());
        assertEquals(Map.of("meta-test-key", "meta-test-value"), cc.getMetadata());
        verifyFilterSubjects(cc.getFilterSubject(), cc.getFilterSubjects(), cc.hasMultipleFilterSubjects(), multipleFilterSubjects);
    }

    static void verifyAbstractEphemeralFields(ConsumerCreator<?> cc) {
        assertEquals("foo-name", cc.getName());
        assertEquals(AckPolicy.All, cc.getAckPolicy());
        assertEquals(10, cc.getMaxDeliver());
        assertTrue(cc.isFlowControl());
        assertEquals(Duration.ofSeconds(20), cc.getIdleHeartbeat());
        assertEquals(5, cc.getNumReplicas());
        assertEquals(TEST_PAUSE_UNTIL, cc.getPauseUntil());
        assertTrue(cc.isMemStorage());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)), cc.getBackoff());
        verifyAckFields(cc.getAckWait(), cc.getMaxAckPending());
    }

    static void verifyPullSpecificFields(ConsumerCreator<?> cc) {
        assertEquals(128, cc.getMaxPullWaiting());
        assertEquals(55, cc.getMaxBatch());
        assertEquals(6666666666L, cc.getMaxBytes());
        assertEquals(Duration.ofSeconds(40), cc.getMaxExpires());
        assertEquals(List.of("pgroup1", "pgroup2"), cc.getPriorityGroups());
        assertEquals(PriorityPolicy.Overflow, cc.getPriorityPolicy());
        assertEquals(Duration.ofSeconds(60), cc.getPriorityTimeout());
    }

    // ----------------------------------------------------------------------------------------------------
    // Verifiers for ConsumerConfiguration (lazy)
    // ----------------------------------------------------------------------------------------------------

    static void verifyConsumerCreatorFields(ConsumerConfiguration cc, boolean multipleFilterSubjects) {
        assertEquals("foo-desc", cc.getDescription());
        assertEquals(DeliverPolicy.All, cc.getDeliverPolicy());
        assertEquals(42, cc.getStartSequence());
        assertEquals(TEST_START_TIME, cc.getStartTime());
        assertEquals(ReplayPolicy.Original, cc.getReplayPolicy());
        assertEquals("sample_freq-value", cc.getSampleFrequency());
        assertEquals(73, cc.getRateLimit());
        assertEquals(Duration.ofSeconds(50), cc.getInactiveThreshold());
        assertTrue(cc.isHeadersOnly());
        assertEquals(Map.of("meta-test-key", "meta-test-value"), cc.getMetadata());
        verifyFilterSubjects(cc.getFilterSubject(), cc.getFilterSubjects(), cc.hasMultipleFilterSubjects(), multipleFilterSubjects);
    }

    static void verifyAbstractEphemeralFields(ConsumerConfiguration cc, boolean multipleFilterSubjects) {
        assertEquals("foo-name", cc.getName());
        assertEquals(AckPolicy.All, cc.getAckPolicy());
        assertEquals(10, cc.getMaxDeliver());
        assertTrue(cc.isFlowControl());
        assertEquals(Duration.ofSeconds(20), cc.getIdleHeartbeat());
        assertEquals(5, cc.getNumReplicas());
        assertEquals(TEST_PAUSE_UNTIL, cc.getPauseUntil());
        assertTrue(cc.isMemStorage());
        assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)), cc.getBackoff());
        verifyAckFields(cc.getAckWait(), cc.getMaxAckPending());
    }

    static void verifyPullSpecificFields(ConsumerConfiguration cc) {
        assertEquals(128, cc.getMaxPullWaiting());
        assertEquals(55, cc.getMaxBatch());
        assertEquals(6666666666L, cc.getMaxBytes());
        assertEquals(Duration.ofSeconds(40), cc.getMaxExpires());
        assertEquals(List.of("pgroup1", "pgroup2"), cc.getPriorityGroups());
        assertEquals(PriorityPolicy.Overflow, cc.getPriorityPolicy());
        assertEquals(Duration.ofSeconds(60), cc.getPriorityTimeout());
    }

    static void verifyDurableField(ConsumerConfiguration cc) {
        assertEquals("foo-name", cc.getDurable());
    }

    static void verifyPushSpecificFields(ConsumerConfiguration cc) {
        assertEquals("deliver.subject", cc.getDeliverSubject());
        assertEquals("deliver-group", cc.getDeliverGroup());
    }

    // ----------------------------------------------------------------------------------------------------
    // Shared helpers
    // ----------------------------------------------------------------------------------------------------

    static void verifyFilterSubjects(String filterSubject, List<String> filterSubjects, boolean hasMultiple, boolean multipleFilterSubjects) {
        if (multipleFilterSubjects) {
            assertEquals(List.of("sub.a", "sub.b"), filterSubjects);
            assertTrue(hasMultiple);
            assertNull(filterSubject);
        }
        else {
            assertEquals("sub.single", filterSubject);
            assertEquals(List.of("sub.single"), filterSubjects);
            assertFalse(hasMultiple);
        }
    }

    static void verifyAckFields(Duration ackWait, long maxAckPending) {
        assertEquals(Duration.ofSeconds(5), ackWait);
        assertEquals(42, maxAckPending);
    }

    // ----------------------------------------------------------------------------------------------------
    // Constructor / defaults tests
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testConsumerFields() {
        // FilterSubject variant via pull
        PullConsumerCreator pull = new PullConsumerCreator(STREAM);
        setConsumerCreatorFields(pull, false);
        setAbstractEphemeralFields(pull);
        setPullSpecificFields(pull);
        verifyConsumerCreatorFields(pull, false);
        verifyAbstractEphemeralFields(pull);
        verifyPullSpecificFields(pull);

        // FilterSubjects variant via push
        PushConsumerCreator push = new PushConsumerCreator(STREAM);
        setConsumerCreatorFields(push, true);
        setAbstractEphemeralFields(push);
        verifyConsumerCreatorFields(push, true);
        verifyAbstractEphemeralFields(push);
    }

    // ----------------------------------------------------------------------------------------------------
    // ConsumerConfiguration
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testConsumerConfiguration() throws JsonParseException {
        // Default instance
        ConsumerConfiguration def = ConsumerConfiguration.getDefaultInstance();
        assertNotNull(def);
        assertNotNull(def.getName());
        assertEquals(DeliverPolicy.All, def.getDeliverPolicy());
        assertEquals(AckPolicy.Explicit, def.getAckPolicy());
        assertEquals(ReplayPolicy.Instant, def.getReplayPolicy());
        assertEquals(PriorityPolicy.None, def.getPriorityPolicy());

        // FilterSubject variant - set, verify, round trip via json, verify
        PullConsumerCreator creator1 = new PullConsumerCreator(STREAM);
        setConsumerCreatorFields(creator1, false);
        setAbstractEphemeralFields(creator1);
        setPullSpecificFields(creator1);
        verifyConsumerCreatorFields(creator1, false);
        verifyAbstractEphemeralFields(creator1);
        verifyPullSpecificFields(creator1);

        String json1 = creator1.toJson();
        LazyJsonValue ljv = LazyJsonParser.parse(json1);
        ConsumerConfiguration config1 = new ConsumerConfiguration(ljv);
        verifyConsumerCreatorFields(config1, false);
        verifyAbstractEphemeralFields(config1, false);
        verifyPullSpecificFields(config1);

        // Parse from JSON data file and verify
        LazyJsonValue filterLjv = LazyJsonParser.parse(CONSUMER_FIELDS_FILTER_SUBJECT_JSON);
        ConsumerConfiguration fromFile1 = new ConsumerConfiguration(filterLjv);
        verifyConsumerCreatorFields(fromFile1, false);
        verifyAbstractEphemeralFields(fromFile1, false);
        verifyPullSpecificFields(fromFile1);
        verifyDurableField(fromFile1);
        verifyPushSpecificFields(fromFile1);

        // FilterSubjects variant - set, verify, round trip via json, verify
        PullConsumerCreator creator2 = new PullConsumerCreator(STREAM);
        setConsumerCreatorFields(creator2, true);
        setAbstractEphemeralFields(creator2);
        setPullSpecificFields(creator2);
        verifyConsumerCreatorFields(creator2, true);
        verifyAbstractEphemeralFields(creator2);
        verifyPullSpecificFields(creator2);

        String json2 = creator2.toJson();
        LazyJsonValue cffsLjv2 = LazyJsonParser.parse(json2);
        ConsumerConfiguration config2 = new ConsumerConfiguration(cffsLjv2);
        verifyConsumerCreatorFields(config2, true);
        verifyAbstractEphemeralFields(config2, true);
        verifyPullSpecificFields(config2);

        // Parse from JSON data file and verify
        filterLjv = LazyJsonParser.parse(CONSUMER_FIELDS_FILTER_SUBJECTS_JSON);
        ConsumerConfiguration fromFile2 = new ConsumerConfiguration(filterLjv);
        verifyConsumerCreatorFields(fromFile2, true);
        verifyAbstractEphemeralFields(fromFile2, true);
        verifyPullSpecificFields(fromFile2);
        verifyDurableField(fromFile2);
        verifyPushSpecificFields(fromFile2);

        // Non-null guarantees
        assertNotNull(fromFile2.getFilterSubjects());
        assertNotNull(fromFile2.getPriorityGroups());
        assertNotNull(fromFile2.getBackoff());
        assertNotNull(fromFile2.getMetadata());
    }

    // ----------------------------------------------------------------------------------------------------
    // Pull Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPullConsumerCreator() {
        PullConsumerCreator cc = new PullConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);
        setAbstractEphemeralFields(cc);
        verifyAbstractEphemeralFields(cc);

        Duration dur = Duration.ofSeconds(10);

        cc.name(null);
        cc.durable("my-durable");
        assertEquals("my-durable", cc.getDurable());

        cc.maxExpires(dur);
        assertEquals(dur, cc.getMaxExpires());
        cc.maxExpires(5000);
        assertEquals(Duration.ofMillis(5000), cc.getMaxExpires());

        cc.maxPullWaiting(50L);
        assertEquals(50, cc.getMaxPullWaiting());
        cc.maxPullWaiting(75);
        assertEquals(75, cc.getMaxPullWaiting());

        cc.maxBatch(100L);
        assertEquals(100, cc.getMaxBatch());
        cc.maxBatch(200);
        assertEquals(200, cc.getMaxBatch());

        cc.maxBytes(1024L);
        assertEquals(1024, cc.getMaxBytes());
        cc.maxBytes(2048);
        assertEquals(2048, cc.getMaxBytes());

        cc.priorityGroups("g1", "g2");
        assertEquals(List.of("g1", "g2"), cc.getPriorityGroups());
        cc.priorityGroups(List.of("g3"));
        assertEquals(List.of("g3"), cc.getPriorityGroups());

        cc.priorityPolicy(PriorityPolicy.Overflow);
        assertEquals(PriorityPolicy.Overflow, cc.getPriorityPolicy());

        cc.priorityTimeout(dur);
        assertEquals(dur, cc.getPriorityTimeout());
        cc.priorityTimeout(3000);
        assertEquals(Duration.ofMillis(3000), cc.getPriorityTimeout());

        // Chaining
        cc.name(null);
        assertSame(cc, cc.durable("d").maxExpires(dur).maxBatch(10));
    }

    // ----------------------------------------------------------------------------------------------------
    // Push Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPushConsumerCreator() {
        PushConsumerCreator cc = new PushConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);
        setAbstractEphemeralFields(cc);
        verifyAbstractEphemeralFields(cc);

        cc.name(null);
        cc.durable("my-durable");
        assertEquals("my-durable", cc.getDurable());

        cc.deliverSubject(DELIVER_SUBJECT);
        assertEquals(DELIVER_SUBJECT, cc.getDeliverSubject());

        cc.deliverGroup("my-group");
        assertEquals("my-group", cc.getDeliverGroup());

        cc.name(null);
        PushConsumerCreator result = cc.durable("d").deliverSubject("s").deliverGroup("g");
        assertSame(cc, result);
    }

    // ----------------------------------------------------------------------------------------------------
    // Pull Ephemeral Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPullEphemeralConsumerCreator() {
        PullConsumerCreator cc = new PullConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());
        assertNull(cc.getDurable());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);
        setAbstractEphemeralFields(cc);
        verifyAbstractEphemeralFields(cc);

        Duration dur = Duration.ofSeconds(10);

        cc.maxExpires(dur);
        assertEquals(dur, cc.getMaxExpires());
        cc.maxExpires(5000);
        assertEquals(Duration.ofMillis(5000), cc.getMaxExpires());

        cc.maxPullWaiting(50L);
        assertEquals(50, cc.getMaxPullWaiting());
        cc.maxPullWaiting(75);
        assertEquals(75, cc.getMaxPullWaiting());

        cc.maxBatch(100L);
        assertEquals(100, cc.getMaxBatch());
        cc.maxBatch(200);
        assertEquals(200, cc.getMaxBatch());

        cc.maxBytes(1024L);
        assertEquals(1024, cc.getMaxBytes());
        cc.maxBytes(2048);
        assertEquals(2048, cc.getMaxBytes());

        cc.priorityGroups("g1", "g2");
        assertEquals(List.of("g1", "g2"), cc.getPriorityGroups());
        cc.priorityGroups(List.of("g3"));
        assertEquals(List.of("g3"), cc.getPriorityGroups());

        cc.priorityPolicy(PriorityPolicy.Overflow);
        assertEquals(PriorityPolicy.Overflow, cc.getPriorityPolicy());

        cc.priorityTimeout(dur);
        assertEquals(dur, cc.getPriorityTimeout());
        cc.priorityTimeout(3000);
        assertEquals(Duration.ofMillis(3000), cc.getPriorityTimeout());
    }

    // ----------------------------------------------------------------------------------------------------
    // Push Ephemeral Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPushEphemeralConsumerCreator() {
        PushConsumerCreator cc = new PushConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);
        setAbstractEphemeralFields(cc);
        verifyAbstractEphemeralFields(cc);

        cc.deliverSubject(DELIVER_SUBJECT);
        assertEquals(DELIVER_SUBJECT, cc.getDeliverSubject());

        cc.deliverGroup("my-group");
        assertEquals("my-group", cc.getDeliverGroup());

        assertNull(cc.getDurable());
    }

    // ----------------------------------------------------------------------------------------------------
    // Pull Ordered Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPullOrderedConsumerCreator() {
        PullOrderedConsumerCreator cc = new PullOrderedConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());

        assertEquals(AckPolicy.None, cc.getAckPolicy());
        assertEquals(1, cc.getMaxDeliver());
        assertEquals(Duration.ofHours(22), cc.getAckWait());
        assertTrue(cc.isMemStorage());
        assertEquals(1, cc.getNumReplicas());
        assertEquals(null, cc.getIdleHeartbeat());

        assertNull(cc.getNamePrefix());
        assertNotNull(cc.getName());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);

        cc.namePrefix("my-prefix");
        assertEquals("my-prefix", cc.getNamePrefix());
        assertTrue(cc.getName().startsWith("my-prefix-"));

        cc.namePrefix(null);
        assertNull(cc.getNamePrefix());
        assertNotNull(cc.getName());
        assertFalse(cc.getName().startsWith("my-prefix-"));

        Duration dur = Duration.ofSeconds(10);

        cc.maxExpires(dur);
        assertEquals(dur, cc.getMaxExpires());
        cc.maxExpires(5000);
        assertEquals(Duration.ofMillis(5000), cc.getMaxExpires());

        cc.maxPullWaiting(50L);
        assertEquals(50, cc.getMaxPullWaiting());
        cc.maxPullWaiting(75);
        assertEquals(75, cc.getMaxPullWaiting());

        cc.maxBatch(100L);
        assertEquals(100, cc.getMaxBatch());
        cc.maxBatch(200);
        assertEquals(200, cc.getMaxBatch());

        cc.maxBytes(1024L);
        assertEquals(1024, cc.getMaxBytes());
        cc.maxBytes(2048);
        assertEquals(2048, cc.getMaxBytes());

        cc.priorityGroups("g1", "g2");
        assertEquals(List.of("g1", "g2"), cc.getPriorityGroups());
        cc.priorityGroups(List.of("g3"));
        assertEquals(List.of("g3"), cc.getPriorityGroups());

        cc.priorityPolicy(PriorityPolicy.Overflow);
        assertEquals(PriorityPolicy.Overflow, cc.getPriorityPolicy());

        cc.priorityTimeout(dur);
        assertEquals(dur, cc.getPriorityTimeout());
        cc.priorityTimeout(3000);
        assertEquals(Duration.ofMillis(3000), cc.getPriorityTimeout());
    }

    // ----------------------------------------------------------------------------------------------------
    // Push Ordered Consumer Creator
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPushOrderedConsumerCreator() {
        PushOrderedConsumerCreator cc = new PushOrderedConsumerCreator(STREAM);
        assertEquals(STREAM, cc.getStream());

        assertEquals(AckPolicy.None, cc.getAckPolicy());
        assertEquals(1, cc.getMaxDeliver());
        assertEquals(Duration.ofHours(22), cc.getAckWait());
        assertTrue(cc.isMemStorage());
        assertEquals(1, cc.getNumReplicas());
        assertEquals(Duration.ofMillis(AbstractOrderedConsumerCreator.DEFAULT_ORDERED_HEARTBEAT), cc.getIdleHeartbeat());

        assertNull(cc.getNamePrefix());
        assertNotNull(cc.getName());

        setConsumerCreatorFields(cc, true);
        verifyConsumerCreatorFields(cc, true);

        cc.namePrefix("my-prefix");
        assertEquals("my-prefix", cc.getNamePrefix());
        assertTrue(cc.getName().startsWith("my-prefix-"));

        cc.namePrefix(null);
        assertNull(cc.getNamePrefix());
        assertNotNull(cc.getName());
        assertFalse(cc.getName().startsWith("my-prefix-"));

        cc.deliverSubject(DELIVER_SUBJECT);
        assertEquals(DELIVER_SUBJECT, cc.getDeliverSubject());
    }

    // ----------------------------------------------------------------------------------------------------
    // Chaining returns correct type
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testChainingReturnTypes() {
        PullConsumerCreator pull = new PullConsumerCreator(STREAM);
        assertSame(pull, pull.description("d").deliverPolicy(DeliverPolicy.New).name("dur").durable("dur"));

        PushConsumerCreator push = new PushConsumerCreator(STREAM);
        assertSame(push, push.description("d").deliverPolicy(DeliverPolicy.New).name("dur").durable("dur"));

        PullConsumerCreator pullEph = new PullConsumerCreator(STREAM);
        assertSame(pullEph, pullEph.description("d").deliverPolicy(DeliverPolicy.New).name("n"));

        PushConsumerCreator pushEph = new PushConsumerCreator(STREAM);
        assertSame(pushEph, pushEph.description("d").deliverPolicy(DeliverPolicy.New).name("n"));

        PullOrderedConsumerCreator pullOrd = new PullOrderedConsumerCreator(STREAM);
        assertSame(pullOrd, pullOrd.description("d").deliverPolicy(DeliverPolicy.New).namePrefix("p"));

        PushOrderedConsumerCreator pushOrd = new PushOrderedConsumerCreator(STREAM);
        assertSame(pushOrd, pushOrd.description("d").deliverPolicy(DeliverPolicy.New).namePrefix("p"));
    }
}
