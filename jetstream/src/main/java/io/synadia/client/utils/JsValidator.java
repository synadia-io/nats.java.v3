package io.synadia.client.utils;

import io.synadia.client.impl.JetStreamConstants;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.synadia.client.impl.JetStreamConstants.MAX_HISTORY_PER_KEY;
import static io.synadia.client.utils.NatsConstants.DOT;

@SuppressWarnings("UnusedReturnValue")
public abstract class JsValidator extends Validator {

    protected JsValidator() {} /* ensures cannot be constructed */

    public static String validateStreamName(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Stream", required);
    }

    public static String validateDurable(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Durable", required);
    }

    public static String validateConsumerName(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Name", required);
    }

    public static String validatePrefixOrDomain(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (s.startsWith(DOT)) {
                throw new IllegalArgumentException(label + " cannot start with '.' [" + s + "]");
            }
            if (notPrintableOrHasWildGt(s)) {
                throw new IllegalArgumentException(label + " must be in the printable ASCII range and cannot include '*', '>' [" + s + "]");
            }
            return s;
        });
    }

    public static List<String> validateKvKeysWildcardAllowedRequired(List<String> keys) {
        required(keys, "Key");
        for (String key : keys) {
            validateWildcardKvKey(key, "Key", true);
        }
        return keys;
    }

    public static String validateKvKeyWildcardAllowedRequired(String s) {
        return validateWildcardKvKey(s, "Key", true);
    }

    public static String validateNonWildcardKvKeyRequired(String s) {
        return validateNonWildcardKvKey(s, "Key", true);
    }

    public static void validateNotSupplied(String s, JetStreamClientError err) {
        if (!nullOrEmpty(s)) {
            throw err.instance();
        }
    }

    public static String validateMustMatchIfBothSupplied(String s1, String s2, JetStreamClientError err) {
        // s1   | s2   || result
        // ---- | ---- || --------------
        // null | null || valid, null s2
        // null | y    || valid, y s2
        // x    | null || valid, x s1
        // x    | x    || valid, x s1
        // x    | y    || invalid
        s1 = emptyAsNull(s1);
        s2 = emptyAsNull(s2);
        if (s1 == null) {
            return s2; // s2 can be either null or y
        }

        // x / null or x / x
        if (s2 == null || s1.equals(s2)) {
            return s1;
        }

        throw err.instance();
    }

    public static String validateBucketName(String s, boolean required) {
        return validateIsRestrictedTerm(s, "Bucket Name", required);
    }

    public static String validateWildcardKvKey(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notWildcardKvKey(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '*', '-', '_', '/', '=', '>' or '.' and cannot start with '.' [" + s + "]");
            }
            return s;
        });
    }

    public static String validateNonWildcardKvKey(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notNonWildcardKvKey(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '-', '_', '/', '=' or '.' and cannot start with '.' [" + s + "]");
            }
            return s;
        });
    }

    public static int validateMaxHistory(int max) {
        if (max < 1 || max > MAX_HISTORY_PER_KEY) {
            throw new IllegalArgumentException("Max History must be from 1 to " + MAX_HISTORY_PER_KEY + " inclusive.");
        }
        return max;
    }

    public static long validateMaxBucketBytes(long max) {
        return validateGtZeroOrMinus1(max, "Max Bucket Bytes"); // max bucket bytes is a kv alias to max bytes
    }

    public static int validateMaxValueSize(int max) {
        return validateGtZeroOrMinus1(max, "Max Value Size"); // max value size is a kv alias to max message size
    }

    public static int validateNumberOfReplicas(int replicas) {
        if (replicas < 1 || replicas > 5) {
            throw new IllegalArgumentException("Replicas must be from 1 to 5 inclusive.");
        }
        return replicas;
    }

    public static Duration validateDurationNotRequiredGtOrEqZero(Duration d, Duration ifNull) {
        if (d == null) {
            return ifNull;
        }
        if (d.isNegative()) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        return d;
    }

    public static Duration validateDurationNotRequiredGtOrEqZero(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        return Duration.ofMillis(millis);
    }

    public static Duration validateDurationNotRequiredGtOrEqZero(long millis, Duration ifZero) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        if (millis == 0) {
            return ifZero;
        }
        return Duration.ofMillis(millis);
    }

    public static Duration validateDurationNotRequiredGtOrEqSeconds(long minSeconds, Duration d, Duration ifNull, String label) {
        return d == null ? ifNull : validateDurationGtOrEqSeconds(minSeconds, d.toMillis(), label);
    }

    public static Duration validateDurationGtOrEqSeconds(long minSeconds, long millis, String label) {
        if (millis < (minSeconds * 1000)) {
            throw new IllegalArgumentException(label + " must be greater than or equal to " + minSeconds + " second(s).");
        }
        return Duration.ofMillis(millis);
    }

    // limited-term = (A-Z, a-z, 0-9, dash 45, dot 46, fwd-slash 47, equals 61, underscore 95)+
    // kv-key-name = limited-term (dot limited-term)*
    public static boolean notNonWildcardKvKey(String s) {
        if (s.charAt(0) == '.') {
            return true; // can't start with dot
        }
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < '0') { // before 0
                if (c == '-' || c == '.' || c == '/') { // only dash dot and fwd slash are accepted
                    continue;
                }
                return true; // "not"
            }
            if (c < ':') {
                continue; // means it's 0 - 9
            }
            if (c < 'A') {
                if (c == '=') { // equals is accepted
                    continue;
                }
                return true; // between 9 and A is "not limited"
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
            if (c > 'z') { // 122 is z, characters after of them are "not limited"
                return true;
            }
        }
        return false;
    }

    // (A-Z, a-z, 0-9, star 42, dash 45, dot 46, fwd-slash 47, equals 61, gt 62, underscore 95)+
    public static boolean notWildcardKvKey(String s) {
        if (s.charAt(0) == '.') {
            return true; // can't start with dot
        }
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < '0') { // before 0
                if (c == '*' || c == '-' || c == '.' || c == '/') { // only star dash dot and fwd slash are accepted
                    continue;
                }
                return true; // "not"
            }
            if (c < ':') {
                continue; // means it's 0 - 9
            }
            if (c < 'A') {
                if (c == '=' || c == '>') { // equals, gt is accepted
                    continue;
                }
                return true; // between 9 and A is "not limited"
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
            if (c > 'z') { // 122 is z, characters after of them are "not limited"
                return true;
            }
        }
        return false;
    }

    // this is a special case map where the meta has both user and nats headers like
    // _nats.req.level=0, _nats.ver=2.12.0-preview.2, _nats.level=2
    // in this case we only want to compare the user keys
    public static boolean metaIsEquivalent(Map<String, String> m1, Map<String, String> m2) {
        if (m1 == null || m1.isEmpty()) {
            return m2 == null || m2.isEmpty();
        }

        // m1 isn't null or empty
        if (m2 == null || m2.isEmpty()) {
            return false;
        }

        // 1. make sure all user keys from m1 are in m2
        int user1 = 0;
        for (Map.Entry<String, String> entry : m1.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(JetStreamConstants.NATS_META_KEY_PREFIX)) {
                if (!m2.containsKey(key)) {
                    return false;
                }
                user1++;
            }
        }

        // 2. all m1 keys were found in m2, so count m2 user keys
        int user2 = 0;
        for (String key : m2.keySet()) {
            if (!key.startsWith(JetStreamConstants.NATS_META_KEY_PREFIX)) {
                user2++;
            }
        }

        // 3. just make sure m2 didn't have more keys
        return user1 == user2;
    }
}
