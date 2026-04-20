package io.synadia.client.api;

import org.jspecify.annotations.NullUnmarked;

/**
 * PushDeliverSubjectInterface is built to allow users to make PushCreators without specifying the deliver subject.
 * The JetStreamImpl._createConsumer checks this and makes one if the user did not provide one
 */
@NullUnmarked
public interface PushDeliverSubjectInterface {

    PushDeliverSubjectInterface deliverSubject(String deliverSubject);
    String getDeliverSubject();
}
