package io.synadia.client.impl;

import io.synadia.client.api.AbstractOrderedConsumerCreator;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.SubscribeBehavior;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The resolved settings for one JetStream subscription, worked out once when the subscription is created
 * by combining the consumer the subscription is bound to with the caller's subscribe behavior.
 */
public class JetStreamSubscribeConfig extends SubscribeBehavior {
    /** The creator that makes replacement consumers when an ordered consumer has to reset, or null if this is not ordered. */
    public final AbstractOrderedConsumerCreator<?> orderedCreator;

    /** The consumer this subscription is bound to, as it was at subscribe time. */
    public final ConsumerInfo consumerInfo;

    /** The configuration of {@link #consumerInfo}, kept out for convenience. */
    public final ConsumerConfiguration consumerConf;

    /** True if the consumer is pull based, so messages must be requested rather than pushed by the server. */
    public final boolean isPull;

    /** True if this is an ordered subscription, which resets its consumer on any gap in the sequence. */
    public final boolean isOrdered;

    /** The prefix used to name each generated ordered consumer, or null if this is not ordered. */
    public final String orderedNamePrefix;

    /** True if the dispatcher was created for this subscription, meaning it is also shut down when the subscription ends. */
    public final boolean internalDispatcher;

    JetStreamSubscribeConfig(@NonNull ConsumerInfo consumerInfo,
                             @Nullable SubscribeBehavior subscribeBehavior,
                             @Nullable AbstractOrderedConsumerCreator<?> orderedCreator,
                             @NonNull Supplier<NatsDispatcher> internalDispatcherSupplier)
    {
        this.orderedCreator = orderedCreator;
        this.consumerInfo = consumerInfo;
        this.consumerConf = consumerInfo.getConsumerConfiguration();
        if (subscribeBehavior != null) {
            subscribeBehavior(subscribeBehavior);
        }
        this.isPull = !consumerInfo.isPushBound();
        if (orderedCreator != null) {
            this.isOrdered = true;
            this.orderedNamePrefix = orderedCreator.getNamePrefix();
        }
        else {
            this.isOrdered = false;
            this.orderedNamePrefix = null;
        }

        boolean internalDispatcher = false;
        if (handler == null) {
            if (dispatcher != null) {
                throw new IllegalArgumentException("Dispatcher without a handler cannot receive messages");
            }
        }
        else if (dispatcher == null) {
            dispatcher(internalDispatcherSupplier.get());
            internalDispatcher = true;
        }
        this.internalDispatcher = internalDispatcher;
    }
}
