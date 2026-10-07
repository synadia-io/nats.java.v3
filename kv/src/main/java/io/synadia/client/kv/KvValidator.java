// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.kv;

import java.util.List;

import static io.synadia.client.kv.KeyValueUtils.MAX_HISTORY_PER_KEY;
import static io.synadia.client.utils.Validator._validate;
import static io.synadia.client.utils.Validator.required;
import static io.synadia.client.utils.Validator.validateGtZeroOrMinus1;

/**
 * Key Value specific validation: key names and the bucket limits that only a KV bucket has.
 */
final class KvValidator {

    private KvValidator() {} /* ensures cannot be constructed */

    /**
     * Validate a required list of key value keys, allowing wildcards.
     * @param keys the keys to validate
     * @return the keys
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static List<String> validateKvKeysWildcardAllowedRequired(List<String> keys) {
        required(keys, "Key");
        for (String key : keys) {
            validateWildcardKvKey(key, "Key", true);
        }
        return keys;
    }

    /**
     * Validate a required key value key, allowing wildcards.
     * @param s the value to validate
     * @return the key
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static String validateKvKeyWildcardAllowedRequired(String s) {
        return validateWildcardKvKey(s, "Key", true);
    }

    /**
     * Validate a required key value key that may not contain wildcards.
     * @param s the value to validate
     * @return the key
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static String validateNonWildcardKvKeyRequired(String s) {
        return validateNonWildcardKvKey(s, "Key", true);
    }

    /**
     * Validate a key value key, allowing wildcards.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static String validateWildcardKvKey(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notWildcardKvKey(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '*', '-', '_', '/', '=', '>' or '.' and cannot start with '.' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate a key value key that may not contain wildcards.
     * @param s the value to validate
     * @param label name used in the error message
     * @param required true if the value must be supplied
     * @return the value, or null when not required and not supplied
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static String validateNonWildcardKvKey(String s, String label, boolean required) {
        return _validate(s, required, label, () -> {
            if (notNonWildcardKvKey(s)) {
                throw new IllegalArgumentException(label + " must only contain A-Z, a-z, 0-9, '-', '_', '/', '=' or '.' and cannot start with '.' [" + s + "]");
            }
            return s;
        });
    }

    /**
     * Validate a bucket's max history per key.
     * @param max the value to validate
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static int validateMaxHistory(int max) {
        if (max < 1 || max > MAX_HISTORY_PER_KEY) {
            throw new IllegalArgumentException("Max History must be from 1 to " + MAX_HISTORY_PER_KEY + " inclusive.");
        }
        return max;
    }

    /**
     * Validate a bucket's max value size in bytes.
     * @param max the value to validate
     * @return the value
     * @throws IllegalArgumentException if the value is invalid, or is required and not supplied
     */
    static int validateMaxValueSize(int max) {
        return validateGtZeroOrMinus1(max, "Max Value Size"); // max value size is a kv alias to max message size
    }

    // limited-term = (A-Z, a-z, 0-9, dash 45, dot 46, fwd-slash 47, equals 61, underscore 95)+
    // kv-key-name = limited-term (dot limited-term)*
    /**
     * Whether the value is not a valid wildcard-free key value key.
     * @param s the value to validate
     * @return true if the value is not valid
     */
    static boolean notNonWildcardKvKey(String s) {
        if (s.charAt(0) == '.') {
            return true; // can't start with dot
        }
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < '0') { // before 0
                if (c == '-' || c == '.' || c == '/') { // only dash dot and fwd slash are accepted
                    continue;
                }
                return true; // "not"
            }
            if (c < ':') {
                continue; // means it's 0 - 9
            }
            if (c < 'A') {
                if (c == '=') { // equals is accepted
                    continue;
                }
                return true; // between 9 and A is "not limited"
            }
            if (c < '[') {
                continue; // means it's A - Z
            }
            if (c < 'a') { // before a
                if (c == '_') { // only underscore is accepted
                    continue;
                }
                return true; // "not"
            }
            if (c > 'z') { // 122 is z, characters after of them are "not limited"
                return true;
            }
        }
        return false;
    }

    // (A-Z, a-z, 0-9, star 42, dash 45, dot 46, fwd-slash 47, equals 61, gt 62, underscore 95)+
    /**
     * Whether the value is not a valid key value key, wildcards allowed.
     * @param s the value to validate
     * @return true if the value is not valid
     */
    static boolean notWildcardKvKey(String s) {
        if (s.charAt(0) == '.') {
            return true; // can't start with dot
        }
        for (int x = 0; x < s.length(); x++) {
            char c = s.charAt(x);
            if (c < '0') { // before 0
                if (c == '*' || c == '-' || c == '.' || c == '/') { // only star dash dot and fwd slash are accepted
                    continue;
                }
                return true; // "not"
            }
            if (c < ':') {
                continue; // means it's 0 - 9
            }
            if (c < 'A') {
                if (c == '=' || c == '>') { // equals, gt is accepted
                    continue;
                }
                return true; // between 9 and A is "not limited"
            }
            if (c < '[') {
                continue; // means it's A - Z
            }
            if (c < 'a') { // before a
                if (c == '_') { // only underscore is accepted
                    continue;
                }
                return true; // "not"
            }
            if (c > 'z') { // 122 is z, characters after of them are "not limited"
                return true;
            }
        }
        return false;
    }
}
