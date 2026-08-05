package io.synadia.client.impl;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * The acknowledgement protocol messages a client can publish to a JetStream message's reply subject.
 */
public enum AckType {
    /** Acknowledge the message was handled successfully, the server will not redeliver it. */
    AckAck("+ACK", true),

    /** Negatively acknowledge the message, asking the server to redeliver it. */
    AckNak("-NAK", true),

    /** Tell the server work is still in progress so it resets the ack wait timer instead of redelivering. */
    AckProgress("+WPI", false),

    /** Stop delivery of the message entirely, the server will not redeliver it. */
    AckTerm("+TERM", true),

    /** Acknowledge and request the next message in the same operation, valid for pull consumers only. */
    AckNext("+NXT", false);

    /** The protocol text, for example {@code +ACK}. */
    public final String text;

    /** {@link #text} encoded as ISO-8859-1, ready to be used as a message body. */
    public final byte[] bytes;

    /** Whether this ack ends the server's interest in the message, as opposed to only extending it. */
    public final boolean terminal;

    AckType(String text, boolean terminal) {
        this.text = text;
        this.bytes = text.getBytes(ISO_8859_1);
        this.terminal = terminal;
    }

    /**
     * The message body for this ack, with an optional delay before the server acts on it.
     * @param delayNanoseconds the delay in nanoseconds, less than 1 means no delay
     * @return the plain {@link #bytes} when there is no delay, otherwise the text plus a delay json payload
     */
    public byte[] bodyBytes(long delayNanoseconds) {
        return delayNanoseconds < 1 ? bytes : (text + " {\"delay\": " + delayNanoseconds + "}").getBytes(ISO_8859_1);
    }
}
