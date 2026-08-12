package io.synadia.client;

/**
 * Controls whether the {@link ReconnectDelayHandler} is invoked before the first round of a
 * reconnect. Rounds after the first always invoke it. The setting applies to every handler,
 * including a custom one — the behavior decides <i>whether</i> to wait, the handler decides
 * <i>how long</i>.
 *
 * <p>The trade-off is failover latency against reconnect storms. A single client losing a single
 * connection wants to retry immediately; a thousand clients losing the same server at the same
 * instant do not. When a server goes down it disconnects every client attached to it at once, so
 * with a large client population that is a large number of simultaneous reconnects. Under
 * {@link #BeforeSubsequentRounds} they all retry immediately and arrive at the remaining servers
 * as a single spike, which can knock over a server that would have absorbed the same clients
 * spread over a couple of seconds.
 *
 * <p>{@link #BeforeAllRounds} addresses that by delaying before the first round as well, spreading
 * the arrival across the reconnect wait plus its jitter. <b>The jitter is what actually does the
 * spreading</b> — a fixed wait moves the spike rather than flattening it — so a deployment relying
 * on this should make sure {@code reconnectJitter} is not zero. The cost is paid on every
 * reconnect, including transient blips a single client would have cleared instantly, so scale is
 * the deciding factor: worth it for a large fleet against a shared cluster, not worth it for a
 * handful of clients.
 *
 * <p>Lame duck mode is the announced form of the same event — the server says it is draining, and
 * every client on it is about to disconnect. {@link #LameDuckAware} delays before the first round
 * only in that case, which keeps fast failover for transient disconnects while still spreading the
 * reconnects the server told us were coming. {@link #BeforeAllRounds} already covers lame duck,
 * since it delays before the first round whatever caused the disconnect; there is no lame-duck
 * variant of it because there would be nothing left for the variant to do.
 */
public enum ReconnectDelayBehavior {
    /**
     * Invoke the reconnect delay only before subsequent rounds, never before the first round.
     * The first round of reconnect attempts runs immediately; the delay applies between rounds
     * after a full round has been attempted and failed.
     *
     * <p>This is the default, and matches v2. Fastest failover, no spreading.
     */
    BeforeSubsequentRounds,
    /**
     * Invoke the reconnect delay before every round, including the first. The handler is called
     * with {@code round == 1} for the first round and {@code round >= 2} for subsequent rounds.
     *
     * <p>Spreads a reconnect storm, and covers the lame duck case as well. Costs one reconnect
     * wait on every failover.
     */
    BeforeAllRounds,
    /**
     * Invoke the reconnect delay before the first round only when the server signaled lame duck
     * mode (via {@code INFO {"ldm": true}}); otherwise behave as {@link #BeforeSubsequentRounds}.
     * Rounds after the first always get the delay either way.
     *
     * <p>Fast failover for transient disconnects, spreading for an announced drain.
     */
    LameDuckAware;

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
