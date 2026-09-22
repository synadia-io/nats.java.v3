package io.synadia.client.impl;

import io.synadia.client.api.Status;
import org.jspecify.annotations.Nullable;

/**
 * The JetStream form of {@link StatusException}: the same unchecked status signal, plus the note and the
 * subscription it arrived on. JetStream code raises this one; core raises the plain {@link StatusException},
 * so {@code catch (StatusException)} covers both and this type narrows to JetStream.
 *
 * <p>Deliberately outside the checked {@link io.synadia.client.api.JetStreamException} hierarchy: the
 * {@code nextMessage} methods implement core {@code Subscription} declarations that carry no checked
 * exception, so a status cannot be checked there.
 */
public class JetStreamStatusException extends StatusException {

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
     * Construct a JetStreamStatusException. The exception message is the status message and code.
     * @param status the status received from the server
     * @param sub the subscription the status arrived on, or null if it did not come from a subscription
     */
    public JetStreamStatusException(Status status, @Nullable JetStreamSubscription sub) {
        this(null, status, sub);
    }

    /**
     * Construct a JetStreamStatusException. The exception message is the note followed by the status message and code.
     * @param note where the status was received, or null to use the status message as the note
     * @param status the status received from the server
     * @param sub the subscription the status arrived on, or null if it did not come from a subscription
     */
    public JetStreamStatusException(@Nullable String note, Status status, @Nullable JetStreamSubscription sub) {
        super(note == null ? status.getMessageWithCode() : note + ": " + status.getMessageWithCode(), status);
        this.note = note == null ? status.getMessage() : note;
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
}
