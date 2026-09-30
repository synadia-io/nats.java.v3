package io.synadia.client.os;

import io.synadia.client.impl.Headers;

import static io.nats.json.Encoding.base64BasicEncodeToString;
import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR;
import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR_SUBJECT;
import static io.synadia.client.utils.NatsConstants.DOT;

/**
 * Naming and header helpers for the Object Store feature. An object store bucket is a JetStream stream named
 * {@code OBJ_&lt;bucket&gt;}; each object is split across a metadata subject ({@code $O.&lt;bucket&gt;.M.&lt;name&gt;})
 * and one or more chunk subjects ({@code $O.&lt;bucket&gt;.C.&lt;nuid&gt;}). Object names are base64 encoded into the
 * subject, since they may contain characters a subject token cannot.
 */
public abstract class ObjectStoreUtil {

    private ObjectStoreUtil() {} /* ensures cannot be constructed */

    /** Default size in bytes of each chunk an object is split into. {@value} */
    public static final int DEFAULT_CHUNK_SIZE = 128 * 1024; // 128k

    /** Prefix of the stream name backing an object store bucket. {@value} */
    public static final String OBJ_STREAM_PREFIX = "OBJ_";

    /** Length of {@link #OBJ_STREAM_PREFIX}. */
    public static final int OBJ_STREAM_PREFIX_LEN = OBJ_STREAM_PREFIX.length();

    /** Prefix of the subjects an object store bucket uses. {@value} */
    public static final String OBJ_SUBJECT_PREFIX = "$O.";

    /** Suffix making a bucket's subject a wildcard. {@value} */
    public static final String OBJ_SUBJECT_SUFFIX = ".>";

    /** Subject token marking the metadata part of an object. {@value} */
    public static final String OBJ_META_PART = ".M";

    /** Subject token marking the chunk part of an object. {@value} */
    public static final String OBJ_CHUNK_PART = ".C";

    /**
     * Get the bucket name out of the name of the stream backing it.
     * @param streamName the stream name, expected to start with {@link #OBJ_STREAM_PREFIX}
     * @return the bucket name
     */
    public static String extractBucketName(String streamName) {
        return streamName.substring(OBJ_STREAM_PREFIX_LEN);
    }

    /**
     * Get the name of the stream that backs a bucket.
     * @param bucketName the bucket name
     * @return the stream name
     */
    public static String toStreamName(String bucketName) {
        return OBJ_STREAM_PREFIX + bucketName;
    }

    /**
     * Get the wildcard subject covering every object's metadata in a bucket.
     * @param bucketName the bucket name
     * @return the subject
     */
    public static String toMetaStreamSubject(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_META_PART + OBJ_SUBJECT_SUFFIX;
    }

    /**
     * Get the wildcard subject covering every object's chunks in a bucket.
     * @param bucketName the bucket name
     * @return the subject
     */
    public static String toChunkStreamSubject(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_CHUNK_PART + OBJ_SUBJECT_SUFFIX;
    }

    /**
     * Get the subject prefix an object's encoded name is appended to for metadata.
     * @param bucketName the bucket name
     * @return the metadata prefix
     */
    public static String toMetaPrefix(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_META_PART + DOT;
    }

    /**
     * Get the subject prefix an object's chunk id is appended to.
     * @param bucketName the bucket name
     * @return the chunk prefix
     */
    public static String toChunkPrefix(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_CHUNK_PART + DOT;
    }

    /**
     * Base64 encode an object name so it is safe to use as a subject token.
     * @param name the object name
     * @return the encoded name
     */
    public static String encodeForSubject(String name) {
        return base64BasicEncodeToString(name);
    }

    /**
     * Build the headers for an object's metadata message, which roll up the subject so only the
     * latest metadata for that object is retained.
     * @return the headers
     */
    public static Headers getMetaHeaders() {
        return new Headers()
            .put(ROLLUP_HDR, ROLLUP_HDR_SUBJECT);
    }
}
