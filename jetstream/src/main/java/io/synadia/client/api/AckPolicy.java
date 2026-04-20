package io.synadia.client.api;

import org.jspecify.annotations.NullMarked;

/**
 * Represents the Ack Policy of a consumer
 */
@NullMarked
public enum AckPolicy {
	/**
     * Messages are acknowledged as soon as the server sends them. Clients do not need to ack.
     */
    None("none"),
    /**
     * All messages with a sequence number less than the message acked are also acknowledged. E.g. reading a batch of messages 1 .. 100. Ack on message 100 will acknowledge 1 .. 99 as well.
     */
    All("all"),
    /**
     * Each message must be acknowledged individually. Message can be acked out of sequence and create gaps of unacknowledged messages in the consumer.
     */
    Explicit("explicit");

    private final String policy;

    /**
     * Construct an AckPolicy
     * @param p the policy JSON value text
     */
    AckPolicy(String p) {
        policy = p;
    }

    @Override
    public String toString() {
        return policy;
    }

    /**
     * Get an instance from a string value
     * @param value the value to look up
     * @param dflt the result if value is null or not matched
     * @return the matching AckPolicy or the supplied default
     */
    public static AckPolicy get(String value, AckPolicy dflt) {
        if (value != null) {
            if (None.policy.equalsIgnoreCase(value)) { return None; }
            if (All.policy.equalsIgnoreCase(value)) { return All; }
            if (Explicit.policy.equalsIgnoreCase(value)) { return Explicit; }
        }
        return dflt;
    }
}
