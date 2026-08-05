package io.synadia.client.utils;

import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Constants of the NATS wire protocol: operation names, line delimiters, header names, error text and protocol defaults.
 */
public interface NatsConstants {
    /** Version token that must begin every serialized message header block. {@value} */
    String HEADER_VERSION = "NATS/1.0";

    /** Port a server is assumed to listen on when a server URL does not specify one. {@value} */
    int DEFAULT_PORT = 4222;

    /** Server URL scheme for a plain TCP connection. {@value} */
    String NATS_PROTOCOL = "nats";
    /** Server URL scheme requiring the connection be upgraded to TLS. {@value} */
    String TLS_PROTOCOL = "tls";
    /** Server URL scheme for TLS that trusts any server certificate, intended for testing. {@value} */
    String OPENTLS_PROTOCOL = "opentls";
    /** Server URL scheme for a connection tunneled over a plain WebSocket. {@value} */
    String WEBSOCKET_PROTOCOL = "ws";
    /** Server URL scheme for a connection tunneled over a TLS secured WebSocket. {@value} */
    String SECURE_WEBSOCKET_PROTOCOL = "wss";
    /** Scheme prefix prepended to a bare host and port to form a complete server URL. {@value} */
    String NATS_PROTOCOL_SLASH_SLASH = "nats://";

    /** Every URL scheme the client accepts in a server URL. */
    List<String> KNOWN_PROTOCOLS = Arrays.asList(NATS_PROTOCOL, TLS_PROTOCOL, OPENTLS_PROTOCOL, WEBSOCKET_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);
    /** URL schemes that require the connection be encrypted with TLS. */
    List<String> SECURE_PROTOCOLS = Arrays.asList(TLS_PROTOCOL, OPENTLS_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);
    /** URL schemes that require the WebSocket handshake and framing. */
    List<String> WEBSOCKET_PROTOCOLS = Arrays.asList(WEBSOCKET_PROTOCOL, SECURE_WEBSOCKET_PROTOCOL);

    /** Separator between the tokens of a protocol line. {@value} */
    String SPACE = " ";
    /** Terminator ending every protocol line and every message payload. {@value} */
    String CRLF = "\r\n";
    /** Separator between the tokens of a subject. {@value} */
    String DOT = ".";
    /** Subject wildcard matching the remaining tokens of a subject. {@value} */
    String GREATER_THAN = ">";
    /** Subject wildcard matching exactly one token of a subject. {@value} */
    String STAR = "*";

    /** Tab byte, accepted as token whitespace when parsing a protocol line. {@value} */
    byte TAB = '\t';
    /** Space byte, the token separator when parsing a protocol line. {@value} */
    byte SP = ' ';
    /** Colon byte, the separator between a header key and its value. {@value} */
    byte COLON = ':';
    /** Carriage return byte, the first byte of the protocol line terminator. {@value} */
    byte CR = '\r';
    /** Line feed byte, the second byte of the protocol line terminator. {@value} */
    byte LF = '\n';

    /** Shared zero length payload used for messages published or delivered with no body. */
    byte[] EMPTY_BODY = new byte[0];
    /** {@link #HEADER_VERSION} encoded for the wire, matched when parsing an inbound header block. */
    byte[] HEADER_VERSION_BYTES = HEADER_VERSION.getBytes(ISO_8859_1);
    /** {@link #HEADER_VERSION} plus its line terminator, written first when serializing a header block. */
    byte[] HEADER_VERSION_BYTES_PLUS_CRLF = (HEADER_VERSION + "\r\n").getBytes(ISO_8859_1);
    /** {@link #COLON} encoded for the wire, written between a header key and its value. */
    byte[] COLON_BYTES = ":".getBytes(ISO_8859_1);
    /** {@link #CRLF} encoded for the wire, written to end a protocol line. */
    byte[] CRLF_BYTES = CRLF.getBytes(ISO_8859_1);
    /** Length of {@link #HEADER_VERSION_BYTES}, the offset past the version when parsing a header block. */
    int HEADER_VERSION_BYTES_LEN = HEADER_VERSION_BYTES.length;

    /** Op sent by the client to supply its options and credentials right after connecting. {@value} */
    String OP_CONNECT = "CONNECT";
    /** Op sent by the server describing itself and the cluster, on connect and whenever it changes. {@value} */
    String OP_INFO = "INFO";
    /** Op registering a subscription's interest in a subject. {@value} */
    String OP_SUB = "SUB";
    /** Op publishing a message with no headers. {@value} */
    String OP_PUB = "PUB";
    /** Op publishing a message that carries headers. {@value} */
    String OP_HPUB = "HPUB";
    /** Op cancelling a subscription, immediately or after a stated number of further messages. {@value} */
    String OP_UNSUB = "UNSUB";
    /** Op delivering a message with no headers to a subscription. {@value} */
    String OP_MSG = "MSG";
    /** Op delivering a message that carries headers to a subscription. {@value} */
    String OP_HMSG = "HMSG";
    /** Op sent to check the peer is alive; the peer must answer with {@link #OP_PONG}. {@value} */
    String OP_PING = "PING";
    /** Op answering an {@link #OP_PING}. {@value} */
    String OP_PONG = "PONG";
    /** Server acknowledgement that a protocol message was accepted, sent only in verbose mode. {@value} */
    String OP_OK = "+OK";
    /** Server report of a protocol, authorization or permission error. {@value} */
    String OP_ERR = "-ERR";
    /** Stand-in used when an op read from the server is not one the client recognizes. {@value} */
    String UNKNOWN_OP = "UNKNOWN";

