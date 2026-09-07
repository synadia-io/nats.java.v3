package io.synadia.client.impl;

import io.synadia.client.utils.NatsConstants;

/**
 * Constants of the JetStream protocol: API subject templates, publish and schedule header names, limits and server error codes.
 */
public interface JetStreamConstants extends NatsConstants {

    /**
     * The Max History Per Key KV key
     */
    int MAX_HISTORY_PER_KEY = 64;

    /** Window the server keeps message ids for duplicate detection when a stream does not set its own. {@value} milliseconds */
    long SERVER_DEFAULT_DUPLICATE_WINDOW_MS = 120_000; // 1000ms/sec * 60sec/min * 2 min

    /** Prefix reserving a metadata key for client library use, so it is not mistaken for user metadata. {@value} */
    String NATS_META_KEY_PREFIX = "_nats.";

    /** Subject prefix reserved by the server for all JetStream subjects. {@value} */
    String PREFIX_DOLLAR_JS_DOT = "$JS.";
    /** Subject token identifying the JetStream API within the JetStream prefix. {@value} */
    String PREFIX_API = "API";
    /** Subject prefix every JetStream API request is sent on unless a custom prefix or domain is configured. {@value} */
    String DEFAULT_API_PREFIX = "$JS.API.";

    /** API subject for obtaining general information about the account's JetStream usage and limits. {@value} */
    String JSAPI_ACCOUNT_INFO = "INFO";

    /** API subject template for creating a consumer on a stream, taking the stream name. {@value} */
    String JSAPI_CONSUMER_CREATE = "CONSUMER.CREATE.%s";

    /** API subject template for creating a durable consumer, taking the stream and durable names. {@value} */
    String JSAPI_DURABLE_CREATE = "CONSUMER.DURABLE.CREATE.%s.%s";

    /** API subject template for creating a named consumer on servers 2.9.0 and later, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_CREATE_V290 = "CONSUMER.CREATE.%s.%s";
    /** API subject template for creating a named consumer with a single filter subject, taking the stream name, consumer name and filter. {@value} */
    String JSAPI_CONSUMER_CREATE_V290_W_FILTER = "CONSUMER.CREATE.%s.%s.%s";

    /** API subject template for reading a consumer's configuration and state, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_INFO = "CONSUMER.INFO.%s.%s";

    /** API subject template for requesting the next message or batch from a pull consumer, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_MSG_NEXT = "CONSUMER.MSG.NEXT.%s.%s";

    /** API subject template for deleting a consumer, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_DELETE = "CONSUMER.DELETE.%s.%s";

    /** API subject template for pausing or resuming a consumer, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_PAUSE = "CONSUMER.PAUSE.%s.%s";

    /** API subject template for listing the names of a stream's consumers, taking the stream name. {@value} */
    String JSAPI_CONSUMER_NAMES = "CONSUMER.NAMES.%s";

    /** API subject template for listing a stream's consumers in full detail, taking the stream name. {@value} */
    String JSAPI_CONSUMER_LIST = "CONSUMER.LIST.%s";

    /** API subject template for creating a stream, taking the stream name. {@value} */
    String JSAPI_STREAM_CREATE = "STREAM.CREATE.%s";

    /** API subject template for reading a stream's configuration and state, taking the stream name. {@value} */
    String JSAPI_STREAM_INFO = "STREAM.INFO.%s";

    /** API subject template for updating an existing stream's configuration, taking the stream name. {@value} */
    String JSAPI_STREAM_UPDATE = "STREAM.UPDATE.%s";

    /** API subject template for deleting a stream and everything in it, taking the stream name. {@value} */
    String JSAPI_STREAM_DELETE = "STREAM.DELETE.%s";

    /** API subject template for purging messages from a stream, taking the stream name. {@value} */
    String JSAPI_STREAM_PURGE = "STREAM.PURGE.%s";

