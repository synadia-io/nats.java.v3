package io.synadia.client.testutils;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.synadia.client.testutils.NatsConstants.DOT;

@SuppressWarnings("UnusedReturnValue")
public abstract class Validator {

    protected Validator() {} /* ensures cannot be constructed */

    /*
        cannot contain spaces \r \n \t
    */
    public static String validateSubjectTerm(String subject, String label, boolean required) {
        if (subject == null || subject.length() == 0) {
            if (required) {
                throw new IllegalArgumentException(label + " cannot be null or empty.");
            }
            return null;
        }
        for (int i = 0; i < subject.length(); i++) {
            char c = subject.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                throw new IllegalArgumentException(label + " cannot contain space, tab, carriage return or linefeed character");
            }
        }
        return subject;
    }

    /*
        cannot contain spaces \r \n \t
        cannot start or end with subject token delimiter .
        some things don't allow it to end greater
    */
    public static String validateSubjectTermStrict(String subject, String label, boolean required) {
        subject = emptyAsNull(subject);
        if (subject == null) {
            if (required) {
                throw new IllegalArgumentException(label + " cannot be null or empty.");
            }
            return null;
        }
        if (subject.endsWith(".")) {
            throw new IllegalArgumentException(label + " cannot end with '.'");
        }

        String[] segments = subject.split("\\.");
        for (int seg = 0; seg < segments.length; seg++) {
            String segment = segments[seg];
            int sl = segment.length();
            if (sl == 0) {
                if (seg == 0) {
                    throw new IllegalArgumentException(label + " cannot start with '.'");
                }
                throw new IllegalArgumentException(label + " segment cannot be empty");
            }
            else {
                for (int m = 0; m < sl; m++) {
                    char c = segment.charAt(m);
                    if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                        throw new IllegalArgumentException(label + " cannot contain space, tab, carriage return or linefeed character");
                    }
                    if (c == '*') {
                        if (sl != 1) {
                            throw new IllegalArgumentException(label + " wildcard improperly placed.");
                        }
                    }
                    if (c == '>') {
                        if (sl != 1 || (seg + 1 != segments.length)) {
                            throw new IllegalArgumentException(label + " wildcard improperly placed.");
                        }
                    }
                }
            }
        }
        return subject;
    }

    public static String validateSubject(String s, boolean required) {
        return validateSubjectTerm(s, "Subject", required);
    }

    public static String validateSubjectStrict(String s, boolean required) {
        return validateSubjectTermStrict(s, "Subject", required);
    }

    public static String validateSubject(String subject, String label, boolean required, boolean cantEndWithGt) {
        subject = validateSubjectTermStrict(subject, label, required);
        if (subject != null && cantEndWithGt && subject.endsWith(".>")) {
            throw new IllegalArgumentException(label + " last segment cannot be '>'");
        }
        return subject;
    }

    public static String validateReplyTo(String s, boolean required) {
        return validatePrintableExceptWildGt(s, "Reply To", required);
    }

    public static String validateQueueName(String s, boolean required) {
        return validateSubjectTermStrict(s, "QueueName", required);
    }

    public static String required(String s, String label) {
        if (emptyAsNull(s) == null) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
        return s;
    }

    public static <T> T required(T o, String label) {
        if (o == null) {
            throw new IllegalArgumentException(label + " cannot be null.");
        }
        return o;
    }

    public static void required(List<?> l, String label) {
        if (l == null || l.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
    }

    public static void required(Map<?, ?> m, String label) {
        if (m == null || m.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
    }

    public static String _validate(String s, boolean required, String label, Supplier<String> customValidate) {
        if (emptyAsNull(s) == null) {
            if (required) {
                throw new IllegalArgumentException(label + " cannot be null or empty.");
            }
            return null;
        }
        return customValidate.get();
    }

    public static String validatePrintable(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintable(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range [" + s + "]");
            }
            return s;
        });
    }

    public static String validatePrintableExceptWildDotGt(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGtDot(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '.' or '>' [" + s + "]");
            }
            return s;
        });
    }

    public static String validatePrintableExceptWildDotGtSlashes(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGtDotSlashes(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '.', '>', '\\' or  '/' [" + s + "]");
            }
            return s;
        });
    }

    public static String validatePrintableExceptWildGt(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGt(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*' or '>' [" + s + "]");
            }
            return s;
        });
    }

    public static String validateIsRestrictedTerm(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notRestrictedTerm(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '-' or '_' [" + s + "]");
            }
            return s;
        });
    }

    public static String validateNotNull(String s, String fieldName) {
        if (s == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return s;
    }

    public static Object validateNotNull(Object o, String fieldName) {
        if (o == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return o;
    }

    public static int validateGtZero(int i, String label) {
        if (i < 1) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return i;
    }

    public static long validateGtZero(long l, String label) {
        if (l < 1) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return l;
    }

    public static long validateGtZeroOrMinus1(long l, String label) {
        if (zeroOrLtMinus1(l)) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return l;
    }

    public static int validateGtZeroOrMinus1(int i, String label) {
        if (zeroOrLtMinus1(i)) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return i;
    }

    public static long validateGtEqMinus1(long l, String label) {
        if (l < -1) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return l;
    }

    public static long validateNotNegative(long l, String label) {
        if (l < 0) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
        return l;
    }

    public static boolean isGtEqZero(long l) {
        return l >= 0;
    }

    public static long validateGtEqZero(long l, String label) {
        if (l < 0) {
            throw new IllegalArgumentException(label + " must be greater than or equal to zero");
        }
        return l;
    }

    public static Duration validateDurationRequired(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException("Duration required and must be greater than 0.");
        }
        return d;
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    public static boolean nullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static <T> boolean nullOrEmpty(T[] a) {
        return a == null || a.length == 0;
    }

    public static boolean nullOrEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    public static boolean notPrintable(String s) {
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < 33 || c > 126) {
                return true;
            }
        }
        return false;
    }

    public static boolean notPrintableOrHasChars(String s, char[] charsToNotHave) {
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < 33 || c > 126) {
                return true;
            }
            for (char cx : charsToNotHave) {
                if (c == cx) {
                    return true;
                }
            }
        }
        return false;
    }

    // restricted-term  = (A-Z, a-z, 0-9, dash 45, underscore 95)+
    public static boolean notRestrictedTerm(String s) {
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < '0') { // before 0
                if (c == '-') { // only dash is accepted
                    continue;
                }
                return true; // "not"
            }
            if (c < ':') {
                continue; // means it's 0 - 9
            }
            if (c < 'A') {
                return true; // between 9 and A is "not restricted"
            }
            if (c < '[') {
                continue; // means it's A - Z
            }
            if (c < 'a') { // before a
                if (c == '_') { // only underscore is accepted
                    continue;
                }
                return true; // "not"
            }
            if (c > 'z') { // 122 is z, characters after of them are "not restricted"
                return true;
            }
        }
        return false;
    }

    protected static final char[] WILD_GT = {'*', '>'};
    protected static final char[] WILD_GT_DOT = {'*', '>', '.'};
    protected static final char[] WILD_GT_DOT_SLASHES = {'*', '>', '.', '\\', '/'};

    public static boolean notPrintableOrHasWildGt(String s) {
        return notPrintableOrHasChars(s, WILD_GT);
    }

    public static boolean notPrintableOrHasWildGtDot(String s) {
        return notPrintableOrHasChars(s, WILD_GT_DOT);
    }

    public static boolean notPrintableOrHasWildGtDotSlashes(String s) {
        return notPrintableOrHasChars(s, WILD_GT_DOT_SLASHES);
    }

    public static String emptyAsNull(String s) {
        return nullOrEmpty(s) ? null : s;
    }

    public static String emptyOrNullAs(String s, String ifEmpty) {
        return nullOrEmpty(s) ? ifEmpty : s;
    }

    public static boolean zeroOrLtMinus1(long l) {
        return l == 0 || l < -1;
    }

    public static Duration ensureNotNullAndNotLessThanMin(Duration provided, Duration minimum, Duration dflt)
    {
        return provided == null || provided.toNanos() < minimum.toNanos() ? dflt : provided;
    }

    public static Duration ensureDurationNotLessThanMin(long providedMillis, Duration minimum, Duration dflt)
    {
        return ensureNotNullAndNotLessThanMin(Duration.ofMillis(providedMillis), minimum, dflt);
    }

    public static String ensureEndsWithDot(String s) {
        return s == null || s.endsWith(DOT) ? s : s + DOT;
    }

    protected static final Pattern SEMVER_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    public static String validateSemVer(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (!isSemVer(s)) {
                throw new IllegalArgumentException(label + " must be a valid SemVer");
            }
            return s;
        });
    }

    public static boolean isSemVer(String s) {
        return SEMVER_PATTERN.matcher(s).find();
    }

    // This function tests filter subject equivalency
    // It does not care what order and also assumes that there are no duplicates.
    // From the server: consumer subject filters cannot overlap [10138]
    public static <T> boolean listsAreEquivalent(List<T> l1, List<T> l2)
    {
        if (l1 == null || l1.isEmpty()) {
            return l2 == null || l2.isEmpty();
        }

        if (l2 == null || l1.size() != l2.size()) {
            return false;
        }

        for (T t : l1) {
            if (!l2.contains(t)) {
                return false;
            }
        }
        return true;
    }

    public static boolean mapsAreEquivalent(Map<String, String> m1, Map<String, String> m2)
    {
        int s1 = m1 == null ? 0 : m1.size();
        int s2 = m2 == null ? 0 : m2.size();

        if (s1 != s2) {
            return false;
        }

        if (s1 > 0) {
            for (Map.Entry<String, String> entry : m1.entrySet())
            {
                if (!entry.getValue().equals(m2.get(entry.getKey()))) {
                    return false;
                }
            }
        }

        return true;
    }
}
