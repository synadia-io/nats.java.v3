package io.synadia.client.impl;

import io.synadia.client.SubjectValidationType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static io.synadia.client.SubjectValidationType.None;

/**
 * The builder is for building normal publish/request messages,
 * as an option for client use developers instead of the normal constructor
 */
public class NatsMessageBuilder {

    private static SubjectValidationType SVT = None;

    public static void setSubjectValidationType(SubjectValidationType subjectValidationType) {
        SVT = (subjectValidationType == null) ? None : subjectValidationType;
    }

    private String subject;
    private String replyTo;
    private Headers headers;
    private byte[] data;

    /**
     * Set the subject
     *
     * @param subject the subject
     * @return the builder
     */
    public NatsMessageBuilder subject(final String subject) {
        this.subject = subject;
        return this;
    }

    /**
     * Set the reply to
     *
     * @param replyTo the reply to
     * @return the builder
     */
    public NatsMessageBuilder replyTo(final String replyTo) {
        this.replyTo = replyTo;
        return this;
    }

    /**
     * Set the headers
     *
     * @param headers the headers
     * @return the builder
     */
    public NatsMessageBuilder headers(final Headers headers) {
        this.headers = headers;
        return this;
    }

    /**
     * Set the data from a string converting using the
     * charset StandardCharsets.UTF_8
     *
     * @param data the data string
     * @return the builder
     */
    public NatsMessageBuilder data(final String data) {
        if (data != null) {
            this.data = data.getBytes(StandardCharsets.UTF_8);
        }
        return this;
    }

    /**
     * Set the data from a string
     *
     * @param data    the data string
     * @param charset the charset, for example {@code StandardCharsets.UTF_8}
     * @return the builder
     */
    public NatsMessageBuilder data(final String data, final Charset charset) {
        this.data = data.getBytes(charset);
        return this;
    }

    /**
     * Set the data from a byte array. null data changed to empty byte array
     *
     * @param data the data
     * @return the builder
     */
    public NatsMessageBuilder data(final byte[] data) {
        this.data = data;
        return this;
    }

    /**
     * Build the {@code NatsMessage} object
     *
     * @return the {@code NatsMessage}
     */
    public NatsMessage build() {
        return new NatsMessage(subject, replyTo, headers, data);
    }
}
