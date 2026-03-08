package io.nats.client.support;

import io.nats.client.NUID;

public abstract class NatsJetStreamUtil {

    private NatsJetStreamUtil() {} /* ensures cannot be constructed */

    public static String generateConsumerName() {
        return NUID.nextGlobal();
    }

    public static String generateConsumerName(String prefix) {
        return prefix + "-" + NUID.nextGlobal();
    }
}
