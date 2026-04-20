package io.synadia.client.api;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * Object used to make a request for message get requests.
 */
@NullMarked
public class MessageGetRequest implements JsonSerializable {
    private final long sequence;
    private final String lastBySubject;
    private final String nextBySubject;
    private final ZonedDateTime startTime;
    public static MessageGetRequest forSequence(long sequence) {
        return new MessageGetRequest(sequence, null, null, null);
    }
    public static MessageGetRequest lastForSubject(String subject) {
        return new MessageGetRequest(-1, subject, null, null);
    }
    public static MessageGetRequest firstForSubject(String subject) {
        return new MessageGetRequest(-1, null, subject, null);
    }
    public static MessageGetRequest firstForStartTime(ZonedDateTime startTime) {
        return new MessageGetRequest(-1, null, null, startTime);
    }
    public static MessageGetRequest firstForStartTimeAndSubject(ZonedDateTime startTime, String subject) {
        return new MessageGetRequest(-1, null, subject, startTime);
    }
    public static MessageGetRequest nextForSubject(long sequence, String subject) {
        return new MessageGetRequest(sequence, null, subject, null);
    }

    protected MessageGetRequest(long sequence, String lastBySubject, String nextBySubject, ZonedDateTime startTime) {
        this.sequence = sequence;
        this.lastBySubject = lastBySubject;
        this.nextBySubject = nextBySubject;
        this.startTime = startTime;
    }

    public long getSequence() {
        return sequence;
    }

    @Nullable
    public String getLastBySubject() {
        return lastBySubject;
    }

    @Nullable
    public String getNextBySubject() {
        return nextBySubject;
    }

    public boolean isSequenceOnly() {
        return sequence > 0 && nextBySubject == null;
    }

    public boolean isLastBySubject() {
        return lastBySubject != null;
    }

    public boolean isNextBySubject() {
        return nextBySubject != null;
    }

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
}
