package io.synadia.client;

/**
 * Controls when the {@link ReconnectDelayHandler} is invoked during reconnect attempts.
 */
public enum ReconnectDelayBehavior {
    /**
     * Invoke the reconnect delay only before subsequent rounds, never before the first round.
     * The first round of reconnect attempts runs immediately; the delay applies between rounds
     * after a full round has been attempted and failed. This is the historical default.
     * {@link ReconnectDelayHandler#getWaitTime(long)} will only be called with
     * {@code totalTries} greater than or equal to 1.
     */
    BeforeSubsequentRounds,
    /**
     * Invoke reconnect delay behavior before each full round of reconnect attempts.
     * {@link ReconnectDelayHandler#getWaitTime(long)} will be called with
     * {@code totalTries} greater than or equal to 0.
     */
    BeforeAllRounds;

    /**
     * Resolve a {@link ReconnectDelayBehavior} from a string (case-insensitive name match).
     * Returns {@link #BeforeSubsequentRounds} if the value is null or does not match any constant.
     *
     * @param value the string value
     * @return the matching behavior, or {@link #BeforeSubsequentRounds} as the default
     */
    public static ReconnectDelayBehavior get(String value) {
        if (value != null) {
            for (ReconnectDelayBehavior rdb : ReconnectDelayBehavior.values()) {
                if (rdb.name().equalsIgnoreCase(value)) {
                    return rdb;
                }
            }
        }
        return BeforeSubsequentRounds;
    }
}
