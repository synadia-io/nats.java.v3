// Copyright (c) 2026 Synadia Communications Inc. All Rights Reserved.

package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

import static io.synadia.client.OptionsConstants.MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT;
import static io.synadia.client.utils.NatsConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * The outgoing side of {@link NatsConnectionV3}.
 * <p>Callers serialize their protocol straight into byte blocks under a short lock, so there is no
 * per message queue node and no per message protocol buffer, and the caller's headers and data are
 * copied before the publish call returns. That copy is the only copy of the payload before the socket
 * write: the writer writes a block as it is.
 * <p>Two lanes:
 * <ul>
 * <li>control - always written first. Traffic that must precede queued data: resubscribes during a
 *     reconnect, and PONG.</li>
 * <li>data - publishes and all other protocol, in call order, as a queue of blocks of
 *     {@link Options#getBufferSize() bufferSize} bytes (a single entry bigger than that gets a block of its
 *     own size). The writer takes one block per pass, so a failed socket write loses at most one block,
 *     the same bound the classic writer has; everything still queued survives the disconnect. Held back
 *     while the connection is connecting or reconnecting; that held back content is what the reconnect
 *     buffer limits.</li>
 * </ul>
 * <p>Every entry records its size and whether it is filtered on stop (SUB, UNSUB, PING, PONG), so the
 * same filtering the classic writer queue does on disconnect can be done by compacting the blocks.
 */
final class OutboundBuffer {
    static final int FILTER_ON_STOP = 0x8000_0000;
    static final int SIZE_MASK = 0x7FFF_FFFF;

    private static final int CONTROL_INITIAL_CAPACITY = 1024;
    private static final int POOL_MIN_BYTES = 1024 * 1024; // standard blocks kept: the larger of this and POOL_MIN_BLOCKS blocks
    private static final int POOL_MIN_BLOCKS = 4;

    private static final byte[] SUB_SP = "SUB ".getBytes(UTF_8);
    private static final byte[] UNSUB_SP = "UNSUB ".getBytes(UTF_8);

    /**
     * A run of serialized protocol plus the size and flags of each entry in it. Used for the control lane
     * and for each block of the data lane.
     */
    static final class Lane {
        byte[] bytes;
        int length;
        int[] entries;
        int count;
        boolean flush; // a data block with a flush entry takes no more entries; the writer flushes after it

        Lane(int capacity) {
            bytes = new byte[capacity];
            entries = new int[64];
        }

        boolean fits(int size) {
            return length + size <= bytes.length;
        }

        void ensureCapacity(int more) {
            int needed = length + more;
            if (needed > bytes.length) {
                int doubled = bytes.length * 2;
                bytes = Arrays.copyOf(bytes, doubled < needed ? needed : doubled);
            }
        }

        void addEntry(int size, boolean filterOnStop) {
            if (count == entries.length) {
                entries = Arrays.copyOf(entries, count * 2);
            }
            entries[count++] = filterOnStop ? (size | FILTER_ON_STOP) : size;
        }

        int entrySize(int i) {
            return entries[i] & SIZE_MASK;
        }

        void clear() {
            length = 0;
            count = 0;
            flush = false;
        }

        // Remove the filter on stop entries, keeping the others in order.
        void filter() {
            int read = 0;
            int write = 0;
            int kept = 0;
            for (int i = 0; i < count; i++) {
                int e = entries[i];
                int size = e & SIZE_MASK;
                if ((e & FILTER_ON_STOP) == 0) {
                    if (read != write) {
                        System.arraycopy(bytes, read, bytes, write, size);
                    }
                    write += size;
                    entries[kept++] = e;
                }
                read += size;
            }
            length = write;
            count = kept;
        }
    }

    /**
     * What the writer took in one pass. Owned by the writer thread.
     */
    static final class Batch {
        Lane control;
        @Nullable Lane data;

