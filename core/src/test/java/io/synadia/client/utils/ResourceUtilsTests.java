package io.synadia.client.utils;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

import static io.synadia.client.utils.ResourceUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the missing-resource behavior of {@link ResourceUtils}. A resource that is not on the classpath must
 * name itself in the failure - {@code getResourceAsStream} returns null for a missing resource, and the
 * unguarded version of this code failed with a bare NullPointerException that said nothing about which file
 * was absent. That cost weeks of CI archaeology on the V2 side, so the behavior is asserted rather than assumed.
 */
public class ResourceUtilsTests {

    private static final String MISSING = "ThisResourceDoesNotExist.json";
    private static final String PRESENT = "ServerInfoJson.txt";

    @Test
    public void testMissingResourceIdentifiesTheFile() {
        assertMissing(assertThrows(RuntimeException.class, () -> dataAsString(MISSING)));
        assertMissing(assertThrows(RuntimeException.class, () -> dataAsLines(MISSING)));
        assertMissing(assertThrows(RuntimeException.class, () -> dataAsInputStream(MISSING)));
    }

    // open() throws inside a try-with-resources whose catch(IOException) wraps it, so the
    // FileNotFoundException is the cause, not the exception the caller sees.
    private void assertMissing(RuntimeException e) {
        Throwable cause = e.getCause();
        assertInstanceOf(FileNotFoundException.class, cause);
        assertTrue(cause.getMessage().contains(MISSING),
            "the failure must name the missing resource, got: " + cause.getMessage());
    }

    @Test
    public void testPresentResourceStillReads() throws Exception {
        String asString = dataAsString(PRESENT);
        assertNotNull(asString);
        assertFalse(asString.isEmpty());

        List<String> asLines = dataAsLines(PRESENT);
        assertNotNull(asLines);
        assertFalse(asLines.isEmpty());

        try (InputStream in = dataAsInputStream(PRESENT)) {
            assertNotNull(in);
            assertTrue(in.read() != -1);
        }
    }
}