    /** API subject for listing the names of the account's streams. {@value} */
    String JSAPI_STREAM_NAMES = "STREAM.NAMES";

    /** API subject for listing the account's streams in full detail. {@value} */
    String JSAPI_STREAM_LIST = "STREAM.LIST";

    /** API subject template for fetching a stored message through the JetStream API, taking the stream name. {@value} */
    String JSAPI_MSG_GET = "STREAM.MSG.GET.%s";

    /** API subject template for fetching a stored message straight from the stream's leader or a mirror, taking the stream name. {@value} */
    String JSAPI_DIRECT_GET = "DIRECT.GET.%s";

    /** API subject template for directly fetching the last message on a subject, taking the stream name and subject. {@value} */
    String JSAPI_DIRECT_GET_LAST = "DIRECT.GET.%s.%s";

    /** API subject template for deleting or erasing a single stored message, taking the stream name. {@value} */
    String JSAPI_MSG_DELETE = "STREAM.MSG.DELETE.%s";

    /** API subject template for releasing a pinned client from a priority group, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_UNPIN = "CONSUMER.UNPIN.%s.%s";

    /** API subject template for resetting a consumer's delivery state, taking the stream and consumer names. {@value} */
    String JSAPI_CONSUMER_RESET = "CONSUMER.RESET.%s.%s";

    /** Publish header carrying the id the server uses to discard a duplicate within the stream's duplicate window. {@value} */
    String MSG_ID_HDR = "Nats-Msg-Id";
    /** Publish header requiring the message be stored only if it lands in the named stream. {@value} */
    String EXPECTED_STREAM_HDR = "Nats-Expected-Stream";
    /** Publish header requiring the stream's last sequence to equal this value. {@value} */
    String EXPECTED_LAST_SEQ_HDR = "Nats-Expected-Last-Sequence";
    /** Publish header requiring the stream's last message id to equal this value. {@value} */
    String EXPECTED_LAST_MSG_ID_HDR = "Nats-Expected-Last-Msg-Id";
    /** Publish header requiring the last sequence on this message's subject to equal this value. {@value} */
    String EXPECTED_LAST_SUB_SEQ_HDR = "Nats-Expected-Last-Subject-Sequence";
    /** Publish header naming the subject that {@link #EXPECTED_LAST_SUB_SEQ_HDR} applies to. {@value} */
    String EXPECTED_LAST_SUB_SEQ_SUB_HDR = "Nats-Expected-Last-Subject-Sequence-Subject";
    /** Publish header setting how long the server keeps this one message before removing it. {@value} */
    String MSG_TTL_HDR = "Nats-TTL";

    /** Header on a message telling the client which consumer last handled it. {@value} */
    String LAST_CONSUMER_HDR = "Nats-Last-Consumer";
    /** Header on a message telling the client which stream last handled it. {@value} */
    String LAST_STREAM_HDR = "Nats-Last-Stream";

    /** Publish header telling the server to purge older messages once this one is stored. {@value} */
    String ROLLUP_HDR = "Nats-Rollup";
    /** Value for {@link #ROLLUP_HDR} purging only the earlier messages on this message's subject. {@value} */
    String ROLLUP_HDR_SUBJECT = "sub";
    /** Value for {@link #ROLLUP_HDR} purging every earlier message in the stream. {@value} */
    String ROLLUP_HDR_ALL = "all";