        Batch() {
            control = new Lane(CONTROL_INITIAL_CAPACITY);
        }
    }

    private final ReentrantLock lock;
    private final Condition writerWake;
    private final Condition notFull;

    private final int blockSize;
    private final int poolMax;
    private final int maxPending;
    private final boolean discardWhenFull;
    private final long pushTimeoutNanos;
    private final long reconnectBufferSize;

    private Lane control;
    private final ArrayDeque<Lane> blocks; // the data lane, oldest first
    private final ArrayDeque<Lane> pool;   // written blocks of blockSize kept for reuse
    private final ArrayDeque<Lane> largePool; // written blocks bigger than blockSize kept for reuse, up to largePoolMax bytes
    private long largePoolBytes;
    private long largePoolMax; // 0, nothing kept, until setLargePoolMax
    private int queuedCount;
    private long queuedBytes;
    private boolean dataOpen;
    private boolean writerWaiting;
    private int publishersWaiting;
    private boolean closed;
    private boolean pausedForTest;

    // copies of queuedCount and queuedBytes, readable without the lock
    private volatile int pendingCount;
    private volatile long pendingBytes;

    OutboundBuffer(Options options) {
        lock = new ReentrantLock();
        writerWake = lock.newCondition();
        notFull = lock.newCondition();

        blockSize = options.getBufferSize();
        int poolBlocks = POOL_MIN_BYTES / blockSize;
        poolMax = poolBlocks < POOL_MIN_BLOCKS ? POOL_MIN_BLOCKS : poolBlocks;

        int max = options.getMaxMessagesInOutgoingQueue();
        maxPending = max > 0 ? max : Integer.MAX_VALUE;
        discardWhenFull = options.isDiscardMessagesWhenOutgoingQueueFull();
        long pushNanos = options.getWriteQueuePushTimeout() * NANOS_PER_MILLI;
        long minNanos = MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT * NANOS_PER_MILLI;
        pushTimeoutNanos = pushNanos < minNanos ? minNanos : pushNanos;
        reconnectBufferSize = options.getReconnectBufferSize();

        control = new Lane(CONTROL_INITIAL_CAPACITY);
        blocks = new ArrayDeque<>();
        pool = new ArrayDeque<>();
        largePool = new ArrayDeque<>();
    }

    Batch newBatch() {
        return new Batch();
    }

    int blockSize() {
        return blockSize;
    }

