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
