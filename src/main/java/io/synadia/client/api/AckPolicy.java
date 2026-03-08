package io.synadia.client.api;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the Ack Policy of a consumer
 */
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

    private static final Map<String, AckPolicy> strEnumHash = new HashMap<>();

    static {
        for (AckPolicy env : AckPolicy.values()) {
            strEnumHash.put(env.toString(), env);
        }
    }

    /**
     * Get an instance of the AckPolicy or null if the string does not match the JSON value text
     * @param value the value to look up
     * @return the AckPolicy or null if not found
     */
    @Nullable
    public static AckPolicy get(String value) {
        return strEnumHash.get(value);
    }
}
