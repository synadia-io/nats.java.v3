package io.synadia.client.utils;

import io.synadia.client.api.Status;
import io.synadia.client.impl.Headers;

import static io.synadia.client.utils.NatsConstants.*;

/**
 * Parses the serialized header block that precedes the payload of an incoming message,
 * splitting out the inline status line and the regular headers.
 */
public class IncomingHeadersProcessor {

    private final int serializedLength;
    private Headers headers;
    private Status inlineStatus;

    /**
     * Parse the serialized header block.
     * @param serialized the raw header bytes, starting with the {@code NATS/1.0} version prefix
     * @throws IllegalArgumentException if the bytes are null or empty, the version prefix does not
     *         match, or the composition is otherwise invalid
     */
    public IncomingHeadersProcessor(byte[] serialized) {

        // basic validation first to help fail fast
        if (serialized == null || serialized.length == 0) {
            throw new IllegalArgumentException(SERIALIZED_HEADER_CANNOT_BE_NULL_OR_EMPTY);
        }

        // is this the correct version
        for (int x = 0; x < HEADER_VERSION_BYTES_LEN; x++) {
            if (serialized[x] != HEADER_VERSION_BYTES[x]) {
                throw new IllegalArgumentException(INVALID_HEADER_VERSION);
            }
        }

        // does the header end properly
        serializedLength = serialized.length;
        Token terminus = new Token(serialized, serializedLength, serializedLength - 2, TokenType.CRLF);
        Token token = new Token(serialized, serializedLength, HEADER_VERSION_BYTES_LEN, null);

        if (token.isType(TokenType.SPACE)) {
            token = initStatus(serialized, serializedLength, token);
            if (token.samePoint(terminus)) {
                return; // status only
            }
        }

        if (token.isType(TokenType.CRLF)) {
            initHeader(serialized, serializedLength, token);
        }
        else {
            throw new IllegalArgumentException(INVALID_HEADER_COMPOSITION);
        }
    }

    /**
     * The number of bytes consumed by the header block, used to find the start of the payload.
     * @return the length in bytes
     */
    public int getSerializedLength() {
        return serializedLength;
    }

    /**
     * The parsed headers.
     * @return the headers, null when the block carried only a status line
     */
    public Headers getHeaders() {
        return headers;
    }

    /**
     * The status parsed from the inline status line, for example a 503 no responders.
     * @return the status, null when the block had no status line
     */
    public Status getStatus() {
        return inlineStatus;
    }

    private void initHeader(byte[] serialized, int len, Token tCrlf) {
        // REGULAR HEADER
        Token peek = new Token(serialized, len, tCrlf, null);
        while (peek.isType(TokenType.TEXT)) {
            Token tKey = new Token(serialized, len, tCrlf, TokenType.KEY);
            Token tVal = new Token(serialized, len, tKey, null);
            if (tVal.isType(TokenType.SPACE)) {
                tVal = new Token(serialized, len, tVal, null);
            }
            if (tVal.isType(TokenType.TEXT)) {
                tCrlf = new Token(serialized, len, tVal, TokenType.CRLF);
            }
            else {
                tVal.mustBe(TokenType.CRLF);
                tCrlf = tVal;
            }
            if (headers == null) {
                headers = new Headers();
            }
            headers.add(tKey.getValueCheckKnownKeys(), tVal.getValue());
            peek = new Token(serialized, len, tCrlf, null);
        }
        peek.mustBe(TokenType.CRLF);
    }

    private Token initStatus(byte[] serialized, int len, Token tSpace) {
        Token tCode = new Token(serialized, len, tSpace, TokenType.WORD);
        Token tVal = new Token(serialized, len, tCode, null);
        Token crlf;
        if (tVal.isType(TokenType.SPACE)) {
            tVal = new Token(serialized, len, tVal, TokenType.TEXT);
            crlf = new Token(serialized, len, tVal, TokenType.CRLF);
        }
        else {
            tVal.mustBe(TokenType.CRLF);
            crlf = tVal;
        }
        inlineStatus = new Status(tCode, tVal);
        return crlf;
    }

}
