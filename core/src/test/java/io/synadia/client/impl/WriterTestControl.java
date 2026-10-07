// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import java.util.concurrent.TimeUnit;

import static io.synadia.client.utils.ConnectionUtils.DEFAULT_WAIT;

/**
 * Pause and resume a connection's writer so outgoing data stays queued, for either implementation.
 */
final class WriterTestControl {
    private WriterTestControl() {}

    static void pauseWriter(NatsConnection nc) throws Exception {
        if (nc instanceof NatsConnectionV3 v3) {
            v3.pauseWriterForTest();
        }
        else {
            nc.getWriter().stop().get(DEFAULT_WAIT, TimeUnit.MILLISECONDS);
        }
    }

    static void resumeWriter(NatsConnection nc) {
        if (nc instanceof NatsConnectionV3 v3) {
            v3.resumeWriterForTest();
        }
        else {
            nc.getWriter().start(nc.getDataPortFuture());
        }
    }
}
