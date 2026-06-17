package io.synadia.client.utils;

import io.nats.json.*;
import io.synadia.client.NUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;

import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.NatsConstants.UNDEFINED;

public abstract class ApiUtils {

    protected ApiUtils() {}  /* ensures cannot be constructed */

    /**
     * Constant used to unset a Duration setting in the builder
     */
    public static final Duration DURATION_UNSET = null;

    /**
     * Constant used to unset a long setting in the builder
     */
    public static final long UNSET = -1;

    /**
     * Constant used to unset a long that represents an unsigned long setting in the builder
     */
    public static final long ULONG_UNSET = 0;

    /**
     * Get a random string.
     * @return the random string
     */
    public static String randomString() {
        return NUID.nextGlobal();
    }

    public static <T> List<T> orEmpty(List<T> l) {
        return l == null ? Collections.emptyList() : l;
    }

    @NonNull
    public static <T> List<T> mapToList(@Nullable LazyJsonValue v, @NonNull Function<LazyJsonValue, T> mapper) {
        if (v == null || v.getArray() == null) {
            return Collections.emptyList();
        }
        return v.getArray().stream().map(mapper).toList();
    }

    public static <T> List<T> copyOrNull(List<T> l) {
        return l == null ? null : new ArrayList<>(l);
    }

    public static <T> List<T> copyOrEmpty(List<T> l) {
        return l == null ? Collections.emptyList() : new ArrayList<>(l);
    }

    public static <K, V> Map<K, V> copyOrNull(Map<K, V> m) {
        return m == null ? null : new HashMap<>(m);
    }

    public static <K, V> Map<K, V> copyOrEmpty(Map<K, V> m) {
        return m == null ? Collections.emptyMap() : new HashMap<>(m);
    }

    public static long normalizeLong(Long l, long min) {
        return l == null || l < min ? UNSET : l;
    }

    public static int normalizeInt(Integer i, int min) {
        return i == null || i < min ? (int) UNSET : i;
    }

    public static long normalizeULong(Long u) {
        return u == null || u <= ULONG_UNSET ? ULONG_UNSET : u;
    }

    public static Duration normalizeDuration(Duration d, Duration dftl) {
        return d == null ? dftl : d.toNanos() <= 0 ? dftl : d;
    }

    public static Duration normalizeDuration(Long millis, Duration dftl) {
        return millis == null || millis <= 0 ? dftl : Duration.ofMillis(millis);
    }

    private static JsonValue JV_NAME_UNDEFINED;

    public static JsonValue jvNameUndefined() {
        if (JV_NAME_UNDEFINED == null) {
            MapBuilder b = new MapBuilder();
            b.put(NAME, UNDEFINED);
            JV_NAME_UNDEFINED = b.jv;
        }
        return JV_NAME_UNDEFINED;
    }

    @NonNull
    public static String readString(@NonNull JsonValue jv, @NonNull String key, @NonNull String dflt) {
        String s = JsonValueUtils.readString(jv, key);
        return s == null ? dflt : s;
    }

    @NonNull
    public static String readString(@NonNull LazyJsonValue jv, @NonNull String key, @NonNull String dflt) {
        String s = LazyJsonValueUtils.readString(jv, key);
        return s == null ? dflt : s;
    }

    public static boolean readBoolean(@NonNull JsonValue jv, @NonNull String key, boolean dflt) {
        Boolean b = JsonValueUtils.readBoolean(jv, key);
        return b == null ? dflt : b;
    }

    public static int readInteger(@NonNull JsonValue jv, @NonNull String key, int dflt) {
        Integer i = JsonValueUtils.readInteger(jv, key);
        return i == null ? dflt : i;
    }

    public static long readLong(@NonNull JsonValue jv, @NonNull String key, long dflt) {
        Long l = JsonValueUtils.readLong(jv, key);
        return l == null ? dflt : l;
    }

    // ---------------------------------------------------------------------------
    // Optimistic required-field readers. See REQUIRED_FIELDS_POLICY.md.
    // Pattern: read the schema-required scalar; return a sensible-empty sentinel
    // when the JSON is missing rather than throw. "" / -1 / Duration.ZERO /
    // DateTimeUtils.DEFAULT_TIME are the per-type defaults.
    // ---------------------------------------------------------------------------

