# Plan: Convert Options PROP_* String Values to camelCase

Source file: `core/src/main/java/io/synadia/client/Options.java`

Rule: convert each constant's string value to lower-camelCase, derived from the Java constant name (i.e. drop `PROP_` then lowerCamel the remaining `_`-separated words). Where a constant lives behind `PFX`, the `PFX +` part is preserved.

The Java constant *names* (e.g. `PROP_CONNECTION_CB`) stay the same except where noted in Section C — **or** where the user has tagged a row with `[rename]` (see legend below).

## Legend

- `[rename]` anywhere in a row (after the constant name *or* after the new value) means: also rename the Java constant so it matches the new value. The new constant name is derived mechanically by UPPER_SNAKE_CASE-ing the new camelCase value. Examples:
  - `PROP_CONNECTION_CB [rename]` with new value `callbackConnection` → constant becomes `PROP_CALLBACK_CONNECTION`.
  - `callbackError [rename]` on the `PROP_ERROR_LISTENER` row → constant becomes `PROP_CALLBACK_ERROR`.
- No tag = constant name stays as it is, only the string value changes.

---

## A. Constants that use the `PFX` prefix

| Line | Constant | Current suffix | New suffix |
|------|----------|----------------|------------|
| 323 | `PROP_CONNECTION_CB` [rename] | `callback.connection` | `connectionListener` *(constant → `PROP_CONNECTION_LISTENER`)* |
| 328 | `PROP_DATA_PORT_TYPE` | `dataport.type` | `dataPortType` |
| 333 | `PROP_ERROR_LISTENER` | `callback.error` | `errorListener` |
| 338 | `PROP_TIME_TRACE_LOGGER` | `time.trace` | `timeTraceLogger` |
| 343 | `PROP_STATISTICS_COLLECTOR` | `statistics.collector` | `statisticsCollector` |
| 347 | `PROP_MAX_PINGS` | `max.pings` | `maxPings` |
| 352 | `PROP_PING_INTERVAL` | `ping.interval` | `pingInterval` |
| 357 | `PROP_CLEANUP_INTERVAL` | `cleanup.interval` | `cleanupInterval` |
| 362 | `PROP_WRITE_QUEUE_PUSH_TIMEOUT` | `write.queue.push.timeout` | `writeQueuePushTimeout` |
| 367 | `PROP_CONNECTION_TIMEOUT` | `timeout` | `connectionTimeout` |
| 372 | `PROP_SOCKET_READ_TIMEOUT_MS` [rename] | `socket.read.timeout.ms` | `socketReadTimeout` |
| 377 | `PROP_SOCKET_WRITE_TIMEOUT` | `socket.write.timeout` | `socketWriteTimeout` |
| 382 | `PROP_SOCKET_SO_LINGER` | `socket.so.linger` | `socketSoLinger` |
| 388 | `PROP_SOCKET_RECEIVE_BUFFER_SIZE` | `socket.receive.buffer.size` | `socketReceiveBufferSize` |
| 394 | `PROP_SOCKET_SEND_BUFFER_SIZE` | `socket.send.buffer.size` | `socketSendBufferSize` |
| 399 | `PROP_RECONNECT_BUF_SIZE` | `reconnect.buffer.size` | `reconnectBufSize` |
| 404 | `PROP_RECONNECT_WAIT` | `reconnect.wait` | `reconnectWait` |
| 409 | `PROP_MAX_RECONNECT` | `reconnect.max` | `maxReconnect` |
| 414 | `PROP_RECONNECT_JITTER` | `reconnect.jitter` | `reconnectJitter` |
| 419 | `PROP_RECONNECT_JITTER_TLS` | `reconnect.jitter.tls` | `reconnectJitterTls` |
| 423 | `PROP_PEDANTIC` | `pedantic` | `pedantic` *(no change)* |
| 427 | `PROP_VERBOSE` | `verbose` | `verbose` *(no change)* |
| 431 | `PROP_NO_ECHO` | `noEcho` | `noEcho` *(no change)* |
| 435 | `PROP_NO_HEADERS` | `noHeaders` | `noHeaders` *(no change)* |
| 440 | `PROP_CONNECTION_NAME` | `name` | `connectionName` |
| 444 | `PROP_NO_NORESPONDERS` [rename] | `noNoResponders` | `noNoResponders` *(value unchanged; constant → `PROP_NO_NO_RESPONDERS`)* |
| 448 | `PROP_NORANDOMIZE` [rename] | `noRandomize` | `noRandomize` *(value unchanged; constant → `PROP_NO_RANDOMIZE`)* |
| 453 | `PROP_HOSTNAME_RESOLVE_MODE` | `hostnameResolveMode` | `hostnameResolveMode` *(no change)* |
| 457 | `PROP_NO_SUBJECT_VALIDATION` | `noSubjectValidation` | `noSubjectValidation` *(no change)* |
| 461 | `PROP_STRICT_SUBJECT_VALIDATION` | `strictSubjectValidation` | `strictSubjectValidation` *(no change)* |
| 465 | `PROP_REPORT_NO_RESPONDERS` | `reportNoResponders` | `reportNoResponders` *(no change)* |
| 469 | `PROP_CLIENT_SIDE_LIMIT_CHECKS` | `clientsidelimitchecks` | `clientSideLimitChecks` |
| 474 | `PROP_SERVERS` | `servers` | `servers` *(no change)* |
| 479 | `PROP_PASSWORD` | `password` | `password` *(no change)* |
| 484 | `PROP_USERNAME` | `username` | `username` *(no change)* |
| 488 | `PROP_TOKEN` | `token` | `token` *(no change)* |
| 492 | `PROP_TOKEN_SUPPLIER` | `token.supplier` | `tokenSupplier` |
| 496 | `PROP_URL` | `url` | `url` *(no change)* |
| 503 | `PROP_SECURE` | `secure` | `secure` *(no change)* |
| 511 | `PROP_OPENTLS` [rename] | `opentls` | `openTls` |
| 516 | `PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE` | `outgoingqueue.maxmessages` | `maxMessagesInOutgoingQueue` |
| 522 | `PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL` | `outgoingqueue.discardwhenfull` | `discardMessagesWhenOutgoingQueueFull` |
| 568 | `PROP_KEYSTORE` [rename] | `keyStore` | `keyStore` *(no change — key + store)* |
| 572 | `PROP_KEYSTORE_PASSWORD` [rename] | `keyStorePassword` | `keyStorePassword` *(no change)* |
| 576 | `PROP_TRUSTSTORE` [rename] | `trustStore` | `trustStore` *(no change — trust + store)* |
| 580 | `PROP_TRUSTSTORE_PASSWORD` [rename] | `trustStorePassword` | `trustStorePassword` *(no change)* |
| 584 | `PROP_TLS_ALGORITHM` | `tls.algorithm` | `tlsAlgorithm` |
| 588 | `PROP_CREDENTIAL_PATH` | `credential.path` | `credentialPath` |
| 594 | `PROP_TLS_FIRST` | `tls.first` | `tlsFirst` |
| 600 | `PROP_USE_TIMEOUT_EXCEPTION` | `use.timeout.exception` | `useTimeoutException` |
| 605 | `PROP_USE_DISPATCHER_WITH_EXECUTOR` | `use.dispatcher.with.executor` | `useDispatcherWithExecutor` |
| 609 | `PROP_FORCE_FLUSH_ON_REQUEST` | `force.flush.on.request` | `forceFlushOnRequest` |

