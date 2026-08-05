package io.synadia.client.impl;

import io.synadia.client.MessageHandler;
import io.synadia.client.api.*;

import java.util.List;

import static io.synadia.client.utils.JetStreamApiUtils.ULONG_UNSET;

/**
 * The base for the key value and object store watch subscriptions. It owns the ordered
 * consumer and the dispatcher that feed the watcher.
 * @param <T> the type of entry given to the watcher
 */
public class NatsWatchSubscription<T> implements AutoCloseable {
    private final JetStream js;
    private NatsDispatcher dispatcher;
    private JetStreamSubscription sub;

    /**
     * Construct the subscription. Nothing is subscribed until the subclass calls finishInit.
     * @param js the JetStream context used to create the consumer and the dispatcher
     */
    public NatsWatchSubscription(JetStream js) {
        this.js = js;
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

        dispatcher = js.conn.createDispatcher();

        PullOrderedConsumerCreator creator =
            new PullOrderedConsumerCreator()
                .namePrefix(consumerNamePrefix)
                .deliverPolicy(deliverPolicy)
                .startSequence(fromRevision)
                .headersOnly(headersOnly)
                .subjects(subscribeSubjects);
        SubscribeBehavior sb = new SubscribeBehavior().handler(handler).dispatcher(dispatcher);
        sub = js.pullSubscribe(fb.getStreamName(), creator, sb);
        if (!handler.endOfDataSent) {
            long pending = sub.getConsumerInfo().getCalculatedPending();
            if (pending == 0) {
                handler.sendEndOfData();
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

    /**
     * Stop the watch, unsubscribing and closing the dispatcher. Calling it more than once is harmless.
     */
    public void unsubscribe() {
        if (dispatcher != null) {
            dispatcher.unsubscribe(sub);
            js.conn.closeDispatcher(dispatcher);
            dispatcher = null;
        }
    }

    @Override
    public void close() throws Exception {
        unsubscribe();
    }
}
