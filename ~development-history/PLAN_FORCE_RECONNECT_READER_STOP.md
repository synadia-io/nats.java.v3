# Plan: forceReconnectImpl reader/writer stop race (V3 port)

**State: COMPLETE (2026-08-04) — A1-A5 + T1-T4 all implemented, uncommitted.** The fix is in `NatsConnection.forceReconnectImpl`: stop reader/writer first capturing both futures, then the async close, then join both with `options.getConnectionTimeout()` millis instead of 100 ms. Null-guards omitted per the A1 finding. `ReconnectTests` 20/20 and `TLSConnectTests` 23/23 green (including the three TLS tests on the flaky watch list); full `core` green; `jetstream`'s `SimplificationTests.testFetchDurable` flaked once and passed on rerun — it has **zero** `forceReconnect` references (as does all of jetstream), so it cannot reach this code.

### T3/T4: the race is now covered by a deterministic test

`ReconnectTests.testForceReconnectWaitsForStaleReaderToStop`, plus a `CLOSE_DELAY` added to the existing `ForceReconnectQueueCheckDataPort` (reused per T3 rather than adding a third data port; it also gained a `resetAll()` because its config is static and must not leak between tests).

The port delays the socket close by 1000 ms - past the old 100 ms join, well inside the 10 s test connection timeout. The reader is blocked in `read()` until that close lands, so it is guaranteed to outlive the old join.

**T4 verified both directions**, running the identical test against a worktree at `1680430c`:

| | Result |
|---|---|
| Without the fix (100 ms joins) | **5 attempts, 5 failures** - `a stale reader fired handleCommunicationIssue after the reconnect settled ==> expected: <1> but was: <2>` |
| With the fix | passes; 3 consecutive runs clean, ~5-7 s each |

It fails on every retry rather than intermittently, so it is a real reproduction, not a flake.

**Timing, measured rather than assumed** - an earlier draft of this test asserted only on connection events with a 2500 ms wait and **passed on the broken code**, which would have been a false negative. Instrumenting the unfixed build showed why:

* `t+900 ms` - exception count 1 -> 2. The stale reader wakes as the delayed close lands and calls `handleCommunicationIssue` -> `processException`.
* `t+2700-3000 ms` - only then does the spurious `DISCONNECTED`/`RECONNECTED` pair surface.

So the test waits `closeDelay + 3500 ms` and asserts the **exception count** first (earliest, most direct signal - it moves the instant the stale reader misbehaves) and the event list second. Note the unfixed code already shows `exceptions=1` at settle: that is the swallowed `TimeoutException` from the 100 ms join itself. The assertion is on the *delta* after settle, not the absolute, so it is not sensitive to that.

### Measured: the fix costs ~0.6s in one scenario, and that is the point

Two samples each, before vs after, via a temp worktree at `1680430c`:

| Test | Baseline (100 ms joins) | With fix (connection-timeout joins) |
|---|---|---|
| `testForceReconnect` | 0.780s, 0.788s | 0.789s, 0.791s |
| `testForceReconnectWithAccount` | 0.905s, 0.888s | **1.454s, 1.545s** |
| `testForceReconnectQueueBehaviorCheck` | 29.098s, 29.302s | 28.811s, 29.778s |
| `testForceReconnectOptionsBuilder` | 0.001s, 0.001s | 0.001s, 0.006s |

`testForceReconnectWithAccount` is **consistently ~+0.6s**. That is not a regression to fix — it is the fix working. Previously `forceReconnect` returned while an I/O thread was still alive (the race); now it waits for the thread to actually exit. The added time is bounded by the connection timeout and only materializes when a thread genuinely takes that long to die. Everything else is unchanged, and the 29s queue-behaviour test was already 29s at baseline (its 75 ms per-write `DELAY`, unrelated to this change) — that answers A5: no blanket failover-latency cost.

**Note this in release notes:** `forceReconnect` may now take marginally longer, because it no longer proceeds while the previous reader/writer threads are still running.

---

**Original plan state below.** Re-verified against the tree 2026-08-03: the bug was still present, every claim held, work WAS needed. The caveat in the original draft ("written from reading source, not built; reader lifecycle assumed to match V2") is now **discharged** — the V3 reader lifecycle was read directly and matches. Line numbers have drifted and are corrected throughout.

## Verification log (2026-08-03)

