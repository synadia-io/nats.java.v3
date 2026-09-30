package io.synadia.client.os;

import org.jspecify.annotations.NullMarked;

/**
 * Options for the Object Store Watcher
 */
@NullMarked
public enum ObjectStoreWatchOption {
    /**
     * Do not include deletes or purges in results.
     * Default is to include deletes.
     */
    IGNORE_DELETE,

    /**
     * Watch starting at the first entry for all keys.
     * Default is to start at the last per key.
     */
    INCLUDE_HISTORY,

    /**
     * Watch starting when there are new entries for keys.
     * Default is to start at the last per key.
     */
    UPDATES_ONLY,

    /**
     * Receive entries with a push consumer.
     * Default is {@link #SIMPLIFIED_CONSUME}.
     */
    PUSH_CONSUME,

    /**
     * Receive entries with the simplified consume, which reads with a pull consumer. This is the default.
     */
    SIMPLIFIED_CONSUME
}
