package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.Objects;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Object used to make a request for message get requests.
 */
@NullMarked
public class MessageGetRequest implements JsonSerializable {
    private final long sequence;
    private final @Nullable String lastBySubject;
    private final @Nullable String nextBySubject;
    private final @Nullable ZonedDateTime startTime;

    /**
     * Create a message get request that gets the message for the given sequence.
     * @param sequence the message sequence
     * @return the MessageGetRequest
     */
    public static MessageGetRequest forSequence(long sequence) {
        return new MessageGetRequest(sequence, null, null, null);
    }

    /**
     * Create a message get request that gets the last message for the given subject.
     * @param subject the subject
     * @return the MessageGetRequest
     */
    public static MessageGetRequest lastForSubject(String subject) {
        return new MessageGetRequest(-1, subject, null, null);
    }

    /**
     * Create a message get request that gets the first message for the given subject.
     * @param subject the subject
     * @return the MessageGetRequest
     */
    public static MessageGetRequest firstForSubject(String subject) {
        return new MessageGetRequest(-1, null, subject, null);
    }

    /**
     * Create a message get request that gets the first message at or after the given start time.
     * @param startTime the start time
     * @return the MessageGetRequest
     */
    public static MessageGetRequest firstForStartTime(ZonedDateTime startTime) {
        return new MessageGetRequest(-1, null, null, startTime);
    }

    /**
     * Create a message get request that gets the first message at or after the given start time for the given subject.
     * @param startTime the start time
     * @param subject the subject
     * @return the MessageGetRequest
     */
    public static MessageGetRequest firstForStartTimeAndSubject(ZonedDateTime startTime, String subject) {
        return new MessageGetRequest(-1, null, subject, startTime);
    }

    /**
     * Create a message get request that gets the next message at or after the given sequence for the given subject.
     * @param sequence the starting sequence
     * @param subject the subject
     * @return the MessageGetRequest
     */
    public static MessageGetRequest nextForSubject(long sequence, String subject) {
        return new MessageGetRequest(sequence, null, subject, null);
    }

    /**
     * Construct a MessageGetRequest with the given parameters.
     * @param sequence the message sequence, or -1 if not used
     * @param lastBySubject the last-by-subject value, may be null
     * @param nextBySubject the next-by-subject value, may be null
     * @param startTime the start time, may be null
     */
    protected MessageGetRequest(long sequence, @Nullable String lastBySubject, @Nullable String nextBySubject, @Nullable ZonedDateTime startTime) {
        this.sequence = sequence;
        this.lastBySubject = lastBySubject;
        this.nextBySubject = nextBySubject;
        this.startTime = startTime;
    }

    /**
     * Get the configured sequence.
     * @return the sequence
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * Get the configured last-by-subject value.
     * @return the last-by-subject value or null if not set
     */
    @Nullable
    public String getLastBySubject() {
        return lastBySubject;
    }

    /**
     * Get the configured next-by-subject value.
     * @return the next-by-subject value or null if not set
     */
    @Nullable
    public String getNextBySubject() {
        return nextBySubject;
    }

    /**
     * Whether this request is for a sequence only (no next-by-subject).
     * @return true if this request targets a sequence only
     */
    public boolean isSequenceOnly() {
        return sequence > 0 && nextBySubject == null;
    }

    /**
     * Whether this request is for the last message by subject.
     * @return true if a last-by-subject is configured
     */
    public boolean isLastBySubject() {
        return lastBySubject != null;
    }

    /**
     * Whether this request is for the next message by subject.
     * @return true if a next-by-subject is configured
     */
    public boolean isNextBySubject() {
        return nextBySubject != null;
    }

    /**
     * Get the configured start time.
     * @return the start time or null if not set
     */
    @Nullable
    public ZonedDateTime getStartTime() {
        return startTime;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SEQ, sequence);
        addField(sb, LAST_BY_SUBJECT, lastBySubject);
        addField(sb, NEXT_BY_SUBJECT, nextBySubject);
        addField(sb, START_TIME, startTime);
        return endJson(sb).toString();
    }

    @Override
    public final boolean equals(@Nullable Object o) {
        if (!(o instanceof MessageGetRequest that)) return false;
        return sequence == that.sequence
            && Objects.equals(lastBySubject, that.lastBySubject)
            && Objects.equals(nextBySubject, that.nextBySubject)
            && Objects.equals(startTime, that.startTime);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(sequence);
        result = 31 * result + Objects.hashCode(lastBySubject);
        result = 31 * result + Objects.hashCode(nextBySubject);
        result = 31 * result + Objects.hashCode(startTime);
        return result;
    }
}
