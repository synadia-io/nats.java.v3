![Synadia](core/src/main/javadoc/images/logo-black.png)

# NATS - Java Client VERSION 3

# WORK IN PROGRESS

### Properties

The property loader accepts each key with or without the `io.nats.client.` prefix; both forms resolve to the same property.

```
| Name                                  | Description                                                                          |
|---------------------------------------|--------------------------------------------------------------------------------------|
| connectionListenerClass               | Configure a connectionListener (class name).                                         |
| dataPortType                          | Configure a dataPortType.                                                            |
| errorListenerClass                    | Configure an errorListener (class name).                                             |
| timeTraceLoggerClass                  | Configure a TimeTraceLogger to receive trace events related to this connection (class name). |
| statisticsCollectorClass              | Configure the statisticsCollector (class name).                                      |
| maxPings                              | Configure maxPingsOut.                                                               |
| pingInterval                          | Configure pingInterval.                                                              |
| cleanupInterval                       | Configure requestCleanupInterval.                                                    |
| writeQueuePushTimeout                 | Configure writeQueuePushTimeout.                                                     |
| connectionTimeout                     | Configure connectionTimeout.                                                         |
| socketReadTimeout                     | Set the underlying socket SO_TIMEOUT (milliseconds).                                 |
| socketWriteTimeout                    | Set the timeout around socket writes, providing support where Java is lacking.       |
| socketSoLinger                        | Configure the socket SO_LINGER property for built-in data port implementations.      |
| socketReceiveBufferSize               | Set the underlying socket receive buffer size hint (SO_RCVBUF).                      |
| socketSendBufferSize                  | Set the underlying socket send buffer size hint (SO_SNDBUF).                         |
| reconnectBufSize                      | Configure reconnectBufferSize.                                                       |
| reconnectWait                         | Configure reconnectWait.                                                             |
| maxReconnect                          | Configure maxReconnects.                                                             |
| reconnectJitter                       | Configure reconnectJitter.                                                           |
| reconnectJitterTls                    | Configure reconnectJitterTls.                                                        |
| pedantic                              | Configure pedantic.                                                                  |
| verbose                               | Configure verbose.                                                                   |
| noEcho                                | Configure noEcho.                                                                    |
| noHeaders                             | Configure noHeaders.                                                                 |
| connectionName                        | Configure connectionName.                                                            |
| doNotReportNoResponders               | Configure doNotReportNoResponders.                                                   |
| noRandomize                           | Configure noRandomize.                                                               |
| hostnameResolveMode                   | Configure the hostname resolution mode (replaces the legacy noResolveHostnames and fast.fallback flags). |
| noSubjectValidation                   | Set subject validation to none.                                                      |
| strictSubjectValidation               | Set subject validation to strict.                                                    |
| reportNoResponders                    | Configure reportNoResponders.                                                        |
| clientSideLimitChecks                 | Configure clientSideLimitChecks.                                                     |
| url                                   | Configure server. The value can be a comma-separated list of server URLs.            |
| servers                               | Configure servers. The value can be a comma-separated list of server URLs.           |
| password                              | Configure userinfo password.                                                         |
| username                              | Configure userinfo username.                                                         |
| token                                 | Configure token.                                                                     |
| tokenSupplierClass                    | Property used to set class name for the token supplier.                              |
| secure                                | See notes on SSL configuration.                                                      |
| openTls                               | See notes on SSL configuration.                                                      |
| maxMessagesInOutgoingQueue            | Configure maxMessagesInOutgoingQueue.                                                |
| discardMessagesWhenOutgoingQueueFull  | Configure discardMessagesWhenOutgoingQueueFull.                                      |
| useOldRequestStyle                    | Configure oldRequestStyle.                                                           |
| maxControlLine                        | Configure maxControlLine.                                                            |
| inboxPrefix                           | Property used to set the inbox prefix.                                               |
| ignoreDiscoveredServers               | Set whether to ignore discovered servers when connecting.                            |
| serversPoolImplementationClass        | Property used to set class name for ServerPool implementation.                       |
| dispatcherFactoryClass                | Property used to set class name for the Dispatcher Factory.                          |
| sslContextFactoryClass                | Property used to set class name for the SSLContextFactory.                           |
| keyStore                              | Property for the keystore path used to create an SSLContext.                         |
| keyStorePassword                      | Property for the keystore password used to create an SSLContext.                     |
| trustStore                            | Property for the truststore path used to create an SSLContext.                       |
| trustStorePassword                    | Property for the truststore password used to create an SSLContext.                   |
| tlsAlgorithm                          | Property for the algorithm used to create an SSLContext.                             |
| credentialPath                        | Property used to set the path to a credentials file to be used in a FileAuthHandler. |
| tlsFirst                              | Property used to set TLS Handshake First behavior.                                   |
| supportUtf8Subjects                   | Property used to enable UTF-8 subject support.                                       |
| useTimeoutException                   | Instruct the client to throw TimeoutException instead of CancellationException.      |
| useDispatcherWithExecutor             | Instruct dispatchers to dispatch all messages as a task.                             |
| forceFlushOnRequest                   | When making a core request, send the message as soon as it's first in the queue.     |
| executorServiceClass                  | Property used to set class name for the main executor.                               |
| scheduledExecutorServiceClass         | Property used to set class name for the scheduled executor.                          |
| connectExecutorServiceClass           | Property used to set class name for the connection executor.                         |
| callbackExecutorServiceClass          | Property used to set class name for the callback executor.                           |
| connectThreadFactoryClass             | Property used to set class name for the connection executor thread factory.          |
| callbackThreadFactoryClass            | Property used to set class name for the callback executor thread factory.            |
| readListenerClass                     | Property used to set class name for the ReadListener implementation.                 |
```
