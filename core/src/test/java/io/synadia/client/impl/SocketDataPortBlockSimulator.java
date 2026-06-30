package io.synadia.client.impl;

import io.synadia.client.ForceReconnectOptions;
import io.synadia.client.Options;
import io.synadia.client.global.NatsSystemClock;
import io.synadia.client.utils.NatsUri;
import io.synadia.client.utils.ScheduledTask;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.synadia.client.OptionsConstants.DEFAULT_SOCKET_WRITE_TIMEOUT;
import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;

@SuppressWarnings("ClassEscapesDefinedScope") // NatsConnection
public class SocketDataPortBlockSimulator extends SocketDataPort {

    private long writeTimeoutNanos;
    private long delayPeriodNanos;
    private ScheduledTask writeWatchTask;
    private final AtomicLong writeMustBeDoneBy;

    public SocketDataPortBlockSimulator() {
        this.writeMustBeDoneBy = new AtomicLong(Long.MAX_VALUE);
    }

    @Override
    public void afterConstruct(@NonNull Options options) {
        // intentionally NOT calling super.afterConstruct: this simulator runs its own write-watch,
        // so we keep the base SocketDataPort's writeTimeoutNanos at 0 (no second, redundant watch).
        long millis = options.getSocketWriteTimeout();
        writeTimeoutNanos = (millis <= 0 ? DEFAULT_SOCKET_WRITE_TIMEOUT : millis) * NANOS_PER_MILLI;
        delayPeriodNanos = writeTimeoutNanos * 51 / 100;
    }

    @Override
    public void connect(@NonNull NatsConnection conn, @NonNull NatsUri nuri, long timeoutNanos) throws IOException {
        super.connect(conn, nuri, timeoutNanos);
        writeWatchTask = new ScheduledTask(conn.getScheduledExecutor(), delayPeriodNanos, TimeUnit.NANOSECONDS,
            () -> {
                //  if now is after when it was supposed to be done by
                if (NatsSystemClock.nanoTime() > writeMustBeDoneBy.get()) {
                    writeWatchTask.shutdown(); // we don't need to repeat this, the connection is going to be closed
                    connection.notifyErrorListener((c, el) -> el.socketWriteTimeout(c));
                    blocking.set(0);
                    SIMULATE_SOCKET_BLOCK.set(0);
                    try {
                        connection.forceReconnect(ForceReconnectOptions.FORCE_CLOSE_INSTANCE);
                    }
                    catch (IOException e) {
                        // retry maybe?
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // This task is going to re-run anyway, so no point in throwing
                    }
                }
            });
    }

    private static final AtomicLong SIMULATE_SOCKET_BLOCK = new AtomicLong();

    public static void simulateBlock() {
        SIMULATE_SOCKET_BLOCK.set(60000);
    }

    AtomicLong blocking = new AtomicLong();
    public void write(byte[] src, int toWrite) throws IOException {
        try {
            writeMustBeDoneBy.set(NatsSystemClock.nanoTime() + writeTimeoutNanos);
            blocking.set(SIMULATE_SOCKET_BLOCK.get());
            while (blocking.get() > 0) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(100);
                    blocking.addAndGet(-100);
                }
                catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            out.write(src, 0, toWrite);
        }
        finally {
            writeMustBeDoneBy.set(Long.MAX_VALUE);
        }
    }

    public void close() throws IOException {
        writeWatchTask.shutdown();
        super.close();
    }
}
