# Options & Timeout Improvement Candidates

Survey of the v2 codebase (`/mnt/c/nats/nats.java`, `io.nats.client.Options`/`NatsConnection`) for deprecated options, conflicting/conflated options, and timeout-handling warts — and which of them v3 (`io.synadia.client`) still carries and could improve. Companion to `RequestBehaviorImprovement.md` (which already owns `useTimeoutException` + advanced request behavior).

## Already clean in v3 — no action (v3 is ahead of v2 here)

These v2 legacy/compat options are **already gone** from v3; don't re-introduce them:
- `useOldRequestStyle` / `setOldRequestStyle()` / `PROP_USE_OLD_REQUEST_STYLE` — the shared-wildcard-inbox legacy request style. v3 only does per-request inboxes.
- `noNoResponders` / `reportNoResponders` (+ their props) — v3 always advertises `no_responders` (`Options.OPTION_NORESPONDERS = "no_responders"`) and surfaces them; the off-switches are gone. (Any remaining no-responders/no-headers tidy-up is tracked in `PLAN_REMOVE_NOHEADERS_NORESPONDERS.md`.)
- Deprecated subject-validation builder methods (`noSubjectValidation()` / `strictSubjectValidation()`) — v3 kept only the `SubjectValidationType` enum setter (and `PR 1578` deprecated the two legacy *property* constants).

## Candidates (ranked)

### 1. `requestCleanupInterval` does double duty — split it (HIGH) → moved to its own doc

This one is a design decision with real semantic weight (and it's entangled with `RequestBehaviorImprovement.md`'s default-timeout source), so it now lives in **`PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md`**. Not done here.

### 2. Mixed timeout units across the timing options — **DONE**

