package io.synadia.client.kv;

import io.synadia.client.JetStreamApiException;
import io.synadia.client.Message;
import io.synadia.client.impl.NatsWatchSubscription;
import io.synadia.client.jsapi.DeliverPolicy;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KeyValueWatchSubscription extends NatsWatchSubscription<KeyValueEntry> {

    public KeyValueWatchSubscription(KeyValue kv, String keyPattern, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException {
        this(kv, Collections.singletonList(keyPattern), watcher, fromRevision, watchOptions);
    }

    public KeyValueWatchSubscription(KeyValue kv, List<String> keyPatterns, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption... watchOptions) throws IOException, JetStreamApiException {
        super(kv.js);
        kvWatchInit(kv, keyPatterns, watcher, fromRevision, watchOptions);
    }

    private void kvWatchInit(KeyValue kv, List<String> keyPatterns, KeyValueWatcher watcher, long fromRevision, KeyValueWatchOption[] watchOptions) throws IOException, JetStreamApiException {
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
        return new WatchMessageHandler<KeyValueEntry>(watcher) {
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
