package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * The PushOrderedConsumerCreator class specifies the configuration for creating an ordered JetStream consumer
 */
@NullMarked
public class PushOrderedConsumerCreator extends AbstractOrderedConsumerCreator<PushOrderedConsumerCreator> implements PushDeliverSubjectInterface {
    /**
     * Construct a PushOrderedConsumerCreator instance
     * @param stream the stream name
     */
    public PushOrderedConsumerCreator(String stream) {
        super(stream, true);
    }

    public PushOrderedConsumerCreator(PushOrderedConsumerCreator creator, long lastStreamSeq) {
        super(creator, lastStreamSeq);
    }

    // ----------------------------------------------------------------------------------------------------
    // Push Specific ConsumerFields
    // ----------------------------------------------------------------------------------------------------

    public PushOrderedConsumerCreator deliverSubject(String deliverSubject) {
        _deliverSubject(deliverSubject);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    @Override
    public String toString() {
        return "PushOrderedConsumerCreator " + toJson();
    }
}
