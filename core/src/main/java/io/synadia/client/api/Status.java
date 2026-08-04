package io.synadia.client.api;

import io.synadia.client.utils.NatsConstants;
import io.synadia.client.utils.Token;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * A status message from the server, carrying a code and a descriptive message. Statuses arrive in place of a
 * normal message payload - a request that no one is listening to comes back as a 503 "No Responders", a pull
 * consumer signals conditions like 404 "No Messages" or 409 "Consumer Deleted", and push consumers receive 100
 * flow control and idle heartbeat statuses.
 * <p>Applications usually see a Status through {@link io.synadia.client.ErrorListener ErrorListener} callbacks such as
 * {@code unhandledStatus} and {@code pullStatusError}, rather than constructing one.
 * <p>The {@code isX} methods identify the statuses the client treats specially; anything else is inspected with
 * {@link #getCode() getCode()} and {@link #getMessage() getMessage()}.
 */
public class Status {

    /** Message text of a flow control status. {@value} */
    public static final String FLOW_CONTROL_TEXT  = "FlowControl Request";

    /** Message text of an idle heartbeat status. {@value} */
    public static final String HEARTBEAT_TEXT     = "Idle Heartbeat";

    /** Message text returned when a request has no responders. {@value} */
    public static final String NO_RESPONDERS_TEXT = "No Responders Available For Request";

    /** Message text of an end-of-batch status. {@value} */
    public static final String EOB_TEXT           = "EOB";

    /** {@link #FLOW_CONTROL_TEXT} encoded for the wire. */
    public static final byte[] FLOW_CONTROL_TEXT_BYTES  = FLOW_CONTROL_TEXT.getBytes(ISO_8859_1);

    /** {@link #HEARTBEAT_TEXT} encoded for the wire. */
    public static final byte[] HEARTBEAT_TEXT_BYTES     = HEARTBEAT_TEXT.getBytes(ISO_8859_1);

    /** {@link #NO_RESPONDERS_TEXT} encoded for the wire. */
    public static final byte[] NO_RESPONDERS_TEXT_BYTES = NO_RESPONDERS_TEXT.getBytes(ISO_8859_1);

    /** {@link #EOB_TEXT} encoded for the wire. */
    public static final byte[] EOB_TEXT_BYTES           = EOB_TEXT.getBytes(ISO_8859_1);

    /** Status code shared by flow control and idle heartbeat messages; the text distinguishes them. {@value} */
    public static final int FLOW_OR_HEARTBEAT_STATUS_CODE = 100;

    /** Status code returned when no responders are listening on the request subject. {@value} */
    public static final int NO_RESPONDERS_CODE = 503;

    /** Status code for a malformed or rejected request. {@value} */
    public static final int BAD_REQUEST_CODE = 400;

    /** Status code for a missing resource, and for a pull that found no messages. {@value} */
    public static final int NOT_FOUND_CODE = 404;

    /** Status code for a JetStream request the server would not process. {@value} */
    public static final int BAD_JS_REQUEST_CODE = 408;

    /** Status code for the conflict conditions a pull consumer reports, such as exceeding a max. {@value} */
    public static final int CONFLICT_CODE = 409;

    /** Status code marking the end of a batch. {@value} */
    public static final int EOB_CODE = 204;

    /** Status code returned when a pinned consumer request carries the wrong pin id. {@value} */
    public static final int PIN_ERROR_CODE = 423;
    /** {@link #PIN_ERROR_CODE} encoded for the wire. */
    public static final byte[] PIN_ERROR_CODE_BYTES = ("" + PIN_ERROR_CODE).getBytes(ISO_8859_1);

    /** Message text of a 400 bad request status. {@value} */
    public static final String BAD_REQUEST                    = "Bad Request"; // 400

    /** Message text of a 404 status meaning the pull found nothing. {@value} */
    public static final String NO_MESSAGES                    = "No Messages"; // 404

    /** Message text of a 409 status meaning the consumer was deleted out from under the pull. {@value} */
    public static final String CONSUMER_DELETED               = "Consumer Deleted"; // 409

    /** Message text of a 409 status meaning a pull was issued against a push consumer. {@value} */
    public static final String CONSUMER_IS_PUSH_BASED         = "Consumer is push based"; // 409

    /** {@link #BAD_REQUEST} encoded for the wire. */
    public static final byte[] BAD_REQUEST_BYTES              = BAD_REQUEST.getBytes(ISO_8859_1);

    /** {@link #NO_MESSAGES} encoded for the wire. */
    public static final byte[] NO_MESSAGES_BYTES              = NO_MESSAGES.getBytes(ISO_8859_1);

    /** {@link #CONSUMER_DELETED} encoded for the wire. */
    public static final byte[] CONSUMER_DELETED_BYTES         = CONSUMER_DELETED.getBytes(ISO_8859_1);

    /** {@link #CONSUMER_IS_PUSH_BASED} encoded for the wire. */
    public static final byte[] CONSUMER_IS_PUSH_BASED_BYTES   = CONSUMER_IS_PUSH_BASED.getBytes(ISO_8859_1);

    /** Message text of a 409 status meaning a message was larger than the request's max bytes. {@value} */
    public static final String MESSAGE_SIZE_EXCEEDS_MAX_BYTES = "Message Size Exceeds MaxBytes"; // 409

    /** Common prefix of the "Exceeded Max..." status texts, for matching any of them. {@value} */
    public static final String EXCEEDED_MAX_PREFIX            = "Exceeded Max";

    /** Message text of a 409 status meaning the consumer's max waiting pulls was reached. {@value} */
    public static final String EXCEEDED_MAX_WAITING           = "Exceeded MaxWaiting"; // 409

    /** Message text of a 409 status meaning the request batch exceeded the consumer's limit. {@value} */
    public static final String EXCEEDED_MAX_REQUEST_BATCH     = "Exceeded MaxRequestBatch"; // 409

    /** Message text of a 409 status meaning the request expiration exceeded the consumer's limit. {@value} */
    public static final String EXCEEDED_MAX_REQUEST_EXPIRES   = "Exceeded MaxRequestExpires"; // 409

    /** Message text of a 409 status meaning the request max bytes exceeded the consumer's limit. {@value} */
    public static final String EXCEEDED_MAX_REQUEST_MAX_BYTES = "Exceeded MaxRequestMaxBytes"; // 409

    /** {@link #MESSAGE_SIZE_EXCEEDS_MAX_BYTES} encoded for the wire. */
    public static final byte[] MESSAGE_SIZE_EXCEEDS_MAX_BYTES_BYTES = MESSAGE_SIZE_EXCEEDS_MAX_BYTES.getBytes(ISO_8859_1);

    /** {@link #EXCEEDED_MAX_WAITING} encoded for the wire. */
    public static final byte[] EXCEEDED_MAX_WAITING_BYTES           = EXCEEDED_MAX_WAITING.getBytes(ISO_8859_1);

    /** {@link #EXCEEDED_MAX_REQUEST_BATCH} encoded for the wire. */
    public static final byte[] EXCEEDED_MAX_REQUEST_BATCH_BYTES     = EXCEEDED_MAX_REQUEST_BATCH.getBytes(ISO_8859_1);

    /** {@link #EXCEEDED_MAX_REQUEST_EXPIRES} encoded for the wire. */
    public static final byte[] EXCEEDED_MAX_REQUEST_EXPIRES_BYTES   = EXCEEDED_MAX_REQUEST_EXPIRES.getBytes(ISO_8859_1);

    /** {@link #EXCEEDED_MAX_REQUEST_MAX_BYTES} encoded for the wire. */
    public static final byte[] EXCEEDED_MAX_REQUEST_MAX_BYTES_BYTES = EXCEEDED_MAX_REQUEST_MAX_BYTES.getBytes(ISO_8859_1);

    /** Message text of the informational 409 status marking a completed batch. {@value} */
    public static final String BATCH_COMPLETED                = "Batch Completed"; // 409 informational

    /** Message text of the informational 409 status sent when the server is shutting down. {@value} */
    public static final String SERVER_SHUTDOWN                = "Server Shutdown"; // 409 informational with headers

    /** Message text of the 409 status sent when stream leadership changes. {@value} */
    public static final String LEADERSHIP_CHANGE              = "Leadership Change"; // 409

    /** {@link #BATCH_COMPLETED} encoded for the wire. */
    public static final byte[] BATCH_COMPLETED_BYTES          = BATCH_COMPLETED.getBytes(ISO_8859_1);

    /** {@link #SERVER_SHUTDOWN} encoded for the wire. */
    public static final byte[] SERVER_SHUTDOWN_BYTES          = SERVER_SHUTDOWN.getBytes(ISO_8859_1);

    /** {@link #LEADERSHIP_CHANGE} encoded for the wire. */
    public static final byte[] LEADERSHIP_CHANGE_BYTES        = LEADERSHIP_CHANGE.getBytes(ISO_8859_1);

    /** A ready-made end-of-batch status, {@link #EOB_CODE} with {@link #EOB_TEXT}. */
    public static final Status EOB = new Status(EOB_CODE, EOB_TEXT);

    /** A ready-made status for a pull that returned nothing before expiring. */
    public static final Status TIMEOUT_OR_NO_MESSAGES = new Status(NOT_FOUND_CODE, "Timeout or No Messages");

    private final int code;
    private final String message;

    private static final Map<Integer, String> CODE_TO_TEXT;

    static {
        CODE_TO_TEXT = new HashMap<>();
        CODE_TO_TEXT.put(NO_RESPONDERS_CODE, NO_RESPONDERS_TEXT);
    }

    /**
     * Construct a status from a code and message. A null message is replaced with the known text for the
     * code, or a generic "Server Status Message: code" when the code is not one the client knows.
     * @param code the status code
     * @param message the status message, or null to derive one from the code
     */
    public Status(int code, String message) {
        this.code = code;
        this.message = message == null ? makeMessage(code) : message ;
    }

    /**
     * Construct a status from the tokens parsed out of a status header line.
     * @param codeToken the token holding the numeric code
     * @param messageToken the token holding the message text
     * @throws IllegalArgumentException if the code token is missing or not a number
     */
    public Status(Token codeToken, Token messageToken) {
        this(extractCode(codeToken), messageToken.getValueCheckKnownStatuses());
    }

    /**
     * The status code, for example 503 for no responders.
     * @return the code
     */
    public int getCode() {
        return code;
    }

    /**
     * The status message text.
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * The code and message together, as {@code "code message"}.
     * @return the combined text
     */
    public String getMessageWithCode() {
        return code + " " + message;
    }

    private static int extractCode(Token codeToken) {
        try {
            String code = codeToken.getValueOrNull();
            if (code == null) {
                throw new IllegalArgumentException(NatsConstants.INVALID_HEADER_STATUS_CODE);
            }
            return Integer.parseInt(code);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException(NatsConstants.INVALID_HEADER_STATUS_CODE);
        }
    }

    private String makeMessage(int code) {
        String message = CODE_TO_TEXT.get(code);
        return message == null ? "Server Status Message: " + code : message;
    }

    @Override
    public String toString() {
        return "Status{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }

    /**
     * Whether this is a flow control status, which a push consumer answers to keep the server sending.
     * @return true if this is flow control
     */
    public boolean isFlowControl() {
        return code == FLOW_OR_HEARTBEAT_STATUS_CODE && message.equals(FLOW_CONTROL_TEXT);
    }

    /**
     * Whether this is an idle heartbeat status, sent when a consumer has had no traffic.
     * @return true if this is an idle heartbeat
     */
    public boolean isHeartbeat() {
        return code == FLOW_OR_HEARTBEAT_STATUS_CODE && message.equals(HEARTBEAT_TEXT);
    }

    /**
     * Whether this is a no-responders status, meaning nothing was listening on the request subject.
     * @return true if this is no responders
     */
    public boolean isNoResponders() {
        return code == NO_RESPONDERS_CODE && message.equals(NO_RESPONDERS_TEXT);
    }

    /**
     * Whether this is an end-of-batch status.
     * @return true if this is end of batch
     */
    public boolean isEob() {
        return code == EOB_CODE && message.equals(EOB_TEXT);
    }
}
