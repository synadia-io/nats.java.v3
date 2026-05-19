package io.synadia.client.os;

import io.synadia.client.impl.Headers;

import static io.nats.json.Encoding.base64BasicEncodeToString;
import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR;
import static io.synadia.client.impl.JetStreamConstants.ROLLUP_HDR_SUBJECT;
import static io.synadia.client.utils.NatsConstants.DOT;

public abstract class ObjectStoreUtil {

    private ObjectStoreUtil() {} /* ensures cannot be constructed */

    public static final int DEFAULT_CHUNK_SIZE = 128 * 1024; // 128k
    public static final String OBJ_STREAM_PREFIX = "OBJ_";
    public static final int OBJ_STREAM_PREFIX_LEN = OBJ_STREAM_PREFIX.length();
    public static final String OBJ_SUBJECT_PREFIX = "$O.";
    public static final String OBJ_SUBJECT_SUFFIX = ".>";
    public static final String OBJ_META_PART = ".M";
    public static final String OBJ_CHUNK_PART = ".C";

    public static String extractBucketName(String streamName) {
        return streamName.substring(OBJ_STREAM_PREFIX_LEN);
    }

    public static String toStreamName(String bucketName) {
        return OBJ_STREAM_PREFIX + bucketName;
    }

    public static String toMetaStreamSubject(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_META_PART + OBJ_SUBJECT_SUFFIX;
    }

    public static String toChunkStreamSubject(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_CHUNK_PART + OBJ_SUBJECT_SUFFIX;
    }

    public static String toMetaPrefix(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_META_PART + DOT;
    }

    public static String toChunkPrefix(String bucketName) {
        return OBJ_SUBJECT_PREFIX + bucketName + OBJ_CHUNK_PART + DOT;
    }

    public static String encodeForSubject(String name) {
        return base64BasicEncodeToString(name);
    }

    public static Headers getMetaHeaders() {
        return new Headers()
            .put(ROLLUP_HDR, ROLLUP_HDR_SUBJECT);
    }
}
