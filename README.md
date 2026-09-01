![Synadia](core/src/main/javadoc/images/logo-black.png)

# NATS - Java Client VERSION 3

# WORK IN PROGRESS

### Server Compatibility

**This client requires nats-server 2.10 or later. 2.14 or later is preferred.**

v2 of the client carried runtime checks and opt-outs for server features introduced in 2.9 and earlier. v3 drops them: the 2.10 floor is assumed, not tested for, so pointing v3 at an older server is unsupported and will fail in ways the client does not attempt to diagnose. Features added after 2.10 are still detected at runtime from the server's `INFO` — those degrade gracefully rather than requiring the newer server.

### Properties

The property loader accepts each key with or without the `io.nats.client.` prefix; both forms resolve to the same property.

| Name | Default Value | Description |
|------|---------------|-------------|
| `connectionListenerClass` | `(none)` | Configure a connectionListener (class name). |
| `dataPortType` | `io.synadia.client.impl.SocketDataPort` | Configure a dataPortType. |
| `errorListenerClass` | `(none)` | Configure an errorListener (class name). |
| `statisticsCollectorClass` | `(none)` | Configure the statisticsCollector (class name). |
| `maxPingsOut` | `2` | Configure maxPingsOut. |
| `pingInterval` | `120000` (ms) | Configure pingInterval. |
| `requestCleanupInterval` | `5000` (ms) | Configure requestCleanupInterval. |
| `writeQueuePushTimeout` | `2000` (ms) | Configure writeQueuePushTimeout. |
| `connectionTimeout` | `2000` (ms) | Configure connectionTimeout. |
| `socketReadTimeout` | `0` (disabled) | Set the underlying socket SO_TIMEOUT (milliseconds). |
| `socketWriteTimeout` | `60000` (ms, 60s) | Set the timeout around socket writes, providing support where Java is lacking. |
| `socketSoLinger` | `-1` (disabled) | Configure the socket SO_LINGER property for built-in data port implementations. |
| `socketReceiveBufferSize` | `-1` (OS default) | Set the underlying socket receive buffer size hint (SO_RCVBUF). |
| `socketSendBufferSize` | `-1` (OS default) | Set the underlying socket send buffer size hint (SO_SNDBUF). |
| `reconnectBufferSize` | `8388608` (bytes) | Configure reconnectBufferSize. |
| `reconnectWait` | `2000` (ms) | Configure reconnectWait. |
| `maxReconnects` | `60` | Configure maxReconnects. |
| `reconnectJitter` | `100` (ms) | Configure reconnectJitter. |
| `reconnectJitterTls` | `1000` (ms) | Configure reconnectJitterTls. |
| `reconnectDelayHandlerClass` | `(none)` | Configure a reconnectDelayHandler (class name). |
| `reconnectDelayBehavior` | `BeforeSubsequentRounds` | Whether the reconnect delay applies before the first round (case-insensitive `BeforeSubsequentRounds`, `BeforeAllRounds`, or `LameDuckAware`). |
| `pedantic` | `false` | Configure pedantic. |
| `verbose` | `false` | Configure verbose. |
| `noEcho` | `false` | Configure noEcho. |
| `connectionName` | `(none)` | Configure connectionName. |
| `noRandomize` | `false` | Configure noRandomize. |
| `hostnameResolveMode` | `ResolveToAll` | Configure the hostname resolution mode (case-insensitive `ResolveToAll`, `ResolveToFirst`, `ResolveToAllIncludeIPV6`, `ResolveToFirstIncludeIPV6`, `Unresolved`, or `HappyEyeballs`). Replaces the legacy noResolveHostnames and fast.fallback flags. |
| `subjectValidationType` | `Lenient` | Set the subject validation type (case-insensitive `None`, `Lenient`, or `Strict`). |
| `clientSideLimitChecks` | `true` | Configure clientSideLimitChecks. |
| `url` | `nats://localhost:4222` | Configure server. The value can be a comma-separated list of server URLs. |
| `servers` | `(none)` | Configure servers. The value can be a comma-separated list of server URLs. |
| `password` | `(none)` | Configure userinfo password. |
| `username` | `(none)` | Configure userinfo username. |
| `token` | `(none)` | Configure token. |
| `tokenSupplierClass` | `(none)` | Property used to set class name for the token supplier. |
| `secure` | `false` | See notes on SSL configuration. |
| `openTls` | `false` | See notes on SSL configuration. |
| `maxMessagesInOutgoingQueue` | `5000` | Configure maxMessagesInOutgoingQueue. |
| `discardMessagesWhenOutgoingQueueFull` | `false` | Configure discardMessagesWhenOutgoingQueueFull. |
| `maxControlLine` | `4096` | Configure maxControlLine. |
| `inboxPrefix` | `_INBOX.` | Property used to set the inbox prefix. |
| `ignoreDiscoveredServers` | `false` | Set whether to ignore discovered servers when connecting. |
| `serversPoolImplementationClass` | `(none)` | Property used to set class name for ServerPool implementation. |
| `dispatcherFactoryClass` | `(none)` | Property used to set class name for the Dispatcher Factory. |
| `sslContextFactoryClass` | `(none)` | Property used to set class name for the SSLContextFactory. |
| `keyStore` | `(none)` | Property for the keystore path used to create an SSLContext. |
| `keyStorePassword` | `(none)` | Property for the keystore password used to create an SSLContext. |
| `trustStore` | `(none)` | Property for the truststore path used to create an SSLContext. |
| `trustStorePassword` | `(none)` | Property for the truststore password used to create an SSLContext. |
| `tlsAlgorithm` | `SunX509` | Property for the algorithm used to create an SSLContext. |
| `credentialPath` | `(none)` | Property used to set the path to a credentials file to be used in a FileAuthHandler. |
| `tlsFirst` | `false` | Property used to set TLS Handshake First behavior. |
| `supportUtf8Subjects` | `false` | Property used to enable UTF-8 subject support. |
| `useTimeoutException` | `false` | Instruct the client to throw TimeoutException instead of CancellationException. |
| `useDispatcherWithExecutor` | `false` | Instruct dispatchers to dispatch all messages as a task. |
| `forceFlushOnRequest` | `true` | When making a core request, send the message as soon as it's first in the queue. |
| `executorServiceClass` | `(none)` | Property used to set class name for the main executor. |
| `scheduledExecutorServiceClass` | `(none)` | Property used to set class name for the scheduled executor. |
| `connectExecutorServiceClass` | `(none)` | Property used to set class name for the connection executor. |
| `callbackExecutorServiceClass` | `(none)` | Property used to set class name for the callback executor. |
| `connectThreadFactoryClass` | `(none)` | Property used to set class name for the connection executor thread factory. |
| `callbackThreadFactoryClass` | `(none)` | Property used to set class name for the callback executor thread factory. |
| `readListenerClass` | `(none)` | Property used to set class name for the ReadListener implementation. |
