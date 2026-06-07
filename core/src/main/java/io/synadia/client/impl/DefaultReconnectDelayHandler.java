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

        switch (options.reconnectDelayBehavior()) {
            case BeforeAllRounds:
                return computeWaitMillis(options, secure);

            case LameDuckAware:
                if (firstRound && !lameDuckTriggered) {
                    return 0L;
                }
                return computeWaitMillis(options, secure);

            case BeforeSubsequentRounds:
            default:
                return firstRound ? 0L : computeWaitMillis(options, secure);
        }
    }

    /**
     * Standard wait calculation in milliseconds: {@link Options#getReconnectWaitMillis()}
     * plus a uniform random jitter from {@link Options#getReconnectJitterMillis()} (or
     * {@link Options#getReconnectJitterTlsMillis()} when {@code secure} is true). Negative
     * results are clamped to zero.
     *
     * <p>Exposed as {@code public static} so custom {@link ReconnectDelayHandler}
     * implementations can reuse the math without copy-pasting.
     */
    public static long computeWaitMillis(Options options, boolean secure) {
        long wait = options.getReconnectWaitMillis();
        long jitter = secure ? options.getReconnectJitterTlsMillis() : options.getReconnectJitterMillis();
        if (jitter > 0) {
            wait += ThreadLocalRandom.current().nextLong(jitter);
        }
        return wait < 0 ? 0L : wait;
    }
}
