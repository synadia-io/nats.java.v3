package io.synadia.client.js;

import io.synadia.client.impl.NatsMessage;
import io.synadia.client.support.DateTimeUtils;

import java.time.ZonedDateTime;

/**
 * Jetstream Metadata about a message, when applicable.
 */
public class JetStreamMetaData {

    private final String replyTo;
    private boolean needsParsed;

    // populated after parse is called

    private String prefix;
    private String domain;
    private String accountHash;
    private String stream;
    private String consumer;
    private long delivered;
    private long streamSeq;
    private long consumerSeq;
    private ZonedDateTime timestamp;
    private long pending;

    @Override
    public String toString() {
        parse();
        return "JetStreamMetaData{" +
            "prefix='" + prefix + '\'' +
            ", domain='" + domain + '\'' +
            ", stream='" + stream + '\'' +
            ", consumer='" + consumer + '\'' +
            ", delivered=" + delivered +
            ", streamSeq=" + streamSeq +
            ", consumerSeq=" + consumerSeq +
            ", timestamp=" + timestamp +
            ", pending=" + pending +
            '}';
    }


    /*
    v0 <prefix>.ACK.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>
    v1 <prefix>.ACK.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
    v2 <prefix>.ACK.<domain>.<account hash>.<stream name>.<consumer name>.<num delivered>.<stream sequence>.<consumer sequence>.<timestamp>.<num pending>
     */

    public JetStreamMetaData(NatsMessage natsMessage) {
        if (!natsMessage.isJetStream()) {
            throw new IllegalArgumentException(notAJetStreamMessage(natsMessage.getReplyTo()));
        }
        replyTo = natsMessage.getReplyTo();
        needsParsed = true;
    }

    private void parse() {
        if (needsParsed) {
            needsParsed = false;
            String[] parts = replyTo.split("\\.");
            if (parts.length < 8 || !"ACK".equals(parts[1])) {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }

            int streamIndex;
            boolean hasPending;
            boolean hasDomainAndHash;
            if (parts.length == 8) {
                streamIndex = 2;
                hasPending = false;
                hasDomainAndHash = false;
            }
            else if (parts.length == 9) {
                streamIndex = 2;
                hasPending = true;
                hasDomainAndHash = false;
            }
            else if (parts.length >= 11) {
                streamIndex = 4;
                hasPending = true;
                hasDomainAndHash = true;
            }
            else {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }

            try {
                prefix = parts[0];
                // "ack" = parts[1]
                domain = hasDomainAndHash ? parts[2] : null;
                accountHash = hasDomainAndHash ? parts[3] : null;
                stream = parts[streamIndex];
                consumer = parts[streamIndex + 1];
                delivered = Long.parseLong(parts[streamIndex + 2]);
                streamSeq = Long.parseLong(parts[streamIndex + 3]);
                consumerSeq = Long.parseLong(parts[streamIndex + 4]);
                timestamp = DateTimeUtils.parseDateTimeNanos(parts[streamIndex + 5]);
                pending = hasPending ? Long.parseLong(parts[streamIndex + 6]) : -1L;
            }
            catch (Exception e) {
                throw new IllegalArgumentException(notAJetStreamMessage(replyTo));
            }
        }
    }

    /**
     * Get the domain for the message. Might be null
     * @return the domain
     */
    public String getDomain() {
        parse();
        return domain;
    }

    /**
     * Gets the stream the message is from.
     * @return the stream.
     */
    public String getStream() {
        parse();
        return stream;
    }

    /**
     * Gets the consumer that generated this message.
     * @return the consumer.
     */
    public String getConsumer() {
        parse();
        return consumer;
    }

    /**
     * Gets the number of times this message has been delivered.
     * @return delivered count.
     */
    public long deliveredCount() {
        parse();
        return delivered;
    }

    /**
     * Gets the stream sequence number of the message.
     * @return sequence number
     */
    public long streamSequence() {
        parse();
        return streamSeq;
    }

    /**
     * Gets consumer sequence number of this message.
     * @return sequence number
     */
    public long consumerSequence() {
        parse();
        return consumerSeq;
    }

    /**
     * Gets the pending count of the consumer.
     * @return pending count
     */
    public long pendingCount() {
        parse();
        return pending;
    }

    /**
     * Gets the timestamp of the message.
     * @return the timestamp
     */
    public ZonedDateTime timestamp() {
        parse();
        return timestamp;
    }

    String getAccountHash() {
        parse();
        return accountHash;
    }

    private String notAJetStreamMessage(String reply) {
        return "Message is not a JetStream message.  ReplySubject: <" + reply + ">";
    }
}
