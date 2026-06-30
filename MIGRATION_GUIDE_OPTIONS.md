# Options Constants — User-Facing Changes

## 1. Structural changes

1. Constants moved out of `Options` into two new files in `io.synadia.client`:
   - `OptionsConstants` — `DEFAULT_*`, `MINIMUM_*`, `MAX_*` value constants and `OPTION_*` protocol-key constants.
   - `OptionsProperties` — `PFX`, `PFX_LEN`, all `PROP_*` keys, and the static `Properties`-loading helpers (`stringProperty`, `booleanProperty`, etc.).
2. Every `PROP_*` value is camelCase; dotted/lower-case keys are gone.

Anything outside `OptionsConstants` / `OptionsProperties` that referenced `Options.PROP_*` or `Options.DEFAULT_*` needs a fresh import. Easiest migration: `import static io.synadia.client.OptionsProperties.*;` and `import static io.synadia.client.OptionsConstants.*;`. You said you'd handle imports manually.

---

## 2. Property key prefix is optional

In the tables below, the `Current value` column shows each key **without** the `io.nats.client.` prefix for readability. 

The property loader accepts a property with the bare key (e.g. `connectionListenerClass`) 
or the prefixed key (e.g. `io.nats.client.connectionListenerClass`). 
Both resolve to the same property.

> This is not a change in behavior, just noted here for completeness.

## 3. Constants whose names changed

| Old name                      | New name                          | Current value                   | Old value if changed     |
|-------------------------------|-----------------------------------|---------------------------------|--------------------------|
| `PROP_CONNECTION_CB`          | `PROP_CONNECTION_LISTENER_CLASS`  | `connectionListenerClass`       | `callback.connection`    |
| `PROP_ERROR_LISTENER`         | `PROP_ERROR_LISTENER_CLASS`       | `errorListenerClass`            | `callback.error`         |
| `PROP_STATISTICS_COLLECTOR`   | `PROP_STATISTICS_COLLECTOR_CLASS` | `statisticsCollectorClass`      | `statisticscollector`    |
| `PROP_TOKEN_SUPPLIER`         | `PROP_TOKEN_SUPPLIER_CLASS`       | `tokenSupplierClass`            | `token.supplier`         |
| `PROP_SOCKET_READ_TIMEOUT_MS` | `PROP_SOCKET_READ_TIMEOUT`        | `socketReadTimeout`             | `socket.read.timeout.ms` |
| `PROP_KEYSTORE`               | `PROP_KEY_STORE`                  | `keyStore`                      |                          |
| `PROP_KEYSTORE_PASSWORD`      | `PROP_KEY_STORE_PASSWORD`         | `keyStorePassword`              |                          |
| `PROP_TRUSTSTORE`             | `PROP_TRUST_STORE`                | `trustStore`                    |                          |
| `PROP_TRUSTSTORE_PASSWORD`    | `PROP_TRUST_STORE_PASSWORD`       | `trustStorePassword`            |                          |
| `PROP_NORANDOMIZE`            | `PROP_NO_RANDOMIZE`               | `noRandomize`                   | `norandomize`            |
| `PROP_OPENTLS`                | `PROP_OPEN_TLS`                   | `openTls`                       | `opentls`                |
| `PROP_UTF8_SUBJECTS`          | `PROP_SUPPORT_UTF8_SUBJECTS`      | `supportUtf8Subjects`           | `allow.utf8.subjects`    |

## 4. Constants whose values changed (name unchanged)

The `Old value` column shows the value as it appears in the old `io.nats.client.Options`. Both columns follow the §2 convention — the `io.nats.client.` prefix is stripped where it was present. Old values without a prefix (e.g. `use.old.request.style`) had no `PFX` in the original code; those are flagged as "(no PFX)".

