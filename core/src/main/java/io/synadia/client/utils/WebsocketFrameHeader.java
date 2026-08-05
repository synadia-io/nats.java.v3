package io.synadia.client.utils;

/**
 * A single websocket frame header, as defined by RFC 6455. Instances are mutable and reusable:
 * build one with the {@code with...} methods to send a frame, or overwrite one from the wire with
 * {@link #write(byte[], int, int)} to receive a frame.
 */
public class WebsocketFrameHeader {
    /** Largest number of bytes a websocket frame header can occupy: 2 fixed bytes, 8 for a 64 bit payload length and 4 for a masking key. */
    public static int MAX_FRAME_HEADER_SIZE = 14;

    /**
     * Create a header with no opcode, no mask and a zero payload length.
     */
    public WebsocketFrameHeader() {}

    /**
     * The frame opcode, which identifies how the frame payload is to be interpreted.
     */
    public enum OpCode {
        /** The frame carries more of the payload of the preceding text or binary frame. */
        CONTINUATION(0),
        /** The frame payload is UTF-8 text. */
        TEXT(1),
        /** The frame payload is binary data. */
        BINARY(2),
        /** The peer is closing the connection. */
        CLOSE(8),
        /** A heartbeat request, which the peer answers with {@link #PONG}. */
        PING(9),
        /** The answer to a {@link #PING}. */
        PONG(10),
        /** A code that is not one of the opcodes this client understands. */
        UNKNOWN(0x10);

        private int code;

        OpCode(int code) {
            this.code = code;
        }

        /**
         * The wire value of this opcode. {@link #UNKNOWN} reports 0x10, which is outside the 4 bit
         * on-the-wire opcode field and so can never collide with a real code.
         * @return the code
         */
        public int getCode() {
            return this.code;
        }

        /**
         * Look up the opcode for a wire value.
         * @param code the wire value
         * @return the matching opcode, or {@link #UNKNOWN} if the value is not a recognized opcode
         */
        public static OpCode of(int code) {
            switch (code) {
                case 0: return CONTINUATION;
                case 1: return TEXT;
                case 2: return BINARY;
                case 8: return CLOSE;
                case 9: return PING;
                case 10: return PONG;
            }
            return UNKNOWN;
        }
    }

    // Fields of header:
    private byte byte0;
    private boolean mask;
    private long payloadLength;
    private int maskingKey;
    private int maskingKeyOffset = 0;

    /**
     * Set the opcode and the FIN bit, which together make up the first header byte.
     * @param op the opcode
     * @param isFinal true if this frame is the last one of its message, false if a continuation frame follows
     * @return this header, for chaining
     */
    public WebsocketFrameHeader withOp(OpCode op, boolean isFinal) {
        this.byte0 = (byte)(op.getCode() | (isFinal ? 0x80 : 0));
        return this;
    }

    /**
     * Send the payload unmasked. Required for frames sent by a server; clients must mask.
     * @return this header, for chaining
     */
    public WebsocketFrameHeader withNoMask() {
        this.mask = false;
        return this;
    }

    /**
     * Mask the payload with the given key. The key is applied one byte at a time, cycling through
     * the key bytes as the payload is filtered.
     * @param maskingKey the masking key
     * @return this header, for chaining
     */
    public WebsocketFrameHeader withMask(int maskingKey) {
        this.mask = true;
        this.maskingKey = maskingKey;
        this.maskingKeyOffset = 0;
        return this;
    }

    /**
     * Set the number of payload bytes that follow this header. The value decides whether the length
     * is encoded in 7, 16 or 64 bits, and so how many bytes the serialized header occupies.
     * @param payloadLength the payload length in bytes
     * @return this header, for chaining
     */
    public WebsocketFrameHeader withPayloadLength(long payloadLength) {
        this.payloadLength = payloadLength;
        return this;
    }

    /**
     * Whether the FIN bit is set, meaning this frame completes its message.
     * @return true if this is the final frame of a message
     */
    public boolean isFinal() {
        return (byte0 & 0x80) != 0;
    }

    /**
     * Whether the payload is masked and so must be run through {@link #filterPayload(byte[], int, int)}.
     * @return true if the payload is masked
     */
    public boolean isMasked() {
        return mask;
    }

    /**
     * The masking key. Only meaningful when {@link #isMasked()} is true.
     * @return the masking key
     */
    public int getMaskingKey() {
        return maskingKey;
    }

    /**
     * Payload bytes still expected for this frame. Filtering consumed bytes reduces this count.
     * @return the remaining payload length in bytes
     */
    public long getPayloadLength() {
        return payloadLength;
    }

    /**
     * The opcode taken from the low 4 bits of the first header byte.
     * @return the opcode, or {@link OpCode#UNKNOWN} if it is not a recognized code
     */
    public OpCode getOpCode() {
        return OpCode.of(byte0 & 0xF);
    }

    /**
     * Whether the whole payload has been consumed, so the next bytes on the wire start a new frame.
     * @return true if no payload bytes remain
     */
    public boolean isPayloadEmpty() {
        return 0 == payloadLength;
    }

