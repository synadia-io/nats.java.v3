package io.synadia.client.impl;

import io.synadia.client.Dispatcher;
import io.synadia.client.MessageHandler;
import io.synadia.client.Subscription;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.synadia.client.utils.Validator.required;
import static io.synadia.client.utils.Validator.validateQueueName;

public class NatsDispatcher extends NatsConsumer implements Dispatcher, Runnable {

    // 5 minutes; idle nap only — a message or the POISON pill wakes poll() immediately, so this only bounds
    // how often an otherwise idle dispatcher wakes to re-check its run flags.
    protected static final long WAIT_FOR_MESSAGE_MINUTES = 5;

    protected final ConsumerMessageQueue incoming;
    protected final MessageHandler defaultHandler;

    protected Future<Boolean> thread;
    protected final AtomicBoolean running;
    protected final AtomicBoolean started;

    protected String id;

    // This tracks subscriptions made with the default handlers
    // There can only be one default handler subscription for any given subject
    protected final Map<String, NatsSubscription> subWithDefaultHandlerBySubject;

    // This tracks subscriptions made with non-default handlers
    protected final Map<String, NatsSubscription> subWithNonDefaultHandlerBySid;

    // There can be multiple non default handlers for any given subject, this track them
    protected final Map<String, Map<String, NatsSubscription>> subsBySidNonDefaultHandlersBySubject;

    // This tracks the non-default handler by sid
    protected final Map<String, MessageHandler> nonDefaultHandlerBySid;

    NatsDispatcher(NatsConnection conn, MessageHandler handler) {
        super(conn);
        this.defaultHandler = handler;
        this.incoming = new ConsumerMessageQueue();
        this.subWithDefaultHandlerBySubject = new ConcurrentHashMap<>();
        this.subWithNonDefaultHandlerBySid = new ConcurrentHashMap<>();
        this.subsBySidNonDefaultHandlersBySubject = new ConcurrentHashMap<>();
        this.nonDefaultHandlerBySid = new ConcurrentHashMap<>();
        this.running = new AtomicBoolean(false);
        this.started = new AtomicBoolean(false);
    }

    @Override
    public void start(String id) {
        internalStart(id, true);
    }

    @SuppressWarnings("SameParameterValue")
    protected void internalStart(String id, boolean threaded) {
        if (!started.get()) {
            this.id = id;
            this.running.set(true);
            this.started.set(true);
            if (threaded) {
                thread = connection.getExecutor().submit(this, Boolean.TRUE);
            }
        }
    }

    boolean breakRunLoop() {
        return this.incoming.isDrained();
    }

    public void run() {
        try {
            while (running.get() && !Thread.interrupted()) {
                NatsMessage msg = this.incoming.pop(WAIT_FOR_MESSAGE_MINUTES, TimeUnit.MINUTES);
                if (msg != null) {
                    NatsSubscription sub = msg.getNatsSubscription();
                    if (sub != null && sub.isActive()) {
                        MessageHandler handler = nonDefaultHandlerBySid.get(sub.getSID());
                        if (handler == null) {
                            handler = defaultHandler;
                        }
                        // A dispatcher can have a null defaultHandler. You can't subscribe without a handler,
                        // but messages might come in while the dispatcher is being closed or after unsubscribe
                        // and the [non-default] handler has already been removed from subscriptionHandlers
                        if (handler != null) {
                            sub.incrementDeliveredCount();
                            this.incrementDeliveredCount();

                            try {
                                handler.onMessage(msg);
                            } catch (Exception exp) {
                                connection.processException(exp);
                            } catch (Error err) {
                                connection.processException(new Exception(err));
                            }

                            if (sub.reachedUnsubLimit()) {
                                connection.invalidate(sub);
                            }
                        }
                    }
                }

                if (breakRunLoop()) {
                    return;
                }
            }
        }
        catch (InterruptedException exp) {
            if (this.running.get()){
                this.connection.processException(exp);
            } //otherwise we did it
            Thread.currentThread().interrupt();
        }
        finally {
            this.running.set(false);
            this.thread = null;
        }
    }

