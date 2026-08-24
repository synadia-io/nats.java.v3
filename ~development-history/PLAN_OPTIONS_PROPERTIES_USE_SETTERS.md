# Plan: route `OptionsBuilder.properties(...)` through the builder setters

## Problem
`OptionsBuilder.properties(Properties)` (core/.../OptionsBuilder.java:172) loads each property by **directly assigning the builder field** in a lambda, e.g.:
```java
millisProperty(props, PROP_CONNECTION_TIMEOUT, l -> this.connectionTimeout = l);
```
This **bypasses the setter method**, which is where validation/normalization lives. A user can put an invalid value in a `.properties` file and it lands in the field unchecked. The setter (`connectionTimeout(long)`) normalizes `<= 0` to the default — the property path does not.

## Goal
Route each property through its builder setter (usually a method reference) so the **setter is the single point of validation/normalization**. Where a property currently does manual work the setter already does (e.g. wrapping a token), the setter call also de-duplicates that logic.

## Implementation note (types)
The property helpers pass boxed values (`Consumer<Long>`, `Consumer<Integer>`, `Consumer<String>`, `Consumer<Boolean>`); the setters take primitives. Method refs like `this::connectionTimeout` satisfy `Consumer<Long>` via auto-unboxing — this compiles. So most conversions are `l -> this.x = l` → `this::x`.

---

## Category A — clean conversions (setter exists, just call it)

These have a value-taking setter; switch the lambda to a method ref. **Bold = setter actually normalizes today (real bug being fixed); the rest are plain assignments today (consistency + future-proofing — once the setter is the single path, adding validation later automatically covers properties).**

