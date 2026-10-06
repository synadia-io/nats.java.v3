package io.synadia.client.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static io.synadia.client.impl.JetStreamConstants.NATS_META_KEY_PREFIX;
import static io.synadia.client.utils.JsValidator.*;
import static io.synadia.client.utils.ResourceUtils.dataAsLines;
import static io.synadia.client.utils.TestBase.*;
import static org.junit.jupiter.api.Assertions.*;

public class JsValidatorTests extends ValidatorTests {
    private static List<String> UTF_ONLY_STRINGS;

    @BeforeAll
    public static void beforeAll() {
        UTF_ONLY_STRINGS = dataAsLines("utf8-only-no-ws-test-strings.txt");
    }

    @Test
    public void testValidateStreamName() {
        allowedRequired(JsValidator::validateStreamName, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOLLAR));
        notAllowedRequired(JsValidator::validateStreamName, Arrays.asList(null, "", HAS_SPACE, HAS_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, HAS_LOW, HAS_127));
        notAllowedRequired(JsValidator::validateStreamName, UTF_ONLY_STRINGS);
        allowedNotRequiredEmptyAsNull(JsValidator::validateStreamName, Arrays.asList(null, ""));
    }

    @Test
    public void testValidateDurable() {
        allowedRequired(JsValidator::validateDurable, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOLLAR));
        notAllowedRequired(JsValidator::validateDurable, Arrays.asList(null, "", HAS_SPACE, HAS_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, HAS_LOW, HAS_127));
        notAllowedRequired(JsValidator::validateDurable, UTF_ONLY_STRINGS);
        allowedNotRequiredEmptyAsNull(JsValidator::validateDurable, Arrays.asList(null, ""));
    }

    @Test
    public void testValidateDurationNotRequiredGtOrEqZero() {
        Duration ifNull = Duration.ofMillis(999);
        assertEquals(ifNull, validateDurationNotRequiredGtOrEqZero(null, ifNull));
        assertEquals(Duration.ZERO, validateDurationNotRequiredGtOrEqZero(Duration.ZERO, ifNull));
        assertEquals(Duration.ofNanos(1), validateDurationNotRequiredGtOrEqZero(Duration.ofNanos(1), ifNull));
        assertThrows(IllegalArgumentException.class, () -> validateDurationNotRequiredGtOrEqZero(Duration.ofNanos(-1), ifNull));

        assertEquals(Duration.ZERO, validateDurationNotRequiredGtOrEqZero(0));
        assertEquals(Duration.ofMillis(1), validateDurationNotRequiredGtOrEqZero(1));
        assertEquals(Duration.ofSeconds(1), validateDurationNotRequiredGtOrEqZero(1000));
        assertThrows(IllegalArgumentException.class, () -> validateDurationNotRequiredGtOrEqZero(-1));
    }

    @Test
    public void testValidateBucketName() {
        validateBucketName(PLAIN, true);
        validateBucketName(PLAIN.toUpperCase(), true);
        validateBucketName(HAS_DASH, true);
        validateBucketName(HAS_UNDER, true);
        validateBucketName("numbers9ok", true);
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(null, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_SPACE, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_DOT, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(STAR_NOT_SEGMENT, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(GT_NOT_SEGMENT, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_DOLLAR, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_LOW, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_127, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_FWD_SLASH, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_EQUALS, true));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_TIC, true));

        validateBucketName(PLAIN, false);
        validateBucketName(PLAIN.toUpperCase(), false);
        validateBucketName(HAS_DASH, false);
        validateBucketName(HAS_UNDER, false);
        validateBucketName("numbers9ok", false);
        validateBucketName(null, false);
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_SPACE, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_DOT, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(STAR_NOT_SEGMENT, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(GT_NOT_SEGMENT, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_DOLLAR, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_LOW, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_127, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_FWD_SLASH, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_EQUALS, false));
        assertThrows(IllegalArgumentException.class, () -> validateBucketName(HAS_TIC, false));
    }

    @Test
    public void testValidateKvKeyWildcardAllowedRequired() {
        validateKvKeyWildcardAllowedRequired(PLAIN);
        validateKvKeyWildcardAllowedRequired(PLAIN.toUpperCase());
        validateKvKeyWildcardAllowedRequired(HAS_DASH);
        validateKvKeyWildcardAllowedRequired(HAS_UNDER);
        validateKvKeyWildcardAllowedRequired(HAS_FWD_SLASH);
        validateKvKeyWildcardAllowedRequired(HAS_EQUALS);
        validateKvKeyWildcardAllowedRequired(HAS_DOT);
        validateKvKeyWildcardAllowedRequired(STAR_NOT_SEGMENT);
        validateKvKeyWildcardAllowedRequired(GT_NOT_SEGMENT);
        validateKvKeyWildcardAllowedRequired("numbers9ok");
        String nullKey = null;
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(nullKey));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_TIC));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_COLON));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(".starts.with.dot.not.allowed"));

        List<String> nullList = null;
        //noinspection ConstantValue
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(nullList));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Collections.singletonList(HAS_SPACE)));
    }

    @Test
    public void testValidateNonWildcardKeyRequired() {
        validateNonWildcardKvKeyRequired(PLAIN);
        validateNonWildcardKvKeyRequired(PLAIN.toUpperCase());
        validateNonWildcardKvKeyRequired(HAS_DASH);
        validateNonWildcardKvKeyRequired(HAS_UNDER);
        validateNonWildcardKvKeyRequired(HAS_FWD_SLASH);
        validateNonWildcardKvKeyRequired(HAS_EQUALS);
        validateNonWildcardKvKeyRequired(HAS_DOT);
        validateNonWildcardKvKeyRequired("numbers9ok");
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(null));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(STAR_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(GT_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_TIC));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_COLON));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(".starts.with.dot.not.allowed"));
    }

    @Test
    public void testValidateMustMatchIfBothSupplied() {
        assertNull(validateMustMatchIfBothSupplied(null, null, "One", "Two"));
        assertEquals("y", validateMustMatchIfBothSupplied(null, "y", "One", "Two"));
        assertEquals("y", validateMustMatchIfBothSupplied("", "y", "One", "Two"));
        assertEquals("x", validateMustMatchIfBothSupplied("x", null, "One", "Two"));
        assertEquals("x", validateMustMatchIfBothSupplied("x", " ", "One", "Two"));
        assertEquals("x", validateMustMatchIfBothSupplied("x", "x", "One", "Two"));
        // both values are individually valid, so a conflict is state, not a bad argument
        IllegalStateException ise = assertThrows(IllegalStateException.class,
            () -> validateMustMatchIfBothSupplied("x", "y", "One", "Two"));
        assertEquals("One must match Two if both are supplied.", ise.getMessage());
    }

    @Test
    public void testClientError() {
        // coverage
        ClientError err = new ClientError("TEST", 999999, "desc", ClientError.KIND_ILLEGAL_ARGUMENT);
        assertEquals("[TEST-999999] desc", err.message());
    }

    @Test
    public void testValidateDurationGtOrEqSeconds() {
        Duration ifNull = Duration.ofMillis(999);
        assertEquals(ifNull, validateDurationNotRequiredGtOrEqSeconds(null, 1, ifNull, ""));
        assertEquals(Duration.ofSeconds(1), validateDurationNotRequiredGtOrEqSeconds(Duration.ofSeconds(1), 1, ifNull, ""));
        assertThrows(IllegalArgumentException.class, () -> validateDurationNotRequiredGtOrEqSeconds(Duration.ofMillis(999), 1, ifNull, ""));

        assertEquals(Duration.ofSeconds(1), validateMillisGtOrEqSeconds(1000, 1, ""));
        assertThrows(IllegalArgumentException.class, () -> validateMillisGtOrEqSeconds(999, 1, ""));
    }

    @Test
    public void testMetaIsEquivalent() {
        Map<String, String> m1 = new HashMap<>();
        Map<String, String> m2 = new HashMap<>();

        assertTrue(JsValidator.metaIsEquivalent(null, null));
        assertTrue(JsValidator.metaIsEquivalent(null, m1));
        assertTrue(JsValidator.metaIsEquivalent(m1, null));
        assertTrue(JsValidator.metaIsEquivalent(m1, m2));

        m1.put("A", "a");
        m1.put(NATS_META_KEY_PREFIX + "foo", "foo");
        assertFalse(JsValidator.metaIsEquivalent(m1, m2));

        m2.put("A", "a");
        m2.put(NATS_META_KEY_PREFIX + "bar", "bar");
        assertTrue(JsValidator.metaIsEquivalent(m1, m2));

        m1.put("B", "b");
        assertFalse(JsValidator.metaIsEquivalent(m1, m2));

        m2.put("B", "b");
        assertTrue(JsValidator.metaIsEquivalent(m1, m2));

        m2.put("C", "C");
        assertFalse(JsValidator.metaIsEquivalent(m1, m2));
    }

    @Test
    public void testValidateMaxMessagesPerSubject() {
        assertEquals(1, validateMaxHistory(1));
        assertEquals(64, validateMaxHistory(64));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(0));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(-1));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(65));
    }

    @Test
    public void testValidateMaxBucketBytes() {
        assertEquals(1, validateMaxBucketBytes(1));
        assertEquals(-1, validateMaxBucketBytes(-1));
        assertThrows(IllegalArgumentException.class, () -> validateMaxBucketBytes(0));
        assertThrows(IllegalArgumentException.class, () -> validateMaxBucketBytes(-2));
    }

    @Test
    public void testValidateConsumerName() {
        allowedRequired(JsValidator::validateConsumerName, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOLLAR));
        notAllowedRequired(JsValidator::validateConsumerName, Arrays.asList(null, "", HAS_SPACE, HAS_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, HAS_LOW, HAS_127, HAS_FWD_SLASH, HAS_BACK_SLASH));
        notAllowedRequired(JsValidator::validateConsumerName, UTF_ONLY_STRINGS);
        allowedNotRequiredEmptyAsNull(JsValidator::validateConsumerName, Arrays.asList(null, ""));
    }

    @Test
    public void testValidatePrefixOrDomain() {
        assertEquals(PLAIN, validatePrefixOrDomain(PLAIN, "label", true));
        assertEquals(HAS_DOT, validatePrefixOrDomain(HAS_DOT, "label", true));
        assertEquals(ENDS_WITH_DOT, validatePrefixOrDomain(ENDS_WITH_DOT, "label", true));
        assertEquals(HAS_DOLLAR, validatePrefixOrDomain(HAS_DOLLAR, "label", true));
        assertEquals(HAS_FWD_SLASH, validatePrefixOrDomain(HAS_FWD_SLASH, "label", true));
        assertNull(validatePrefixOrDomain(null, "label", false));
        assertNull(validatePrefixOrDomain("", "label", false));

        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(null, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain("", "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(STARTS_WITH_DOT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(STAR_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(GT_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(HAS_SPACE, "label", false));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(HAS_LOW, "label", false));
        assertThrows(IllegalArgumentException.class, () -> validatePrefixOrDomain(HAS_127, "label", false));
    }

    @Test
    public void testValidateKvKeysWildcardAllowedRequiredValid() {
        List<String> keys = Arrays.asList(PLAIN, HAS_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT);
        assertSame(keys, validateKvKeysWildcardAllowedRequired(keys));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Arrays.asList(PLAIN, HAS_SPACE)));
    }

    @Test
    public void testValidateWildcardKvKey() {
        assertEquals(PLAIN, validateWildcardKvKey(PLAIN, "label", true));
        assertEquals(STAR_NOT_SEGMENT, validateWildcardKvKey(STAR_NOT_SEGMENT, "label", false));
        assertEquals(GT_NOT_SEGMENT, validateWildcardKvKey(GT_NOT_SEGMENT, "label", false));
        assertNull(validateWildcardKvKey(null, "label", false));
        assertNull(validateWildcardKvKey("", "label", false));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(HAS_SPACE, "label", false));
        assertTrue(iae.getMessage().startsWith("label"));
        assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(null, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(STARTS_WITH_DOT, "label", false));
    }

    @Test
    public void testValidateNonWildcardKvKey() {
        assertEquals(PLAIN, validateNonWildcardKvKey(PLAIN, "label", true));
        assertEquals(HAS_DOT, validateNonWildcardKvKey(HAS_DOT, "label", false));
        assertNull(validateNonWildcardKvKey(null, "label", false));
        assertNull(validateNonWildcardKvKey("", "label", false));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(STAR_NOT_SEGMENT, "label", false));
        assertTrue(iae.getMessage().startsWith("label"));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(GT_NOT_SEGMENT, "label", false));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(null, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(STARTS_WITH_DOT, "label", false));
    }

    @Test
    public void testValidateMaxValueSize() {
        assertEquals(1, validateMaxValueSize(1));
        assertEquals(-1, validateMaxValueSize(-1));
        assertThrows(IllegalArgumentException.class, () -> validateMaxValueSize(0));
        assertThrows(IllegalArgumentException.class, () -> validateMaxValueSize(-2));
    }

    @Test
    public void testValidateNumberOfReplicas() {
        assertEquals(1, validateNumberOfReplicas(1));
        assertEquals(5, validateNumberOfReplicas(5));
        assertThrows(IllegalArgumentException.class, () -> validateNumberOfReplicas(0));
        assertThrows(IllegalArgumentException.class, () -> validateNumberOfReplicas(6));
    }

    @Test
    public void testValidateDurationNotRequiredGtOrEqZeroIfZero() {
        Duration ifZero = Duration.ofMillis(999);
        assertEquals(ifZero, validateDurationNotRequiredGtOrEqZero(0, ifZero));
        assertEquals(Duration.ofMillis(1), validateDurationNotRequiredGtOrEqZero(1, ifZero));
        assertEquals(Duration.ofSeconds(1), validateDurationNotRequiredGtOrEqZero(1000, ifZero));
        assertThrows(IllegalArgumentException.class, () -> validateDurationNotRequiredGtOrEqZero(-1, ifZero));
    }

    @Test
    public void testNotNonWildcardKvKey() {
        assertFalse(notNonWildcardKvKey(PLAIN));
        assertFalse(notNonWildcardKvKey(HAS_DASH));
        assertFalse(notNonWildcardKvKey(HAS_UNDER));
        assertFalse(notNonWildcardKvKey(HAS_DOT));
        assertFalse(notNonWildcardKvKey(HAS_FWD_SLASH));
        assertFalse(notNonWildcardKvKey(HAS_EQUALS));
        assertTrue(notNonWildcardKvKey(STARTS_WITH_DOT));
        assertTrue(notNonWildcardKvKey(STAR_NOT_SEGMENT));
        assertTrue(notNonWildcardKvKey(GT_NOT_SEGMENT));
        assertTrue(notNonWildcardKvKey(HAS_SPACE));
        assertTrue(notNonWildcardKvKey(HAS_DOLLAR));
        assertTrue(notNonWildcardKvKey(HAS_BACK_SLASH));
        assertTrue(notNonWildcardKvKey(HAS_TIC));
        assertTrue(notNonWildcardKvKey(HAS_COLON));
        assertTrue(notNonWildcardKvKey(HAS_BRACE));
    }

    @Test
    public void testNotWildcardKvKey() {
        assertFalse(notWildcardKvKey(PLAIN));
        assertFalse(notWildcardKvKey(HAS_DASH));
        assertFalse(notWildcardKvKey(HAS_UNDER));
        assertFalse(notWildcardKvKey(HAS_DOT));
        assertFalse(notWildcardKvKey(HAS_FWD_SLASH));
        assertFalse(notWildcardKvKey(HAS_EQUALS));
        assertFalse(notWildcardKvKey(STAR_NOT_SEGMENT));
        assertFalse(notWildcardKvKey(GT_NOT_SEGMENT));
        assertTrue(notWildcardKvKey(STARTS_WITH_DOT));
        assertTrue(notWildcardKvKey(HAS_SPACE));
        assertTrue(notWildcardKvKey(HAS_DOLLAR));
        assertTrue(notWildcardKvKey(HAS_BACK_SLASH));
        assertTrue(notWildcardKvKey(HAS_TIC));
        assertTrue(notWildcardKvKey(HAS_COLON));
        assertTrue(notWildcardKvKey(HAS_BRACE));
    }
}
