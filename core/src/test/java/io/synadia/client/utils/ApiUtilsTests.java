package io.synadia.client.utils;

import io.nats.json.JsonValue;
import io.nats.json.LazyJsonParser;
import io.nats.json.LazyJsonValue;
import io.nats.json.MapBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static io.synadia.client.utils.ApiConstants.NAME;
import static io.synadia.client.utils.ApiUtils.*;
import static io.synadia.client.utils.NatsConstants.UNDEFINED;
import static org.junit.jupiter.api.Assertions.*;

public class ApiUtilsTests {

    @Test
    public void randomStringReturnsDistinctValues() {
        String a = randomString();
        String b = randomString();
        assertNotNull(a);
        assertFalse(a.isEmpty());
        assertNotEquals(a, b);
    }

    @Test
    public void orEmpty_nullReturnsEmpty_nonNullReturnsSame() {
        assertTrue(orEmpty((List<String>) null).isEmpty());
        List<String> in = Arrays.asList("a", "b");
        assertSame(in, orEmpty(in));
    }

    @Test
    public void mapToList_nullOrEmptyArray() {
        // null LazyJsonValue
        assertTrue(mapToList(null, lv -> "x").isEmpty());

        // LazyJsonValue that isn't an array (a map)
        LazyJsonValue mapLjv = LazyJsonParser.parseUnchecked("{\"a\":1}");
        assertTrue(mapToList(mapLjv, lv -> "x").isEmpty());
    }

    @Test
    public void mapToList_appliesMapperOverArray() {
        LazyJsonValue arrayLjv = LazyJsonParser.parseUnchecked("[\"a\",\"b\",\"c\"]");
        List<String> result = mapToList(arrayLjv, LazyJsonValue::getString);
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void copyOrNull_list() {
        //noinspection ConstantValue
        assertNull(copyOrNull((List<String>) null));

        List<String> in = Arrays.asList("a", "b");
        List<String> copy = copyOrNull(in);
        assertNotSame(in, copy);
        assertEquals(in, copy);
    }

    @Test
    public void copyOrEmpty_list() {
        List<String> empty = copyOrEmpty((List<String>) null);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        List<String> in = Arrays.asList("a", "b");
        List<String> copy = copyOrEmpty(in);
        assertNotSame(in, copy);
        assertEquals(in, copy);
    }

    @Test
    public void copyOrNull_map() {
        //noinspection ConstantValue
        assertNull(copyOrNull((Map<String, String>) null));

        Map<String, String> in = new HashMap<>();
        in.put("k", "v");
        Map<String, String> copy = copyOrNull(in);
        assertNotSame(in, copy);
        assertEquals(in, copy);
    }

    @Test
    public void copyOrEmpty_map() {
        Map<String, String> empty = copyOrEmpty((Map<String, String>) null);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());

        Map<String, String> in = new HashMap<>();
        in.put("k", "v");
        Map<String, String> copy = copyOrEmpty(in);
        assertNotSame(in, copy);
        assertEquals(in, copy);
    }

    @Test
    public void normalizeLong_withMin() {
        // null -> UNSET
        assertEquals(UNSET, normalizeLong(null, 0L));
        // below min -> UNSET
        assertEquals(UNSET, normalizeLong(-1L, 0L));
        assertEquals(UNSET, normalizeLong(0L, 1L));
        // at or above min -> kept
        assertEquals(0L, normalizeLong(0L, 0L));
        assertEquals(5L, normalizeLong(5L, 0L));
        assertEquals(1L, normalizeLong(1L, 1L));
    }

    @Test
    public void normalizeLong_singleArg() {
        // null -> UNSET
        assertEquals(UNSET, normalizeLong(null));
        // <= UNSET (-1) -> UNSET
        assertEquals(UNSET, normalizeLong(-2L));
        assertEquals(UNSET, normalizeLong(-1L));
        // > UNSET -> kept (including 0)
        assertEquals(0L, normalizeLong(0L));
        assertEquals(7L, normalizeLong(7L));
    }

    @Test
    public void normalizeULong_singleArg() {
        // null or <= 0 -> ULONG_UNSET (0)
        assertEquals(ULONG_UNSET, normalizeULong(null));
        assertEquals(ULONG_UNSET, normalizeULong(0L));
        assertEquals(ULONG_UNSET, normalizeULong(-5L));
        // > 0 kept
        assertEquals(1L, normalizeULong(1L));
        assertEquals(100L, normalizeULong(100L));
    }

