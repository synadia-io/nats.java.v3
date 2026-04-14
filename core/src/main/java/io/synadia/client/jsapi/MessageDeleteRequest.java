package io.synadia.client.jsapi;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NullMarked;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.support.ApiConstants.NO_ERASE;
import static io.synadia.client.support.ApiConstants.SEQ;

/**
 * Object used to make a request for message delete requests.
 */
@NullMarked
public class MessageDeleteRequest implements JsonSerializable {
    private final long sequence;
    private final boolean erase;

    public MessageDeleteRequest(long sequence, boolean erase) {
        this.sequence = sequence;
        this.erase = erase;
    }

    public long getSequence() {
        return sequence;
    }

    public boolean isErase() {
        return erase;
    }

    public boolean isNoErase() {
        return !erase;
    }

    @Override
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, SEQ, sequence);
        addField(sb, NO_ERASE, isNoErase());
        return endJson(sb).toString();
    }

    /**
     * Creates a builder for the purge options
     * @return a purge options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long seq = -1;
        private boolean erase = true;

        /**
         * Set upper-bound sequence for messages to be deleted
         * @param seq the upper-bound sequence
         * @return the builder
         */
        public Builder sequence(final long seq) {
            this.seq = seq < 1 ? -1 : seq;
            return this;
        }

        /**
         * set to the default, erase the message on delete
         * @return the builder
         */
        public Builder erase() {
            this.erase = true;
            return this;
        }

        /**
         * set to not erase the message on delete
         * @return the builder
         */
        public Builder noErase() {
            this.erase = false;
            return this;
        }

        /**
         * Build the MessageDeleteRequest
         * @return the built MessageDeleteRequest
         */
        public MessageDeleteRequest build() {
            return new MessageDeleteRequest(seq, erase);
        }
    }
}
