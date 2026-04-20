package io.synadia.client.support;

import io.synadia.client.testutils.JetStreamClientError;
import io.synadia.client.testutils.JsValidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static io.synadia.client.impl.JetStreamConstants.NATS_META_KEY_PREFIX;
import static io.synadia.client.testutils.JsValidator.*;
import static io.synadia.client.testutils.ResourceUtils.dataAsLines;
import static io.synadia.client.testutils.TestBase.*;
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
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired("colon:isbetween9andA"));
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
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired("colon:isbetween9andA"));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(".starts.with.dot.not.allowed"));
    }

    @Test
    public void testValidateMustMatchIfBothSupplied() {
        JetStreamClientError err = new JetStreamClientError("TEST", 999999, "desc");
        assertNull(validateMustMatchIfBothSupplied(null, null, err));
        assertEquals("y", validateMustMatchIfBothSupplied(null, "y", err));
        assertEquals("y", validateMustMatchIfBothSupplied("", "y", err));
        assertEquals("x", validateMustMatchIfBothSupplied("x", null, err));
        assertEquals("x", validateMustMatchIfBothSupplied("x", " ", err));
        assertEquals("x", validateMustMatchIfBothSupplied("x", "x", err));
        assertThrows(IllegalArgumentException.class, () -> validateMustMatchIfBothSupplied("x", "y", err));
    }

    @Test
    public void testValidateDurationGtOrEqSeconds() {
        Duration ifNull = Duration.ofMillis(999);
        assertEquals(ifNull, validateDurationNotRequiredGtOrEqSeconds(1, null, ifNull, ""));
        assertEquals(Duration.ofSeconds(1), validateDurationNotRequiredGtOrEqSeconds(1, Duration.ofSeconds(1), ifNull, ""));
        assertThrows(IllegalArgumentException.class, () -> validateDurationNotRequiredGtOrEqSeconds(1, Duration.ofMillis(999), ifNull, ""));

        assertEquals(Duration.ofSeconds(1), validateDurationGtOrEqSeconds(1, 1000, ""));
        assertThrows(IllegalArgumentException.class, () -> validateDurationGtOrEqSeconds(1, 999, ""));
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
}