| Property | Current (direct) | Change to | Setter normalizes today? |
|---|---|---|---|
| `PROP_CONNECTION_TIMEOUT` | `l -> this.connectionTimeout = l` | `this::connectionTimeout` | **yes (`<=0` → default)** |
| `PROP_MAX_CONTROL_LINE` | `i -> this.maxControlLine = i` | `this::maxControlLine` | **yes (`<0` → default)** |
| `PROP_DATA_PORT_TYPE` | `s -> this.dataPortType = s` | `this::dataPortType` | **yes (null → default)** |
| `PROP_MAX_MESSAGES_IN_OUTGOING_QUEUE` | `i -> this.maxMessagesInOutgoingQueue = i` | `this::maxMessagesInOutgoingQueue` | **yes (`<0` → default)** |
| `PROP_TLS_ALGORITHM` | `s -> this.tlsAlgorithm = s` | `this::tlsAlgorithm` | **yes (empty/null → default)** |
| `PROP_TOKEN` | `ca -> this.tokenSupplier = new Options.DefaultTokenSupplier(ca)` | `this::token` | **yes (wraps in supplier — dedups)** |
| `PROP_RECONNECT_WAIT` | `l -> this.reconnectWait = l` | `this::reconnectWait` | no |
| `PROP_RECONNECT_JITTER` | `l -> this.reconnectJitter = l` | `this::reconnectJitter` | no |
| `PROP_RECONNECT_JITTER_TLS` | `l -> this.reconnectJitterTls = l` | `this::reconnectJitterTls` | no |
| `PROP_RECONNECT_BUF_SIZE` | `l -> this.reconnectBufferSize = l` | `this::reconnectBufferSize` | no |
| `PROP_SOCKET_READ_TIMEOUT` | `i -> this.socketReadTimeout = i` | `this::socketReadTimeout` | no (build() normalizes) |
| `PROP_SOCKET_WRITE_TIMEOUT` | `l -> this.socketWriteTimeout = l` | `this::socketWriteTimeout` | no (build() normalizes) |
| `PROP_SOCKET_SO_LINGER` | `i -> socketSoLinger = i` | `this::socketSoLinger` | no |
| `PROP_SOCKET_RECEIVE_BUFFER_SIZE` | `i -> this.receiveBufferSize = i` | `this::receiveBufferSize` | no |
| `PROP_SOCKET_SEND_BUFFER_SIZE` | `i -> this.sendBufferSize = i` | `this::sendBufferSize` | no |
| `PROP_PING_INTERVAL` | `l -> this.pingInterval = l` | `this::pingInterval` | no |
| `PROP_CLEANUP_INTERVAL` | `l -> this.requestCleanupInterval = l` | `this::requestCleanupInterval` | no |
| `PROP_WRITE_QUEUE_PUSH_TIMEOUT` | `l -> this.writeQueuePushTimeout = l` | `this::writeQueuePushTimeout` | no |
| `PROP_MAX_PINGS` | `i -> this.maxPingsOut = i` | `this::maxPingsOut` | no |
| `PROP_MAX_RECONNECT` | `i -> this.maxReconnect = i` | `this::maxReconnects` | no (note: setter is plural `maxReconnects`) |
| `PROP_CONNECTION_NAME` | `s -> this.connectionName = s` | `this::connectionName` | no |
| `PROP_CREDENTIAL_PATH` | `s -> this.credentialPath = s` | `this::credentialPath` | no |
| `PROP_KEY_STORE` | `s -> this.keystore = s` | `this::keystorePath` | no (field `keystore`, setter `keystorePath`) |
| `PROP_KEY_STORE_PASSWORD` | `ca -> this.keystorePassword = ca` | `this::keystorePassword` | no |
| `PROP_TRUST_STORE` | `s -> this.truststore = s` | `this::truststorePath` | no |
| `PROP_TRUST_STORE_PASSWORD` | `ca -> this.truststorePassword = ca` | `this::truststorePassword` | no |
| `PROP_TOKEN_SUPPLIER_CLASS` | `o -> this.tokenSupplier = (Supplier<char[]>) o` | `o -> tokenSupplier((Supplier<char[]>) o)` | no (cast still needed) |
| `PROP_SSL_CONTEXT_FACTORY_CLASS` | `o -> this.sslContextFactory = (SSLContextFactory) o` | `o -> sslContextFactory((SSLContextFactory) o)` | no |
| `PROP_RECONNECT_DELAY_HANDLER_CLASS` | `o -> this.reconnectDelayHandler = (...) o` | `o -> reconnectDelayHandler((ReconnectDelayHandler) o)` | no |
| `PROP_CONNECTION_LISTENER_CLASS` | `o -> this.connectionListener = (...) o` | `o -> connectionListener((ConnectionListener) o)` | no |
| `PROP_ERROR_LISTENER_CLASS` | … | `o -> errorListener((ErrorListener) o)` | no |
| `PROP_READ_LISTENER_CLASS` | … | `o -> readListener((ReadListener) o)` | no |
| `PROP_STATISTICS_COLLECTOR_CLASS` | … | `o -> statisticsCollector((StatisticsCollector) o)` | no |
| `PROP_SERVERS_POOL_IMPLEMENTATION_CLASS` | … | `o -> serverPool((ServerPool) o)` | no |
| `PROP_DISPATCHER_FACTORY_CLASS` | … | `o -> dispatcherFactory((DispatcherFactory) o)` | no |
| executor / thread-factory class props | direct casts | route through `executor(...)`/`connectExecutor(...)`/`callbackExecutor(...)`/`scheduledExecutor(...)`/`connectThreadFactory(...)`/`callbackThreadFactory(...)` | no |
| `PROP_CLIENT_SIDE_LIMIT_CHECKS` | `b -> this.clientSideLimitChecks = b` | `this::clientSideLimitChecks` | no — **but a `(boolean)` setter already exists** |

Already correct (no change): `PROP_URL` → `this::server`, `PROP_SERVERS` → `servers(...)`, `PROP_INBOX_PREFIX` → `this::inboxPrefix`.

---

## Category B — boolean toggles: give every one a `foo()` / `foo(boolean)` pair

**Rule (decided):** every boolean toggle setter has a **pair** — a no-arg `foo()` that sets `foo` to `true`, and a `foo(boolean b)` that sets `foo` to `b`. The no-arg form keeps the convenient fluent style; the boolean form is what the property loader (and any caller needing `false`) uses. Add the missing half of each pair, then route the property through `foo(boolean)`.

Most of these toggles today have only the no-arg form, so **add the `(boolean)` overload** and route the property to it:

