package io.synadia.client.api;

import io.nats.json.LazyJsonValue;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.utils.ApiConstants.*;

/**
 * Object representing the state of a stream
 */
@NullMarked
public class StreamState extends LazyApiObject {
    static final StreamState EMPTY;
    static {
        try { EMPTY = new StreamState(io.nats.json.LazyJsonParser.parse("{}")); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private @Nullable List<Subject> _subjects;
    private @Nullable Map<String, Long> _subjectMap;

    StreamState(LazyJsonValue v) {
        super(v);
    }

    /**
     * Gets the message count of the stream.
     * @return the message count
     */
    public long getMessageCount() {
        return readLong(ljv, MESSAGES, 0);
    }

    /**
     * Gets the byte count of the stream.
     * @return the byte count
     */
    public long getByteCount() {
        return readLong(ljv, BYTES, 0);
    }

    /**
     * Gets the first sequence number of the stream. May be 0 if there are no messages.
     * @return a sequence number
     */
    public long getFirstSequence() {
        return readLong(ljv, FIRST_SEQ, 0);
    }

    /**
     * Gets the time stamp of the first message in the stream
     * @return the first time
     */
    @Nullable
    public ZonedDateTime getFirstTime() {
        return readDate(ljv, FIRST_TS);
    }

    /**
     * Gets the last sequence of a message in the stream
     * @return a sequence number
     */
    public long getLastSequence() {
        return readLong(ljv, LAST_SEQ, 0);
    }

    /**
     * Gets the time stamp of the last message in the stream
     * @return the last time
     */
    @Nullable
    public ZonedDateTime getLastTime() {
        return readDate(ljv, LAST_TS);
    }

    /**
     * Gets the number of consumers attached to the stream.
     * @return the consumer count
     */
    public long getConsumerCount() {
        return readLong(ljv, CONSUMER_COUNT, 0);
    }

    /**
     * Gets the count of subjects in the stream.
     * @return the subject count
     */
    public long getSubjectCount() {
        return readLong(ljv, NUM_SUBJECTS, 0);
    }

    /**
     * Get a list of the Subject objects. May be null if the Stream Info request
     * did not ask for subjects or if there are no subjects.
     * @return the list of subjects
     */
    public List<Subject> getSubjects() {
        if (_subjects == null) {
            _subjects = Subject.listOf(readValue(ljv, SUBJECTS));
        }
        return _subjects;
    }

    /**
     * Get a map of subjects instead of a list of Subject objects.
     * @return the map
     */
    public Map<String, Long> getSubjectMap() {
        if (_subjectMap == null) {
            _subjectMap = new HashMap<>();
            LazyJsonValue v = readValue(ljv, SUBJECTS);
            if (v != null && v.getMap() != null) {
                for (Map.Entry<String, LazyJsonValue> entry : v.getMap().entrySet()) {
                    Long count = entry.getValue().getLong();
                    if (count != null) {
                        _subjectMap.put(entry.getKey(), count);
                    }
                }
            }
        }
        return _subjectMap;
    }

    /**
     * Gets the count of deleted messages
     * @return the deleted count
     */
    public long getDeletedCount() {
        return readLong(ljv, NUM_DELETED, 0);
    }

    /**
     * Get a list of deleted sequence numbers. May be null.
     * @return the list of deleted sequences
     */
    public List<Long> getDeleted() {
        return readLongListOrEmpty(ljv, DELETED);
    }

    /**
     * Get the lost stream data information if available.
     * @return the LostStreamData
     */
    @Nullable
    public LostStreamData getLostStreamData() {
        return LostStreamData.optionalInstance(readValue(ljv, LOST));
    }

    @Override
    public String toString() {
        return "StreamState " + ljv.toJson();
    }
}
