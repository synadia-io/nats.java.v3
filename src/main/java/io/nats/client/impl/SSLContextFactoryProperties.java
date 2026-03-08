package io.nats.client.impl;

public class SSLContextFactoryProperties {
    public final String keystorePath;
    public final char[] keystorePassword;
    public final String truststorePath;
    public final char[] truststorePassword;
    public final String tlsAlgorithm;

    private SSLContextFactoryProperties(Builder b) {
        this.keystorePath = b.keystore;
        this.keystorePassword = b.keystorePassword;
        this.truststorePath = b.truststore;
        this.truststorePassword = b.truststorePassword;
        this.tlsAlgorithm = b.tlsAlgorithm;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    public char[] getKeystorePassword() {
        return keystorePassword;
    }

    public String getTruststorePath() {
        return truststorePath;
    }

    public char[] getTruststorePassword() {
        return truststorePassword;
    }

    public String getTlsAlgorithm() {
        return tlsAlgorithm;
    }

    public static class Builder {
        String keystore;
        char[] keystorePassword;
        String truststore;
        char[] truststorePassword;
        String tlsAlgorithm;

        public Builder keystore(String keystore) {
            this.keystore = keystore;
            return this;
        }

        public Builder keystorePassword(char[] keystorePassword) {
            this.keystorePassword = keystorePassword;
            return this;
        }

        public Builder truststore(String truststore) {
            this.truststore = truststore;
            return this;
        }

        public Builder truststorePassword(char[] truststorePassword) {
            this.truststorePassword = truststorePassword;
            return this;
        }

        public Builder tlsAlgorithm(String tlsAlgorithm) {
            this.tlsAlgorithm = tlsAlgorithm;
            return this;
        }

        public SSLContextFactoryProperties build() {
            return new SSLContextFactoryProperties(this);
        }
    }
}
