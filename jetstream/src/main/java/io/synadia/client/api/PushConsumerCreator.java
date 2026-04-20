package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * PushConsumerCreator helps you create a push Consumer with durable support.
 */
@NullMarked
public class PushConsumerCreator extends AbstractEphemeralConsumerCreator<PushConsumerCreator> implements PushDeliverSubjectInterface {

    /**
     * Construct the creator
     * @param stream the stream name
     */
    public PushConsumerCreator(String stream) {
        super(stream, true);
    }

    // ----------------------------------------------------------------------------------------------------
    // Durable
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the name of the durable consumer.
     * Null or empty clears the field.
     * @param durable name of the durable consumer.
     * @return the creator
     */
    public PushConsumerCreator durable(String durable) {
        _durable(durable);
        return this;
    }

    // ----------------------------------------------------------------------------------------------------
    // Push-specific
    // ----------------------------------------------------------------------------------------------------

    /**
     * Sets the group to deliver messages to.
     * @param group the delivery group.
     * @return the creator
     */
    public PushConsumerCreator deliverGroup(String group) {
        _deliverGroup(group);
        return this;
    }

    public PushConsumerCreator deliverSubject(String deliverSubject) {
        _deliverSubject(deliverSubject);
        return this;
    }

    @Override
    public String toString() {
        return "PushConsumerCreator " + toJson();
    }
}
