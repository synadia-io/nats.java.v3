package io.synadia.client.utils;

import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static io.synadia.client.utils.NatsConstants.DOT;

/**
 * Validation helpers for the values the client accepts: subjects, queue names, durations and numeric limits.
 * Each {@code validate} method returns the value it was given so it can be used inline, and throws
 * {@link IllegalArgumentException} when the value is not acceptable. The {@code notX} and {@code nullOrEmpty}
 * methods are the predicate forms, returning a boolean instead of throwing.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class Validator {

    protected Validator() {} /* ensures cannot be constructed */

    /**
     * Validate a single subject term under the lenient rules.
     * <ul>
     * <li>cannot contain a space, tab, carriage return or linefeed</li>
     * </ul>
     * @param subject the term to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
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

    /**
     * Validate a single subject term under the strict rules.
     * <ul>
     * <li>cannot contain a space, tab, carriage return or linefeed</li>
     * <li>cannot start or end with the subject token delimiter '.', and no token may be empty,
     *     so consecutive delimiters are rejected</li>
     * <li>the '*' wildcard is only valid as an entire token, never as part of one - "a.*.b" is
     *     accepted, "a.*x.b" is not</li>
     * <li>the '&gt;' wildcard is only valid as an entire token <i>and</i> only as the last token -
     *     "a.&gt;" is accepted, "a.x&gt;" and "a.&gt;.b" are not</li>
     * </ul>
     * @param subject the term to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateSubjectTermStrict(String subject, String label, boolean required) {
        subject = emptyAsNull(subject);
        if (subject == null) {
            if (required) {
                throw new IllegalArgumentException(label + " cannot be null or empty.");
            }
            return null;
        }
        return validateSubjectTermStrict(subject, label);
    }

    /**
     * Validate a required subject term under the strict rules, which are listed on
     * {@link #validateSubjectTermStrict(String, String, boolean) validateSubjectTermStrict}.
     * @param subject the term to validate
     * @param label name used in the error message
     * @return the term
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static @NonNull String validateSubjectTermStrict(@NonNull String subject, String label) {
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

    /**
     * Validate a subject under the lenient rules.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateSubject(String s, boolean required) {
        return validateSubjectTerm(s, "Subject", required);
    }

    /**
     * Validate a subject under the strict rules.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateSubjectStrict(String s, boolean required) {
        return validateSubjectTermStrict(s, "Subject", required);
    }

    /**
     * Validate a subject, optionally rejecting a trailing greater-than wildcard.
     * @param subject the subject to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @param cantEndWithGt true to reject a subject ending in the greater-than wildcard
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateSubject(String subject, String label, boolean required, boolean cantEndWithGt) {
        subject = validateSubjectTermStrict(subject, label, required);
        if (subject != null && cantEndWithGt && subject.endsWith(".>")) {
            throw new IllegalArgumentException(label + " last segment cannot be '>'");
        }
        return subject;
    }

    /**
     * Validate a reply-to subject.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateReplyTo(String s, boolean required) {
        return validatePrintableExceptWildGt(s, "Reply To", required);
    }

    /**
     * Validate a queue group name.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateQueueName(String s, boolean required) {
        return validateSubjectTermStrict(s, "QueueName", required);
    }

    /**
     * Require a non-null, non-empty string.
     * @param s the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static @NonNull String required(String s, String label) {
        if (emptyAsNull(s) == null) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
        return s;
    }

    /**
     * Require a non-null object.
     * @param <T> the value type
     * @param o the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static @NonNull <T> T required(T o, String label) {
        if (o == null) {
            throw new IllegalArgumentException(label + " cannot be null.");
        }
        return o;
    }

    /**
     * Require a non-null, non-empty list.
     * @param l the list to validate
     * @param label name used in the error message
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static void required(List<?> l, String label) {
        if (l == null || l.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
    }

    /**
     * Require a non-null, non-empty map.
     * @param m the map to validate
     * @param label name used in the error message
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static void required(Map<?, ?> m, String label) {
        if (m == null || m.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be null or empty.");
        }
    }

    /**
     * Apply the shared required/empty handling, delegating the value check to the supplier.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @param label name used in the error message
     * @param customValidate supplies the validated value when one was given
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String _validate(String s, boolean required, String label, Supplier<String> customValidate) {
        if (emptyAsNull(s) == null) {
            if (required) {
                throw new IllegalArgumentException(label + " cannot be null or empty.");
            }
            return null;
        }
        return customValidate.get();
    }

    /**
     * Validate that a value contains only printable characters.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validatePrintable(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintable(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate printable characters, also rejecting wildcards, dot and greater-than.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validatePrintableExceptWildDotGt(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGtDot(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '.' or '>' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate printable characters, also rejecting wildcards, dot, greater-than and slashes.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validatePrintableExceptWildDotGtSlashes(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGtDotSlashes(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '.', '>', '\\' or  '/' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate printable characters, also rejecting wildcards and greater-than.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validatePrintableExceptWildGt(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notPrintableOrHasWildGt(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*' or '>' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate a restricted term, which allows only letters, digits, dash and underscore.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateIsRestrictedTerm(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notRestrictedTerm(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '-' or '_' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Require a non-null string.
     * @param s the value to validate
     * @param fieldName name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateNotNull(String s, String fieldName) {
        if (s == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return s;
    }

    /**
     * Require a non-null object.
     * @param o the value to validate
     * @param fieldName name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Object validateNotNull(Object o, String fieldName) {
        if (o == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
        return o;
    }

    /**
     * Require a value greater than zero.
     * @param i the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static int validateGtZero(int i, String label) {
        if (i < 1) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return i;
    }

    /**
     * Require a value greater than zero.
     * @param l the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateGtZero(long l, String label) {
        if (l < 1) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return l;
    }

    /**
     * Require a value greater than zero, or exactly -1.
     * @param l the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateGtZeroOrMinus1(long l, String label) {
        if (zeroOrLtMinus1(l)) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return l;
    }

    /**
     * Require a value greater than zero, or exactly -1.
     * @param i the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static int validateGtZeroOrMinus1(int i, String label) {
        if (zeroOrLtMinus1(i)) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return i;
    }

    /**
     * Require a value greater than or equal to -1.
     * @param l the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateGtEqMinus1(long l, String label) {
        if (l < -1) {
            throw new IllegalArgumentException(label + " must be greater than zero or -1 for unlimited");
        }
        return l;
    }

    /**
     * Require a value that is not negative.
     * @param l the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateNotNegative(long l, String label) {
        if (l < 0) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
        return l;
    }

    /**
     * Whether a value is greater than or equal to zero.
     * @param l the value to check
     * @return true if the value is not negative
     */
    public static boolean isGtEqZero(long l) {
        return l >= 0;
    }

    /**
     * Require a value greater than or equal to zero.
     * @param l the value to validate
     * @param label name used in the error message
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateGtEqZero(long l, String label) {
        if (l < 0) {
            throw new IllegalArgumentException(label + " must be greater than or equal to zero");
        }
        return l;
    }

    /**
     * Require a duration that is present and greater than zero.
     * @param d the duration to validate
     * @return the duration
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateDurationRequired(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException("Duration required and must be greater than 0.");
        }
        return d;
    }

    // ----------------------------------------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------------------------------------
    /**
     * Whether a string is null or empty.
     * @param s the string to check
     * @return true if null or empty
     */
    public static boolean nullOrEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Whether an array is null or empty.
     * @param <T> the array element type
     * @param a the array to check
     * @return true if null or empty
     */
    public static <T> boolean nullOrEmpty(T[] a) {
        return a == null || a.length == 0;
    }

    /**
     * Whether a collection is null or empty.
     * @param c the collection to check
     * @return true if null or empty
     */
    public static boolean nullOrEmpty(Collection<?> c) {
        return c == null || c.isEmpty();
    }

    /**
     * Whether a string contains a non-printable character.
     * @param s the string to check
     * @return true if not printable
     */
    public static boolean notPrintable(String s) {
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < 33 || c > 126) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a string is non-printable or contains any of the given characters.
     * @param s the string to check
     * @param charsToNotHave the characters that must not appear
     * @return true if not acceptable
     */
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
    /**
     * Whether a string is not a valid restricted term.
     * @param s the string to check
     * @return true if not a restricted term
     */
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

    /**
     * Whether a string is non-printable or contains a wildcard or greater-than.
     * @param s the string to check
     * @return true if not acceptable
     */
    public static boolean notPrintableOrHasWildGt(String s) {
        return notPrintableOrHasChars(s, WILD_GT);
    }

    /**
     * Whether a string is non-printable or contains a wildcard, greater-than or dot.
     * @param s the string to check
     * @return true if not acceptable
     */
    public static boolean notPrintableOrHasWildGtDot(String s) {
        return notPrintableOrHasChars(s, WILD_GT_DOT);
    }

    /**
     * Whether a string is non-printable or contains a wildcard, greater-than, dot or slash.
     * @param s the string to check
     * @return true if not acceptable
     */
    public static boolean notPrintableOrHasWildGtDotSlashes(String s) {
        return notPrintableOrHasChars(s, WILD_GT_DOT_SLASHES);
    }

    /**
     * Treat an empty or blank string as null.
     * @param s the string, may be null
     * @return the string, or null if it was empty
     */
    public static String emptyAsNull(String s) {
        return nullOrEmpty(s) ? null : s;
    }

    /**
     * Substitute a default when a string is null or empty.
     * @param s the string, may be null
     * @param ifEmpty returned when the string is null or empty
     * @return the string, or ifEmpty
     */
    public static String emptyOrNullAs(String s, String ifEmpty) {
        return nullOrEmpty(s) ? ifEmpty : s;
    }

    /**
     * Whether a value is zero or less than -1, the values many settings reject.
     * @param l the value to check
     * @return true if zero or less than -1
     */
    public static boolean zeroOrLtMinus1(long l) {
        return l == 0 || l < -1;
    }

    /**
     * Substitute a default when a duration is null or below a minimum.
     * @param provided the duration, may be null
     * @param minimum the minimum acceptable duration
     * @param dflt returned when provided is null or too small
     * @return the duration, or the default
     */
    public static Duration ensureNotNullAndNotLessThanMin(Duration provided, Duration minimum, Duration dflt)
    {
        return provided == null || provided.toNanos() < minimum.toNanos() ? dflt : provided;
    }

    /**
     * Substitute a default when a duration in millis is below a minimum.
     * @param providedMillis the duration in milliseconds
     * @param minimum the minimum acceptable duration
     * @param dflt returned when the value is too small
     * @return the duration, or the default
     */
    public static Duration ensureDurationNotLessThanMin(long providedMillis, Duration minimum, Duration dflt)
    {
        return ensureNotNullAndNotLessThanMin(Duration.ofMillis(providedMillis), minimum, dflt);
    }

    /**
     * Append a dot to a string when it does not already end with one.
     * @param s the string
     * @return the string ending in a dot
     */
    public static String ensureEndsWithDot(String s) {
        return s == null || s.endsWith(DOT) ? s : s + DOT;
    }

    protected static final Pattern SEMVER_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    /**
     * Validate a semantic version string.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateSemVer(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (!isSemVer(s)) {
                throw new IllegalArgumentException(label + " must be a valid SemVer");
            }
            return s;
        });
    }

    /**
     * Whether a string is a valid semantic version.
     * @param s the string to check
     * @return true if it is a semantic version
     */
    public static boolean isSemVer(String s) {
        return SEMVER_PATTERN.matcher(s).find();
    }

    // This function tests filter subject equivalency
    // It does not care what order and also assumes that there are no duplicates.
    // From the server: consumer subject filters cannot overlap [10138]
    /**
     * Compare two lists ignoring order, treating null and empty as equivalent.
     * @param <T> the list element type
     * @param l1 the first list, may be null
     * @param l2 the second list, may be null
     * @return true if the lists are equivalent
     */
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

    /**
     * Compare two maps, treating null and empty as equivalent.
     * @param m1 the first map, may be null
     * @param m2 the second map, may be null
     * @return true if the maps are equivalent
     */
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
