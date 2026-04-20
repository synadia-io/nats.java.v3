package io.synadia.client.testutils;

import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

public interface NatsConstants {
    String HEADER_VERSION = "NATS/1.0";

    int DEFAULT_PORT = 4222;

    String NATS_PROTOCOL = "nats";
    String TLS_PROTOCOL = "tls";
    String OPENTLS_PROTOCOL = "opentls";
    String WEBSOCKET_PROTOCOL = "ws";
    String SECURE_WEBSOCKET_PROTOCOL = "wss";
    String NATS_PROTOCOL_SLASH_SLASH = "nats://";

    List<String> KNOWN_PROTOCOLS = Arrays.asList(NATS_PROTOCOL, TLS_PROTOCOL, OPENTLS_PROTOCOL, WEBSOCKET_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);
    List<String> SECURE_PROTOCOLS = Arrays.asList(TLS_PROTOCOL, OPENTLS_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);
    List<String> WEBSOCKET_PROTOCOLS = Arrays.asList(WEBSOCKET_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);

    String SPACE = " ";
    String CRLF = "\r\n";
    String DOT = ".";
    String GREATER_THAN = ">";
    String STAR = "*";

    byte TAB = '\t';
    byte SP = ' ';
    byte COLON = ':';
    byte CR = '\r';
    byte LF = '\n';

    byte[] EMPTY_BODY = new byte[0];
    byte[] HEADER_VERSION_BYTES = HEADER_VERSION.getBytes(ISO_8859_1);
    byte[] HEADER_VERSION_BYTES_PLUS_CRLF = (HEADER_VERSION + "\r\n").getBytes(ISO_8859_1);
    byte[] COLON_BYTES = ":".getBytes(ISO_8859_1);
    byte[] CRLF_BYTES = CRLF.getBytes(ISO_8859_1);
    int HEADER_VERSION_BYTES_LEN = HEADER_VERSION_BYTES.length;

    String OP_CONNECT = "CONNECT";
    String OP_INFO = "INFO";
    String OP_SUB = "SUB";
    String OP_PUB = "PUB";
    String OP_HPUB = "HPUB";
    String OP_UNSUB = "UNSUB";
    String OP_MSG = "MSG";
    String OP_HMSG = "HMSG";
    String OP_PING = "PING";
    String OP_PONG = "PONG";
    String OP_OK = "+OK";
    String OP_ERR = "-ERR";
    String UNKNOWN_OP = "UNKNOWN";

    byte[] OP_PING_BYTES = OP_PING.getBytes();
    byte[] OP_PONG_BYTES = OP_PONG.getBytes();

    byte[] PUB_SP_BYTES = (OP_PUB + SPACE).getBytes(ISO_8859_1);
    byte[] HPUB_SP_BYTES = (OP_HPUB + SPACE).getBytes(ISO_8859_1);
    byte[] CONNECT_SP_BYTES = (OP_CONNECT + SPACE).getBytes();
    byte[] SUB_SP_BYTES = (OP_SUB + SPACE).getBytes();
    byte[] UNSUB_SP_BYTES = (OP_UNSUB + SPACE).getBytes();

    int PUB_SP_BYTES_LEN = PUB_SP_BYTES.length;
    int HPUB_SP_BYTES_LEN = HPUB_SP_BYTES.length;
    int OP_CONNECT_SP_LEN = CONNECT_SP_BYTES.length;
    int OP_SUB_SP_LEN = SUB_SP_BYTES.length;
    int OP_UNSUB_SP_LEN = UNSUB_SP_BYTES.length;

    int MAX_PROTOCOL_RECEIVE_OP_LENGTH = 4;

    String INVALID_HEADER_VERSION = "Invalid header version";
    String INVALID_HEADER_COMPOSITION = "Invalid header composition";
    String INVALID_HEADER_STATUS_CODE = "Invalid header status code";
    String SERIALIZED_HEADER_CANNOT_BE_NULL_OR_EMPTY = "Serialized header cannot be null or empty.";

    // The trailing space is intentional as in "Output queue is full 5000"
    String OUTPUT_QUEUE_IS_FULL = "Output queue is full ";
    String OUTPUT_QUEUE_BUSY = "Output queue is busy ";

    long NANOS_PER_MILLI = 1_000_000L;

    String UNDEFINED = "UNDEFINED";

    // JetStream Specific

    String CONSUMER_STALLED_HDR         = "Nats-Consumer-Stalled";
    String MSG_SIZE_HDR                 = "Nats-Msg-Size";
    String NATS_MARKER_REASON_HDR       = "Nats-Marker-Reason";
    byte[] CONSUMER_STALLED_HDR_BYTES   = CONSUMER_STALLED_HDR.getBytes(ISO_8859_1);
    byte[] MSG_SIZE_HDR_BYTES           = MSG_SIZE_HDR.getBytes(ISO_8859_1);
    byte[] NATS_MARKER_REASON_HDR_BYTES = NATS_MARKER_REASON_HDR.getBytes(ISO_8859_1);

    String NATS_STREAM        = "Nats-Stream";
    String NATS_SEQUENCE      = "Nats-Sequence";
    String NATS_TIMESTAMP     = "Nats-Time-Stamp";
    String NATS_SUBJECT       = "Nats-Subject";
    String NATS_LAST_SEQUENCE = "Nats-Last-Sequence";
    String NATS_NUM_PENDING   = "Nats-Num-Pending";
    String[] MESSAGE_INFO_HEADERS = new String[]{NATS_SUBJECT, NATS_SEQUENCE, NATS_TIMESTAMP, NATS_STREAM, NATS_LAST_SEQUENCE, NATS_NUM_PENDING};

    // bytes used for faster matching and less string allocation when
    byte[] NATS_STREAM_BYTES = NATS_STREAM.getBytes(ISO_8859_1);
    byte[] NATS_SEQUENCE_BYTES = NATS_SEQUENCE.getBytes(ISO_8859_1);
    byte[] NATS_TIMESTAMP_BYTES = NATS_TIMESTAMP.getBytes(ISO_8859_1);
    byte[] NATS_SUBJECT_BYTES = NATS_SUBJECT.getBytes(ISO_8859_1);
    byte[] NATS_LAST_SEQUENCE_BYTES = NATS_LAST_SEQUENCE.getBytes(ISO_8859_1);
    byte[] NATS_NUM_PENDING_BYTES = NATS_NUM_PENDING.getBytes(ISO_8859_1);

    String NATS_PENDING_MESSAGES       = "Nats-Pending-Messages";
    String NATS_PENDING_BYTES          = "Nats-Pending-Bytes";
    byte[] NATS_PENDING_MESSAGES_BYTES = NATS_PENDING_MESSAGES.getBytes(ISO_8859_1);
    byte[] NATS_PENDING_BYTES_BYTES    = NATS_PENDING_BYTES.getBytes(ISO_8859_1);

    String JS_ACK_SUBJECT_PREFIX = "$JS.ACK.";

    String KV_OPERATION_HEADER_KEY       = "KV-Operation";
    byte[] KV_OPERATION_HEADER_KEY_BYTES = KV_OPERATION_HEADER_KEY.getBytes(ISO_8859_1);
}
