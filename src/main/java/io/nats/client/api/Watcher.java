package io.nats.client.api;

import org.jspecify.annotations.Nullable;

/**
 * Use the Watcher interface to watch for updates
 * @param <T> the type being watched
 */
public interface Watcher<T> {

    /**
     * Called when an object has been updated.
     * @param t The watched object
     */
    void watch(T t);

    /**
     * Called once if there is no data when the watch is created
     * or if there is data, the first time the watch exhausts all existing data.
     */
    void endOfData();

    /**
     * The watcher can supply a prefix to use on the consumer name
     * that is generated when creating the internal watch consumer.
     * This can be useful for monitoring the consumer.
     * @return the name, or null if not needed, which is the default interface implementation.
     */
    @Nullable
    default String getConsumerNamePrefix() {
        return null;
    }
}
