package io.synadia.client.api;

import io.synadia.client.utils.JetStreamApiUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * The AbstractOrderedConsumerCreator class helps specify the configuration for creating an ordered JetStream consumer
 * @param <T> the concrete creator type, returned by the fluent setters for chaining
 */
@NullMarked
public abstract class AbstractOrderedConsumerCreator<T extends AbstractOrderedConsumerCreator<T>> extends ConsumerCreator<T> {
    /** Idle heartbeat in milliseconds used by ordered consumers. {@value} */
    public static final long DEFAULT_ORDERED_HEARTBEAT = 5000;

    protected @Nullable String namePrefix;

    protected AbstractOrderedConsumerCreator(boolean isPush) {
        super(isPush);
        commonInit();
        namePrefix(null);
    }

    protected AbstractOrderedConsumerCreator(AbstractOrderedConsumerCreator<?> creator, long lastStreamSeq, @Nullable Long inactiveThreshold) {
        super(creator);
        commonInit();
        namePrefix(creator.namePrefix); // this is called because the base class doesn't know about namePrefix
        if (lastStreamSeq > 0) {
            // if the last stream seq is > 0, this means to set the policy
            deliverPolicy(DeliverPolicy.ByStartSequence)
                .startSequence(lastStreamSeq + 1)
                .startTime(null); // clear start time in case it was originally set
        }
        if (inactiveThreshold != null) {
            inactiveThreshold(inactiveThreshold);
        }
    }

    private void commonInit() {
        _ackPolicy(AckPolicy.None);
        _maxDeliver(1);
        _ackWait(Duration.ofHours(22));
        _memStorage(true);
        _numReplicas(1);
        if (isPush) {
            _idleHeartbeat(DEFAULT_ORDERED_HEARTBEAT);
        }
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

    @Override
    public boolean equals(@Nullable Object o) {
        if (!super.equals(o)) return false;
        AbstractOrderedConsumerCreator<?> that = (AbstractOrderedConsumerCreator<?>) o;
        return Objects.equals(namePrefix, that.namePrefix);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + Objects.hashCode(namePrefix);
    }
}
