package io.synadia.client.impl;

/**
 * The FeatureOptions is a base class of general options for features.
 */
public abstract class FeatureOptions {

    private final JetStreamOptions jso;

    protected FeatureOptions(Builder<?, ?> b) {
        jso = b.jsoBuilder.build();
    }

    /**
     * Gets the JetStream options
     * @return the JetStream options
     */
    public JetStreamOptions getJetStreamOptions() {
        return jso;
    }

    /**
     * Feature options are created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are called.
     * @param <B> The builder type
     * @param <FO> the resulting option type
     */
    protected static abstract class Builder<B, FO> {

        private JetStreamOptions.Builder jsoBuilder;

        protected abstract B getThis();
        
        protected Builder() {
            jsoBuilder = JetStreamOptions.builder();
        }
        
        protected Builder(FeatureOptions oso) {
            if (oso != null) {
                jsoBuilder = JetStreamOptions.builder(oso.jso);
            }
            else {
                jsoBuilder = JetStreamOptions.builder();
            }
        }

        /**
         * Sets the JetStreamOptions.
         * @param jso the JetStreamOptions
         * @return the builder.
         */
        public B jetStreamOptions(JetStreamOptions jso) {
            jsoBuilder = JetStreamOptions.builder(jso);
            return getThis();
        }

        /**
         * Sets the request timeout in milliseconds for JetStream API calls.
         * @param millis the milliseconds to wait for responses.
         * @return the builder
         */
        public B jsRequestTimeout(long millis) {
            jsoBuilder.requestTimeout(millis);
            return getThis();
        }

        /**
         * Sets the prefix for JetStream subjects. A prefix can be used in conjunction with
         * user permissions to restrict access to certain JetStream instances. This must
         * match the prefix used in the server.
         * @param prefix the JetStream prefix
         * @return the builder.
         */
        public B jsPrefix(String prefix) {
            jsoBuilder.prefix(prefix);
            return getThis();
        }

        /**
         * Sets the domain for JetStream subjects, creating a standard prefix from that domain
         * in the form $JS.(domain).API.
         * A domain can be used in conjunction with user permissions to restrict access to certain JetStream instances.
         * This must match the domain used in the server.
         * @param domain the JetStream domain
         * @return the builder.
         */
        public B jsDomain(String domain) {
            jsoBuilder.domain(domain);
            return getThis();
        }

        /**
         * Builds the Feature options.
         * @return Feature options
         */
        public abstract FO build();
    }
}
