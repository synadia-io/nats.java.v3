package io.synadia.client;

/**
 * Whether subject strings should be validated against naming rules,
 * and the level of subject validation.
 */
public enum SubjectValidationType {
    /**
     * No Subject Validation
     */
    None,
    /**
     * Lenient Subject Validation
     */
    Lenient,
    /**
     * Strict Subject Validation
     */
    Strict;
}
