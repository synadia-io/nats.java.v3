package io.synadia.client.impl;

import io.synadia.client.Options;
import io.synadia.client.OptionsBuilder;
import io.synadia.client.ReconnectDelayBehavior;
import io.synadia.client.ReconnectDelayHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultReconnectDelayHandlerTests {

    private static final long WAIT_MS = 200L;
    private static final long JITTER_MS = 50L;
    private static final long JITTER_TLS_MS = 500L;

    private static Options optsFor(ReconnectDelayBehavior behavior) {
        return new OptionsBuilder()
            .reconnectWait(WAIT_MS)
            .reconnectJitter(JITTER_MS)
            .reconnectJitterTls(JITTER_TLS_MS)
            .reconnectDelayBehavior(behavior)
            .build();
    }

    private static void assertInWaitRange(long actual, long jitterMs) {
        assertTrue(actual >= WAIT_MS, "expected >= " + WAIT_MS + " but was " + actual);
        assertTrue(actual < WAIT_MS + jitterMs, "expected < " + (WAIT_MS + jitterMs) + " but was " + actual);
    }

    private final ReconnectDelayHandler h = DefaultReconnectDelayHandler.INSTANCE;

    // ---------- BeforeSubsequentRounds (historical v2 semantics) ----------

    @Test
    public void beforeSubsequentRounds_round1_noLdm_noWait() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeSubsequentRounds);
        assertEquals(0L, h.getWaitTimeMillis(1L, o, false, false));
    }

    @Test
    public void beforeSubsequentRounds_round1_withLdm_stillNoWait() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeSubsequentRounds);
        assertEquals(0L, h.getWaitTimeMillis(1L, o, false, true));
    }

    @Test
    public void beforeSubsequentRounds_round2_waits() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeSubsequentRounds);
        assertInWaitRange(h.getWaitTimeMillis(2L, o, false, false), JITTER_MS);
    }

    @Test
    public void beforeSubsequentRounds_round2_securePicksTlsJitter() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeSubsequentRounds);
        assertInWaitRange(h.getWaitTimeMillis(2L, o, true, false), JITTER_TLS_MS);
    }

    // ---------- BeforeAllRounds ----------

    @Test
    public void beforeAllRounds_round1_noLdm_waits() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeAllRounds);
        assertInWaitRange(h.getWaitTimeMillis(1L, o, false, false), JITTER_MS);
    }

    @Test
    public void beforeAllRounds_round1_withLdm_waits() {
        Options o = optsFor(ReconnectDelayBehavior.BeforeAllRounds);
        assertInWaitRange(h.getWaitTimeMillis(1L, o, false, true), JITTER_MS);
    }

    // ---------- LameDuckAware (the new default) ----------

    @Test
    public void lameDuckAware_round1_noLdm_noWait() {
        Options o = optsFor(ReconnectDelayBehavior.LameDuckAware);
        assertEquals(0L, h.getWaitTimeMillis(1L, o, false, false));
    }

    @Test
    public void lameDuckAware_round1_withLdm_waits() {
        Options o = optsFor(ReconnectDelayBehavior.LameDuckAware);
        assertInWaitRange(h.getWaitTimeMillis(1L, o, false, true), JITTER_MS);
    }

    @Test
    public void lameDuckAware_round2_waits() {
        Options o = optsFor(ReconnectDelayBehavior.LameDuckAware);
        assertInWaitRange(h.getWaitTimeMillis(2L, o, false, false), JITTER_MS);
    }

    // ---------- computeWaitMillis directly (also covers the "fix" from PLAN_DURATION_TO_MILLIS) ----------

    @Test
    public void computeWaitMillis_jitterOnly_zeroWait_stillProducesRandomness() {
        Options o = new OptionsBuilder()
            .reconnectWait(0L)
            .reconnectJitter(JITTER_MS)
            .build();
        for (int i = 0; i < 50; i++) {
            long v = DefaultReconnectDelayHandler.computeWaitMillis(o, false);
            assertTrue(v >= 0L && v < JITTER_MS, "expected [0, " + JITTER_MS + ") but was " + v);
        }
    }

    @Test
    public void computeWaitMillis_noJitter_returnsExactWait() {
        Options o = new OptionsBuilder()
            .reconnectWait(WAIT_MS)
            .reconnectJitter(0L)
            .build();
        assertEquals(WAIT_MS, DefaultReconnectDelayHandler.computeWaitMillis(o, false));
    }

    @Test
    public void computeWaitMillis_secureFlagPicksTlsJitter() {
        Options o = new OptionsBuilder()
            .reconnectWait(0L)
            .reconnectJitter(10L)
            .reconnectJitterTls(10_000L)
            .build();
        // Run many times and at least one secure=true draw should exceed the non-TLS jitter ceiling.
        long maxSecure = 0L;
        for (int i = 0; i < 200; i++) {
            maxSecure = Math.max(maxSecure, DefaultReconnectDelayHandler.computeWaitMillis(o, true));
        }
        assertTrue(maxSecure > 10L, "secure=true should sample from the larger TLS jitter pool; max seen " + maxSecure);
    }

    @Test
    public void computeWaitMillis_negativeWait_clampsToZero() {
        Options o = new OptionsBuilder()
            .reconnectWait(-50L)
            .reconnectJitter(0L)
            .build();
        assertEquals(0L, DefaultReconnectDelayHandler.computeWaitMillis(o, false));
    }

    // ---------- Singleton sanity ----------

    @Test
    public void instance_isShareable() {
        assertSame(DefaultReconnectDelayHandler.INSTANCE, DefaultReconnectDelayHandler.INSTANCE);
        Options a = new OptionsBuilder().build();
        Options b = new OptionsBuilder().build();
        assertSame(a.getReconnectDelayHandler(), b.getReconnectDelayHandler());
    }
}
