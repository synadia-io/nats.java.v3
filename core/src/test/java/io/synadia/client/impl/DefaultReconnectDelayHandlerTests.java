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
        assertInWaitRange(actual, jitterMs, "");
    }

    private static void assertInWaitRange(long actual, long jitterMs, String desc) {
        assertTrue(actual >= WAIT_MS, desc + " expected >= " + WAIT_MS + " but was " + actual);
        assertTrue(actual < WAIT_MS + jitterMs, desc + " expected < " + (WAIT_MS + jitterMs) + " but was " + actual);
    }

    private final ReconnectDelayHandler h = DefaultReconnectDelayHandler.INSTANCE;

    // ---------- The handler answers "how long", never "whether" ----------
    // Whether a round gets a delay at all is the connection's decision, driven by
    // ReconnectDelayBehavior - see ReconnectTests. The handler returns the standard wait for
    // every round it is asked about, so behavior, round number and the lame duck flag must not
    // change what comes back. Only the secure flag does, by selecting which jitter applies.

    @Test
    public void sameWaitForEveryBehaviorRoundAndLameDuckFlag() {
        for (ReconnectDelayBehavior behavior : ReconnectDelayBehavior.values()) {
            Options o = optsFor(behavior);
            for (long round : new long[]{1L, 2L, 17L}) {
                for (boolean lameDuck : new boolean[]{false, true}) {
                    String desc = behavior + " round=" + round + " lameDuck=" + lameDuck;
                    assertInWaitRange(h.getWaitTimeMillis(round, o, false, lameDuck), JITTER_MS, desc);
                    assertInWaitRange(h.getWaitTimeMillis(round, o, true, lameDuck), JITTER_TLS_MS, desc + " secure");
                }
            }
        }
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
            .reconnectJitterTls(10000L)
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
