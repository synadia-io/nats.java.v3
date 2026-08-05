package io.synadia.client.utils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Internal utility methods for the JetStream API implementation.
 */
@NullMarked
public abstract class JetStreamApiUtils {

    private JetStreamApiUtils() {}  /* ensures cannot be constructed */

    /**
     * Constant used to unset a Duration setting in the builder
     */
    public static final @Nullable Duration DURATION_UNSET = null;

    /**
     * Constant used to unset a long setting in the builder
     */
    public static final long UNSET = -1;

    /**
     * Constant used to unset a long that represents an unsigned long setting in the builder
     */
    public static final long ULONG_UNSET = 0;

    /**
     * Turn a builder's boxed long into the primitive the configuration holds, treating null and
     * anything under the minimum as not set.
     * @param l the value from the builder, may be null
     * @param min the smallest value that counts as set
     * @return the value, or {@link #UNSET}
     */
    public static long normalizeLong(@Nullable Long l, long min) {
        return l == null || l < min ? UNSET : l;
    }

    /**
     * Turn a builder's boxed integer into the primitive the configuration holds, treating null and
     * anything under the minimum as not set.
     * @param i the value from the builder, may be null
     * @param min the smallest value that counts as set
     * @return the value, or {@link #UNSET}
     */
    public static int normalizeInt(@Nullable Integer i, int min) {
        return i == null || i < min ? (int) UNSET : i;
    }

    /**
     * Turn a builder's boxed long that represents an unsigned long into the primitive the
     * configuration holds. Zero and below are not set, since an unsigned value starts at 1.
     * @param u the value from the builder, may be null
     * @return the value, or {@link #ULONG_UNSET}
     */
    public static long normalizeULong(@Nullable Long u) {
        return u == null || u <= ULONG_UNSET ? ULONG_UNSET : u;
    }

    /**
     * Resolve a duration against a default, treating null and any duration of zero or less as not set.
     * @param d the value from the builder, may be null
     * @param dftl the value to use when d is not set, itself allowed to be null
     * @return the duration, or the default
     */
    public static @Nullable Duration normalizeDuration(@Nullable Duration d, @Nullable Duration dftl) {
        return d == null ? dftl : d.toNanos() <= 0 ? dftl : d;
    }

    /**
     * Resolve a duration expressed in milliseconds against a default, treating null and any value
     * of zero or less as not set.
     * @param millis the value from the builder in milliseconds, may be null
     * @param dftl the value to use when millis is not set, itself allowed to be null
     * @return the duration, or the default
     */
    public static @Nullable Duration normalizeDuration(@Nullable Long millis, @Nullable Duration dftl) {
        return millis == null || millis <= 0 ? dftl : Duration.ofMillis(millis);
    }

    /**
     * Generate a random consumer name.
     * @return the generated consumer name
     */
    public static String generateConsumerName() {
        return ApiUtils.randomString();
    }

    /**
     * Generate a consumer name using the given prefix. If the prefix is null,
     * a fully random name is returned; otherwise the result is {@code prefix-<random>}.
     * @param prefix the prefix to prepend, or null for a fully random name
     * @return the generated consumer name
     */
    public static String generateConsumerName(@Nullable String prefix) {
        return prefix == null ? ApiUtils.randomString() : prefix + "-" + ApiUtils.randomString();
    }

    /**
     * Replace the contents of {@code target} with the items from {@code source}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Items must be non-null; duplicates (per {@link List#contains})
     * are not added.
     * @param target the list to populate; must not be null
     * @param source the items to copy in, or null to clear the target
     * @param <T> the element type
     */
    public static <T> void replaceAll(List<T> target, @Nullable Collection<T> source) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                if (!target.contains(item)) {
                    target.add(item);
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from the {@code source} array.
     * The target is always cleared first. Items must be non-null; duplicates (per
     * {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to copy in
     * @param <T> the element type
     */
    public static <T> void replaceAll(List<T> target, T[] source) {
        target.clear();
        for (T item : source) {
            if (!target.contains(item)) {
                target.add(item);
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from {@code source},
     * converting each source element via {@code converter}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Items must be non-null; converted duplicates (per
     * {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to convert and copy in, or null to clear the target
     * @param converter the function used to convert each source item to a target item
     * @param <T> the source element type
     * @param <R> the target element type
     */
    public static <T, R> void replaceAll(List<R> target, @Nullable Collection<T> source, Function<T, R> converter) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                R converted = converter.apply(item);
                if (!target.contains(converted)) {
                    target.add(converted);
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from the {@code source} array,
     * converting each source element via {@code converter}.
     * The target is always cleared first. Items must be non-null; converted duplicates
     * (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to convert and copy in
     * @param converter the function used to convert each source item to a target item
     * @param <T> the source element type
     * @param <R> the target element type
     */
    public static <T, R> void replaceAll(List<R> target, T[] source, Function<T, R> converter) {
        target.clear();
        for (T item : source) {
            R converted = converter.apply(item);
            if (!target.contains(converted)) {
                target.add(converted);
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the entries from {@code source}.
     * The target is always cleared first. If {@code source} is null or empty, the target is
     * left empty; otherwise all entries from the source are copied in.
     * @param target the map to populate; must not be null
     * @param source the entries to copy in, or null to clear the target
     * @param <K> the key type
     * @param <V> the value type
     */
    public static <K, V> void replaceAll(Map<K, V> target, @Nullable Map<K, V> source) {
        target.clear();
        if (source != null && !source.isEmpty()) {
            target.putAll(source);
        }
    }

    /**
     * Replace the contents of {@code target} with the strings from {@code source}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Strings must be non-null; empty strings are skipped, and
     * duplicates (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     */
    public static void replaceAllStrings(List<String> target, @Nullable Collection<String> source) {
        replaceAllStrings(target, source, s -> s);
    }

    /**
     * Replace the contents of {@code target} with the strings from the {@code source} array.
     * The target is always cleared first. Strings must be non-null; empty strings
     * are skipped, and duplicates (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in
     */
    public static void replaceAllStrings(List<String> target, String[] source) {
        replaceAllStrings(target, source, s -> s);
    }

    /**
     * Replace the contents of {@code target} with the strings from {@code source},
     * passing each non-empty entry through {@code validator}. The validator may
     * normalize the string or throw {@link IllegalArgumentException} on invalid
     * input; its return value is what gets added.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Strings must be non-null; empty strings are skipped (the validator
     * is not called for them). Duplicates (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     * @param validator the function applied to each non-empty string; may throw IllegalArgumentException
     */
    public static void replaceAllStrings(List<String> target, @Nullable Collection<String> source, Function<String, String> validator) {
        target.clear();
        if (source != null) {
            for (String item : source) {
                if (!item.isEmpty()) {
                    String validated = validator.apply(item);
                    if (!target.contains(validated)) {
                        target.add(validated);
                    }
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the strings from the {@code source} array,
     * passing each non-empty entry through {@code validator}. The validator may
     * normalize the string or throw {@link IllegalArgumentException} on invalid
     * input; its return value is what gets added.
     * The target is always cleared first. Strings must be non-null; empty strings
     * are skipped (the validator is not called for them). Duplicates (per
     * {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in
     * @param validator the function applied to each non-empty string; may throw IllegalArgumentException
     */
    public static void replaceAllStrings(List<String> target, String[] source, Function<String, String> validator) {
        target.clear();
        for (String item : source) {
            if (!item.isEmpty()) {
                String validated = validator.apply(item);
                if (!target.contains(validated)) {
                    target.add(validated);
                }
            }
        }
    }
}
