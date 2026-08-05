package io.synadia.client.utils;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *
 * WARNING: THIS CLASS IS PUBLIC BUT ITS API IS NOT GUARANTEED TO *
 * BE BACKWARD COMPATIBLE AS IT IS INTENDED AS AN INTERNAL CLASS  *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! *
 */
public class ScheduledTask implements Runnable {
    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final String id;
    private final Runnable runnable;
    protected final AtomicReference<ScheduledFuture<?>> scheduledFutureRef;

    protected final AtomicBoolean notShutdown;
    protected final AtomicBoolean executing;
    protected final long initialDelayNanos;
    protected final long periodNanos;

    /**
     * Construct and immediately schedule a task with a generated id, using the same value
     * for the initial delay and the period.
     * @param ses the executor the task is scheduled on
     * @param initialAndPeriodMillis the initial delay and the period between executions, in milliseconds
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(ScheduledExecutorService ses, long initialAndPeriodMillis, Runnable runnable) {
        this(null, ses, initialAndPeriodMillis, initialAndPeriodMillis, TimeUnit.MILLISECONDS, runnable);
    }

    /**
     * Construct and immediately schedule a task, using the same value for the initial delay and the period.
     * @param id the task id, null or empty gets a generated id
     * @param ses the executor the task is scheduled on
     * @param initialAndPeriodMillis the initial delay and the period between executions, in milliseconds
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(String id, ScheduledExecutorService ses, long initialAndPeriodMillis, Runnable runnable) {
        this(id, ses, initialAndPeriodMillis, initialAndPeriodMillis, TimeUnit.MILLISECONDS, runnable);
    }

    /**
     * Construct and immediately schedule a task with a generated id, using the same value
     * for the initial delay and the period.
     * @param ses the executor the task is scheduled on
     * @param initialAndPeriod the initial delay and the period between executions, in the given unit
     * @param unit the time unit of initialAndPeriod
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(ScheduledExecutorService ses, long initialAndPeriod, TimeUnit unit, Runnable runnable) {
        this(null, ses, initialAndPeriod, initialAndPeriod, unit, runnable);
    }

    /**
     * Construct and immediately schedule a task, using the same value for the initial delay and the period.
     * @param id the task id, null or empty gets a generated id
     * @param ses the executor the task is scheduled on
     * @param initialAndPeriod the initial delay and the period between executions, in the given unit
     * @param unit the time unit of initialAndPeriod
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(String id, ScheduledExecutorService ses, long initialAndPeriod, TimeUnit unit, Runnable runnable) {
        this(id, ses, initialAndPeriod, initialAndPeriod, unit, runnable);
    }

    /**
     * Construct and immediately schedule a task with a generated id.
     * @param ses the executor the task is scheduled on
     * @param initialDelay the delay before the first execution, in the given unit
     * @param period the period between executions, in the given unit
     * @param unit the time unit of initialDelay and period
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(ScheduledExecutorService ses, long initialDelay, long period, TimeUnit unit, Runnable runnable) {
        this(null, ses, initialDelay, period, unit, runnable);
    }

    /**
     * Construct and immediately schedule a task at a fixed rate.
     * @param id the task id, null or empty gets a generated id
     * @param ses the executor the task is scheduled on
     * @param initialDelay the delay before the first execution, in the given unit
     * @param period the period between executions, in the given unit
     * @param unit the time unit of initialDelay and period
     * @param runnable the work to execute on each period
     */
    public ScheduledTask(String id, ScheduledExecutorService ses, long initialDelay, long period, TimeUnit unit, Runnable runnable) {
        this.id = id == null || id.isEmpty() ? "st-" + ID_GENERATOR.getAndIncrement() : id;
        this.runnable = runnable;
        notShutdown = new AtomicBoolean(true);
        executing = new AtomicBoolean(false);
        this.initialDelayNanos = unit.toNanos(initialDelay);
        this.periodNanos = unit.toNanos(period);
        scheduledFutureRef = new AtomicReference<>(
            ses.scheduleAtFixedRate(this, initialDelayNanos, periodNanos, TimeUnit.NANOSECONDS));
    }

    /**
     * The delay before the first execution, converted to nanoseconds from the unit given at construction.
     * @return the initial delay in nanoseconds
     */
    public long getInitialDelayNanos() {
        return initialDelayNanos;
    }

    /**
     * The period between executions, converted to nanoseconds from the unit given at construction.
     * @return the period in nanoseconds
     */
    public long getPeriodNanos() {
        return periodNanos;
    }

    @Override
    public void run() {
        try {
            if (notShutdown.get()) {
                executing.set(true);
                runnable.run();
            }
        }
        finally {
            executing.set(false);
        }
    }

    /**
     * Whether {@link #shutdown()} has been called. Once shut down the runnable is never executed again.
     * @return true if the task has been shut down
     */
    public boolean isShutdown() {
        return !notShutdown.get();
    }

    /**
     * Whether the runnable is executing at this instant.
     * @return true if the runnable is currently in progress
     */
    public boolean isExecuting() {
        return executing.get();
    }

    /**
     * Whether the underlying scheduled future has completed or been cancelled.
     * Also true once the future has been released by shutdown.
     * @return true if the task will not run again
     */
    public boolean isDone() {
        ScheduledFuture<?> f = scheduledFutureRef.get();
        return f == null || f.isDone();
    }

    /**
     * The id supplied at construction, or a generated {@code st-&lt;n&gt;} id when none was supplied.
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Stop the task from executing again and cancel the scheduled future without interrupting
     * an execution already in progress. Exceptions raised while cancelling are swallowed.
     */
    public void shutdown() {
        try {
            notShutdown.set(false);
            ScheduledFuture<?> f = scheduledFutureRef.get();
            if (f != null) {
                scheduledFutureRef.set(null); // just releasing resources.
                if (!f.isDone()) {
                    f.cancel(false);
                }
            }
        }
        catch (Exception ignore) {
            // don't want this to be passed along
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(id);
        if (notShutdown.get()) {
            sb.append(" [live");
        }
        else {
            sb.append(" [shutdown");
        }
        sb.append(isDone() ? "/done" : "/!done");
        sb.append(executing.get() ? "/executing" : "/!executing");
        sb.append("]");
        return sb.toString();
    }
}
