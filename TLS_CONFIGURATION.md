# TLS configuration

How the client decides whether a connection uses TLS, which `SSLContext` it uses, how it checks the server's certificate, and how to supply a context that trusts every certificate. The `secure` and `tlsVerifyHostname` rows of the options table in the [README](README.md) refer here.

## 1. One SSLContext per connection

A connection has one `SSLContext`. It is used for every server the connection ever talks to: the bootstrap servers given in the options and the servers discovered from the cluster through `connect_urls`. Discovered servers take the scheme of the first bootstrap server that is not plain `nats://`, so a connection bootstrapped with `tls://` reaches discovered servers with TLS and the same context.

The context is accepted or created once, when `OptionsBuilder.build()` runs, in this order. The first rule that applies wins and the rest are not consulted.

1. `sslContext(SSLContext ctx)` was called: that context, as given.
2. An `SSLContextFactory` was set, by builder or through the `sslContextFactoryClass` property: the factory builds the context from the keystore, truststore and algorithm properties.
3. `keyStore` or `trustStore` was set, by builder or property: the client builds a context from those JKS files with the `tlsAlgorithm` property, `SunX509` by default.
4. `secure()` was called, the `secure` property is true, or any bootstrap server has the `tls://` or `wss://` scheme: `SSLContext.getDefault()`, described in section 2.
5. Otherwise no context, and the connection is plain TCP. A server that requires TLS then fails the connect with "SSL required by server."

There is no `opentls://` scheme, `openTls()` builder method or `openTls` property. An `opentls://` url fails with "Unsupported NATS URI scheme." A context that trusts every certificate is supplied with rule 1, section 4.

## 2. The default context: `tls://`, `wss://`, `secure()`

`SSLContext.getDefault()` is the JVM's default: it verifies the server's certificate chain against the JVM trust store, `cacerts` in the JDK's `lib/security`, or whatever the `javax.net.ssl.trustStore` system properties point at. It presents the client certificate from `javax.net.ssl.keyStore` when a server asks for one. It does not read the operating system's certificate store; a CA installed in Windows or macOS is not trusted by the JVM unless it is also in the JVM trust store or the JVM is configured to use the system store.

So `tls://` works out of the box against a server whose certificate chains to a public CA, and against a private CA once that CA is in the JVM trust store or supplied through the `trustStore` properties, an `SSLContextFactory`, or an `SSLContext` you build.

## 3. Hostname verification

Hostname verification is on by default, whatever context is in use. The certificate the server presents must be issued for the server name the connection was made with. The check is the one the JDK performs for HTTPS, against the certificate's subject alternative names.

The server name is:

- the configured hostname, in every `HostnameResolveMode`. The resolving modes connect to an ip address, but the hostname is still what is checked, and it is also sent to the server as the TLS server name (SNI), so an endpoint that selects its certificate by name serves the right one.
- the ip address, when the server is configured by ip address. The certificate must then carry that address as an ip subject alternative name.
- for a server discovered from `connect_urls` as a bare ip address, the hostname of the server that announced it, when that server was configured or discovered by hostname. Otherwise the ip address.

`tlsVerifyHostname(false)` on the builder, or the `tlsVerifyHostname=false` property, turns the check off. That removes the proof that the server is the one named in the url. Do it only where the certificate is known not to name the server, such as development against a self-signed certificate, and prefer reissuing the certificate for the name.

The check is performed by the trust manager. The JDK performs it for its own trust managers, which every context from section 1 rules 2 to 4 has, and for any plain `X509TrustManager`, which it wraps; that includes the trust-all context of section 4. A custom `X509ExtendedTrustManager` is responsible for its own identity check.

## 4. A context that trusts every certificate

The client never builds a trust-all context by itself. To use one, supply it:

```java
Options options = Options.builder()
    .server("tls://localhost:4222")
    .sslContext(SSLUtils.createTrustAllTlsContext())
    .build();
```

`SSLUtils.createTrustAllTlsContext()` returns a context whose trust manager accepts any server certificate chain: self-signed, expired, revoked, issued by anyone. It presents no client certificate, so the server must have client verification off. Any other `SSLContext` initialized with a trust manager whose `checkServerTrusted` does not throw behaves the same way. Nothing in the client detects a trust-all context, warns about it, or treats it differently from one that verifies.

Hostname verification still applies with this context (section 3). A self-signed certificate that does not name the server therefore fails until you also call `tlsVerifyHostname(false)`. Go's `InsecureSkipVerify` turns off both checks with one switch; here they are two separate settings.

### What that removes

TLS gives two things: encryption of the bytes, and proof of who is at the other end. The second comes from checking the server's certificate against something the client already trusts. A trust-all context keeps the encryption and discards most of the proof. The name check that remains is weak protection on its own: anyone can make a self-signed certificate for any name, and the trust-all context accepts it.

### What an impersonated server can do

With a trust-all context, anything on the network path that can answer the TCP connection can present its own certificate for the right name, and the client completes the handshake with it: a compromised router or host on the same network, a wrongly configured proxy or load balancer, a DNS answer that points at the wrong machine, a container or pod that took over an address. The client then sends its CONNECT with the credentials the options hold.

- A username and password, or a token, are sent inside the TLS session and are captured outright.
- NKey and credentials-file authentication sign a nonce the server sends. An impersonator that relays to the real server passes the real nonce through and forwards the signature, so it authenticates to the real server as the client. Signed authentication does not prevent this; only server verification does.
- From then on the impersonator reads every message the client publishes, delivers any message it likes to the client's subscriptions, and can relay everything to the real server so that nothing looks wrong to either side.

The client cannot notice. The handshake succeeded, the connection reports as secure, and no error, event or log line says that the chain was not checked.

### When it is acceptable, and what to use otherwise

A trust-all context is for development and tests, where the server is on the developer's own machine or the certificate is known to be throwaway. Outside that, do not use it. For a server with a private CA, put the CA in the JVM trust store, or in a truststore given to the client through the `trustStore` properties, or build an `SSLContext` from it; sections 2 and 5. The effort is one `keytool` import, and the connection then proves who it is talking to.

Because the context is per connection, it applies to every server the connection reaches, including discovered ones.

## 5. Client certificates

A server configured with `verify: true` requires a client certificate. Supply it through the `keyStore` and `keyStorePassword` properties together with `trustStore` and `trustStorePassword`, through an `SSLContextFactory`, through an `SSLContext` built with key managers, or through the `javax.net.ssl.keyStore` system properties when using the default context. The trust-all context presents no client certificate and cannot be used against such a server.

## 6. TLS handshake first

`tlsFirst()` performs the TLS handshake before reading the server's INFO, for servers configured with `handshake_first`. It requires a context: building options with `tlsFirst()` and no context from the rules above fails with "SSL context required for tls handshake first".

## 7. What the server and client expect of each other

Whether the client attempts the TLS upgrade is decided by whether it has a context; whether the server requires or offers TLS comes from its INFO. If there is a mismatch, an `IOException` is thrown while connecting.

| server config | client has a context | result |
|---|---|---|
| required | no | mismatch, "SSL required by server." |
| available | no | ok |
| neither | no | ok |
| required | yes | ok |
| available | yes | ok |
| neither | yes | mismatch, "SSL connection wanted by client." |
