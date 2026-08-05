package io.synadia.client.utils;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Shared random number generators and helpers used where randomness is needed,
 * for instance jitter on reconnect delays and server pool shuffling.
 */
public abstract class RandomUtils {
    private RandomUtils() {}  /* ensures cannot be constructed */

    /** Cryptographically strong generator, used where the value must not be predictable, i.e. inbox ids and nonce signing. */
    public static final SecureRandom SRAND = new SecureRandom();

    /** General purpose generator, seeded from {@link #SRAND}. Much cheaper than {@link #SRAND}, for non-security uses such as jitter. */
    public static final Random PRAND = new Random(bytesToLong(SRAND.generateSeed(8))); // seed with 8 bytes (64 bits)

    /**
     * Get a uniformly distributed value in the range 0 (inclusive) to maxValue (exclusive).
     * There is no error checking; maxValue must be positive.
     * @param rng the generator to draw from
     * @param maxValue the exclusive upper bound
     * @return the random value
     */
    public static long nextLong(Random rng, long maxValue) {
        // error checking and 2^x checking removed for simplicity.
        long bits;
        long val;
        do {
            bits = (rng.nextLong() << 1) >>> 1;
            val = bits % maxValue;
        } while (bits - val + (maxValue - 1) < 0L);
        return val;
    }

    /**
     * Assemble the first 8 bytes of the array into a long, big endian.
     * @param bytes the source bytes, at least 8 of them
     * @return the long value
     */
    public static long bytesToLong(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE);
        buffer.put(bytes);
        buffer.flip();// need flip
        return buffer.getLong();
    }
}
