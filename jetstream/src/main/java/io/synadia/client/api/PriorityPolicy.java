package io.synadia.client.api;

import io.synadia.client.impl.BaseConsumeOptions;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Priority Policy of a consumer
 * @see <a href="https://github.com/nats-io/nats-architecture-and-design/blob/main/adr/ADR-42.md">nats-io ADR-42</a>
 * Setting a priority policy will also require setting a Priority group. <BR>
 * When a priority policy and priority group are set, client instances making pull request need to specify the priority group
 * and optionally other ConsumerOptions See {@link BaseConsumeOptions}
 *
 */
@NullMarked
public enum PriorityPolicy {
    /** Standard consumer. All client instances are load balance fairly. */
    None("none"),
    /**
	 * Each client pull request specifies overflow limits in the  {@link BaseConsumeOptions} <BR>
	 * Currently minPending and minAckPending are respected. <BR>
	 * When either the minimum number of pending messages (not yet delivered to any client) OR the number pending acks is exceeded the pull request will return messages.
     * */
    Overflow("overflow"),

    /** The client pull request will specify a priortity from 1 to 10 in the {@link BaseConsumeOptions} <BR>
     * Request with lower priority will be served first. That is, higher priority request will only be served when no pull request from lower priorities are pending.
     * */
    Prioritized("prioritized"),

    /**
		If multiple clients make requests only ONE will be served messages. The API will identify clients through a UUID. <BR>.
		If a client fails to make requests for more than the timeout specified in {@link BaseConsumeOptions} another client will be served.
    */
    PinnedClient("pinned_client");

    private final String policy;

    PriorityPolicy(String p) {
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
     * @return the matching PriorityPolicy or the supplied default
     */
    public static PriorityPolicy get(String value, PriorityPolicy dflt) {
        if (value != null) {
            if (None.policy.equalsIgnoreCase(value)) { return None; }
            if (Overflow.policy.equalsIgnoreCase(value)) { return Overflow; }
            if (Prioritized.policy.equalsIgnoreCase(value)) { return Prioritized; }
            if (PinnedClient.policy.equalsIgnoreCase(value)) { return PinnedClient; }
        }
        return dflt;
    }
}
