package io.synadia.client.api;

/**
 * Options for the Object Store Watcher
 */
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
    UPDATES_ONLY
}
