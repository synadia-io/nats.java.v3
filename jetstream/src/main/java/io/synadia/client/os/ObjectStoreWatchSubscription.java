package io.synadia.client.os;

import io.synadia.client.Message;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.NatsWatchSubscription;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.Collections;

import static io.synadia.client.utils.JetStreamApiUtils.ULONG_UNSET;


public class ObjectStoreWatchSubscription extends NatsWatchSubscription<ObjectInfo> {

    public ObjectStoreWatchSubscription(ObjectStore os, ObjectStoreWatcher watcher, ObjectStoreWatchOption... watchOptions) throws IOException, JetStreamApiException {
        super(os.js);

        // figure out the result options
        boolean headersOnly = false;
        boolean ignoreDeletes = false;
        DeliverPolicy deliverPolicy = DeliverPolicy.LastPerSubject;
        if (watchOptions != null) {
            for (ObjectStoreWatchOption wo : watchOptions) {
                if (wo != null) {
                    switch (wo) {
                        case IGNORE_DELETE: ignoreDeletes = true; break;
                        case UPDATES_ONLY: deliverPolicy = DeliverPolicy.New; break;
                        case INCLUDE_HISTORY: deliverPolicy = DeliverPolicy.All; break;
                    }
                }
            }
        }

        finishInit(os,
            Collections.singletonList(os.rawAllMetaSubject()),
            deliverPolicy,
            headersOnly,
            ULONG_UNSET,
            getHandler(watcher, !ignoreDeletes),
            watcher.getConsumerNamePrefix());
    }

    private static @NonNull WatchMessageHandler<ObjectInfo> getHandler(ObjectStoreWatcher watcher, boolean includeDeletes) {
        return new WatchMessageHandler<ObjectInfo>(watcher) {
            @Override
            public void onMessage(Message m) throws InterruptedException {
                ObjectInfo os = new ObjectInfo(m);
                if (includeDeletes || !os.isDeleted()) {
                    watcher.watch(os);
                }
                if (!endOfDataSent && m.metaData().pendingCount() == 0) {
                    sendEndOfData();
                }
            }
        };
    }
}
