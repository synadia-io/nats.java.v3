package io.synadia.client.support;

import io.synadia.client.NUID;

public abstract class JetStreamApiUtils extends ApiUtils {

    private JetStreamApiUtils() {}  /* ensures cannot be constructed */

    public static String generateConsumerName() {
        return NUID.nextGlobal();
    }

    public static String generateConsumerName(String prefix) {
        return prefix == null ? NUID.nextGlobal() : prefix + "-" + NUID.nextGlobal();
    }
}
