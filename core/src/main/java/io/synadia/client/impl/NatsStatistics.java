package io.synadia.client.impl;

import io.synadia.client.Statistics;
import io.synadia.client.StatisticsCollector;
import org.jspecify.annotations.NonNull;

import java.text.NumberFormat;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The connection's statistics, acting both as the collector the internals write to and as the
 * read-only view the application sees. Simple counters are atomic; the read and write size
 * summaries are guarded by their own locks and are only maintained when advanced tracking is on.
 */
public class NatsStatistics implements StatisticsCollector, Statistics {
    private final ReentrantLock readStatsLock;
    private final ReentrantLock writeStatsLock;

    private final LongSummaryStatistics readStats;
    private final LongSummaryStatistics writeStats;

    private final AtomicLong flushCounter;
    private final AtomicLong outstandingRequests;
    private final AtomicLong requestsSent;
    private final AtomicLong repliesReceived;
    private final AtomicLong duplicateRepliesReceived;
    private final AtomicLong orphanRepliesReceived;
    private final AtomicLong reconnects;
    private final AtomicLong inMsgs;
    private final AtomicLong outMsgs;
    private final AtomicLong inBytes;
    private final AtomicLong outBytes;
    private final AtomicLong pingCount;
    private final AtomicLong okCount;
    private final AtomicLong errCount;
    private final AtomicLong exceptionCount;
    private final AtomicLong droppedCount;

    private boolean trackAdvanced;

    /**
     * Construct a statistics instance with every counter at zero and advanced tracking off.
     */
    public NatsStatistics() {
        this.readStatsLock = new ReentrantLock();
        this.writeStatsLock = new ReentrantLock();

        this.readStats = new LongSummaryStatistics();
        this.writeStats = new LongSummaryStatistics();

        this.flushCounter = new AtomicLong();
        this.outstandingRequests = new AtomicLong();
        this.requestsSent = new AtomicLong();
        this.repliesReceived = new AtomicLong();
        this.duplicateRepliesReceived = new AtomicLong();
        this.orphanRepliesReceived = new AtomicLong();
        this.reconnects = new AtomicLong();
        this.inMsgs = new AtomicLong();
        this.outMsgs = new AtomicLong();
        this.inBytes = new AtomicLong();
        this.outBytes = new AtomicLong();
        this.pingCount = new AtomicLong();
        this.okCount = new AtomicLong();
        this.errCount = new AtomicLong();
        this.exceptionCount = new AtomicLong();
        this.droppedCount = new AtomicLong();
    }

    @Override
    public void setAdvancedTracking(boolean trackAdvanced) {
        this.trackAdvanced = trackAdvanced;
    }

    @Override
    public void incrementPingCount() {
        this.pingCount.incrementAndGet();
    }

    @Override
    public void incrementDroppedCount() {
        this.droppedCount.incrementAndGet();
    }

    @Override
    public void incrementOkCount() {
        this.okCount.incrementAndGet();
    }

    @Override
    public void incrementErrCount() {
        this.errCount.incrementAndGet();
    }

    @Override
    public void incrementExceptionCount() {
        this.exceptionCount.incrementAndGet();
    }

    @Override
    public void incrementRequestsSent() {
        this.requestsSent.incrementAndGet();
    }

    @Override
    public void incrementRepliesReceived() {
        this.repliesReceived.incrementAndGet();
    }

    @Override
    public void incrementDuplicateRepliesReceived() {
        this.duplicateRepliesReceived.incrementAndGet();
    }

    @Override
    public void incrementOrphanRepliesReceived() {
        this.orphanRepliesReceived.incrementAndGet();
    }

    @Override
    public void incrementReconnects() {
        this.reconnects.incrementAndGet();
    }

    @Override
    public void incrementIn(long bytes) {
        this.inMsgs.incrementAndGet();
        this.inBytes.addAndGet(bytes);
    }

    @Override
    public void incrementOut(long bytes) {
        this.outMsgs.incrementAndGet();
        this.outBytes.addAndGet(bytes);
    }

    @Override
    public void incrementFlushCounter() {
        this.flushCounter.incrementAndGet();
    }

    @Override
    public void incrementOutstandingRequests() {
        this.outstandingRequests.incrementAndGet();
    }

    @Override
    public void decrementOutstandingRequests() {
        this.outstandingRequests.decrementAndGet();
    }

    @Override
    public void registerRead(long bytes) {
        if (!trackAdvanced) {
            return;
        }

        readStatsLock.lock();
        try {
            readStats.accept(bytes);
        } finally {
            readStatsLock.unlock();
        }
    }