| Field | Today | Add | Property routes to |
|---|---|---|---|
| `noRandomize` | `noRandomize()` | `noRandomize(boolean)` | `this::noRandomize` |
| `noEcho` | `noEcho()` | `noEcho(boolean)` | `this::noEcho` |
| `supportUTF8Subjects` | `supportUTF8Subjects()` | `supportUTF8Subjects(boolean)` | `this::supportUTF8Subjects` |
| `verbose` | `verbose()` | `verbose(boolean)` | `this::verbose` |
| `pedantic` | `pedantic()` | `pedantic(boolean)` | `this::pedantic` |
| `ignoreDiscoveredServers` | `ignoreDiscoveredServers()` | `ignoreDiscoveredServers(boolean)` | `this::ignoreDiscoveredServers` |
| `tlsFirst` | `tlsFirst()` | `tlsFirst(boolean)` | `this::tlsFirst` |
| `useTimeoutException` | `useTimeoutException()` | `useTimeoutException(boolean)` | `this::useTimeoutException` |
| `useDispatcherWithExecutor` | `useDispatcherWithExecutor()` | `useDispatcherWithExecutor(boolean)` | `this::useDispatcherWithExecutor` |
| `discardMessagesWhenOutgoingQueueFull` | `discardMessagesWhenOutgoingQueueFull()` | `discardMessagesWhenOutgoingQueueFull(boolean)` | `this::discardMessagesWhenOutgoingQueueFull` |
| `clientSideLimitChecks` | `clientSideLimitChecks(boolean)` (boolean form only) | **add no-arg** `clientSideLimitChecks()` | already `this::clientSideLimitChecks` |