    // Schedule Headers set to server
    /** Publish header carrying the cron expression on which the server republishes the message. {@value} */
    String NATS_SCHEDULE_HDR           = "Nats-Schedule";
    /** Publish header naming the subject each scheduled republish is sent to. {@value} */
    String NATS_SCHEDULE_TARGET_HDR    = "Nats-Schedule-Target";
    /** Publish header setting how long each scheduled republish is kept before removal. {@value} */
    String NATS_SCHEDULE_TTL_HDR       = "Nats-Schedule-TTL";
    /** Publish header naming the subject whose latest message supplies the scheduled payload. {@value} */
    String NATS_SCHEDULE_SOURCE_HDR    = "Nats-Schedule-Source";
    /** Publish header giving the rollup applied to each scheduled republish, as in {@link #ROLLUP_HDR}. {@value} */
    String NATS_SCHEDULE_ROLLUP_HDR    = "Nats-Schedule-Rollup";
    /** Publish header giving the time zone the schedule's cron expression is evaluated in. {@value} */
    String NATS_SCHEDULE_TIME_ZONE_HDR = "Nats-Schedule-Time-Zone";

    /** Header the server sets on a republished message naming the schedule that produced it. {@value} */
    String NATS_SCHEDULER_HDR          = "Nats-Scheduler";
    /** Header the server sets on a republished message giving the time of the next scheduled run. {@value} */
    String NATS_SCHEDULE_NEXT_HDR      = "Nats-Schedule-Next";

    /** Publish header grouping messages into an atomic batch, all stored together or not at all. {@value} */
    String NATS_BATCH_ID_HDR        = "Nats-Batch-Id";
    /** Publish header giving this message's position within its batch. {@value} */
    String NATS_BATCH_SEQUENCE_HDR  = "Nats-Batch-Sequence";
    /** Publish header marking the last message of a batch, telling the server to commit it. {@value} */
    String NATS_BATCH_COMMIT_HDR    = "Nats-Batch-Commit";

    /**
     * Value for {@link #NATS_BATCH_COMMIT_HDR} committing the batch and storing the final message.
     * Presence of the header marks the commit message and the value selects the mode. This is the
     * original 2.12 value, a boolean true, from before {@link #NATS_BATCH_COMMIT_EOB} existed. {@value}
     */
    String NATS_BATCH_COMMIT_STORE = "1";
    /** Value for {@link #NATS_BATCH_COMMIT_HDR} committing the batch without storing the final message, server 2.14 and later. {@value} */
    String NATS_BATCH_COMMIT_EOB = "eob";

    /**
     * Last token of the reply subject that carries a fast ingest batch's control state,
     * {@code <prefix>.<batch-id>.<initial-flow>.<gap-mode>.<batch-sequence>.<operation>.$FI}.
     * The server parses that subject right to left, so the prefix may itself contain dots. {@value}
     */
    String FAST_BATCH_SUFFIX   = "$FI";
    /** Gap mode in a fast ingest reply subject telling the server to report lost messages and carry on. {@value} */
    String FAST_BATCH_GAP_OK   = "ok";
    /** Gap mode in a fast ingest reply subject telling the server to abandon the batch on the first lost message. {@value} */
    String FAST_BATCH_GAP_FAIL = "fail";

    /** Fast ingest subject token starting a batch, always with batch sequence 1. {@value} */
    String FAST_BATCH_OP_START      = "0";
    /** Fast ingest subject token appending to an already started batch. {@value} */
    String FAST_BATCH_OP_APPEND     = "1";
    /** Fast ingest subject token committing the batch and storing the final message. {@value} */
    String FAST_BATCH_OP_COMMIT     = "2";
    /** Fast ingest subject token committing the batch without storing the final message. {@value} */
    String FAST_BATCH_OP_COMMIT_EOB = "3";
    /** Fast ingest subject token keeping the batch alive and asking the server to resend its flow control state. {@value} */
    String FAST_BATCH_OP_PING       = "4";

    /**
     * Value of the {@code type} field marking a fast ingest flow control acknowledgement.
     * A publish ack carries no {@code type} field, which is what tells the two apart. {@value}
     */
    String FAST_BATCH_TYPE_ACK = "ack";
    /** Value of the {@code type} field marking a fast ingest report of lost messages. {@value} */
    String FAST_BATCH_TYPE_GAP = "gap";
    /** Value of the {@code type} field marking a fast ingest report of a failed per message check. {@value} */
    String FAST_BATCH_TYPE_ERR = "err";

