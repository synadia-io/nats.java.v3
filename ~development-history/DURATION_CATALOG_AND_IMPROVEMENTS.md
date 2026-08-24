# Duration Catalog & Improvements

Re-scanned from scratch. A catalog of every duration-valued knob in the v3 API, classified by **where the value goes**, plus the status of migrating client-wait timeouts to milliseconds.

**Governing rule (destination-based, from `OPTIONS_TIMEOUT_IMPROVEMENTS.md`):** the unit is always made visible — in the name or in the type.
- **Serialized to the server as nanoseconds on the wire** → keep **`Duration`** (unit in the type). The wire is nanos only because the Go server reads these from JSON as a `time.Duration` (an integer nanosecond count) — a protocol-encoding artifact, not a precision need.
- **Client-side "how long do I wait" timeouts** (not serialized) → **`long` milliseconds** (plain name, unit in the javadoc — ms is the only client-wait unit, never seconds or nanos). Millisecond resolution is always enough; nobody waits on a nanosecond. **One exception:** `socketWriteTimeout` is `long` **nanoseconds** (it bounds a local socket write, not a network op, so sub-ms matters).

---

## 1. Wire-nanos durations — keep `Duration` (+ `long …Millis` convenience overload)

**Decision: keep both overloads.** The `long` sibling is already millis (`…Millis` param, `Duration.ofMillis(...)` body), so the pair is `xxx(Duration)` + `xxx(long millis)` — both unit-visible. Nanos lives only in the serializer (`addFieldAsNanos`).

| Setter | Where | Serialized |
|---|---|---|
| `ackWait` | `AbstractEphemeralConsumerCreator` | `addFieldAsNanos ACK_WAIT` |
| `idleHeartbeat` | `ConsumerCreator`, `PullRequestOptions` | `addFieldAsNanos IDLE_HEARTBEAT` |
| `maxExpires` | `PullConsumerCreator`, `PullOrderedConsumerCreator` | `addFieldAsNanos MAX_EXPIRES` |
| `inactiveThreshold` | `ConsumerCreator`, `ConsumerLimitsCreator` | `addFieldAsNanos INACTIVE_THRESHOLD` |
| `priorityTimeout` | `PullConsumerCreator`, `PullOrderedConsumerCreator` | `addFieldAsNanos PRIORITY_TIMEOUT` |
| `expiresIn` | `PullRequestOptions` | `addFieldAsNanos EXPIRES` |
| `flowControl` | `AbstractEphemeralConsumerCreator` | sets `idleHeartbeat` (nanos) |
| `backoff` | `AbstractEphemeralConsumerCreator` | `BACKOFF` nanos array |
| `maxAge` | `StreamCreator` | `addFieldAsNanos MAX_AGE` |
| `duplicateWindow` | `StreamCreator` | `addFieldAsNanos DUPLICATE_WINDOW` |
| `subjectDeleteMarkerTtl` | `StreamCreator` | `addFieldAsNanos SUBJECT_DELETE_MARKER_TTL` |
| `nakWithDelay` | `JetStreamMessage`, `NatsMessage` | NAK payload nanos — keeps **both** overloads: `nakWithDelay(Duration)` (`toNanos()`) and `nakWithDelay(long nakDelayMillis)` (`* NANOS_PER_MILLI`). The `Duration` form is a convenience for expressing the delay in seconds/minutes. |
| `ttl` | `KeyValueConfigurationCreator`, `ObjectStoreConfigurationCreator` | KV/OS config |
| `limitMarker` | `KeyValueConfigurationCreator` | KV marker TTL |
| `deleteMarkersThreshold` | `KeyValuePurgeOptions` | KV purge request — **was B, not A:** a client-side purge threshold; its `Duration` overload has now been dropped (see §2c), leaving `deleteMarkersThreshold(long …Millis)` |

Naming hygiene (done): every `long` overload's param is named `…Millis`.

**Server-response durations** (parsed from JSON via `readNanosAsDuration`/`readDurationOrZero`, keep as `Duration` returns): `ConsumerInfo.getPauseRemaining`, `ConsumerPauseResponse.getPauseRemaining`, `StreamSourceInfo.getActive`, `PeerInfo.getActive`.

---

## 2. Client-wait timeouts — collapse to `long` milliseconds (drop `Duration`)

These are "how long do I wait" timeouts; none is serialized. Collapse each to a single `long`-millis form following the pattern the reconnect fields established (§3).

### 2a. Connection `Options` — **DONE**

