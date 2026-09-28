package io.synadia.client.kv;

import org.jspecify.annotations.NullMarked;

/**
 * Options for the Key Value Watcher
 */
@NullMarked
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
    UPDATES_ONLY,

    /**
     * Receive entries with a push consumer.
     * Default is {@link #SIMPLIFIED_CONSUME}.
     * <p>Reading a bucket in another account this way requires the account exporting the bucket
     * to export the client inbox as a stream. See the account section of the readme.
     */
    PUSH_CONSUME,

    /**
     * Receive entries with the simplified consume, which reads with a pull consumer. This is the default.
     * <p>Reading a bucket in another account this way requires the account exporting the bucket
     * to export {@code $JS.API.>} with {@code response_type: Stream}. See the account section of the readme.
     */
    SIMPLIFIED_CONSUME
}
