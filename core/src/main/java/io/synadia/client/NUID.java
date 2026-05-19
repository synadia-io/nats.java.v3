// Copied from 1.0 Java client for consistency and expedience.

package io.synadia.client;

import java.util.concurrent.locks.ReentrantLock;

import static io.synadia.client.utils.RandomUtils.*;

/**
 * A highly performant unique identifier generator. The library uses this to generate
 * an inbox for request-replies. Applications can use their own NUID to generate
 * subjects as well. A shareable Global instance is also available via {@link #nextGlobal nextGlobal()}.
 */
public final class NUID {
    /*
     * NUID needs to be very fast to generate and truly unique, all while being
     * entropy pool friendly. We will use 12 bytes of crypto generated data (entropy
     * draining), and 10 bytes of sequential data that is started at a pseudo random
     * number and increments with a pseudo-random increment. Total is 22 bytes of
     * base 62 ascii text :)
     */

    // Constants
    static final char[] digits = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G',
            'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b',
            'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w',
            'x', 'y', 'z' };
    static final int base = 62;
    static final int preLen = 12;
    static final int seqLen = 10;
    static final long maxSeq = 839299365868340224L; // base^seqLen == 62^10
    static final long minInc = 33L;
    static final long maxInc = 333L;
    static final int totalLen = preLen + seqLen;

    // Instance fields
    char[] pre;
    private long seq;
    private long inc;

    private static final NUID globalNUID;
    private final ReentrantLock nextLock;

    static {
        globalNUID = new NUID();
    }

    static NUID getInstance() {
        return globalNUID;
    }

    /**
     * The default NUID constructor.
     * 
     * Relies on the OS Default instance of SecureRandom.
     * 
     * @throws IllegalStateException
     *                                   if the class cannot find the necessary
     *                                   SecureRandom instance.
     */
    public NUID() {
        nextLock = new ReentrantLock();
        // Generate a cryto random int, 0 <= val < max to seed pseudorandom
        seq = nextLong(PRAND, maxSeq);
        inc = minInc + nextLong(PRAND, maxInc - minInc);
        pre = new char[preLen];
        for (int i = 0; i < preLen; i++) {
            pre[i] = '0';
        }
        randomizePrefix();
    }

    /**
     * the next NUID string from a shared global NUID instance
     * @return the next NUID string from a shared global NUID instance
     */
    public static String nextGlobal() {
        return globalNUID.next();
    }

    /**
     * the next sequence portion of the NUID string from a shared global NUID instance
     * @return the next sequence portion of the NUID string from a shared global NUID instance
     */
    public static String nextGlobalSequence() {
        return globalNUID.nextSequence();
    }

    /**
     * Generate the next NUID string from this instance.
     *
     * @return the next NUID string from this instance.
     */
    public String next() {
        nextLock.lock();
        try {
            // Increment and capture.
            seq += inc;
            if (seq >= maxSeq) {
                randomizePrefix();
                resetSequential();
            }

            // Copy prefix
            char[] b = new char[totalLen];
            System.arraycopy(pre, 0, b, 0, preLen);

            // copy in the seq
            int i = b.length;
            for (long l = seq; i > preLen; l /= base) {
                b[--i] = digits[(int) (l % base)];
            }
            return new String(b);
        }
        finally {
            nextLock.unlock();
        }
    }

    /**
     * Generate the next NUID string from this instance and return only the sequence portion.
     * @return the next sequence portion of the NUID string from a shared global NUID instance
     */
    public String nextSequence() {
        nextLock.lock();
        try {
            // Increment and capture.
            seq += inc;
            if (seq >= maxSeq) {
                randomizePrefix();
                resetSequential();
            }

            char[] b = new char[seqLen];
            // copy in the seq
            int ix = seqLen;
            for (long l = seq; ix > 0; l /= base) {
                b[--ix] = digits[(int) (l % base)];
            }
            return new String(b);
        }
        finally {
            nextLock.unlock();
        }
    }

    // Resets the sequential portion of the NUID
    void resetSequential() {
        seq = nextLong(PRAND, maxSeq);
        inc = minInc + nextLong(PRAND, maxInc - minInc);
    }

    /*
     * Generate a new prefix from random. This *can* drain entropy and will be
     * called automatically when we exhaust the sequential range.
     */

    final void randomizePrefix() {
        byte[] cb = new byte[preLen];

        // Use SecureRandom for prefix only
        SRAND.nextBytes(cb);

        for (int i = 0; i < preLen; i++) {
            pre[i] = digits[(cb[i] & 0xFF) % base];
        }
    }

    /**
     * The pre portion of the NUID
     * @return the pre
     */
    char[] getPre() {
        return pre;
    }

    /**
     * Return the current sequence value.
     * @return the seq
     */
    long getSeq() {
        return seq;
    }

    /**
     * Set the sequence to the supplied value.
     * @param seq the seq to set
     */
    void setSeq(long seq) {
        this.seq = seq;
    }
}
