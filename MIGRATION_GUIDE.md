# Migration Guide — NATS Java v2 → v3

> **Work in progress.** This guide is being built up incrementally. Sections are added as APIs stabilize.

This is the top-level migration guide for moving code from the v2 client (`nats.java`) to v3 (`nats.java.v3`). The v3 client is reorganized into separate projects: **core**, **jetstream**, **kv**, **os**, and **service**. Migration topics are grouped by project.

---

## Core

- **[Options constants — user-facing changes](MIGRATION_GUIDE_OPTIONS.md)** — the `Options` class was split into `OptionsConstants` and `OptionsProperties`. Constants renamed to camelCase. May be folded into this guide later.

- **`Connection` interface is gone — use `NatsConnection` directly.**
  The v2 `io.nats.client.Connection` interface is removed. There is now exactly one connection type, `io.synadia.client.impl.NatsConnection`. It is created from the `NatsImpl` class (or, more commonly, via the `Nats.connect(...)` static factory which now returns `NatsConnection` instead of `Connection`).

  | v2 | v3 |
  |---|---|
  | `Connection nc = Nats.connect();` | `NatsConnection nc = Nats.connect();` |
  | `Connection nc = Nats.connect(options);` | `NatsConnection nc = Nats.connect(options);` |
  | `Connection nc = Nats.connect(url);` | `NatsConnection nc = Nats.connect(url);` |

  Anywhere a method previously returned `Connection`, it now returns `NatsConnection`. Anywhere you accepted `Connection` as a parameter, switch to `NatsConnection`.

- **Removed connect-option behavior.** Both the property constants and the corresponding `OptionsBuilder` setters are gone. Any code that referenced these must be removed; there is no replacement.

  | Removed constant            | Removed `OptionsBuilder` setter(s)                  | Reason                              |
  |-----------------------------|-----------------------------------------------------|-------------------------------------|
  | `PROP_TIME_TRACE_LOGGER`    | `timeTraceLogger(TimeTraceLogger)`, `traceConnection()` | Feature removed (the `TimeTraceLogger` interface is also gone) |
  | `PROP_REPORT_NO_RESPONDERS` | `reportNoResponders()`                              | Client always reports No Responders |

- **`OptionsBuilder.userInfo(String, String)` removed.** The `String`-typed overload is gone — Strings live on the heap until GC and can't be securely cleared. Use the existing `char[]` overload instead:

  | v2 | v3 |
  |---|---|
  | `builder.userInfo("user", "password")` | `builder.userInfo("user".toCharArray(), "password".toCharArray())` |

  The `char[]` overload was already present in v2; only the `String` overload is retired in v3. Token-based authentication (`token`, `tokenSupplier`), keystore passwords, and the property-file path were already `char[]`-based — no other credential setters change.

- **`OptionsBuilder.inboxPrefix(null)` / `inboxPrefix("")` no longer throw.** v2's setter would NPE on null (it dereferenced the argument to check for a trailing `"."`). v3 treats null / empty as "re-default at `build()` time" — the resulting `Options` carries `DEFAULT_INBOX_PREFIX`. Code that accidentally passed null now silently gets sensible behavior; code that deliberately passed null was broken in v2 anyway. Non-null, non-empty prefixes behave as before (with `"."` appended if missing).

- **`OptionsBuilder.noHeaders()` / `noNoResponders()` setters and their properties are gone.** v3 always advertises both headers and no-responders support in its `CONNECT` payload — every NATS server v3 supports has those features unconditionally, so the toggles were no-ops in practice (the values were hard-coded in the connect string). Migration:

  | v2 / removed in v3 | v3 |
  |---|---|
  | `builder.noHeaders()` | (delete) |
  | `builder.noNoResponders()` | (delete) |
  | `options.isNoHeaders()` | (delete; treat as always `false`) |
  | `options.isNoNoResponders()` | (delete; treat as always `false`) |
  | `io.nats.client.noHeaders` property | (delete; silently ignored if left in a properties file) |
  | `io.nats.client.noNoResponders` property | (delete; silently ignored if left in a properties file) |

