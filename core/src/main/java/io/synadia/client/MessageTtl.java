package io.synadia.client;

import io.synadia.client.testutils.Validator;

/**
 * Class to make setting a per message ttl easier.
 */
public class MessageTtl {
    private final String ttlString;

    private MessageTtl(String ttlString) {
        this.ttlString = ttlString;
    }

    /**
     * Get the string representation of the message ttl, used as a value
     * @return the string
     */
    public String getTtlString() {
        return ttlString;
    }

    @Override
    public String toString() {
        return "MessageTtl{'" + ttlString + "'}";
    }

    /**
     * Sets the TTL for this specific message to be published
     * @param msgTtlSeconds the ttl in seconds
     * @return The MessageTtl instance
     */
    public static MessageTtl seconds(int msgTtlSeconds) {
        if (msgTtlSeconds < 1) {
            throw new IllegalArgumentException("Must be at least 1 second.");
        }
        return new MessageTtl(msgTtlSeconds + "s");
    }

    /**
     * Sets the TTL for this specific message to be published. Use at your own risk.
     * The current specification can be found here @see <a href="https://github.com/nats-io/nats-architecture-and-design/blob/main/adr/ADR-43.md#per-message-ttl">JetStream Per-Message TTL</a>
     * @param messageTtlCustom the ttl in seconds
     * @return The MessageTtl instance
     */
    public static MessageTtl custom(String messageTtlCustom) {
        if (Validator.nullOrEmpty(messageTtlCustom)) {
            throw new IllegalArgumentException("Custom value required.");
        }
        return new MessageTtl(messageTtlCustom);
    }

    /**
     * Sets the TTL for this specific message to be published and never be expired
     * @return The MessageTtl instance
     */
    public static MessageTtl never() {
        return new MessageTtl("never");
    }
}