    /**
     * Set the most bytes kept in blocks bigger than blockSize. Only the first call has effect.
     * The connection calls it with the largest entry the server accepts: max payload + max control line + CRLF.
     */
    void setLargePoolMax(long max) {
        lock.lock();
        try {
            if (largePoolMax == 0 && max > 0) {
                largePoolMax = max;
            }
        }
        finally {
            lock.unlock();
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Caller side
    // ----------------------------------------------------------------------------------------------------

    /**
     * Serialize a publish into the data lane.
     * @return true if queued, false if discarded because the outgoing queue is full and discard is configured
     * @throws IllegalStateException if the reconnect buffer is exceeded, the queue stays full for the push timeout,
     *         the lock cannot be had within the push timeout, the caller is interrupted, or the buffer is closed
     * @throws IllegalArgumentException if client side limit checks are on and the payload or control line is too big
     */
    boolean publish(String subject, @Nullable String replyTo, @Nullable Headers headers, byte[] payload, boolean flush,
                    boolean checkReconnectBuffer, long maxPayload, int maxControlLine, boolean limitChecks) {
        byte[] subjectUtf8 = utf8IfNeeded(subject);
        int subjectLen = subjectUtf8 == null ? subject.length() : subjectUtf8.length;
        byte[] replyUtf8 = replyTo == null ? null : utf8IfNeeded(replyTo);
        int replyLen = replyTo == null ? 0 : (replyUtf8 == null ? replyTo.length() : replyUtf8.length);
        int hdrLen = headers == null ? 0 : headers.serializedLength();
        int total = hdrLen + payload.length;
        int controlLen = (hdrLen > 0 ? HPUB_SP_BYTES.length : PUB_SP_BYTES.length)
            + subjectLen + 1
            + (replyLen > 0 ? replyLen + 1 : 0)
            + (hdrLen > 0 ? digits(hdrLen) + 1 : 0)
            + digits(total) + 2;
        int size = controlLen + total + 2;

        long startNanos = NatsSystemClock.nanoTime();
        acquire();
        try {
            requireNotClosed();
            if (checkReconnectBuffer && reconnectBufferSize >= 0 && queuedBytes + size >= reconnectBufferSize) {
                throw new IllegalStateException(
                    "Unable to queue any more messages during reconnect, max buffer is " + reconnectBufferSize);
            }
            if (limitChecks) {
                if (maxPayload > 0 && total > maxPayload) {
                    throw new IllegalArgumentException(
                        "Message payload size exceed server configuration " + total + " vs " + maxPayload);
                }
                if (controlLen > maxControlLine) {
                    throw new IllegalArgumentException("Control line is too long");
                }
            }
            if (!awaitRoom(discardWhenFull, startNanos)) {
                return false;
            }

            Lane lane = dataBlockFor(size);
            byte[] b = lane.bytes;
            int pos = lane.length;
            if (hdrLen > 0) {
                pos = put(b, pos, HPUB_SP_BYTES);
            }
            else {
                pos = put(b, pos, PUB_SP_BYTES);
            }
            pos = subjectUtf8 == null ? putAscii(b, pos, subject) : put(b, pos, subjectUtf8);
            b[pos++] = SP;
            if (replyLen > 0) {
                pos = replyUtf8 == null ? putAscii(b, pos, replyTo) : put(b, pos, replyUtf8);
                b[pos++] = SP;
            }
            if (hdrLen > 0) {
                pos = putInt(b, pos, hdrLen);
                b[pos++] = SP;
            }
            pos = putInt(b, pos, total);
            b[pos++] = CR;
            b[pos++] = LF;
            if (hdrLen > 0) {
                pos += headers.serializeToArray(pos, b);
            }
            System.arraycopy(payload, 0, b, pos, payload.length);
            pos += payload.length;
            b[pos++] = CR;
            b[pos++] = LF;
            lane.length = pos;
            appended(lane, size, false, flush);
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Queue a SUB.
     * @param internal true for a resubscribe or other library traffic, which is never discarded and goes
     *                 to the control lane while the data lane is held back
     * @return false if discarded
     * @throws IllegalArgumentException if client side limit checks are on and the control line is too big
     */
    boolean sub(String subject, @Nullable String queueGroup, String sid, boolean internal, int maxControlLine, boolean limitChecks) {
        byte[] s = subject.getBytes(UTF_8);
        byte[] q = queueGroup == null ? null : queueGroup.getBytes(UTF_8);
        int size = SUB_SP.length + s.length + 1 + (q == null ? 0 : q.length + 1) + sid.length() + 2;
        if (limitChecks && size > maxControlLine) {
            throw new IllegalArgumentException("Control line is too long");
        }
        long startNanos = NatsSystemClock.nanoTime();
        acquire();
        try {
            requireNotClosed();
            Lane lane = target(internal, false, size, startNanos);
            if (lane == null) {
                return false;
            }
            byte[] b = lane.bytes;
            int pos = put(b, lane.length, SUB_SP);
            pos = put(b, pos, s);
            b[pos++] = SP;
            if (q != null) {
                pos = put(b, pos, q);
                b[pos++] = SP;
            }
            pos = putAscii(b, pos, sid);
            b[pos++] = CR;
            b[pos++] = LF;
            lane.length = pos;
            appended(lane, size, true, false);
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Queue an UNSUB. Never internal, so it goes to the data lane and can be discarded, like the classic writer.
     * @return false if discarded
     */
    boolean unsub(String sid, int after) {
        int size = UNSUB_SP.length + sid.length() + (after > 0 ? 1 + digits(after) : 0) + 2;
        long startNanos = NatsSystemClock.nanoTime();
        acquire();
        try {
            requireNotClosed();
            Lane lane = target(false, false, size, startNanos);
            if (lane == null) {
                return false;
            }
            byte[] b = lane.bytes;
            int pos = put(b, lane.length, UNSUB_SP);
            pos = putAscii(b, pos, sid);
            if (after > 0) {
                b[pos++] = SP;
                pos = putInt(b, pos, after);
            }
            b[pos++] = CR;
            b[pos++] = LF;
            lane.length = pos;
            appended(lane, size, true, false);
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Queue a protocol line given as bytes without its CRLF.
     * @param pong true puts it on the control lane, which is how PONG skips ahead of queued data
     * @return false if discarded
     */
    boolean protocol(byte[] line, int len, boolean filterOnStop, boolean internal, boolean pong) {
        int size = len + 2;
        long startNanos = NatsSystemClock.nanoTime();
        acquire();
        try {
            if (closed) {
                if (pong) {
                    return true; // nothing to answer once closed
                }
                requireNotClosed();
            }
            Lane lane = target(internal, pong, size, startNanos);
            if (lane == null) {
                return false;
            }
            System.arraycopy(line, 0, lane.bytes, lane.length, len);
            int pos = lane.length + len;
            lane.bytes[pos++] = CR;
            lane.bytes[pos++] = LF;
            lane.length = pos;
            appended(lane, size, filterOnStop, false);
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    // Under the lock. The lane to append to, with room for size bytes, or null when the entry is discarded.
    // Internal traffic goes to the control lane while the data lane is held back, the same way the classic
    // writer sends internal messages to its reconnect queue while in reconnect mode.
    private @Nullable Lane target(boolean internal, boolean pong, int size, long startNanos) {
        if (pong || (internal && !dataOpen)) {
            control.ensureCapacity(size);
            return control;
        }
        if (!awaitRoom(!internal && discardWhenFull, startNanos)) {
            return null;
        }
        return dataBlockFor(size);
    }

    // Under the lock. The last block if the entry fits, otherwise a new block at the end of the queue.
    private Lane dataBlockFor(int size) {
        Lane tail = blocks.peekLast();
        if (tail != null && !tail.flush) {
            if (tail.fits(size)) {
                return tail;
            }
            if (tail.length == 0) {
                tail.bytes = new byte[size]; // an empty block too small for a single big entry
                return tail;
            }
        }
        Lane block = size <= blockSize ? pool.pollFirst() : largeBlockFor(size);
        if (block == null) {
            block = new Lane(size <= blockSize ? blockSize : size);
        }
        blocks.addLast(block);
        return block;
    }

    // Under the lock. A kept large block big enough for size, if there is one.
    private @Nullable Lane largeBlockFor(int size) {
        Iterator<Lane> it = largePool.iterator();
        while (it.hasNext()) {
            Lane block = it.next();
            if (block.bytes.length >= size) {
                it.remove();
                largePoolBytes -= block.bytes.length;
                return block;
            }
        }
        return null;
    }

    // Under the lock. Keep a written or emptied block for reuse, or let it go.
    private void keep(Lane block) {
        block.clear();
        if (closed) {
            return;
        }
        int capacity = block.bytes.length;
        if (capacity == blockSize) {
            if (pool.size() < poolMax) {
                pool.addLast(block);
            }
        }
        else if (capacity > blockSize && capacity <= largePoolMax) {
            // never trade a bigger kept block for a smaller one: only smaller ones make room
            Iterator<Lane> it = largePool.iterator();
            while (largePoolBytes + capacity > largePoolMax && it.hasNext()) {
                Lane kept = it.next();
                if (kept.bytes.length < capacity) {
                    it.remove();
                    largePoolBytes -= kept.bytes.length;
                }
            }
            if (largePoolBytes + capacity <= largePoolMax) {
                largePool.addLast(block);
                largePoolBytes += capacity;
            }
        }
    }

    // Under the lock, after the bytes are in the lane.
    private void appended(Lane lane, int size, boolean filterOnStop, boolean flush) {
        lane.addEntry(size, filterOnStop);
        if (flush) {
            lane.flush = true;
        }
        if (lane == control) {
            if (writerWaiting) {
                writerWake.signal();
            }
        }
        else {
            queuedCount++;
            queuedBytes += size;
            dataChanged();
        }
    }

    private void dataChanged() {
        pendingCount = queuedCount;
        pendingBytes = queuedBytes;
        if (dataOpen && writerWaiting) {
            writerWake.signal();
        }
    }

    private void acquire() {
        try {
            if (!lock.tryLock(pushTimeoutNanos, TimeUnit.NANOSECONDS)) {
                throw new IllegalStateException(OUTPUT_QUEUE_BUSY + pendingCount);
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(OUTPUT_QUEUE_INTERRUPTED + pendingCount, e);
        }
    }

    private void requireNotClosed() {
        if (closed) {
            throw new IllegalStateException("NatsConnection is Closed");
        }
    }

    // Under the lock. True when there is room in the data lane, false when the entry is to be discarded.
    private boolean awaitRoom(boolean canDiscard, long startNanos) {
        if (queuedCount < maxPending) {
            return true;
        }
        if (canDiscard) {
            return false;
        }
        long elapsed = NatsSystemClock.nanoTime() - startNanos;
        long remaining = pushTimeoutNanos - elapsed;
        long minNanos = MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT * NANOS_PER_MILLI;
        remaining = remaining < minNanos ? minNanos : remaining;
        while (queuedCount >= maxPending) {
            requireNotClosed();
            if (remaining <= 0) {
                throw new IllegalStateException(OUTPUT_QUEUE_IS_FULL + queuedCount);
            }
            publishersWaiting++;
            try {
                remaining = notFull.awaitNanos(remaining);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(OUTPUT_QUEUE_INTERRUPTED + queuedCount, e);
            }
            finally {
                publishersWaiting--;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------
    // Writer side
    // ----------------------------------------------------------------------------------------------------

    /**
     * Wait for work, then move it into the batch: the whole control lane (swapped with the batch's empty
     * one), and the oldest data block. Return the block with {@link #recycle(Lane)} once written.
     * @param running the calling writer's running flag, checked while waiting
     * @return false when the writer should stop
     * @throws InterruptedException if the writer thread is interrupted while waiting
     */
    boolean take(Batch batch, BooleanSupplier running) throws InterruptedException {
        lock.lock();
        try {
            while (running.getAsBoolean() && !closed
                && (pausedForTest || (control.length == 0 && !(dataOpen && queuedCount > 0))))
            {
                writerWaiting = true;
                try {
                    writerWake.await();
                }
                finally {
                    writerWaiting = false;
                }
            }
            if (!running.getAsBoolean() || closed) {
                return false;
            }
            if (control.length > 0) {
                Lane t = control;
                control = batch.control;
                batch.control = t;
            }
            if (dataOpen && queuedCount > 0) {
                Lane block = blocks.pollFirst();
                if (block != null) {
                    queuedCount -= block.count;
                    queuedBytes -= block.length;
                    batch.data = block;
                    pendingCount = queuedCount;
                    pendingBytes = queuedBytes;
                    if (publishersWaiting > 0) {
                        notFull.signalAll();
                    }
                }
            }
            return true;
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Give a written data block back for reuse.
     */
    void recycle(Lane block) {
        lock.lock();
        try {
            keep(block);
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Wake a writer waiting in take so it can see it was stopped.
     */
    void wakeWriter() {
        lock.lock();
        try {
            writerWake.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    // ----------------------------------------------------------------------------------------------------
    // Connection side
    // ----------------------------------------------------------------------------------------------------

    /**
     * Let the writer send the data lane. The equivalent of the classic writer reaching END_RECONNECT.
     */
    void openData() {
        lock.lock();
        try {
            dataOpen = true;
            if (queuedCount > 0 && writerWaiting) {
                writerWake.signal();
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Called when the socket is gone. Holds back the data lane, empties the control lane, and removes
     * the filter on stop entries from the data lane, which keeps the publishes made before or during
     * the disconnect for the next connection. A block the writer had already taken is not here; it was
     * written or lost with the socket.
     */
    void disconnected() {
        lock.lock();
        try {
            dataOpen = false;
            control.clear();
            queuedCount = 0;
            queuedBytes = 0;
            Iterator<Lane> it = blocks.iterator();
            while (it.hasNext()) {
                Lane block = it.next();
                block.filter();
                if (block.count == 0) {
                    it.remove();
                    keep(block);
                }
                else {
                    queuedCount += block.count;
                    queuedBytes += block.length;
                }
            }
            dataChanged();
            if (publishersWaiting > 0) {
                notFull.signalAll();
            }
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Discard everything and refuse further entries. Wakes any waiting writer and publisher.
     */
    void close() {
        lock.lock();
        try {
            closed = true;
            dataOpen = false;
            control = new Lane(CONTROL_INITIAL_CAPACITY);
            blocks.clear();
            pool.clear();
            largePool.clear();
            largePoolBytes = 0;
            queuedCount = 0;
            queuedBytes = 0;
            pendingCount = 0;
            pendingBytes = 0;
            writerWake.signalAll();
            notFull.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: stop the writer taking anything, so the lanes fill up. Routing is unchanged.
    void pauseForTest() {
        lock.lock();
        try {
            pausedForTest = true;
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: undo pauseForTest
    void resumeForTest() {
        lock.lock();
        try {
            pausedForTest = false;
            writerWake.signalAll();
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: the data lane's bytes, all blocks in order
    byte[] dataBytesForTest() {
        lock.lock();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Lane block : blocks) {
                out.write(block.bytes, 0, block.length);
            }
            return out.toByteArray();
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: the number of blocks in the data lane
    int dataBlockCountForTest() {
        lock.lock();
        try {
            return blocks.size();
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: the capacity of each kept large block, in pool order
    int[] largePoolForTest() {
        lock.lock();
        try {
            return largePool.stream().mapToInt(b -> b.bytes.length).toArray();
        }
        finally {
            lock.unlock();
        }
    }

    // For testing: a copy of the control lane's bytes
    byte[] controlBytesForTest() {
        lock.lock();
        try {
            return Arrays.copyOf(control.bytes, control.length);
        }
        finally {
            lock.unlock();
        }
    }

    long pendingCount() {
        return pendingCount;
    }

    long pendingBytes() {
        return pendingBytes;
    }

    // ----------------------------------------------------------------------------------------------------
    // Encoding helpers
    // ----------------------------------------------------------------------------------------------------

    static byte @Nullable [] utf8IfNeeded(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) {
                return s.getBytes(UTF_8);
            }
        }
        return null;
    }

    static int put(byte[] dest, int pos, byte[] src) {
        System.arraycopy(src, 0, dest, pos, src.length);
        return pos + src.length;
    }

    static int putAscii(byte[] dest, int pos, String s) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            dest[pos++] = (byte) s.charAt(i);
        }
        return pos;
    }

    static int putInt(byte[] dest, int pos, int value) {
        int end = pos + digits(value);
        int p = end;
        do {
            dest[--p] = (byte) ('0' + (value % 10));
            value /= 10;
        }
        while (value > 0);
        return end;
    }

    static int digits(int value) {
        int d = 1;
        while (value >= 10) {
            value /= 10;
            d++;
        }
        return d;
    }
}
