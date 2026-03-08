package io.nats.client.impl;

import io.nats.client.ForceReconnectOptions;
import io.nats.client.NatsSystemClock;
import io.nats.client.Options;
import io.nats.client.support.NatsUri;
import io.nats.client.support.ScheduledTask;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is not thread-safe.  Caller must ensure thread safety.
 */
@SuppressWarnings("ClassEscapesDefinedScope") // NatsConnection
public class SocketDataPortWithWriteTimeout extends SocketDataPort {

    private long writeTimeoutNanos;
    private long delayPeriodNanos;
    private ScheduledTask writeWatchTask;
    private final AtomicLong writeMustBeDoneBy;

    public SocketDataPortWithWriteTimeout() {
        writeMustBeDoneBy = new AtomicLong(Long.MAX_VALUE);
    }

    @Override
    public void afterConstruct(@NonNull Options options) {
        super.afterConstruct(options);
        writeTimeoutNanos = options.getSocketWriteTimeout() == null
            ? Options.DEFAULT_SOCKET_WRITE_TIMEOUT.toNanos()
            : options.getSocketWriteTimeout().toNanos();
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

    public void write(byte[] src, int toWrite) throws IOException {
        writeMustBeDoneBy.set(NatsSystemClock.nanoTime() + writeTimeoutNanos);
        out.write(src, 0, toWrite);
        writeMustBeDoneBy.set(Long.MAX_VALUE);
    }

    public void close() throws IOException {
        writeWatchTask.shutdown();
        super.close();
    }
}
