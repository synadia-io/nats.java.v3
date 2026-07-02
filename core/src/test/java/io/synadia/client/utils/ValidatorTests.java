package io.synadia.client.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static io.synadia.client.utils.ResourceUtils.dataAsLines;
import static io.synadia.client.utils.TestBase.*;
import static io.synadia.client.utils.Validator.*;
import static org.junit.jupiter.api.Assertions.*;

public class ValidatorTests {
    private static List<String> UTF_ONLY_STRINGS;

    @BeforeAll
    public static void beforeAll() {
        UTF_ONLY_STRINGS = dataAsLines("utf8-only-no-ws-test-strings.txt");
    }

    @Test
    public void testValidateSubject() {
        // subject is required
        allowedRequired(Validator::validateSubject, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOT, HAS_DOLLAR, HAS_LOW, HAS_127));
        allowedRequired(Validator::validateSubject, UTF_ONLY_STRINGS);
        allowedRequired(Validator::validateSubject, Arrays.asList(STAR_SEGMENT, GT_LAST_SEGMENT));
        allowedRequired(Validator::validateSubject, Arrays.asList(STARTS_WITH_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, EMPTY_SEGMENT, GT_NOT_LAST_SEGMENT));
        allowedRequired(Validator::validateSubject, Collections.singletonList(ENDS_WITH_DOT));
        notAllowedRequired(Validator::validateSubject, Arrays.asList(null, "", HAS_SPACE, HAS_CR, HAS_LF));
        notAllowedRequired(Validator::validateSubject, Arrays.asList(ENDS_WITH_CR, ENDS_WITH_LF, ENDS_WITH_TAB));
        notAllowedRequiredStrict(Arrays.asList(STARTS_WITH_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, EMPTY_SEGMENT, GT_NOT_LAST_SEGMENT));
        notAllowedRequiredStrict(Arrays.asList(ENDS_WITH_DOT, ENDS_WITH_DOT_SPACE, ENDS_WITH_CR, ENDS_WITH_LF, ENDS_WITH_TAB));

