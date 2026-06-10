package io.synadia.client.impl;

import io.nats.json.DateTimeUtils;
import io.nats.json.LazyJsonValue;
import io.synadia.client.Message;
import io.synadia.client.api.ApiResponse;
import io.synadia.client.api.Status;
import io.synadia.client.utils.IncomingHeadersProcessor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.time.ZonedDateTime;

import static io.nats.json.JsonWriteUtils.*;
import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.impl.JetStreamConstants.*;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * The MessageInfo class contains information about a JetStream message.
 */
@NullMarked
public class MessageInfo extends ApiResponse<MessageInfo> {

    private final @Nullable String subject;
    private final long seq;
    private final byte @Nullable[] data;
    private final @Nullable ZonedDateTime time;
    private final @Nullable Headers headers;
    private final @Nullable String stream;
    private final long lastSeq;
    private final long numPending;
    private final @Nullable Status status;

    /**
     * Create a Message Info
     * @param msg the message
     * @param streamName the stream name if known
     * @param direct true if the object is being created from a direct api call instead of get message
     */
    public MessageInfo(Message msg, String streamName, boolean direct) {
        super(direct ? null : msg);
        this.status = null;

        // working vars because the object vars are final
        String _subject = null;
        long _seq = -1;
        //noinspection DataFlowIssue the ide is wrong in flagging this, _data can be null
        byte[] _data = null;
        ZonedDateTime _time = null;
        Headers _headers = null;
        String _stream = streamName;
        long _lastSeq = -1;
        long _numPending = -1;

        if (direct) {
            _data = msg.getData();
            Headers msgHeaders = msg.getHeaders();
            if (msgHeaders == null) {
                _headers = new Headers(null, true);
            }
            else {
                _subject = msgHeaders.getLast(NATS_SUBJECT);
                _stream = msgHeaders.getLast(NATS_STREAM);
                String temp = msgHeaders.getLast(NATS_SEQUENCE);
                if (temp != null) {
                    // safeParseLong will handle it this happens to be in the unsigned range
                    _seq = safeParseLong(temp, -1);
                }
                temp = msgHeaders.getLast(NATS_LAST_SEQUENCE);
                if (temp != null) {
                    // safeParseLong will handle it this happens to be in the unsigned range
                    _lastSeq = safeParseLong(temp, -1);
                }
                temp = msgHeaders.getLast(NATS_NUM_PENDING);
                if (temp != null) {
                    _numPending = safeParseLong(temp, 0) - 1;
                }
                temp = msgHeaders.getLast(NATS_TIMESTAMP);
                if (temp != null) {
                    _time = DateTimeUtils.parseDateTime(temp);
                }

                // these are control headers, not real headers so don't give them to the user. Must be done last
                _headers = new Headers(msgHeaders, true, MESSAGE_INFO_HEADERS);
            }
        }
        else if (!hasError()) {
            LazyJsonValue mjv = readValue(ljv, MESSAGE);
            _subject = readString(mjv, SUBJECT);
            //noinspection DataFlowIssue the ide is wrong in flagging this, _data can be null
            _data = readBase64Basic(mjv, DATA);
            _seq = readUnsignedLong(mjv, SEQ, 0);
            _time = readDate(mjv, TIME);
            //noinspection DataFlowIssue the ide is wrong in flagging this, hdrBytes can be null
            byte[] hdrBytes = readBase64Basic(mjv, HDRS);
            _headers = hdrBytes == null ? null : new IncomingHeadersProcessor(hdrBytes).getHeaders();
        }

        this.subject = _subject;
        this.data = _data;
        this.seq = _seq;
        this.time = _time;
        this.headers = _headers;
        this.stream = _stream;
        this.lastSeq = _lastSeq;
        this.numPending = _numPending;
    }

    /**
     * Create a Message Info
     * @param status     the status
     * @param streamName the stream name if known
     */
    public MessageInfo(Status status, String streamName) {
        super((LazyJsonValue)null);
        this.status = status;
        this.stream = streamName;
        subject = null;
        seq = -1;
        data = null;
        time = null;
        headers = null;
        lastSeq = -1;
        numPending = -1;
    }

    /**
     * Get the message subject
     * @return the subject
     */
    @Nullable
    public String getSubject() {
        return subject;
    }

