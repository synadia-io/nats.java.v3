package io.synadia.client.api;

import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.synadia.client.MessageHandler;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.NatsMessage;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive equals/hashCode coverage test for the io.synadia.client.api package.
 *
 * <p>Strategy:
 * <ul>
 *   <li>For LazyApiObject subclasses: equality is delegated to the wrapped LazyJsonValue.
 *       Two distinct prefab LazyJsonValue instances drive EqualsVerifier coverage. Lazy
 *       underscore cached fields are ignored as they are derived from {@code ljv}.</li>
 *   <li>For ApiResponse subclasses: equality is inherited from ApiResponse (compares the
 *       same {@code ljv} field). We construct via the Message-based or LazyJsonValue-based
 *       constructors and use manual assertions to exercise the contract.</li>
 *   <li>For Plain POJOs and Creator classes: EqualsVerifier handles them where possible;
 *       manual tests fill the gaps.</li>
 *   <li>For the ConsumerCreator hierarchy: the non-final equals uses {@code getClass()}
 *       and AbstractOrderedConsumerCreator chains via {@code super.equals()}. We test the
 *       four terminal concrete classes manually because EqualsVerifier struggles with the
 *       protected setter API and lazily initialized {@code name} field set in constructors.</li>
 * </ul>
 */
@SuppressWarnings({"EqualsWithItself", "MisorderedAssertEqualsArguments", "AssertBetweenInconvertibleTypes"})
public class ApiEqualityAndHashCodeCoverageTest {

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------

    /** Parse JSON without throwing checked exceptions. */
    private static LazyJsonValue lj(String json) {
        return LazyJsonParser.parseUnchecked(json);
    }

    /** Two distinct LazyJsonValue prefabs for EqualsVerifier. */
    private static final LazyJsonValue LJV_RED = lj("{\"a\":1}");
    private static final LazyJsonValue LJV_BLUE = lj("{\"b\":2}");

    private static NatsMessage msg(String json) {
        return new NatsMessage("subj", null, json.getBytes(StandardCharsets.US_ASCII));
    }

    /** Verify the full equals/hashCode contract for two value-equal instances and one distinct one. */
    private static void assertEqualsContract(Object a, Object b, Object different) {
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(different);

        // reflexive
        assertEquals(a, a);

        // symmetric & equal content -> equal & same hashCode
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());

        // not equal to a distinct-value instance
        assertNotEquals(a, different);
        assertNotEquals(different, a);

        // null parameter
        assertNotEquals(a, null);

