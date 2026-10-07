// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.StatisticsCollector;

import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The writer thread of one {@link NatsConnectionV3} socket. A new instance is made for every socket,
 * so a writer that outlives its socket can never touch the next one.
 */
final class OutboundWriter implements Runnable {
    private final NatsConnectionV3 connection;
    private final NatsConnectionV3.Generation generation;
    private final OutboundBuffer buffer;
    private final DataPort dataPort;
    private final StatisticsCollector stats;
    private final OutboundBuffer.Batch batch;
    private final ReentrantLock portLock; // a flushBuffer() call must not interleave with a write
    private volatile boolean running;
    private Future<Boolean> stopped;

    OutboundWriter(NatsConnectionV3 connection, NatsConnectionV3.Generation generation,
                   OutboundBuffer buffer, DataPort dataPort, StatisticsCollector stats) {
        this.connection = connection;
        this.generation = generation;
        this.buffer = buffer;
        this.dataPort = dataPort;
        this.stats = stats;
        this.batch = buffer.newBatch();
        this.portLock = new ReentrantLock();
    }

    void start() {
        running = true;
        stopped = connection.getWriterExecutor().submit(this, Boolean.TRUE);
    }

    /**
     * Ask the writer to stop. A batch already taken is still written, or fails on the closed socket.
     * @return a future completed when the thread exits
     */
    Future<Boolean> stop() {
        running = false;
        buffer.wakeWriter();
        return stopped;
    }

    boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                if (!buffer.take(batch, this::isRunning)) {
                    break;
                }
                portLock.lock();
                try {
                    writeBatch();
                }
                finally {
                    portLock.unlock();
                }
            }
        }
        catch (IOException e) {
            if (running) {
                connection.handleWriterIssue(generation, e);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            running = false;
        }
    }

    private void writeBatch() throws IOException {
        boolean flush = false;
        OutboundBuffer.Lane lane = batch.control;
        if (lane.length > 0) {
            dataPort.write(lane.bytes, lane.length);
            stats.registerWrite(lane.length);
            countOut(lane);
            flush = lane.flush;
            lane.clear();
            if (lane.bytes.length > buffer.blockSize()) {
                batch.control = new OutboundBuffer.Lane(1024); // do not keep a burst's worth of control lane
            }
        }
        OutboundBuffer.Lane block = batch.data;
        if (block != null) {
            // if this write fails the block is lost with the socket, like the classic writer's batch
            batch.data = null;
            dataPort.write(block.bytes, block.length);
            stats.registerWrite(block.length);
            countOut(block);
            flush |= block.flush;
            buffer.recycle(block);
        }
        if (flush) {
            dataPort.flush();
        }
    }

    private void countOut(OutboundBuffer.Lane lane) {
        for (int i = 0; i < lane.count; i++) {
            stats.incrementOut(lane.entrySize(i));
        }
    }

    // flushBuffer() support: the writer already writes as soon as there is work, so this only flushes the port
    void flushPort() throws IOException {
        portLock.lock();
        try {
            dataPort.flush();
        }
        finally {
            portLock.unlock();
        }
    }
}
