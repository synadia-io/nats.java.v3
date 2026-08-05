package io.synadia.client.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static io.synadia.client.api.Status.*;
import static io.synadia.client.utils.NatsConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A single token found while parsing a serialized protocol or header line. A token does not copy
 * any bytes; it only remembers the type it matched and the start and end offsets of its value in
 * the underlying array, so the value is only turned into a String if it is actually asked for.
 */
public class Token {
    private final byte[] serialized;
    private final TokenType type;
    private final int start;
    private int end;
    private boolean hasValue;
    private final int valueLength;

    /**
     * Parse the token that follows another token, skipping the character that ended the
     * previous token, two characters in the case of a key since a key is followed by a colon and a space.
     * @param serialized the bytes being parsed
     * @param len the number of bytes in the array that are in play
     * @param prev the token immediately before this one
     * @param required the type the token must be, or null to accept text
     * @throws IllegalArgumentException if the bytes do not form a token of the required type
     */
    public Token(byte[] serialized, int len, Token prev, TokenType required) {
        this(serialized, len, prev.end + (prev.type == TokenType.KEY ? 2 : 1), required);
    }

    /**
     * Parse the token starting at a given offset.
     * @param serialized the bytes being parsed
     * @param len the number of bytes in the array that are in play
     * @param cur the offset the token starts at
     * @param required the type the token must be, or null to accept text
     * @throws IllegalArgumentException if the bytes do not form a token of the required type
     */
    public Token(byte[] serialized, int len, int cur, TokenType required) {
        this.serialized = serialized;

        if (cur >= len) {
            throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
        }
        if (serialized[cur] == SP) {
            type = TokenType.SPACE;
            start = cur;
            end = cur;
            while (serialized[++cur] == SP) {
                end = cur;
            }
        } else if (serialized[cur] == CR) {
            mustBeCrlf(len, cur);
            type = TokenType.CRLF;
            start = cur;
            end = cur + 1;
        } else if (required == TokenType.CRLF || required == TokenType.SPACE) {
            throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
        } else {
            byte ender1;
            byte ender2;
            if (required == null || required == TokenType.TEXT) {
                type = TokenType.TEXT;
                ender1 = CR;
                ender2 = CR;
            }
            else if (required == TokenType.WORD) {
                ender1 = SP;
                ender2 = CR;
                type = TokenType.WORD;
            } else { // KEY is all that's left if (required == TokenType.KEY) {
                ender1 = COLON;
                ender2 = COLON;
                type = TokenType.KEY;
            }
            start = cur;
            end = cur;
            while (++cur < len && serialized[cur] != ender1 && serialized[cur] != ender2) {
                end = cur;
            }
            if (cur >= len) {
                throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
            }
            if (serialized[cur] == CR) {
                mustBeCrlf(len, cur);
            }
            hasValue = true;
        }
        valueLength = hasValue ? end - start + 1 : 0;
    }

    private void mustBeCrlf(int len, int cur) {
        if ((cur + 1) >= len || serialized[cur + 1] != LF) {
            throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
        }
    }

    /**
     * Assert the token turned out to be the given type, for the cases where the type could not
     * be demanded up front at parse time.
     * @param expected the type the token must be
     * @throws IllegalArgumentException if the token is a different type
     */
    public void mustBe(TokenType expected) {
        if (type != expected) {
            throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
        }
    }

    /**
     * Test the token's type without throwing.
     * @param expected the type to test against
     * @return true if the token is that type
     */
    public boolean isType(TokenType expected) {
        return type == expected;
    }

    /**
     * Whether the token carries text. Space and CRLF tokens are pure delimiters and have none.
     * @return true if there is a value to read
     */
    public boolean hasValue() {
        return hasValue;
    }

    /**
     * The token's text, trimmed of surrounding whitespace.
     * @return the value, or an empty string if the token is a delimiter
     */
    @NonNull
    public String getValue() {
        return hasValue ? valueAsString() : "";
    }

    /**
     * The token's text, trimmed of surrounding whitespace, for callers that need to tell a
     * delimiter apart from an empty value.
     * @return the value, or null if the token is a delimiter
     */
    @Nullable
    public String getValueOrNull() {
        return hasValue ? valueAsString() : null;
    }

    private String valueAsString() {
        return new String(serialized, start, valueLength, UTF_8).trim();
    }

    /**
     * The token's text as a header key, returning the shared constant when the bytes match a key
     * the client already knows. Comparing bytes and handing back an interned constant avoids
     * allocating a String for the keys that show up on nearly every message.
     * @return the known key constant, or a newly built string when the key is not one of them
     */
    @NonNull
    public String getValueCheckKnownKeys() {
        if (valueLength == 0) {
            return "";
        }
        // all known keys are at least 5 characters Nats-<...> and KV-Operation
        if (valueLength > 5) {
            if (valueStartsNatsDash()) {
                if (endsMatch(NATS_STREAM_BYTES, 5)) {
                    return NATS_STREAM;
                }
                if (endsMatch(NATS_SEQUENCE_BYTES, 5)) {
                    return NATS_SEQUENCE;
                }
                if (endsMatch(NATS_TIMESTAMP_BYTES, 5)) {
                    return NATS_TIMESTAMP;
                }
                if (endsMatch(NATS_SUBJECT_BYTES, 5)) {
                    return NATS_SUBJECT;
                }
                if (endsMatch(NATS_LAST_SEQUENCE_BYTES, 5)) {
                    return NATS_LAST_SEQUENCE;
                }
                if (endsMatch(NATS_NUM_PENDING_BYTES, 5)) {
                    return NATS_NUM_PENDING;
                }
                if (endsMatch(CONSUMER_STALLED_HDR_BYTES, 5)) {
                    return CONSUMER_STALLED_HDR;
                }
                if (endsMatch(MSG_SIZE_HDR_BYTES, 5)) {
                    return MSG_SIZE_HDR;
                }
                if (endsMatch(NATS_MARKER_REASON_HDR_BYTES, 5)) {
                    return NATS_MARKER_REASON_HDR;
                }
                if (endsMatch(NATS_PENDING_MESSAGES_BYTES, 5)) {
                    return NATS_PENDING_MESSAGES;
                }
                if (endsMatch(NATS_PENDING_BYTES_BYTES, 5)) {
                    return NATS_PENDING_BYTES;
                }
            }
            else if (endsMatch(KV_OPERATION_HEADER_KEY_BYTES, 0)) {
                return KV_OPERATION_HEADER_KEY;
            }
        }

        // didn't know the key
        return valueAsString();
    }