    @Override
    public void registerWrite(long bytes) {
        if (!trackAdvanced) {
            return;
        }

        writeStatsLock.lock();
        try {
            writeStats.accept(bytes);
        } finally {
            writeStatsLock.unlock();
        }
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Statistics getStatistics() {
        return this;
    }

    /** {@inheritDoc} */
    @Override
    public long getPings() {
        return this.pingCount.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getDroppedCount() {
        return this.droppedCount.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getOKs() {
        return this.okCount.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getErrs() {
        return this.errCount.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getExceptions() {
        return this.exceptionCount.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getRequestsSent() {
        return this.requestsSent.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getReconnects() {
        return this.reconnects.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getInMsgs() {
        return this.inMsgs.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getOutMsgs() {
        return this.outMsgs.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getInBytes() {
        return this.inBytes.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getOutBytes() {
        return this.outBytes.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getFlushCounter() {
        return flushCounter.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getOutstandingRequests() {
        return outstandingRequests.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getRepliesReceived() { return repliesReceived.get(); }

    /** {@inheritDoc} */
    @Override
    public long getDuplicateRepliesReceived() {
        return duplicateRepliesReceived.get();
    }

    /** {@inheritDoc} */
    @Override
    public long getOrphanRepliesReceived() { return orphanRepliesReceived.get(); }

    void appendNumberStat(StringBuilder builder, String name, long value) {
        builder.append(name);
        builder.append(NumberFormat.getNumberInstance().format(value));
        builder.append("\n");
    }

    void appendNumberStat(StringBuilder builder, String name, double value) {
        builder.append(name);
        builder.append(NumberFormat.getNumberInstance().format(value));
        builder.append("\n");
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("### NatsConnection ###\n");
        appendNumberStat(builder, "Reconnects:                      ", this.reconnects.get());
        appendNumberStat(builder, "Requests Sent:                   ", this.requestsSent.get());
        appendNumberStat(builder, "Replies Received:                ", this.repliesReceived.get());
        if (this.trackAdvanced) {
            appendNumberStat(builder, "Duplicate Replies Received:      ", this.duplicateRepliesReceived.get());
            appendNumberStat(builder, "Orphan Replies Received:         ", this.orphanRepliesReceived.get());
        }
        appendNumberStat(builder, "Pings Sent:                      ", this.pingCount.get());
        appendNumberStat(builder, "+OKs Received:                   ", this.okCount.get());
        appendNumberStat(builder, "-Errs Received:                  ", this.errCount.get());
        appendNumberStat(builder, "Handled Exceptions:              ", this.exceptionCount.get());
        appendNumberStat(builder, "Successful Flush Calls:          ", this.flushCounter.get());
        appendNumberStat(builder, "Outstanding Request Futures:     ", this.outstandingRequests.get());
        appendNumberStat(builder, "Dropped Messages:                ", this.droppedCount.get());
        builder.append("\n");
        builder.append("### Reader ###\n");
        appendNumberStat(builder, "Messages in:                     ", this.inMsgs.get());
        appendNumberStat(builder, "Bytes in:                        ", this.inBytes.get());
        builder.append("\n");
        if (this.trackAdvanced) {
            readStatsLock.lock();
            try {
                appendNumberStat(builder, "Socket Reads:                    ", readStats.getCount());
                appendNumberStat(builder, "Average Bytes Per Read:          ", readStats.getAverage());
                appendNumberStat(builder, "Min Bytes Per Read:              ", readStats.getMin());
                appendNumberStat(builder, "Max Bytes Per Read:              ", readStats.getMax());
            } finally {
                readStatsLock.unlock();
            }
        }
        builder.append("\n");
        builder.append("### Writer ###\n");
        appendNumberStat(builder, "Messages out:                    ", this.outMsgs.get());
        appendNumberStat(builder, "Bytes out:                       ", this.outBytes.get());
        builder.append("\n");
        if (this.trackAdvanced) {
            writeStatsLock.lock();
            try {
                appendNumberStat(builder, "Socket Writes:                   ", writeStats.getCount());
                appendNumberStat(builder, "Average Bytes Per Write:         ", writeStats.getAverage());
                appendNumberStat(builder, "Min Bytes Per Write:             ", writeStats.getMin());
                appendNumberStat(builder, "Max Bytes Per Write:             ", writeStats.getMax());
            } finally {
                writeStatsLock.unlock();
            }
        }

        return builder.toString();
    }
}