    /**
     * Decrement the payloadLength by at most maxSize such that payloadLength is non-negative,
     * returning the amount decremented.
     * 
     * @param buffer is the buffer to filter.
     * @param offset is the start offset within buffer to filter.
     * @param length is the number of bytes to filter.
     * 
     * @return min(payloadLength, maxSize), decrementing the internal payloadLength by this amount.
     */
    public int filterPayload(byte[] buffer, int offset, int length) {
        length = Math.min(length, payloadLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)payloadLength);
        payloadLength -= length;
        if (mask) {
            for (int i=0; i < length; i++) {
                int key = 0xFF & (maskingKey >> (8 * (7 - maskingKeyOffset)));
                buffer[offset + i] ^= key;
                maskingKeyOffset = (maskingKeyOffset + 1) % 8;
            }
        }
        return length;
    }

    /**
     * How many bytes this header will occupy once serialized, which varies with the payload length
     * encoding and whether a masking key is present.
     * @return the serialized size in bytes, between 2 and 14
     */
    public int size() {
        int size = 2;
        if (payloadLength > 0xFFFF) {
            size += 8;
        } else if (payloadLength > 125) {
            size += 2;
        }
        if (mask) {
            size += 4;
        }
        return size;
    }

    /**
     * Introspects the first 2 bytes of buffer at the specified offset to
     * determine how large the entire header is.
     * 
     * @param buffer is the buffer to introspect
     * @param offset is the offset within the buffer where the websocket
     *     header begins.
     * @return the number of bytes used by the full websocket header.
     */
    public static int size(byte[] buffer, int offset) {
        int size = 2;
        if (0 != (buffer[offset + 1] & 0x80)) {
            // mask adds 4 required bytes.
            size += 4;
        }

        switch (buffer[offset + 1] & 0x7F) {
        case 126:
            size += 2;
            break;
        case 127:
            size += 8;
            break;
        }
        return size;
    }

    /**
     * Serializes this WebsocketFrameHeader into a buffer.
     * 
     * @param buffer where the serialized header will be placed.
     * 
     * @param offset is the start offset into the buffer.
     * 
     * @param length is the max bytes that can be placed in the buffer.
     * 
     * @return 0 if there is insufficient remainder in the buffer, otherwise
     *    returns the number of bytes placed into the buffer.
     */
    public int read(byte[] buffer, int offset, int length) {
        if (length < size()) {
            return 0;
        }

        int startOffset = offset;

        buffer[offset++] = byte0;
        if (payloadLength > 0xFFFF) {
            buffer[offset++] = (byte)(127 | (mask ? 0x80 : 0));
            // 64 bit length
            buffer[offset++] = (byte)((payloadLength >> 56) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 48) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 40) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 32) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 24) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 16) & 0xFF);
            buffer[offset++] = (byte)((payloadLength >> 8) & 0xFF);
            buffer[offset++] = (byte)(payloadLength & 0xFF);
        } else if (payloadLength > 125) {
            buffer[offset++] = (byte)(126 | (mask ? 0x80 : 0));
            // 16 bit length
            buffer[offset++] = (byte)(payloadLength >> 8);
            buffer[offset++] = (byte)(payloadLength & 0xFF);
        } else {
            buffer[offset++] = (byte)(payloadLength | (mask ? 0x80 : 0));
        }
        if (mask) {
            buffer[offset++] = (byte)((maskingKey >> 24) & 0xFF);
            buffer[offset++] = (byte)((maskingKey >> 16) & 0xFF);
            buffer[offset++] = (byte)((maskingKey >> 8) & 0xFF);
            buffer[offset++] = (byte)(maskingKey & 0xFF);
        }
        return offset - startOffset;
    }

    /**
     * Overwrite internal frame header content with input buffer.
     * 
     * <pre>
     * WebsocketFrameHeader header = new WebsocketFrameHeader();
     * int consumedLength = header.write(buffer, offset, length);
     * offset += consumedLength;
     * length -= consumedLength;
     * </pre>
     * 
     * @param buffer containing the serialized header.
     * 
     * @param offset is the start offset into the buffer.
     * 
     * @param length is the max bytes to consume.
     * 
     * @return 0 if there is insufficient remainder in the buffer, otherwise
     *     returns the number of bytes consumed from the buffer.
     */
    public int write(byte[] buffer, int offset, int length) {
        // Sufficient remainder?
        if (length < 2) {
            return 0;
        }
        int size = size(buffer, offset);
        if (size > length) {
            return 0;
        }

        byte0 = buffer[offset++];
        mask = 0 != (buffer[offset] & 0x80);
        payloadLength = buffer[offset] & 0x7F;
        offset++;
        if (126 == payloadLength) {
            payloadLength = 0;
            for (int i=0; i < 2; i++) {
                payloadLength <<= 8;
                payloadLength |= buffer[offset++] & 0xFF;
            }
        } else if (127 == payloadLength) {
            payloadLength = 0;
            for (int i=0; i < 8; i++) {
                payloadLength <<= 8;
                payloadLength |= buffer[offset++] & 0xFF;
            }
        }
        if (mask) {
            maskingKey = 0;
            maskingKeyOffset = 0;
            for (int i=0; i < 4; i++) {
                maskingKey <<= 8;
                maskingKey |= buffer[offset++] & 0xFF;
            }
        }
        return size;
    }
}
