package io.synadia.client.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * This is a utility class for making digesting data.
 */
public class Digester {
    /** Digest algorithm used when none is supplied, {@value}. */
    public static final String DEFAULT_DIGEST_ALGORITHM = "SHA-256";

    /** Charset used to turn a String into bytes when none is supplied, UTF-8. */
    public static final Charset DEFAULT_STRING_ENCODING = StandardCharsets.UTF_8;

    private final Charset stringCharset;
    private final Base64.Encoder encoder;
    private final MessageDigest digest;
    private String digestValue;

    /**
     * Construct a digester with all defaults: {@value #DEFAULT_DIGEST_ALGORITHM}, UTF-8 and
     * the URL-safe Base64 encoder.
     * @throws NoSuchAlgorithmException if the default algorithm is not available in this JVM
     */
    public Digester() throws NoSuchAlgorithmException {
        this(null, null, null);
    }

    /**
     * Construct a digester with a specific Base64 encoder, defaulting the rest.
     * @param encoder the encoder used to render the digest value, null for the URL-safe encoder
     * @throws NoSuchAlgorithmException if the default algorithm is not available in this JVM
     */
    public Digester(Base64.Encoder encoder) throws NoSuchAlgorithmException {
        this(null, null, encoder);
    }

    /**
     * Construct a digester with a specific algorithm, defaulting the rest.
     * @param digestAlgorithm a {@link MessageDigest} algorithm name, null for
     *                        {@value #DEFAULT_DIGEST_ALGORITHM}
     * @throws NoSuchAlgorithmException if the algorithm is not available in this JVM
     */
    public Digester(String digestAlgorithm) throws NoSuchAlgorithmException {
        this(digestAlgorithm, null, null);
    }

    /**
     * Construct a fully specified digester. Any argument may be null to take its default.
     * @param digestAlgorithm a {@link MessageDigest} algorithm name, null for
     *                        {@value #DEFAULT_DIGEST_ALGORITHM}
     * @param stringCharset the charset used to convert String input to bytes, null for UTF-8
     * @param encoder the encoder used to render the digest value, null for the URL-safe encoder
     * @throws NoSuchAlgorithmException if the algorithm is not available in this JVM
     */
    public Digester(String digestAlgorithm, Charset stringCharset, Base64.Encoder encoder) throws NoSuchAlgorithmException {
        this.stringCharset = stringCharset == null ? DEFAULT_STRING_ENCODING : stringCharset;
        this.encoder = encoder == null ? Base64.getUrlEncoder() : encoder;
        this.digest = MessageDigest.getInstance(
            digestAlgorithm == null ? DEFAULT_DIGEST_ALGORITHM : digestAlgorithm);
    }

    /**
     * Add a String to the running digest, encoding it with this digester's charset.
     * @param input the data to add
     * @return this digester, for chaining
     */
    public Digester update(String input) {
        digest.update(input.getBytes(stringCharset));
        digestValue = null;
        return this;
    }

    /**
     * Add bytes to the running digest.
     * @param input the data to add
     * @return this digester, for chaining
     */
    public Digester update(byte[] input) {
        digest.update(input);
        digestValue = null;
        return this;
    }

    /**
     * Add part of a byte array to the running digest.
     * @param input the array holding the data
     * @param offset the index of the first byte to add
     * @param len the number of bytes to add
     * @return this digester, for chaining
     */
    public Digester update(byte[] input, int offset, int len) {
        digest.update(input, offset, len);
        digestValue = null;
        return this;
    }

    /**
     * Discard everything accumulated so far so the digester can be reused.
     * @return this digester, for chaining
     */
    public Digester reset() {
        digest.reset();
        digestValue = null;
        return this;
    }

    /**
     * Reset, then start a fresh digest over the given String.
     * @param input the data to digest
     * @return this digester, for chaining
     */
    public Digester reset(String input) {
        return reset().update(input);
    }

    /**
     * Reset, then start a fresh digest over the given bytes.
     * @param input the data to digest
     * @return this digester, for chaining
     */
    public Digester reset(byte[] input) {
        return reset().update(input);
    }

    /**
     * Reset, then start a fresh digest over part of a byte array.
     * @param input the array holding the data
     * @param offset the index of the first byte to digest
     * @param len the number of bytes to digest
     * @return this digester, for chaining
     */
    public Digester reset(byte[] input, int offset, int len) {
        return reset().update(input, offset, len);
    }

    /**
     * The encoded digest of everything accumulated so far. Computed on first call and cached
     * until the next update or reset. Note that reading the value finishes the underlying
     * {@link MessageDigest}, which resets it.
     * @return the Base64 encoded digest
     */
    public String getDigestValue() {
        if (digestValue == null) {
            digestValue = encoder.encodeToString(digest.digest());
        }
        return digestValue;
    }

    /**
     * The digest in the {@code ALGORITHM=value} wire form used by the object store digest header.
     * @return the algorithm name, an equals sign, and the encoded digest
     */
    public String getDigestEntry() {
        return digest.getAlgorithm() + "=" + getDigestValue();
    }

    /**
     * Compare an {@code ALGORITHM=value} entry against this digest. The algorithm name is
     * compared case insensitively, the digest value exactly.
     * @param digestEntry the entry to compare, in the form produced by {@link #getDigestEntry()}
     * @return true if the algorithm and the digest value both match
     */
    public boolean matches(String digestEntry) {
        String algo = digest.getAlgorithm().toUpperCase();
        if (!digestEntry.toUpperCase().startsWith(algo)) {
            return false;
        }
        return getDigestValue().equals(digestEntry.substring(algo.length() + 1)); // + 1 for equals
    }
}
