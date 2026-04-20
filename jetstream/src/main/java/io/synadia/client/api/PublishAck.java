package io.synadia.client.api;

import io.synadia.client.Message;
import io.synadia.client.impl.JetStreamApiException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

import static io.nats.json.LazyJsonValueUtils.*;
import static io.synadia.client.testutils.ApiConstants.*;

/**
 * PublishAck objects represent a JetStream enabled server acknowledgment from a publish call.
 */
@NullMarked
public class PublishAck extends ApiResponse<PublishAck> {

    private final String stream;
    private final long seq;
    private final @Nullable String domain;
    private final boolean duplicate;
    private final @Nullable String val;
    private final @Nullable String batchId;
    private final int batchSize;

    /**
     *
     * This signature is public for testing purposes and is not intended to be used externally
     * @param msg the message containing the Pub Ack JSON <a href="https://github.com/nats-io/jsm.go/blob/main/schemas/jetstream/api/v1/pub_ack_response.json">pub_ack_response.json</a>
     * @throws IOException various IO exception such as timeout or interruption
     * @throws JetStreamApiException the request had an error related to the request
     */
    public PublishAck(Message msg) throws IOException, JetStreamApiException {
        super(msg);
        throwOnHasError();
        stream = stringRequired(STREAM);
        if (stream.isEmpty()) {
            throw new IOException("Invalid JetStream ack.");
        }
        seq = readLong(ljv, SEQ, -1);
        if (seq < 0) {
            throw new IOException("Invalid JetStream ack.");
        }
        domain = readString(ljv, DOMAIN);
        duplicate = readBoolean(ljv, DUPLICATE, false);
        val = readString(ljv, VAL);
        batchId = readString(ljv, BATCH);
        batchSize = readInteger(ljv, COUNT, -1);
    }

    /**
     * Get the stream sequence number for the corresponding published message.
     * @return the sequence number for the stored message.
     */
    public long getSequenceNumber() {
        return seq;
    }

    /**
     * Get the name of the stream a published message was stored in.
     * @return the name of the stream.
     */
    public String getStream() {
        return stream;
    }

    /**
     * Gets the domain of a stream
     * @return the domain name
     */
    @Nullable
    public String getDomain() {
        return domain;
    }

    /**
     * Gets if the server detected the published message was a duplicate.
     * @return true if the message is a duplicate, false otherwise.
     */
    public boolean isDuplicate() {
        return duplicate;
    }

    /**
     * Gets a counter value. Only available on counter enabled streams
     * @return the counter value as a string or null
     */
    @Nullable
    public String getVal() {
        return val;
    }

    /**
     * Gets the batch id. Only populated for batch publishes
     * @return the batch id
     */
    @Nullable
    public String getBatchId() {
        return batchId;
    }

    /**
     * Gets the batch size. Only populated for batch publishes.
     * @return the size of the batch
     */
    public int getBatchSize() {
        return batchSize;
    }
}
