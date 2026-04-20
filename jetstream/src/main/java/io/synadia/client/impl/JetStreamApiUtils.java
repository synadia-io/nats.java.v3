package io.synadia.client.impl;

import io.synadia.client.testutils.ApiUtils;

public abstract class JetStreamApiUtils extends ApiUtils {

    private JetStreamApiUtils() {}  /* ensures cannot be constructed */

    public static String generateConsumerName() {
        return randomString();
    }

    public static String generateConsumerName(String prefix) {
        return prefix == null ? randomString() : prefix + "-" + randomString();
    }
}
