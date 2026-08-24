# Duration audit — where `java.time.Duration` still lives in main source

Re-scanned from scratch. Goal: chart every meaningful `Duration` usage so we can decide what to drop. Categorized by what the Duration *means*:

- **A — KEEP (wire / server duration):** serialized to the server as JSON nanoseconds (Go `time.Duration`) or parsed back from a server response. Per the established rule (Group A) these stay `Duration` — each setter already has a `long…Millis` sibling. Listed to confirm, not to remove.
- **B — REMOVE → `long …Millis`:** a timeout the **client blocks on locally** (drain / next / fetch / iterate / pull*-expire / flush-wait / drain-timeout). This is the family we've been migrating. **These are the actionable targets.**
- **C — helper/validator/internal converter:** follows A/B — simplifies or disappears when its callers change.
- **D — decide:** genuinely ambiguous; one human call each.

"Sibling" = a `long`-millis overload already exists, so the Duration form is a drop-in removal. "Cascade" = no `long` version yet; one must be created first (interface + impls + callers).

---

## 0 — Already migrated (no `Duration` form remains)

For reference, so the remaining list reads clean. These were the bulk of the client-wait work:

- **Connection `Options` timeouts** — `connectionTimeout`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout` → `long` millis; **`socketWriteTimeout` → `long` NANOSECONDS** (the one exception — bounds a local socket write). Property loaders route through the setters.
- **JetStream request timeouts** — `JetStreamOptions.requestTimeout`, `FeatureOptions.jsRequestTimeout`, `PublishOptions.streamTimeout`, internal `JetStreamImpl` plumbing → `long` millis.
- **`NatsConnection.request` / `requestAsync`** → `long timeoutMillis` (Duration overloads removed; `timeoutToMillis` deleted).
- **`NatsConnection.flush`** → `flush(long timeoutMillis)` (`<= 0` = wait forever; `waitWhile`/`waitForConnectOrClose` operate in nanos internally).
- **Incoming-message reader chain** — `Subscription.nextMessage`, `nextMessageInternal`, `ConsumerMessageQueue.pop`, `MessageQueueBase._poll`, `WriterMessageQueue.accumulate`, `JetStreamSubscription.nextMessage`, and the JetStream reader interfaces (`JetStreamReader`, `IterableMessageConsumer`, `NatsIterableMessageConsumer`, `JetStreamReaderImpl`) → **`@Nullable Long timeoutMillis`** with one rule everywhere: `null` = poll once (immediate), `<= 0` = wait forever, `> 0` = timed.
- **`Message.ackSync(Duration)` → `ackSync(long timeoutMillis)`** (interface + `NatsMessage` no-op + `JetStreamMessage`; validation moved from `validateDurationRequired` to `validateGtZero`; calls `nc.request(replyTo, AckAck.bytes, timeoutMillis)`).
- **`Message.nakWithDelay`** keeps **both** overloads (Group A wire-nanos): `nakWithDelay(Duration)` (`toNanos()`) and `nakWithDelay(long nakDelayMillis)` (`* NANOS_PER_MILLI`). The `Duration` form is intentionally kept as a convenience for expressing the delay in seconds/minutes. (Its neighbor `ackSync` went `long` millis — see above — but the nak *delay* is a wire value, not a client wait.)
- **B1 trivial Duration-overload drops — DONE** — `pullNoWait(int,Duration)`, `pullExpiresIn(int,Duration)` (`JetStreamPullSubscription`) and `deleteMarkersThreshold(Duration)` (`KeyValuePurgeOptions`) were dropped outright (their `long` siblings remain). The three **reading** methods — `next` (`BaseConsumerContext`/`NatsConsumerContext`/`NatsOrderedConsumerContext`) and `fetch`/`iterate` (`JetStreamPullSubscription`) — had their `Duration` overload replaced by a single **`@Nullable Long`** form (reading methods take `Long` so `null` preserves the old `Duration`-null meaning): `next(@Nullable Long)` → `null`/zero/negative = `DEFAULT_EXPIRES_IN_MILLIS`, positive must be `>= MIN_EXPIRES_MILLS`; `fetch`/`iterate(@Nullable Long)` → `null`/`<= 0` still throws (`> 0` required). The three context files' now-unused `Duration` imports were removed; the example caller (`Z01`) and the `SimplificationTests`/`JetStreamManagementTests` `next(...)` callers were updated (`next(1000L)`, `next(null)`, …).

---

## B — removal targets (client-wait timeouts → `long …Millis`)

### B1. Trivial drops — ✅ DONE (see §0). Kept here for the record.

| File | Symbol (Duration) | Sibling (`long`) | Note |
|---|---|---|---|
| `impl/JetStreamPullSubscription.java` | `fetch(int, Duration maxWait)` L162 | `fetch(int, long maxWaitMillis)` L146 | `_fetch` already millis |
| `impl/JetStreamPullSubscription.java` | `iterate(int, Duration maxWait)` L270 | `iterate(int, long maxWaitMillis)` L287 | `_iterate` already millis |
| `impl/JetStreamPullSubscription.java` | `pullNoWait(int, Duration expiresIn)` L77 | `pullNoWait(int, long expiresInMillis)` L91 | drop Duration overload |
| `impl/JetStreamPullSubscription.java` | `pullExpiresIn(int, Duration expiresIn)` L110 | `pullExpiresIn(int, long expiresInMillis)` L130 | drop Duration overload |
| `impl/NatsConsumerContext.java` | `next(Duration maxWait)` L163 | `next(long maxWaitMillis)` L174 | delegates to millis |
| `impl/NatsOrderedConsumerContext.java` | `next(Duration maxWait)` L57 | `next(long maxWaitMillis)` L66 | delegates to millis |
| `impl/BaseConsumerContext.java` | `next(Duration maxWait)` (interface) | `next(long maxWaitMillis)` L67 | drop interface method |
| `kv/KeyValuePurgeOptions.java` | `deleteMarkersThreshold(Duration)` L60 | `deleteMarkersThreshold(long …Millis)` L73 | already millis-backed internally |

### B2. Small field/constant flips — ✅ DONE

| File | What was done |
|---|---|
| `client/ForceReconnectOptions.java` | field + builder field → `long flushWait` (0 = no flush); `getFlushWait()` → `long` (millis); `isFlush()` → `flushWait > 0`; the `flush(Duration)` overload dropped, `flush(long)` keeps it (`<= 0` → 0/no flush). `Duration` import removed. Caller `NatsConnection.forceReconnectImpl` now uses `isFlush()` + `flush(getFlushWait())`. |
| `client/OptionsConstants.java` | `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT` → `long` `50` (millis); `Duration` import removed. `WriterMessageQueue.MIN_PUSH_TIMEOUT_NANOS` now `… * NANOS_PER_MILLI`. |
| `service/ServiceBuilder.java` | field → `long drainTimeout = DEFAULT_DRAIN_TIMEOUT_MILLIS`; the `DEFAULT_DRAIN_TIMEOUT` (Duration) constant and the `drainTimeout(Duration)` overload dropped; `drainTimeout(long)` re-defaults on `<= 0`. `Duration` import removed. |
| `service/Service.java` | field → `long drainTimeout`; `getDrainTimeout()` → `long`; `drainTimeout.toMillis()` → `drainTimeout`. The `Dispatcher.drain` call wraps `Duration.ofMillis(drainTimeout)` at the boundary (since `Dispatcher.drain` is still `Duration` — B3). |
| `service/EndpointContext.java` | internal `drain(Duration)` → `drain(long timeoutMillis)` → `dispatcher.drain(Duration.ofMillis(timeoutMillis))`. |

Tests updated: `ReconnectTests.testForceReconnectOptionsBuilder` (flush now `long`), `ServiceTests` (drainTimeout now `long`). The remaining `Duration.ofMillis(...)` wraps in `Service`/`EndpointContext` are the **single hand-off to the still-`Duration` `Dispatcher.drain`** — they disappear when the `drain` cascade (B3) is done.

### B3. The `drain` cascade — ✅ DONE (mirrored the `flush` migration)

`drain(Duration)` → **`drain(long timeoutMillis)`** everywhere (`<= 0` = wait forever, like `flush`):

| File | What was done |
|---|---|
| `client/Consumer.java` | interface `drain(Duration)` → `drain(long timeoutMillis)`; `Duration` import removed (also fixed a stale `{@link Subscription#nextMessage(java.time.Duration)}` → `nextMessage(Long)`). |
| `impl/NatsConsumer.java` | `drain(long timeoutMillis)`: `connection.flush(timeoutMillis)`; wait-loop `timeoutNanos = timeoutMillis <= 0 ? Long.MAX_VALUE : timeoutMillis * NANOS_PER_MILLI`. Swapped `Duration` import for `NANOS_PER_MILLI`. |
| `impl/NatsConnection.java` | `drain(long timeoutMillis)`: overall start via `NatsSystemClock.nanoTime()`; initial `flush(timeoutMillis)`; wait-loop nanos as above; **final flush** = `remainingMillis = timeoutMillis - (nanoTime - startNanos)/NANOS_PER_MILLI` (was `Instant`/`Duration.minus`). `Instant` import removed; `{@link #drain(long)}` link fixed. |
| `service/Service.java` + `service/EndpointContext.java` | the B2 `Duration.ofMillis(drainTimeout)` / `Duration.ofMillis(timeoutMillis)` hand-off wraps are gone — `d.drain(drainTimeout)` and `dispatcher.drain(timeoutMillis)` are now plain `long`. Both `Duration` imports removed. |

Callers updated: `DrainTests` (all `Duration.ofSeconds(n)`→`n*1000`, `Duration.ZERO`/`null`→`0`, the one `testTimeout` Duration var → `.toMillis()`). `DrainTests` passes. **All of B is now done.**

---

## A — keep as `Duration` (wire-nanos config + server-returned)

Each is serialized to the server via `addFieldAsNanos`/backoff-nanos, or parsed from a response. Every Duration **setter already has a `long…Millis` sibling**, so the dual API is in place. Per the Group A decision these stay `Duration`.

**Consumer config** (`api/ConsumerCreator.java`, `api/ConsumerConfiguration.java`, `api/ConsumerLimits*.java`, `api/PullConsumerCreator.java`, `api/PullOrderedConsumerCreator.java`, `api/AbstractEphemeralConsumerCreator.java`, `api/AbstractOrderedConsumerCreator.java`): `ackWait`, `idleHeartbeat` (+ `flowControl`), `maxExpires`, `inactiveThreshold`, `backoff` (`List<Duration>`), `priorityTimeout` — fields, getters, Duration setter overloads.

**Stream config** (`api/StreamCreator.java`, `api/StreamConfiguration.java`): `maxAge`, `duplicateWindow`, `subjectDeleteMarkerTtl`.

**KV / OS config** (`kv/KeyValueConfigurationCreator.java`, `kv/KeyValueConfiguration.java`, `kv/KeyValueStatus.java`, `os/ObjectStoreConfigurationCreator.java`, `os/ObjectStoreConfiguration.java`, `os/ObjectStoreStatus.java`): `ttl`, `limitMarker`/`limitMarkerTtl`. **Note:** these cross the KV/OS ↔ core boundary via `StreamCreator`/`StreamConfiguration` — relevant to the planned KV/OS split (keep the dependency one-way).

**Pull request wire fields** (`impl/PullRequestOptions.java`): `expiresIn`, `idleHeartbeat` — fields, getters, Duration + long setters, `addFieldAsNanos` serialize. Also the internal wire `expiresIn` builds inside `JetStreamPullSubscription._fetch`/`_iterate` (built from the client millis maxWait — the A side of the B1 methods; these stay).

**Server-response durations** (parsed from JSON, keep): `api/ConsumerInfo.getPauseRemaining`, `api/ConsumerPauseResponse.getPauseRemaining`, `api/StreamSourceInfo.getActive`, `api/PeerInfo.getActive`, `utils/ApiUtils.readDurationOrZero`.

**Wire ack delay** (`Message.nakWithDelay(Duration)` + `nakWithDelay(long)` + `JetStreamMessage`/`NatsMessage`): both overloads serialize the delay to the server as nanos. Keep both (the `Duration` form is a seconds/minutes convenience). (Its neighbor `ackSync` is now `long` millis — see §0.)

---

## C — helpers / validators / converters (follow A/B)

| File | Symbol | Serves | Fate |
|---|---|---|---|
| `utils/Validator.java` | **`validateDurationRequired`** | was ackSync only | **now orphaned in main source** (only `ValidatorTests` calls it) — removable, since `ackSync` moved to `validateGtZero` |
| `utils/Validator.java` | `ensureNotNullAndNotLessThanMin`, `ensureDurationNotLessThanMin(long,…)` | config min-floor helpers (A) | keep while A keeps Duration |
| `utils/ApiUtils.java` | `DURATION_UNSET`, `normalizeDuration(Duration,…)`, `normalizeDuration(Long,…)`, `readDurationOrZero` | config-Duration builders (A) + server-response | keep with A |
| `utils/JsValidator.java` | `validateDurationNotRequiredGtOrEqZero` ×3, `validateDurationNotRequiredGtOrEqSeconds`, `validateDurationGtOrEqSeconds` | A (config/wire durations) | keep with A |
| `impl/JetStreamPullSubscription.java` | `durationGtZeroRequired(Duration)` / `(long)` | Fetch/Iterate (B1) + expiresIn (A) | the Duration overload is removable once Fetch/Iterate go long |
| `impl/PullRequestOptions.java` | `idleNanosTemp`/`expiresNanos` validation locals | wire expiresIn/idleHeartbeat (A) | keep |
| `OptionsProperties.java` | `Duration.parse(value).toMillis()` | ISO-8601 property parse | internal — stays (parses text → long millis) |

---

## D — decided

| File | Symbol | Decision |
|---|---|---|
| `impl/NatsConnection.java` | `Duration RTT()` L1744 (`Duration.ofNanos(nanoTime - t)`) | **KEEP `Duration`.** It returns a precise local round-trip measurement; `Duration` preserves the sub-millisecond precision that a `long` millis would throw away. |
| `impl/MessageManager.java` | `configureIdleHeartbeat(…, long)` L100 | **✅ DONE → `configureIdleHeartbeat(long configIdleHeartbeatMillis, long configMessageAlarmTime)`.** Internal-only and it already did its work in millis. The two callers (`PullMessageManager`, `PushMessageManager`) convert the config `Duration idleHeartbeat` inline (`== null ? 0 : .toMillis()`); `Duration` import removed from `MessageManager`. |

---

## Import-only cleanups (bare unused `import java.time.Duration;`)

- `core/src/main/java/io/synadia/client/impl/SocketDataPort.java:20` — `connect(...)` already takes `long timeoutNanos`; import unused.
- `service/src/main/java/io/synadia/service/Discovery.java:8` — already migrated to `maxTimeMillis`; import unused.

(`OptionsConstants.java:6` joins this list once `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT` becomes `long` — see B2.)

---

## Suggested order

1. **Trivial drops (B1)** — ✅ DONE. (Still pending from that cleanup batch: the two unused `Duration` imports in §"Import-only cleanups" and the now-orphaned `validateDurationRequired` — both are independent of B1 and not yet removed.)
2. **Small flips (B2)** — ✅ DONE (`ForceReconnectOptions.flushWait`, `OptionsConstants.MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT`, `ServiceBuilder`/`Service`/`EndpointContext` `drainTimeout`).
3. **The `drain` cascade (B3)** — ✅ DONE (`Consumer`/`NatsConnection`/`NatsConsumer` → `drain(long)`, `Service`/`EndpointContext` wraps removed, `DrainTests` updated).
4. **Decide D:** `RTT()` and `MessageManager.configureIdleHeartbeat`.
5. **Leave A** as-is (Group A wire-nanos), unless you ever want to strip the Duration *setters* from the config/creator classes (getters/returns stay).
