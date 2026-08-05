package io.synadia.client.utils;

import javax.net.ssl.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

import static io.synadia.client.OptionsConstants.DEFAULT_SSL_PROTOCOL;
import static io.synadia.client.utils.RandomUtils.SRAND;

/**
 * Helpers for building the {@link SSLContext}, {@link KeyManager} and {@link TrustManager}
 * instances the client uses for TLS connections.
 */
public class SSLUtils {

    private SSLUtils() {}  /* ensures cannot be constructed */

    /** Key/trust manager factory algorithm used when none is supplied, {@value}. */
    public static final String DEFAULT_TLS_ALGORITHM = "SunX509";

    /** Keystore format expected by {@link #loadKeystore(String, char[])}, {@value}. */
    public static final String DEFAULT_KEYSTORE_TYPE = "JKS";

    private static TrustManagerDelegate TRUST_MANAGER_DELEGATE;

    /**
     * Install the delegate consulted by the trust-all trust manager. When no delegate is set,
     * the trust-all trust manager accepts every certificate without inspecting it. Setting a
     * delegate is global to the JVM and affects every context created afterward by
     * {@link #createTrustAllTlsContext()} / {@link #createOpenTLSContext()}.
     * @param trustManagerDelegate the delegate to install, or null to go back to accepting everything
     */
    public static void setDefaultTrustManagerDelegate(TrustManagerDelegate trustManagerDelegate) {
        SSLUtils.TRUST_MANAGER_DELEGATE = trustManagerDelegate;
    }

    /**
     * Hook for inspecting certificates that the trust-all trust manager would otherwise ignore.
     * Implementations signal rejection by throwing an unchecked exception from a check method.
     */
    public interface TrustManagerDelegate {
        /**
         * The certificate authorities this delegate accepts.
         * @return the accepted issuers, may be null to indicate no restriction
         */
        X509Certificate[] getAcceptedIssuers();

        /**
         * Inspect the certificate chain presented by a peer acting as the client.
         * @param certs the peer certificate chain, ordered leaf first
         * @param authType the key exchange algorithm used
         */
        void checkClientTrusted(X509Certificate[] certs, String authType);

        /**
         * Inspect the certificate chain presented by a peer acting as the server.
         * @param certs the peer certificate chain, ordered leaf first
         * @param authType the key exchange algorithm used
         */
        void checkServerTrusted(X509Certificate[] certs, String authType);
    }

