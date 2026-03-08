package io.nats.client;

/**
 * Interface to implement to provide a custom clock implementation
 */
public interface NatsSystemClockProvider {
    /**
     * Returns the current time in milliseconds
     * @return the current time in milliseconds
     */
    default long currentTimeMillis() { return System.currentTimeMillis(); }

    /**
     * A nano time suitable for calculating elapsed time
     * @return the nano time
     */
    default long nanoTime() { return System.nanoTime(); }
}
