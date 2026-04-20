package io.synadia.client.api;

import io.synadia.client.impl.JetStreamApiUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The AbstractOrderedConsumerCreator class helps specify the configuration for creating an ordered JetStream consumer
 */
@NullMarked
public abstract class AbstractOrderedConsumerCreator<T extends AbstractOrderedConsumerCreator<T>> extends ConsumerCreator<T> {
    public static final long DEFAULT_ORDERED_HEARTBEAT = 5000;

    protected @Nullable String namePrefix;

    protected AbstractOrderedConsumerCreator(String stream, boolean isPush) {
        super(stream, isPush);
        commonInit();
        namePrefix(null);
    }

    protected AbstractOrderedConsumerCreator(AbstractOrderedConsumerCreator<?> creator, long lastStreamSeq) {
        super(creator);
        commonInit();
        namePrefix(creator.namePrefix); // this is called because the base class doesn't know about namePrefix 
        if (lastStreamSeq > 0) {
            // if the last stream seq is > 0, this means to set the policy
            creator
                .deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(lastStreamSeq + 1)
                .startTime(null); // clear start time in case it was originally set
        }
    }

    private void commonInit() {
        _ackPolicy(AckPolicy.None);
        _maxDeliver(1);
        _ackWait(Duration.ofHours(22));
        _memStorage(true);
        _numReplicas(1);
        _idleHeartbeat(DEFAULT_ORDERED_HEARTBEAT);
    }

    /**
     * The prefix to be used for the underlying consumer name
     * @return the prefix
     */
    @Nullable
    public String getNamePrefix() {
        return namePrefix;
    }

    /**
     * Sets the prefix for the name of the consumer.
     * Null or empty clears the field.
     * @param namePrefix the prefix for the name of the consumer.
     * @return this instance for chaining.
     */
    public T namePrefix(@Nullable String namePrefix) {
        this.namePrefix = namePrefix;
        _name(JetStreamApiUtils.generateConsumerName(namePrefix));
        //noinspection unchecked
        return (T)this;
    }
}
