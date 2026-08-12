# Migration Guide — NATS Java v2 → v3

> **Work in progress.** This guide is being built up incrementally. Sections are added as APIs stabilize.

This is the top-level migration guide for moving code from the v2 client (`nats.java`) to v3 (`nats.java.v3`). The v3 client is reorganized into separate projects: **core**, **jetstream**, **kv**, **os**, and **service**. Migration topics are grouped by project.

---

## Exceptions — read this first

> **Work in progress.** This section tracks the v3 exception rework as it lands. Re-read it when you take a new v3 build. It has two parts: the **reclassifications** (usage errors that were `IOException` are now unchecked — first), and the **`JetStreamException` consolidation** (the `IOException` + `JetStreamApiException` pair on the JetStream surface collapses to one base type — second).

v3 is correcting a long-standing v2 habit: throwing a **checked `IOException` for things that were never I/O problems.** In v2 several client-side validation failures — misuse the API and you get an exception — were reported as `IOException` purely because the surrounding method already declared it and changing the signature would have been a breaking change. v3 is the major version where that constraint is lifted, so those cases now throw the *unchecked* exception that actually fits (`IllegalStateException` for calling something at the wrong time, `IllegalArgumentException` for a bad argument). These are programming errors — you fix them by changing your code, not by catching them at runtime — so forcing a `catch` was never the right shape.

**Two things change, and they migrate very differently.**

**1. Calls that no longer declare `IOException` at all — the compiler will find these for you.** Constructing a JetStream context does not talk to the server, so it can no longer fail with an `IOException`; the constructors and factories dropped it:

| Affected | v2 | v3 |
|---|---|---|
| `new JetStream(nc)` / `JetStream.instance(nc)` | `throws IOException` | (no checked exception) |
| `new JetStreamManagement(nc)` / `.instance(nc)` | `throws IOException` | (no checked exception) |
| `new KeyValueManagement(nc)` / `new ObjectStoreManagement(nc)` | `throws IOException` | (no checked exception) |
| `new ObjectStore(...)` / `osm.objectStore(name)` | `throws IOException` | (no checked exception) |

If your v2 code wrapped one of these in `try { … } catch (IOException e)`, it will now **fail to compile** — `error: exception IOException is never thrown in body of corresponding try statement`. That is the safe kind of break: the compiler points at every site and you delete the dead `catch`. (The "connection is closing/closed" guard these had still fires — it is now an `IllegalStateException`, unchecked, which you should not normally catch.)

One asymmetry to expect: **getting a KV bucket still throws, getting an OS bucket does not.** `KeyValueManagement.keyValue(name)` (and the `KeyValue` constructor) still declare `throws IOException` because they verify the backing stream exists (`getStreamInfo`) at creation time — a real server round-trip. `ObjectStore` does no such check at construction, so it dropped the exception. This is not an oversight; it reflects that only one of the two actually contacts the server when you open the bucket.

**2. Calls that still declare `IOException` but no longer route *every* failure through it — the compiler will NOT warn you.** This is the dangerous case. `ConsumerContext.next()` / `fetch()` / `iterate()` / `consume()` still throw `IOException` (a real request over the wire genuinely can), so a `catch (IOException)` around them still compiles cleanly — but two client-side checks that used to land in that `catch` now throw unchecked `IllegalStateException` and sail straight past it:

| Now `IllegalStateException` (was `IOException`) | When |
|---|---|
| `"The ordered consumer is already receiving messages…"` | you start a `next`/`fetch`/`iterate`/`consume` on an ordered consumer while a previous one is still running |
| `"Pinned not allowed with Next/Fetch"` | you call `next`/`fetch` on a pinned-client consumer |

So if you have v2 code shaped like this:

```java
try {
    Message m = consumerContext.next(1000L);
    // …
} catch (IOException e) {
    // in v2 this ALSO caught "already receiving" / "pinned not allowed"
    handleProblem(e);
}
```

it still compiles under v3, but those two conditions now escape as `IllegalStateException` and reach whatever is above you — often an uncaught crash. There is no compiler error to lead you here, so **grep your codebase for `catch` blocks around consumer `next`/`fetch`/`iterate`/`consume` and around JetStream/KV/OS context creation, and check whether you were relying on `IOException` to catch a *usage* error.** If you were, either fix the misuse (the right answer — these fire only when the calling code is wrong) or add a `catch (IllegalStateException e)`.

### The `JetStreamException` consolidation

