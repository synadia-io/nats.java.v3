# Plan: active/passive in V3

Written 2026-10-06. How `java-active-passive` (`/mnt/c/nats/java-active-passive`, version 0.0.5, on `io.nats:jnats:2.26.1`) becomes a V3 subproject next to `batch-publish`, and whether that needs the `Connection` interface back. Options are in my order of preference.

## 1. What `ApConnection` depends on today (measured from the code)

`ApConnection extends NatsConnection` (600 lines) and lives in `io.nats.client.impl` so it can reach package-private members. It keeps a second, plain `NatsConnection` as the passive. On failover it does not reconnect: it **steals the passive's live socket**. In `reconnectImplConnect()` (a classic hook, called from `reconnectImpl`) it:
1. sets `connecting` and status RECONNECTING under `statusLock`;
2. stops its own reader and writer, force-closes its own dead `dataPort`, joins both;
3. `cleanUpPongQueue()`, `needPing.set(true)`;
4. stops the passive's writer, shuts down the passive's `pingTask` and `cleanupTask`;
5. swaps readers: takes the passive's running reader and repoints it with `reader.setConnection(this)` (the jnats "Hook 1"), gives its dead reader to the passive;
6. takes the passive's `dataPort`, repoints `SocketDataPort.connection`, nulls the passive's port, completes a new `dataPortFuture`, restarts its own writer on the port;
7. sets `currentServer`, `serverInfo`, clears `serverAuthErrors`, status CONNECTED, `softPing()`;
8. re-arms a new passive; closes the old one in the background after clearing its `connectionListeners` (so the user does not see CLOSED for a promoted connection).

`reconnectImpl` then resends the subscriptions and flushes the queued outgoing on the stolen socket. `switchToPassive()` drives the same path through `tryingToConnect.compareAndSet` + `reconnectImpl()`.

Classic members it touches: `reconnectImplConnect`, `reconnectImpl`, `statusLock`, `connecting`, `updateStatus`, `clearCurrentServer`, `statusChanged`, `reader`, `writer`, `dataPort`, `dataPortFuture`, `cleanUpPongQueue`, `needPing`, `pingTask`, `cleanupTask`, `softPing`, `currentServer`, `serverInfo`, `serverAuthErrors`, `tryingToConnect`, `connectionListeners`, `executor`, `processException`, `close(boolean, boolean)`, `connect(boolean)`, `serverPool`, plus `NatsConnectionReader.setConnection` and `SocketDataPort.connection`.

## 2. What that means against V3 (measured)

- **Classic in the V3 repo:** every member above still exists on `io.synadia.client.impl.NatsConnection` (`reconnectImplConnect` at `NatsConnection.java:455`, `setConnection` at `NatsConnectionReader.java:177`). Because `ApConnection` extends `NatsConnection` and builds its passive with `new NatsConnection(...)`, it is always classic, whatever `connectionImplementation` says. So a port that only changes names (package, `Status` → `ConnectionStatus`, `Events` → `ConnectionEvent`, `Duration` options → millis, `new Options.Builder(...)` → `Options.builder(...)`) works on classic.
- **On `NatsConnectionV3`:** none of the steal works. V3 overrides `reconnectImpl` and never calls `reconnectImplConnect`; it has no shared `reader`/`writer`/`dataPort` — each socket is a `Generation` (port + reader + `OutboundWriter`) and outgoing data lives in one `OutboundBuffer` per connection, not in the writer. A subclass of `NatsConnectionV3` cannot even see `Generation` (package-private, `private` field).
- **Package:** a subproject jar that puts classes in `io.synadia.client.impl` splits that package across two jars. That fails on the JPMS module path (two automatic modules cannot share a package) and collides in OSGi `Export-Package` (`OSGi_JPMS_TODO.md` O3). `batch-publish` has no sources yet, so there is no precedent to follow; the subproject should use its own package (e.g. `io.synadia.ap`) and reach the connection only through `protected` members.

## 3. Options

