package io.synadia.client.impl;

import org.junit.jupiter.api.Test;

import java.util.*;

import static io.synadia.client.impl.JetStreamApiUtils.*;
import static org.junit.jupiter.api.Assertions.*;

public class JetStreamApiUtilsTests {

    // ----------------------------------------------------------------------------------------------------
    // generateConsumerName
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void generateConsumerName_noArg() {
        String a = generateConsumerName();
        String b = generateConsumerName();
        assertNotNull(a);
        assertFalse(a.isEmpty());
        assertNotEquals(a, b);
        assertFalse(a.contains("-"));  // pure NUID, no prefix
    }

    @Test
    public void generateConsumerName_nullPrefixActsLikeNoArg() {
        String name = generateConsumerName(null);
        assertNotNull(name);
        assertFalse(name.isEmpty());
        assertFalse(name.contains("-"));
    }

    @Test
    public void generateConsumerName_withPrefix() {
        String name = generateConsumerName("pfx");
        assertTrue(name.startsWith("pfx-"));
        assertTrue(name.length() > "pfx-".length());
        // distinct on repeated calls
        assertNotEquals(name, generateConsumerName("pfx"));
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAll(List, Collection)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAll_collection_nullClearsTarget() {
        List<String> target = new ArrayList<>(Arrays.asList("old1", "old2"));
        replaceAll(target, (List<String>) null);
        assertTrue(target.isEmpty());
    }

    @Test
    public void replaceAll_collection_copiesItemsAndDeduplicates() {
        List<String> target = new ArrayList<>(List.of("seed"));
        replaceAll(target, Arrays.asList("a", "b", "a", "c"));
        assertEquals(Arrays.asList("a", "b", "c"), target);
    }

    @Test
    public void replaceAll_collection_emptySourceClears() {
        List<String> target = new ArrayList<>(List.of("old"));
        replaceAll(target, new ArrayList<>());
        assertTrue(target.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAll(List, T[])
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAll_array_copiesAndDeduplicates() {
        List<String> target = new ArrayList<>(List.of("seed"));
        replaceAll(target, new String[]{"a", "b", "a", "c"});
        assertEquals(Arrays.asList("a", "b", "c"), target);
    }

    @Test
    public void replaceAll_array_emptyClears() {
        List<String> target = new ArrayList<>(List.of("old"));
        replaceAll(target, new String[0]);
        assertTrue(target.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAll(List, Collection, Function)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAll_collectionWithConverter_appliesConverter() {
        List<String> target = new ArrayList<>();
        replaceAll(target, Arrays.asList(1, 2, 3), i -> "n" + i);
        assertEquals(Arrays.asList("n1", "n2", "n3"), target);
    }

    @Test
    public void replaceAll_collectionWithConverter_dedupAfterConversion() {
        // distinct source values that all converge to the same converted value -> only one entry kept
        List<String> target = new ArrayList<>();
        replaceAll(target, Arrays.asList(1, 2, 3), i -> "same");
        assertEquals(1, target.size());
        assertEquals("same", target.get(0));
    }

    @Test
    public void replaceAll_collectionWithConverter_nullClears() {
        List<String> target = new ArrayList<>(List.of("old"));
        replaceAll(target, (List<Integer>) null, i -> "n" + i);
        assertTrue(target.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAll(List, T[], Function)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAll_arrayWithConverter_appliesConverter() {
        List<String> target = new ArrayList<>();
        replaceAll(target, new Integer[]{1, 2, 3}, i -> "n" + i);
        assertEquals(Arrays.asList("n1", "n2", "n3"), target);
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAll(Map, Map)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAll_map_nullClears() {
        Map<String, String> target = new HashMap<>();
        target.put("k", "v");
        replaceAll(target, (Map<String, String>) null);
        assertTrue(target.isEmpty());
    }

    @Test
    public void replaceAll_map_emptyClears() {
        Map<String, String> target = new HashMap<>();
        target.put("k", "v");
        replaceAll(target, new HashMap<>());
        assertTrue(target.isEmpty());
    }

    @Test
    public void replaceAll_map_copiesAllEntries() {
        Map<String, String> target = new HashMap<>();
        target.put("old", "x");

        Map<String, String> source = new HashMap<>();
        source.put("a", "1");
        source.put("b", "2");

        replaceAll(target, source);
        assertEquals(2, target.size());
        assertEquals("1", target.get("a"));
        assertEquals("2", target.get("b"));
        assertFalse(target.containsKey("old"));
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAllStrings(List, Collection)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAllStrings_collection_skipsEmptyAndDedups() {
        List<String> target = new ArrayList<>(List.of("seed"));
        replaceAllStrings(target, Arrays.asList("a", "", "b", "a", "c"));
        assertEquals(Arrays.asList("a", "b", "c"), target);
    }

    @Test
    public void replaceAllStrings_collection_nullClears() {
        List<String> target = new ArrayList<>(List.of("old"));
        replaceAllStrings(target, (List<String>) null);
        assertTrue(target.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAllStrings(List, String[])
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAllStrings_array_skipsEmptyAndDedups() {
        List<String> target = new ArrayList<>(List.of("seed"));
        replaceAllStrings(target, new String[]{"a", "", "b", "a", "c"});
        assertEquals(Arrays.asList("a", "b", "c"), target);
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAllStrings(List, Collection, Function)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAllStrings_collectionWithValidator_appliesValidator() {
        List<String> target = new ArrayList<>();
        replaceAllStrings(target, Arrays.asList("a", "b"), String::toUpperCase);
        assertEquals(Arrays.asList("A", "B"), target);
    }

    @Test
    public void replaceAllStrings_collectionWithValidator_skipsEmpty_doesNotCallValidator() {
        // validator would NPE / count calls — verify it's only invoked for non-empty entries
        List<String> seen = new ArrayList<>();
        List<String> target = new ArrayList<>();
        replaceAllStrings(target, Arrays.asList("a", "", "b"), s -> { seen.add(s); return s; });
        assertEquals(Arrays.asList("a", "b"), seen);
        assertEquals(Arrays.asList("a", "b"), target);
    }

    @Test
    public void replaceAllStrings_collectionWithValidator_dedupAfterValidation() {
        List<String> target = new ArrayList<>();
        replaceAllStrings(target, Arrays.asList("a", "A"), String::toLowerCase);
        assertEquals(1, target.size());
        assertEquals("a", target.get(0));
    }

    @Test
    public void replaceAllStrings_collectionWithValidator_validatorThrowingPropagates() {
        List<String> target = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () ->
            replaceAllStrings(target, Arrays.asList("ok", "bad"), s -> {
                if ("bad".equals(s)) throw new IllegalArgumentException("nope");
                return s;
            }));
    }

    @Test
    public void replaceAllStrings_collectionWithValidator_nullClears() {
        List<String> target = new ArrayList<>(List.of("old"));
        replaceAllStrings(target, (List<String>) null, s -> s);
        assertTrue(target.isEmpty());
    }

    // ----------------------------------------------------------------------------------------------------
    // replaceAllStrings(List, String[], Function)
    // ----------------------------------------------------------------------------------------------------

    @Test
    public void replaceAllStrings_arrayWithValidator_appliesValidator() {
        List<String> target = new ArrayList<>();
        replaceAllStrings(target, new String[]{"a", "", "b"}, String::toUpperCase);
        assertEquals(Arrays.asList("A", "B"), target);
    }
}
