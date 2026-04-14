package io.synadia.client.kv;

import io.nats.json.MapBuilder;
import io.synadia.client.Message;
import io.synadia.client.impl.Headers;
import io.synadia.client.jsapi.MessageInfo;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

import static io.synadia.client.kv.KeyValueUtils.BucketAndKey;
import static io.synadia.client.support.JetStreamConstants.MSG_SIZE_HDR;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The KeyValueEntry represents a record in the Key Value history
 */
@NullMarked
public class KeyValueEntry {

    private final BucketAndKey bucketAndKey;
    private final byte @Nullable [] value;
    private final long dataLen;
    private final ZonedDateTime created;
    private final long revision;
    private final long delta;
    private final KeyValueOperation op;

    /**
     * Construct KeyValueEntry from message info
     * @param mi the message info
     */
    public KeyValueEntry(MessageInfo mi) {
        Headers h = mi.getHeaders();
        if (mi.getSubject() == null || mi.getTime() == null) {
            throw new IllegalStateException("Invalid Message Info for a Key Value Entry");
        }
        bucketAndKey = new BucketAndKey(mi.getSubject());
        value = extractValue(mi.getData());
        dataLen = calculateLength(value, h);
        created = mi.getTime();
        revision = mi.getSequence();
        delta = 0;
        op = KeyValueUtils.getOperation(h);
    }

    /**
     * Construct KeyValueEntry from a message
     * @param m the message
     */
    public KeyValueEntry(Message m) {
        Headers h = m.getHeaders();
        bucketAndKey = new BucketAndKey(m.getSubject());
        value = extractValue(m.getData());
        dataLen = calculateLength(value, h);
        created = m.metaData().timestamp();
        revision = m.metaData().streamSequence();
        delta = m.metaData().pendingCount();
        op = KeyValueUtils.getOperation(h);
    }

    /**
     * Get the key value bucket this key in.
     * @return the bucket
     */
    public String getBucket() {
        return bucketAndKey.bucket;
    }

    /**
     * Get the key
     * @return the key
     */
    public String getKey() {
        return bucketAndKey.key;
    }

    /**
     * Get the value. May be null
     * @return the value
     */
    public byte @Nullable [] getValue() {
        return value;
    }

    /**
     * Get the value as a string using UTF-8 encoding
     * @return the value as a string or null if there is no value
     */
    @Nullable
    public String getValueAsString() {
        return value == null ? null : new String(value, UTF_8);
    }

    /**
     * Get the value as a long
     * @return the value or null if there is no value
     * @throws NumberFormatException  if the string does not contain a parsable {@code long}.
     */
    @Nullable
    public Long getValueAsLong() {
        return value == null ? null : Long.parseLong(new String(value, ISO_8859_1));
    }

    /**
     * Get the number of bytes in the data. May be zero
     * @return the number of bytes
     */
    public long getDataLen() {
        return dataLen;
    }

    /**
     * Get the creation time of the current version of the key
     * @return the creation time
     */
    public ZonedDateTime getCreated() {
        return created;
    }

    /**
     * Get the revision number of the string. Not a version, but an internally strictly monotonical value
     * @return the revision
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Internal reference to pending message from the entry request
     * @return the delta
     */
    public long getDelta() {
        return delta;
    }

    /**
     * The KeyValueOperation of this entry
     * @return the operation
     */
    public KeyValueOperation getOperation() {
        return op;
    }

    @Override
    public String toString() {
        return "KeyValueEntry " + new MapBuilder()
            .put("bucket", bucketAndKey.bucket)
            .put("key", bucketAndKey.key)
            .put("operation", op)
            .put("revision", revision)
            .put("delta", delta)
            .put("dataLen", dataLen)
            .put("created", created)
            .toJson();
    }

    private static byte @Nullable [] extractValue(byte @Nullable [] data) {
        return data == null || data.length == 0 ? null : data;
    }

    private static long calculateLength(byte @Nullable [] value, @Nullable Headers h) {
        if (value == null) {
            String hlen = h == null ? null : h.getFirst(MSG_SIZE_HDR);
            return hlen == null ? 0 : Long.parseLong(hlen);
        }
        return value.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KeyValueEntry that = (KeyValueEntry) o;

        return bucketAndKey.equals(that.bucketAndKey)
            && revision == that.revision;
    }

    @Override
    public int hashCode() {
        int result = bucketAndKey.hashCode();
        result = 31 * result + Long.hashCode(revision);
        return result;
    }
}