        // subject not required, null and empty both mean not supplied
        allowedNotRequiredEmptyAsNull(Validator::validateSubject, Arrays.asList(null, ""));
        allowedNotRequired(Validator::validateSubject, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOT, HAS_DOLLAR, HAS_LOW, HAS_127));
        allowedNotRequired(Validator::validateSubject, UTF_ONLY_STRINGS);
        allowedNotRequired(Validator::validateSubject, Arrays.asList(STAR_SEGMENT, GT_LAST_SEGMENT));
        allowedNotRequired(Validator::validateSubject, Arrays.asList(STARTS_WITH_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, EMPTY_SEGMENT, GT_NOT_LAST_SEGMENT));
        allowedNotRequired(Validator::validateSubject, Collections.singletonList(ENDS_WITH_DOT));

        notAllowedNotRequired(Validator::validateSubject, Arrays.asList(HAS_SPACE, HAS_CR, HAS_LF));
        notAllowedNotRequiredStrict(Arrays.asList(STARTS_WITH_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, EMPTY_SEGMENT, GT_NOT_LAST_SEGMENT));
        notAllowedNotRequiredStrict(Arrays.asList(ENDS_WITH_DOT, ENDS_WITH_DOT_SPACE, ENDS_WITH_CR, ENDS_WITH_LF, ENDS_WITH_TAB));

        allowedRequiredCheckEndWith(Validator::validateSubject, false, Arrays.asList(STAR_SEGMENT, GT_LAST_SEGMENT));
        allowedRequiredCheckEndWith(Validator::validateSubject, true, Collections.singletonList(STAR_SEGMENT));
        notAllowedRequiredCheckEndWith(Validator::validateSubject, true, Collections.singletonList(GT_LAST_SEGMENT));
        allowedNotRequiredCheckEndWith(Validator::validateSubject, false, Arrays.asList(null, GT_LAST_SEGMENT));
        notAllowedNotRequiredCheckEndWith(Validator::validateSubject, true, Collections.singletonList(GT_LAST_SEGMENT));
    }

    @Test
    public void testValidateReplyTo() {
        allowedRequired(Validator::validateReplyTo, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOT, HAS_DOLLAR));
        notAllowedRequired(Validator::validateReplyTo, Arrays.asList(null, "", HAS_SPACE, STAR_NOT_SEGMENT, GT_NOT_SEGMENT, HAS_LOW, HAS_127));
        notAllowedRequired(Validator::validateReplyTo, UTF_ONLY_STRINGS);
        allowedNotRequiredEmptyAsNull(Validator::validateReplyTo, Arrays.asList(null, ""));
    }

    @Test
    public void testValidateQueueName() {
        // validateQueueName(String s, boolean required)
        allowedRequired(Validator::validateQueueName, Arrays.asList(PLAIN, HAS_PRINTABLE, HAS_DOLLAR, HAS_DOT, HAS_LOW, HAS_127));
        notAllowedRequired(Validator::validateQueueName, Arrays.asList(null, "", HAS_SPACE, STAR_NOT_SEGMENT, GT_NOT_SEGMENT));
        allowedRequired(Validator::validateQueueName, UTF_ONLY_STRINGS);
        allowedNotRequiredEmptyAsNull(Validator::validateQueueName, Arrays.asList(null, ""));
    }

    @Test
    public void testValidatePrintable() {
        validatePrintable(PLAIN, "label", true);
        validatePrintable(HAS_PRINTABLE, "label", true);
        validatePrintable(HAS_DOT, "label", true);
        validatePrintable(STAR_NOT_SEGMENT, "label", true);
        validatePrintable(GT_NOT_SEGMENT, "label", true);
        validatePrintable(HAS_DASH, "label", true);
        validatePrintable(HAS_UNDER, "label", true);
        validatePrintable(HAS_DOLLAR, "label", true);
        validatePrintable(HAS_FWD_SLASH, "label", true);
        validatePrintable(HAS_BACK_SLASH, "label", true);
        validatePrintable(HAS_EQUALS, "label", true);
        validatePrintable(HAS_TIC, "label", true);

        assertThrows(IllegalArgumentException.class, () -> validatePrintable(HAS_SPACE, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintable(HAS_127, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintable(HAS_LOW, "label", true));

        validatePrintableExceptWildDotGt(PLAIN, "label", true);
        validatePrintableExceptWildDotGt(HAS_PRINTABLE, "label", true);
        validatePrintableExceptWildDotGt(HAS_DASH, "label", true);
        validatePrintableExceptWildDotGt(HAS_UNDER, "label", true);
        validatePrintableExceptWildDotGt(HAS_DOLLAR, "label", true);
        validatePrintableExceptWildDotGt(HAS_FWD_SLASH, "label", true);
        validatePrintableExceptWildDotGt(HAS_BACK_SLASH, "label", true);
        validatePrintableExceptWildDotGt(HAS_EQUALS, "label", true);
        validatePrintableExceptWildDotGt(HAS_TIC, "label", true);

        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(HAS_DOT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(STAR_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(GT_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(HAS_SPACE, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(HAS_127, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGt(HAS_LOW, "label", true));

        validatePrintableExceptWildDotGtSlashes(PLAIN, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_PRINTABLE, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_DASH, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_UNDER, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_DOLLAR, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_EQUALS, "label", true);
        validatePrintableExceptWildDotGtSlashes(HAS_TIC, "label", true);

        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_FWD_SLASH, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_BACK_SLASH, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_DOT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(STAR_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(GT_NOT_SEGMENT, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_SPACE, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_127, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validatePrintableExceptWildDotGtSlashes(HAS_LOW, "label", true));
    }

    @Test
    public void testValidateDurationRequired() {
        assertEquals(Duration.ofNanos(1), validateDurationRequired(Duration.ofNanos(1)));
        assertEquals(Duration.ofSeconds(1), validateDurationRequired(Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> validateDurationRequired(null));
        assertThrows(IllegalArgumentException.class, () -> validateDurationRequired(Duration.ofNanos(0)));
        assertThrows(IllegalArgumentException.class, () -> validateDurationRequired(Duration.ofSeconds(0)));
        assertThrows(IllegalArgumentException.class, () -> validateDurationRequired(Duration.ofNanos(-1)));
        assertThrows(IllegalArgumentException.class, () -> validateDurationRequired(Duration.ofSeconds(-1)));
    }

    @Test
    public void testIsGtEqZero() {
        assertTrue(Validator.isGtEqZero(0));
        assertTrue(Validator.isGtEqZero(1));
        assertFalse(Validator.isGtEqZero(-1));
    }

    @Test
    public void testValidateGtEqZero() {
        assertEquals(0, validateGtEqZero(0, "test"));
        assertEquals(1, validateGtEqZero(1, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtEqZero(-1, "test"));
    }

    @Test
    public void testEnsureDuration() {
        assertEquals(Duration.ofMillis(10), ensureNotNullAndNotLessThanMin(null, Duration.ofMillis(2), Duration.ofMillis(10)));
        assertEquals(Duration.ofMillis(10), ensureNotNullAndNotLessThanMin(Duration.ofMillis(1), Duration.ofMillis(2), Duration.ofMillis(10)));
        assertEquals(Duration.ofMillis(100), ensureNotNullAndNotLessThanMin(Duration.ofMillis(100), Duration.ofMillis(2), Duration.ofMillis(10)));
        assertEquals(Duration.ofMillis(10), ensureDurationNotLessThanMin(1, Duration.ofMillis(2), Duration.ofMillis(10)));
        assertEquals(Duration.ofMillis(100), ensureDurationNotLessThanMin(100, Duration.ofMillis(2), Duration.ofMillis(10)));
    }

    @SuppressWarnings({"ObviousNullCheck", "rawtypes"})
    @Test
    public void testValidateRequired() {
        required("required", "label");
        required(new Object(), "label");
        required(Collections.singletonList("list"), "label");
        required(Collections.singletonMap("key", "value"), "label");

        assertThrows(IllegalArgumentException.class, () -> required((String)null, "label"));
        assertThrows(IllegalArgumentException.class, () -> required("", "label"));
        assertThrows(IllegalArgumentException.class, () -> required((Object)null, "label"));
        assertThrows(IllegalArgumentException.class, () -> required((List)null, "label"));
        assertThrows(IllegalArgumentException.class, () -> required(new ArrayList<>(), "label"));
        assertThrows(IllegalArgumentException.class, () -> required((Map)null, "label"));
        assertThrows(IllegalArgumentException.class, () -> required(new HashMap<>(), "label"));
    }

    @Test
    public void testNotNull() {
        Object o = new Object();
        validateNotNull(o, "fieldName");
        assertThrows(IllegalArgumentException.class, () -> validateNotNull(null, "fieldName"));
    }

    @Test
    public void testZeroOrLtMinus1() {
        assertTrue(zeroOrLtMinus1(0));
        assertTrue(zeroOrLtMinus1(-2));
        assertFalse(zeroOrLtMinus1(1));
        assertFalse(zeroOrLtMinus1(-1));
    }

    @Test
    public void testValidateGtZero() {
        assertEquals(1, validateGtZero(1, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtZero(0, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtZero(-1, "test"));
        assertEquals(1, validateGtZero(1L, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtZero(0L, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtZero(-1L, "test"));
    }

    @Test
    public void testValidateGtZeroOrMinus1() {
        assertEquals(1, validateGtZeroOrMinus1(1, "test"));
        assertEquals(-1, validateGtZeroOrMinus1(-1, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtZeroOrMinus1(0, "test"));
    }

    @Test
    public void testValidateGtEqMinus1() {
        assertEquals(1, validateGtEqMinus1(1, "test"));
        assertEquals(0, validateGtEqMinus1(0, "test"));
        assertEquals(-1, validateGtEqMinus1(-1, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateGtEqMinus1(-2, "test"));
    }

    @Test
    public void testValidateNotNegative() {
        assertEquals(0, validateNotNegative(0, "test"));
        assertEquals(1, validateNotNegative(1, "test"));
        assertThrows(IllegalArgumentException.class, () -> validateNotNegative(-1, "test"));
    }

    @Test
    public void testEmptyAsNull() {
        assertEquals("test", emptyAsNull("test"));
        assertNull(emptyAsNull(null));
        assertNull(emptyAsNull(""));
        assertNull(emptyAsNull(" "));
        assertNull(emptyAsNull("\t"));
    }

    @Test
    public void testEmptyOrNullAs() {
        assertEquals("test", emptyOrNullAs("test", null));
        assertNull(emptyOrNullAs(null, null));
        assertNull(emptyOrNullAs("", null));
        assertNull(emptyOrNullAs(" ", null));
        assertNull(emptyOrNullAs("\t", null));

        assertEquals("test", emptyOrNullAs("test", "as"));
        assertEquals("as", emptyOrNullAs(null, "as"));
        assertEquals("as", emptyOrNullAs("", "as"));
        assertEquals("as", emptyOrNullAs(" ", "as"));
        assertEquals("as", emptyOrNullAs("\t", "as"));
    }

    public interface StringAndRequiredTest { String validate(String s, boolean required); }
    public interface StringLabelRequiredCantEndWithGtTest { String validate(String s, String l, boolean required, boolean cantEndWithGt); }

    protected void allowedRequired(StringAndRequiredTest test, List<String> strings) {
        for (String s : strings) {
            assertEquals(s, test.validate(s, true), allowedMessage(s));
        }
    }

    protected void allowedRequiredCheckEndWith(StringLabelRequiredCantEndWithGtTest test, boolean cantEndWithGt, List<String> strings) {
        for (String s : strings) {
            assertEquals(s, test.validate(s, "allowedRequired", true, cantEndWithGt), allowedMessage(s));
        }
    }

    protected void notAllowedRequired(StringAndRequiredTest test, List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> test.validate(s, true), notAllowedMessage(s));
        }
    }

    protected void notAllowedRequiredStrict(List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> validateSubjectTermStrict(s, "notAllowedRequiredStrict", true), notAllowedMessage(s));
        }
    }

    protected void notAllowedRequiredCheckEndWith(StringLabelRequiredCantEndWithGtTest test, boolean cantEndWithGt, List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> test.validate(s, "notAllowedRequired", true, cantEndWithGt), notAllowedMessage(s));
        }
    }

    protected void allowedNotRequired(StringAndRequiredTest test, List<String> strings) {
        for (String s : strings) {
            assertEquals(s, test.validate(s, false), allowedMessage(s));
        }
    }

    protected void allowedNotRequiredCheckEndWith(StringLabelRequiredCantEndWithGtTest test, boolean cantEndWithGt, List<String> strings) {
        for (String s : strings) {
            assertEquals(s, test.validate(s, "allowedNotRequired", false, cantEndWithGt), allowedMessage(s));
        }
    }

    protected void allowedNotRequiredEmptyAsNull(StringAndRequiredTest test, List<String> strings) {
        for (String s : strings) {
            assertNull(test.validate(s, false), allowedMessage(s));
        }
    }

    protected void notAllowedNotRequired(StringAndRequiredTest test, List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> test.validate(s, false), notAllowedMessage(s));
        }
    }

    protected void notAllowedNotRequiredStrict(List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> validateSubjectTermStrict(s, "notAllowedRequiredStrict", false), notAllowedMessage(s));
        }
    }

    protected void notAllowedNotRequiredCheckEndWith(StringLabelRequiredCantEndWithGtTest test, boolean cantEndWithGt, List<String> strings) {
        for (String s : strings) {
            assertThrows(IllegalArgumentException.class, () -> test.validate(s, "notAllowedNotRequired", false, cantEndWithGt), notAllowedMessage(s));
        }
    }

    protected String allowedMessage(String s) {
        return "Testing [" + s + "] as allowed.";
    }

    protected String notAllowedMessage(String s) {
        return "Testing [" + s + "] as not allowed.";
    }

    @Test
    public void testSemver() {
        String label = "Version";
        validateSemVer("0.0.4", label, true);
        validateSemVer("1.2.3", label, true);
        validateSemVer("10.20.30", label, true);
        validateSemVer("1.1.2-prerelease+meta", label, true);
        validateSemVer("1.1.2+meta", label, true);
        validateSemVer("1.1.2+meta-valid", label, true);
        validateSemVer("1.0.0-alpha", label, true);
        validateSemVer("1.0.0-beta", label, true);
        validateSemVer("1.0.0-alpha.beta", label, true);
        validateSemVer("1.0.0-alpha.beta.1", label, true);
        validateSemVer("1.0.0-alpha.1", label, true);
        validateSemVer("1.0.0-alpha0.valid", label, true);
        validateSemVer("1.0.0-alpha.0valid", label, true);
        validateSemVer("1.0.0-alpha-a.b-c-somethinglong+build.1-aef.1-its-okay", label, true);
        validateSemVer("1.0.0-rc.1+build.1", label, true);
        validateSemVer("2.0.0-rc.1+build.123", label, true);
        validateSemVer("1.2.3-beta", label, true);
        validateSemVer("10.2.3-DEV-SNAPSHOT", label, true);
        validateSemVer("1.2.3-SNAPSHOT-123", label, true);
        validateSemVer("1.0.0", label, true);
        validateSemVer("2.0.0", label, true);
        validateSemVer("1.1.7", label, true);
        validateSemVer("2.0.0+build.1848", label, true);
        validateSemVer("2.0.1-alpha.1227", label, true);
        validateSemVer("1.0.0-alpha+beta", label, true);
        validateSemVer("1.2.3----RC-SNAPSHOT.12.9.1--.12+788", label, true);
        validateSemVer("1.2.3----R-S.12.9.1--.12+meta", label, true);
        validateSemVer("1.2.3----RC-SNAPSHOT.12.9.1--.12", label, true);
        validateSemVer("1.0.0+0.build.1-rc.10000aaa-kk-0.1", label, true);
        validateSemVer("99999999999999999999999.999999999999999999.99999999999999999", label, true);
        validateSemVer("1.0.0-0A.is.legal", label, true);

        assertNull(validateSemVer(null, label, false));
        assertNull(validateSemVer("", label, false));
        
        assertThrows(IllegalArgumentException.class, () -> validateSemVer(null, label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("", label, true));

        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2.3-0123", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2.3-0123.0123", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.1.2+.123", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("+invalid", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("-invalid", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("-invalid+invalid", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("-invalid.01", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha.beta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha.beta.1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha.1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha+beta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha_beta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha.", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("alpha..", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("beta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha_beta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("-alpha.", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha..", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha..1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha...1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha....1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha.....1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha......1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.0.0-alpha.......1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("01.1.1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.01.1", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.1.01", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2.3.DEV", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2-SNAPSHOT", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2.31.2.3----RC-SNAPSHOT.12.09.1--..12+788", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("1.2-RC-SNAPSHOT", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("-1.0.3-gamma+b7718", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("+justmeta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("9.8.7+meta+meta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("9.8.7-whatever+meta+meta", label, true));
        assertThrows(IllegalArgumentException.class, () -> validateSemVer("99999999999999999999999.999999999999999999.99999999999999999----RC-SNAPSHOT.12.09.1--------------------------------..12", label, true));
    }

    @Test
    public void testListsAreEquivalent() {
        List<String> l1 = Arrays.asList("one", "two");
        List<String> l2 = Arrays.asList("two", "one");
        List<String> l3 = Arrays.asList("one", "not");
        List<String> l4 = Collections.singletonList("three");
        List<String> l5 = new ArrayList<>();

        assertTrue(listsAreEquivalent(l1, l1));
        assertTrue(listsAreEquivalent(l1, l2));
        assertFalse(listsAreEquivalent(l1, l3));
        assertFalse(listsAreEquivalent(l1, l4));
        assertFalse(listsAreEquivalent(l1, null));
        assertFalse(listsAreEquivalent(l1, l5));

        assertTrue(listsAreEquivalent(l2, l1));
        assertTrue(listsAreEquivalent(l2, l2));
        assertFalse(listsAreEquivalent(l2, l3));
        assertFalse(listsAreEquivalent(l2, l4));
        assertFalse(listsAreEquivalent(l2, null));
        assertFalse(listsAreEquivalent(l2, l5));

        assertFalse(listsAreEquivalent(l3, l1));
        assertFalse(listsAreEquivalent(l3, l2));
        assertTrue(listsAreEquivalent(l3, l3));
        assertFalse(listsAreEquivalent(l3, l4));
        assertFalse(listsAreEquivalent(l3, null));
        assertFalse(listsAreEquivalent(l3, l5));

        assertFalse(listsAreEquivalent(l4, l1));
        assertFalse(listsAreEquivalent(l4, l2));
        assertFalse(listsAreEquivalent(l4, l3));
        assertTrue(listsAreEquivalent(l4, l4));
        assertFalse(listsAreEquivalent(l4, null));
        assertFalse(listsAreEquivalent(l4, l5));

        assertFalse(listsAreEquivalent(null, l1));
        assertFalse(listsAreEquivalent(null, l2));
        assertFalse(listsAreEquivalent(null, l3));
        assertFalse(listsAreEquivalent(null, l4));
        assertTrue(listsAreEquivalent(null, null));
        assertTrue(listsAreEquivalent(null, l5));

        assertFalse(listsAreEquivalent(l5, l1));
        assertFalse(listsAreEquivalent(l5, l2));
        assertFalse(listsAreEquivalent(l5, l3));
        assertFalse(listsAreEquivalent(l5, l4));
        assertTrue(listsAreEquivalent(l5, null));
        assertTrue(listsAreEquivalent(l5, l5));
    }

    @Test
    public void testMapsAreEqual() {
        Map<String, String> m1 = new HashMap<>();
        m1.put("one", "1");
        m1.put("two", "2");

        Map<String, String> m2 = new HashMap<>();
        m2.put("two", "2");
        m2.put("one", "1");

        Map<String, String> m3 = new HashMap<>();
        m3.put("one", "1");
        m3.put("two", "not");

        Map<String, String> m4 = new HashMap<>();
        m4.put("one", "1");
        m4.put("not", "not");

        Map<String, String> m5 = new HashMap<>();
        m5.put("five", "5");

        Map<String, String> m6 = new HashMap<>();

        assertTrue(mapsAreEquivalent(m1, m1));
        assertTrue(mapsAreEquivalent(m1, m2));
        assertFalse(mapsAreEquivalent(m1, m3));
        assertFalse(mapsAreEquivalent(m1, m4));
        assertFalse(mapsAreEquivalent(m1, m5));
        assertFalse(mapsAreEquivalent(m1, null));
        assertFalse(mapsAreEquivalent(m1, m6));

        assertTrue(mapsAreEquivalent(m2, m1));
        assertTrue(mapsAreEquivalent(m2, m2));
        assertFalse(mapsAreEquivalent(m2, m3));
        assertFalse(mapsAreEquivalent(m2, m4));
        assertFalse(mapsAreEquivalent(m2, m5));
        assertFalse(mapsAreEquivalent(m2, null));
        assertFalse(mapsAreEquivalent(m2, m6));

        assertFalse(mapsAreEquivalent(m3, m1));
        assertFalse(mapsAreEquivalent(m3, m2));
        assertTrue(mapsAreEquivalent(m3, m3));
        assertFalse(mapsAreEquivalent(m3, m4));
        assertFalse(mapsAreEquivalent(m3, m5));
        assertFalse(mapsAreEquivalent(m3, null));
        assertFalse(mapsAreEquivalent(m3, m6));

        assertFalse(mapsAreEquivalent(m4, m1));
        assertFalse(mapsAreEquivalent(m4, m2));
        assertFalse(mapsAreEquivalent(m4, m3));
        assertTrue(mapsAreEquivalent(m4, m4));
        assertFalse(mapsAreEquivalent(m4, m5));
        assertFalse(mapsAreEquivalent(m4, null));
        assertFalse(mapsAreEquivalent(m4, m6));

        assertFalse(mapsAreEquivalent(m5, m1));
        assertFalse(mapsAreEquivalent(m5, m2));
        assertFalse(mapsAreEquivalent(m5, m3));
        assertFalse(mapsAreEquivalent(m5, m4));
        assertTrue(mapsAreEquivalent(m5, m5));
        assertFalse(mapsAreEquivalent(m5, null));
        assertFalse(mapsAreEquivalent(m5, m6));

        assertFalse(mapsAreEquivalent(null, m1));
        assertFalse(mapsAreEquivalent(null, m2));
        assertFalse(mapsAreEquivalent(null, m3));
        assertFalse(mapsAreEquivalent(null, m4));
        assertFalse(mapsAreEquivalent(null, m5));
        assertTrue(mapsAreEquivalent(null, null));
        assertTrue(mapsAreEquivalent(null, m6));

        assertFalse(mapsAreEquivalent(m6, m1));
        assertFalse(mapsAreEquivalent(m6, m2));
        assertFalse(mapsAreEquivalent(m6, m3));
        assertFalse(mapsAreEquivalent(m6, m5));
        assertTrue(mapsAreEquivalent(m6, null));
        assertTrue(mapsAreEquivalent(m6, m6));
    }
}
