package io.synadia.client.utils;

import io.synadia.client.Message;
import io.synadia.client.global.NatsSystemClock;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.NatsConstants.NANOS_PER_MILLI;

/**
 * This is an internal class and is only public for access.
 */
public class NatsRequestCompletableFuture extends CompletableFuture<Message> {

    // allows a small buffer to account for communication and code execution time, probably more than needed but...
    private static final long HYDRATION_TIME_NANOS = 10 * NANOS_PER_MILLI;

    /**
     * What the connection does with an outstanding request future when the request times out
     * or the connection closes before a reply arrives.
     */
    public enum CancelAction {
        /** Complete the future exceptionally and drop the entry without telling the error listener. */
        CANCEL,

        /** Complete the future exceptionally and also report the unhandled request to the error listener. */
        REPORT,

        /** Complete the future normally with a null message instead of an exception. */
        COMPLETE
    }

    private static final String CLOSING_MESSAGE = "Future cancelled, connection closing.";
    private static final String CANCEL_MESSAGE = "Future cancelled, response not registered in time, check connection status.";

    private final CancelAction cancelAction;
    private final long timeOutAfterNanoTime;
    private boolean wasCanceledClosing;
    private boolean wasCanceledTimedOut;
    private final boolean useTimeoutException;

    /**
     * Construct a future for a single request/reply exchange. A small fixed grace period is added to the
     * timeout to allow for communication and code execution time.
     * @param cancelAction what to do with this future if it is not satisfied in time
     * @param timeoutMillis how long from now, in milliseconds, until this future is considered timed out
     * @param useTimeoutException true to fail with a TimeoutException, false to fail with a CancellationException
     */
    public NatsRequestCompletableFuture(@NonNull CancelAction cancelAction, long timeoutMillis, boolean useTimeoutException) {
        this.cancelAction = cancelAction;
        timeOutAfterNanoTime = NatsSystemClock.nanoTime() + HYDRATION_TIME_NANOS + (timeoutMillis * NANOS_PER_MILLI);
        this.useTimeoutException = useTimeoutException;
    }

    /**
     * Fail this future because the connection is closing. Always a CancellationException,
     * regardless of the useTimeoutException setting.
     */
    public void cancelClosing() {
        wasCanceledClosing = true;
        completeExceptionally(new CancellationException(CLOSING_MESSAGE));
    }

    /**
     * Fail this future because no reply arrived in time, with either a TimeoutException
     * or a CancellationException per the useTimeoutException setting.
     */
    public void cancelTimedOut() {
        wasCanceledTimedOut = true;
        completeExceptionally(
            useTimeoutException
                ? new TimeoutException(CANCEL_MESSAGE)
                : new CancellationException(CANCEL_MESSAGE));
    }

    /**
     * The disposition the connection applies to this future when it is not satisfied in time.
     * @return the cancel action
     */
    @NonNull
    public CancelAction getCancelAction() {
        return cancelAction;
    }

    /**
     * Whether a timeout failure is reported as a TimeoutException rather than a CancellationException.
     * @return true if a TimeoutException is used
     */
    public boolean useTimeoutException() {
        return useTimeoutException;
    }

    /**
     * Whether this future is past its deadline. Checked by the connection's periodic request cleanup.
     * @return true if the deadline has passed
     */
    public boolean hasExceededTimeout() {
        return NatsSystemClock.nanoTime() > timeOutAfterNanoTime;
    }

    /**
     * Whether this future was failed by {@link #cancelClosing()}.
     * @return true if cancelled because the connection was closing
     */
    public boolean wasCanceledClosing() {
        return wasCanceledClosing;
    }

    /**
     * Whether this future was failed by {@link #cancelTimedOut()}.
     * @return true if cancelled because the deadline passed
     */
    public boolean wasCanceledTimedOut() {
        return wasCanceledTimedOut;
    }
}
