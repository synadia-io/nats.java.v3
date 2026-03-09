package io.synadia.client;

/**
 * Bridge to the NatsSystemClockProvider implementation
 */
public final class NatsSystemClock {
    private static NatsSystemClockProvider PROVIDER = new NatsSystemClockProvider() {};

    private NatsSystemClock() {}  /* ensures cannot be constructed */

    /**
     * Set the provider. Null will reset to system default
     * @param provider the provider
     */
    public static void setProvider(final NatsSystemClockProvider provider) {
        PROVIDER = provider == null ? new NatsSystemClockProvider() {} : provider;
    }

    /**
     * Get the current milliseconds from the provider
     * @return the milliseconds
     */
    public static long currentTimeMillis() {
        return PROVIDER.currentTimeMillis();
    }

    /**
     * Get the current nano time from the provider, suitable for calculating elapsed time
     * @return the nano time
     */
    public static long nanoTime() {
        return PROVIDER.nanoTime();
    }
}
