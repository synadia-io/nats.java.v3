package io.nats.client.api;

/**
 * Options for the Key Value Watcher
 */
public enum KeyValueWatchOption {
    /**
     * Do not include deletes or purges in results.
     * Default is to include deletes.
     */
    IGNORE_DELETE,

    /**
     * Only get metadata, skip value when retrieving data from the server.
     */
    META_ONLY,

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