        // foreign type
        //noinspection AssertEqualsBetweenInconvertibleTypes
        assertNotEquals(a, "some-foreign-string");
    }

    // ----------------------------------------------------------------------------------------------------
    // LazyApiObject subclasses (equals/hashCode delegated to ljv via base class)
    // ----------------------------------------------------------------------------------------------------

    private static <T> void verifyLazyApiObject(Class<T> cls, String... ignoredFields) {
        EqualsVerifier.simple()
            .withPrefabValues(LazyJsonValue.class, LJV_RED, LJV_BLUE)
            .forClass(cls)
            .withNonnullFields("ljv")  // ljv is final and never null (LazyApiObject ctor enforces)
            .withIgnoredFields(ignoredFields)
            .verify();
    }

    @Test
    public void testAccountLimits_equalsContract() {
        verifyLazyApiObject(AccountLimits.class);
    }

    @Test
    public void testAccountTier_equalsContract() {
        verifyLazyApiObject(AccountTier.class, "_limits");
    }

    @Test
    public void testApiStats_equalsContract() {
        verifyLazyApiObject(ApiStats.class);
    }

    @Test
    public void testClusterInfo_equalsContract() {
        verifyLazyApiObject(ClusterInfo.class, "_replicas");
    }

    @Test
    public void testConsumerConfiguration_equalsContract() {
        // DEFAULT_INSTANCE is a static field; EqualsVerifier ignores statics automatically.
        verifyLazyApiObject(ConsumerConfiguration.class);
    }

    @Test
    public void testConsumerLimits_equalsContract() {
        verifyLazyApiObject(ConsumerLimits.class);
    }

    @Test
    public void testExternal_equalsContract() {
        verifyLazyApiObject(External.class);
    }

    @Test
    public void testLostStreamData_equalsContract() {
        verifyLazyApiObject(LostStreamData.class);
    }

    @Test
    public void testPlacement_equalsContract() {
        verifyLazyApiObject(Placement.class);
    }

    @Test
    public void testPriorityGroupState_equalsContract() {
        verifyLazyApiObject(PriorityGroupState.class);
    }

    @Test
    public void testRepublish_equalsContract() {
        verifyLazyApiObject(Republish.class);
    }

    @Test
    public void testSequenceInfo_equalsContract() {
        verifyLazyApiObject(SequenceInfo.class);
    }

    @Test
    public void testStreamAlternate_equalsContract() {
        verifyLazyApiObject(StreamAlternate.class);
    }

    @Test
    public void testStreamConfiguration_equalsContract() {
        // Static EMPTY field is ignored by EqualsVerifier automatically.
        verifyLazyApiObject(StreamConfiguration.class);
    }

    @Test
    public void testStreamState_equalsContract() {
        verifyLazyApiObject(StreamState.class, "_subjects", "_subjectMap");
    }

    @Test
    public void testSubjectTransform_equalsContract() {
        verifyLazyApiObject(SubjectTransform.class);
    }

    // ---- Concrete subclasses of abstract PeerInfo / StreamSource / StreamSourceInfo ----

    @Test
    public void testReplica_equalsContract() {
        verifyLazyApiObject(Replica.class);
    }

    @Test
    public void testMirror_equalsContract() {
        verifyLazyApiObject(Mirror.class);
    }

    @Test
    public void testSource_equalsContract() {
        verifyLazyApiObject(Source.class);
    }

    @Test
    public void testMirrorInfo_equalsContract() {
        verifyLazyApiObject(MirrorInfo.class);
    }

    @Test
    public void testSourceInfo_equalsContract() {
        verifyLazyApiObject(SourceInfo.class);
    }

    // ----------------------------------------------------------------------------------------------------
    // ApiResponse hierarchy
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testAccountStatistics_equalsAndHashCode() {
        String j1 = "{\"memory\":100,\"storage\":200}";
        String j2 = "{\"memory\":999}";
        AccountStatistics a = new AccountStatistics(msg(j1));
        AccountStatistics b = new AccountStatistics(msg(j1));
        AccountStatistics c = new AccountStatistics(msg(j2));
        assertEqualsContract(a, b, c);
    }

    @Test
    public void testConsumerInfo_equalsAndHashCode() {
        String j1 = "{\"name\":\"c1\",\"stream_name\":\"S1\"}";
        String j2 = "{\"name\":\"c2\",\"stream_name\":\"S2\"}";
        ConsumerInfo a = new ConsumerInfo(lj(j1));
        ConsumerInfo b = new ConsumerInfo(lj(j1));
        ConsumerInfo c = new ConsumerInfo(lj(j2));
        assertEqualsContract(a, b, c);
    }

    @Test
    public void testConsumerPauseResponse_equalsAndHashCode() {
        String j1 = "{\"paused\":true,\"pause_until\":\"2026-01-01T00:00:00Z\"}";
        String j2 = "{\"paused\":false}";
        ConsumerPauseResponse a = new ConsumerPauseResponse(msg(j1));
        ConsumerPauseResponse b = new ConsumerPauseResponse(msg(j1));
        ConsumerPauseResponse c = new ConsumerPauseResponse(msg(j2));
        assertEqualsContract(a, b, c);
    }

    @Test
    public void testPublishAck_equalsAndHashCode() {
        String j1 = "{\"stream\":\"S1\",\"seq\":1}";
        String j2 = "{\"stream\":\"S2\",\"seq\":2}";
        try {
            PublishAck a = new PublishAck(msg(j1));
            PublishAck b = new PublishAck(msg(j1));
            PublishAck c = new PublishAck(msg(j2));
            assertEqualsContract(a, b, c);
        }
        catch (IOException | JetStreamApiException e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testPurgeResponse_equalsAndHashCode() {
        String j1 = "{\"success\":true,\"purged\":10}";
        String j2 = "{\"success\":false}";
        PurgeResponse a = new PurgeResponse(msg(j1));
        PurgeResponse b = new PurgeResponse(msg(j1));
        PurgeResponse c = new PurgeResponse(msg(j2));
        assertEqualsContract(a, b, c);
    }

    @Test
    public void testSuccessApiResponse_equalsAndHashCode() {
        String j1 = "{\"success\":true}";
        String j2 = "{\"success\":false}";
        SuccessApiResponse a = new SuccessApiResponse(msg(j1));
        SuccessApiResponse b = new SuccessApiResponse(msg(j1));
        SuccessApiResponse c = new SuccessApiResponse(msg(j2));
        assertEqualsContract(a, b, c);
    }

    // ----------------------------------------------------------------------------------------------------
    // Plain POJOs
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testSubject_equalsContract() {
        EqualsVerifier.simple().forClass(Subject.class).verify();
    }

    @Test
    public void testError_equalsContract() {
        EqualsVerifier.simple().forClass(Error.class).verify();
    }

    @Test
    public void testConsumerPauseRequest_equalsContract() {
        EqualsVerifier.simple().forClass(ConsumerPauseRequest.class).verify();
    }

    @Test
    public void testMessageDeleteRequest_equalsContract() {
        EqualsVerifier.simple().forClass(MessageDeleteRequest.class).verify();
    }

    @Test
    public void testMessageGetRequest_equalsContract() {
        EqualsVerifier.simple().forClass(MessageGetRequest.class).verify();
    }

    @Test
    public void testStreamInfoOptions_equalsAndHashCode() {
        // Builder is private; construct via factories and builder.
        StreamInfoOptions a = StreamInfoOptions.builder().filterSubjects("a.>").deletedDetails().build();
        StreamInfoOptions b = StreamInfoOptions.builder().filterSubjects("a.>").deletedDetails().build();
        StreamInfoOptions c = StreamInfoOptions.allSubjects();
        assertEqualsContract(a, b, c);

        // additional branches: same filter, different deletedDetails flag
        StreamInfoOptions d = StreamInfoOptions.filterSubjects("a.>");
        StreamInfoOptions e = StreamInfoOptions.filterSubjects("a.>");
        StreamInfoOptions f = StreamInfoOptions.filterSubjects("b.>");
        assertEqualsContract(d, e, f);

        // deletedDetails toggled
        StreamInfoOptions g = StreamInfoOptions.deletedDetails();
        StreamInfoOptions h = StreamInfoOptions.deletedDetails();
        StreamInfoOptions i = StreamInfoOptions.builder().build();
        assertEqualsContract(g, h, i);
    }

    @Test
    public void testSubscribeBehavior_equalsAndHashCode() {
        // All defaults
        SubscribeBehavior a = new SubscribeBehavior();
        SubscribeBehavior b = new SubscribeBehavior();
        SubscribeBehavior c = new SubscribeBehavior().messageAlarmTime(123L);
        assertEqualsContract(a, b, c);

        // Test handler equality via reference (single shared instance)
        MessageHandler handler = m -> { /* no-op */ };
        SubscribeBehavior d = new SubscribeBehavior().handler(handler).messageAlarmTime(50).pendingMessageLimit(10).pendingByteLimit(20);
        SubscribeBehavior e = new SubscribeBehavior().handler(handler).messageAlarmTime(50).pendingMessageLimit(10).pendingByteLimit(20);
        SubscribeBehavior f = new SubscribeBehavior().handler(handler).messageAlarmTime(50).pendingMessageLimit(11).pendingByteLimit(20);
        assertEqualsContract(d, e, f);

        // Different pendingByteLimit triggers inequality
        SubscribeBehavior g = new SubscribeBehavior().pendingByteLimit(1);
        SubscribeBehavior h = new SubscribeBehavior().pendingByteLimit(1);
        SubscribeBehavior i = new SubscribeBehavior().pendingByteLimit(2);
        assertEqualsContract(g, h, i);

        // Different handler triggers inequality
        MessageHandler other = m -> { /* no-op */ };
        SubscribeBehavior j = new SubscribeBehavior().handler(handler);
        SubscribeBehavior k = new SubscribeBehavior().handler(handler);
        SubscribeBehavior l = new SubscribeBehavior().handler(other);
        assertEqualsContract(j, k, l);
    }

    // ----------------------------------------------------------------------------------------------------
    // Creator-style equals (final, instanceof pattern)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testExternalCreator_equalsContract() {
        EqualsVerifier.simple().forClass(ExternalCreator.class).verify();
    }

    @Test
    public void testPlacementCreator_equalsContract() {
        EqualsVerifier.simple().forClass(PlacementCreator.class).verify();
    }

    @Test
    public void testRepublishCreator_equalsContract() {
        EqualsVerifier.simple().forClass(RepublishCreator.class).verify();
    }

    @Test
    public void testConsumerLimitsCreator_equalsContract() {
        EqualsVerifier.simple().forClass(ConsumerLimitsCreator.class).verify();
    }

    @Test
    public void testSubjectTransformCreator_equalsContract() {
        EqualsVerifier.simple().forClass(SubjectTransformCreator.class).verify();
    }

    // ---- StreamSourceCreator (abstract) tested via concrete subclasses ----
    // Note: the base equals uses `instanceof StreamSourceCreator<?>` so MirrorCreator
    // and SourceCreator with identical fields are considered equal to each other.
    // This deliberate cross-class equality requires manual testing.

    @Test
    public void testMirrorCreator_equalsAndHashCode() {
        MirrorCreator a = new MirrorCreator("S1");
        MirrorCreator b = new MirrorCreator("S1");
        MirrorCreator c = new MirrorCreator("S2");
        assertEqualsContract(a, b, c);

        // Exercise mutator branches
        ZonedDateTime t = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneId.of("UTC"));
        MirrorCreator d = new MirrorCreator("S1")
            .startSequence(7)
            .startTime(t)
            .filterSubject("x.>")
            .externalCreator(new ExternalCreator("api", "deliver"))
            .subjectTransforms(new SubjectTransformCreator("a", "b"));
        MirrorCreator e = new MirrorCreator("S1")
            .startSequence(7)
            .startTime(t)
            .filterSubject("x.>")
            .externalCreator(new ExternalCreator("api", "deliver"))
            .subjectTransforms(new SubjectTransformCreator("a", "b"));
        MirrorCreator f = new MirrorCreator("S1").startSequence(8);
        assertEqualsContract(d, e, f);
    }

    @Test
    public void testSourceCreator_equalsAndHashCode() {
        SourceCreator a = new SourceCreator("S1");
        SourceCreator b = new SourceCreator("S1");
        SourceCreator c = new SourceCreator("S2");
        assertEqualsContract(a, b, c);
    }

    @Test
    public void testStreamSourceCreator_crossSubclassEqualityIsByDesign() {
        // The base equals() uses `instanceof StreamSourceCreator<?>` which intentionally
        // allows cross-subclass equality. Confirm this documented behavior.
        MirrorCreator m = new MirrorCreator("S1");
        SourceCreator s = new SourceCreator("S1");
        assertEquals(m, s);
        assertEquals(m.hashCode(), s.hashCode());
    }

    @Test
    public void testStreamCreator_equalsAndHashCode() {
        // 39 fields; build pairs covering many branches manually rather than via EqualsVerifier
        // because StreamCreator has only validating constructors (no zero-arg)
        StreamCreator a = baseStreamCreator("S1");
        StreamCreator b = baseStreamCreator("S1");
        StreamCreator c = baseStreamCreator("S2");
        assertEqualsContract(a, b, c);

        // Branch: same name, different description
        StreamCreator d = baseStreamCreator("S1").description("hello");
        StreamCreator e = baseStreamCreator("S1").description("hello");
        StreamCreator f = baseStreamCreator("S1").description("world");
        assertEqualsContract(d, e, f);

        // Branch: max/long/boolean variants
        StreamCreator g = baseStreamCreator("S1").maxBytes(1024).maxMessages(10).allowDirect(true).denyPurge(true);
        StreamCreator h = baseStreamCreator("S1").maxBytes(1024).maxMessages(10).allowDirect(true).denyPurge(true);
        StreamCreator i = baseStreamCreator("S1").maxBytes(1024).maxMessages(10).allowDirect(true).denyPurge(false);
        assertEqualsContract(g, h, i);

        // Branch: enums
        StreamCreator j = baseStreamCreator("S1").retentionPolicy(RetentionPolicy.WorkQueue).storageType(StorageType.Memory);
        StreamCreator k = baseStreamCreator("S1").retentionPolicy(RetentionPolicy.WorkQueue).storageType(StorageType.Memory);
        StreamCreator l = baseStreamCreator("S1").retentionPolicy(RetentionPolicy.Interest).storageType(StorageType.Memory);
        assertEqualsContract(j, k, l);

        // Branch: nested creators
        PlacementCreator p1 = new PlacementCreator("c1", Arrays.asList("t1", "t2"));
        PlacementCreator p2 = new PlacementCreator("c1", Arrays.asList("t1", "t2"));
        PlacementCreator p3 = new PlacementCreator("c2", Arrays.asList("t1", "t2"));
        StreamCreator m1 = baseStreamCreator("S1").placementCreator(p1);
        StreamCreator m2 = baseStreamCreator("S1").placementCreator(p2);
        StreamCreator m3 = baseStreamCreator("S1").placementCreator(p3);
        assertEqualsContract(m1, m2, m3);

        // Branch: duration / persist mode
        StreamCreator n1 = baseStreamCreator("S1").maxAge(Duration.ofMinutes(1)).persistMode(PersistMode.Async);
        StreamCreator n2 = baseStreamCreator("S1").maxAge(Duration.ofMinutes(1)).persistMode(PersistMode.Async);
        StreamCreator n3 = baseStreamCreator("S1").maxAge(Duration.ofMinutes(2)).persistMode(PersistMode.Async);
        assertEqualsContract(n1, n2, n3);

        // null parameter & foreign type also handled by assertEqualsContract above
    }

    private static StreamCreator baseStreamCreator(String name) {
        return new StreamCreator(name);
    }

    // ----------------------------------------------------------------------------------------------------
    // ConsumerCreator hierarchy (non-final equals; getClass()-based)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPullConsumerCreator_equalsAndHashCode() {
        PullConsumerCreator a = new PullConsumerCreator("S1");
        PullConsumerCreator b = new PullConsumerCreator("S1");
        PullConsumerCreator c = new PullConsumerCreator("S2");
        assertEqualsContract(a, b, c);

        // Same stream, different durable name
        PullConsumerCreator d = new PullConsumerCreator("S1").durable("d1");
        PullConsumerCreator e = new PullConsumerCreator("S1").durable("d1");
        PullConsumerCreator f = new PullConsumerCreator("S1").durable("d2");
        assertEqualsContract(d, e, f);

        // Same stream, different filter subject and max batch
        PullConsumerCreator g = new PullConsumerCreator("S1").filterSubject("x.>").maxBatch(10L);
        PullConsumerCreator h = new PullConsumerCreator("S1").filterSubject("x.>").maxBatch(10L);
        PullConsumerCreator i = new PullConsumerCreator("S1").filterSubject("y.>").maxBatch(10L);
        assertEqualsContract(g, h, i);

        // Same stream, different boolean (headersOnly)
        PullConsumerCreator j = new PullConsumerCreator("S1").headersOnly(true);
        PullConsumerCreator k = new PullConsumerCreator("S1").headersOnly(true);
        PullConsumerCreator l = new PullConsumerCreator("S1").headersOnly(false);
        assertEqualsContract(j, k, l);
    }

    @Test
    public void testPushConsumerCreator_equalsAndHashCode() {
        PushConsumerCreator a = new PushConsumerCreator("S1");
        PushConsumerCreator b = new PushConsumerCreator("S1");
        PushConsumerCreator c = new PushConsumerCreator("S2");
        assertEqualsContract(a, b, c);

        // Same stream, different deliver subject
        PushConsumerCreator d = new PushConsumerCreator("S1").deliverSubject("ds1");
        PushConsumerCreator e = new PushConsumerCreator("S1").deliverSubject("ds1");
        PushConsumerCreator f = new PushConsumerCreator("S1").deliverSubject("ds2");
        assertEqualsContract(d, e, f);
    }

    @Test
    public void testConsumerCreator_pushVsPullNotEqual() {
        // ConsumerCreator.equals uses getClass() != o.getClass() — different concrete classes
        // are never equal, even with the same stream.
        PullConsumerCreator pull = new PullConsumerCreator("S1");
        PushConsumerCreator push = new PushConsumerCreator("S1");
        assertNotEquals(pull, push);
        assertNotEquals(push, pull);
    }

    @Test
    public void testPullOrderedConsumerCreator_equalsAndHashCode() {
        // Note: AbstractOrderedConsumerCreator constructors call namePrefix(...) which
        // generates a random underlying `name` via _name(), so two freshly-constructed
        // instances have different `name` fields and are not equal. We manually align the
        // `name` field (package-protected access) to test the equals/hashCode contract.
        PullOrderedConsumerCreator a = new PullOrderedConsumerCreator("S1");
        PullOrderedConsumerCreator b = new PullOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        b._name(a.getName()); // align names b/c they are internally randomly generated
        b.namePrefix = a.namePrefix; // align prefix (package-private field via same-package access)
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        PullOrderedConsumerCreator c = new PullOrderedConsumerCreator("S2");
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        //noinspection AssertEqualsBetweenInconvertibleTypes
        assertNotEquals(a, "foreign");
        assertEquals(a, a);

        // Different namePrefix branch in AbstractOrderedConsumerCreator.equals
        // Start from aligned state, then diverge ONLY namePrefix
        PullOrderedConsumerCreator d = new PullOrderedConsumerCreator("S1");
        PullOrderedConsumerCreator e = new PullOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        e._name(d.getName()); // align name so only namePrefix differs
        d.namePrefix = "prefix1";
        e.namePrefix = "prefix2";
        assertNotEquals(d, e);
    }

    @Test
    public void testPushOrderedConsumerCreator_equalsAndHashCode() {
        PushOrderedConsumerCreator a = new PushOrderedConsumerCreator("S1");
        PushOrderedConsumerCreator b = new PushOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        b._name(a.getName()); // align names b/c they are internally randomly generated
        b.namePrefix = a.namePrefix;
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        PushOrderedConsumerCreator c = new PushOrderedConsumerCreator("S2");
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        //noinspection AssertEqualsBetweenInconvertibleTypes
        assertNotEquals(a, "foreign");

        // Different namePrefix branch
        PushOrderedConsumerCreator d = new PushOrderedConsumerCreator("S1");
        PushOrderedConsumerCreator e = new PushOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        e._name(d.getName()); // align name so only namePrefix differs
        d.namePrefix = "p1";
        e.namePrefix = "p2";
        assertNotEquals(d, e);
    }

    @Test
    public void testOrderedConsumerCreators_pullVsPushNotEqual() {
        // Different concrete classes (PullOrdered vs PushOrdered) must never be equal,
        // even with identical configuration. ConsumerCreator equals uses getClass().
        PullOrderedConsumerCreator pull = new PullOrderedConsumerCreator("S1");
        PushOrderedConsumerCreator push = new PushOrderedConsumerCreator("S1");
        assertNotEquals(pull, push);
        assertNotEquals(push, pull);
    }

    @Test
    public void testAbstractOrderedConsumerCreator_namePrefixIsPartOfIdentity() {
        // Verify AbstractOrderedConsumerCreator.equals exercises both branches:
        // 1. super.equals returns false -> short-circuits to false
        // 2. super.equals returns true; namePrefix differs -> overall false
        PullOrderedConsumerCreator a = new PullOrderedConsumerCreator("S1");
        PullOrderedConsumerCreator b = new PullOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        b._name(a.getName()); // align names b/c they are internally randomly generated
        b.namePrefix = a.namePrefix;
        assertEquals(a, b);

        // Now diverge namePrefix on b — exercises namePrefix branch in equals
        b.namePrefix = "differs";
        assertNotEquals(a, b);

        // Trigger super.equals false branch: different stream -> super.equals=false
        PullOrderedConsumerCreator c = new PullOrderedConsumerCreator("S99");
        assertNotEquals(a, c);

        // Identical namePrefix produces equal hashCode
        PullOrderedConsumerCreator d = new PullOrderedConsumerCreator("S1");
        PullOrderedConsumerCreator e = new PullOrderedConsumerCreator("S1");
        //noinspection DataFlowIssue // we know name is set in ordered
        e._name(d.getName()); // align names b/c they are internally randomly generated
        d.namePrefix = "same";
        e.namePrefix = "same";
        assertEquals(d, e);
        assertEquals(d.hashCode(), e.hashCode());
    }

    // ----------------------------------------------------------------------------------------------------
    // Coverage sanity: ensure null-list params on PlacementCreator are exercised
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void testPlacementCreator_nullAndEmptyAndForeign() {
        PlacementCreator a = new PlacementCreator();
        PlacementCreator b = new PlacementCreator();
        PlacementCreator c = new PlacementCreator("c1", null);
        assertEqualsContract(a, b, c);

        // Empty tag list equality
        List<String> tagsA = new ArrayList<>();
        List<String> tagsB = new ArrayList<>();
        PlacementCreator d = new PlacementCreator(null, tagsA);
        PlacementCreator e = new PlacementCreator(null, tagsB);
        PlacementCreator f = new PlacementCreator(null, Collections.singletonList("x"));
        assertEqualsContract(d, e, f);
    }
}
