package io.synadia.client.impl;

import io.synadia.client.api.AbstractOrderedConsumerCreator;
import io.synadia.client.api.ConsumerConfiguration;
import io.synadia.client.api.ConsumerInfo;
import io.synadia.client.api.SubscribeBehavior;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

class JetStreamSubscribeConfig extends SubscribeBehavior {
    public final AbstractOrderedConsumerCreator<?> orderedCreator;
    public final ConsumerInfo consumerInfo;
    public final ConsumerConfiguration consumerConf;
    public final boolean isPull;
    public final boolean isOrdered;
    public final String orderedNamePrefix;

    public JetStreamSubscribeConfig(@NonNull ConsumerInfo consumerInfo,
                                    @Nullable SubscribeBehavior subscribeBehavior,
                                    @Nullable AbstractOrderedConsumerCreator<?> orderedCreator) {
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
    }
}
