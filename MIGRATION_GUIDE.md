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

- **`Consumer` interface is gone.** The v2 `io.nats.client.Consumer` interface — the shared supertype of `Subscription` and `Dispatcher` that carried the pending-limit, pending/dropped/delivered counts, and `drain` methods — is removed, with no public replacement supertype. In practice it was almost never referenced by name (it only ever showed up as a supertype), so most code needs no change: every method it declared still exists, now declared directly on `Subscription` / `NatsSubscription` (synchronous) and on `Dispatcher` (asynchronous). The confusing "consumer" concept — the thing that owns a pending queue and can become a "slow consumer" — was renamed internally to `NatsMessageSink`, a more useful name for what it actually is (a sink messages drain into); that class is impl-only and never appears in the public API.

  | v2 | v3 |
  |---|---|
  | `Consumer` used as a type | use `Subscription` / `NatsSubscription` or `Dispatcher` directly |
  | `slowConsumerDetected(Connection, Consumer)` | `slowConsumerDetected(NatsConnection, Subscription)` |
  | `Consumer.DEFAULT_MAX_MESSAGES` / `Consumer.DEFAULT_MAX_BYTES` | `OptionsConstants.DEFAULT_MAX_MESSAGES` / `OptionsConstants.DEFAULT_MAX_BYTES` |

  One behavior change to note on the slow-consumer callback: for a dispatched (async) subscription, v2 handed you the `Dispatcher`; v3 always hands you the `Subscription` whose message hit the full queue — more useful, since the subscription names the subject.

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

