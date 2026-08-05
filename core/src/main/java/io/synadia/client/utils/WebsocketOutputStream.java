package io.synadia.client.utils;

import io.synadia.client.utils.WebsocketFrameHeader.OpCode;

import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.Random;

/**
 * Wraps an output stream so that everything written to it goes out as websocket binary frames.
 * Closing writes a close frame before closing the wrapped stream.
 */
public class WebsocketOutputStream extends OutputStream {
    private OutputStream wrap;
    private boolean masked;
    private byte[] oneByte = new byte[1];
    /**
     * Minimize fragmenting, by using a 1440 buffer size. This buffer is used to
     * store the websocket header + payload for the first write.
     *
     * We are using this size since a typical MTU for ethernet is 1500 and the
     * TCP/IPv6 header consumes 60 bytes, thus by using 1440 we should minimize
     * the need for further fragmentation at the lower layers and yet minimize
     * the overhead associated with the fact that a write to the Socket (which
     * has NO_DELAY set on it) results in a single TCP/IPv6 datagram which has
     * a 60 byte overhead.
     *
     * Note that the effective payload is less due to the websocket frame overhead:
     * 4 bytes header/length + 4 bytes masking. Which would be a payload
     * size of 1432. This means any write which is larger than 1432 will be
     * fragmented into two socket writes and will thus have another 60 bytes
     * of TCP/IPv6 datagram header overhead.
     */
    private byte[] headerBuffer = new byte[1440];
    private WebsocketFrameHeader header = new WebsocketFrameHeader()
        .withOp(OpCode.BINARY, true)
        .withNoMask();
    private Random random = new SecureRandom();

    /**
     * Construct a websocket output stream over another stream.
     * @param wrap the stream the frames are written to
     * @param masked whether to mask the payload, which the websocket spec requires of a client
     */
    public WebsocketOutputStream(OutputStream wrap, boolean masked) {
        this.wrap = wrap;
        this.masked = masked;
    }

    @Override
    public void close() throws IOException {
        // NOTE: Per spec, we should technically wait to receive the close frame,
        // but we are not in control of the InputStream here...
        WebsocketFrameHeader header = new WebsocketFrameHeader()
            .withOp(OpCode.CLOSE, true)
            .withNoMask()
            .withPayloadLength(0);
        int length = header.read(headerBuffer, 0, headerBuffer.length);
        wrap.write(headerBuffer, 0, length);
        wrap.close();
    }

    @Override
    public void flush() throws IOException {
        wrap.flush();
    }

    @Override
    public void write(byte[] buffer) throws IOException {
        write(buffer, 0, buffer.length);
    }

    @Override
    public void write(int b) throws IOException {
        oneByte[0] = (byte)(b & 0xFF);
        write(oneByte, 0, 1);
    }

    /**
     * NOTE: the buffer will be modified if masking is enabled and the length is greater
     * than 1432. Regardless of if masking is enabled or not, any writes of length &gt; 1432
     * will be split into two writes to the underlying OutputStream which is being wrapped.
     */
    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        header.withPayloadLength(length);
        if (masked) {
            header.withMask(random.nextInt());
        }
        int headerBufferOffset = header.read(headerBuffer, 0, headerBuffer.length);
        int consumed = Math.min(length, headerBuffer.length - headerBufferOffset);
        System.arraycopy(buffer, offset, headerBuffer, headerBufferOffset, consumed);

        header.filterPayload(headerBuffer, headerBufferOffset, consumed);
        wrap.write(headerBuffer, 0, headerBufferOffset + consumed);
        if (consumed < length) {
            // NOTE: We could perform a "mark" operation before filtering the
            // payload which saves the payloadLength and maskingKeyOffset, then
            // perform a "resetMark" operation after writing the masked buffer
            // and finally re-applying the filter in order to preserve the
            // original values of the bytes. However, there appears to be no
            // code which relies on the bytes in the buffer to be preserved,
            // so we are keeping things efficient by not performing these
            // operations.
            header.filterPayload(buffer, offset + consumed, length - consumed);
            wrap.write(buffer, offset + consumed, length - consumed);
        }
    }

}
