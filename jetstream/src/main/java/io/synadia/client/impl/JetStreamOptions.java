package io.synadia.client.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static io.synadia.client.impl.JetStreamConstants.*;
import static io.synadia.client.utils.JsValidator.validatePrefixOrDomain;
import static io.synadia.client.utils.NatsConstants.DOT;
import static io.synadia.client.utils.Validator.ensureEndsWithDot;

/**
 * The JetStreamOptions class specifies the general options for JetStream.
 * Options are created using the  {@link JetStreamOptions.Builder Builder}.
 */
public class JetStreamOptions {

    /**
     * An instance of default options
     */
    public static final JetStreamOptions DEFAULT_JS_OPTIONS = new Builder().build();

    private final String jsPrefix;
    private final Duration requestTimeout;
    private final boolean defaultPrefix;
    private final boolean optOut290ConsumerCreate;
    private final Charset defaultCharset;

    private JetStreamOptions(Builder b) {
        if (b.jsPrefix == null) {
            defaultPrefix = true;
            this.jsPrefix = DEFAULT_API_PREFIX;
        }
        else {
            defaultPrefix = false;
            this.jsPrefix = b.jsPrefix;
        }
        this.requestTimeout = b.requestTimeout;
        this.optOut290ConsumerCreate = b.optOut290ConsumerCreate;
        this.defaultCharset = b.defaultCharset;
    }

    /**
     * Gets the request timeout the stream.
     * @return the name of the stream.
     */
    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Gets the prefix for this JetStream context. A prefix can be used in conjunction with
     * user permissions to restrict access to certain JetStream instances.
     * @return the prefix.
     */
    public String getPrefix() {
        return jsPrefix;
    }

    /**
     * Returns true if the prefix for the options is the default prefix.
     * @return the true for default prefix.
     */
    public boolean isDefaultPrefix() {
        return defaultPrefix;
    }

    /**
     * Gets whether the opt-out of the server v2.9.0 consumer create api is set
     * @return the flag
     */
    public boolean isOptOut290ConsumerCreate() {
        return optOut290ConsumerCreate;
    }

    /**
     * Get the charset to use for conversions of Strings to byte[]
     * @return the default charset
     */
    public Charset getDefaultCharset() {
        return defaultCharset;
    }

    /**
     * Creates a builder for the options.
     * @return the builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder to copy the options.
     * @param jso an existing JetStreamOptions
     * @return a JetStreamOptions builder
     */
    public static Builder builder(JetStreamOptions jso) {
        return new Builder(jso);
    }

    /**
     * Get an instance of JetStreamOptions with all defaults
     * @return the configuration
     */
    public static JetStreamOptions defaultOptions() {
        return DEFAULT_JS_OPTIONS;
    }

    /**
     * JetStreamOptions can be created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are calls.
     */
    public static class Builder {

        private String jsPrefix;
        private Duration requestTimeout;
        private boolean optOut290ConsumerCreate;
        private Charset defaultCharset = StandardCharsets.UTF_8;

        /**
         * Construct a builder
         */
        public Builder() {}

        /**
         * Construct a builder from an existing JetStreamOptions
         * @param jso the options
         */
        public Builder(JetStreamOptions jso) {
            if (jso != null) {
                if (jso.isDefaultPrefix()) {
                    this.jsPrefix = null;
                }
                else {
                    this.jsPrefix = jso.jsPrefix;
                }
                this.requestTimeout = jso.requestTimeout;
                this.optOut290ConsumerCreate = jso.optOut290ConsumerCreate;
                this.defaultCharset = jso.defaultCharset;
            }
        }

        /**
         * Sets the request timeout for JetStream API calls.
         * @param requestTimeout the duration to wait for responses.
         * @return the builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Sets the prefix for JetStream subjects. A prefix can be used in conjunction with
         * user permissions to restrict access to certain JetStream instances. This must
         * match the prefix used in the server.
         * @param prefix the JetStream prefix
         * @return the builder.
         */
        public Builder prefix(String prefix) {
            jsPrefix = ensureEndsWithDot(validatePrefixOrDomain(prefix, "Prefix", false));
            return this;
        }

        /**
         * Sets the domain for JetStream subjects, creating a standard prefix from that domain
         * in the form $JS.(domain).API.
         * A domain can be used in conjunction with user permissions to restrict access to certain JetStream instances.
         * This must match the domain used in the server.
         * @param domain the JetStream domain
         * @return the builder.
         */
        public Builder domain(String domain) {
            String prefix = convertDomainToPrefix(domain);
            jsPrefix = prefix == null ? null : prefix + DOT;
            return this;
        }

        /**
         * Set whether to opt-out of the server v2.9.0 consumer create api. Default is false (opt-in)
         * @param optOut the opt-out flag
         * @return the builder
         */
        public Builder optOut290ConsumerCreate(boolean optOut) {
            this.optOut290ConsumerCreate = optOut;
            return this;
        }

        /**
         * Set the charset to be used whenever a string must be converted to a byte array
         * @param defaultCharset the conversion charset
         * @return the builder
         */
        public Builder defaultCharset(Charset defaultCharset) {
            this.defaultCharset = defaultCharset == null ? StandardCharsets.UTF_8 : defaultCharset;
            return this;
        }

        /**
         * Builds the JetStream options.
         * @return JetStream options
         */
        public JetStreamOptions build() {
            return new JetStreamOptions(this);
        }
    }

    /**
     * Helper function to convert a domain to a prefix
     * @param domain the domain
     * @return the prefix
     */
    public static String convertDomainToPrefix(String domain) {
        String valid = validatePrefixOrDomain(domain, "Domain", false);
        return valid == null ? null
            : PREFIX_DOLLAR_JS_DOT + ensureEndsWithDot(valid) + PREFIX_API;
    }
}