    /**
     * The token's text as a status message, returning the shared constant when the bytes match a
     * status the client already knows, on the same allocation-avoiding principle as
     * {@link #getValueCheckKnownKeys()}.
     * @return the known status constant, a newly built string when the status is not one of them,
     *         or null if the token has no value
     */
    @Nullable
    public String getValueCheckKnownStatuses() {
        if (valueLength == 0) {
            return null;
        }
        if (valueLength > 10) {
            switch (serialized[start]) {
                case 'E':
                    if (valueStartsWithExceededMax()) {
                        if (endsMatch(EXCEEDED_MAX_WAITING_BYTES, 8)) {
                            return EXCEEDED_MAX_WAITING;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_BATCH_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_BATCH;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_EXPIRES_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_EXPIRES;
                        }
                        if (endsMatch(EXCEEDED_MAX_REQUEST_MAX_BYTES_BYTES, 8)) {
                            return EXCEEDED_MAX_REQUEST_MAX_BYTES;
                        }
                    }
                    break;
                case 'B':
                    if (endsMatch(BATCH_COMPLETED_BYTES, 1)) {
                        return BATCH_COMPLETED;
                    }
                    if (endsMatch(BAD_REQUEST_BYTES, 1)) {
                        return BAD_REQUEST;
                    }
                    break;
                case 'N':
                    if (endsMatch(NO_RESPONDERS_TEXT_BYTES, 1)) {
                        return NO_RESPONDERS_TEXT;
                    }
                    if (endsMatch(NO_MESSAGES_BYTES, 1)) {
                        return NO_MESSAGES;
                    }
                    break;
                case 'F':
                    if (endsMatch(FLOW_CONTROL_TEXT_BYTES, 1)) {
                        return FLOW_CONTROL_TEXT;
                    }
                    break;
                case 'I':
                    if (endsMatch(HEARTBEAT_TEXT_BYTES, 1)) {
                        return HEARTBEAT_TEXT;
                    }
                    break;
                case 'M':
                    if (endsMatch(MESSAGE_SIZE_EXCEEDS_MAX_BYTES_BYTES, 1)) {
                        return MESSAGE_SIZE_EXCEEDS_MAX_BYTES;
                    }
                    break;
                case 'L':
                    if (endsMatch(LEADERSHIP_CHANGE_BYTES, 1)) {
                        return LEADERSHIP_CHANGE;
                    }
                    break;
                case 'S':
                    if (endsMatch(SERVER_SHUTDOWN_BYTES, 1)) {
                        return SERVER_SHUTDOWN;
                    }
                    break;
                case 'C':
                    if (endsMatch(CONSUMER_DELETED_BYTES, 1)) {
                        return CONSUMER_DELETED;
                    }
                    if (endsMatch(CONSUMER_IS_PUSH_BASED_BYTES, 1)) {
                        return CONSUMER_IS_PUSH_BASED;
                    }
                    break;
            }
        }
        else if (endsMatch(EOB_TEXT_BYTES, 0)) { // only short status
            return EOB_TEXT;
        }
        return valueAsString();
    }

    static final byte[] NATS_DASH_PREFIX_BYTES = "Nats-".getBytes();
    static final byte[] EXCEEDED_MAX_PREFIX_BYTES = EXCEEDED_MAX_PREFIX.getBytes();
    static final int EXCEEDED_MAX_PREFIX_BYTES_LEN = EXCEEDED_MAX_PREFIX.length();

    private boolean valueStartsNatsDash() {
        for (int i = 0; i < 4; i++) {
            if (NATS_DASH_PREFIX_BYTES[i] != serialized[start + i]) {
                return false;
            }
        }
        return true;
    }

    private boolean valueStartsWithExceededMax() {
        // we know we already checked the first letter to be E
        for (int i = 1; i < EXCEEDED_MAX_PREFIX_BYTES_LEN; i++) {
            if (EXCEEDED_MAX_PREFIX_BYTES[i] != serialized[start + i]) {
                return false;
            }
        }
        return true;
    }

    private boolean endsMatch(byte @NonNull [] checkBytes, int compareStartIndex) {
        if (valueLength != checkBytes.length) {
            return false;
        }
        for (int i = compareStartIndex; i < valueLength; i++) {
            if (checkBytes[i] != serialized[start + i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Whether two tokens cover the same span, meaning the same type and the same start and end
     * offsets. Used to detect that parsing has not moved forward.
     * @param token the token to compare with
     * @return true if both tokens are at the same point
     */
    public boolean samePoint(Token token) {
        return start == token.start
                && end == token.end
                && type == token.type;
    }
}