    /** Header carrying the id identifying the client currently pinned to a consumer's priority group. {@value} */
    String NATS_PIN_ID_HDR = "Nats-Pin-Id";

    /** Server error code reported when the named consumer does not exist. {@value} */
    int JS_CONSUMER_NOT_FOUND_ERR = 10014;
    /** Server error code reported when no message matched the get request. {@value} */
    int JS_NO_MESSAGE_FOUND_ERR = 10037;
    /** Server error code reported when a publish failed its expected last sequence check. {@value} */
    int JS_WRONG_LAST_SEQUENCE = 10071;
    /** Server error code reported when the server cannot yet resolve the sequence and the request may be retried. {@value} */
    int JS_SEQUENCE_TEMPORARILY_UNKNOWN = 10164;

    /** Server error code reported when the stream does not have atomic batch publish enabled. {@value} */
    int JS_ATOMIC_PUBLISH_DISABLED             = 10174;
    /** Server error code reported when an atomic batch message has no batch sequence header. {@value} */
    int JS_ATOMIC_PUBLISH_MISSING_SEQ          = 10175;
    /** Server error code reported when an atomic batch was abandoned before it could be committed. {@value} */
    int JS_ATOMIC_PUBLISH_INCOMPLETE_BATCH     = 10176;
    /** Server error code reported when an atomic batch message used a header the feature does not support. {@value} */
    int JS_ATOMIC_PUBLISH_UNSUPPORTED_HEADER   = 10177;
    /** Server error code reported when an atomic batch id is missing or longer than 64 characters. {@value} */
    int JS_ATOMIC_PUBLISH_INVALID_BATCH_ID     = 10179;
    /** Server error code reported when a stream config tries to enable atomic batch publish on a mirror. {@value} */
    int JS_MIRROR_WITH_ATOMIC_PUBLISH          = 10198;
    /** Server error code reported when an atomic batch holds more messages than the server allows. {@value} */
    int JS_ATOMIC_PUBLISH_TOO_LARGE_BATCH      = 10199;
    /** Server error code reported when the batch commit header value is neither {@value #NATS_BATCH_COMMIT_STORE} nor {@value #NATS_BATCH_COMMIT_EOB}. {@value} */
    int JS_ATOMIC_PUBLISH_INVALID_BATCH_COMMIT = 10200;
    /** Server error code reported when an atomic batch contains two messages with the same message id. {@value} */
    int JS_ATOMIC_PUBLISH_DUPLICATE_MESSAGE    = 10201;
    /** Server error code reported when the stream or server already has as many atomic batches in flight as it allows. {@value} */
    int JS_ATOMIC_PUBLISH_TOO_MANY_INFLIGHT    = 10210;

    /** Server error code reported when the stream does not have fast ingest batch publish enabled. {@value} */
    int JS_BATCH_PUBLISH_DISABLED              = 10205;
    /** Server error code reported when a fast ingest reply subject does not match the expected pattern. {@value} */
    int JS_BATCH_PUBLISH_INVALID_PATTERN       = 10206;
    /** Server error code reported when a fast ingest batch id is missing or longer than 64 characters. {@value} */
    int JS_BATCH_PUBLISH_INVALID_BATCH_ID      = 10207;
    /** Server error code reported when a fast ingest message names a batch the server does not know. {@value} */
    int JS_BATCH_PUBLISH_UNKNOWN_BATCH_ID      = 10208;
    /** Server error code reported when a stream config tries to enable fast ingest batch publish on a mirror. {@value} */
    int JS_MIRROR_WITH_BATCH_PUBLISH           = 10209;
    /** Server error code reported when the stream or server already has as many fast ingest batches in flight as it allows. {@value} */
    int JS_BATCH_PUBLISH_TOO_MANY_INFLIGHT     = 10211;
}