`connectionTimeout`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout` migrated (main + tests): fields → `long`; getters → plain `getConnectionTimeout()` etc. returning `long`; setters → plain `connectionTimeout(long)` etc., `Duration` overloads dropped; default constants → plain `long`; property loaders → `millisProperty` (a plain millis integer **or** an ISO-8601 duration string like `PT2S`, converted to whole millis — so v2 property files keep working) and all property loaders now route **through the setters** (validation/normalization applies to property-loaded values too). **`socketWriteTimeout` is the one exception — `long` NANOSECONDS** (local socket write): default `DEFAULT_SOCKET_WRITE_TIMEOUT = 60_000_000_000L` (60 s), minimum enabled `MINIMUM_SOCKET_WRITE_TIMEOUT = 100` ns, `<= 0` (or below the minimum) disables; property loaded with `longGtEqZeroProperty` (plain nanos, no ISO-8601). See `MIGRATION_GUIDE_OPTIONS.md` §7.

### 2b. JetStream API request timeouts — **DONE**

`JetStreamOptions.requestTimeout` (`getRequestTimeout()`, `<= 0` = unset → connection timeout), `FeatureOptions.jsRequestTimeout`, `PublishOptions.streamTimeout` (+ `DEFAULT_TIMEOUT`, property via `millisProperty`, validated `validateGtEqZero`), and the internal `JetStreamImpl.timeoutMillis`/`getTimeout()` with `makeRequest*` taking `long timeoutMillis` (wrapping `Duration.ofMillis(...)` at the still-`Duration` connection-request boundary — now removed, since `request` takes millis). `requestTimeout` keeps a `<= 0` sentinel for "not set → use the connection timeout."

**Decision (recorded):** keep v3's behavior — **do not match Go.** `requestTimeout`/`streamTimeout` continue to fall back to the connection timeout (2 s) when unset, via the `<= 0` → fall-back sentinel. We are **not** adopting Go's standalone 5 s default (`defaultAPITimeout`/`defaultRequestWait`) nor its `> 0`-required sentinel. No code change — this records that the current behavior stands.

### 2c. Runtime wait-methods — **DONE**

| Method | Where | Status |
|---|---|---|
| `request` / `requestAsync` | `NatsConnection` | **DONE** → `long timeoutMillis` (`< 0` = use connection default) |
| `flush` | `NatsConnection` | **DONE** → `flush(long timeoutMillis)` (`<= 0` = forever) |
| `nextMessage` | `Subscription`/`NatsSubscription`, `JetStreamSubscription`, `JetStreamPullSubscription`/`JetStreamReaderImpl`, `JetStreamReader`, `IterableMessageConsumer`, `NatsIterableMessageConsumer` | **DONE** → **`@Nullable Long timeoutMillis`**: `null` = poll once (immediate), `<= 0` = wait forever, `> 0` = timed. Same rule down the whole chain (`pop` → `_poll`) and through `WriterMessageQueue.accumulate`. |
| `ackSync` | `Message`/`JetStreamMessage`/`NatsMessage` | **DONE** → `ackSync(long timeoutMillis)` (validated `> 0`; calls `nc.request(replyTo, AckAck.bytes, timeoutMillis)`) |
| `next` | `BaseConsumerContext`, `NatsConsumerContext`, `NatsOrderedConsumerContext` | **DONE** — now `next(@Nullable Long maxWaitMillis)`: `null`, zero or negative → `DEFAULT_EXPIRES_IN_MILLIS` (the original `Duration` behavior, **including `null`**); a positive value must be `>= MIN_EXPIRES_MILLS`. (A reading method, so `Long` not `long` — see note.) |
| `fetch`, `iterate` | `JetStreamPullSubscription` (the `maxWait` arg) | **DONE** — now `@Nullable Long maxWaitMillis` (reading method). `null`/`<= 0` still throws (`> 0` required — no leniency). |
| `pullNoWait`, `pullExpiresIn` | `JetStreamPullSubscription` | **DONE** — `Duration` overload dropped; also removed the unused `durationGtZeroRequired(Duration)` helper |
| `deleteMarkersThreshold` | `KeyValuePurgeOptions` | **DONE** — `Duration` overload dropped |
| `flush` (force-reconnect) | `ForceReconnectOptions` | **DONE** — `flushWait` field → `long` (0 = no flush), `getFlushWait()` → `long`, `Duration` setter dropped |
| `drainTimeout` | `ServiceBuilder`/`Service`/`EndpointContext` | **DONE** — field → `long`, `getDrainTimeout()` → `long`, `Duration` overload + `DEFAULT_DRAIN_TIMEOUT` (Duration) dropped; `EndpointContext.drain` → `long`. (Service's drain calls are plain `long` now that `drain` is `long` — see the `drain` row.) |
| `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT` | `OptionsConstants` | **DONE** — `Duration` constant → `long 50` (millis); `WriterMessageQueue` uses `* NANOS_PER_MILLI` |
| `drain` | `Consumer`/`NatsConnection`/`NatsConsumer`/`Dispatcher` | **DONE** — `drain(long timeoutMillis)` (`<= 0` = forever), mirroring `flush`: `nanoTime` elapsed, final flush = `timeoutMillis - elapsedMillis`. The `Service`/`EndpointContext` `Duration.ofMillis(...)` hand-off wraps are gone too. |

All client-wait timeouts are now `long` millis (or `@Nullable Long` for the reading methods). The **D — decide** calls are resolved: `NatsConnection.RTT()` **keeps `Duration`** (it's a precise local measurement, sub-ms matters), and `MessageManager.configureIdleHeartbeat` is now `long` millis. All that's left is **A — keep** (wire-nanos config, by design). See `DURATION_AUDIT.md`.

---

## 3. The naming / shape pattern — plain names, `long` millis, unit in the javadoc

**The setter and getter keep their plain names** (`connectionTimeout(long)` / `getConnectionTimeout()`); they take / return a `long` of milliseconds, and the **unit is documented in the javadoc**, not baked into the method name. The exceptions are **parameters** and **user-implemented interface returns**, which carry the `…Millis`/`…Nanos` suffix so the unit is visible at the call/implement site (`nextMessage(@Nullable Long timeoutMillis)`, `ackSync(long timeoutMillis)`, `next(@Nullable Long maxWaitMillis)`).

- **Setter / method**: `connectionTimeout(long millis)`, `requestTimeout(long millis)`, `nextMessage(@Nullable Long timeoutMillis)` — the `Duration` overload is dropped.
- **Getter**: `getConnectionTimeout()` returning `long`.
- **Field** is a plain `long`; **default** is a plain `long` constant; **property loader** is `millisProperty` (millis integer or ISO-8601 string → whole millis), routed through the setter.

**Why `long` millis and not `Duration` (for client-waits):** `Duration`'s only real advantages — unit-in-the-type and sub-millisecond precision — are unused for "how long do I wait" (ms resolution is always enough; every such timeout reflects a network request or processing network-delivered data). Meanwhile it costs an object allocation plus null-handling on every call, and the codebase is full of `Duration`→millis conversions immediately thrown away. So client-waits are plain `long` millis, with the unit in the javadoc.

**Reading methods take `@Nullable Long`, not `long`.** Any method that reads/consumes a message — `nextMessage`, `next`, `fetch`, `iterate` — uses `@Nullable Long timeoutMillis` so `null` can carry the method's "special" meaning that the old `Duration` API expressed with a `null` Duration: `nextMessage(null)` = poll once / immediate; `next(null)` = use the default expiry; `fetch(null)`/`iterate(null)` = (no default — `null` is invalid, throws, matching the old required-positive `Duration`). The boxing is acceptable here — it's not a precision/fast-path concern, just allocation-tidiness — and it's the only way to keep the original `null` semantics without `Duration`. Non-reading timeouts (`request`, `flush`, `ackSync`, `drain`, the pull-init primitives, config thresholds) have no `null` meaning to preserve, so they stay plain `long`.

**The sentinel conventions** (one rule per family): timeouts that can wait forever use `<= 0` = forever (`flush`, `nextMessage`/`_poll`, JetStream `nextMessage`); `request` uses `< 0` = "use the connection default"; `nextMessage` additionally uses `null` = poll-once. These are documented at each method.

---

## 4. Behavior changes to document (migration)

- **Timing-property values** accept a millis integer **or** an ISO-8601 duration string (converted to whole millis), via `millisProperty` — so v2 property files need no change → `MIGRATION_GUIDE_OPTIONS.md` §7. All property loaders now route through the builder setters.
- **`socketWriteTimeout`**: `<= 0` disables; the one option in **nanoseconds** (local socket write, not network), minimum enabled 100 ns, default 60_000_000_000 ns (60 s).
- **`nextMessage(Long)`**: the old "negative returns immediately" is now `null` = return immediately and `<= 0` = wait forever — aligning the forever rule with `flush` and JetStream `nextMessage`. Callers that passed a negative for poll-once must pass `null`.
- **`ackSync(long timeoutMillis)`**: timeout is now a millis `long`, validated `> 0` (was a required positive `Duration`). (`nakWithDelay` keeps both a `Duration` and a `long nakDelayMillis` overload — the `Duration` one is a convenience for seconds/minutes.)

## 5. Cross-references

- `DURATION_AUDIT.md` — the file/line chart of every remaining `Duration` (A keep / B remove / C helper / D decide) + suggested order.
- `MIGRATION_GUIDE_OPTIONS.md` — user-facing Options migration (incl. §7 timing-millis + property-format change).
- `OPTIONS_TIMEOUT_IMPROVEMENTS.md` — the governing unit rule and related Options cleanups.
- `POLL_NANOS_BEHAVIOR_CHANGES.md` — the `nextMessage`/`_poll`/`accumulate` millis-and-`Long` resolution.
- `RequestBehaviorImprovement.md` — the request-result redesign context.
