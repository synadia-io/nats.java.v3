// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.kv;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.synadia.client.kv.KvValidator.*;
import static io.synadia.client.utils.TestBase.*;
import static org.junit.jupiter.api.Assertions.*;

public class KvValidatorTests {

    @Test
    public void testValidateKvKeyWildcardAllowedRequired() {
        validateKvKeyWildcardAllowedRequired(PLAIN);
        validateKvKeyWildcardAllowedRequired(PLAIN.toUpperCase());
        validateKvKeyWildcardAllowedRequired(HAS_DASH);
        validateKvKeyWildcardAllowedRequired(HAS_UNDER);
        validateKvKeyWildcardAllowedRequired(HAS_FWD_SLASH);
        validateKvKeyWildcardAllowedRequired(HAS_EQUALS);
        validateKvKeyWildcardAllowedRequired(HAS_DOT);
        validateKvKeyWildcardAllowedRequired(STAR_NOT_SEGMENT);
        validateKvKeyWildcardAllowedRequired(GT_NOT_SEGMENT);
        validateKvKeyWildcardAllowedRequired("numbers9ok");
        String nullKey = null;
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(nullKey));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_TIC));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(HAS_COLON));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeyWildcardAllowedRequired(".starts.with.dot.not.allowed"));

        List<String> nullList = null;
        //noinspection ConstantValue
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(nullList));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Collections.singletonList(HAS_SPACE)));
    }

    @Test
    public void testValidateNonWildcardKeyRequired() {
        validateNonWildcardKvKeyRequired(PLAIN);
        validateNonWildcardKvKeyRequired(PLAIN.toUpperCase());
        validateNonWildcardKvKeyRequired(HAS_DASH);
        validateNonWildcardKvKeyRequired(HAS_UNDER);
        validateNonWildcardKvKeyRequired(HAS_FWD_SLASH);
        validateNonWildcardKvKeyRequired(HAS_EQUALS);
        validateNonWildcardKvKeyRequired(HAS_DOT);
        validateNonWildcardKvKeyRequired("numbers9ok");
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(null));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_SPACE));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(STAR_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(GT_NOT_SEGMENT));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_DOLLAR));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_LOW));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_127));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_TIC));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(HAS_COLON));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKeyRequired(".starts.with.dot.not.allowed"));
    }

    @Test
    public void testValidateMaxHistory() {
        assertEquals(1, validateMaxHistory(1));
        assertEquals(64, validateMaxHistory(64));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(0));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(-1));
        assertThrows(IllegalArgumentException.class, () -> validateMaxHistory(65));
    }

    @Test
    public void testValidateKvKeysWildcardAllowedRequiredValid() {
        List<String> keys = Arrays.asList(PLAIN, HAS_DOT, STAR_NOT_SEGMENT, GT_NOT_SEGMENT);
        assertSame(keys, validateKvKeysWildcardAllowedRequired(keys));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(new ArrayList<>()));
        assertThrows(IllegalArgumentException.class, () -> validateKvKeysWildcardAllowedRequired(Arrays.asList(PLAIN, HAS_SPACE)));
    }

    @Test
    public void testValidateWildcardKvKey() {
        assertEquals(PLAIN, validateWildcardKvKey(PLAIN, "label", true));
        assertEquals(STAR_NOT_SEGMENT, validateWildcardKvKey(STAR_NOT_SEGMENT, "label", false));
        assertEquals(GT_NOT_SEGMENT, validateWildcardKvKey(GT_NOT_SEGMENT, "label", false));
        assertNull(validateWildcardKvKey(null, "label", false));
        assertNull(validateWildcardKvKey("", "label", false));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(HAS_SPACE, "label", false));
        assertTrue(iae.getMessage().startsWith("label"));
        assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(null, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validateWildcardKvKey(STARTS_WITH_DOT, "label", false));
    }

    @Test
    public void testValidateNonWildcardKvKey() {
        assertEquals(PLAIN, validateNonWildcardKvKey(PLAIN, "label", true));
        assertEquals(HAS_DOT, validateNonWildcardKvKey(HAS_DOT, "label", false));
        assertNull(validateNonWildcardKvKey(null, "label", false));
        assertNull(validateNonWildcardKvKey("", "label", false));

        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(STAR_NOT_SEGMENT, "label", false));
        assertTrue(iae.getMessage().startsWith("label"));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(GT_NOT_SEGMENT, "label", false));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(null, "label", true));
        assertThrows(IllegalArgumentException.class, () -> validateNonWildcardKvKey(STARTS_WITH_DOT, "label", false));
    }

    @Test
    public void testValidateMaxValueSize() {
        assertEquals(1, validateMaxValueSize(1));
        assertEquals(-1, validateMaxValueSize(-1));
        assertThrows(IllegalArgumentException.class, () -> validateMaxValueSize(0));
        assertThrows(IllegalArgumentException.class, () -> validateMaxValueSize(-2));
    }

    @Test
    public void testNotNonWildcardKvKey() {
        assertFalse(notNonWildcardKvKey(PLAIN));
        assertFalse(notNonWildcardKvKey(HAS_DASH));
        assertFalse(notNonWildcardKvKey(HAS_UNDER));
        assertFalse(notNonWildcardKvKey(HAS_DOT));
        assertFalse(notNonWildcardKvKey(HAS_FWD_SLASH));
        assertFalse(notNonWildcardKvKey(HAS_EQUALS));
        assertTrue(notNonWildcardKvKey(STARTS_WITH_DOT));
        assertTrue(notNonWildcardKvKey(STAR_NOT_SEGMENT));
        assertTrue(notNonWildcardKvKey(GT_NOT_SEGMENT));
        assertTrue(notNonWildcardKvKey(HAS_SPACE));
        assertTrue(notNonWildcardKvKey(HAS_DOLLAR));
        assertTrue(notNonWildcardKvKey(HAS_BACK_SLASH));
        assertTrue(notNonWildcardKvKey(HAS_TIC));
        assertTrue(notNonWildcardKvKey(HAS_COLON));
        assertTrue(notNonWildcardKvKey(HAS_BRACE));
    }

    @Test
    public void testNotWildcardKvKey() {
        assertFalse(notWildcardKvKey(PLAIN));
        assertFalse(notWildcardKvKey(HAS_DASH));
        assertFalse(notWildcardKvKey(HAS_UNDER));
        assertFalse(notWildcardKvKey(HAS_DOT));
        assertFalse(notWildcardKvKey(HAS_FWD_SLASH));
        assertFalse(notWildcardKvKey(HAS_EQUALS));
        assertFalse(notWildcardKvKey(STAR_NOT_SEGMENT));
        assertFalse(notWildcardKvKey(GT_NOT_SEGMENT));
        assertTrue(notWildcardKvKey(STARTS_WITH_DOT));
        assertTrue(notWildcardKvKey(HAS_SPACE));
        assertTrue(notWildcardKvKey(HAS_DOLLAR));
        assertTrue(notWildcardKvKey(HAS_BACK_SLASH));
        assertTrue(notWildcardKvKey(HAS_TIC));
        assertTrue(notWildcardKvKey(HAS_COLON));
        assertTrue(notWildcardKvKey(HAS_BRACE));
    }
}
