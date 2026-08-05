package io.synadia.client.impl;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.Status;
import org.jspecify.annotations.Nullable;

/**
 *  A checked version of a JetStreamStatusInternalException
 */
public class JetStreamStatusException extends JetStreamException {

    /**
     * The note about where the status was received
     */
    private final String note;

    /**
     * The subscription that this exception occurred on
     */
    @Nullable
    private final JetStreamSubscription sub;

    /**
     * The status object
     */
    private final Status status;

    /**
     * construct a JetStreamStatusException from a JetStreamStatusInternalException.
     * Package-private: the internal cause type is not public, so this bridge is only usable within this package.
     * @param cause the JetStreamStatusInternalException cause
     */
    JetStreamStatusException(JetStreamStatusInternalException cause) {
        this(cause.getNote(), cause.getStatus(), cause.getSubscription());
    }

    /**
     * Construct a JetStreamStatusException. The exception message is the note followed by the status message and code.
     * @param note where the status was received
     * @param status the status received from the server
     * @param sub the subscription the status arrived on, or null if it did not come from a subscription
     */
    public JetStreamStatusException(String note, Status status, @Nullable JetStreamSubscription sub) {
        super(note + ": " + status.getMessageWithCode());
        this.status = status;
        this.note = note;
        this.sub = sub;
    }

    /**
     * Get the note about where the status was received
     * @return the note
     */
    public String getNote() {
        return note;
    }

    /**
     * Get the subscription this issue occurred on. Will be null if not from a subscription
     * @return the subscription
     */
    @Nullable
    public JetStreamSubscription getSubscription() {
        return sub;
    }

    /**
     * Get the full status object
     * @return the status
     */
    public Status getStatus() {
        return status;
    }
}
