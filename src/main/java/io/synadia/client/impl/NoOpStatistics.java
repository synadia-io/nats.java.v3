package io.synadia.client.impl;

import io.synadia.client.StatisticsCollector;

public class NoOpStatistics implements StatisticsCollector {
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
    @Override public void incrementOut(long bytes) {}
    @Override public void incrementInMsgs() {}
    @Override public void incrementOutMsgs() {}
    @Override public void incrementInBytes(long bytes) {}
    @Override public void incrementOutBytes(long bytes) {}
    @Override public void incrementFlushCounter() {}
    @Override public void incrementOutstandingRequests() {}
    @Override public void decrementOutstandingRequests() {}
    @Override public void registerRead(long bytes) {}
    @Override public void registerWrite(long bytes) {}
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
    @Override public long getOutMsgs() { return 0; }
    @Override public long getInBytes() { return 0; }
    @Override public long getOutBytes() { return 0; }
    @Override public long getFlushCounter() { return 0; }
    @Override public long getOutstandingRequests() { return 0; }
}
