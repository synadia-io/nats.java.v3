package io.synadia.client.testutils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * A class that wraps a ByteBuffer that can automatically grow
 */
public class ByteArrayBuilder {

    /**
     * Allocation boundary
     */
    public static final int ALLOCATION_BOUNDARY = 32;

    /**
     * Default allocation for ASCII or ISO_8859_1 charset
     */
    public static final int DEFAULT_ASCII_ALLOCATION = 32;

    /**
     * Default allocation for other charsets
     */
    public static final int DEFAULT_OTHER_ALLOCATION = 64;

    /**
     * a byte array representing the word "null"
     */
    public static final byte[] NULL = "null".getBytes(ISO_8859_1);

    /**
     * The default character set
     */
    private final Charset defaultCharset;

    /**
     * The allocation size
     */
    protected int allocationSize;

    private ByteBuffer buffer;

    /**
     * Construct the ByteArrayBuilder with the supplied initial size,
     * allocation size and character set
     * @param initialSize the initial size
     * @param allocationSizeSuggestion the allocationSize size suggestion
     * @param defaultCharset the default character set
     */
    public ByteArrayBuilder(int initialSize, int allocationSizeSuggestion, Charset defaultCharset) {
        this.defaultCharset = defaultCharset;
        _setAllocationSize(allocationSizeSuggestion);
        this.buffer = ByteBuffer.allocate(bufferAllocSize(initialSize, allocationSize));
    }

    /**
     * Construct the ByteArrayBuilder with
     * the initial size and allocation size of {@value #DEFAULT_ASCII_ALLOCATION}
     * and the character set {@link java.nio.charset.StandardCharsets#ISO_8859_1}
     * since ISO_8859_1 is faster when encoding and decoding than ISO_8859_1
     */
    public ByteArrayBuilder() {
        this(-1, DEFAULT_ASCII_ALLOCATION, ISO_8859_1);
    }

    /**
     * Construct the ByteArrayBuilder with the supplied initial size,
     * allocation size of {@value #DEFAULT_ASCII_ALLOCATION}
     * and the character set {@link java.nio.charset.StandardCharsets#ISO_8859_1}
     *
     * @param initialSize the initial size
     */
    public ByteArrayBuilder(int initialSize) {
        this(initialSize, DEFAULT_ASCII_ALLOCATION, ISO_8859_1);
    }

    /**
     * Construct the ByteArrayBuilder with the supplied character set
     * with the default initial size and allocation size determined by that character set
     *
     * @param defaultCharset the default character set
     */
    public ByteArrayBuilder(Charset defaultCharset) {
        this(-1, -1, defaultCharset);
    }

    /**
     * Construct the ByteArrayBuilder with the supplied initial size and character set
     * with the allocation size determined by that character set.
     *
     * @param initialSize the initial size
     * @param defaultCharset the default character set
     */
    public ByteArrayBuilder(int initialSize, Charset defaultCharset) {
        this(initialSize, -1, defaultCharset);
    }

    /**
     * Construct the ByteArrayBuilder copying all the bytes
     * using the character set {@link java.nio.charset.StandardCharsets#ISO_8859_1}
     * and the character set {@link java.nio.charset.StandardCharsets#ISO_8859_1}
     * Then initializes the buffer with the supplied bytes
     * @param bytes the bytes
     */
    public ByteArrayBuilder(byte[] bytes) {
        this(bytes.length, DEFAULT_ASCII_ALLOCATION, ISO_8859_1);
        buffer.put(bytes, 0, bytes.length);
    }

    /**
     * Construct the ByteArrayBuilder copying the specified number of bytes;
     * and the character set {@link java.nio.charset.StandardCharsets#ISO_8859_1}
     * Then initializes the buffer with the supplied bytes
     * @param bytes the bytes
     * @param len the number of bytes to copy
     */
    public ByteArrayBuilder(byte[] bytes, int len) {
        this(len, DEFAULT_ASCII_ALLOCATION, ISO_8859_1);
        buffer.put(bytes, 0, len);
    }

    /**
     * Get the length of the data in the buffer
     * @return the length of the data
     */
    public int length() {
        return buffer.position();
    }

