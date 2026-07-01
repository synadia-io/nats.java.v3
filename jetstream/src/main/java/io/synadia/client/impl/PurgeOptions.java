package io.synadia.client.impl;

import io.nats.json.JsonSerializable;
import org.jspecify.annotations.NonNull;

import static io.nats.json.JsonWriteUtils.*;
import static io.synadia.client.utils.ApiConstants.*;
import static io.synadia.client.utils.Validator.validateSubjectStrict;

/**
 * The PurgeOptions class specifies the options for purging a stream
 * The builder validates its values and throws {@link IllegalArgumentException} for invalid input.
 */
public class PurgeOptions implements JsonSerializable {

    protected final String subject;
    protected final long seq;
    protected final long keep;

    private PurgeOptions(String subject, long seq, long keep) {
        this.subject = subject;
        this.seq = seq;
        this.keep = keep;
    }

    @Override
    @NonNull
    public String toJson() {
        StringBuilder sb = beginJson();
        addField(sb, FILTER, subject);
        addField(sb, SEQ, seq);
        addField(sb, KEEP, keep);
        return endJson(sb).toString();
    }

    /**
     * Get the subject for the Purge Options
     * @return the subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Get the upper bound sequence for the Purge Options
     * @return the upper bound sequence
     */
    public long getSequence() {
        return seq;
    }

    /**
     * Get the max number of messages to keep for the Purge Options
     * @return the max number of messages to keep
     */
    public long getKeep() {
        return keep;
    }

    /**
     * Creates a builder for the purge options
     * @return a purge options builder
     */
    public static PurgeOptions.Builder builder() {
        return new Builder();
    }

    /**
     * Creates a completed Purge Options for just a subject
     * @param subject the subject to purge
     * @return a purge options for a subject
     */
    public static PurgeOptions subject(String subject) {
        return new Builder().subject(subject).build();
    }

    /**
     * Builder class for PurgeOptions
     */
    public static class Builder {
        private String subject;
        private long seq = -1;
        private long keep = -1;

        /**
         * Construct a builder instance
         */
        public Builder() {}

        /**
         * Set the subject to filter the purge. Wildcards allowed.
         * @param subject the subject
         * @return the builder
         * @throws IllegalArgumentException if the subject is not a valid subject
         */
        public Builder subject(final String subject) {
            this.subject = validateSubjectStrict(subject, false);
            return this;
        }

        /**
         * Set upper-bound sequence for messages to be deleted
         * @param seq the upper-bound sequence
         * @return the builder
         */
        public Builder sequence(final long seq) {
            this.seq = seq;
            return this;
        }

        /**
         * set the max number of messages to keep
         * @param keep the max number of messages to keep
         * @return the builder
         */
        public Builder keep(final long keep) {
            this.keep = keep;
            return this;
        }

        /**
         * Build the PurgeOptions
         * @return the built PurgeOptions
         * @throws IllegalArgumentException if both sequence and keep are set (they are mutually exclusive)
         */
        public PurgeOptions build() {
            if (seq > 0 && keep > 0) {
                throw new IllegalArgumentException("seq and keep are mutually exclusive.");
            }

            return new PurgeOptions(subject, seq, keep);
        }
    }
}
