package io.synadia.client.api;

import org.jspecify.annotations.NullUnmarked;

/**
 * PushDeliverSubjectInterface is built to allow users to make PushCreators without specifying the deliver subject.
 * The JetStreamImpl._createConsumer checks this and makes one if the user did not provide one
 */
@NullUnmarked
public interface PushDeliverSubjectInterface {

    /**
     * Set the subject the server delivers messages to for this push consumer.
     * @param deliverSubject the deliver subject
     * @return this, for chaining
     */
    PushDeliverSubjectInterface deliverSubject(String deliverSubject);

    /**
     * The subject the server delivers messages to for this push consumer.
     * @return the deliver subject
     */
    String getDeliverSubject();
}
