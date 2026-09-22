package io.synadia.client.utils;

import org.junit.jupiter.api.Test;

import static io.synadia.client.utils.ClientError.*;
import static org.junit.jupiter.api.Assertions.*;

public class ClientErrorTests {

    private static final ClientError FIXED = new ClientError("TST", 1, "Fixed message.", KIND_ILLEGAL_STATE);
    private static final ClientError ONE = new ClientError("TST", 2, "One %s label.", KIND_ILLEGAL_ARGUMENT);
    private static final ClientError THREE = new ClientError("TST", 3, "a %s b %s c %s.", KIND_ILLEGAL_STATE);

    @Test
    public void testIdMessageAndLabelCount() {
        assertEquals("TST-1", FIXED.id());
        assertEquals("[TST-1] Fixed message.", FIXED.message());
        assertEquals(0, FIXED.labelCount());
        assertEquals(1, ONE.labelCount());
        assertEquals(3, THREE.labelCount());
        assertEquals("[TST-3] a %s b %s c %s.", THREE.message()); // the template, unfilled
        assertEquals(KIND_ILLEGAL_STATE, FIXED.getKind());
        assertEquals(KIND_ILLEGAL_ARGUMENT, ONE.getKind());
    }

    @Test
    public void testKindDecidesTheException() {
        assertInstanceOf(IllegalStateException.class, FIXED.instance());
        assertInstanceOf(IllegalArgumentException.class, ONE.instance("only"));
    }

    @Test
    public void testLabelsFillPlaceholders() {
        assertEquals("[TST-2] One only label.", ONE.instance("only").getMessage());
        assertEquals("[TST-3] a A b B c C.", THREE.instance("A", "B", "C").getMessage());
    }

    @Test
    public void testTooManyLabelsCombineIntoTheLast() {
        assertEquals("[TST-3] a A b B c C, D.", THREE.instance("A", "B", "C", "D").getMessage());
        assertEquals("[TST-3] a A b B c C, D, E.", THREE.instance("A", "B", "C", "D", "E").getMessage());
        assertEquals("[TST-2] One x, y label.", ONE.instance("x", "y").getMessage());
    }

    @Test
    public void testTooFewLabelsReadMissing() {
        assertEquals("[TST-3] a A b B c " + MISSING_LABEL + ".", THREE.instance("A", "B").getMessage());
        assertEquals("[TST-3] a " + MISSING_LABEL + " b " + MISSING_LABEL + " c " + MISSING_LABEL + ".", THREE.instance().getMessage());
    }

    @Test
    public void testFixedMessageIgnoresLabels() {
        assertEquals("[TST-1] Fixed message.", FIXED.instance().getMessage());
        assertEquals("[TST-1] Fixed message.", FIXED.instance("x", "y").getMessage());
    }

    @Test
    public void testMatchesByKindAndId() {
        assertTrue(FIXED.matches((Exception)FIXED.instance()));
        assertTrue(THREE.matches((Exception)THREE.instance("A", "B", "C"))); // filled in, still matches
        assertTrue(THREE.matches((Exception)THREE.instance()));              // and so does an unfilled one

        assertFalse(FIXED.matches((Exception)THREE.instance("A", "B", "C"))); // different id
        assertFalse(ONE.matches(new IllegalStateException("[TST-2] One only label."))); // right id, wrong kind
        assertFalse(FIXED.matches(new IllegalStateException("[TST-10] Fixed message."))); // id is not a prefix match
        assertFalse(FIXED.matches(new RuntimeException("[TST-1] Fixed message."))); // neither kind
        assertFalse(FIXED.matches(new IllegalStateException((String)null)));
        assertFalse(FIXED.matches(null));
    }
}
