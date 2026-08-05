package io.synadia.client.utils;

/**
 * The kinds of token the header parser recognizes while walking a protocol line.
 */
public enum TokenType {
    /** One or more space characters separating tokens. */
    SPACE,

    /** The carriage-return / line-feed pair that terminates a line. */
    CRLF,

    /** A header key, terminated by a colon. */
    KEY,

    /** A single whitespace delimited word. */
    WORD,

    /** Free-form text running to the end of the line. */
    TEXT
}
