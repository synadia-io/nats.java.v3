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

    /**
     * Resolve a {@link SubjectValidationType} from a string (case-insensitive name match).
     * Returns {@link #Lenient} if the value is null or does not match any constant.
     *
     * @param value the string value
     * @return the matching type, or {@link #Lenient} as the default
     */
    public static SubjectValidationType get(String value) {
        if (value != null) {
            for (SubjectValidationType svt : SubjectValidationType.values()) {
                if (svt.name().equalsIgnoreCase(value)) {
                    return svt;
                }
            }
        }
        return Lenient;
    }
}
