package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The PushOrderedConsumerCreator class specifies the configuration for creating an ordered JetStream consumer
 */
@NullMarked
public class PushOrderedConsumerCreator extends AbstractOrderedConsumerCreator<PushOrderedConsumerCreator> implements PushDeliverSubjectInterface {
    /**
     * Construct a PushOrderedConsumerCreator instance
     */
    public PushOrderedConsumerCreator() {
        super(true);
    }

    public PushOrderedConsumerCreator(PushOrderedConsumerCreator creator, long lastStreamSeq, @Nullable Long inactiveThreshold) {
        super(creator, lastStreamSeq, inactiveThreshold);
    }

    // ----------------------------------------------------------------------------------------------------
    // Push Specific ConsumerFields
    // ----------------------------------------------------------------------------------------------------

    public PushOrderedConsumerCreator deliverSubject(@Nullable String deliverSubject) {
        _deliverSubject(deliverSubject);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return "PushOrderedConsumerCreator " + toJson();
    }
}
