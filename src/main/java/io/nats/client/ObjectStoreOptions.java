package io.nats.client;

/**
 * The ObjectStoreOptions class specifies the general options for ObjectStore.
 * Options are created using the {@link ObjectStoreOptions.Builder Builder}.
 */
public class ObjectStoreOptions extends FeatureOptions {

    private ObjectStoreOptions(Builder b) {
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
     * @param oso an existing ObjectStoreOptions
     * @return an ObjectStoreOptions builder
     */
    public static Builder builder(ObjectStoreOptions oso) {
        return new ObjectStoreOptions.Builder(oso);
    }

    /**
     * Creates a builder to copy the options.
     * @param jso an existing JetStreamOptions
     * @return an ObjectStoreOptions builder
     */
    public static Builder builder(JetStreamOptions jso) {
        return new Builder().jetStreamOptions(jso);
    }

    /**
     * ObjectStoreOptions can be created using a Builder. The builder supports chaining and will
     * create a default set of options if no methods are calls.
     */
    public static class Builder extends FeatureOptions.Builder<Builder, ObjectStoreOptions> {

        @Override
        protected Builder getThis() {
            return this;
        }

        /**
         * Construct the builder
         */
        public Builder() {
            super();
        }

        /**
         * Construct the builder from existing options
         * @param oso the options
         */
        public Builder(ObjectStoreOptions oso) {
            super(oso);
        }

        /**
         * Builds the ObjectStore options.
         * @return ObjectStore options
         */
        public ObjectStoreOptions build() {
            return new ObjectStoreOptions(this);
        }
    }
}