    /** {@link #OP_PING} encoded for the wire. */
    byte[] OP_PING_BYTES = OP_PING.getBytes();
    /** {@link #OP_PONG} encoded for the wire. */
    byte[] OP_PONG_BYTES = OP_PONG.getBytes();

    /** {@link #OP_PUB} and its trailing space, which begin every publish protocol line. */
    byte[] PUB_SP_BYTES = (OP_PUB + SPACE).getBytes(ISO_8859_1);
    /** {@link #OP_HPUB} and its trailing space, which begin every publish with headers protocol line. */
    byte[] HPUB_SP_BYTES = (OP_HPUB + SPACE).getBytes(ISO_8859_1);
    /** {@link #OP_CONNECT} and its trailing space, which begin the connect protocol line. */
    byte[] CONNECT_SP_BYTES = (OP_CONNECT + SPACE).getBytes();
    /** {@link #OP_SUB} and its trailing space, which begin every subscribe protocol line. */
    byte[] SUB_SP_BYTES = (OP_SUB + SPACE).getBytes();
    /** {@link #OP_UNSUB} and its trailing space, which begin every unsubscribe protocol line. */
    byte[] UNSUB_SP_BYTES = (OP_UNSUB + SPACE).getBytes();

    /** Length of {@link #PUB_SP_BYTES}, the offset at which a publish line's arguments start. */
    int PUB_SP_BYTES_LEN = PUB_SP_BYTES.length;
    /** Length of {@link #HPUB_SP_BYTES}, the offset at which a publish with headers line's arguments start. */
    int HPUB_SP_BYTES_LEN = HPUB_SP_BYTES.length;
    /** Length of {@link #CONNECT_SP_BYTES}, the offset at which the connect line's options JSON starts. */
    int OP_CONNECT_SP_LEN = CONNECT_SP_BYTES.length;
    /** Length of {@link #SUB_SP_BYTES}, the offset at which a subscribe line's arguments start. */
    int OP_SUB_SP_LEN = SUB_SP_BYTES.length;
    /** Length of {@link #UNSUB_SP_BYTES}, the offset at which an unsubscribe line's arguments start. */
    int OP_UNSUB_SP_LEN = UNSUB_SP_BYTES.length;

    /** Longest op name the server can send, used to bound how far the reader scans for an op. {@value} */
    int MAX_PROTOCOL_RECEIVE_OP_LENGTH = 4;

    /** Error text used when a header block does not begin with {@link #HEADER_VERSION}. {@value} */
    String INVALID_HEADER_VERSION = "Invalid header version";
    /** Error text used when a header block's key and value structure is malformed. {@value} */
    String INVALID_HEADER_COMPOSITION = "Invalid header composition";
    /** Error text used when the status code on the header version line is not a valid number. {@value} */
    String INVALID_HEADER_STATUS_CODE = "Invalid header status code";
    /** Error text used when the bytes handed to the header parser are null or empty. {@value} */
    String SERIALIZED_HEADER_CANNOT_BE_NULL_OR_EMPTY = "Serialized header cannot be null or empty.";

    // The trailing space is intentional as in "Output queue is full 5000"
    /** Error text prefix used when a publish is refused because the output queue is at its limit; the limit is appended. {@value} */
    String OUTPUT_QUEUE_IS_FULL = "Output queue is full ";
    /** Error text prefix used when a publish times out waiting for room in the output queue; the wait is appended. {@value} */
    String OUTPUT_QUEUE_BUSY = "Output queue is busy ";
    /** Error text prefix used when a thread is interrupted while waiting on the output queue; the wait is appended. {@value} */
    String OUTPUT_QUEUE_INTERRUPTED = "Output queue is interrupted ";

    /** Nanoseconds in one millisecond, for converting the millisecond durations the API takes. {@value} */
    long NANOS_PER_MILLI = 1_000_000L;

    /** Stand-in used when a value has not been set or cannot be determined. {@value} */
    String UNDEFINED = "UNDEFINED";

    // JetStream Specific