| Claim | Status | Evidence (current line numbers) |
|---|---|---|
| Port close is submitted **before** the stop | **CONFIRMED** | `NatsConnection.java:355-371` submits the close task; the stop follows at `:373-385` |
| Both joins are still **100 ms** | **CONFIRMED** | `NatsConnection.java:375` and `:381` |
| `reader.stop(false)` clears `running` without `shutdownInput()` | **CONFIRMED** | `NatsConnectionReader.java:191-204` — `running.set(false)` at `:193`, `shutdownInput()` gated on `shutdownDataPort` at `:194` |
| `stopped` completes only when `run()` returns | **CONFIRMED** | `start()` at `:178-182` submits `this` and stores the future |
| Comm-issue guard is `if (running.get())` | **CONFIRMED** | `NatsConnectionReader.java:257-260` |
| `finally { running.set(false) }` stomps the shared flag | **CONFIRMED** | `NatsConnectionReader.java:267-268` |
| `tryToConnect` re-joins reader only `if (reader.isRunning())` | **CONFIRMED** | `NatsConnection.java:531` (plan said 516 — drifted) |
| `cleanUpPongQueue()` in `tryToConnect` | **CONFIRMED** | `NatsConnection.java:540` (plan said 525 — drifted) |
| `getConnectionTimeout()` returns `long` millis | **CONFIRMED** | `Options.java:835-837` (plan said 831 — drifted). `.toNanos()` still will not compile |
| Writer has `stop()` / `isRunning()` to mirror the reader | **CONFIRMED** | `NatsConnectionWriter.java:107`, `:123` |

**One simplification found.** The V2-style `reader.isRunning() ? reader.stop(false) : null` null-guard is **not needed in V3**: `stopped` is initialized to an already-completed future in the reader constructor (`NatsConnectionReader.java:76-78`, "we are stopped on creation"), and `stop()` returns that field unconditionally, so joining a never-started reader returns immediately rather than hanging. Capture the futures unguarded and skip the null checks — fewer branches, same behavior. Confirm the writer does the same before relying on it (see A1).

**One behavior change to be deliberate about.** Today a missed 100 ms join is swallowed by `processException(ex)` (`:377-379`, `:383-385`) — it notifies error listeners, counts a stat, and proceeds. That is precisely what hides this race. After the change the join uses the full connection timeout, so a timeout there means a reader/writer thread genuinely refused to die within the connection timeout. Decide whether that should still be `processException` + proceed (preserves current failure mode, just far less likely) or something louder. **Recommend keeping `processException` + proceed** — forceReconnect must not become a path that throws where it previously did not.

Source: `FORCE_RECONNECT_AUDIT.md` (lives in the java-active-passive repo). This is the V3 half of that audit's Finding 1; the V2 half is already implemented in nats.java (`io.nats.client.impl.NatsConnection.forceReconnectImpl`) and is the reference implementation to mirror.

## The bug (recap)

`forceReconnectImpl` tears the socket down with `reader.stop(false)` + an async port close + a **100 ms** join, then proceeds to `reconnectImpl()` regardless of whether that join succeeded. Three facts combine into a race:

1. `reader.stop(false)` sets `running=false` but does NOT `shutdownInput()` — the reader thread stays blocked in `dataPort.read(...)`; only the port close wakes it, and that runs on another thread.
2. The `stopped` future completes only when `run()` actually returns, so the `.get(100ms)` is really "wait up to 100 ms for the reader thread to die" — and on a busy executor / TLS socket it can miss.
3. `tryToConnect` re-joins the reader only `if (reader.isRunning())` (V3: `core/.../NatsConnection.java:516`), which is now false — so it does NOT wait for a still-alive reader before calling `reader.start(...)` on the same instance.

A reader that outlives the 100 ms window can then, once the async close finally unblocks it: throw `IOException`, see `running` flipped back true by the new `start()`, call `handleCommunicationIssue` on the now-healthy connection (spurious full reconnect), and — via its `finally { running.set(false) }` — stomp the shared flag the freshly started reader loops on. Timing-dependent, worse under executor pressure and TLS. See the audit for the full trace.

## The fix (mirror V2)

In `core/src/main/java/io/synadia/client/impl/NatsConnection.java`, inside `forceReconnectImpl` (currently ~lines 329–372, the `closeSocketLock` block):

1. Move the reader/writer stop to happen **before** the port close, capturing the stopped-futures:
   ```java
   Future<Boolean> readerStopped = reader.isRunning() ? reader.stop(false) : null;
   Future<Boolean> writerStopped = writer.isRunning() ? writer.stop() : null;
   ```
   Ordering `stop(false)` first means `running` is already false when the close unblocks the read, so the reader reads the resulting IOException as an expected shutdown, not a comm issue.
2. Keep the async port close exactly as-is (graceful vs force per `frOpts`).
3. Replace the two `.get(100, TimeUnit.MILLISECONDS)` joins with joins on the captured futures using the **full connection timeout**, so the old threads are actually dead before their reader/writer instances are reused. In the common case the close lands sub-millisecond and the joins return immediately — no added failover latency.

The exact reordered block is in the V2 implementation; copy its structure.

## V3-specific adaptations (do NOT blind-port the V2 lines)

- **Connection timeout is already millis in V3.** V2 uses `options.getConnectionTimeout().toNanos()` because V2's accessor returns a `Duration`. V3's `Options.getConnectionTimeout()` returns a **`long` in milliseconds** (`core/.../Options.java:831`). So V3 must use:
  ```java
  long timeoutMillis = options.getConnectionTimeout();
  ...
  readerStopped.get(timeoutMillis, TimeUnit.MILLISECONDS);
  ```
  Using `.toNanos()` will not compile.
