package io.synadia.client.impl;

import javax.net.ssl.SSLContext;

/**
 * Supplies the {@link SSLContext} for a TLS connection. Set one on the options to take over
 * context creation, for instance to pick up rotated certificates on each reconnect.
 */
public interface SSLContextFactory {
    /**
     * Build a context from the TLS settings on the options. Called on every connect and
     * reconnect, so it can return a fresh context each time.
     * @param properties the TLS related options the connection was configured with
     * @return the context to use for the connection
     */
    SSLContext createSSLContext(SSLContextFactoryProperties properties);
}