**Status:** all client-wait timeout options migrated to `…Millis` longs and the `Duration` overloads dropped — connection `Options` (`connectionTimeout`, `socketWriteTimeout`, `pingInterval`, `requestCleanupInterval`, `writeQueuePushTimeout`) and the JetStream request timeouts (`JetStreamOptions.requestTimeout`, `FeatureOptions.jsRequestTimeout`, `PublishOptions.streamTimeout`, and the internal `JetStreamImpl.timeoutMillis`/`getTimeout()`/`makeRequest*`). Naming/shape per `DURATION_CATALOG_AND_IMPROVEMENTS.md` §3. The reference analysis below is retained. (Runtime wait-methods — `drain`/`flush`/`next`/`nextMessage`/`ackSync` — remain, tracked in that catalog's §2c.)

**Governing rule (destination-based, not type-based):** the same physical quantity (a duration) is represented by *where the value goes*, and the unit is always made visible — in the name or in the type:
- **Client-side "how long do I wait" timeouts** (connection, socket read/write, request, ping, cleanup, JetStream/publish request timeouts) → millisecond resolution is sufficient → **`…Millis` long** (unit in the *name*).
- **Values serialized to the server as nanoseconds on the wire** (`ConsumerConfiguration.ackWait`/`idleHeartbeat`/`inactiveThreshold`, NAK delay, pull `expires`, …) → the API/protocol accepts nanos, so don't cap the client at millis → keep **`Duration`** (unit in the *type*).
- **Do NOT use a bare `long`-nanos** for the wire fields: it reintroduces the exact "is this nanos or millis?" ambiguity the `…Millis` naming convention was created to kill — and worse, because nanosecond magnitudes are far from intuition. `Duration` is self-documenting (`Duration.ofSeconds(30)`); `long`-nanos satisfies neither the name- nor the type-visibility test.

This partitions with no awkward middle: `JetStreamOptions.requestTimeout` / `PublishOptions.streamTimeout` are client waits → millis; the `ConsumerConfiguration` durations are server-bound → `Duration`.


The timeout knobs disagree on type/unit:
- `socketReadTimeout` — `int` millis (0 = off).
- `socketWriteTimeout` — `Duration` field, but the builder setter is `socketWriteTimeout(long millis)`.
- `connectionTimeout` / `pingInterval` / `requestCleanupInterval` / `writeQueuePushTimeout` — `Duration`.

So one `int`-millis, one `Duration`-with-a-long-millis-setter, and four `Duration`s. `PLAN_DURATION_TO_MILLIS.md` already proposes moving the `Duration` timing fields to visible-unit `…Millis` longs; **fold the socket-timeout naming into that plan** so all timeout knobs land on one consistent unit story (and `socketReadTimeout` vs `socketWriteTimeout` stop being asymmetric).

**Does any Options timeout need sub-millisecond precision (the one thing that would justify keeping `Duration`)? No.** Audited every `.toNanos()` on these fields: all of them just feed a nanos-based JDK API (`Future.get(n, NANOSECONDS)`, `awaitTermination`, `nanoTime()` deadlines) where `millis * 1_000_000` is exact and lossless. The only sub-millisecond number anywhere is `MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS = 100` (100ns), and that is a *degenerate non-zero floor* ("set to 100 nanos to ensure the scheduled task can execute"), not a configurable value — default socket write timeout is 1 minute. The smallest *meaningful* configured timeout in the system is `MINIMUM_WRITE_QUEUE_PUSH_TIMEOUT = 50ms`. So millis-longs lose nothing; keep the 100ns floor as an internal impl constant and compute nanos from `millis`. (Genuine nanosecond values do exist — NAK delay, `idleHeartbeat`/`ackWait` serialized via `addFieldAsNanos` — but those are JetStream *consumer-config / wire-format* values, not connection `Options` timeouts, and even they carry coarse values; the nanos is the protocol encoding, not a precision requirement. Keep `Duration` there, migrate the Options timeouts to millis.)

### 3. Don't reintroduce v2's socket-timeout-vs-connection-timeout cross-validation (LOW) — **DONE**

Documented the relationship in the `socketReadTimeout` builder javadoc (keep it comfortably longer than the ping interval to avoid spurious read-timeout disconnects; the library deliberately does not validate it). No cross-field validation re-added. Original note retained below.


v2 once enforced "socket read/write timeout must exceed the connection timeout" — now **deprecated** (`MINIMUM_SOCKET_WRITE_TIMEOUT_GT_CONNECTION_TIMEOUT` / `..._READ_..._GT_...`, javadoc: *"No longer enforcing a minimum compared to the connection timeout"*). v3 correctly does **not** validate this. Action is just to **document the relationship** (a socket read timeout shorter than the ping interval can cause spurious read-timeout disconnects) rather than re-add brittle cross-field validation. Keep `MINIMUM_SOCKET_WRITE_TIMEOUT_NANOS = 100` (a real scheduler floor) but never resurrect the GT-connection-timeout checks.

### 4. Dangling javadoc reference to removed v2 props (LOW / trivial) — **DONE**

`OptionsProperties.PROP_HOSTNAME_RESOLVE_MODE` javadoc no longer references the nonexistent `PROP_NO_RESOLVE_HOSTNAMES`/`PROP_FAST_FALLBACK`; it now points at the live `HostnameResolveMode` enum.

## Cross-references (owned elsewhere, listed so they're not duplicated here)

- `useTimeoutException` (make TimeoutException the only timed-out-future completion) and the always-on advanced request behavior → **`RequestBehaviorImprovement.md`** (companion-cleanup section).
- `Duration` → `…Millis` for timing fields → **`DURATION_CATALOG_AND_IMPROVEMENTS.md`** (the living catalog; supersedes the original `PLAN_DURATION_TO_MILLIS.md` reconnect-only plan).
- No-responders / no-headers tidy-up → **`PLAN_REMOVE_NOHEADERS_NORESPONDERS.md`**.

## Status

- ✅ **#2** (unit consistency) — done; see `DURATION_CATALOG_AND_IMPROVEMENTS.md`.
- ✅ **#3** (socket/connection timeout doc) — done; `socketReadTimeout` javadoc.
- ✅ **#4** (dangling javadoc) — done.
- ⬜ **#1** (requestCleanupInterval split) — moved to `PLAN_REQUEST_CLEANUP_INTERVAL_SPLIT.md`; decide alongside `RequestBehaviorImprovement.md`'s default-timeout source.
