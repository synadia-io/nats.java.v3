package io.synadia.client.impl;

/**
 * The keystore, truststore and algorithm settings collected from the options
 * and handed to a {@link SSLContextFactory} so it can build an SSLContext.
 */
public class SSLContextFactoryProperties {
    /** File system path to the keystore holding the client certificate. */
    public final String keystorePath;
    /** Password protecting the keystore. */
    public final char[] keystorePassword;
    /** File system path to the truststore holding the trusted certificates. */
    public final String truststorePath;
    /** Password protecting the truststore. */
    public final char[] truststorePassword;
    /** Algorithm name given to the key and trust manager factories, for example {@code SunX509}. */
    public final String tlsAlgorithm;

    private SSLContextFactoryProperties(Builder b) {
        this.keystorePath = b.keystore;
        this.keystorePassword = b.keystorePassword;
        this.truststorePath = b.truststore;
        this.truststorePassword = b.truststorePassword;
        this.tlsAlgorithm = b.tlsAlgorithm;
    }

    /**
     * File system path to the keystore holding the client certificate.
     * @return the path, may be null if no keystore was configured
     */
    public String getKeystorePath() {
        return keystorePath;
    }

    /**
     * Password protecting the keystore.
     * @return the password characters, may be null if no keystore was configured
     */
    public char[] getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * File system path to the truststore holding the trusted certificates.
     * @return the path, may be null if no truststore was configured
     */
    public String getTruststorePath() {
        return truststorePath;
    }

    /**
     * Password protecting the truststore.
     * @return the password characters, may be null if no truststore was configured
     */
    public char[] getTruststorePassword() {
        return truststorePassword;
    }

    /**
     * Algorithm name given to the key and trust manager factories, for example {@code SunX509}.
     * @return the algorithm name, may be null to accept the JVM default
     */
    public String getTlsAlgorithm() {
        return tlsAlgorithm;
    }

    /**
     * Collects the properties, then makes the immutable
     * {@link SSLContextFactoryProperties} instance via {@link #build()}.
     */
    public static class Builder {
        String keystore;
        char[] keystorePassword;
        String truststore;
        char[] truststorePassword;
        String tlsAlgorithm;

        /**
         * Construct a builder with all properties unset.
         */
        public Builder() {}

        /**
         * Set the file system path to the keystore holding the client certificate.
         * @param keystore the path
         * @return the builder
         */
        public Builder keystore(String keystore) {
            this.keystore = keystore;
            return this;
        }

        /**
         * Set the password protecting the keystore.
         * @param keystorePassword the password characters
         * @return the builder
         */
        public Builder keystorePassword(char[] keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        /**
         * Set the file system path to the truststore holding the trusted certificates.
         * @param truststore the path
         * @return the builder
         */
        public Builder truststore(String truststore) {
            this.truststore = truststore;
            return this;
        }

        /**
         * Set the password protecting the truststore.
         * @param truststorePassword the password characters
         * @return the builder
         */
        public Builder truststorePassword(char[] truststorePassword) {
            this.truststorePassword = truststorePassword;
            return this;
        }

        /**
         * Set the algorithm name given to the key and trust manager factories, for example {@code SunX509}.
         * @param tlsAlgorithm the algorithm name
         * @return the builder
         */
        public Builder tlsAlgorithm(String tlsAlgorithm) {
            this.tlsAlgorithm = tlsAlgorithm;
            return this;
        }

        /**
         * Build the {@code SSLContextFactoryProperties} object.
         * @return the properties
         */
        public SSLContextFactoryProperties build() {
            return new SSLContextFactoryProperties(this);
        }
    }
}