| Current Constant                                 | Current value                          | Old value                           |
|--------------------------------------------------|----------------------------------------|-------------------------------------|
| `PROP_DATA_PORT_TYPE`                            | `dataPortType`                         | `dataport.type`                     |
| `PROP_NO_ECHO`                                   | `noEcho`                               | `noecho`                            |
| `PROP_MAX_PINGS_OUT`                                 | `maxPingsOut`                             | `maxpings`                          |
| `PROP_PING_INTERVAL`                             | `pingInterval`                         | `pinginterval`                      |
| `PROP_REQUEST_CLEANUP_INTERVAL`                          | `requestCleanupInterval`                      | `cleanupinterval`                   |
| `PROP_CONNECTION_TIMEOUT`                        | `connectionTimeout`                    | `timeout`                           |
| `PROP_SOCKET_WRITE_TIMEOUT`                      | `socketWriteTimeout`                   | `socket.write.timeout`              |
| `PROP_SOCKET_SO_LINGER`                          | `socketSoLinger`                       | `socket.so.linger`                  |
| `PROP_SOCKET_RECEIVE_BUFFER_SIZE`                | `socketReceiveBufferSize`              | `socket.receive.buffer.size`        |
| `PROP_SOCKET_SEND_BUFFER_SIZE`                   | `socketSendBufferSize`                 | `socket.send.buffer.size`           |
| `PROP_RECONNECT_BUFFER_SIZE`                        | `reconnectBufferSize`                     | `reconnect.buffer.size`             |
| `PROP_RECONNECT_WAIT`                            | `reconnectWait`                        | `reconnect.wait`                    |
| `PROP_MAX_RECONNECTS`                             | `maxReconnects`                         | `reconnect.max`                     |
| `PROP_RECONNECT_JITTER`                          | `reconnectJitter`                      | `reconnect.jitter`                  |
| `PROP_RECONNECT_JITTER_TLS`                      | `reconnectJitterTls`                   | `reconnect.jitter.tls`              |
| `PROP_CONNECTION_NAME`                           | `connectionName`                       | `name`                              |
| `PROP_CLIENT_SIDE_LIMIT_CHECKS`                  | `clientSideLimitChecks`                | `clientsidelimitchecks`             |
| `PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE`            | `maxMessagesInOutgoingQueue`           | `outgoingqueue.maxmessages`         |
| `PROP_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL` | `discardMessagesWhenOutgoingQueueFull` | `outgoingqueue.discardwhenfull`     |
| `PROP_TLS_ALGORITHM`                             | `tlsAlgorithm`                         | `tls.algorithm`                     |
| `PROP_CREDENTIAL_PATH`                           | `credentialPath`                       | `credential.path`                   |
| `PROP_TLS_FIRST`                                 | `tlsFirst`                             | `tls.first`                         |
| `PROP_USE_TIMEOUT_EXCEPTION`                     | `useTimeoutException`                  | `use.timeout.exception`             |
| `PROP_USE_DISPATCHER_WITH_EXECUTOR`              | `useDispatcherWithExecutor`            | `use.dispatcher.with.executor`      |
| `PROP_FORCE_FLUSH_ON_REQUEST`                    | `forceFlushOnRequest`                  | `force.flush.on.request`            |
| `PROP_MAX_CONTROL_LINE`                          | `maxControlLine`                       | `max.control.line`                  |
| `PROP_INBOX_PREFIX`                              | `inboxPrefix`                          | `inbox.prefix`                      |
| `PROP_IGNORE_DISCOVERED_SERVERS`                 | `ignoreDiscoveredServers`              | `ignore_discovered_servers`         |
| `PROP_SERVERS_POOL_IMPLEMENTATION_CLASS`         | `serversPoolImplementationClass`       | `servers_pool_implementation_class` |
| `PROP_DISPATCHER_FACTORY_CLASS`                  | `dispatcherFactoryClass`               | `dispatcher.factory.class`          |
| `PROP_SSL_CONTEXT_FACTORY_CLASS`                 | `sslContextFactoryClass`               | `ssl.context.factory.class`         |
| `PROP_EXECUTOR_SERVICE_CLASS`                    | `executorServiceClass`                 | `executor.service.class`            |
| `PROP_SCHEDULED_EXECUTOR_SERVICE_CLASS`          | `scheduledExecutorServiceClass`        | `scheduled.executor.service.class`  |
| `PROP_CONNECT_EXECUTOR_SERVICE_CLASS`            | `connectExecutorServiceClass`          | `connect.executor.service.class`    |
| `PROP_CALLBACK_EXECUTOR_SERVICE_CLASS`           | `callbackExecutorServiceClass`         | `callback.executor.service.class`   |
| `PROP_CONNECT_THREAD_FACTORY_CLASS`              | `connectThreadFactoryClass`            | `connect.thread.factory.class`      |
| `PROP_CALLBACK_THREAD_FACTORY_CLASS`             | `callbackThreadFactoryClass`           | `callback.thread.factory.class`     |
| `PROP_READ_LISTENER_CLASS`                       | `readListenerClass`                    | `read.listener.class`               |

## 5. Constants unchanged (name and value)

