package io.synadia.client;

/**
 * Drives the reconnect-attempt cadence. The connection invokes this handler
 * before every reconnect round, including round 1 (the first round after a
 * disconnect). Implementations decide how long to wait by inspecting the
 * round number, the connection {@link Options}, and the lame-duck flag the
 * connection passes in (the connection tracks the LDM signal itself and
 * consumes it on the round-1 call).
 *
 * <p>Implementations are expected to be stateless — all relevant inputs come
 * in as parameters, so there's nothing to remember between calls. One handler
 * instance can be safely shared across multiple connections.
 *
 * <p>For the standard behaviour, use
 * {@link io.synadia.client.impl.DefaultReconnectDelayHandler#INSTANCE}
 * (which is what {@link OptionsBuilder} falls back to when no handler is
 * supplied). For a custom strategy, implement this interface — a lambda
 * works since the interface is single-abstract-method.
 */
public interface ReconnectDelayHandler {

    /**
     * Compute the wait before the given reconnect round, in milliseconds.
     *
     * @param round              1-based round number; {@code 1} is the first round
     *                           after a disconnect, {@code 2+} are pool wrap-arounds.
     * @param options            the immutable {@link Options} for the connection.
     * @param secure             whether the active server pool contains a TLS URL.
     * @param lameDuckTriggered  whether this reconnect campaign was triggered by a
     *                           server-sent lame-duck signal ({@code INFO {"ldm": true}}).
     *                           The connection consumes this flag on the {@code round == 1}
     *                           call, so subsequent rounds within the same campaign always
     *                           see {@code false}.
     * @return the duration to wait before this round, in milliseconds.
     *         A non-positive value means no wait.
     */
    long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered);
}