    /**
     * Read a String, returning {@code ""} when absent. Use for schema-required strings.
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the string value, or {@code ""} if absent
     */
    @NonNull
    public static String readStringOrEmpty(@NonNull LazyJsonValue ljv, @NonNull String key) {
        String s = LazyJsonValueUtils.readString(ljv, key);
        return s == null ? "" : s;
    }

    /**
     * Read a long, returning {@code -1} when absent. {@code -1} is the codebase-wide
     * "missing required numeric" sentinel (matches {@code Error.NOT_SET},
     * {@code PublishAck.seq}, {@code StreamConfiguration} max-limits).
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the long value, or {@code -1} if absent
     */
    public static long readLongOrMinusOne(@NonNull LazyJsonValue ljv, @NonNull String key) {
        return LazyJsonValueUtils.readLong(ljv, key, -1L);
    }

    /**
     * Read a long, returning {@code 0} when absent. {@code 0} is the codebase-wide
     * "missing required numeric" sentinel for an unsigned 64 bit value
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the long value, or {@code -1} if absent
     */
    public static long readLongOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
        return LazyJsonValueUtils.readLong(ljv, key, 0L);
    }

    /**
     * Read an <b>unsigned 64-bit</b> value into a {@code long}, returning {@code 0} when absent.
     * Unlike {@link #readLongOrZero}, this reads the full {@code 0 .. 2^64-1} range: a value above
     * {@code Long.MAX_VALUE} (parsed as a {@code BigInteger}) is returned as its low 64 bits, i.e.
     * the two's-complement bit pattern, which reads as a <i>negative</i> {@code long}. For such
     * values interpret the result with {@link Long#toUnsignedString(long)} /
     * {@link Long#compareUnsigned(long, long)}, or use {@link #readUnsignedBigIntegerOrZero} for a
     * non-negative value. (For NATS these fields are always far below {@code Long.MAX_VALUE}.)
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the unsigned long bit pattern, or {@code 0} if absent
     */
    public static long readUnsignedLongOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
        return LazyJsonValueUtils.readUnsignedLong(ljv, key, 0L);
    }

    /**
     * Read an <b>unsigned 64-bit</b> value as a non-negative {@link BigInteger}, returning
     * {@link BigInteger#ZERO} when absent. This is the sign-unambiguous companion to
     * {@link #readUnsignedLongOrZero} for values that could exceed {@code Long.MAX_VALUE}.
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the value as a non-negative BigInteger, or {@link BigInteger#ZERO} if absent
     */
    @NonNull
    public static BigInteger readUnsignedBigIntegerOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
        BigInteger bi = LazyJsonValueUtils.readUnsignedBigInteger(ljv, key, BigInteger.ZERO);
        return bi == null ? BigInteger.ZERO : bi;
    }

    /**
     * Read an int, returning {@code -1} when absent. See {@link #readLongOrMinusOne}.
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the int value, or {@code -1} if absent
     */
    public static int readIntegerOrMinusOne(@NonNull LazyJsonValue ljv, @NonNull String key) {
        return LazyJsonValueUtils.readInteger(ljv, key, -1);
    }

    /**
     * Read a nanos-encoded Duration, returning {@link Duration#ZERO} when absent or
     * negative.
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the Duration, or {@code Duration.ZERO} if absent / negative
     */
    @NonNull
    public static Duration readDurationOrZero(@NonNull LazyJsonValue ljv, @NonNull String key) {
        Duration d = LazyJsonValueUtils.readNanosAsDuration(ljv, key);
        return d == null || d.isNegative() ? Duration.ZERO : d;
    }

    /**
     * Read a date, returning {@link DateTimeUtils#DEFAULT_TIME} when absent.
     * {@code DEFAULT_TIME} is the codebase-wide "absent timestamp" sentinel and is
     * skipped by {@code JsonWriteUtils} on emit, preserving round-trip.
     * @param ljv the value to read from
     * @param key the JSON key
     * @return the date, or {@code DateTimeUtils.DEFAULT_TIME} if absent
     */
    @NonNull
    public static ZonedDateTime readDateOrDefault(@NonNull LazyJsonValue ljv, @NonNull String key) {
        ZonedDateTime zdt = LazyJsonValueUtils.readDate(ljv, key);
        return zdt == null ? DateTimeUtils.DEFAULT_TIME : zdt;
    }
}
