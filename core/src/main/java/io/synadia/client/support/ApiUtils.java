package io.synadia.client.support;

import io.nats.json.JsonValue;
import io.nats.json.JsonValueUtils;
import io.nats.json.LazyJsonValue;
import io.nats.json.MapBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

import static io.synadia.client.support.ApiConstants.NAME;
import static io.synadia.client.support.NatsConstants.UNDEFINED;

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
     * Constant used to as a standard minimum value
     */
    public static final int STANDARD_MIN = 0;

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

    public static long normalizeLong(long l, long min) {
        return l < min ? UNSET : l;
    }

    public static long normalizeLong(long l) {
        return l >= UNSET ? l : UNSET;
    }

    public static long normalizeUlong(long u) {
        return u >= ULONG_UNSET ? u : ULONG_UNSET;
    }

    public static Duration normalizeDuration(Duration d) {
        return normalizeDuration(d, Duration.ZERO);
    }

    public static Duration normalizeDuration(long millis) {
        return normalizeDuration(millis, Duration.ZERO);
    }

    public static Duration normalizeDuration(Duration d, Duration dftl) {
        return d == null ? dftl : d.toNanos() <= 0 ? null : d;
    }

    public static Duration normalizeDuration(long millis, Duration dftl) {
        return millis <= 0 ? dftl : Duration.ofMillis(millis);
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
}
