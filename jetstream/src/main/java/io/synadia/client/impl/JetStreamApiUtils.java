package io.synadia.client.impl;

import io.synadia.client.testutils.ApiUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Internal utility methods for the JetStream API implementation.
 */
public abstract class JetStreamApiUtils extends ApiUtils {

    private JetStreamApiUtils() {}  /* ensures cannot be constructed */

    /**
     * Generate a random consumer name.
     * @return the generated consumer name
     */
    public static String generateConsumerName() {
        return randomString();
    }

    /**
     * Generate a consumer name using the given prefix. If the prefix is null,
     * a fully random name is returned; otherwise the result is {@code prefix-<random>}.
     * @param prefix the prefix to prepend, or null for a fully random name
     * @return the generated consumer name
     */
    public static String generateConsumerName(String prefix) {
        return prefix == null ? randomString() : prefix + "-" + randomString();
    }

    /**
     * Replace the contents of {@code target} with the items from {@code source}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null items in the source are skipped, and duplicates (per
     * {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to copy in, or null to clear the target
     * @param <T> the element type
     */
    public static <T> void replaceAll(List<T> target, @Nullable Collection<@Nullable T> source) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                if (item != null && !target.contains(item)) {
                    target.add(item);
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from the {@code source} array.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null items in the source are skipped, and duplicates (per
     * {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to copy in, or null to clear the target
     * @param <T> the element type
     */
    public static <T> void replaceAll(List<T> target, @Nullable T @Nullable[] source) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                if (item != null && !target.contains(item)) {
                    target.add(item);
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from {@code source},
     * converting each non-null source element via {@code converter}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null items in the source are skipped, and converted duplicates
     * (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to convert and copy in, or null to clear the target
     * @param converter the function used to convert each non-null source item to a target item
     * @param <T> the source element type
     * @param <R> the target element type
     */
    public static <T, R> void replaceAll(List<R> target, @Nullable Collection<@Nullable T> source, Function<@NonNull T, R> converter) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                if (item != null) {
                    R converted = converter.apply(item);
                    if (!target.contains(converted)) {
                        target.add(converted);
                    }
                }
            }
        }
    }

    /**
     * Replace the contents of {@code target} with the items from the {@code source} array,
     * converting each non-null source element via {@code converter}.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null items in the source are skipped, and converted duplicates
     * (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the items to convert and copy in, or null to clear the target
     * @param converter the function used to convert each non-null source item to a target item
     * @param <T> the source element type
     * @param <R> the target element type
     */
    public static <T, R> void replaceAll(List<R> target, @Nullable T @Nullable[] source, Function<@NonNull T, R> converter) {
        target.clear();
        if (source != null) {
            for (T item : source) {
                if (item != null) {
                    R converted = converter.apply(item);
                    if (!target.contains(converted)) {
                        target.add(converted);
                    }
                }
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
     * left empty. Null and empty strings in the source are skipped, and duplicates
     * (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     */
    public static void replaceAllStrings(List<String> target, @Nullable Collection<@Nullable String> source) {
        replaceAllStrings(target, source, s -> s);
    }

    /**
     * Replace the contents of {@code target} with the strings from the {@code source} array.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null and empty strings in the source are skipped, and duplicates
     * (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     */
    public static void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source) {
        replaceAllStrings(target, source, s -> s);
    }

    /**
     * Replace the contents of {@code target} with the strings from {@code source},
     * passing each non-null/non-empty entry through {@code validator}. The validator
     * may normalize the string or throw {@link IllegalArgumentException} on invalid
     * input; its return value is what gets added.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null and empty strings in the source are skipped (the validator is
     * not called for them). Duplicates (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     * @param validator the function applied to each non-null/non-empty string; may throw IllegalArgumentException
     */
    public static void replaceAllStrings(List<String> target, @Nullable Collection<@Nullable String> source, Function<String, String> validator) {
        target.clear();
        if (source != null) {
            for (String item : source) {
                if (item != null && !item.isEmpty()) {
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
     * passing each non-null/non-empty entry through {@code validator}. The validator
     * may normalize the string or throw {@link IllegalArgumentException} on invalid
     * input; its return value is what gets added.
     * The target is always cleared first. If {@code source} is null, the target is
     * left empty. Null and empty strings in the source are skipped (the validator is
     * not called for them). Duplicates (per {@link List#contains}) are not added.
     * @param target the list to populate; must not be null
     * @param source the strings to copy in, or null to clear the target
     * @param validator the function applied to each non-null/non-empty string; may throw IllegalArgumentException
     */
    public static void replaceAllStrings(List<String> target, @Nullable String @Nullable[] source, Function<String, String> validator) {
        target.clear();
        if (source != null) {
            for (String item : source) {
                if (item != null && !item.isEmpty()) {
                    String validated = validator.apply(item);
                    if (!target.contains(validated)) {
                        target.add(validated);
                    }
                }
            }
        }
    }
}