- **Static-imported `DISCONNECTED`** and `frOpts.isFlush()` — cosmetic, already present in V3; leave them.
- Everything else (`reader.stop(false)`, `reader.isRunning()`, `writer.isRunning()`, `cleanUpPongQueue` in `tryToConnect` at 525, `handleCommunicationIssue` guard) matches V2 — confirmed by inspection.

## Verified safe on V3 too (no extra work)

The rest of the AP issue set (3/5/7/9/10/11/12) does not reproduce in `forceReconnectImpl` on V3 for the same reasons as V2: the reconnect routes through `tryToConnect`, which calls `cleanUpPongQueue()` (V3: line 525) and sends a fresh ping; and the method drives its own status to DISCONNECTED so its `pingTask` short-circuits. Only Finding 1 needs a code change.

## Action items

### A — The fix

- [x] **A1** Confirm `NatsConnectionWriter`'s `stopped` field is also initialized to a completed future in its constructor (mirroring `NatsConnectionReader.java:76-78`). If it is, drop the null-guards for both; if not, guard the writer only.
- [x] **A2** In `forceReconnectImpl` (`NatsConnection.java:332-392`), move the reader/writer stop **above** the async port close block (`:355-371`), capturing the futures:
  ```java
  Future<Boolean> readerStopped = reader.stop(false);
  Future<Boolean> writerStopped = writer.stop();
  ```
  Ordering matters for correctness, not tidiness: with `running` already false, the IOException the close raises in the blocked reader is read as an expected shutdown by the `:257-260` guard instead of a comm issue.
- [x] **A3** Leave the async close block itself byte-for-byte as-is — graceful vs `forceClose()` per `frOpts.isForceClose()`, still submitted to `executor` so it cannot block the reconnect.
- [x] **A4** Replace the two `.get(100, TimeUnit.MILLISECONDS)` calls (`:375`, `:381`) with joins on the captured futures using `options.getConnectionTimeout()` **as millis** — `readerStopped.get(timeoutMillis, TimeUnit.MILLISECONDS)`. Do **not** port V2's `.toNanos()`; V3's accessor already returns millis (`Options.java:835`). Keep the surrounding `catch (Exception ex) { processException(ex); }` per the behavior note above.
- [x] **A5** Sanity-check the common path costs nothing: when the close lands sub-millisecond the joins return immediately, so no failover latency is added. Verify against a normal `forceReconnect` run, not by inspection alone.

### T — Tests

- [x] **T1** Baseline first: run `ReconnectTests` (has `forceReconnect` coverage at `:710` and `:797`) and `TLSConnectTests` before touching anything, so a pre-existing flake is not mistaken for a regression. **Both are on the current flaky watch list** — see `FLAKY_TESTS_ANALYSIS.md` "Second observation pass"; `TLSConnectTests` has three tests on it. Get a clean baseline or a known failure rate before starting.
- [x] **T2** Re-run both after the change and compare against that baseline.
- [x] **T3** Targeted test, if wanted: the repo already has the two pieces needed, so this is cheaper than the original plan assumed — `ForceReconnectQueueCheckDataPort` (a `SocketDataPort` subclass with a static `DELAY`) and `SocketDataPortBlockSimulator` (already models blocking/timeout behavior) are both in `core/src/test/java/io/synadia/client/impl/`. Extend one so `read()` blocks until close and the close is delayed past 100 ms, then assert no spurious `DISCONNECTED`/reconnect event fires after `forceReconnect()` returns. Reuse rather than writing a third data port.
- [x] **T4** The test must fail before A2/A4 and pass after — otherwise it is not covering this race. If it passes both ways, it is testing something else.

## Test

Mirror whatever test lands with the V2 change. A deterministic unit test is hard (the race needs a reader that misses the 100 ms window), so the realistic coverage is: (a) confirm `ForceReconnectTests` / `ReconnectTests` stay green, and (b) if a targeted test is wanted, inject a `DataPort` whose `read()` blocks until `forceClose()`/`close()` is called and whose close is artificially delayed past 100 ms, then assert no spurious `DISCONNECTED`/reconnect fires after the forceReconnect completes. Match the V2 test approach once it exists.

## Caveat (resolved)

~~Plan written from reading V3 source + a structural diff against V2; the V3 reader lifecycle is assumed to match V2's `stop(false)`/`run()` semantics — worth a direct confirmation before landing.~~

**Resolved 2026-08-03.** `NatsConnectionReader.stop(boolean)` (`:191-204`), the `catch (IOException) { if (running.get()) handleCommunicationIssue }` guard (`:257-260`), and the `finally { running.set(false) }` (`:267-268`) were all read directly and match the assumption. Still not *built* — the code change has not been written or compiled; that is A2/A4.