    /** Header on a flow control message telling the client its consumer has stalled. {@value} */
    String CONSUMER_STALLED_HDR         = "Nats-Consumer-Stalled";
    /** Header carrying the size in bytes of the message the server is reporting on. {@value} */
    String MSG_SIZE_HDR                 = "Nats-Msg-Size";
    /** Header naming why the server delivered a marker message in place of a real one. {@value} */
    String NATS_MARKER_REASON_HDR       = "Nats-Marker-Reason";
    /** {@link #CONSUMER_STALLED_HDR} encoded for the wire. */
    byte[] CONSUMER_STALLED_HDR_BYTES   = CONSUMER_STALLED_HDR.getBytes(ISO_8859_1);
    /** {@link #MSG_SIZE_HDR} encoded for the wire. */
    byte[] MSG_SIZE_HDR_BYTES           = MSG_SIZE_HDR.getBytes(ISO_8859_1);
    /** {@link #NATS_MARKER_REASON_HDR} encoded for the wire. */
    byte[] NATS_MARKER_REASON_HDR_BYTES = NATS_MARKER_REASON_HDR.getBytes(ISO_8859_1);

    /** Header naming the stream the message was stored in. {@value} */
    String NATS_STREAM        = "Nats-Stream";
    /** Header carrying the message's sequence number within its stream. {@value} */
    String NATS_SEQUENCE      = "Nats-Sequence";
    /** Header carrying the time the message was stored in the stream. {@value} */
    String NATS_TIMESTAMP     = "Nats-Time-Stamp";
    /** Header carrying the subject the message was originally published to. {@value} */
    String NATS_SUBJECT       = "Nats-Subject";
    /** Header carrying the sequence of the previous message on the same subject. {@value} */
    String NATS_LAST_SEQUENCE = "Nats-Last-Sequence";
    /** Header carrying how many messages remain after this one. {@value} */
    String NATS_NUM_PENDING   = "Nats-Num-Pending";
    /** The headers a direct get reply carries to describe the message it is returning. */
    String[] MESSAGE_INFO_HEADERS = new String[]{NATS_SUBJECT, NATS_SEQUENCE, NATS_TIMESTAMP, NATS_STREAM, NATS_LAST_SEQUENCE, NATS_NUM_PENDING};

    // bytes used for faster matching and less string allocation when
    /** {@link #NATS_STREAM} encoded for the wire. */
    byte[] NATS_STREAM_BYTES = NATS_STREAM.getBytes(ISO_8859_1);
    /** {@link #NATS_SEQUENCE} encoded for the wire. */
    byte[] NATS_SEQUENCE_BYTES = NATS_SEQUENCE.getBytes(ISO_8859_1);
    /** {@link #NATS_TIMESTAMP} encoded for the wire. */
    byte[] NATS_TIMESTAMP_BYTES = NATS_TIMESTAMP.getBytes(ISO_8859_1);
    /** {@link #NATS_SUBJECT} encoded for the wire. */
    byte[] NATS_SUBJECT_BYTES = NATS_SUBJECT.getBytes(ISO_8859_1);
    /** {@link #NATS_LAST_SEQUENCE} encoded for the wire. */
    byte[] NATS_LAST_SEQUENCE_BYTES = NATS_LAST_SEQUENCE.getBytes(ISO_8859_1);
    /** {@link #NATS_NUM_PENDING} encoded for the wire. */
    byte[] NATS_NUM_PENDING_BYTES = NATS_NUM_PENDING.getBytes(ISO_8859_1);

    /** Header carrying how many messages are still pending for the consumer. {@value} */
    String NATS_PENDING_MESSAGES       = "Nats-Pending-Messages";
    /** Header carrying how many bytes are still pending for the consumer. {@value} */
    String NATS_PENDING_BYTES          = "Nats-Pending-Bytes";
    /** {@link #NATS_PENDING_MESSAGES} encoded for the wire. */
    byte[] NATS_PENDING_MESSAGES_BYTES = NATS_PENDING_MESSAGES.getBytes(ISO_8859_1);
    /** {@link #NATS_PENDING_BYTES} encoded for the wire. */
    byte[] NATS_PENDING_BYTES_BYTES    = NATS_PENDING_BYTES.getBytes(ISO_8859_1);

    /** Prefix of the reply subject a message must be acknowledged on. {@value} */
    String JS_ACK_SUBJECT_PREFIX = "$JS.ACK.";
    /** Prefix of the subject the server sends consumer flow control requests on. {@value} */
    String JS_FC_SUBJECT_PREFIX = "$JS.FC.";

    /** Header marking a key value entry as a delete or purge rather than a value. {@value} */
    String KV_OPERATION_HEADER_KEY       = "KV-Operation";
    /** {@link #KV_OPERATION_HEADER_KEY} encoded for the wire. */
    byte[] KV_OPERATION_HEADER_KEY_BYTES = KV_OPERATION_HEADER_KEY.getBytes(ISO_8859_1);
}
