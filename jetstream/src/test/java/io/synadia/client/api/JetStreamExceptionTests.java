package io.synadia.client.api;

import io.synadia.client.impl.JetStreamApiException;
import io.synadia.client.impl.JetStreamStatusException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for the v3 JetStreamException hierarchy (EXCEPTIONS_AUDIT A10–A14).
 * Deterministic and server-independent — they assert the type shape, not runtime behavior.
 */
public class JetStreamExceptionTests {

    // ----------------------------------------------------------------------------------------------------
    // Hierarchy: catch-base catches every user-facing subtype (D1/D2, A10/A11)
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testCatchBaseCatchesAllSubtypes() {
        // the base is a checked exception, not a RuntimeException
        assertTrue(Exception.class.isAssignableFrom(JetStreamException.class));
        assertFalse(RuntimeException.class.isAssignableFrom(JetStreamException.class));

        // every user-facing subtype IS-A JetStreamException, so catch(JetStreamException) covers them
        assertInstanceOf(JetStreamException.class, new JetStreamApiException(err(500, "boom")));
        assertInstanceOf(JetStreamException.class, new JetStreamTimeoutException("t"));
        assertInstanceOf(JetStreamException.class, new JetStreamProtocolException("p"));
        assertInstanceOf(JetStreamException.class, new JetStreamStatusException("n", status(503, "No Responders"), null));
    }

    // ----------------------------------------------------------------------------------------------------
    // JetStreamApiException carries the Error (A11 — getError added)
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testApiExceptionCarriesError() {
        Error e = err(500, "boom");
        JetStreamApiException ex = new JetStreamApiException(e);
        assertSame(e, ex.getError());
        assertEquals(e.toString(), ex.getMessage());
        assertEquals(e.getCode(), ex.getErrorCode());
        assertEquals(e.getApiErrorCode(), ex.getApiErrorCode());
        assertEquals(e.getDescription(), ex.getErrorDescription());
    }

    // ----------------------------------------------------------------------------------------------------
    // Message-only subtypes (A6 timeout, A8 protocol)
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testMessageOnlySubtypes() {
        assertEquals("no response", new JetStreamTimeoutException("no response").getMessage());
        assertEquals("Invalid JetStream ack.", new JetStreamProtocolException("Invalid JetStream ack.").getMessage());
    }

    // ----------------------------------------------------------------------------------------------------
    // JetStreamStatusException carries the Status and builds "note: code message" (A7)
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testStatusExceptionCarriesStatus() {
        Status st = status(503, "No Responders Available For Request");
        JetStreamStatusException ex = new JetStreamStatusException("Error Publishing", st, null);
        assertSame(st, ex.getStatus());
        assertEquals("Error Publishing", ex.getNote());
        assertNull(ex.getSubscription());
        assertEquals("Error Publishing: " + st.getMessageWithCode(), ex.getMessage());
    }

    // ----------------------------------------------------------------------------------------------------
    // The catch-base + pattern-switch ergonomics the migration guide documents (D2)
    // ----------------------------------------------------------------------------------------------------
    @Test
    public void testPatternSwitchDispatch() {
        assertEquals("api:500", classify(new JetStreamApiException(err(500, "boom"))));
        assertEquals("status:503", classify(new JetStreamStatusException("n", status(503, "x"), null)));
        assertEquals("timeout", classify(new JetStreamTimeoutException("t")));
        assertEquals("protocol", classify(new JetStreamProtocolException("p")));
        assertEquals("base", classify(new JetStreamException("plain")));   // the default arm — non-sealed base
    }

    private static String classify(JetStreamException e) {
        return switch (e) {
            case JetStreamApiException api    -> "api:" + api.getErrorCode();
            case JetStreamStatusException st  -> "status:" + st.getStatus().getCode();
            case JetStreamTimeoutException t  -> "timeout";
            case JetStreamProtocolException p -> "protocol";
            default                           -> "base";
        };
    }

    private static Status status(int code, String message) {
        return new Status(code, message);
    }

    private static Error err(int code, String description) {
        return Error.convert(new Status(code, description));
    }
}
