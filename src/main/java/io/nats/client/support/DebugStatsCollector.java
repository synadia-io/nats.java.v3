package io.nats.client.support;

import io.nats.client.impl.NoOpStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class DebugStatsCollector extends NoOpStatistics {
    public static class TimeTrack {
        long time;
        long messages;
        long bytes;

        public TimeTrack(long messages, long bytes) {
            time = System.currentTimeMillis();
            this.messages = messages;
            this.bytes = bytes;
        }
    }

    private final AtomicLong outMsgs;
    private final AtomicLong outBytes;
    private final AtomicLong writeBytes;

    private final List<TimeTrack> outMsgsList;
    private final List<TimeTrack> outBytesList;
    private final List<TimeTrack> writeBytesList;

    public DebugStatsCollector() {
        outMsgs = new AtomicLong();
        outBytes = new AtomicLong();
        writeBytes = new AtomicLong();
        outMsgsList = new ArrayList<>();
        outBytesList = new ArrayList<>();
        writeBytesList = new ArrayList<>();
    }

    @Override
    public void incrementOut(long bytes) {
        outMsgsList.add(new TimeTrack(outMsgs.incrementAndGet(), outBytes.addAndGet(bytes)));
    }

    @Override
    public void registerWrite(long bytes) {
        writeBytesList.add(new TimeTrack(-1, writeBytes.addAndGet(bytes)));
    }

    @Override
    public long getOutMsgs() {
        return outMsgs.get();
    }

    @Override
    public long getOutBytes() {
        return outBytes.get();
    }

    public long getWriteBytes() {
        return writeBytes.get();
    }

    public List<TimeTrack> getOutMsgsList() {
        return outMsgsList;
    }

    public List<TimeTrack> getOutBytesList() {
        return outBytesList;
    }

    public List<TimeTrack> getWriteBytesList() {
        return writeBytesList;
    }

    @Override
    public String toString() {
        return "DebugStatsCollector{" +
            "outMsgs=" + outMsgs.get() +
            ", outBytes=" + outBytes.get() +
            ", writeBytes=" + writeBytes.get() +
            "}";
    }

    public String toShortString() {
        return "{" +
            "outMsgs=" + outMsgs.get() +
            ", outBytes=" + outBytes.get() +
            ", writeBytes=" + writeBytes.get() +
            "}";
    }
}