- **Reconnect-delay configuration moves from `Duration` to `long` milliseconds.** The three reconnect timing values use primitive `long` ms everywhere — fewer allocations on the reconnect path, no null-handling, and the unit (milliseconds) is documented in the javadoc. **Property files need no change** for these keys (`PROP_RECONNECT_WAIT`, `PROP_RECONNECT_JITTER`, `PROP_RECONNECT_JITTER_TLS`): the loader accepts either a plain integer of milliseconds (e.g. `2000`) or the ISO-8601 duration form (e.g. `PT2S`), converted to whole milliseconds. Negative values continue to be silently ignored (the default survives), matching the pre-existing v2 behavior.

  | v2 / today | v3 |
  |---|---|
  | `builder.reconnectWait(Duration.ofSeconds(2))` | `builder.reconnectWait(2000)` |
  | `builder.reconnectJitter(Duration.ofMillis(100))` | `builder.reconnectJitter(100)` |
  | `builder.reconnectJitterTls(Duration.ofSeconds(1))` | `builder.reconnectJitterTls(1000)` |
  | `options.getReconnectWait()` *(returns `Duration`)* | `options.getReconnectWait()` *(returns `long`)* |
  | `options.getReconnectJitter()` | `options.getReconnectJitter()` |
  | `options.getReconnectJitterTls()` | `options.getReconnectJitterTls()` |
  | `DEFAULT_RECONNECT_WAIT` *(`Duration`)* | `DEFAULT_RECONNECT_WAIT` *(`long`)* |
  | `DEFAULT_RECONNECT_JITTER` | `DEFAULT_RECONNECT_JITTER` |
  | `DEFAULT_RECONNECT_JITTER_TLS` | `DEFAULT_RECONNECT_JITTER_TLS` |

  No `Duration` convenience overloads on the setters — pass a `long`. The other connection timing options (`connectionTimeout`, `socketWriteTimeout`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout`) made the same move — see [MIGRATION_GUIDE_OPTIONS.md](MIGRATION_GUIDE_OPTIONS.md) §7.

- **`ReconnectDelayHandler` signature change.** The single abstract method goes from `Duration getWaitTime(long totalTries)` to `long getWaitTime(long round, Options options, boolean secure, boolean lameDuckTriggered)`. The interface stays single-abstract-method so lambda handlers continue to work. The handler is now stateless — the connection tracks the lame-duck signal itself and passes it in as the fourth argument. A new stateless `DefaultReconnectDelayHandler.INSTANCE` singleton is what `Options.getReconnectDelayHandler()` returns when the user hasn't supplied a custom handler, and it's safe to share across many connections. A new enum value `ReconnectDelayBehavior.LameDuckAware` is the default (only delays before round 1 when the server has signalled LDM; otherwise behaves like `BeforeSubsequentRounds`).

  | v2 / today | v3 |
  |---|---|
  | `l -> Duration.ofSeconds(l * 2)` | `(round, opts, secure, ld) -> round * 2000` |
  | `options.getReconnectDelayHandler() // may be null` | never null; defaults to `DefaultReconnectDelayHandler.INSTANCE` |

- **Runtime method timeouts move from `Duration` to `long` / `Long` milliseconds.** Every "how long do I wait" timeout on the runtime API — not just the `Options` config covered above — is now a primitive millisecond value: no per-call allocation, no `null`-`Duration` handling, unit documented in the javadoc. The `Duration` overloads are dropped (the lone exception is `nakWithDelay`, which keeps a `Duration` convenience overload for expressing seconds/minutes). Methods that **read/consume a message** take a nullable `@Nullable Long` so `null` can still carry the meaning the old `null`-`Duration` had; every other timeout takes a plain `long`. The catch when porting is the sentinels — a `0` or negative value can silently change behavior:

  | v2 | v3 | sentinel for `0` / negative / `null` |
  |---|---|---|
  | `sub.nextMessage(Duration.ofSeconds(1))` | `sub.nextMessage(1000L)` | `@Nullable Long`: **`null` = poll once / return immediately**, `<= 0` = wait forever, `> 0` = wait that long |
  | `nc.request(subj, data, Duration.ofSeconds(2))` | `nc.request(subj, data, 2000)` | `<= 0` = use the default (blocks for the connection timeout) |
  | `nc.requestAsync(subj, data, Duration.ofSeconds(2))` | `nc.requestAsync(subj, data, 2000)` | `<= 0` = use the default (future swept at the request-cleanup interval) |
  | `nc.flush(Duration.ofSeconds(5))` | `nc.flush(5000)` | `<= 0` = wait forever |
  | `consumer.drain(Duration.ofSeconds(10))` | `consumer.drain(10000)` | `<= 0` = wait forever |
  | `msg.ackSync(Duration.ofSeconds(2))` | `msg.ackSync(2000)` | must be `> 0` |
  | `msg.nakWithDelay(Duration.ofSeconds(5))` | `msg.nakWithDelay(5000)` *or keep* `msg.nakWithDelay(Duration.ofSeconds(5))` | `Duration` overload retained for convenience |
  | `ctx.next(Duration.ofSeconds(1))` | `ctx.next(1000L)` | `@Nullable Long`: `null` / `<= 0` → default expiry; a positive value must be `>= MIN_EXPIRES_MILLS` |
  | `sub.fetch(100, Duration.ofSeconds(1))` | `sub.fetch(100, 1000L)` | `@Nullable Long`: `null` / `<= 0` throws — a positive max-wait is required |
  | `sub.iterate(100, Duration.ofSeconds(1))` | `sub.iterate(100, 1000L)` | same as `fetch` — positive required |

  **The most common gotcha is `nextMessage`.** v3 maps `null` = poll once and `<= 0` = wait forever. If you used a v2 idiom that passed a negative/zero `Duration` to mean "return immediately," pass `null` now — **not `0L`, which now waits forever.** The reading methods are boxed (`Long`) specifically so `null` survives; the non-reading ones (`request`, `requestAsync`, `flush`, `drain`, `ackSync`) are plain `long` and have no `null` form.

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

### JetStream API timeouts move from `Duration` to `long` milliseconds

The JetStream request and publish-ack timeouts follow the same `Duration` → `long` ms move as the connection options — no allocation, no null-handling, unit (milliseconds) documented in the javadoc — and the `Duration` overloads are dropped.

| v2 / today | v3 |
|---|---|
| `JetStreamOptions.builder().requestTimeout(Duration.ofSeconds(5))` | `.requestTimeout(5000)` |
| `jso.getRequestTimeout()` *(returns `Duration`)* | `jso.getRequestTimeout()` *(returns `long`)* |
| `featureOptionsBuilder.jsRequestTimeout(Duration.ofSeconds(5))` | `.jsRequestTimeout(5000)` |
| `PublishOptions.builder().streamTimeout(Duration.ofSeconds(2))` | `.streamTimeout(2000L)` *(boxed `Long` — the `L` is required; `.streamTimeout(null)` resets to the default)* |
| `po.getStreamTimeout()` *(returns `Duration`)* | `po.getStreamTimeout()` *(returns `long`)* |
| `PublishOptions.DEFAULT_TIMEOUT` *(`Duration`)* | `PublishOptions.DEFAULT_TIMEOUT` *(`long`)* |

Two behavior notes:
- **`PublishOptions` property file needs no change.** `PROP_PUBLISH_TIMEOUT` accepts either a plain integer of milliseconds (e.g. `1200000`) or the ISO-8601 duration form (e.g. `PT20M`), converted to whole milliseconds — same loader as the connection-timeout properties (see [MIGRATION_GUIDE_OPTIONS.md](MIGRATION_GUIDE_OPTIONS.md) §7).
- **`JetStreamOptions.requestTimeout`'s "unset" sentinel.** v2 used `null` to mean "not set → fall back to the connection timeout." With a primitive `long`, a value `<= 0` means the same thing — so `requestTimeout(0)` (or never setting it) falls back to the connection timeout, exactly as `null` did.
- **`PublishOptions.streamTimeout` keeps the `null`-resets-to-default behavior.** Unlike the other JetStream timeouts (primitive `long`), `streamTimeout` takes a `@Nullable Long`: passing `null` resets the field to `DEFAULT_TIMEOUT` (reproducing the old `Duration`-`null` semantics), `0` is a valid explicit value, and a negative throws. Because the parameter is a boxed `Long`, integer literals need the `L` suffix — `streamTimeout(2000L)`, not `streamTimeout(2000)`.

---

## KeyValue

See **[kv/README.md](kv/README.md)** for the project overview.

---

## ObjectStore

See **[os/README.md](os/README.md)** for the project overview.

---

## Service

See **[service/README.md](service/README.md)** for the project overview.