| Constant                          | Current value             |
|-----------------------------------|---------------------------|
| `PROP_PEDANTIC`                   | `pedantic`                |
| `PROP_VERBOSE`                    | `verbose`                 |
| `PROP_WRITE_QUEUE_PUSH_TIMEOUT`   | `writeQueuePushTimeout`   |
| `PROP_HOSTNAME_RESOLVE_MODE`      | `hostnameResolveMode`     |
| `PROP_SERVERS`                    | `servers`                 |
| `PROP_PASSWORD`                   | `password`                |
| `PROP_USERNAME`                   | `username`                |
| `PROP_TOKEN`                      | `token`                   |
| `PROP_URL`                        | `url`                     |
| `PROP_SECURE`                     | `secure`                  |


## 6. Constants removed

| Constant                          | Reason                                                                                  |
|-----------------------------------|-----------------------------------------------------------------------------------------|
| `PROP_TIME_TRACE_LOGGER`          | Feature removed                                                                         |
| `PROP_REPORT_NO_RESPONDERS`       | Client always reports No Responders                                                     |
| `PROP_NO_SUBJECT_VALIDATION`      | Use `PROP_SUBJECT_VALIDATION_TYPE` (value `None`) instead of the legacy boolean         |
| `PROP_STRICT_SUBJECT_VALIDATION`  | Use `PROP_SUBJECT_VALIDATION_TYPE` (value `Strict`) instead of the legacy boolean       |
| `PROP_NO_HEADERS`                 | v3 always advertises headers support; the toggle was no longer wired                    |
| `PROP_NO_NO_RESPONDERS`           | v3 always advertises no-responders support; the toggle was no longer wired              |

## 7. Timing options are milliseconds (value-format change)

The connection timing options moved from `Duration` to a plain `long` **milliseconds**: `connectionTimeout`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout`, and `socketWriteTimeout` (the reconnect options `reconnectWait`, `reconnectJitter`, `reconnectJitterTls` already made this move). They are all milliseconds — there is no nanosecond special case anymore.

**Property files need no change.** The loader (`millisProperty`) accepts either a plain integer number of milliseconds (e.g. `2000`) **or** the ISO-8601 duration form (e.g. `PT2S`), converted to whole milliseconds — so existing v2 property files using `PT…` keep working. Negative values are ignored (the default is kept); a value that is neither an integer nor a valid duration throws `NumberFormatException` at build time. This applies uniformly, including `socket.write.timeout`.

**API shape change** for the same options on `OptionsBuilder` / `Options`. The setters and getters keep their plain names but now take / return a `long` (milliseconds) instead of a `Duration`; the `Duration` overloads were dropped. The unit is documented in the javadoc rather than baked into the method name.
- Setters: `connectionTimeout(long)`, `pingInterval(long)`, `requestCleanupInterval(long)`, `writeQueuePushTimeout(long)`, `socketWriteTimeout(long)`, `reconnectWait(long)`, `reconnectJitter(long)`, `reconnectJitterTls(long)` — all milliseconds (the parameter is named simply `millis`).
- Getters: `getConnectionTimeout()`, `getPingInterval()`, `getRequestCleanupInterval()`, `getWriteQueuePushTimeout()`, `getSocketWriteTimeout()` — all `long` milliseconds.
- `socketWriteTimeout` is in **milliseconds**; a value below `MINIMUM_SOCKET_WRITE_TIMEOUT` (`1` ms, including `<= 0`) disables it, mirroring `socketReadTimeout`. Default `DEFAULT_SOCKET_WRITE_TIMEOUT = 60000L` (60 s). *(It was previously `long` nanoseconds — the floor constant was `MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS = 100`, now `MINIMUM_SOCKET_WRITE_TIMEOUT = 1` ms; if you had set the timeout in nanoseconds, e.g. `60_000_000_000L`, divide by 1,000,000.)*
- `socketReadTimeout` widened from `int` to **`long`** (still milliseconds, still `<= 0` disables — it maps to `setSoTimeout`, which the library casts back to `int` at the one call site). `getSocketReadTimeout()` now returns `long` and `socketReadTimeout(long millis)` takes `long`, bringing it in line with the other millisecond timing options. Source-compatible: `socketReadTimeout(44)` still compiles (the `int` literal widens). The other two socket ints — `socketSoLinger` (**seconds**, not millis — it maps straight to `setSoLinger`) and the `socketReceiveBufferSize` / `socketSendBufferSize` byte counts — stay `int`.

## 8. Prompts for Claude Code

Drop this `MIGRATION_GUIDE_OPTIONS.md` file into your project (or pass its path to Claude Code) and use one of the prompts below to migrate.

### 7.1 Convert a properties file

```
I have a Java properties file (or files) at <PATH(S)> that uses old NATS Options
property keys. Update every key to the current form using the mapping in
MIGRATION_GUIDE_OPTIONS.md (sections 3, 4, and 5).