    /**
     * Get the message sequence.
     * <p>The server value is an unsigned 64-bit number.
     * @return the sequence number
     */
    public long getSequence() {
        return seq;
    }

    /**
     * Get the message sequence as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getSequence()}.
     * @return the sequence number, or {@code -1} if not known
     */
    public BigInteger getSequenceAsBigInteger() {
        return asUnsignedBigInteger(seq);
    }

    /**
     * Get the message data
     * @return the data bytes
     */
    public byte @Nullable [] getData() {
        return data;
    }

    /**
     * Get the time the message was received
     * @return the time
     */
    @Nullable
    public ZonedDateTime getTime() {
        return time;
    }

    /**
     * Get the headers
     * @return the headers object or null if there were no headers
     */
    @Nullable
    public Headers getHeaders() {
        return headers;
    }

    /**
     * Get the name of the stream. Not always set.
     * @return the stream name or null if the name is not known.
     */
    @Nullable
    public String getStream() {
        return stream;
    }

    /**
     * Get the sequence number of the last message in the stream. Not always set.
     * <p>The server value is an unsigned 64-bit number.
     * @return the last sequence or -1 if the value is not known.
     */
    public long getLastSequence() {
        return lastSeq;
    }

    /**
     * Get the sequence number of the last message in the stream as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getLastSequence()}.
     * @return the last sequence, or {@code -1} if not known
     */
    public BigInteger getLastSequenceAsBigInteger() {
        return asUnsignedBigInteger(lastSeq);
    }

    /**
     * Amount of pending messages that can be requested with a subsequent batch request.
     * <p>The server value is an unsigned 64-bit number.
     * @return number of pending messages
     */
    public long getNumPending() {
        return numPending;
    }

    /**
     * Amount of pending messages as a non-negative unsigned value.
     * The {@link BigInteger} companion to {@link #getNumPending()}.
     * @return number of pending messages, or {@code -1} if not known
     */
    public BigInteger getNumPendingAsBigInteger() {
        return asUnsignedBigInteger(numPending);
    }

    /**
     * Convert a stored uint64 value to its non-negative unsigned magnitude, preserving the
     * {@code -1} "not known" sentinel that the long getters return when a field was absent.
     */
    private static BigInteger asUnsignedBigInteger(long v) {
        return v == -1 ? BigInteger.valueOf(-1) : new BigInteger(Long.toUnsignedString(v));
    }

    /**
     * Get the Status object. Null if this MessageInfo is not a Status.
     * @return the status object
     */
    @Nullable
    public Status getStatus() {
        return status;
    }

    /**
     * Whether this MessageInfo is a regular message
     * @return true if the MessageInfo is a regular message
     */
    public boolean isMessage() {
        return status == null && !hasError();
    }

    /**
     * Whether this MessageInfo is a status message
     * @return true if this MessageInfo is a status message
     */
    public boolean isStatus() {
        return status != null;
    }

    /**
     * Whether this MessageInfo is a status message and is a direct EOB status
     * @return true if this MessageInfo is a status message and is a direct EOB status
     */
    public boolean isEobStatus() {
        return status != null && status.isEob();
    }

    /**
     * Whether this MessageInfo is a status message and is an error status
     * @return true if this MessageInfo is a status message and is an error status
     */
    public boolean isErrorStatus() {
        return status != null && !status.isEob();
    }

    @Override
    public String toString() {
        StringBuilder sb = beginJsonPrefixed("\"MessageInfo\":");
        if (status != null) {
            addField(sb, "status_code", status.getCode());
            addField(sb, "status_message", status.getMessage());
        }
        else if (hasError()) {
            addField(sb, ERROR, getError());
        }
        else {
            addField(sb, SEQ, seq);
            addField(sb, LAST_SEQ, lastSeq);
            addFieldWhenGteMinusOne(sb, NUM_PENDING, numPending);
            addField(sb, STREAM, stream);
            addField(sb, SUBJECT, subject);
            addField(sb, TIME, time);
            if (data == null) {
                addRawJson(sb, DATA, "null");
            }
            else {
                addField(sb, "data_length", data.length);
            }
            if (headers != null && headers.size() > 0) { addField(sb, HDRS, headers.toMap()); }
        }
        return endJson(sb).toString();
    }
}