    private static final TrustManager[] DEFAULT_TRUST_MANAGERS = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return TRUST_MANAGER_DELEGATE == null ? null : TRUST_MANAGER_DELEGATE.getAcceptedIssuers();
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                if (TRUST_MANAGER_DELEGATE != null) {
                    TRUST_MANAGER_DELEGATE.checkClientTrusted(certs, authType);
                }
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                if (TRUST_MANAGER_DELEGATE != null) {
                    TRUST_MANAGER_DELEGATE.checkServerTrusted(certs, authType);
                }
            }
        }
    };

    /**
     * Same as {@link #createTrustAllTlsContext()} but swallows any failure. Intended for the
     * "opentls" convenience URI scheme, where there is nowhere to report a problem.
     * @return the context, or null if one could not be created
     */
    public static SSLContext createOpenTLSContext() {
        try {
            return createTrustAllTlsContext();
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a context whose trust manager accepts any server certificate. Only appropriate for
     * testing or for a server using a self-signed certificate, since it defeats certificate
     * validation. A delegate installed with {@link #setDefaultTrustManagerDelegate} can narrow this.
     * @return the context
     * @throws GeneralSecurityException if the protocol is unavailable or the context cannot be initialized
     */
    public static SSLContext createTrustAllTlsContext() throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance(DEFAULT_SSL_PROTOCOL);
        context.init(null, DEFAULT_TRUST_MANAGERS, SRAND);
        return context;
    }

    /**
     * Read a keystore of type {@value #DEFAULT_KEYSTORE_TYPE} from the file system.
     * @param keystorePath the path to the keystore file
     * @param keystorePwd the password protecting the keystore, may be null if it is not protected
     * @return the loaded keystore
     * @throws GeneralSecurityException if the keystore cannot be loaded or its integrity check fails
     * @throws IOException if the file cannot be read
     */
    public static KeyStore loadKeystore(String keystorePath, char[] keystorePwd) throws GeneralSecurityException, IOException {
        final KeyStore store = KeyStore.getInstance(DEFAULT_KEYSTORE_TYPE);
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(Paths.get(keystorePath)))) {
            store.load(in, keystorePwd);
        }
        return store;
    }

    /**
     * Build the key managers that present the client certificate, using the
     * {@value #DEFAULT_TLS_ALGORITHM} algorithm.
     * @param keystorePath the path to the keystore holding the client key, null yields null
     * @param keystorePwd the password protecting the keystore and its keys
     * @return the key managers, or null if keystorePath was null
     * @throws GeneralSecurityException if the keystore or key manager factory cannot be initialized
     * @throws IOException if the keystore file cannot be read
     */
    public static KeyManager[] createKeyManagers(String keystorePath, char[] keystorePwd) throws GeneralSecurityException, IOException {
        return createKeyManagers(keystorePath, keystorePwd, DEFAULT_TLS_ALGORITHM);
    }

    /**
     * Build the key managers that present the client certificate.
     * @param keystorePath the path to the keystore holding the client key, null yields null
     * @param keystorePwd the password protecting the keystore and its keys
     * @param tlsAlgo the key manager factory algorithm
     * @return the key managers, or null if keystorePath was null
     * @throws GeneralSecurityException if the keystore or key manager factory cannot be initialized
     * @throws IOException if the keystore file cannot be read
     */
    public static KeyManager[] createKeyManagers(String keystorePath, char[] keystorePwd, String tlsAlgo) throws GeneralSecurityException, IOException {
        if (keystorePath == null) {
            return null;
        }
        KeyStore store = loadKeystore(keystorePath, keystorePwd);
        KeyManagerFactory factory = KeyManagerFactory.getInstance(tlsAlgo);
        factory.init(store, keystorePwd);
        return factory.getKeyManagers();
    }

    /**
     * Build the trust managers that validate the server certificate, using the
     * {@value #DEFAULT_TLS_ALGORITHM} algorithm.
     * @param truststorePath the path to the truststore holding the trusted issuers, null yields null
     * @param truststorePwd the password protecting the truststore
     * @return the trust managers, or null if truststorePath was null
     * @throws GeneralSecurityException if the truststore or trust manager factory cannot be initialized
     * @throws IOException if the truststore file cannot be read
     */
    public static TrustManager[] createTrustManagers(String truststorePath, char[] truststorePwd) throws GeneralSecurityException, IOException {
        return createTrustManagers(truststorePath, truststorePwd, DEFAULT_TLS_ALGORITHM);
    }

    /**
     * Build the trust managers that validate the server certificate.
     * @param truststorePath the path to the truststore holding the trusted issuers, null yields null
     * @param truststorePwd the password protecting the truststore
     * @param tlsAlgo the trust manager factory algorithm
     * @return the trust managers, or null if truststorePath was null
     * @throws GeneralSecurityException if the truststore or trust manager factory cannot be initialized
     * @throws IOException if the truststore file cannot be read
     */
    public static TrustManager[] createTrustManagers(String truststorePath, char[] truststorePwd, String tlsAlgo) throws GeneralSecurityException, IOException {
        if (truststorePath == null) {
            return null;
        }
        KeyStore store = loadKeystore(truststorePath, truststorePwd);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(tlsAlgo);
        factory.init(store);
        return factory.getTrustManagers();
    }

    /**
     * Create a context from a keystore and a truststore, using the
     * {@value #DEFAULT_TLS_ALGORITHM} algorithm. Either path may be null to leave that
     * half of the context at the JVM default.
     * @param keystorePath the path to the keystore holding the client key
     * @param keystorePwd the password protecting the keystore and its keys
     * @param truststorePath the path to the truststore holding the trusted issuers
     * @param truststorePwd the password protecting the truststore
     * @return the context
     * @throws GeneralSecurityException if a store, factory or the context cannot be initialized
     * @throws IOException if a store file cannot be read
     */
    public static SSLContext createSSLContext(String keystorePath, char[] keystorePwd, String truststorePath, char[] truststorePwd) throws GeneralSecurityException, IOException {
        return createSSLContext(keystorePath, keystorePwd, truststorePath, truststorePwd, DEFAULT_TLS_ALGORITHM);
    }

    /**
     * Create a context from a keystore and a truststore. Either path may be null to leave that
     * half of the context at the JVM default.
     * @param keystorePath the path to the keystore holding the client key
     * @param keystorePwd the password protecting the keystore and its keys
     * @param truststorePath the path to the truststore holding the trusted issuers
     * @param truststorePwd the password protecting the truststore
     * @param tlsAlgo the key and trust manager factory algorithm
     * @return the context
     * @throws GeneralSecurityException if a store, factory or the context cannot be initialized
     * @throws IOException if a store file cannot be read
     */
    public static SSLContext createSSLContext(String keystorePath, char[] keystorePwd, String truststorePath, char[] truststorePwd, String tlsAlgo) throws GeneralSecurityException, IOException {
        SSLContext ctx = SSLContext.getInstance(DEFAULT_SSL_PROTOCOL);
        ctx.init(createKeyManagers(keystorePath, keystorePwd, tlsAlgo), createTrustManagers(truststorePath, truststorePwd, tlsAlgo), SRAND);
        return ctx;
    }
}
