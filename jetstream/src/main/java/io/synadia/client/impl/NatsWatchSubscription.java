package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;
import io.synadia.client.kv.KeyValueWatchOption;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.synadia.client.utils.JetStreamApiUtils.ULONG_UNSET;

/**
 * The base for the key value and object store watch subscriptions. It owns the ordered
 * consumer and the dispatcher that feed the watcher.
 * @param <T> the type of entry given to the watcher
 */
public class NatsWatchSubscription<T> implements AutoCloseable {

    private final JetStream js;
    private final boolean pushConsume;
    private JetStreamPushSubscription sub;
    private MessageConsumer messageConsumer;

    /**
     * Construct the subscription. Nothing is subscribed until the subclass calls finishInit.
     * @param js the JetStream context used to create the consumer and the dispatcher
     * @param watchOptions the watch options, which decide whether entries are received with a push
     *                     consumer or with the simplified consume. Simplified consume is the default.
     */
    public NatsWatchSubscription(JetStream js, @Nullable KeyValueWatchOption[] watchOptions) {
        this.js = js;
        this.pushConsume = isPushConsume(watchOptions);
    }

    private static boolean isPushConsume(@Nullable KeyValueWatchOption[] watchOptions) {
        if (watchOptions != null) {
            for (KeyValueWatchOption wo : watchOptions) {
                if (wo == KeyValueWatchOption.PUSH_CONSUME) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void finishInit(AbstractBucketFeature fb,
                              List<String> subscribeSubjects,
                              DeliverPolicy deliverPolicy,
                              boolean headersOnly,
                              long fromRevision,
                              WatchMessageHandler<T> handler,
                              String consumerNamePrefix)
        throws JetStreamException, InterruptedException
    {
        if (fromRevision > ULONG_UNSET) {
            deliverPolicy = DeliverPolicy.ByStartSequence;
        }
        else {
            fromRevision = ULONG_UNSET; // easier on the builder since we aren't starting at a fromRevision
            if (deliverPolicy == DeliverPolicy.New) {
                handler.sendEndOfData();
            }
        }

        if (pushConsume) {
            PushOrderedConsumerCreator creator = new PushOrderedConsumerCreator()
                .namePrefix(consumerNamePrefix)
                .deliverPolicy(deliverPolicy)
                .startSequence(fromRevision)
                .headersOnly(headersOnly)
                .filterSubjects(subscribeSubjects);
            SubscribeBehavior sb = new SubscribeBehavior().handler(handler).dispatcher(js.createDispatcher());

            sub = js.pushSubscribe(fb.streamName, creator, sb);

            if (!handler.endOfDataSent) {
                long pending = sub.getConsumerInfo().getCalculatedPending();
                if (pending == 0) {
                    handler.sendEndOfData();
                }
            }
        }
        else {
            PullOrderedConsumerCreator creator =
                new PullOrderedConsumerCreator()
                    .namePrefix(consumerNamePrefix)
                    .deliverPolicy(deliverPolicy)
                    .startSequence(fromRevision)
                    .headersOnly(headersOnly)
                    .filterSubjects(subscribeSubjects);

            StreamContext streamContext = js.getStreamContext(fb.streamName);
            OrderedConsumerContext occ = streamContext.createOrderedConsumer(creator);
            messageConsumer = occ.consume(js.createDispatcher(), handler);

            if (!handler.endOfDataSent) {
                long pending = messageConsumer.getConsumerInfo().getCalculatedPending();
                if (pending == 0) {
                    handler.sendEndOfData();
                }
            }

        }
    }

    protected static abstract class WatchMessageHandler<T> implements MessageHandler {
        private final Watcher<T> watcher;
        protected boolean endOfDataSent;

        protected WatchMessageHandler(Watcher<T> watcher) {
            this.watcher = watcher;
        }

        public void sendEndOfData() {
            endOfDataSent = true;
            watcher.endOfData();
        }
    }

    @Override
    public void close() throws Exception {
        if (pushConsume) {
            sub.unsubscribe();
        }
        else {
            messageConsumer.close();
        }
    }
}