    void stop(boolean unsubscribeAll) {
        this.running.set(false);
        this.incoming.pause();

        if (this.thread != null) {
            try {
                if (!this.thread.isCancelled()) {
                    this.thread.cancel(true);
                }
            } catch (Exception exp) {
                // let it go
            }
        }

        if (unsubscribeAll) {
            subWithDefaultHandlerBySubject.forEach((subject, sub) -> connection.unsubscribe(sub, -1));
            subWithNonDefaultHandlerBySid.forEach((sid, sub) -> connection.unsubscribe(sub, -1));
        }

        subWithDefaultHandlerBySubject.clear();
        subWithNonDefaultHandlerBySid.clear();
        subsBySidNonDefaultHandlersBySubject.clear();
        nonDefaultHandlerBySid.clear();
    }

    public boolean isActive() {
        return this.running.get();
    }

    String getId() {
        return id;
    }

    ConsumerMessageQueue getMessageQueue() {
        return incoming;
    }

    MessageHandler getNonDefaultHandlerBySid(String sid) {
        return nonDefaultHandlerBySid.get(sid);
    }

    boolean hasNoSubs() {
        return subWithDefaultHandlerBySubject.isEmpty() && subWithNonDefaultHandlerBySid.isEmpty();
    }

    void resendSubscriptions() {
        this.subWithDefaultHandlerBySubject.forEach((subject, sub) ->
            connection.sendSubscriptionMessage(sub.getSID(), subject, sub.getQueueName(), true));
        this.subWithNonDefaultHandlerBySid.forEach((sid, sub) ->
            connection.sendSubscriptionMessage(sid, sub.getSubject(), sub.getQueueName(), true));
    }

    // Remove this sub from all of our tracking maps.
    // Instead of logic to figure where the sub is in the first place,
    //   we try all tracking maps, with guard rails.
    // It's possible multiple threads/workflow could be hitting this,
    //   but all the maps are ConcurrentHashMap, we're safe.
    // For the case when we do find the sub's subject mapped to the default handler,
    //   we double-check that what is mapped is the same sub, again mostly a code guard.
    void remove(NatsSubscription sub) {
        // remove from all maps
        // lots of code guards here instead of checking where the sub might be
        String sid = sub.getSID();
        NatsSubscription defaultSub = subWithDefaultHandlerBySubject.get(sub.getSubject());
        if (defaultSub != null && defaultSub.getSID().equals(sid)) {
            subWithDefaultHandlerBySubject.remove(sub.getSubject());
        }
        subWithNonDefaultHandlerBySid.remove(sid);
        nonDefaultHandlerBySid.remove(sid);
        Map<String, NatsSubscription> subsBySid = subsBySidNonDefaultHandlersBySubject.get(sub.getSubject());
        if (subsBySid != null) {
            // it could be null, I know it's weird
            subsBySid.remove(sid);
            if (subsBySid.isEmpty()) {
                // if there are no more for the subject, we can remove the entry
                // from the map to avoid empty entries/memory leak
                subsBySidNonDefaultHandlersBySubject.remove(sub.getSubject());
            }
        }
    }

    public Dispatcher subscribe(String subject) {
        if (defaultHandler == null) {
            throw new IllegalStateException("Dispatcher was made without a default handler.");
        }
        connection.subjectValidate(subject);
        this.subscribeImplCore(subject, null, null);
        return this;
    }

    NatsSubscription subscribeReturningSubscription(String subject) {
        connection.subjectValidate(subject);
        return this.subscribeImplCore(subject, null, null);
    }

    public Subscription subscribe(String subject, MessageHandler handler) {
        connection.subjectValidate(subject);
        required(handler, "Handler");
        return this.subscribeImplCore(subject, null, handler);
    }

    public Dispatcher subscribe(String subject, String queueName) {
        connection.subjectValidate(subject);
        validateQueueName(queueName, true);
        this.subscribeImplCore(subject, queueName, null);
        return this;
    }

    public Subscription subscribe(String subject, String queueName,  MessageHandler handler) {
        connection.subjectValidate(subject);
        validateQueueName(queueName, true);
        if (handler == null) {
            throw new IllegalArgumentException("MessageHandler is required in subscribe");
        }
        return this.subscribeImplCore(subject, queueName, handler);
    }

    // Assumes the subj/queuename checks are done, does check for closed status
    NatsSubscription subscribeImplCore(String subject, String queueName, MessageHandler handler) {
        checkBeforeSubImpl();

        // If the handler is null, then we use the default handler, which will not allow
        // duplicate subscriptions to exist.
        if (handler == null) {
            NatsSubscription sub = this.subWithDefaultHandlerBySubject.get(subject);

            if (sub == null) {
                sub = connection.createSubscriptionInternal(subject, queueName, this, null);
                NatsSubscription wonTheRace = this.subWithDefaultHandlerBySubject.putIfAbsent(subject, sub);
                if (wonTheRace != null) {
                    this.connection.unsubscribe(sub, -1); // Could happen on very bad timing
                }
            }

            return sub;
        }

        return _subscribeImplHandlerProvided(subject, queueName, handler, null);
    }

