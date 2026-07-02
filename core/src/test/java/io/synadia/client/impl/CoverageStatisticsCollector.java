package io.synadia.client.impl;

import io.synadia.client.Statistics;
import io.synadia.client.StatisticsCollector;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is simply to have a concrete implementation to test setting properties and calling the builder in Options
 */
public class CoverageStatisticsCollector implements StatisticsCollector, Statistics {

    private final AtomicLong outMsgs = new AtomicLong();
    private final AtomicLong outBytes = new AtomicLong();

    @Override
    public void incrementOut(long bytes) {
        outMsgs.incrementAndGet();
        outBytes.addAndGet(bytes);
    }

    // no-op write methods this coverage collector does not track
    @Override public void setAdvancedTracking(boolean trackAdvanced) {}
    @Override public void incrementPingCount() {}
    @Override public void incrementReconnects() {}
    @Override public void incrementDroppedCount() {}
    @Override public void incrementOkCount() {}
    @Override public void incrementErrCount() {}
    @Override public void incrementExceptionCount() {}
    @Override public void incrementRequestsSent() {}
    @Override public void incrementRepliesReceived() {}
    @Override public void incrementDuplicateRepliesReceived() {}
    @Override public void incrementOrphanRepliesReceived() {}
    @Override public void incrementIn(long bytes) {}
    @Override public void incrementFlushCounter() {}
    @Override public void incrementOutstandingRequests() {}
    @Override public void decrementOutstandingRequests() {}
    @Override public void registerRead(long bytes) {}
    @Override public void registerWrite(long bytes) {}

    @Override
    @NonNull
    public Statistics getStatistics() {
        return this;
    }

    @Override
    public long getOutMsgs() {
        return outMsgs.get();
    }

    @Override
    public long getOutBytes() {
        return outBytes.get();
    }

    @Override public long getPings() { return 0; }
    @Override public long getReconnects() { return 0; }
    @Override public long getDroppedCount() { return 0; }
    @Override public long getOKs() { return 0; }
    @Override public long getErrs() { return 0; }
    @Override public long getExceptions() { return 0; }
    @Override public long getRequestsSent() { return 0; }
    @Override public long getRepliesReceived() { return 0; }
    @Override public long getDuplicateRepliesReceived() { return 0; }
    @Override public long getOrphanRepliesReceived() { return 0; }
    @Override public long getInMsgs() { return 0; }
    @Override public long getInBytes() { return 0; }
    @Override public long getFlushCounter() { return 0; }
    @Override public long getOutstandingRequests() { return 0; }
}
