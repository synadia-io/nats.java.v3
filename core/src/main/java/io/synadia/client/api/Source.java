package io.synadia.client.api;

import io.synadia.client.support.JsonValue;
import io.synadia.client.support.JsonValueUtils;

import java.util.List;

/**
 * Source Information
 */
public class Source extends SourceBase {

    static List<Source> optionalListOf(JsonValue vSources) {
        return JsonValueUtils.optionalListOf(vSources, Source::new);
    }

    Source(JsonValue vSource) {
        super(vSource);
    }

    Source(Builder b) {
        super(b);
    }

    /**
     * Get an instance of the builder
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get an instance of the builder copying an existing source
     * @param source the source
     * @return the builder
     */
    public static Builder builder(Source source) {
        return new Builder(source);
    }

    /**
     * The builder for a Source
     */
    public static class Builder extends SourceBaseBuilder<Builder> {
        @Override
        Builder getThis() {
            return this;
        }

        /**
         * Construct an instance of the builder
         */
        public Builder() {}

        /**
         * Construct an instance of the builder copying an existing source
         * @param source the source
         */
        public Builder(Source source) {
            super(source);
        }

        /**
         * Build a Source
         * @return the Source
         */
        public Source build() {
            return new Source(this);
        }
    }
}
