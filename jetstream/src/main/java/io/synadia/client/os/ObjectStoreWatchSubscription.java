package io.synadia.client.os;

import io.synadia.client.Message;
import io.synadia.client.api.DeliverPolicy;
import io.synadia.client.api.JetStreamException;
import io.synadia.client.impl.NatsWatchSubscription;
import org.jspecify.annotations.NonNull;

import java.util.Collections;

import static io.synadia.client.utils.JetStreamApiUtils.ULONG_UNSET;


/**
 * An active watch over an Object Store bucket. Close it to stop receiving updates.
 */
public class ObjectStoreWatchSubscription extends NatsWatchSubscription<ObjectInfo> {

    /**
     * Construct a watch over the bucket. Prefer the watch methods on {@link ObjectStore ObjectStore}.
     * @param os the object store to watch
     * @param watcher the watcher to receive updates
     * @param watchOptions the watch options to apply
     * @throws JetStreamException covers communication and server-side JetStream errors
     * @throws InterruptedException if interrupted while waiting for the server
     */
    public ObjectStoreWatchSubscription(ObjectStore os, ObjectStoreWatcher watcher, ObjectStoreWatchOption... watchOptions) throws JetStreamException, InterruptedException {
        super(os.js, null); // object store has its own options and no push or consume choice yet

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
