package io.synadia.client.kv;

import io.synadia.client.impl.FeatureOptions;
import io.synadia.client.impl.JetStreamOptions;

/**
 * The KeyValueOptions class specifies the general options for KeyValueO.
 * Options are created using the {@link KeyValueOptions.Builder Builder}.
 */
public class KeyValueOptions extends FeatureOptions {

    private KeyValueOptions(Builder b) {
        super(b);
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
     * @param kvo an existing KeyValueOptions
     * @return a KeyValueOptions builder
     */
    public static Builder builder(KeyValueOptions kvo) {
        return new Builder(kvo);
    }

    /**
     * Creates a builder to copy the options.
     * @param jso an existing JetStreamOptions
     * @return a KeyValueOptions builder
     */
    public static Builder builder(JetStreamOptions jso) {
        return new Builder().jetStreamOptions(jso);
    }

    /**
     * KeyValueOptions can be created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are calls.
     */
    public static class Builder extends FeatureOptions.Builder<Builder, KeyValueOptions> {

        @Override
        protected Builder getThis() {
            return this;
        }

        /**
         * Construct an instance of the builder
         */
        public Builder() {
            super();
        }

        /**
         * Construct an instance of the builder based on an existing KeyValueOptions
         * @param kvo the KeyValueOptions
         */
        public Builder(KeyValueOptions kvo) {
            super(kvo);
        }

        /**
         * Builds the KeyValue Options.
         * @return KeyValue Options
         */
        public KeyValueOptions build() {
            return new KeyValueOptions(this);
        }
    }
}
