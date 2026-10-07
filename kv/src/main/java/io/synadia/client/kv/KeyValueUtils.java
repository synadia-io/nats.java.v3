package io.synadia.client.kv;

import io.synadia.client.Message;
import io.synadia.client.api.MessageTtl;
import io.synadia.client.impl.Headers;
import io.synadia.client.impl.JetStreamConstants;
import io.synadia.client.impl.PublishOptions;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.synadia.client.impl.JetStreamConstants.*;
import static io.synadia.client.utils.NatsConstants.DOT;

/**
 * Naming and header helpers for the Key Value feature. A KV bucket is stored as a JetStream stream named
 * {@code KV_&lt;bucket&gt;} whose subjects are {@code $KV.&lt;bucket&gt;.&lt;key&gt;}; these helpers convert between
 * bucket names, stream names and subjects, and build the headers that mark delete and purge operations.
 */
public abstract class KeyValueUtils {

    private KeyValueUtils() {} /* ensures cannot be constructed */

    /** The most history a KV bucket keeps per key. {@value} */
    public static final int MAX_HISTORY_PER_KEY = 64;

    /** Prefix of the stream name backing a KV bucket. {@value} */
    public static final String KV_STREAM_PREFIX = "KV_";

    /** Length of {@link #KV_STREAM_PREFIX}. */
    public static final int KV_STREAM_PREFIX_LEN = KV_STREAM_PREFIX.length();

    /** Prefix of the subjects a KV bucket stores its keys under. {@value} */
    public static final String KV_SUBJECT_PREFIX = "$KV.";

    /** Suffix making a bucket's subject a wildcard over all its keys. {@value} */
    public static final String KV_SUBJECT_SUFFIX = ".>";

    /** Header key carrying the KV operation (delete, purge) on a message. */
    public static final String KV_OPERATION_HEADER_KEY = JetStreamConstants.KV_OPERATION_HEADER_KEY;

    /**
     * Get the bucket name out of the name of the stream backing it.
     * @param streamName the stream name, expected to start with {@link #KV_STREAM_PREFIX}
     * @return the bucket name
     */
    @NonNull
    public static String extractBucketName(String streamName) {
        return streamName.substring(KV_STREAM_PREFIX_LEN);
    }

    /**
     * Get the name of the stream that backs a bucket.
     * @param bucketName the bucket name
     * @return the stream name
     */
    @NonNull
    public static String toStreamName(String bucketName) {
        return KV_STREAM_PREFIX + bucketName;
    }

    /**
     * Get the wildcard subject covering every key in a bucket.
     * @param bucketName the bucket name
     * @return the subject
     */
    @NonNull
    public static String toStreamSubject(String bucketName) {
        return KV_SUBJECT_PREFIX + bucketName + KV_SUBJECT_SUFFIX;
    }

    /**
     * Get the subject prefix a bucket's keys are appended to.
     * @param bucketName the bucket name
     * @return the key prefix
     */
    @NonNull
    public static String toKeyPrefix(String bucketName) {
        return KV_SUBJECT_PREFIX + bucketName + DOT;
    }

    /**
     * Whether a name already carries the {@link #KV_STREAM_PREFIX}.
     * @param bucketName the name to check
     * @return true if the name is prefixed
     */
    public static boolean hasPrefix(String bucketName) {
        return bucketName.startsWith(KV_STREAM_PREFIX);
    }

    /**
     * Remove the {@link #KV_STREAM_PREFIX} from a name if present, otherwise return it unchanged.
     * @param bucketName the name to trim
     * @return the name without the prefix
     */
    @NonNull
    public static String trimPrefix(String bucketName) {
        if (bucketName.startsWith(KV_STREAM_PREFIX)) {
            return bucketName.substring(KV_STREAM_PREFIX.length());
        }
        return bucketName;
    }

    /**
     * Read the KV operation header from a message's headers.
     * @param h the headers, may be null
     * @return the header value, or null if absent
     */
    @Nullable
    public static String getOperationHeader(Headers h) {
        return h == null ? null : h.getFirst(KV_OPERATION_HEADER_KEY);
    }

    /**
     * Read the server marker reason header, which the server sets on messages it removes itself,
     * for instance when a per-message TTL expires.
     * @param h the headers, may be null
     * @return the header value, or null if absent
     */
    @Nullable
    public static String getNatsMarkerReasonHeader(Headers h) {
        return h == null ? null : h.getFirst(NATS_MARKER_REASON_HDR);
    }

    /**
     * Determine the operation a message represents, preferring the KV operation header and falling back to
     * the server marker reason. Messages with neither are a PUT.
     * @param h the headers, may be null
     * @return the operation, never null
     */
    @NonNull
    public static KeyValueOperation getOperation(Headers h) {
        KeyValueOperation kvo = null;
        String hs = getOperationHeader(h);
        if (hs != null) {
            kvo = KeyValueOperation.instance(hs);
        }
        if (kvo == null) {
            hs = getNatsMarkerReasonHeader(h);
            if (hs != null) {
                kvo = KeyValueOperation.instanceByMarkerReason(hs);
            }
        }
        return kvo == null ? KeyValueOperation.PUT : kvo;
    }

    /**
     * Build the headers that mark a message as a key delete.
     * @return the headers
     */
    @NonNull
    public static Headers getDeleteHeaders() {
        return new Headers()
            .put(KV_OPERATION_HEADER_KEY, KeyValueOperation.DELETE.getHeaderValue());
    }

    /**
     * Build the headers that mark a message as a key purge, which also rolls up the key's history.
     * @return the headers
     */
    @NonNull
    public static Headers getPurgeHeaders() {
        return new Headers()
            .put(KV_OPERATION_HEADER_KEY, KeyValueOperation.PURGE.getHeaderValue())
            .put(ROLLUP_HDR, ROLLUP_HDR_SUBJECT);
    }

    /**
     * Build publish options for a KV write, or null when neither constraint applies.
     * @param expectedRevision the revision the key must currently be at, or -1 for no expectation
     * @param messageTtl the per-message TTL, or null for the bucket default
     * @return the options, or null if no options are needed
     */
    @Nullable
    public static PublishOptions getPublishOptions(long expectedRevision, MessageTtl messageTtl) {
        boolean returnNull = true;
        PublishOptions.Builder b = PublishOptions.builder();
        if (expectedRevision > -1) {
            returnNull = false;
            b.expectedLastSubjectSequence(expectedRevision);
        }
        if (messageTtl != null) {
            returnNull = false;
            b.messageTtl(messageTtl);
        }
        return returnNull ? null : b.build();
    }

    /**
     * The bucket and key parsed out of a KV message subject.
     */
    public static class BucketAndKey {
        /** The bucket name. */
        public final String bucket;
        /** The key within the bucket. */
        public final String key;

        /**
         * Parse the bucket and key from a message's subject.
         * @param m the message
         */
        public BucketAndKey(Message m) {
            this(m.getSubject());
        }

        /**
         * Parse the bucket and key from a KV subject.
         * @param subject the subject, of the form {@code $KV.bucket.key}
         */
        public BucketAndKey(String subject) {
            String[] split = subject.split("\\Q.\\E", 3);
            bucket = split[1];
            key = split[2];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BucketAndKey that = (BucketAndKey) o;

            if (!bucket.equals(that.bucket)) return false;
            return key.equals(that.key);
        }

        @Override
        public int hashCode() {
            int result = bucket.hashCode();
            result = 31 * result + key.hashCode();
            return result;
        }
    }
}
