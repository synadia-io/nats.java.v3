// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client;

/**
 * Which connection implementation {@link Nats#connect(Options) Nats.connect} builds.
 */
public enum ConnectionImplementation {
    /**
     * The original implementation, {@link io.synadia.client.impl.NatsConnection NatsConnection}.
     */
    Classic,

    /**
     * The rewritten implementation, {@link io.synadia.client.impl.NatsConnectionV3 NatsConnectionV3}.
     * It is a subclass of {@code NatsConnection}, so every API that takes a connection accepts it.
     * Its documented behavior differences are listed in its class javadoc. Default.
     */
    V3;

    /**
     * Find the constant whose name matches, ignoring case.
     * @param value the name
     * @return the constant, or null if none matches
     */
    public static ConnectionImplementation get(String value) {
        for (ConnectionImplementation ci : values()) {
            if (ci.name().equalsIgnoreCase(value)) {
                return ci;
            }
        }
        return null;
    }
}