    public NatsSubscription subscribeImplJetStream(String subject, String queueName, MessageHandler handler, NatsSubscriptionFactory nsf) {
        checkBeforeSubImpl();
        return _subscribeImplHandlerProvided(subject, queueName, handler, nsf);
    }

    private NatsSubscription _subscribeImplHandlerProvided(String subject, String queueName, MessageHandler handler, NatsSubscriptionFactory nsf) {
        NatsSubscription sub = connection.createSubscriptionInternal(subject, queueName, this, nsf);
        trackSubWithUserHandler(sub.getSID(), sub, handler);
        return sub;
    }

    String reSubscribe(NatsSubscription sub, String subject, String queueName, MessageHandler handler) {
        String sid = connection.reSubscribe(sub, subject, queueName);
        trackSubWithUserHandler(sid, sub, handler);
        return sid;
    }

    private void trackSubWithUserHandler(String sid, NatsSubscription sub, MessageHandler handler) {
        subWithNonDefaultHandlerBySid.put(sid, sub);
        Map<String, NatsSubscription> subsBySid =
            subsBySidNonDefaultHandlersBySubject.computeIfAbsent(sub.getSubject(), k -> new ConcurrentHashMap<>());
        subsBySid.put(sid, sub);
        nonDefaultHandlerBySid.put(sid, handler);
    }

    private void checkBeforeSubImpl() {
        if (!running.get()) {
            throw new IllegalStateException("Dispatcher is closed");
        }

        if (isDraining()) {
            throw new IllegalStateException("Dispatcher is draining");
        }
    }

    public Dispatcher unsubscribe(String subject) {
        return this.unsubscribe(subject, -1);
    }

    public Dispatcher unsubscribe(Subscription subscription) {
        return this.unsubscribe(subscription, -1);
    }

    public Dispatcher unsubscribe(String subject, int after) {
        if (!this.running.get()) {
            throw new IllegalStateException("Dispatcher is closed");
        }

        if (isDraining()) { // No op while draining
            return this;
        }

        if (subject == null || subject.length() == 0) {
            throw new IllegalArgumentException("Subject is required in unsubscribe");
        }

        // NatsConnection unsubscribe ends up calling invalidate on the sub which calls dispatcher.remove
        // meaning all we do is call this unsubscribe method and the workflow takes care of the rest
        NatsSubscription defaultHandlerSub = subWithDefaultHandlerBySubject.get(subject);
        if (defaultHandlerSub != null) {
            connection.unsubscribe(defaultHandlerSub, after);
        }

        subWithNonDefaultHandlerBySid.forEach((sid, sub) -> {
            if (subject.equals(sub.getSubject())) {
                connection.unsubscribe(sub, after);
            }
        });

        return this;
    }

    public Dispatcher unsubscribe(Subscription subscription, int after) {
        if (!this.running.get()) {
            throw new IllegalStateException("Dispatcher is closed");
        }

        if (isDraining()) { // No op while draining
            return this;
        }

        if (subscription.getDispatcher() != this) {
            throw new IllegalStateException("Subscription is not managed by this Dispatcher");
        }

        // We can probably optimize this path by adding getSID() to the Subscription interface.
        if (!(subscription instanceof NatsSubscription)) {
            throw new IllegalArgumentException("This Subscription implementation is not known by Dispatcher");
        }
        
        NatsSubscription ns = ((NatsSubscription) subscription);
        // Grab the NatsSubscription to verify we weren't given a different manager's subscription.
        NatsSubscription sub = subWithNonDefaultHandlerBySid.get(ns.getSID());

        if (sub != null) {
            connection.unsubscribe(sub, after); // NatsConnection will tell us when to remove from the map
        }

        return this;
    }

    void sendUnsubForDrain() {
        subWithDefaultHandlerBySubject.forEach((id, sub) -> connection.sendUnsub(sub, -1));
        subWithNonDefaultHandlerBySid.forEach((sid, sub) -> connection.sendUnsub(sub, -1));
    }

    void cleanUpAfterDrain() {
        this.connection.cleanupDispatcher(this);
    }

    public boolean isDrained() {
        return !isActive() && super.isDrained();
    }
}
