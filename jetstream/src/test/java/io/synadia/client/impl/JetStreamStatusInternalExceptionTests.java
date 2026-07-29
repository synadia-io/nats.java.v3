package io.synadia.client.impl;

import io.synadia.client.api.JetStreamException;
import io.synadia.client.api.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the package-private internal status exception (EXCEPTIONS_AUDIT §6a, A12/A13).
 * Lives in {@code io.synadia.client.impl} because {@link JetStreamStatusInternalException} is not
 * public — it is thrown internally and bridged to the user-facing {@link JetStreamStatusException}.
 */
public class JetStreamStatusInternalExceptionTests {

    // The internal/user status split is real: the internal type is unchecked and out of hierarchy.
    // All three assertions are Class.isAssignableFrom(...) on a fixed class hierarchy, so they're
    // statically decidable:
    //     - assertTrue(RuntimeException.class.isAssignableFrom(...)) → always true (it extends StatusException → IllegalStateException →
    // RuntimeException)
    //     - assertTrue(StatusException.class.isAssignableFrom(...)) → always true (it extends StatusException)
    //     - assertFalse(JetStreamException.class.isAssignableFrom(...)) → always false (unrelated branch)
    //
    // They test the compiler, not the code. They'd only "fail" if someone re-parented the class — and a deliberate re-parent would just get the
    // test updated, while most accidental ones wouldn't compile. It's an architecture-guard with near-zero yield.
    @Test
    public void testStatusInternalIsUncheckedAndOutOfHierarchy() {
        // JetStreamStatusInternalException stays unchecked (extends StatusException -> IllegalStateException)
        assertTrue(RuntimeException.class.isAssignableFrom(JetStreamStatusInternalException.class));
        assertTrue(StatusException.class.isAssignableFrom(JetStreamStatusInternalException.class));
        // ...and is deliberately NOT part of the checked JetStreamException hierarchy — it can't be,
        // because core Subscription.nextMessage only declares throws InterruptedException.
        assertFalse(JetStreamException.class.isAssignableFrom(JetStreamStatusInternalException.class));
    }

    // The internal exception bridges into the user-facing JetStreamStatusException, carrying its status/note.
    @Test
    public void testStatusExceptionFromInternalCause() {
        Status st = status(409, "Consumer Deleted");
        JetStreamStatusInternalException internal = new JetStreamStatusInternalException("Fetch", st, null);
        JetStreamStatusException ex = new JetStreamStatusException(internal);
        assertSame(st, ex.getStatus());
        assertEquals("Fetch", ex.getNote());
        assertNull(ex.getSubscription());
    }

    private static Status status(int code, String message) {
        return new Status(code, message);
    }
}
