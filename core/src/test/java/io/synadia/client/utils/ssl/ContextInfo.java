package io.synadia.client.utils.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.util.Date;

/**
 * Snapshot of the SSLContext configuration captured after {@code init}.
 */
public class ContextInfo {
    public final Date timestamp;
    public final String protocol;
    public final String providerName;
    public final String providerVersion;
    public final String[] defaultProtocols;
    public final String[] defaultCipherSuites;
    public final String[] supportedProtocols;

    ContextInfo(SSLContext ctx) {
        this.timestamp = new Date();
        this.protocol = ctx.getProtocol();
        this.providerName = ctx.getProvider().getName();
        this.providerVersion = ctx.getProvider().getVersionStr();
        SSLParameters defaults = ctx.getDefaultSSLParameters();
        this.defaultProtocols = defaults.getProtocols();
        this.defaultCipherSuites = defaults.getCipherSuites();
        SSLParameters supported = ctx.getSupportedSSLParameters();
        this.supportedProtocols = supported.getProtocols();
    }
}
