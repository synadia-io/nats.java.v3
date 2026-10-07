package io.synadia.client;

import io.synadia.client.impl.NatsConnection;
import io.synadia.client.impl.NatsImpl;
import io.synadia.client.utils.ApiUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static io.synadia.client.OptionsConstants.DEFAULT_URL;

/**
 * The Nats class is the entry point into the NATS client for Java. This class
 * is used to create a connection to the NATS server. Connecting is a
 * synchronous process, with a new asynchronous version available.
 * 
 * <p>Simple connections can be created with a URL, while more control is provided
 * when an {@link Options Options} object is used. There are a number of options that
 * effect every connection, as described in the {@link Options Options} documentation.
 * 
 * <p>At its simplest, you can connect to a nats-server on the local host using the default port with:
 * <pre>NatsConnection nc = Nats.connect()</pre>
 * <p>and start sending or receiving messages immediately after that.
 * 
 * <p>While the simple case relies on a single URL, the options allows you to configure a list of servers
 * that is used at connect time and during reconnect scenarios.
 * 
 * <p>NATS supports TLS connections. This library relies on the standard SSLContext class to configure
 * SSL certificates and trust managers, as a result there are two steps to setting up a TLS connection,
 * configuring the SSL context and telling the library which one to use. Several options are provided
 * for each. To tell the library to connect with TLS:
 * 
 * <ul>
 * <li>Pass a tls:// URL to the connect method, or as part of the options. The library will use the 
 * default SSLContext for both the client certificates and trust managers.
 * <li>Call the {@link OptionsBuilder#secure() secure} method on the options builder, again the default
 * SSL Context is used.
 * <li>Call {@link OptionsBuilder#sslContext(javax.net.ssl.SSLContext) sslContext} when building your options.
 * Your context will be used.
 * <li>Pass an opentls:// url to the connect method, or in the options. The library will create a special
 * SSLContext that has no client certificates and trusts any server. <strong>This is less secure, but useful for
 * testing and behind a firewall.</strong>
 * <li>Call the {@link OptionsBuilder#openTls() opentls} method on the builder when creating your options, again
 * the all trusting, non-verifiable client is created.
 * </ul>
 * 
 * <p>To set up the default context for tls:// or {@link OptionsBuilder#secure() secure} you can:
 * <ul>
 * <li>Configure the default using System properties, i.e. <em>javax.net.ssl.keyStore</em>.
 * <li>Set the context manually with the SSLContext setDefault method.
 * </ul>
 * 
 * <p>If the server is configured to verify clients, the opentls mode will not work, and the other modes require a client certificate
 * to work.
 * 
 * <p>Authentication, if configured on the server, is managed via the Options as well. However, the url passed to {@link #connect(String) connect()}
 * can provide a user/password pair or a token using the forms: {@code nats://user:password@server:port} and {@code nats://token@server:port}.
 * 
 * <p>Regardless of the method used a {@link NatsConnection NatsConnection} object is created, and provides the methods for
 * sending, receiving and dispatching messages.
 */
public abstract class Nats {

    /**
     * Current version of the library
     */
    public static final String CLIENT_VERSION = ApiUtils.loadVersion(Nats.class, "core");

    /**
     * Current language of the library - {@value}
     */
    public static final String CLIENT_LANGUAGE = "java";


