package io.synadia.client.utils;

import io.nats.json.*;
import io.synadia.client.Nats;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import static io.nats.json.DateTimeUtils.DEFAULT_TIME;
import static io.synadia.client.utils.ApiUtils.*;
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
    public void mapToList_nullOrEmptyArray() {
        // null LazyJsonValue
        assertTrue(mapToList(null, lv -> "x").isEmpty());

        // LazyJsonValue that isn't an array (a map)
        LazyJsonValue mapLjv = new LazyMapBuilder().put("a", 1).build();
        assertTrue(mapToList(mapLjv, lv -> "x").isEmpty());
    }

    @Test
    public void mapToList_appliesMapperOverArray() {
        LazyJsonValue arrayLjv = new LazyArrayBuilder().add("a").add("b").add("c").build();
        List<String> result = mapToList(arrayLjv, LazyJsonValue::getString);
        assertEquals(Arrays.asList("a", "b", "c"), result);
    }

    @Test
    public void readString_lazyJsonValue_presentAndMissing() {
        LazyJsonValue ljv = new LazyMapBuilder().put("k", "v").build();
        assertEquals("v", readString(ljv, "k", "dflt"));
        assertEquals("dflt", readString(ljv, "missing", "dflt"));
    }

    // ---------------------------------------------------------------------------
    // Optimistic required-field readers (used by the jetstream API objects).
    // ---------------------------------------------------------------------------

    @Test
    public void readDateOrDefault_presentAbsentAndDefault() {
        ZonedDateTime expected = DateTimeUtils.parseDateTime("2021-01-20T23:41:08.579Z");
        LazyJsonValue ljv = LazyJsonParser.parseUnchecked(
            "{\"d\":\"2021-01-20T23:41:08.579Z\"}");

        ZonedDateTime read = readDateOrDefault(ljv, "d");
        assertTrue(DateTimeUtils.equals(expected, read));

        // absent -> DEFAULT_TIME sentinel
        assertEquals(DEFAULT_TIME, readDateOrDefault(ljv, "missing"));
    }

    @Test
    public void readDurationOrZero_presentAbsentAndNegative() {
        // nanos-encoded duration
        LazyJsonValue ljv = LazyJsonParser.parseUnchecked(
            "{\"d\":1000000000,\"neg\":-5}");

        assertEquals(Duration.ofSeconds(1), readDurationOrZero(ljv, "d"));
        // absent -> Duration.ZERO
        assertEquals(Duration.ZERO, readDurationOrZero(ljv, "missing"));
        // negative -> Duration.ZERO
        assertEquals(Duration.ZERO, readDurationOrZero(ljv, "neg"));
    }

    @Test
    public void readIntegerOrMinusOne_presentAndAbsent() {
        LazyJsonValue ljv = new LazyMapBuilder().put("n", 42).build();
        assertEquals(42, readIntegerOrMinusOne(ljv, "n"));
        // absent -> -1
        assertEquals(-1, readIntegerOrMinusOne(ljv, "missing"));
    }

    @Test
    public void readLongOrMinusOne_presentAndAbsent() {
        LazyJsonValue ljv = new LazyMapBuilder().put("n", 9_000_000_000L).build();
        assertEquals(9_000_000_000L, readLongOrMinusOne(ljv, "n"));
        // absent -> -1
        assertEquals(-1L, readLongOrMinusOne(ljv, "missing"));
    }

    @Test
    public void readStringOrEmpty_presentAndAbsent() {
        LazyJsonValue ljv = new LazyMapBuilder().put("k", "v").build();
        assertEquals("v", readStringOrEmpty(ljv, "k"));
        // absent -> ""
        assertEquals("", readStringOrEmpty(ljv, "missing"));
    }

    @Test
    public void testLoadVersion() {
        assertEquals(Nats.CLIENT_VERSION, loadVersion(Nats.class, "core"));
        assertEquals("development", loadVersion(Nats.class, "no-such-project"));
    }
}
