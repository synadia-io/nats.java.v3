package io.synadia.client;

/**
 * Controls when the {@link ReconnectDelayHandler} is invoked during reconnect attempts.
 */
public enum ReconnectDelayBehavior {
    /**
     * Invoke the reconnect delay only before subsequent rounds, never before the first round.
     * The first round of reconnect attempts runs immediately; the delay applies between rounds
     * after a full round has been attempted and failed. This is the historical (v2) default.
     */
    BeforeSubsequentRounds,
    /**
     * Invoke reconnect delay behavior before each full round of reconnect attempts.
     * The handler will be called with {@code round == 1} for the first round and {@code round >= 2}
     * for subsequent rounds.
     */
    BeforeAllRounds,
    /**
     * Wait before round 1 only when the server has signalled lame duck mode
     * (via {@code INFO {"ldm": true}}). Otherwise behave as {@link #BeforeSubsequentRounds}.
     * Wait between rounds in either case.
     *
     * <p>This is the default. It preserves fast cluster-failover semantics for transient
     * disconnects while honouring the operator's explicit "I'm going down, please back off" signal.
     */
    LameDuckAware;

    /**
     * Resolve a {@link ReconnectDelayBehavior} from a string (case-insensitive name match).
     * Returns {@link #LameDuckAware} if the value is null or does not match any constant.
     *
     * @param value the string value
     * @return the matching behavior, or {@link #LameDuckAware} as the default
     */
    public static ReconnectDelayBehavior get(String value) {
        if (value != null) {
            for (ReconnectDelayBehavior rdb : ReconnectDelayBehavior.values()) {
                if (rdb.name().equalsIgnoreCase(value)) {
                    return rdb;
                }
            }
        }
        return LameDuckAware;
    }
}
