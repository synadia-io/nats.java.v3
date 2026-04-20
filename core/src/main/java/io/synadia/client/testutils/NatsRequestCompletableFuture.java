package io.synadia.client.testutils;

import io.synadia.client.Message;
import io.synadia.client.Options;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.testutils.NatsConstants.NANOS_PER_MILLI;

/**
 * This is an internal class and is only public for access.
 */
public class NatsRequestCompletableFuture extends CompletableFuture<Message> {

    // allows a small buffer to account for communication and code execution time, probably more than needed but...
    private static final long HYDRATION_TIME = 10 * NANOS_PER_MILLI;

    public enum CancelAction { CANCEL, REPORT, COMPLETE }

    private static final String CLOSING_MESSAGE = "Future cancelled, connection closing.";
    private static final String CANCEL_MESSAGE = "Future cancelled, response not registered in time, check connection status.";
    private static final long DEFAULT_TIMEOUT_NANOS = Options.DEFAULT_REQUEST_CLEANUP_INTERVAL.toNanos(); // currently 5 seconds

    private final CancelAction cancelAction;
    private final long timeOutAfterNanoTime;
    private boolean wasCanceledClosing;
    private boolean wasCanceledTimedOut;
    private final boolean useTimeoutException;

    public NatsRequestCompletableFuture(@NonNull CancelAction cancelAction, @Nullable Duration timeout, boolean useTimeoutException) {
        this.cancelAction = cancelAction;
        timeOutAfterNanoTime = NatsSystemClock.nanoTime() + HYDRATION_TIME + (timeout == null ? DEFAULT_TIMEOUT_NANOS : timeout.toNanos());
        this.useTimeoutException = useTimeoutException;
    }

    public void cancelClosing() {
        wasCanceledClosing = true;
        completeExceptionally(new CancellationException(CLOSING_MESSAGE));
    }

    public void cancelTimedOut() {
        wasCanceledTimedOut = true;
        completeExceptionally(
            useTimeoutException
                ? new TimeoutException(CANCEL_MESSAGE)
                : new CancellationException(CANCEL_MESSAGE));
    }

    @NonNull
    public CancelAction getCancelAction() {
        return cancelAction;
    }

    public boolean useTimeoutException() {
        return useTimeoutException;
    }

    public boolean hasExceededTimeout() {
        return NatsSystemClock.nanoTime() > timeOutAfterNanoTime;
    }

    public boolean wasCanceledClosing() {
        return wasCanceledClosing;
    }

    public boolean wasCanceledTimedOut() {
        return wasCanceledTimedOut;
    }
}