    /**
     * Connect to the default URL, {@link OptionsConstants#DEFAULT_URL DEFAULT_URL}, with all the
     * default options.
     * 
     * <p>This is a synchronous call, and the connection should be ready for use on return
     * there are network timing issues that could result in a successful connect call but
     * the connection is invalid soon after return, where soon is in the network/thread world.
     * 
     * <p>If the connection fails, an IOException is thrown
     * 
     * <p>See {@link Nats#connect(Options) connect(Options)} for more information on exceptions.
     *
     * @return the connection
     * @throws IOException if a networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connect() throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(DEFAULT_URL).build();
        return createConnection(options, false);
    }

    /**
     * Connect to the default URL, {@link OptionsConstants#DEFAULT_URL DEFAULT_URL}, with all the
     * default options, allowing re-connect attempts if the initial connection fails
     * @return the connection
     * @throws IOException if an unrecoverable networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connectReconnectOnConnect() throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(DEFAULT_URL).build();
        return createConnection(options, true);
    }

    /**
     * Connect to specific url, with all the default options.
     * The Java client generally expects URLs of the form {@code nats://hostname:port}
     *
     * <p>but also allows urls with a user password {@code nats://user:pass@hostname:port}.</p>
     *
     * <p>or token in them {@code nats://token@hostname:port}.</p>
     *
     * <p>Moreover, you can initiate a TLS connection, by using the `tls`
     * schema, which will use the default SSLContext, or fail if one is not set. For
     * testing and development, the `opentls` schema is support when the server is
     * in non-verify mode. In this case, the client will accept any server
     * certificate and will not provide one of its own.</p>
     *
     * <p>This is a synchronous call, and the connection should be ready for use on return
     * there are network timing issues that could result in a successful connect call but
     * the connection is invalid soon after return, where soon is in the network/thread world.</p>
     * 
     * <p>If the connection fails, an IOException is thrown</p>
     * 
     * <p>See {@link Nats#connect(Options) connect(Options)} for more information on exceptions.</p>
     *
     * @param url comma separated list of the URLs of the server, i.e. nats://localhost:4222,nats://localhost:4223
     * @throws IOException if a networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     * @return the connection
     */
    public static NatsConnection connect(String url) throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(url).build();
        return createConnection(options, false);
    }

    /**
     * Connect to specific url, with all the default options,
     * allowing re-connect attempts if the initial connection fails
     * @param url comma separated list of the URLs of the server, i.e. nats://localhost:4222,nats://localhost:4223
     * @return the connection
     * @throws IOException if an unrecoverable networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connectReconnectOnConnect(String url) throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(url).build();
        return createConnection(options, true);
    }

    /**
     * Connect to the specified URL with the specified auth handler.
     *
     * <p>This is a synchronous call, and the connection should be ready for use on return
     * there are network timing issues that could result in a successful connect call but
     * the connection is invalid soon after return, where soon is in the network/thread world.
     *
     * <p>If the connection fails, an IOException is thrown
     *
     * <p>See {@link Nats#connect(Options) connect(Options)} for more information on exceptions.
     *
     * @param url comma separated list of the URLs of the server, i.e. nats://localhost:4222,nats://localhost:4223
     * @param handler the authentication handler implementation
     * @return the connection
     * @throws IOException if a networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connect(String url, AuthHandler handler) throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(url).authHandler(handler).build();
        return createConnection(options, false);
    }

    /**
     * Connect to the specified URL with the specified auth handler,
     * allowing re-connect attempts if the initial connection fails
     * @param url comma separated list of the URLs of the server, i.e. nats://localhost:4222,nats://localhost:4223
     * @param handler the authentication handler implementation
     * @return the connection
     * @throws IOException if an unrecoverable networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connectReconnectOnConnect(String url, AuthHandler handler) throws IOException, InterruptedException {
        Options options = new OptionsBuilder().server(url).authHandler(handler).build();
        return createConnection(options, true);
    }

    /**
     * Options can be used to set the server URL, or multiple URLS, callback
     * handlers for various errors, and connection events.
     * 
     * <p>This is a synchronous call, and the connection should be ready for use on return
     * there are network timing issues that could result in a successful connect call but
     * the connection is invalid soon after return, where soon is in the network/thread world.
     * 
     * <p>If the connection fails, an IOException is thrown
     * 
     * <p>As of 2.6 the connect call with throw an io.nats.AuthenticationException if an authentication
     * error occurred during connect, and the connect failed. Because multiple servers are tried, this exception
     * may not indicate a problem on the "last server" tried, only that all the servers were tried and at least
     * one failed because of authentication. In situations with heterogeneous authentication for multiple servers
     * you may need to use an ErrorListener to determine which one had the problem. Authentication failures are not
     * immediate connect failures because of the server list, and the existing 2.x API contract.
     * 
     * <p>As of 2.6.1 authentication errors play an even stronger role. If a server returns an authentication error
     * twice without a successful connection, the connection is closed. This will require a reconnect scenario, since
     * the initial connection only tries each server one time. However, if you have two servers S1 and S2, and S1 returns
     * and authentication error on connect, but S2 succeeds. Later, if S2 fails and S1 returns the same error the connection
     * will be closed. However, if S1 succeeds on reconnect the "last error" will be cleared so it would be allowed to fail
     * again in the future.
     * 
     * @param options the options object to use to create the connection
     * @return the connection
     * @throws IOException if a networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connect(Options options) throws IOException, InterruptedException {
        return createConnection(options, false);
    }

    /**
     * Connect, allowing re-connect attempts if the initial connection fails
     * @param options the options object to use to create the connection
     * @return the connection
     * @throws IOException if an unrecoverable networking issue occurs
     * @throws InterruptedException if the current thread is interrupted
     */
    public static NatsConnection connectReconnectOnConnect(Options options) throws IOException, InterruptedException {
        return createConnection(options, true);
    }

    /**
     * Try to connect in another thread. Returns immediately with a future that completes with the
     * connection once it is connected, or completes exceptionally if the connection attempt fails.
     *
     * <p>Normally connect will loop through the available servers one time. If
     * reconnectOnConnect is true, the connection attempt will repeat based on the
     * settings in options, including indefinitely.
     *
     * <p>The future is how you should get the connection. It completes only once the connection is actually
     * connected, so what it hands back is ready to use; a failed attempt completes it exceptionally instead.
     *
     * <p>A {@link ConnectionListener ConnectionListener} set in the options also receives the connection, but not
     * until the first connection event - and that event is as likely to be a failure or a retry as a successful
     * connect. Taking the handle from there still works but is <b>discouraged</b>: you get it no earlier than the
     * first event, with no indication the connection ever succeeded, so every call on it has to cope with a
     * connection that is not connected and may never be. Use the listener to react to connection events; use the
     * future to get the connection.
     *
     * <p>This starts a <b>new thread per call</b>, named "NATS - async connection", which runs the whole connect
     * attempt and then exits. It inherits daemon status from the calling thread, so in the usual case it is not
     * a daemon and a connect that never succeeds will keep the JVM alive - see the retry note below. Nothing is
     * pooled or reused; two calls get two threads. To run the attempt somewhere else - a pool you own, or
     * virtual threads - use {@link #connectAsynchronously(Options, boolean, Executor) the overload taking an
     * Executor}.
     *
     * <p>With reconnectOnConnect true the retry loop runs on that thread, so the future stays pending for as
     * long as retries continue - indefinitely if maxReconnects is -1, which also means the thread lives that
     * long. Wait on the future with a timeout rather than reaching for the listener's copy.
     *
     * <p>If there is an exception before a connection is created, any error
     * listeners set in the options are notified with a null connection, in addition to the future
     * completing exceptionally.
     *
     * @param options            the connection options
     * @param reconnectOnConnect if true, the connection will treat the initial
     *                           connection as any other and attempt reconnects on
     *                           failure
     * @return a future that completes with the connection, or completes exceptionally on failure
     */
    public static CompletableFuture<NatsConnection> connectAsynchronously(Options options, boolean reconnectOnConnect) {
        // a thread per call - the connect task blocks for the whole attempt, so it must never share a thread
        return connectAsynchronously(options, reconnectOnConnect, r -> new Thread(r, "NATS - async connection").start());
    }

    /**
     * Try to connect using the supplied executor, otherwise behaving exactly as
     * {@link #connectAsynchronously(Options, boolean) connectAsynchronously(options, reconnectOnConnect)},
     * which is the same thing on a thread per call.
     *
     * <p>The connect task <b>blocks for the whole connect attempt</b>, and with reconnectOnConnect true that
     * includes the entire retry loop - indefinitely if maxReconnects is -1. Choose the executor accordingly:
     * <ul>
     * <li>a single-thread executor serializes concurrent async connects behind each other.</li>
     * <li>{@link java.util.concurrent.ForkJoinPool#commonPool() commonPool} - what a bare
     * {@code CompletableFuture.supplyAsync} would use - is a poor fit, being sized for short CPU-bound work.</li>
     * <li>{@code Executors.newVirtualThreadPerTaskExecutor()} is a good fit, the task being purely blocking.</li>
     * </ul>
     * <p><b>Do not pass any of the executors you supplied to {@link Options Options}</b> - not the connect,
     * callback, reader, writer, scheduled or general executor. Those belong to the connection and are sized and
     * used for its own work, and this task blocks on top of them:
     * <ul>
     * <li>the connect executor deadlocks outright - connecting submits to it and blocks awaiting the result, so
     * a connect task already occupying it can never be completed by it.</li>
     * <li>the others get an indefinitely blocked thread taken out of the pool the connection needs to read,
     * write, and deliver callbacks.</li>
     * </ul>
     * Their lifecycle is wrong for this too: they are reference counted per connection and shut down when the
     * last one closes, while this call runs before any connection exists. Supply a separate executor.
     *
     * @param options            the connection options
     * @param reconnectOnConnect if true, the connection will treat the initial
     *                           connection as any other and attempt reconnects on
     *                           failure
     * @param executor           the executor to run the connect attempt on
     * @return a future that completes with the connection, or completes exceptionally on failure
     */
    public static CompletableFuture<NatsConnection> connectAsynchronously(
            Options options, boolean reconnectOnConnect, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return NatsImpl.createConnection(options, reconnectOnConnect);
            }
            catch (Exception ex) {
                // straight off the Options, not a connection - there is no NatsConnection to add listeners to yet
                for (ErrorListener el : options.getErrorListeners()) {
                    el.exceptionOccurred(null, ex);
                }
                // CompletableFuture unwraps this, so future.get() still reports the original cause
                throw new CompletionException(ex);
            }
        }, executor);
    }

    /**
     * Create an auth handler from a creds file. The handler will read the file each time it needs to respond to a request
     * and clear the memory after. This has a small price, but will only be encountered during connect or reconnect.
     * The creds file has a JWT - generally commented with a separator - followed by an nkey - also with a separator.
     * 
     * @param credsFile a file containing a user JWT and an nkey
     * @return an AuthHandler that will use the creds file to load/clear the nkey and jwt as needed
     */
    public static AuthHandler credentials(String credsFile) {
        return NatsImpl.credentials(credsFile);
    }

    /**
     * Create an AuthHandler from a jwt file and an nkey file. The handler will read the files each time it needs to respond to a request
     * and clear the memory after. This has a small price, but will only be encountered during connect or reconnect.
     *
     * <p>The {@code jwtFile} parameter can be set to {@code null} for challenge only authentication.
     * 
     * @param jwtFile a file containing a user JWT, may or may not contain separators
     * @param nkeyFile a file containing a user nkey that matches the JWT, may or may not contain separators
     * @return an AuthHandler that will use the creds file to load/clear the nkey and jwt as needed
     */
    public static AuthHandler credentials(String jwtFile, String nkeyFile) {
        return NatsImpl.credentials(jwtFile, nkeyFile);
    }

    /**
     * Create an auth handler from the data found in a credsFile. This credentials object is static, and will not change
     * over the course of its lifetime. Create a custom AuthHandler or use the file-based handler for dynamic credentials.
     *
     * @param credsBytes the contents of a user JWT file (optional if nkey authentication is being used)
     * @return an AuthHandler that will return the JWT, public key or sign a nonce appropriately
     */
    public static AuthHandler staticCredentials(byte[] credsBytes) {
        return NatsImpl.staticCredentials(credsBytes);
    }

    /**
     * Create an auth handler from an nkey and an optional JWT. This credentials object is static, and will not change
     * over the course of its lifetime. Create a custom AuthHandler or use the file-based handler for dynamic credentials.
     *
     * @param jwt the contents of a user JWT file (optional if nkey authentication is being used)
     * @param nkey an nkey seed
     * @return an AuthHandler that will return the JWT, public key or sign a nonce appropriately
     */
    public static AuthHandler staticCredentials(char[] jwt, char[] nkey) {
        return NatsImpl.staticCredentials(jwt, nkey);
    }

    private static NatsConnection createConnection(Options options, boolean reconnectOnConnect)
            throws IOException, InterruptedException {
        return NatsImpl.createConnection(options, reconnectOnConnect);
    }

    private Nats() {} /* ensures cannot be constructed */
}
