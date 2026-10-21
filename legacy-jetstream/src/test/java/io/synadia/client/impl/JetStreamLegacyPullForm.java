// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Message;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.List;

/**
 * The two ways to call the legacy pull behaviors: through an instance, or through the static methods.
 */
@NullMarked
public enum JetStreamLegacyPullForm {
    INSTANCE,
    STATIC;

    public JetStreamLegacyPull of(JetStreamPullSubscription sub) {
        return this == INSTANCE ? new JetStreamLegacyPull(sub) : new StaticLegacyPull(sub);
    }

    // Each method calls the static method with the same parameters
    private static class StaticLegacyPull extends JetStreamLegacyPull {
        private final JetStreamPullSubscription sub;

        StaticLegacyPull(JetStreamPullSubscription sub) {
            super(sub);
            this.sub = sub;
        }

        @Override
        public List<Message> fetch(int batchSize, long maxWaitMillis) {
            return JetStreamLegacyPull.fetch(sub, batchSize, maxWaitMillis);
        }

        @Override
        public Iterator<Message> iterate(int batchSize, long maxWaitMillis) {
            return JetStreamLegacyPull.iterate(sub, batchSize, maxWaitMillis);
        }

        @Override
        public JetStreamReader reader(int batchSize, int repullAt) {
            return JetStreamLegacyPull.reader(sub, batchSize, repullAt);
        }
    }
}
