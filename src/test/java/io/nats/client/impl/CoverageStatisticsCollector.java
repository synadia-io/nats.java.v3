package io.nats.client.impl;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is simply to have a concrete implementation to test setting properties and calling the builder in Options
 */
public class CoverageStatisticsCollector extends NoOpStatistics {

    private final AtomicLong outMsgs = new AtomicLong();
    private final AtomicLong outBytes = new AtomicLong();

    @Override
    public void incrementOut(long bytes) {
        outMsgs.incrementAndGet();
        outBytes.addAndGet(bytes);
    }

    @Override
    public long getOutMsgs() {
        return outMsgs.get();
    }

    @Override
    public long getOutBytes() {
        return outBytes.get();
    }
}