Rules:
- Match against the "Old value" column (with or without the `io.nats.client.` prefix).
- Replace with the "Current value", keeping whichever prefix style the file already uses
  (i.e. if the original key was bare, leave the new key bare; if it was prefixed, keep the
  prefix). Both forms are accepted by the loader.
- Preserve each key's existing value, comments, and ordering.
- For keys you cannot map (not present in any of sections 3, 4, or 5), leave the line in
  place and add a `# TODO:` comment above it noting the unmapped key. Do not delete it.
- The keys `noResolveHostnames`, `no.resolve.hostnames`, `fast.fallback` are no longer
  supported on their own; they were folded into a single `hostnameResolveMode` enum
  property. Flag any of these with a `# TODO:` comment that mentions
  `PROP_HOSTNAME_RESOLVE_MODE`.
- Report any keys you couldn't map.
```

### 7.2 Convert Java code

```
Update Java source under <PATH> to use the new NATS Options constants per
MIGRATION_GUIDE_OPTIONS.md.

Do all of the following:
1. Rewrite identifier references for every renamed constant in section 3
   (e.g. `Options.PROP_CONNECTION_CB` → `OptionsProperties.PROP_CONNECTION_LISTENER`,
   `Options.PROP_KEYSTORE` → `OptionsProperties.PROP_KEY_STORE`, etc.).
2. Move references to the right new home:
   - `PROP_*`, `PFX`, `PFX_LEN` now live in `io.synadia.client.OptionsProperties`.
   - `DEFAULT_*`, `MINIMUM_*`, `MAX_*`, `OPTION_*` now live in `io.synadia.client.OptionsConstants`.
   Adjust qualified references and `import` / `import static` lines accordingly.
3. The constants `PROP_NO_RESOLVE_HOSTNAMES` and `PROP_FAST_FALLBACK` no longer exist —
   their functionality was folded into `PROP_HOSTNAME_RESOLVE_MODE`. Do NOT silently
   substitute; flag each call site with a `// TODO:` comment that names
   `PROP_HOSTNAME_RESOLVE_MODE` as the closest current equivalent and asks for human review.
4. Do not touch any string literals that happen to look like old property values; only
   update references to the Java constants themselves.
5. Leave behavior unchanged. Compile and report any unresolved references.
```

## 9. v3 property keys and builder API renamed for consistency

Late in v3 a few property keys, builder setters, and getters were renamed so the **property key, the builder setter, and the getter all agree**. (Previously the property key sometimes differed from the API name — e.g. the key `maxPings` set `maxPingsOut`.)

**Property keys** (what you put in a `.properties` file):

| Old key | New key |
|---|---|
| `maxPings` | `maxPingsOut` |
| `cleanupInterval` | `requestCleanupInterval` |
| `reconnectBufSize` | `reconnectBufferSize` |
| `maxReconnect` | `maxReconnects` |

**Builder methods / getters** (Java code):

| Old | New |
|---|---|
| `OptionsBuilder.receiveBufferSize(int)` | `OptionsBuilder.socketReceiveBufferSize(int)` |
| `OptionsBuilder.sendBufferSize(int)` | `OptionsBuilder.socketSendBufferSize(int)` |
| `Options.getReceiveBufferSize()` | `Options.getSocketReceiveBufferSize()` |
| `Options.getSendBufferSize()` | `Options.getSocketSendBufferSize()` |
| `Options.getMaxReconnect()` | `Options.getMaxReconnects()` |
| `OptionsBuilder.opentls()` / `opentls(boolean)` | `OptionsBuilder.openTls()` / `openTls(boolean)` |

For `maxPings`/`cleanupInterval`/`reconnectBufSize`/`maxReconnect` the **key** was changed to match the existing setter/getter; for the socket buffer sizes the **setter/getter** were changed to match the existing `socketReceiveBufferSize`/`socketSendBufferSize` keys; for `opentls` only the method casing was fixed (the `openTls` property key was already correct). The `PROP_*` constants were renamed to match (`PROP_MAX_PINGS`→`PROP_MAX_PINGS_OUT`, `PROP_CLEANUP_INTERVAL`→`PROP_REQUEST_CLEANUP_INTERVAL`, `PROP_RECONNECT_BUF_SIZE`→`PROP_RECONNECT_BUFFER_SIZE`, `PROP_MAX_RECONNECT`→`PROP_MAX_RECONNECTS`).
