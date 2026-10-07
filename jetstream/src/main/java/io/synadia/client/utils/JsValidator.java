package io.synadia.client.utils;

import io.synadia.client.impl.JetStreamConstants;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.synadia.client.utils.NatsConstants.DOT;

/**
 * JetStream specific validation, extending the core {@link Validator Validator} with the naming and limit rules
 * for streams, consumers, key value keys and object store buckets. Each method returns the validated value so it
 * can be used inline, and throws {@link IllegalArgumentException} when the value is not acceptable.
 */
@SuppressWarnings("UnusedReturnValue")
public abstract class JsValidator extends Validator {

    protected JsValidator() {} /* ensures cannot be constructed */

    /**
     * Validate a stream name.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateStreamName(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Stream", required);
    }

    /**
     * Validate a durable name.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateDurable(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Durable", required);
    }

    /**
     * Validate a consumer name.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateConsumerName(String s, boolean required) {
        return validatePrintableExceptWildDotGtSlashes(s, "Name", required);
    }

    /**
     * Validate a JetStream prefix or domain.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
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

    /**
     * Require two values to match when both are supplied, returning whichever was given.
     * Both values being individually valid, a conflict is the object's state, not a bad argument.
     * @param s1 the first value
     * @param s2 the second value
     * @param label1 the label for the first value, used in the message
     * @param label2 the label for the second value, used in the message
     * @return the supplied value, or null if neither was supplied
     * @throws IllegalStateException if both are supplied and they do not match
     */
    public static String validateMustMatchIfBothSupplied(String s1, String s2, String label1, String label2) {
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

        throw new IllegalStateException(label1 + " must match " + label2 + " if both are supplied.");
    }

    /**
     * Validate a key value or object store bucket name.
     * @param s the value to validate
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static String validateBucketName(String s, boolean required) {
        return validateIsRestrictedTerm(s, "Bucket Name", required);
    }

    /**
     * Validate a bucket's max size in bytes.
     * @param max the value to validate
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static long validateMaxBucketBytes(long max) {
        return validateGtZeroOrMinus1(max, "Max Bucket Bytes"); // max bucket bytes is a kv alias to max bytes
    }

    /**
     * Validate a replica count.
     * @param replicas the value to validate
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static int validateNumberOfReplicas(int replicas) {
        if (replicas < 1 || replicas > 5) {
            throw new IllegalArgumentException("Replicas must be from 1 to 5 inclusive.");
        }
        return replicas;
    }

    /**
     * Validate an optional duration that may not be negative.
     * @param d the duration, may be null
     * @param ifNull returned when the duration is null
     * @return the duration, or ifNull
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateDurationNotRequiredGtOrEqZero(Duration d, Duration ifNull) {
        if (d == null) {
            return ifNull;
        }
        if (d.isNegative()) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        return d;
    }

    /**
     * Validate an optional duration in millis that may not be negative.
     * @param millis the duration in milliseconds
     * @return the duration
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateDurationNotRequiredGtOrEqZero(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        return Duration.ofMillis(millis);
    }

    /**
     * Validate an optional duration in millis that may not be negative.
     * @param millis the duration in milliseconds
     * @param ifZero returned when millis is zero
     * @return the duration, or ifZero
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateDurationNotRequiredGtOrEqZero(long millis, Duration ifZero) {
        if (millis < 0) {
            throw new IllegalArgumentException("Duration must be greater than or equal to 0.");
        }
        if (millis == 0) {
            return ifZero;
        }
        return Duration.ofMillis(millis);
    }

    /**
     * Validate an optional duration against a minimum in seconds.
     *
     * @param d              the duration, may be null
     * @param minSeconds     the minimum in seconds
     * @param dIfInputIsNull returned when the duration is null
     * @param label          name used in the error message
     * @return the duration, or ifNull
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateDurationNotRequiredGtOrEqSeconds(Duration d, long minSeconds, Duration dIfInputIsNull, String label) {
        return d == null ? dIfInputIsNull : validateMillisGtOrEqSeconds(d.toMillis(), minSeconds, label);
    }

    /**
     * Validate a duration in millis against a minimum in seconds.
     *
     * @param inputMillis the duration in milliseconds
     * @param minSeconds  the minimum in seconds
     * @param label       name used in the error message
     * @return the duration
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    public static Duration validateMillisGtOrEqSeconds(long inputMillis, long minSeconds, String label) {
        if (inputMillis < (minSeconds * 1000)) {
            throw new IllegalArgumentException(label + " must be greater than or equal to " + minSeconds + " second(s).");
        }
        return Duration.ofMillis(inputMillis);
    }

    // this is a special case map where the meta has both user and nats headers like
    // _nats.req.level=0, _nats.ver=2.12.0-preview.2, _nats.level=2
    // in this case we only want to compare the user keys
    /**
     * Compare two metadata maps, ignoring the entries the server adds itself.
     * @param m1 the first map, may be null
     * @param m2 the second map, may be null
     * @return true if the maps are equivalent
     */
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