This is the change the reclassifications above were clearing the way for. On the JetStream surface, the v2 signature pair `throws IOException, JetStreamApiException` becomes a single `throws JetStreamException` — plus `throws InterruptedException`, which v2 hid by reboxing it as `IOException`. So a JetStream call that read `throws IOException, JetStreamApiException` in v2 reads `throws JetStreamException, InterruptedException` in v3:

```java
// v2
public StreamInfo getStreamInfo(String streamName) throws IOException, JetStreamApiException
// v3
public StreamInfo getStreamInfo(String streamName) throws JetStreamException, InterruptedException
```

Two exceptions, both of which are true — replacing two, one of which (`IOException`) never actually happened on this surface. Every synthetic `IOException` the JetStream layer used to throw (a timeout, a status error, a bad ack, a rewrapped interrupt) is now thrown as the type that fits, all under the `JetStreamException` base.

**What to catch.** Catch the base `JetStreamException` and, if you need to tell the failures apart, `switch` on the subtype (Java 21 pattern-matching switch, no `instanceof` ladder):

```java
try {
    js.publish(subject, data);
}
catch (JetStreamException e) {
    switch (e) {
        case JetStreamApiException api      -> report(api.getError());   // server returned an error
        case JetStreamStatusException st    -> inspect(st.getStatus());  // unexpected status message
        case JetStreamTimeoutException t    -> retry();                  // no response in time
        case JetStreamProtocolException p   -> fail(p);                  // malformed reply
        default                             -> fail(e);                  // required — the base is not sealed
    }
}
catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // abandon or retry
}
```

The `default` is not optional and not a wart: `JetStreamException` is deliberately **not** `sealed`, so a future v3 build can add a subtype without breaking your switch. That is the whole reason it isn't sealed — additive, non-breaking evolution of the failure taxonomy.

**The subtypes:**

| Type | Package | Means | Key accessor |
|---|---|---|---|
| `JetStreamException` | `io.synadia.client.api` | base — catch this | — |
| `JetStreamApiException` | `io.synadia.client.impl` | the server returned an `Error` (JetStream API error) | `getError()` |
| `JetStreamStatusException` | `io.synadia.client.impl` | an unexpected / unhandled status message | `getStatus()` |
| `JetStreamTimeoutException` | `io.synadia.client.api` | no response within the request timeout | — |
| `JetStreamProtocolException` | `io.synadia.client.api` | malformed response (the v2 "Invalid JetStream ack" cases) | — |

`JetStreamApiException` is unchanged as the runtime type for server-side API errors — if your v2 code already did `catch (JetStreamApiException)`, it still catches exactly the same failures. It is now *also* a `JetStreamException`, so you can widen to the base and drop the separate `IOException` catch in the same edit. (The `.impl`-package subtypes are slated to move to `.api` in a later v3 build; catching the base `JetStreamException` — which is already in `.api` — insulates you from that move entirely.)

**Two rename notes, only relevant if you referenced these types by name:**
- `JetStreamStatusCheckedException` is **gone.** The checked "unexpected status" error you would catch is now just `JetStreamStatusException`.
- The v2 `JetStreamStatusException` was an *unchecked* internal signal; it is renamed `JetStreamStatusInternalException` and stays unchecked and internal. You should not be catching it — the name it vacated now belongs to the user-facing checked type above.

**The one place `IOException` survives — and it is real.** `ObjectStore.put(...)` and `ObjectStore.get(...)` still declare `throws IOException` alongside `JetStreamException`, because they read and write *your* `InputStream` / `OutputStream`. That `IOException` means a stream failure on your side, not a NATS failure — keep the `catch (IOException)` there. It is the only spot on the JetStream surface where `IOException` is not a lie.

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

- **`ReconnectDelayHandler` signature change.** The single abstract method goes from `Duration getWaitTime(long totalTries)` to `long getWaitTimeMillis(long round, Options options, boolean secure, boolean lameDuckTriggered)`. The interface stays single-abstract-method so lambda handlers continue to work. The handler is now stateless — the connection tracks the lame-duck signal itself and passes it in as the fourth argument. A new stateless `DefaultReconnectDelayHandler.INSTANCE` singleton is what `Options.getReconnectDelayHandler()` returns when the user hasn't supplied a custom handler, and it's safe to share across many connections. `ReconnectDelayBehavior` keeps v2's `BeforeSubsequentRounds` default, so failover latency is unchanged. `BeforeAllRounds` additionally delays before the first round, which spreads a reconnect storm when one server drops a large client population at once; the new `LameDuckAware` does that only when the server signalled LDM. The behavior governs whether the handler is invoked before round 1 at all, and applies to custom handlers as well as the default one.

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