- **Reconnect-delay configuration moves from `Duration` to `long` milliseconds.** The three reconnect timing values use primitive `long` ms everywhere — fewer allocations on the reconnect path, no null-handling, and the unit is visible at every call site. **Properties-file format changes for these three keys** (`PROP_RECONNECT_WAIT`, `PROP_RECONNECT_JITTER`, `PROP_RECONNECT_JITTER_TLS`): the value must now be a plain integer of milliseconds (e.g. `2000`). ISO-8601 duration strings (e.g. `PT2S`) no longer parse for these keys — anyone using that form must convert to milliseconds. Negative values continue to be silently ignored (the default survives), matching the pre-existing v2 behavior.

  | v2 / today | v3 |
  |---|---|
  | `builder.reconnectWait(Duration.ofSeconds(2))` | `builder.reconnectWait(2_000L)` |
  | `builder.reconnectJitter(Duration.ofMillis(100))` | `builder.reconnectJitter(100L)` |
  | `builder.reconnectJitterTls(Duration.ofSeconds(1))` | `builder.reconnectJitterTls(1_000L)` |
  | `options.getReconnectWait()` *(returns `Duration`)* | `options.getReconnectWaitMillis()` *(returns `long`)* |
  | `options.getReconnectJitter()` | `options.getReconnectJitterMillis()` |
  | `options.getReconnectJitterTls()` | `options.getReconnectJitterTlsMillis()` |
  | `DEFAULT_RECONNECT_WAIT` *(`Duration`)* | `DEFAULT_RECONNECT_WAIT_MILLIS` *(`long`)* |
  | `DEFAULT_RECONNECT_JITTER` | `DEFAULT_RECONNECT_JITTER_MILLIS` |
  | `DEFAULT_RECONNECT_JITTER_TLS` | `DEFAULT_RECONNECT_JITTER_TLS_MILLIS` |

  No `Duration` convenience overloads on the setters — pass a `long`. The other timing values on `Options` (connection/socket/ping timeouts, etc.) still use `Duration` for now; that conversion is tracked separately in `todo.md`.

- **`ReconnectDelayHandler` signature change.** The single abstract method goes from `Duration getWaitTime(long totalTries)` to `long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered)`. The interface stays single-abstract-method so lambda handlers continue to work. The handler is now stateless — the connection tracks the lame-duck signal itself and passes it in as the fourth argument. A new stateless `DefaultReconnectDelayHandler.INSTANCE` singleton is what `Options.getReconnectDelayHandler()` returns when the user hasn't supplied a custom handler, and it's safe to share across many connections. A new enum value `ReconnectDelayBehavior.LameDuckAware` is the default (only delays before round 1 when the server has signalled LDM; otherwise behaves like `BeforeSubsequentRounds`).

  | v2 / today | v3 |
  |---|---|
  | `l -> Duration.ofSeconds(l * 2)` | `(round, opts, secure, ld) -> round * 2_000L` |
  | `options.getReconnectDelayHandler() // may be null` | never null; defaults to `DefaultReconnectDelayHandler.INSTANCE` |

---

## JetStream

See **[jetstream/README.md](jetstream/README.md)** for the project overview.

### Obtaining a JetStream or JetStreamManagement context

In v2 you obtained these from the `Connection` interface:

```java
JetStream js                = nc.jetStream();
JetStream js                = nc.jetStream(jsOptions);
JetStreamManagement jsm     = nc.jetStreamManagement();
JetStreamManagement jsm     = nc.jetStreamManagement(jsOptions);
```

In v3, those overloads are removed from `NatsConnection`. Construct them directly with their public constructors:

```java
JetStream js                = new JetStream(nc);
JetStream js                = new JetStream(nc, jsOptions);
JetStreamManagement jsm     = new JetStreamManagement(nc);
JetStreamManagement jsm     = new JetStreamManagement(nc, jsOptions);
```

(Both classes live in `io.synadia.client.impl`. Static `instance(...)` factories also exist on `JetStream` if you prefer that style.)

### Simplified consumer renames

The simplified-consumer interfaces and their `Nats*` implementations have been renamed to make their role explicit (they consume `Message`s):

| v2 (`io.nats.client` / `io.nats.client.impl`) | v3 (`io.synadia.client.impl`) |
|---|---|
| `IterableConsumer`     | `IterableMessageConsumer` |
| `NatsIterableConsumer` | `NatsIterableMessageConsumer` |
| `FetchConsumer`        | `FetchMessageConsumer` |
| `NatsFetchConsumer`    | `NatsFetchMessageConsumer` |

These continue to extend `MessageConsumer` / `NatsMessageConsumerBase` as before — only the type names changed. Update imports and any explicit type declarations; behavior and method signatures are unchanged.

---

## KeyValue

See **[kv/README.md](kv/README.md)** for the project overview.

---

## ObjectStore

See **[os/README.md](os/README.md)** for the project overview.

---

## Service

See **[service/README.md](service/README.md)** for the project overview.
