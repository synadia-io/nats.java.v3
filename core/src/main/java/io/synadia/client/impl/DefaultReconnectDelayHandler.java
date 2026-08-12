package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.ReconnectDelayHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Standard {@link ReconnectDelayHandler} implementation. Stateless, and a pure function of the
 * options — it returns the standard wait for every round it is asked about.
 *
 * <p>It does not consult {@link Options#reconnectDelayBehavior()}. The connection reads that to
 * decide <i>whether</i> to invoke a handler before the first round; a handler is only ever asked
 * <i>how long</i> to wait.
 *
 * <p>Use {@link #INSTANCE} when you want the standard behaviour; it is what
 * {@link io.synadia.client.OptionsBuilder} falls back to when no custom handler is supplied.
 */
public final class DefaultReconnectDelayHandler implements ReconnectDelayHandler {

    /** Construct a handler. Prefer {@link #INSTANCE} since the handler holds no state. */
    public DefaultReconnectDelayHandler() {}

    /** Stateless singleton, safe to share across all connections. */
    public static final DefaultReconnectDelayHandler INSTANCE = new DefaultReconnectDelayHandler();

    @Override
    public long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered) {
        return computeWaitMillis(options, secure);
    }

    /**
     * Standard wait calculation in milliseconds: {@link Options#getReconnectWait()}
     * plus a uniform random jitter from {@link Options#getReconnectJitter()} (or
     * {@link Options#getReconnectJitterTls()} when {@code secure} is true). Negative
     * results are clamped to zero.
     *
     * <p>Exposed as {@code public static} so custom {@link ReconnectDelayHandler}
     * implementations can reuse the math without copy-pasting.
     *
     * @param options the options supplying the wait and jitter settings
     * @param secure whether the connection being reestablished is a TLS connection
     * @return the wait in milliseconds, never negative
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
