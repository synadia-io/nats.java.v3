package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.ReconnectDelayHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Standard {@link ReconnectDelayHandler} implementation. Stateless — switches purely
 * on {@link Options#reconnectDelayBehavior()} and the parameters the connection passes in.
 *
 * <p>Use {@link #INSTANCE} when you want the standard behaviour; it is what
 * {@link io.synadia.client.OptionsBuilder} falls back to when no custom handler is supplied.
 */
public final class DefaultReconnectDelayHandler implements ReconnectDelayHandler {

    /** Stateless singleton, safe to share across all connections. */
    public static final DefaultReconnectDelayHandler INSTANCE = new DefaultReconnectDelayHandler();

    @Override
    public long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered) {
        boolean firstRound = round <= 1;

        return switch (options.reconnectDelayBehavior()) {
            case BeforeAllRounds ->
                computeWaitMillis(options, secure);
            case LameDuckAware ->
                (firstRound && !lameDuckTriggered)  ? 0L : computeWaitMillis(options, secure);
            default ->
                firstRound ? 0L : computeWaitMillis(options, secure);
        };
    }

    /**
     * Standard wait calculation in milliseconds: {@link Options#getReconnectWait()}
     * plus a uniform random jitter from {@link Options#getReconnectJitter()} (or
     * {@link Options#getReconnectJitterTls()} when {@code secure} is true). Negative
     * results are clamped to zero.
     *
     * <p>Exposed as {@code public static} so custom {@link ReconnectDelayHandler}
     * implementations can reuse the math without copy-pasting.
     */
    public static long computeWaitMillis(Options options, boolean secure) {
        long wait = options.getReconnectWait();
        long jitter = secure ? options.getReconnectJitterTls() : options.getReconnectJitter();
        if (jitter > 0) {
            wait += ThreadLocalRandom.current().nextLong(jitter);
        }
        return wait < 0 ? 0L : wait;
    }
}
