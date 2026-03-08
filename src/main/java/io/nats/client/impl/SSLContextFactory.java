package io.nats.client.impl;

import javax.net.ssl.SSLContext;

public interface SSLContextFactory {
    SSLContext createSSLContext(SSLContextFactoryProperties properties);
}
