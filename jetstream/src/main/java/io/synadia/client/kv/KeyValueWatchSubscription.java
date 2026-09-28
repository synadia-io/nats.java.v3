package io.synadia.client.kv;

import io.synadia.client.Message;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.impl.NatsWatchSubscription;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An active watch over one or more keys in a Key Value bucket. Close it to stop receiving updates.
 */
public class KeyValueWatchSubscription extends NatsWatchSubscription<KeyValueEntry> {

    /**
     * Construct a watch over a single key pattern. Prefer the watch methods on {@link KeyValue KeyValue}.
     * @param kv the key value bucket to watch
     * @param keyPattern the key pattern, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param fromRevision the revision to start from, or -1 to start from the latest
     * @param watchOptions the watch options to apply
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription(KeyValue kv, String keyPattern, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        this(kv, Collections.singletonList(keyPattern), watcher, fromRevision, watchOptions);
    }

    /**
     * Construct a watch over several key patterns. Prefer the watch methods on {@link KeyValue KeyValue}.
     * @param kv the key value bucket to watch
     * @param keyPatterns the key patterns, which may contain wildcards
     * @param watcher the watcher to receive updates
     * @param fromRevision the revision to start from, or -1 to start from the latest
     * @param watchOptions the watch options to apply
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public KeyValueWatchSubscription(KeyValue kv, List<String> keyPatterns, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        super(kv.js, watchOptions);
        kvWatchInit(kv, keyPatterns, watcher, fromRevision, watchOptions);
    }

    private void kvWatchInit(KeyValue kv, List<String> keyPatterns, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption[] watchOptions) throws JetStreamException, InterruptedException {
        // figure out the result options
        boolean headersOnly = false;
        boolean ignoreDeletes = false;
        DeliverPolicy deliverPolicy = DeliverPolicy.LastPerSubject;
        if (watchOptions != null) {
            for (KeyValueWatchOption wo : watchOptions) {
                if (wo != null) {
                    switch (wo) {
                        case META_ONLY: headersOnly = true; break;
                        case IGNORE_DELETE: ignoreDeletes = true; break;
                        case UPDATES_ONLY: deliverPolicy = DeliverPolicy.New; break;
                        case INCLUDE_HISTORY: deliverPolicy = DeliverPolicy.All; break;
                    }
                }
            }
        }

        // convert each key to a read subject
        List<String> readSubjects = new ArrayList<>();
        for (String keyPattern : keyPatterns) {
            readSubjects.add(kv.readSubject(keyPattern.trim()));
        }

        finishInit(kv,
            readSubjects,
            deliverPolicy,
            headersOnly,
            fromRevision,
            getHandler(watcher, !ignoreDeletes),
            watcher.getConsumerNamePrefix());
    }

    private static @NonNull WatchMessageHandler<KeyValueEntry> getHandler(KeyValueWatcher watcher, boolean includeDeletes) {
        return new WatchMessageHandler<>(watcher) {
            @Override
            public void onMessage(Message m) throws InterruptedException {
                KeyValueEntry kve = new KeyValueEntry(m);
                if (includeDeletes || kve.getOperation() == KeyValueOperation.PUT) {
                    watcher.watch(kve);
                }
                if (!endOfDataSent && kve.getDelta() == 0) {
                    sendEndOfData();
                }
            }
        };
    }
}