**Option 1 (recommended). AP subproject on V3 only, through a small protected adoption API in `NatsConnectionV3`.**
- New subproject `:active-passive`, artifact `jnats3-ap` (ext block like `batch-publish`), package `io.synadia.ap` (`ApConnection`, `ApOptions`, `ApServerPool`, `ApPassiveServerPool`). `ApConnection extends NatsConnectionV3`; its passive is a `NatsConnectionV3`.
- `NatsConnectionV3` gets two protected members and does the steal itself, so AP never touches internals:
  - `protected boolean adoptConnection(NatsConnectionV3 donor)`: under the transition guard, detach the donor's `Generation` without closing its port, stop the donor's writer and timers, tear down this connection's dead generation, build a new `Generation` on the donor's port with the donor's running reader repointed (`setConnection(this)`) and a new `OutboundWriter` on this connection's `OutboundBuffer`, then finish exactly like a successful connect (server info, auth errors cleared, CONNECTED, resubscribe on the control lane, `openData()`, ping). Returns false if the donor is not connected, so the caller falls back to a normal reconnect.
  - `protected boolean reconnectByAdoption()`: called at the start of each reconnect campaign, default `false`. `ApConnection` overrides it to call `adoptConnection(passive)` and re-arm.
  - Plus whatever close-without-events AP needs for the promoted passive (today it clears `connectionListeners`), as one protected method.
- Why V3 makes this simpler than classic: the reader is already per socket and reports through a source check (`g.reader == source`), so a repointed reader on a new `Generation` is accepted as current and an old one cannot disturb it; outgoing data does not live in the writer, so a new writer on the adopted port loses nothing; no END_RECONNECT marker or mode to manage.
- Classic AP users stay on the V2 artifact (`io.synadia:active-passive` on jnats 2.x). AP in V3 ignores `connectionImplementation` (documented).
- Work: the adoption API and its tests in core; port AP (about 1,000 main lines) and its tests (`ApTests`, `ApConnectionTests`, `ApOptionsTests`, `ApPassiveServerPoolTests`, `ApServerPoolCallbackTests`, about 1,350 lines) to the new package and V3 API.

**Option 2. Port AP as is, on classic, now; move to V3 later.** Mechanical rename only (section 2); it runs on classic regardless of the default. Cheapest today, but it needs a split package or opening classic internals as protected, it ties AP to the implementation that may be retired, and Option 1 has to be done anyway.

**Option 3. Bring back the `Connection` interface; `ApConnection` becomes a wrapper implementing it.** Does not remove the need for Option 1's adoption API: subscriptions, the pending request map and queued outgoing must stay on one engine object across a failover, so the engine still has to take the passive's socket. On top of that, everything that takes a connection (`JetStream`, KV, OS, Service factories, listeners) takes `NatsConnection` today and reaches its internals; they would all have to take `Connection` plus an internal SPI. Large change, no gain for AP.

**Option 4. Two AP classes, one per implementation** (`extends NatsConnection` and `extends NatsConnectionV3`). Supports both, doubles the hardest code to maintain.

## 4. The `Connection` interface (D1 of `PLAN_NATS_CONNECTION_V3.md`)

AP does not need it under Option 1. `NatsConnectionV3 extends NatsConnection`, so every API that takes a `NatsConnection` already accepts V3, and `ApConnection` (extends `NatsConnectionV3`) is also a `NatsConnection`. The interface earns its place only for an independent reason: mocking, or if classic and V3 stop being parent/child (e.g. classic is kept long term and V3 is re-based to not inherit from it). `PLAN_CORE_JETSTREAM_BOUNDARY.md` §8 reached the same conclusion for the core/JetStream boundary. Recommendation: keep D1 open, decide it with the classic retirement question, not with AP.

## 5. Open decisions

- **AP1.** Option 1, 2, 3 or 4.
- **AP2.** Subproject name / artifact / package: `:active-passive`, `jnats3-ap`, `io.synadia.ap`.
- **AP3.** Classic AP users: V2 artifact only (Option 1), or also supported in V3 (Option 4).
- **AP4.** Order: land the V3 connection PR first, then the adoption API + AP port as a second PR.
- **AP5.** Which untracked docs in `java-active-passive` (`LDM_HANDLING_PROPOSAL.md`, `SWAP_ACTIVE_PASSIVE_ANALYSIS.md`, `TEST_SCENARIOS.md`, `INTEGRATION_TEST_PLAN.md`, ...) carry open work that should move with the port. Not read for this plan.
