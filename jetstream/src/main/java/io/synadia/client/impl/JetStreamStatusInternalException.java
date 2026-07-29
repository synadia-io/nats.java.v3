package io.synadia.client.impl;

import io.synadia.client.api.Status;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * JetStreamStatusInternalException is used to indicate an unknown status message was received.
 * Internal to the JetStream implementation — thrown and bridged to the user-facing
 * {@link JetStreamStatusException} within this package; never exposed to callers.
 */
@NullMarked
class JetStreamStatusInternalException extends StatusException {

    /**
     * The note about where the status was received
     */
    private final String note;

    /**
     * The subscription that this exception occurred on, if applicable
     */
    @Nullable
    private final JetStreamSubscription sub;

    /**
     * Construct JetStreamStatusInternalException for a subscription and a status
     *
     * @param status the status
     * @param sub    the subscription
     */
    JetStreamStatusInternalException(String note, Status status, @Nullable JetStreamSubscription sub) {
        super(status);
        this.note = note;
        this.sub = sub;
    }

    /**
     * Get the note about where the status was received
     * @return the note
     */
    String getNote() {
        return note;
    }

    /**
     * Get the subscription this issue occurred on. Will be null if not from a subscription
     * @return the subscription
     */
    @Nullable
    JetStreamSubscription getSubscription() {
        return sub;
    }
}