## B. Constants WITHOUT the `PFX` prefix

| Line | Constant | Current value | New value |
|------|----------|---------------|-----------|
| 527 | `PROP_USE_OLD_REQUEST_STYLE` | `use.old.request.style` | `useOldRequestStyle` |
| 532 | `PROP_MAX_CONTROL_LINE` | `max.control.line` | `maxControlLine` |
| 536 | `PROP_INBOX_PREFIX` | `inbox.prefix` | `inboxPrefix` |
| 559 | `PROP_DISPATCHER_FACTORY_CLASS` | `dispatcher.factory.class` | `dispatcherFactoryClass` |
| 564 | `PROP_SSL_CONTEXT_FACTORY_CLASS` | `ssl.context.factory.class` | `sslContextFactoryClass` |
| 614 | `PROP_EXECUTOR_SERVICE_CLASS` | `executor.service.class` | `executorServiceClass` |
| 619 | `PROP_SCHEDULED_EXECUTOR_SERVICE_CLASS` | `scheduled.executor.service.class` | `scheduledExecutorServiceClass` |
| 624 | `PROP_CONNECT_EXECUTOR_SERVICE_CLASS` | `connect.executor.service.class` | `connectExecutorServiceClass` |
| 629 | `PROP_CALLBACK_EXECUTOR_SERVICE_CLASS` | `callback.executor.service.class` | `callbackExecutorServiceClass` |
| 634 | `PROP_CONNECT_THREAD_FACTORY_CLASS` | `connect.thread.factory.class` | `connectThreadFactoryClass` |
| 639 | `PROP_CALLBACK_THREAD_FACTORY_CLASS` | `callback.thread.factory.class` | `callbackThreadFactoryClass` |
| 644 | `PROP_READ_LISTENER_CLASS` | `read.listener.class` | `readListenerClass` |

## C. `_PREFERRED` pairs — delete the non-preferred, rename the preferred

The non-preferred constant is removed entirely. The `_PREFERRED` constant is renamed to drop the suffix, and its value is camelCased.

| Action | Line | Constant | Resulting state |
|--------|------|----------|-----------------|
| **DELETE** | 540 | `PROP_IGNORE_DISCOVERED_SERVERS` (value `ignore_discovered_servers`) | removed |
| **RENAME + REVALUE** | 544 | `PROP_IGNORE_DISCOVERED_SERVERS_PREFERRED` (value `ignore.discovered.servers`) | becomes `PROP_IGNORE_DISCOVERED_SERVERS` with value `ignoreDiscoveredServers` |
| **DELETE** | 549 | `PROP_SERVERS_POOL_IMPLEMENTATION_CLASS` (value `servers_pool_implementation_class`) | removed |
| **RENAME + REVALUE** | 554 | `PROP_SERVERS_POOL_IMPLEMENTATION_CLASS_PREFERRED` (value `servers.pool.implementation.class`) | becomes `PROP_SERVERS_POOL_IMPLEMENTATION_CLASS` with value `serversPoolImplementationClass` |

After this, any references in the codebase to the deleted constants must be updated to use the new (un-suffixed) names. Their javadoc comments should also drop "preferred" wording.

---

## Summary

- Constants reviewed: **62**
- Values that change: **38**
- Values unchanged: **20**
- Constants deleted: **2** (the non-preferred pair)
- Constants renamed: **2** (drop `_PREFERRED`)

## Follow-up impact (not part of this plan, but worth knowing)

Anywhere in the codebase that *parses* property files keyed on the old dotted strings will need updating in lockstep — otherwise existing user-supplied `Properties` will silently stop being recognized. This plan only covers the constant declarations themselves; we'll handle call sites and any tests that hard-code the old strings as a follow-up.
