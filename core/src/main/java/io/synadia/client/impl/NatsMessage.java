package io.synadia.client.impl;

import io.synadia.client.Message;
import io.synadia.client.Subscription;
import io.synadia.client.api.Status;
import io.synadia.client.utils.ByteArrayBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static io.synadia.client.utils.NatsConstants.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NatsMessage implements Message {

    protected static final String NOT_A_JET_STREAM_MESSAGE = "Message is not a JetStream message";

    // the real data
    protected String subject;
    protected String replyTo;
    protected byte[] data;
    protected Headers headers;

    // incoming specific : subject, replyTo, data and these fields
    protected String sid;
    protected int controlLineLength;

    // protocol specific : just this field
    ByteArrayBuilder protocolBab;

    // housekeeping
    protected int sizeInBytes;
    protected int headerLen;
    protected int dataLen;

    protected NatsSubscription subscription;

    // for accumulate
    protected NatsMessage next;
    protected boolean flushImmediatelyAfterPublish;

    // ack tracking
    protected AckType lastAck;

    // ----------------------------------------------------------------------------------------------------
    // Constructors - Prefer to use Builder
    // ----------------------------------------------------------------------------------------------------
    protected NatsMessage(byte @Nullable[] data) {
        this(data, null, null, null, false);
    }

    public NatsMessage(@NonNull String subject, @Nullable String replyTo, byte @Nullable[] data) {
        this(data, subject, replyTo, null, false);
    }

    public NatsMessage(@NonNull String subject, @Nullable String replyTo, @Nullable Headers headers, byte @Nullable[] data) {
        this(data, subject, replyTo, headers, false);
    }

    protected NatsMessage(byte @Nullable[] data, @Nullable String subject, @Nullable String replyTo, @Nullable Headers headers, boolean flushImmediatelyAfterPublish) {
        this.data = data == null ? EMPTY_BODY : data;
        dataLen = this.data.length;
        this.subject = subject;
        this.replyTo = replyTo;
        this.headers = headers;
        this.flushImmediatelyAfterPublish = flushImmediatelyAfterPublish;
    }

    // ----------------------------------------------------------------------------------------------------
    // Client and Message Internal Methods
    // ----------------------------------------------------------------------------------------------------
    boolean isProtocol() {
        return false; // overridden in NatsMessage.ProtocolMessage
    }

    boolean isFilterOnStop() {
        return false; // overridden in NatsMessage.ProtocolMessage
    }

    private static final Headers EMPTY_READ_ONLY = new Headers(null, true, null);

    protected void calculate() {
        int replyToLen = replyTo == null ? 0 : replyTo.length();

        // headers get frozen (read only) at this point
        if (headers == null) {
            headerLen = 0;
        }
        else if (headers.isEmpty()) {
            headers = EMPTY_READ_ONLY;
            headerLen = 0;
        }
        else {
            headers = headers.isReadOnly() ? headers : new Headers(headers, true, null);
            headerLen = headers.serializedLength();
        }

        int headerAndDataLen = headerLen + dataLen;

        // initialize the builder with a reasonable length, preventing resize in 99.9% of the cases
        // 32 for misc + subject length doubled in case of utf8 mode + replyToLen + totLen (headerLen + dataLen)
        ByteArrayBuilder bab = new ByteArrayBuilder(32 + (subject.length() * 2) + replyToLen + headerAndDataLen, UTF_8);

        // protocol come first
        if (headerLen > 0) {
            bab.append(HPUB_SP_BYTES, 0, HPUB_SP_BYTES_LEN);
        }
        else {
            bab.append(PUB_SP_BYTES, 0, PUB_SP_BYTES_LEN);
        }

        // next comes the subject
        bab.append(subject.getBytes(UTF_8)).append(SP);

        // reply to if it's there
        if (replyToLen > 0) {
            bab.append(replyTo.getBytes(UTF_8)).append(SP);
        }

        // header length if there are headers
        if (headerLen > 0) {
            bab.append(Integer.toString(headerLen).getBytes(ISO_8859_1)).append(SP);
        }

        // payload length
        bab.append(Integer.toString(headerAndDataLen).getBytes(ISO_8859_1));

        protocolBab = bab;
        controlLineLength = protocolBab.length() + 2; // One CRLF. This is just how controlLineLength is defined.
        sizeInBytes = controlLineLength + headerAndDataLen + 2; // The 2nd CRLFs
    }

    ByteArrayBuilder getProtocolBab() {
        calculate();
        return protocolBab;
    }

    long getSizeInBytes() {
        calculate();
        return sizeInBytes;
    }

    byte[] getProtocolBytes() {
        calculate();
        return protocolBab.toByteArray();
    }

    int getPayloadSize() {
        calculate();
        return dataLen + headerLen;
    }

    int getControlLineLength() {
        calculate();
        return controlLineLength;
    }

    /**
     * @param destPosition the position index in destination byte array to start
     * @param dest is the byte array to write to
     * @return the length of the header
     */
    int copyNotEmptyHeaders(int destPosition, byte[] dest) {
        calculate();
        if (headerLen > 0) {
            return headers.serializeToArray(destPosition, dest);
        }
        return 0;
    }

    void setSubscription(NatsSubscription sub) {
        subscription = sub;
    }

    NatsSubscription getNatsSubscription() {
        return subscription;
    }

    // ----------------------------------------------------------------------------------------------------
    // Public Interface Methods
    // ----------------------------------------------------------------------------------------------------
    /**
     * {@inheritDoc}
     */
    @Override
    public String getSID() {
        return sid;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NatsConnection getConnection() {
        return subscription == null ? null : subscription.connection;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSubject() {
        return subject;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getReplyTo() {
        return replyTo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasHeaders() {
        return headers != null && !headers.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Headers getHeaders() {
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStatusMessage() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Status getStatus() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getData() {
        return data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Subscription getSubscription() {
        return subscription;
    }

    @Override
    public AckType lastAck() {
        return lastAck;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ack() {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ackSync(Duration d) throws InterruptedException, TimeoutException {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nak() {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nakWithDelay(Duration nakDelay) {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void nakWithDelay(long nakDelayMillis) {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void inProgress() {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void term() {
        // do nothing. faster. saves checking whether a message is jetstream or not
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JetStreamMetaData metaData() {
        throw new IllegalStateException(NOT_A_JET_STREAM_MESSAGE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isJetStream() {
        return false;  // overridden in NatsJetStreamMessage
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long consumeByteCount() {
        return subject == null ? 0 : subject.length()
            + headerLen
            + dataLen
            + (replyTo == null ? 0 : replyTo.length());
    }

    @Override
    public String toString() {
        if (subject == null) {
            return getClass().getSimpleName() + " | " + protocolBytesToString();
        }
        return getClass().getSimpleName() + " |" + subject + "|" + replyToString() + "|" + dataToString() + "|";
    }

    String toDetailString() {
        return "NatsMessage:" +
                "\n  subject='" + subject + '\'' +
                "\n  replyTo='" + replyToString() + '\'' +
                "\n  data=" + dataToString() +
                "\n  headers=" + headersToString() +
                "\n  sid='" + sid + '\'' +
                "\n  protocolBytes=" + protocolBytesToString() +
                "\n  sizeInBytes=" + sizeInBytes +
                "\n  headerLen=" + headerLen +
                "\n  dataLen=" + dataLen +
                "\n  subscription=" + subscription +
                "\n  next=" + nextToString();

    }

    private String headersToString() {
        return hasHeaders() ? new String(headers.getSerialized(), ISO_8859_1).replace("\r", "+").replace("\n", "+") : "";
    }

    private String dataToString() {
        if (data.length == 0) {
            return "<no data>";
        }
        String s = new String(data, UTF_8);
        int at = s.indexOf("io.nats.jetstream.api");
        if (at == -1) {
            return s.length() > 27 ? s.substring(0, 27) + "..." : s;
        }
        int at2 = s.indexOf('"', at);
        return s.substring(at, at2);
    }

    private String replyToString() {
        return replyTo == null ? "<no reply>" : replyTo;
    }

    private String protocolBytesToString() {
        return protocolBab == null ? null : protocolBab.toString();
    }

    private String nextToString() {
        return next == null ? "No" : "Yes";
    }

    // ----------------------------------------------------------------------------------------------------
    // Standard Builder
    // ----------------------------------------------------------------------------------------------------
    public static NatsMessageBuilder builder() {
        return new NatsMessageBuilder();
    }

}