    /**
     * Get the number of bytes currently allocated (available) without resizing
     * @return the number of bytes
     */
    public int capacity() {
        return buffer.capacity();
    }

    /**
     * Determine if a byte array contains the same bytes as this builder
     * @param bytes the bytes
     * @return true if the supplied value equals what is in the builder
     */
    public boolean equals(byte[] bytes) {
        if (bytes == null || buffer.position() != bytes.length) {
            return false;
        }
        byte[] hb = buffer.array();
        for (int x = 0; x < bytes.length; x++) {
            if (hb[x] != bytes[x]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Copy the contents of the buffer to the byte array starting at the destination
     * positions supplied. Assumes that the {@link #length} method has been called
     * and the destination byte array has enough space allocated
     * @param dest the destination byte array
     * @param destPos the starting position in the destination byte array
     * @return the number of bytes copied
     */
    public int copyTo(byte[] dest, int destPos) {
        int len = length();
        byte[] hb = buffer.array();
        System.arraycopy(hb, 0, dest, destPos, len);
        return len;
    }

    /**
     * Copy the contents of the buffer to the output stream
     * @param out the output stream
     * @throws IOException if an I/O error occurs
     */
    public void copyTo(OutputStream out) throws IOException {
        out.write(buffer.array(), 0, buffer.position());
    }

    /**
     * Copy the value in the buffer to a new byte array
     * @return the copy of the bytes
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    /**
     * Access the internal byte array of this buffer. Intended for read only
     * with knowledge of {@link #length()}
     * @return a direct handle to the internal byte array
     */
    public byte[] internalArray() {
        return buffer.array();
    }

    /**
     * Ensures that the buffer can accept the number of bytes needed
     * Useful if the size of multiple append operations is known ahead of time
     * @param bytesNeeded the number of bytes needed
     * @return this (fluent)
     */
    public ByteArrayBuilder ensureCapacity(int bytesNeeded) {
        int bytesAvailable = buffer.capacity() - buffer.position();
        if (bytesAvailable < bytesNeeded) {
            ByteBuffer newBuffer = ByteBuffer.allocate(
                bufferAllocSize(buffer.position() + bytesNeeded, allocationSize));
            newBuffer.put(buffer.array(), 0, buffer.position());
            buffer = newBuffer;
        }
        return this;
    }

    /**
     * Clear the buffer, resetting its length but not capacity
     * @return this (fluent)
     */
    public ByteArrayBuilder clear() {
        buffer.clear();
        return this;
    }

    /**
     * Change the allocation size
     * @param allocationSizeSuggestion the new allocation size suggestion
     * @return this (fluent)
     */
    public ByteArrayBuilder setAllocationSize(int allocationSizeSuggestion) {
        _setAllocationSize(allocationSizeSuggestion);
        return this;
    }

    /**
     * Append a String representation of the number.
     * @param  i the number
     * @return this (fluent)
     */
    public ByteArrayBuilder append(int i) {
        append(Integer.toString(i).getBytes(ISO_8859_1)); // a number is always ascii (ISO_8859_1 is faster)
        return this;
    }

    /**
     * Append a String with the default charset.
     * If the src is null, the word 'null' is appended.
     * @param src The String from which bytes are to be read
     * @return this (fluent)
     */
    public ByteArrayBuilder append(String src) {
        return append(src, defaultCharset);
    }

    /**
     * Append a String with specified charset.
     * If the src is null, the word 'null' is appended.
     * @param src The String from which bytes are to be read
     * @param charset the charset for encoding
     * @return this (fluent)
     */
    public ByteArrayBuilder append(String src, Charset charset) {
        return src == null ? append(NULL, 0, 4) : append(src.getBytes(charset));
    }

    /**
     * Append a CharBuffer with default charset.
     * If the src is null, the word 'null' is appended.
     * @param src The CharBuffer from which bytes are to be read
     * @return this (fluent)
     */
    public ByteArrayBuilder append(CharBuffer src) {
        return append(src, defaultCharset);
    }

    /**
     * Append a CharBuffer with specified charset.
     * If the src is null, the word 'null' is appended.
     * @param src The CharBuffer from which bytes are to be read
     * @param charset the charset for encoding
     * @return this (fluent)
     */
    public ByteArrayBuilder append(CharBuffer src, Charset charset) {
        if (src == null) {
            append(NULL, 0, 4);
        }
        else {
            append(src.toString().getBytes(charset));
        }
        return this;
    }

    /**
     * Append a byte as is
     * @param b the byte
     * @return this (fluent)
     */
    public ByteArrayBuilder append(byte b) {
        ensureCapacity(1);
        buffer.put(b);
        return this;
    }

    /**
     * Append an entire byte array
     * @param src The array from which bytes are to be read
     * @return this (fluent)
     */
    public ByteArrayBuilder append(byte[] src) {
        if (src.length > 0) {
            ensureCapacity(src.length);
            buffer.put(src, 0, src.length);
        }
        return this;
    }

    /**
     * Append a byte array
     * @param src The array from which bytes are to be read
     * @param  len The number of bytes to be read from the given array
     * @return this (fluent)
     */
    public ByteArrayBuilder append(byte[] src, int len) {
        if (len > 0) {
            ensureCapacity(len);
            buffer.put(src, 0, len);
        }
        return this;
    }

    /**
     * Append a byte array
     * @param src The array from which bytes are to be read
     * @param  offset The offset within the array of the first byte to be read;
     * @param  len The number of bytes to be read from the given array;
     * @return this (fluent)
     */
    public ByteArrayBuilder append(byte[] src, int offset, int len) {
        if (len > 0) {
            ensureCapacity(len);
            buffer.put(src, offset, len);
        }
        return this;
    }

    /**
     * Appends the data bytes from an existing byte array builder
     * @param bab an existing builder
     * @return this (fluent)
     */
    public ByteArrayBuilder append(ByteArrayBuilder bab) {
        if (bab != null && bab.length() > 0) {
            append(bab.buffer.array(), 0, bab.length());
        }
        return this;
    }

    /**
     * Append a single byte without checking that the builder has the capacity
     * @param b the byte
     * @return the number of bytes appended, always 1
     */
    public int appendUnchecked(byte b) {
        buffer.put(b);
        return 1;
    }

    /**
     * Append the entire byte array without checking that the builder has the capacity
     * @param src the source byte array
     * @return the number of bytes appended
     */
    public int appendUnchecked(byte[] src) {
        buffer.put(src, 0, src.length);
        return src.length;
    }

    /**
     * Append the entire byte array without checking that the builder has the capacity
     * @param src the source byte array
     * @param srcPos starting position in the source array.
     * @param len the number of array elements to be copied.
     * @return the number of bytes appended
     */
    public int appendUnchecked(byte[] src, int srcPos, int len) {
        buffer.put(src, srcPos, len);
        return len;
    }

    /**
     * Get the current allocation size
     * @return the allocation size
     */
    public int getAllocationSize() {
        return allocationSize;
    }

    @Override
    public String toString() {
        return new String(buffer.array(), 0, buffer.position(), defaultCharset);
    }

    private int _defaultCharsetAllocationSize() {
        return defaultCharset == US_ASCII || defaultCharset == ISO_8859_1 ? DEFAULT_ASCII_ALLOCATION : DEFAULT_OTHER_ALLOCATION;
    }

    /**
     * Internal delegate method to set the allocationSizeSuggestion
     * @param allocationSizeSuggestion the suggestion
     */
    private void _setAllocationSize(int allocationSizeSuggestion) {
        int dcas = _defaultCharsetAllocationSize();
        if (allocationSizeSuggestion <= dcas) {
            allocationSize = dcas;
        }
        else {
            allocationSize = bufferAllocSize(allocationSizeSuggestion, ALLOCATION_BOUNDARY);
        }
    }

    /**
     * calculate a buffer allocation size
     * @param atLeast the allocation must be at least
     * @param blockSize the blocksize
     * @return the allocation size
     */
    public static int bufferAllocSize(int atLeast, int blockSize) {
        return atLeast < blockSize
            ? blockSize
            : ((atLeast + blockSize) / blockSize) * blockSize;
    }
}