    @Test
    public void normalizeDuration_durationOverload() {
        Duration dflt = Duration.ofSeconds(10);
        // null -> default
        assertEquals(dflt, normalizeDuration((Duration) null, dflt));
        //noinspection ConstantValue
        assertNull(normalizeDuration((Duration) null, null));
        // zero / negative -> null
        assertNull(normalizeDuration(Duration.ZERO, dflt));
        assertNull(normalizeDuration(Duration.ofMillis(-1), dflt));
        // positive -> kept
        Duration d = Duration.ofSeconds(5);
        assertEquals(d, normalizeDuration(d, dflt));
    }

    @Test
    public void normalizeDuration_millisOverload() {
        Duration dflt = Duration.ofSeconds(10);
        // null -> default
        assertEquals(dflt, normalizeDuration((Long) null, dflt));
        //noinspection ConstantValue
        assertNull(normalizeDuration((Long) null, null));
        // zero / negative -> default
        assertEquals(dflt, normalizeDuration(0L, dflt));
        assertEquals(dflt, normalizeDuration(-1L, dflt));
        assertNull(normalizeDuration(0L, null));
        // positive -> Duration.ofMillis
        assertEquals(Duration.ofMillis(250), normalizeDuration(250L, dflt));
    }

    @Test
    public void jvNameUndefined_returnsCachedInstance() {
        JsonValue first = jvNameUndefined();
        JsonValue second = jvNameUndefined();
        assertSame(first, second);
        assertNotNull(first.map);
        // Confirm content
        assertEquals(UNDEFINED, first.map.get(NAME).string);
    }

    @Test
    public void readString_jsonValue_presentAndMissing() {
        JsonValue jv = MapBuilder.instance().put("k", "v").jv;
        assertEquals("v", readString(jv, "k", "dflt"));
        assertEquals("dflt", readString(jv, "missing", "dflt"));
    }

    @Test
    public void readString_lazyJsonValue_presentAndMissing() {
        LazyJsonValue ljv = LazyJsonParser.parseUnchecked("{\"k\":\"v\"}");
        assertEquals("v", readString(ljv, "k", "dflt"));
        assertEquals("dflt", readString(ljv, "missing", "dflt"));
    }

    @Test
    public void readBoolean_presentAndMissing() {
        JsonValue jv = MapBuilder.instance().put("flag", true).jv;
        assertTrue(readBoolean(jv, "flag", false));
        assertFalse(readBoolean(jv, "missing", false));
        assertTrue(readBoolean(jv, "missing", true));
    }

    @Test
    public void readInteger_presentAndMissing() {
        JsonValue jv = MapBuilder.instance().put("n", 42).jv;
        assertEquals(42, readInteger(jv, "n", 0));
        assertEquals(7, readInteger(jv, "missing", 7));
    }

    @Test
    public void readLong_presentAndMissing() {
        JsonValue jv = MapBuilder.instance().put("n", 9_000_000_000L).jv;
        assertEquals(9_000_000_000L, readLong(jv, "n", 0L));
        assertEquals(123L, readLong(jv, "missing", 123L));
    }

    @Test
    public void constants() {
        assertNull(DURATION_UNSET);
        assertEquals(-1L, UNSET);
        assertEquals(0L, ULONG_UNSET);
        assertEquals(0, STANDARD_MIN);
    }

    @Test
    public void copyOrNull_listIsMutableCopy() {
        List<String> in = Arrays.asList("a", "b");
        List<String> copy = copyOrNull(in);
        assertNotNull(copy);
        // mutable
        copy.add("c");
        assertEquals(3, copy.size());
        // original untouched
        assertEquals(2, in.size());
    }

    @Test
    public void copyOrEmpty_emptyResultIsMutable_orImmutableEmpty() {
        // for null input, an immutable Collections.emptyList() is returned — not required to be mutable
        List<String> empty = copyOrEmpty((List<String>) null);
        assertTrue(empty.isEmpty());

        // for non-null input, the copy is mutable (backed by ArrayList)
        List<String> in = new ArrayList<>();
        in.add("a");
        List<String> copy = copyOrEmpty(in);
        copy.add("b");
        assertEquals(2, copy.size());
        assertEquals(1, in.size());
    }
}
