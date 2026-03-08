package io.nats.client.impl;

import io.nats.client.support.ssl.SslTestingHelper;

import javax.net.ssl.SSLContext;

public class SSLContextFactoryForTesting implements SSLContextFactory {
    public SSLContextFactoryProperties properties;

    @Override
    public SSLContext createSSLContext(SSLContextFactoryProperties properties) {
        this.properties = properties;
        try {
            return SslTestingHelper.createTestSSLContext();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