**Awkward-naming cases:**
- `trackAdvancedStats` — **DECIDED: rename to match the field.** Replace `turnOnAdvancedStats()` with the pair `trackAdvancedStats()` (sets `true`) / `trackAdvancedStats(boolean)`. (No property loads this today, so no property routing needed — this is just the naming/pair cleanup.)
- `forceFlushOnRequest` — **DECIDED: change the default to `false` and drop the inverted convenience.** The `dontForceFlushOnRequest()` method only existed because the field defaulted to `true`; with the default flipped to `false` it's no longer needed.
  - Change field default `OptionsBuilder.java:98` `boolean forceFlushOnRequest = true;` → `false` (and remove the `// true since it's the original b/w compatible way` comment — this is a deliberate divergence from v2's force-flush-by-default behavior).
  - **Remove** `dontForceFlushOnRequest()` (`OptionsBuilder.java:1122`). Verified: **no callers anywhere** (main or test), so removal is safe.
  - **Add** the standard pair `forceFlushOnRequest()` (sets `true`) / `forceFlushOnRequest(boolean)`.
  - Route `PROP_FORCE_FLUSH_ON_REQUEST` → `this::forceFlushOnRequest`.
  - **Behavior change:** by default, `request(...)`/`requestAsync(...)` no longer force an immediate flush (used at `NatsConnection` request paths + `JetStreamPullSubscription`). Intentional.
  - **Test impact:** `OptionsTests._testPropertiesCoverageOptions` (line 594) asserts `forceFlushOnRequest()` is `false` via a property — with the new default also `false`, flip that coverage property value to `true` (non-default) so it still proves the loader works; update the assertion to `assertTrue`. Check the `options_coverage_*.properties` resource files for the `forceFlushOnRequest` value too.
- `secure` / `opentls` (TLS) — see Category C.

---

## Category C — no individual setter exists

- **`PROP_USERNAME` / `PROP_PASSWORD`** — only `userInfo(char[], char[])` exists (the validated pair); there are no standalone `username`/`password` setters, and they're set independently from properties. Options: keep direct, **or** add private/standalone setters. Note the token-vs-username conflict is checked in `build()`, not the setter, so direct assignment doesn't skip that check. **Recommend keep direct.**
- **`PROP_SECURE` (`useDefaultTls`) / `PROP_OPEN_TLS` (`useTrustAllTls`)** — the setters `secure()` / `opentls()` are no-arg, set `true` only, and **throw `NoSuchAlgorithmException`**. **DECIDED:** keep the `throws`, add `secure(boolean)` / `opentls(boolean)` (also `throws NoSuchAlgorithmException`), and route `PROP_SECURE`/`PROP_OPEN_TLS` through them — **the property path must NOT swallow `NoSuchAlgorithmException`; it must propagate.**
  - Constraint this creates: `booleanProperty(...)` takes a `Consumer<Boolean>`, which **cannot throw a checked exception**, so `secure(boolean)` can't be routed through the generic `booleanProperty` lambda as-is.
  - **DECIDED resolution:** add our own functional interface that permits throwing — e.g.
    ```java
    @FunctionalInterface
    interface ThrowingBooleanConsumer { void accept(boolean b) throws NoSuchAlgorithmException; }
    ```
    (or a generic `ThrowingConsumer<T, E extends Exception>`), plus a throwing variant of the property helper:
    ```java
    static void booleanProperty(Properties props, String key, ThrowingBooleanConsumer consumer) throws NoSuchAlgorithmException { ... }
    ```
    Then `properties(...)` routes `PROP_SECURE` → `this::secure` and `PROP_OPEN_TLS` → `this::opentls`, and **declares `throws NoSuchAlgorithmException`** so it propagates up through the `OptionsBuilder(Properties)` / `OptionsBuilder(String path)` constructors (deliberate, correct API change — a bad/secure-impossible TLS property fails fast).
  - **Do NOT** wrap-and-swallow (no empty `catch`, no rethrow as a silent default). The whole point is that an invalid TLS setup surfaces to the user.

---

## Category D — enum / custom-logic properties

- `PROP_SUBJECT_VALIDATION_TYPE`: currently `s -> this.subjectValidationType = SubjectValidationType.get(s)`. Setter `subjectValidationType(...)` defaults null → `Lenient`. Change to `s -> subjectValidationType(SubjectValidationType.get(s))` so an unparseable value (→ null) is normalized to `Lenient` by the setter instead of assigning null.
- `PROP_RECONNECT_DELAY_BEHAVIOR`: `s -> this.reconnectDelayBehavior = ReconnectDelayBehavior.get(s)` → `s -> reconnectDelayBehavior(ReconnectDelayBehavior.get(s))` (verify the setter's null handling first).
- `PROP_HOSTNAME_RESOLVE_MODE`: already has custom "set only if non-null" logic. **Check** whether `hostnameResolveMode(mode)` setter handles null the same way; if so, simplify to route through it, else leave the custom block.

---

## Behavior-change warning (important)
Routing through setters changes behavior **for invalid property values** — which is the point, but must be verified:
- A setter that **normalizes** will now silently fix invalid property values (e.g. `connectionTimeout=0` → default 2000) instead of storing the invalid value. Good, but a test asserting the old (invalid) stored value would change.
- A setter that **throws/validates** (none of the current numeric setters throw, but future ones might) would now reject a bad property at build time. Intended, but ensure that's the desired UX (fail-fast vs. silent-default).
- **Action:** re-run `OptionsTests` (esp. `testDurationProperties`, `testPropertyIntOptions`, the coverage/property tests) after the change; adjust assertions where the normalized result now differs from the old direct-assigned value.

## Related (optional follow-up)
Many Category-A setters **do not validate today** (plain assignment). Once properties route through them, they become the single choke point — consider adding the intended validation/normalization to those setters (e.g. should `pingInterval`/`socketWriteTimeout` clamp negatives, like `connectionTimeout` does?). That's a separate decision per field; this plan only changes the *routing*, not the setter bodies.

## Verification
- `./gradlew :core:compileJava :core:compileTestJava`
- `./gradlew :core:test --tests "io.synadia.client.OptionsTests"`

## Status
- [ ] Category A method-ref conversions
- [ ] Category B — add `foo()` / `foo(boolean)` pairs to all boolean toggles; route properties through `foo(boolean)`
- [x] `trackAdvancedStats` — **decided:** rename `turnOnAdvancedStats()` → `trackAdvancedStats()` / `trackAdvancedStats(boolean)` (naming/pair cleanup; no property routing)
- [x] `forceFlushOnRequest` — **decided:** default → `false`; remove `dontForceFlushOnRequest()` (no callers); add `forceFlushOnRequest()` / `forceFlushOnRequest(boolean)`; route `PROP_FORCE_FLUSH_ON_REQUEST`; flip the coverage test to `assertTrue` + non-default property value
- [ ] Category C — username/password (keep direct); add a throwing functional interface + throwing `booleanProperty` variant; `secure(boolean)`/`opentls(boolean)` added (keep `throws`) and routed via `this::secure`/`this::opentls` so `properties(...)` **declares + propagates `NoSuchAlgorithmException`** (no swallowing)
- [ ] Category D enum routing + hostnameResolveMode check
- [ ] OptionsTests green (adjust assertions for normalized values)
